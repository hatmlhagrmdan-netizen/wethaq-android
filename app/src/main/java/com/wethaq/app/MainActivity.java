package com.wethaq.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private static final String PREFS="wethaq";
    private static final String TOKEN="token";
    private static final String USER_ID="wethaq_id";
    private static final String NAME="name";
    private static final String YEAR="birth_year";
    private static final String CONTACTS="saved_contacts";
    private static final String ARCHIVE="media_archive";
    private static final String LAST_NOTIFY="last_notify_key";
    private static final int AUDIO_REQ=701;
    private static final int IMAGE_REQ=702;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root,content,messages;
    private EditText input;
    private String activeId,activeName;
    private Runnable poller;
    private MediaRecorder recorder;
    private File audioFile;
    private boolean recording;

    @Override protected void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences(PREFS,MODE_PRIVATE);createChannel();if(hasToken())home();else login();}
    @Override protected void onResume(){super.onResume();if(hasToken())startPolling();}
    @Override protected void onPause(){stopPolling();super.onPause();}
    @Override protected void onDestroy(){stopPolling();if(recording)stopRecording(false);io.shutdownNow();super.onDestroy();}
    private boolean hasToken(){return prefs.getString(TOKEN,"").length()>10;}
    private int gold(){return Color.rgb(212,175,55);}
    private int black(){return Color.rgb(7,7,7);}
    private TextView tv(String s,float size,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(c);t.setPadding(14,12,14,12);return t;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.GRAY);e.setTextColor(Color.WHITE);e.setSingleLine(true);e.setPadding(16,10,16,10);e.setBackgroundColor(Color.rgb(24,24,24));return e;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(gold());b.setAllCaps(false);b.setBackgroundColor(Color.rgb(24,24,24));return b;}
    private LinearLayout.LayoutParams lp(int w,int h,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.bottomMargin=bottom;return p;}
    private void base(String title){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(black());root.setPadding(12,12,12,12);TextView h=tv(title,22,gold());h.setTypeface(Typeface.DEFAULT_BOLD);h.setGravity(Gravity.CENTER);root.addView(h,lp(-1,-2,10));ScrollView s=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);s.addView(content);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);}
    private void login(){base("وَثاق");TextView a=tv("هوية رقمية آمنة",25,Color.WHITE);a.setGravity(17);content.addView(a);EditText n=field("الاسم الثلاثي"),y=field("سنة الميلاد");y.setInputType(InputType.TYPE_CLASS_NUMBER);content.addView(n,lp(-1,-2,8));content.addView(y,lp(-1,-2,12));Button l=btn("دخول"),c=btn("إنشاء هوية جديدة");content.addView(l,lp(-1,-2,8));content.addView(c,lp(-1,-2,16));TextView f=tv("المؤسس: حاتم حسين الحاج رمضان",14,gold());f.setGravity(17);content.addView(f);l.setOnClickListener(v->authenticate(n.getText().toString().trim(),y.getText().toString().trim(),false));c.setOnClickListener(v->authenticate(n.getText().toString().trim(),y.getText().toString().trim(),true));}
    private boolean valid(String n,String y){return n.split("\\s+").length>=3&&y.matches("\\d{4}");}
    private void authenticate(String n,String y,boolean create){if(!valid(n,y)){toast("أدخل الاسم الثلاثي وسنة الميلاد");return;}io.execute(()->{try{JSONObject b=new JSONObject();b.put("name",n);b.put("birthYear",Integer.parseInt(y));b.put("deviceKey",deviceKey());HttpResult r=request("POST",create?"/api/identity":"/api/login",b.toString(),null);if(!create&&r.code==404)r=request("POST","/api/identity",b.toString(),null);if(r.code>=200&&r.code<300){JSONObject u=new JSONObject(r.body),user=u.getJSONObject("user");prefs.edit().putString(TOKEN,u.getString("token")).putString(USER_ID,user.optString("wethaq_id")).putString(NAME,user.optString("name",n)).putString(YEAR,y).apply();main.post(this::home);}else main.post(()->toast(error(r)));}catch(Exception e){main.post(()->toast("تعذر الاتصال بالخادم"));}});}
    private void home(){stopPolling();base("المؤسس: حاتم حسين الحاج رمضان");TextView me=tv(prefs.getString(NAME,""),20,Color.WHITE);me.setGravity(17);content.addView(me);TextView id=tv("المعرف: "+prefs.getString(USER_ID,""),14,gold());id.setGravity(17);content.addView(id,lp(-1,-2,14));Button[] bs={btn("⌕  البحث"),btn("◉  جهات الاتصال"),btn("⚙  الإعدادات"),btn("▣  المعلومات الشخصية"),btn("▤  المحفوظات")};for(Button b:bs)content.addView(b,lp(-1,-2,8));bs[0].setOnClickListener(v->searchScreen());bs[1].setOnClickListener(v->contactsScreen());bs[2].setOnClickListener(v->settingsScreen());bs[3].setOnClickListener(v->profileScreen());bs[4].setOnClickListener(v->archiveScreen());Button out=btn("تسجيل الخروج");content.addView(out);out.setOnClickListener(v->{prefs.edit().clear().apply();login();});startPolling();}
    private void searchScreen(){base("البحث عن مستخدم");EditText q=field("الاسم أو المعرف");Button go=btn("بحث"),back=btn("رجوع");LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(q,lp(-1,-2,8));content.addView(go,lp(-1,-2,8));content.addView(tv("حفظ في جهات الاتصال",16,gold()),lp(-1,-2,10));content.addView(list);content.addView(back);go.setOnClickListener(v->search(q.getText().toString().trim(),list));back.setOnClickListener(v->home());}
    private void search(String q,LinearLayout list){if(q.length()<2){toast("أدخل حرفين على الأقل");return;}io.execute(()->{try{HttpResult r=request("GET","/api/search?q="+URLEncoder.encode(q,"UTF-8"),null,null);if(r.code==200){JSONArray a=new JSONObject(r.body).optJSONArray("users");main.post(()->{list.removeAllViews();if(a==null||a.length()==0){list.addView(tv("لا توجد نتائج",16,Color.GRAY));return;}for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u==null)continue;String id=u.optString("wethaq_id"),name=u.optString("name");TextView row=tv("👤  "+name+"\n"+id,17,Color.WHITE);list.addView(row,lp(-1,-2,2));Button save=btn("حفظ جهة الاتصال");list.addView(save,lp(-1,-2,10));save.setOnClickListener(v->saveContact(id,name));}});}else main.post(()->toast(error(r)));}catch(Exception e){main.post(()->toast("فشل البحث"));}});}
    private void saveContact(String id,String name){io.execute(()->{try{JSONObject b=new JSONObject();b.put("wethaqId",id);b.put("receiver_id",id);HttpResult r=request("POST","/api/contacts",b.toString(),auth());if(r.code>=200&&r.code<300){saveLocal(id,name);main.post(()->toast("تم حفظ جهة الاتصال ✓"));}else{saveLocal(id,name);main.post(()->toast("تم حفظها محليًا ✓"));}}catch(Exception e){saveLocal(id,name);main.post(()->toast("تم حفظ جهة الاتصال ✓"));}});}
    private void contactsScreen(){base("جهات الاتصال");Button back=btn("رجوع");content.addView(back);back.setOnClickListener(v->home());JSONArray a=localContacts();if(a.length()==0){content.addView(tv("لا توجد جهات اتصال محفوظة",16,Color.GRAY));return;}for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u==null)continue;String id=u.optString("wethaq_id"),name=u.optString("name");Button b=btn("👤  "+name+"\n"+id);content.addView(b,lp(-1,-2,8));b.setOnClickListener(v->conversation(id,name));}}
    private void conversation(String id,String name){stopPolling();activeId=id;activeName=name;base(name);TextView sub=tv("محادثة آمنة",13,gold());sub.setGravity(17);content.addView(sub);messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.addView(messages);content.addView(sc,new LinearLayout.LayoutParams(-1,0,1));LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);ImageButton mic=new ImageButton(this);mic.setImageResource(android.R.drawable.ic_btn_speak_now);mic.setBackgroundColor(Color.rgb(24,24,24));ImageButton gallery=new ImageButton(this);gallery.setImageResource(android.R.drawable.ic_menu_gallery);gallery.setBackgroundColor(Color.rgb(24,24,24));input=field("اكتب رسالة…");Button send=btn("إرسال");bar.addView(mic,new LinearLayout.LayoutParams(52,58));bar.addView(gallery,new LinearLayout.LayoutParams(52,58));bar.addView(input,new LinearLayout.LayoutParams(0,58,1));bar.addView(send,new LinearLayout.LayoutParams(70,58));root.addView(bar);Button back=btn("‹ جهات الاتصال");root.addView(back,new LinearLayout.LayoutParams(-1,55));back.setOnClickListener(v->contactsScreen());send.setOnClickListener(v->sendMessage());mic.setOnClickListener(v->{if(recording)stopRecording(true);else startRecording();});gallery.setOnClickListener(v->pickImage());loadMessages();startPolling();}
    private void loadMessages(){final String target=activeId;if(target==null)return;io.execute(()->{try{HttpResult r=request("GET","/api/messages/"+URLEncoder.encode(target,"UTF-8"),null,auth());if(r.code==200){JSONArray a=new JSONObject(r.body).optJSONArray("messages");main.post(()->{if(target.equals(activeId))renderMessages(a);});}}catch(Exception ignored){}});}
    private String sender(JSONObject m){String s=m.optString("sender_id");if(s.isEmpty())s=m.optString("senderId");if(s.isEmpty())s=m.optString("from");return s;}
    private String body(JSONObject m){String s=m.optString("body");if(s.isEmpty())s=m.optString("text");return s;}
    private void renderMessages(JSONArray a){if(messages==null)return;messages.removeAllViews();if(a==null||a.length()==0){TextView e=tv("ابدأ المحادثة الآن",15,Color.GRAY);e.setGravity(17);messages.addView(e);return;}String me=prefs.getString(USER_ID,"");for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;boolean mine=me.equals(sender(m));TextView bubble=tv(body(m)+"\n"+(mine?"✓✓":"✓"),16,mine?Color.BLACK:Color.WHITE);bubble.setTypeface(Typeface.DEFAULT);bubble.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);bubble.setPadding(20,14,20,14);bubble.setBackgroundColor(mine?gold():Color.rgb(30,30,30));LinearLayout line=new LinearLayout(this);line.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);line.addView(bubble,new LinearLayout.LayoutParams(-2,-2));messages.addView(line,lp(-1,-2,8));}}
    private void sendMessage(){if(input==null)return;String text=input.getText().toString().trim(),target=activeId;if(text.isEmpty()||target==null)return;input.setEnabled(false);io.execute(()->{try{JSONObject b=new JSONObject();b.put("receiver_id",target);b.put("to",target);b.put("body",text);HttpResult r=request("POST","/api/messages",b.toString(),auth());main.post(()->{input.setEnabled(true);if(r.code>=200&&r.code<300){input.setText("");loadMessages();}else toast(error(r));});}catch(Exception e){main.post(()->{input.setEnabled(true);toast("فشل الإرسال");});}});}
    private void settingsScreen(){base("الإعدادات");Button n=btn("🔔 الإشعارات: تشغيل"),sound=btn("🔊 صوت النقر: تشغيل"),pic=btn("🖼 الصورة الشخصية"),save=btn("حفظ");EditText name=field("الاسم الثلاثي"),year=field("المواليد");name.setText(prefs.getString(NAME,""));year.setText(prefs.getString(YEAR,""));year.setInputType(InputType.TYPE_CLASS_NUMBER);content.addView(n,lp(-1,-2,8));content.addView(sound,lp(-1,-2,12));content.addView(tv("الاسم الشخصي",14,gold()));content.addView(name,lp(-1,-2,8));content.addView(tv("المواليد",14,gold()));content.addView(year,lp(-1,-2,8));content.addView(tv("المعرف الشخصي",14,gold()));content.addView(tv(prefs.getString(USER_ID,""),17,Color.WHITE),lp(-1,-2,12));content.addView(pic,lp(-1,-2,8));content.addView(save,lp(-1,-2,8));Button back=btn("رجوع");content.addView(back);save.setOnClickListener(v->{prefs.edit().putString(NAME,name.getText().toString().trim()).putString(YEAR,year.getText().toString().trim()).apply();toast("تم حفظ الإعدادات ✓");});n.setOnClickListener(v->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.POST_NOTIFICATIONS},703);else openNotificationSettings();});sound.setOnClickListener(v->toast("صوت النقر مفعل"));pic.setOnClickListener(v->pickImage());back.setOnClickListener(v->home());}
    private void profileScreen(){base("المعلومات الشخصية");TextView pic=tv("◉\nالصورة الشخصية",20,gold());pic.setGravity(17);content.addView(pic,lp(-1,150,10));content.addView(tv("المعرف الشخصي",14,gold()));content.addView(tv(prefs.getString(USER_ID,""),19,Color.WHITE),lp(-1,-2,8));content.addView(tv("الاسم الثلاثي",14,gold()));content.addView(tv(prefs.getString(NAME,""),19,Color.WHITE),lp(-1,-2,8));content.addView(tv("المواليد",14,gold()));content.addView(tv(prefs.getString(YEAR,""),19,Color.WHITE));Button b=btn("رجوع");content.addView(b);b.setOnClickListener(v->home());}
    private void archiveScreen(){base("المحفوظات");TextView h=tv("الصور والوسائط المرسلة والمستلمة",18,gold());content.addView(h);JSONArray a=archive();if(a.length()==0)content.addView(tv("لا توجد وسائط محفوظة بعد",16,Color.GRAY));for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m!=null)content.addView(tv(m.optString("type")+"\n"+m.optString("uri"),15,Color.WHITE),lp(-1,-2,8));}Button b=btn("رجوع");content.addView(b);b.setOnClickListener(v->home());}
    private void pickImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,IMAGE_REQ);}
    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==IMAGE_REQ&&c==RESULT_OK&&d!=null&&d.getData()!=null){Uri u=d.getData();getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);saveArchive("صورة",u.toString());toast("تم حفظ الصورة في المحفوظات ✓");}}
    private void startRecording(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_REQ);return;}try{audioFile=new File(getExternalCacheDir(),"wethaq_"+System.currentTimeMillis()+".m4a");recorder=new MediaRecorder(this);recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setOutputFile(audioFile.getAbsolutePath());recorder.prepare();recorder.start();recording=true;toast("🔴 جارٍ التسجيل… اضغط الميكروفون للإيقاف");}catch(Exception e){toast("تعذر تشغيل الميكروفون");}}
    private void stopRecording(boolean save){try{if(recorder!=null){recorder.stop();recorder.release();recorder=null;}}catch(Exception ignored){}recording=false;if(save&&audioFile!=null){saveArchive("مقطع صوتي",Uri.fromFile(audioFile).toString());toast("تم حفظ المقطع الصوتي ✓");}}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("messages","رسائل وَثاق",NotificationManager.IMPORTANCE_HIGH);c.setDescription("إشعارات الرسائل الجديدة");getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    private void notifyMessage(String text){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;NotificationCompat.Builder b=new NotificationCompat.Builder(this,"messages").setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle("رسالة جديدة من "+activeName).setContentText(text).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true);NotificationManagerCompat.from(this).notify((int)System.currentTimeMillis(),b.build());}
    private void pollInbox(){if(!hasToken())return;io.execute(()->{try{HttpResult r=request("GET","/api/messages/inbox",null,auth());if(r.code!=200)return;JSONObject o=new JSONObject(r.body);JSONArray a=o.optJSONArray("messages");if(a==null)return;for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;String s=sender(m),text=body(m),key=s+"|"+text+"|"+m.optString("created_at");if(!prefs.getString(LAST_NOTIFY,"").equals(key)&&!prefs.getString(USER_ID,"").equals(s)){prefs.edit().putString(LAST_NOTIFY,key).apply();main.post(()->notifyMessage(text));}}}catch(Exception ignored){}});}
    private void startPolling(){stopPolling();poller=()->{if(activeId!=null)loadMessages();pollInbox();if(hasToken())main.postDelayed(poller,3000);};main.postDelayed(poller,700);}
    private void stopPolling(){if(poller!=null)main.removeCallbacks(poller);poller=null;}
    private JSONArray localContacts(){try{return new JSONArray(prefs.getString(CONTACTS,"[]"));}catch(Exception e){return new JSONArray();}}
    private void saveLocal(String id,String name){try{JSONArray a=localContacts();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&id.equals(x.optString("wethaq_id")))return;}JSONObject x=new JSONObject();x.put("wethaq_id",id);x.put("name",name);a.put(x);prefs.edit().putString(CONTACTS,a.toString()).apply();}catch(Exception ignored){}}
    private JSONArray archive(){try{return new JSONArray(prefs.getString(ARCHIVE,"[]"));}catch(Exception e){return new JSONArray();}}
    private void saveArchive(String type,String uri){try{JSONArray a=archive();JSONObject x=new JSONObject();x.put("type",type);x.put("uri",uri);a.put(x);prefs.edit().putString(ARCHIVE,a.toString()).apply();}catch(Exception ignored){}}
    private String deviceKey(){String k=prefs.getString("device_key","");if(k.length()>20)return k;k="wethaq-"+UUID.randomUUID();prefs.edit().putString("device_key",k).apply();return k;}
    private String auth(){return "Bearer "+prefs.getString(TOKEN,"");}
    private void openNotificationSettings(){try{startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}catch(Exception ignored){}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private String error(HttpResult r){try{JSONObject o=new JSONObject(r.body);String s=o.optString("error");if(!s.isEmpty())return s;s=o.optString("message");if(!s.isEmpty())return s;}catch(Exception ignored){}return "HTTP "+r.code;}
    private HttpResult request(String method,String path,String body,String authorization)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");if(authorization!=null)c.setRequestProperty("Authorization",authorization);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");byte[] b=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(b.length);try(OutputStream o=c.getOutputStream()){o.write(b);}}int code=c.getResponseCode();InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();}c.disconnect();return new HttpResult(code,new String(out.toByteArray(),StandardCharsets.UTF_8));}
    private static final class HttpResult{final int code;final String body;HttpResult(int c,String b){code=c;body=b;}}
}
