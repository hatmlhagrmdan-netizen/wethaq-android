package com.wethaq.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProfessionalConversationActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private static final String PREFS="wethaq";
    private static final String OUTBOX="message_outbox";
    private static final int PICK_IMAGE=4101;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout messages;
    private EditText targetId,input;
    private ProgressBar progress;
    private MediaRecorder recorder;
    private File recordingFile;
    private boolean recording;

    private GradientDrawable bg(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(14,10,14,10);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setAllCaps(false);b.setMinHeight(52);b.setBackground(bg(Color.rgb(28,28,32),24));return b;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.GRAY);e.setTextColor(Color.WHITE);e.setSingleLine(true);e.setPadding(14,8,14,8);e.setBackground(bg(Color.rgb(24,24,28),24));return e;}

    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(5,5,7));getWindow().setNavigationBarColor(Color.rgb(5,5,7));build();String target=getIntent().getStringExtra("notification_target");String name=getIntent().getStringExtra("notification_name");if(target!=null&&!target.trim().isEmpty()){targetId.setText(target);if(name!=null&&!name.isEmpty())targetId.setHint(name);loadMessages();}}

    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(10,8,10,8);root.setBackgroundColor(Color.rgb(5,5,7));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("محادثة وَثاق",22,Color.rgb(212,175,55));title.setTypeface(null,1);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(text("● آمن",12,Color.rgb(80,220,130)));root.addView(header);
        LinearLayout target=new LinearLayout(this);target.setPadding(0,8,0,8);targetId=field("معرف وَثاق للطرف الآخر");Button load=button("فتح");target.addView(targetId,new LinearLayout.LayoutParams(0,-2,1));target.addView(load,new LinearLayout.LayoutParams(-2,-2));root.addView(target);
        progress=new ProgressBar(this);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,4));
        ScrollView scroll=new ScrollView(this);messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);messages.setPadding(4,8,4,8);scroll.addView(messages);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);input=field("اكتب رسالة…");Button image=button("🖼️");Button mic=button("🎙️");Button send=button("إرسال");bar.addView(input,new LinearLayout.LayoutParams(0,-2,1));bar.addView(image,new LinearLayout.LayoutParams(58,-2));bar.addView(mic,new LinearLayout.LayoutParams(58,-2));bar.addView(send,new LinearLayout.LayoutParams(-2,-2));root.addView(bar);
        setContentView(root);load.setOnClickListener(v->loadMessages());send.setOnClickListener(v->sendText());mic.setOnClickListener(v->toggleRecording(mic));image.setOnClickListener(v->pickImage());
        messages.addView(text("اختر معرف وَثاق لبدء المحادثة.\nالنص والصور والمقاطع الصوتية مدعومة.",15,Color.LTGRAY));
    }

    private String token(){return getSharedPreferences(PREFS,MODE_PRIVATE).getString("token","");}
    private String myId(){return getSharedPreferences(PREFS,MODE_PRIVATE).getString("wethaq_id","");}
    private String id(){return targetId==null?"":targetId.getText().toString().trim();}
    private void busy(boolean value){runOnUiThread(()->progress.setVisibility(value?View.VISIBLE:View.GONE));}

    private void loadMessages(){String target=id();if(target.length()<2){toast("أدخل معرف وَثاق أولاً");return;}io.execute(()->{try{retryOutbox();HttpResult r=request("GET","/api/messages/"+URLEncoder.encode(target,"UTF-8"),null);if(r.code==200){JSONArray a=new JSONObject(r.body).optJSONArray("messages");runOnUiThread(()->render(a));}else toast("تعذر تحميل المحادثة ("+r.code+")");}catch(Exception e){toast("غير متصل — ستتم إعادة المحاولة تلقائياً");}});}

    private void render(JSONArray a){messages.removeAllViews();if(a==null||a.length()==0){messages.addView(text("لا توجد رسائل بعد.",15,Color.LTGRAY));return;}String me=myId();for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;String type=m.optString("message_type","text");String body=m.optString("body");if("image".equals(type))body="🖼️ صورة";else if("audio".equals(type))body="🎧 مقطع صوتي";String sender=m.optString("sender_wethaq_id",m.optString("sender_id",m.optString("from","")));boolean mine=me.equals(sender);LinearLayout row=new LinearLayout(this);row.setGravity(mine?Gravity.END:Gravity.START);TextView bubble=text(body+"\n"+(mine?"✓ أرسلت":"✓ استلمت"),15,Color.WHITE);bubble.setBackground(bg(mine?Color.rgb(72,55,18):Color.rgb(28,28,33),24));row.addView(bubble,new LinearLayout.LayoutParams(-2,-2));messages.addView(row,new LinearLayout.LayoutParams(-1,-2));}}

    private void sendText(){String target=id(),body=input.getText().toString().trim();if(target.length()<2||body.isEmpty()){toast("اكتب الرسالة وحدد المعرف");return;}sendPayload(target,"text",body,null,null);}

    private void sendPayload(String target,String type,String body,String data,String mime){if(target.length()<2)return;JSONObject j=new JSONObject();try{j.put("receiver_id",target);j.put("to",target);j.put("body",body==null?"":body);j.put("message_type",type);j.put("client_id",UUID.randomUUID().toString());if(data!=null)j.put("audio_data",data);if(mime!=null)j.put("mime_type",mime);}catch(Exception e){toast("تعذر تجهيز الرسالة");return;}busy(true);io.execute(()->{try{HttpResult r=request("POST","/api/messages",j.toString());if(r.code>=200&&r.code<300){runOnUiThread(()->{if("text".equals(type))input.setText("");toast("✓ أرسلت الرسالة");});}else{queuePayload(j);toast("تعذر الإرسال — حُفظت لإعادة المحاولة");}loadMessages();}catch(Exception e){queuePayload(j);toast("لا يوجد اتصال — حُفظت الرسالة لإعادة المحاولة");}finally{busy(false);}});}

    private void queuePayload(JSONObject j){synchronized(OUTBOX){try{android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);JSONArray q=new JSONArray(p.getString(OUTBOX,"[]"));q.put(j);p.edit().putString(OUTBOX,q.toString()).apply();}catch(Exception ignored){}}}
    private void retryOutbox(){synchronized(OUTBOX){try{android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);JSONArray q=new JSONArray(p.getString(OUTBOX,"[]"));JSONArray keep=new JSONArray();for(int i=0;i<q.length();i++){JSONObject j=q.optJSONObject(i);if(j==null)continue;try{HttpResult r=request("POST","/api/messages",j.toString());if(r.code<200||r.code>=300)keep.put(j);}catch(Exception e){keep.put(j);}}p.edit().putString(OUTBOX,keep.toString()).apply();}catch(Exception ignored){}}}

    private void toggleRecording(Button mic){if(recording){finishRecording(mic);return;}try{recordingFile=new File(getCacheDir(),"wethaq_voice_"+System.currentTimeMillis()+".3gp");recorder=new MediaRecorder(this);recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);recorder.setOutputFile(recordingFile.getAbsolutePath());recorder.prepare();recorder.start();recording=true;mic.setText("⏹️");toast("جارٍ التسجيل… اضغط للإرسال");}catch(Exception e){releaseRecorder();toast("تعذر بدء التسجيل");}}
    private void finishRecording(Button mic){try{recorder.stop();recorder.release();recorder=null;recording=false;mic.setText("🎙️");if(recordingFile==null||!recordingFile.exists()){toast("لم يتم إنشاء المقطع");return;}long size=recordingFile.length();if(size>5*1024*1024){toast("المقطع أكبر من الحد المسموح");recordingFile.delete();return;}byte[] bytes=readFile(recordingFile);String data=Base64.encodeToString(bytes,Base64.NO_WRAP);String target=id();if(target.length()<2){toast("حدد معرف وَثاق قبل إرسال الصوت");recordingFile.delete();return;}sendPayload(target,"audio","",data,"audio/3gpp");recordingFile.delete();}catch(Exception e){releaseRecorder();toast("تعذر حفظ المقطع الصوتي");}}
    private void releaseRecorder(){try{if(recorder!=null)recorder.release();}catch(Exception ignored){}recorder=null;recording=false;}
    private byte[] readFile(File file)throws Exception{try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}

    private void pickImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_IMAGE);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK_IMAGE||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();io.execute(()->{try{long size=contentLength(uri);if(size>6*1024*1024){toast("الصورة أكبر من الحد المسموح");return;}byte[] bytes=read(uri);String b64=Base64.encodeToString(bytes,Base64.NO_WRAP);String mime=getContentResolver().getType(uri);String target=id();if(target.length()<2){toast("حدد معرف وَثاق قبل إرسال الصورة");return;}sendPayload(target,"image","",b64,mime==null?"image/*":mime);}catch(Exception e){toast("تعذر قراءة الصورة");}});}
    private long contentLength(Uri uri){try(android.database.Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst())return c.getLong(0);}catch(Exception ignored){}return -1;}
    private byte[] read(Uri uri)throws Exception{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}

    private HttpResult request(String method,String path,String body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");String t=token();if(!t.isEmpty())c.setRequestProperty("Authorization","Bearer "+t);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");byte[] b=body.getBytes(StandardCharsets.UTF_8);try(OutputStream o=c.getOutputStream()){o.write(b);}}int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();}c.disconnect();return new HttpResult(code,new String(out.toByteArray(),StandardCharsets.UTF_8));}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
    @Override protected void onDestroy(){releaseRecorder();io.shutdownNow();super.onDestroy();}
    private static final class HttpResult{final int code;final String body;HttpResult(int c,String b){code=c;body=b;}}
}
