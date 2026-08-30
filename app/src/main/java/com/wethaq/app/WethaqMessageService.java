package com.wethaq.app;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import okhttp3.*;
import org.json.*;
import java.util.concurrent.TimeUnit;

public final class WethaqMessageService extends Service {
    private static final int ID=4101;
    private static final String CHANNEL="wethaq_realtime";
    private OkHttpClient client; private WebSocket socket;
    @Override public void onCreate(){super.onCreate();createChannel();startForeground(ID,baseNotification("الاتصال بخدمة الرسائل"));connect();}
    private void createChannel(){NotificationManager nm=getSystemService(NotificationManager.class);if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"رسائل واتصالات وَثاق",NotificationManager.IMPORTANCE_HIGH);c.enableVibration(true);c.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,null);nm.createNotificationChannel(c);}}
    private Notification baseNotification(String text){return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("وَثاق").setContentText(text).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build();}
    private void connect(){String token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");if(token.length()<10)return;client=new OkHttpClient.Builder().pingInterval(25,TimeUnit.SECONDS).retryOnConnectionFailure(true).build();Request r=new Request.Builder().url("wss://wethaq-backend-production.up.railway.app/ws?token="+HttpUrl.parse("http://localhost/?token="+HttpUrl.parse("http://x/?v="+token).queryParameter("v")).queryParameter("v")).build();socket=client.newWebSocket(r,new WebSocketListener(){@Override public void onOpen(WebSocket w,Response x){update("متصل — دریافت پیام‌ها فعال");}@Override public void onMessage(WebSocket w,String text){handle(text);}@Override public void onClosed(WebSocket w,int code,String reason){scheduleReconnect();}@Override public void onFailure(WebSocket w,Throwable t,Response r){scheduleReconnect();}});}
    private void scheduleReconnect(){new Handler(Looper.getMainLooper()).postDelayed(()->{if(!isDestroyedCompat())connect();},3000);}
    private boolean isDestroyedCompat(){return Build.VERSION.SDK_INT>=17&&isFinishing();}
    private void update(String text){NotificationManager nm=getSystemService(NotificationManager.class);nm.notify(ID,baseNotification(text));}
    private void handle(String text){try{JSONObject o=new JSONObject(text);if("message".equals(o.optString("event"))){JSONObject m=o.optJSONObject("message");String sender=m==null?"مستخدم":m.optString("sender_name","مستخدم");String body=m==null?"رسالة جديدة":m.optString("body","");String type=m==null?"text":m.optString("message_type","text");String content=body.isEmpty()?("audio".equals(type)?"🎙 رسالة صوتية":"image".equals(type)?"🖼 صورة":"رسالة جديدة"):body;showMessage(sender,content);}else if("call".equals(o.optString("event"))){JSONObject from=o.optJSONObject("from");showMessage("مكالمة واردة",(from==null?"مستخدم":from.optString("name","مستخدم"))+" يحاول الاتصال بك");}}catch(Exception ignored){}}
    private void showMessage(String title,String body){NotificationCompat.Builder b=new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_MESSAGE);getSystemService(NotificationManager.class).notify((int)(System.currentTimeMillis()&0x7fffffff),b.build());}
    @Override public int onStartCommand(Intent i,int flags,int startId){if(socket==null)connect();return START_STICKY;}
    @Override public void onDestroy(){if(socket!=null)socket.close(1000,"service stopped");if(client!=null)client.dispatcher().executorService().shutdown();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
