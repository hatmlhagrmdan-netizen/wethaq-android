package com.wethaq.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProfessionalConversationActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private static final String PREFS="wethaq";
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout messages;
    private EditText targetId,input;
    private MediaRecorder recorder;
    private boolean recording;

    private GradientDrawable bg(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(14,10,14,10);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setAllCaps(false);b.setBackground(bg(Color.rgb(28,28,32),24));return b;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.GRAY);e.setTextColor(Color.WHITE);e.setSingleLine(true);e.setPadding(14,8,14,8);e.setBackground(bg(Color.rgb(24,24,28),24));return e;}

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(5,5,7));getWindow().setNavigationBarColor(Color.rgb(5,5,7));build();String target=getIntent().getStringExtra("notification_target");String name=getIntent().getStringExtra("notification_name");if(target!=null&&!target.trim().isEmpty()){targetId.setText(target);if(name!=null&&!name.isEmpty())targetId.setHint(name);loadMessages();}}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(10,8,10,8);root.setBackgroundColor(Color.rgb(5,5,7));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("محادثة وَثاق",22,Color.rgb(212,175,55));title.setTypeface(null,1);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(text("● مفتوح",12,Color.rgb(80,220,130)));root.addView(header);
        LinearLayout target=new LinearLayout(this);target.setPadding(0,8,0,8);targetId=field("معرف وَثاق للطرف الآخر");Button load=button("فتح");target.addView(targetId,new LinearLayout.LayoutParams(0,-2,1));target.addView(load,new LinearLayout.LayoutParams(-2,-2));root.addView(target);
        ScrollView scroll=new ScrollView(this);messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);messages.setPadding(4,8,4,8);scroll.addView(messages);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);input=field("اكتب رسالة…");Button mic=button("🎙️");Button send=button("إرسال");bar.addView(input,new LinearLayout.LayoutParams(0,-2,1));bar.addView(mic,new LinearLayout.LayoutParams(58,-2));bar.addView(send,new LinearLayout.LayoutParams(-2,-2));root.addView(bar);
        setContentView(root);load.setOnClickListener(v->loadMessages());send.setOnClickListener(v->send());mic.setOnClickListener(v->toggleRecording(mic));
        messages.addView(text("اختر معرف وَثاق لبدء المحادثة.\nرسائلك تظهر في جهة ورسائل الطرف الآخر في الجهة المقابلة.",15,Color.LTGRAY));
    }
    private String token(){return getSharedPreferences(PREFS,MODE_PRIVATE).getString("token","");}
    private String id(){return targetId==null?"":targetId.getText().toString().trim();}
    private void loadMessages(){String target=id();if(target.length()<2){return;}io.execute(()->{try{HttpResult r=request("GET","/api/messages/"+URLEncoder.encode(target,"UTF-8"),null);if(r.code==200){JSONArray a=new JSONObject(r.body).optJSONArray("messages");runOnUiThread(()->render(a));}else runOnUiThread(()->toast("تعذر تحميل المحادثة"));}catch(Exception e){runOnUiThread(()->toast("تعذر الاتصال بالخادم"));}});}
    private void render(JSONArray a){messages.removeAllViews();if(a==null||a.length()==0){messages.addView(text("لا توجد رسائل بعد.",15,Color.LTGRAY));return;}String me=getSharedPreferences(PREFS,MODE_PRIVATE).getString("wethaq_id","");for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;String body=m.optString("body"),sender=m.optString("sender_wethaq_id",m.optString("sender_id",m.optString("from","")));boolean mine=me.equals(sender);LinearLayout row=new LinearLayout(this);row.setGravity(mine?Gravity.END:Gravity.START);TextView bubble=text(body+"\n"+(mine?"✓ خرجت الرسالة":"استلمت الرسالة"),15,Color.WHITE);bubble.setBackground(bg(mine?Color.rgb(72,55,18):Color.rgb(28,28,33),24));row.addView(bubble,new LinearLayout.LayoutParams(-2,-2));messages.addView(row,new LinearLayout.LayoutParams(-1,-2));}}
    private void send(){String target=id(),body=input.getText().toString().trim();if(target.length()<2||body.isEmpty())return;io.execute(()->{try{JSONObject j=new JSONObject();j.put("receiver_id",target);j.put("to",target);j.put("body",body);HttpResult r=request("POST","/api/messages",j.toString());runOnUiThread(()->{if(r.code>=200&&r.code<300){input.setText("");loadMessages();toast("✓ خرجت الرسالة");}else toast("فشل الإرسال");});}catch(Exception e){runOnUiThread(()->toast("فشل الإرسال"));}});}
    private void toggleRecording(Button mic){if(recording){try{recorder.stop();recorder.release();recorder=null;recording=false;mic.setText("🎙️");toast("تم تسجيل المقطع الصوتي محليًا ✓");}catch(Exception e){recording=false;toast("تعذر إنهاء التسجيل");}}else{try{recorder=new MediaRecorder(this);recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);recorder.setOutputFile(getCacheDir().getAbsolutePath()+"/wethaq_voice.3gp");recorder.prepare();recorder.start();recording=true;mic.setText("⏹️");toast("جارٍ تسجيل المقطع…");}catch(Exception e){recorder=null;toast("تعذر بدء التسجيل");}}}
    private HttpResult request(String method,String path,String body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");String t=token();if(!t.isEmpty())c.setRequestProperty("Authorization","Bearer "+t);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");byte[] b=body.getBytes(StandardCharsets.UTF_8);try(OutputStream o=c.getOutputStream()){o.write(b);}}int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] b= new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();}c.disconnect();return new HttpResult(code,new String(out.toByteArray(),StandardCharsets.UTF_8));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){if(recorder!=null){try{recorder.stop();recorder.release();}catch(Exception ignored){}}io.shutdownNow();super.onDestroy();}
    private static final class HttpResult{final int code;final String body;HttpResult(int c,String b){code=c;body=b;}}
}
