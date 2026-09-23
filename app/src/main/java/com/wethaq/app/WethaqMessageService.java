package com.wethaq.app;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.*;
import androidx.core.app.NotificationCompat;
import okhttp3.*;
import org.json.*;
import java.net.URLEncoder;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

public final class WethaqMessageService extends Service {
    private static final int FOREGROUND_ID=4101;
    private static final int ADMIN_NOTIFICATION_ID_BASE=52000;
    private static final int ACTION_NOTIFICATION_ID_BASE=62000;
    private static final String SERVICE_CHANNEL="wethaq_service";
    private static final String MESSAGE_CHANNEL="wethaq_messages_v2";
    private static final String CALL_CHANNEL="wethaq_calls";
    private static final String LIVE_MESSAGE_ACTION="com.wethaq.MESSAGE_RECEIVED";
    private static final String CALL_SIGNAL_ACTION="com.wethaq.CALL_SIGNAL";
    private final LinkedHashSet<String> seenMessageIds=new LinkedHashSet<>();
    private OkHttpClient client;
    private WebSocket socket;
    private boolean stopping;

    @Override public void onCreate(){super.onCreate();createChannels();client=new OkHttpClient.Builder().pingInterval(20,TimeUnit.SECONDS).retryOnConnectionFailure(true).build();startForeground(FOREGROUND_ID,baseNotification("الاتصال بخدمة الرسائل"));connect();}

    private AudioAttributes notificationAudio(){
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    }

