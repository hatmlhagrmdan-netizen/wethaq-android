package com.wethaq.app;

import android.app.*;
import android.content.*;
import android.media.RingtoneManager;
import android.os.*;
import androidx.core.app.NotificationCompat;
import okhttp3.*;
import org.json.*;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

public final class WethaqMessageService extends Service {
    private static final int FOREGROUND_ID=4101;
    private static final String SERVICE_CHANNEL="wethaq_service";
    private static final String MESSAGE_CHANNEL="wethaq_messages";
    private static final String CALL_CHANNEL="wethaq_calls";
    private OkHttpClient client;
    private WebSocket socket;
    private boolean stopping;
    @Override public void onCreate(){super.onCreate();createChannels();startForeground(FOREGROUND_ID,baseNotification("الاتصال بخدمة الرسائل"));connect();}
    private void createChannels(){if(Build.VERSION.SDK_INT<26)return;NotificationManager nm=getSystemService(NotificationManager.class);NotificationChannel service=new NotificationChannel(SERVICE_CHANNEL,"خدمة وَثاق",NotificationManager.IMPORTANCE_LOW);service.setSound(null,null);service.setShowBadge(false);nm.createNotificationChannel(service);NotificationChannel messages=new NotificationChannel(MESSAGE_CHANNEL,"رسائل وَثاق",NotificationManager.IMPORTANCE_HIGH);messages.enableVibration(true);messages.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),null);messages.setShowBadge(true);nm.createNotificationChannel(messages);NotificationChannel calls=new NotificationChannel(CALL_CHANNEL,"مكالمات وَثاق",NotificationManager.IMPORTANCE_HIGH);calls.enableVibration(true);calls.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),null);calls.setShowBadge(true);nm.createNotificationChannel(calls);}
    private Notification baseNotification(String text){return new NotificationCompat.Builder(this,SERVICE_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("وَثاق").setContentText(text).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build();}
    private void connect(){if(stopping)return;String token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");if(token.length()<10){scheduleReconnect();return;}try{String encoded=URLEncoder.encode(token,"UTF-8");client=new OkHttpClient.Builder().pingInterval(20,TimeUnit.SECONDS).retryOnConnectionFailure(true).build();Request r=new Request.Builder().url("wss://wethaq-backend-production.up.railway.app/ws?token="+encoded).build();socket=client.newWebSocket(r,new WebSocketListener(){@Override public void onOpen(WebSocket w,Response x){update("متصل — استقبال الرسائل والمكالمات فعال");}@Override public void onMessage(WebSocket w,String text){handle(text);}@Override public void onClosed(WebSocket w,int code,String reason){socket=null;scheduleReconnect();}@Override public void onFailure(WebSocket w,Throwable t,Response r){socket=null;scheduleReconnect();}});}catch(Exception e){scheduleReconnect();}}
    private void scheduleReconnect(){if(stopping)return;new Handler(Looper.getMainLooper()).postDelayed(()->{if(!stopping&&socket==null)connect();},3000);}
    private void update(String text){NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.notify(FOREGROUND_ID,baseNotification(text));}
    private void handle(String text){try{JSONObject o=new JSONObject(text);String event=o.optString("event");if("message".equals(event)){JSONObject m=o.optJSONObject("message");String sender=m==null?"مستخدم":m.optString("sender_name","مستخدم");String body=m==null?"رسالة جديدة":m.optString("body","");String type=m==null?"text":m.optString("message_type","text");String content=body.isEmpty()?("audio".equals(type)?"🎙 رسالة صوتية":"image".equals(type)?"🖼 صورة":"رسالة جديدة"):body;showMessage(sender,content);}else if("call".equals(event)){JSONObject from=o.optJSONObject("from");String id=from==null?"":from.optString("wethaq_id","");String name=from==null?"مستخدم":from.optString("name","مستخدم");String type=o.optString("type","");String payload=o.optString("payload","");if("offer".equals(type)&&!id.isEmpty()){boolean audioOnly=!payload.contains("m=video");showIncomingCall(id,name,audioOnly,payload);}}}catch(Exception ignored){}}
    private void showMessage(String title,String body){NotificationCompat.Builder b=new NotificationCompat.Builder(this,MESSAGE_CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_MESSAGE).setDefaults(NotificationCompat.DEFAULT_VIBRATE);int id=(int)(System.currentTimeMillis()&0x7fffffff);NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.notify(id,b.build());}
    private void showIncomingCall(String target,String name,boolean audioOnly,String offerPayload){Intent i=new Intent(this,VideoCallActivity.class);i.putExtra("target",target);i.putExtra("name",name);i.putExtra("audioOnly",audioOnly);i.putExtra("incomingOffer",offerPayload);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent pi=PendingIntent.getActivity(this,(target+name).hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);NotificationCompat.Builder b=new NotificationCompat.Builder(this,CALL_CHANNEL).setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle((audioOnly?"مكالمة صوتية واردة من ":"مكالمة فيديو واردة من ")+name).setContentText("اضغط للرد على المكالمة").setContentIntent(pi).setFullScreenIntent(pi,true).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_CALL).setOngoing(true).setTimeoutAfter(60000);NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.notify(4102,b.build());}
    @Override public int onStartCommand(Intent i,int flags,int startId){stopping=false;if(socket==null)connect();return START_STICKY;}
    @Override public void onDestroy(){stopping=true;if(socket!=null){socket.close(1000,"service stopped");socket=null;}if(client!=null)client.dispatcher().executorService().shutdown();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
