package com.wethaq.app;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.*;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import okhttp3.*;
import org.json.*;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public final class WethaqMessageService extends Service {
    private static final int FOREGROUND_ID=4101;
    private static final int INCOMING_CALL_ID=4102;
    private static final int ADMIN_ID_BASE=4700;
    private static final String SERVICE_CHANNEL="wethaq_service";
    private static final String MESSAGE_CHANNEL="wethaq_messages";
    private static final String CALL_CHANNEL="wethaq_calls";
    private static final String MESSAGE_GROUP="wethaq_message_group";
    private OkHttpClient client;
    private WebSocket socket;
    private boolean stopping;
    private final Handler reconnectHandler=new Handler(Looper.getMainLooper());
    private long reconnectDelayMs=3000;

    @Override public void onCreate(){
        super.onCreate();
        createChannels();
        startForeground(FOREGROUND_ID,baseNotification("الاتصال بخدمة الرسائل"));
        connect();
    }

    private void createChannels(){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm==null)return;
        NotificationChannel service=new NotificationChannel(SERVICE_CHANNEL,"خدمة وَثاق",NotificationManager.IMPORTANCE_LOW);
        service.setSound(null,null);service.setShowBadge(false);
        nm.createNotificationChannel(service);
        AudioAttributes notificationAudio=new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel messages=new NotificationChannel(MESSAGE_CHANNEL,"رسائل وَثاق",NotificationManager.IMPORTANCE_HIGH);
        messages.enableVibration(true);
        messages.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),notificationAudio);
        messages.setShowBadge(true);
        nm.createNotificationChannel(messages);
        AudioAttributes callAudio=new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel calls=new NotificationChannel(CALL_CHANNEL,"مكالمات وَثاق",NotificationManager.IMPORTANCE_HIGH);
        calls.enableVibration(true);
        calls.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),callAudio);
        calls.setShowBadge(true);
        nm.createNotificationChannel(calls);
    }

    private Notification baseNotification(String text){
        return new NotificationCompat.Builder(this,SERVICE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("وَثاق")
                .setContentText(text)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setShowWhen(false)
                .build();
    }

    private boolean notificationsEnabled(){
        return Build.VERSION.SDK_INT<33 || NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    private void connect(){
        if(stopping)return;
        String token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");
        if(token.length()<10){scheduleReconnect();return;}
        try{
            String encoded=URLEncoder.encode(token,"UTF-8");
            client=new OkHttpClient.Builder().pingInterval(20,TimeUnit.SECONDS).retryOnConnectionFailure(true).build();
            Request r=new Request.Builder().url("wss://wethaq-backend-production.up.railway.app/ws?token="+encoded).build();
            socket=client.newWebSocket(r,new WebSocketListener(){
                @Override public void onOpen(WebSocket w,Response x){reconnectDelayMs=3000;update("متصل — استقبال الرسائل والمكالمات فعال");}
                @Override public void onMessage(WebSocket w,String text){handle(text);}
                @Override public void onClosed(WebSocket w,int code,String reason){if(socket==w)socket=null;scheduleReconnect();}
                @Override public void onFailure(WebSocket w,Throwable t,Response r){if(socket==w)socket=null;scheduleReconnect();}
            });
        }catch(Exception e){scheduleReconnect();}
    }

    private void scheduleReconnect(){
        if(stopping)return;
        reconnectHandler.removeCallbacksAndMessages(null);
        long delay=reconnectDelayMs;
        reconnectDelayMs=Math.min(reconnectDelayMs*2,60000);
        reconnectHandler.postDelayed(()->{if(!stopping&&socket==null)connect();},delay);
    }

    private void update(String text){
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null)nm.notify(FOREGROUND_ID,baseNotification(text));
    }

    private void handle(String text){
        try{
            JSONObject o=new JSONObject(text);
            String event=o.optString("event");
            if("message".equals(event)){
                JSONObject m=o.optJSONObject("message");
                String sender=m==null?"مستخدم":m.optString("sender_name","مستخدم");
                String senderId=m==null?"":m.optString("sender_wethaq_id","");
                String body=m==null?"رسالة جديدة":m.optString("body","");
                String type=m==null?"text":m.optString("message_type","text");
                String messageId=m==null?"":m.optString("id","");
                String createdAt=m==null?"":m.optString("created_at","");
                String content=body.isEmpty()?("audio".equals(type)?"🎙 رسالة صوتية":"image".equals(type)?"🖼 صورة":"رسالة جديدة"):body;
                showMessage(sender,senderId,content,messageId,createdAt);
            }else if("call".equals(event)){
                JSONObject from=o.optJSONObject("from");
                String id=from==null?"":from.optString("wethaq_id","");
                String name=from==null?"مستخدم":from.optString("name","مستخدم");
                String type=o.optString("type","");
                String payload=o.optString("payload","");
                if("offer".equals(type)&&!id.isEmpty()){
                    boolean audioOnly=!payload.contains("m=video");
                    showIncomingCall(id,name,audioOnly,payload);
                }else if("end".equals(type))cancelIncomingCallNotification();
            }else if("admin".equals(event)){
                String msg=o.optString("message","تم تحديث صلاحياتك الإدارية في وَثاق");
                showAdminNotice(msg,o.optString("type","admin"));
            }
        }catch(Exception ignored){}
    }

    private void showMessage(String title,String senderId,String body,String messageId,String createdAt){
        if(!notificationsEnabled())return;
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,MESSAGE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                .setGroup(MESSAGE_GROUP)
                .setShowWhen(true);
        if(senderId!=null&&!senderId.isEmpty()){
            Intent i=new Intent(this,ProfessionalConversationActivity.class);
            i.putExtra("notification_target",senderId);
            i.putExtra("notification_name",title);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi=PendingIntent.getActivity(this,stableHash(senderId),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            b.setContentIntent(pi);
        }
        int id=messageNotificationId(senderId,messageId,body,createdAt);
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null)nm.notify(id,b.build());
    }

    private int messageNotificationId(String senderId,String messageId,String body,String createdAt){
        String key=(messageId==null?"":messageId)+"|"+(senderId==null?"":senderId)+"|"+(createdAt==null?"":createdAt)+"|"+(body==null?"":body);
        int id=stableHash(key)&0x3fffffff;
        return id<5000?id+5000:id;
    }

    private int stableHash(String value){
        int h=7;
        String s=value==null?"":value;
        for(int i=0;i<s.length();i++)h=31*h+s.charAt(i);
        return Math.abs(h==Integer.MIN_VALUE?0:h);
    }

    private void showAdminNotice(String message,String type){
        if(!notificationsEnabled())return;
        Intent i=new Intent(this,AdminActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,7000+Math.abs(type.hashCode()%1000),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,MESSAGE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("صلاحية إدارية في وَثاق")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_STATUS);
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null)nm.notify(ADMIN_ID_BASE+Math.abs(type.hashCode()%200),b.build());
    }

    private void showIncomingCall(String target,String name,boolean audioOnly,String offerPayload){
        if(!notificationsEnabled())return;
        Intent i=new Intent(this,VideoCallActivity.class);
        i.putExtra("target",target);i.putExtra("name",name);i.putExtra("audioOnly",audioOnly);i.putExtra("incomingOffer",offerPayload);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,(target+name).hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,CALL_CHANNEL)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle((audioOnly?"مكالمة صوتية واردة من ":"مكالمة فيديو واردة من ")+name)
                .setContentText("اضغط للرد على المكالمة")
                .setContentIntent(pi)
                .setFullScreenIntent(pi,true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setTimeoutAfter(60000)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true);
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null)nm.notify(INCOMING_CALL_ID,b.build());
    }

    private void cancelIncomingCallNotification(){
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(nm!=null)nm.cancel(INCOMING_CALL_ID);
    }

    @Override public int onStartCommand(Intent i,int flags,int startId){
        stopping=false;
        if(socket==null)connect();
        return START_STICKY;
    }

    @Override public void onDestroy(){
        stopping=true;
        reconnectHandler.removeCallbacksAndMessages(null);
        cancelIncomingCallNotification();
        if(socket!=null){socket.close(1000,"service stopped");socket=null;}
        if(client!=null)client.dispatcher().executorService().shutdown();
        super.onDestroy();
    }

    @Override public android.os.IBinder onBind(Intent i){return null;}
}