    private void createChannels(){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager nm=getSystemService(NotificationManager.class);
        NotificationChannel service=new NotificationChannel(SERVICE_CHANNEL,"خدمة وَثاق",NotificationManager.IMPORTANCE_LOW);
        service.setSound(null,null);service.setShowBadge(false);nm.createNotificationChannel(service);
        NotificationChannel messages=new NotificationChannel(MESSAGE_CHANNEL,"رسائل وَثاق",NotificationManager.IMPORTANCE_HIGH);
        messages.enableVibration(true);messages.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),notificationAudio());messages.setShowBadge(true);nm.createNotificationChannel(messages);
        NotificationChannel calls=new NotificationChannel(CALL_CHANNEL,"مكالمات وَثاق",NotificationManager.IMPORTANCE_HIGH);
        calls.enableVibration(true);calls.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),notificationAudio());calls.setShowBadge(true);nm.createNotificationChannel(calls);
    }

    private Notification baseNotification(String text){return new NotificationCompat.Builder(this,SERVICE_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("وَثاق").setContentText(text).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build();}
    private void connect(){if(stopping)return;String token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");if(token.length()<10){scheduleReconnect();return;}try{String encoded=URLEncoder.encode(token,"UTF-8");Request r=new Request.Builder().url(WethaqConfig.WS+"?token="+encoded).build();socket=client.newWebSocket(r,new WebSocketListener(){@Override public void onOpen(WebSocket w,Response x){update("متصل — استقبال الرسائل والمكالمات والإجراءات فعال");syncPendingNotifications();}@Override public void onMessage(WebSocket w,String text){handle(text);}@Override public void onClosed(WebSocket w,int code,String reason){socket=null;scheduleReconnect();}@Override public void onFailure(WebSocket w,Throwable t,Response r){socket=null;scheduleReconnect();}});}catch(Exception e){scheduleReconnect();}}
    private void scheduleReconnect(){if(stopping)return;new Handler(Looper.getMainLooper()).postDelayed(()->{if(!stopping&&socket==null)connect();},3000);}
    private void update(String text){NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.notify(FOREGROUND_ID,baseNotification(text));}
    private boolean firstTimeMessage(String id){if(id==null||id.isEmpty())return true;synchronized(seenMessageIds){if(seenMessageIds.contains(id))return false;if(seenMessageIds.size()>=256){java.util.Iterator<String> it=seenMessageIds.iterator();if(it.hasNext()){it.next();it.remove();}}seenMessageIds.add(id);return true;}}
    private void publishLiveMessage(String senderId,String senderName){if(senderId==null||senderId.isEmpty())return;Intent live=new Intent(LIVE_MESSAGE_ACTION);live.setPackage(getPackageName());live.putExtra("sender_wethaq_id",senderId);live.putExtra("sender_name",senderName==null?"مستخدم":senderName);sendBroadcast(live);}

    private void handle(String text){
        try{
            JSONObject o=new JSONObject(text);String event=o.optString("event");
            if("message".equals(event)){
                JSONObject m=o.optJSONObject("message");String id=m==null?"":m.optString("id","");if(!firstTimeMessage(id))return;
                String sender=m==null?"مستخدم":m.optString("sender_name","مستخدم");String senderId=m==null?"":m.optString("sender_wethaq_id","");String body=m==null?"رسالة جديدة":m.optString("body","");String type=m==null?"text":m.optString("message_type","text");
                String content=body.isEmpty()?("audio".equals(type)?"🎙 رسالة صوتية":"image".equals(type)?"🖼 صورة":"رسالة جديدة"):body;
                saveIncomingContactIfNeeded(senderId,sender);
                if("admin_assignment".equals(type)||"admin_alert".equals(type)){} else showMessage(sender,senderId,content);
                publishLiveMessage(senderId,sender);
            }else if("action".equals(event)){
                JSONObject a=o.optJSONObject("action");
                if(a==null)return;
                int actionId=a.optInt("id",0);String title=a.optString("title","إجراء إداري");String body=a.optString("body","تم اتخاذ إجراء على حسابك.");String actor=a.optString("actor_name","");
                showActionNotification(title,actor,body,actionId);
            }else if("call".equals(event)){
                JSONObject from=o.optJSONObject("from");String id=from==null?"":from.optString("wethaq_id","");String name=from==null?"مستخدم":from.optString("name","مستخدم");String type=o.optString("type","");String payload=o.optString("payload","");publishCallSignal(id,type,payload,o.optLong("signal_id",0));if("offer".equals(type)&&!id.isEmpty())showIncomingCall(id,name,!payload.contains("m=video"),payload);
            }
        }catch(Exception ignored){}
    }

    private void saveIncomingContactIfNeeded(String senderId,String senderName){
        if(senderId==null||senderId.trim().isEmpty())return;
        try{
            String ownId=getSharedPreferences("wethaq",MODE_PRIVATE).getString("wethaq_id","");
            if(senderId.equals(ownId))return;
            android.content.SharedPreferences p=getSharedPreferences("wethaq",MODE_PRIVATE);
            JSONArray contacts;
            try{
                contacts=new JSONArray(p.getString("saved_contacts","[]"));
            }catch(Exception e){
                contacts=new JSONArray();
            }
            for(int i=0;i<contacts.length();i++){
                JSONObject c=contacts.optJSONObject(i);
                if(c!=null&&senderId.equals(c.optString("wethaq_id","")))return;
            }
            JSONObject c=new JSONObject();
            c.put("wethaq_id",senderId);
            c.put("name",senderName==null||senderName.trim().isEmpty()?"مستخدم":senderName.trim());
            contacts.put(c);
            p.edit().putString("saved_contacts",contacts.toString()).apply();
        }catch(Exception ignored){}
    }
    private void publishCallSignal(String senderId,String type,String payload,long signalId){if(senderId==null||senderId.isEmpty()||type==null||type.isEmpty())return;Intent i=new Intent(CALL_SIGNAL_ACTION);i.setPackage(getPackageName());i.putExtra("sender_wethaq_id",senderId);i.putExtra("type",type);i.putExtra("payload",payload==null?"":payload);i.putExtra("signal_id",signalId);sendBroadcast(i);}

    private void syncPendingNotifications(){
        final String token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");
        if(token.length()<10||client==null)return;
        try{
            Request mr=new Request.Builder().url(WethaqConfig.API+"/api/messages/inbox?pending=1").header("Authorization","Bearer "+token).get().build();
            client.newCall(mr).enqueue(new Callback(){
                @Override public void onFailure(Call call,java.io.IOException e){}
                @Override public void onResponse(Call call,Response response){
                    try(Response rr=response){
                        if(!rr.isSuccessful()||rr.body()==null)return;
                        JSONObject z=new JSONObject(rr.body().string());JSONArray a=z.optJSONArray("messages");java.util.ArrayList<Integer> ids=new java.util.ArrayList<>();
                        if(a!=null)for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;int id=m.optInt("id",0);String sender=m.optString("sender_name","مستخدم"),senderId=m.optString("sender_wethaq_id",""),body=m.optString("body",""),type=m.optString("message_type","text");String preview=body.isEmpty()?("audio".equals(type)?"🎙 رسالة صوتية":"image".equals(type)?"🖼 صورة":"رسالة جديدة"):body;saveIncomingContactIfNeeded(senderId,sender);if(!"admin_assignment".equals(type)&&!"admin_alert".equals(type)&&firstTimeMessage(String.valueOf(id)))showMessage(sender,senderId,preview);if(id>0)ids.add(id);}
                        acknowledgeMessages(ids);
                    }catch(Exception ignored){}
                }
            });
            Request ar=new Request.Builder().url(WethaqConfig.API+"/api/me/actions?pending=1").header("Authorization","Bearer "+token).get().build();
            client.newCall(ar).enqueue(new Callback(){
                @Override public void onFailure(Call call,java.io.IOException e){}
                @Override public void onResponse(Call call,Response response){
                    try(Response rr=response){
                        if(!rr.isSuccessful()||rr.body()==null)return;
                        JSONObject z=new JSONObject(rr.body().string());JSONArray a=z.optJSONArray("actions");java.util.ArrayList<Integer> ids=new java.util.ArrayList<>();
                        if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;int id=x.optInt("id",0);if(id>0){showActionNotification(x.optString("title","إجراء إداري"),x.optString("actor_name",""),x.optString("body","تم اتخاذ إجراء على حسابك."),id);ids.add(id);}}
                        acknowledgeActions(ids);
                    }catch(Exception ignored){}
                }
            });
        }catch(Exception ignored){}
    }
    private void acknowledgeMessages(java.util.ArrayList<Integer> ids){if(ids==null||ids.isEmpty())return;String token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");if(token.length()<10)return;try{org.json.JSONArray a=new org.json.JSONArray();for(Integer id:ids)a.put(id);JSONObject body=new JSONObject();body.put("ids",a);Request r=new Request.Builder().url(WethaqConfig.API+"/api/messages/notifications/delivered").header("Authorization","Bearer "+token).header("Content-Type","application/json").post(RequestBody.create(body.toString(),MediaType.parse("application/json"))).build();client.newCall(r).enqueue(new Callback(){@Override public void onFailure(Call c,java.io.IOException e){}@Override public void onResponse(Call c,Response r){r.close();}});}catch(Exception ignored){}}
    private void acknowledgeActions(java.util.ArrayList<Integer> ids){if(ids==null||ids.isEmpty())return;String token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");if(token.length()<10)return;try{org.json.JSONArray a=new org.json.JSONArray();for(Integer id:ids)a.put(id);JSONObject body=new JSONObject();body.put("ids",a);Request r=new Request.Builder().url(WethaqConfig.API+"/api/me/actions/delivered").header("Authorization","Bearer "+token).header("Content-Type","application/json").post(RequestBody.create(body.toString(),MediaType.parse("application/json"))).build();client.newCall(r).enqueue(new Callback(){@Override public void onFailure(Call c,java.io.IOException e){}@Override public void onResponse(Call c,Response r){r.close();}});}catch(Exception ignored){}}
    private void showMessage(String title,String senderId,String body){NotificationCompat.Builder b=new NotificationCompat.Builder(this,MESSAGE_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_MESSAGE).setDefaults(NotificationCompat.DEFAULT_ALL);setConversationIntent(b,senderId,title);NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.notify((int)(System.currentTimeMillis()&0x7fffffff),b.build());}
    private void showAdminMessageNotification(String title,String sender,String senderId,String body,int messageId){String preview=body==null?"":body.replace('\n',' ').trim();if(preview.length()>140)preview=preview.substring(0,140)+"…";NotificationCompat.Builder b=new NotificationCompat.Builder(this,MESSAGE_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(preview.isEmpty()?"لديك رسالة إدارية جديدة":preview).setStyle(new NotificationCompat.BigTextStyle().bigText(body==null?"":body)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_EVENT).setDefaults(NotificationCompat.DEFAULT_ALL);setConversationIntent(b,senderId,sender);NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null){int id=messageId>0?ADMIN_NOTIFICATION_ID_BASE+messageId:(int)(System.currentTimeMillis()&0x7fffffff);nm.notify(id,b.build());}}
    private void showActionNotification(String title,String actor,String body,int actionId){String full=actor==null||actor.isEmpty()?body:body+"\nالمنفذ: "+actor;NotificationCompat.Builder b=new NotificationCompat.Builder(this,MESSAGE_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("🔔 "+title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(full)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_EVENT).setDefaults(NotificationCompat.DEFAULT_ALL);Intent i=new Intent(this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(this,Math.max(1,actionId),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);b.setContentIntent(pi);NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.notify(actionId>0?ACTION_NOTIFICATION_ID_BASE+actionId:(int)(System.currentTimeMillis()&0x7fffffff),b.build());}
    private void setConversationIntent(NotificationCompat.Builder b,String senderId,String title){if(senderId==null||senderId.isEmpty())return;Intent i=new Intent(this,ProfessionalConversationActivity.class);i.putExtra("notification_target",senderId);i.putExtra("notification_name",title);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);PendingIntent pi=PendingIntent.getActivity(this,Math.abs(senderId.hashCode()),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);b.setContentIntent(pi);}
    private void showIncomingCall(String target,String name,boolean audioOnly,String offerPayload){Intent i=new Intent(this,VideoCallActivity.class);i.putExtra("target",target);i.putExtra("name",name);i.putExtra("audioOnly",audioOnly);i.putExtra("incomingOffer",offerPayload);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent pi=PendingIntent.getActivity(this,(target+name).hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);NotificationCompat.Builder b=new NotificationCompat.Builder(this,CALL_CHANNEL).setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle((audioOnly?"مكالمة صوتية واردة من ":"مكالمة فيديو واردة من ")+name).setContentText("اضغط للرد على المكالمة").setContentIntent(pi).setFullScreenIntent(pi,true).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true).setTimeoutAfter(60000).setDefaults(NotificationCompat.DEFAULT_ALL);NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.notify(4102,b.build());}
    @Override public int onStartCommand(Intent i,int flags,int startId){stopping=false;if(socket==null)connect();return START_STICKY;}
    @Override public void onDestroy(){stopping=true;if(socket!=null){socket.close(1000,"service stopped");socket=null;}if(client!=null)client.dispatcher().executorService().shutdown();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
