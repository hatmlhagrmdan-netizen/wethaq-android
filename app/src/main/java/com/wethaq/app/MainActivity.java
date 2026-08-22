package com.wethaq.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.os.*;
import android.widget.*;
import android.graphics.Color;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private static final String PREFS="wethaq", TOKEN="token", USER_ID="wethaq_id", NAME="name", CONTACTS="saved_contacts";
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root,content,messages;
    private EditText input;
    private String activeId,activeName;
    private Runnable poller;

    @Override protected void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences(PREFS,MODE_PRIVATE);if(hasToken())home();else login();}
    @Override protected void onResume(){super.onResume();if(hasToken())startPolling();}
    @Override protected void onPause(){super.onPause();stopPolling();}
    @Override protected void onDestroy(){stopPolling();io.shutdownNow();super.onDestroy();}
    private boolean hasToken(){return prefs.getString(TOKEN,"").length()>10;}
    private void base(String title){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(16,16,16,16);root.setBackgroundColor(Color.rgb(7,15,27));TextView h=tv(title,22,Color.WHITE);h.setGravity(17);root.addView(h,lp(-1,-2,0,12));ScrollView s=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);s.addView(content);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);}
    private TextView tv(String x,float z,int c){TextView t=new TextView(this);t.setText(x);t.setTextSize(z);t.setTextColor(c);t.setPadding(10,10,10,10);return t;}
    private EditText field(String h){EditText e=new EditText(this);e.setHint(h);e.setTextColor(Color.WHITE);e.setHintTextColor(Color.LTGRAY);e.setPadding(14,10,14,10);return e;}
    private Button btn(String x){Button b=new Button(this);b.setText(x);return b;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.leftMargin=l;p.bottomMargin=b;return p;}
    private void login(){base("وَثاق");content.addView(tv("هويتك الرقمية الآمنة",24,Color.WHITE));EditText n=field("الاسم الثلاثي"),y=field("سنة الميلاد");y.setInputType(2);content.addView(n,lp(-1,-2,0,8));content.addView(y,lp(-1,-2,0,8));Button a=btn("دخول"),c=btn("إنشاء هوية جديدة");content.addView(a);content.addView(c);a.setOnClickListener(v->auth(n.getText().toString().trim(),y.getText().toString().trim(),false));c.setOnClickListener(v->auth(n.getText().toString().trim(),y.getText().toString().trim(),true));content.addView(tv("المؤسس: حاتم حسين الحاج رمضان",14,Color.LTGRAY));}
    private void auth(String name,String year,boolean create){if(name.split("\\s+").length<3||!year.matches("\\d{4}")){toast("أدخل الاسم الثلاثي وسنة الميلاد");return;}io.execute(()->{try{JSONObject b=new JSONObject();b.put("name",name);b.put("birthYear",Integer.parseInt(year));b.put("deviceKey",deviceKey());HttpResult r=request("POST",create?"/api/identity":"/api/login",b.toString(),null);if(!create&&r.code==404)r=request("POST","/api/identity",b.toString(),null);if(r.code>=200&&r.code<300){JSONObject d=new JSONObject(r.body),u=d.getJSONObject("user");prefs.edit().putString(TOKEN,d.getString("token")).putString(USER_ID,u.getString("wethaq_id")).putString(NAME,u.getString("name")).putString("numeric_id",String.valueOf(u.optInt("id",-1))).apply();main.post(this::home);}else main.post(()->toast(error(r)));}catch(Exception e){main.post(()->toast("تعذر الاتصال بالخادم"));}});}
    private void home(){stopPolling();activeId=null;base("المؤسس: حاتم حسين الحاج رمضان");content.addView(tv(prefs.getString(NAME,"مستخدم وَثاق"),20,Color.WHITE));content.addView(tv(prefs.getString(USER_ID,""),14,Color.LTGRAY));Button s=btn("البحث عن مستخدم"),c=btn("جهات الاتصال"),o=btn("تسجيل الخروج");content.addView(s);content.addView(c);content.addView(o);s.setOnClickListener(v->searchScreen());c.setOnClickListener(v->contactsScreen());o.setOnClickListener(v->{prefs.edit().clear().apply();stopPolling();login();});startPolling();}
    private void searchScreen(){base("البحث");Button back=btn("العودة");EditText q=field("الاسم أو معرف وَثاق");Button go=btn("بحث");LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(back);content.addView(q);content.addView(go);content.addView(list);back.setOnClickListener(v->home());go.setOnClickListener(v->search(q.getText().toString().trim(),list));}
    private void search(String q,LinearLayout list){if(q.length()<2){toast("أدخل حرفين على الأقل");return;}io.execute(()->{try{HttpResult r=request("GET","/api/search?q="+URLEncoder.encode(q,"UTF-8"),null,null);if(r.code==200){JSONArray a=new JSONObject(r.body).optJSONArray("users");main.post(()->{list.removeAllViews();if(a==null||a.length()==0)list.addView(tv("لا توجد نتائج",16,Color.LTGRAY));else for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u!=null)addResult(list,u);}});}else main.post(()->toast(error(r)));}catch(Exception e){main.post(()->toast("فشل البحث"));}});}
    private void addResult(LinearLayout list,JSONObject u){String id=u.optString("wethaq_id"),name=u.optString("name");list.addView(tv(name+"\n"+id,18,Color.WHITE));Button b=btn("حفظ في جهات الاتصال");list.addView(b);b.setOnClickListener(v->saveContact(id,name));}
    private void saveContact(String id,String name){io.execute(()->{try{JSONObject b=new JSONObject();b.put("wethaqId",id);HttpResult r=request("POST","/api/contacts",b.toString(),auth());if(r.code>=200&&r.code<300){saveLocal(id,name);main.post(()->toast("تم الحفظ ✓"));}else main.post(()->toast(error(r)));}catch(Exception e){main.post(()->toast("تعذر حفظ جهة الاتصال"));}});}
    private void contactsScreen(){base("جهات الاتصال");Button back=btn("العودة");content.addView(back);back.setOnClickListener(v->home());JSONArray a=localContacts();if(a.length()==0){content.addView(tv("لا توجد جهات اتصال محفوظة",16,Color.LTGRAY));return;}for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u==null)continue;String id=u.optString("wethaq_id"),name=u.optString("name");Button b=btn(name+"\n"+id);content.addView(b);b.setOnClickListener(v->conversation(id,name));}}
    private void conversation(String id,String name){stopPolling();activeId=id;activeName=name;base("محادثة مع "+name);Button back=btn("جهات الاتصال");content.addView(back);messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);ScrollView s=new ScrollView(this);s.addView(messages);content.addView(s,new LinearLayout.LayoutParams(-1,0,1));LinearLayout bar=new LinearLayout(this);input=field("اكتب رسالة…");Button send=btn("إرسال");bar.addView(input,new LinearLayout.LayoutParams(0,-2,1));bar.addView(send,new LinearLayout.LayoutParams(-2,-2));root.addView(bar);back.setOnClickListener(v->contactsScreen());send.setOnClickListener(v->sendMessage());loadMessages();startPolling();}
    private void loadMessages(){if(activeId==null)return;final String target=activeId;io.execute(()->{try{HttpResult r=request("GET","/api/messages/"+URLEncoder.encode(target,"UTF-8"),null,auth());if(r.code==200){JSONArray a=new JSONObject(r.body).optJSONArray("messages");main.post(()->{if(target.equals(activeId))renderMessages(a);});}}catch(Exception ignored){}});}
    private void renderMessages(JSONArray a){if(messages==null)return;messages.removeAllViews();if(a==null)return;for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m!=null)messages.addView(tv(m.optString("body"),16,Color.WHITE));}}
    private void sendMessage(){if(input==null)return;final String text=input.getText().toString().trim();final String target=activeId;if(text.isEmpty()||target==null||target.isEmpty())return;if(target.equals(prefs.getString(USER_ID,""))){toast("لا يمكن الإرسال إلى نفسك");return;}input.setEnabled(false);io.execute(()->{try{JSONObject b=new JSONObject();b.put("receiver_id",target);b.put("to",target);b.put("body",text);HttpResult r=request("POST","/api/messages",b.toString(),auth());main.post(()->{input.setEnabled(true);if(r.code>=200&&r.code<300){input.setText("");loadMessages();toast("تم إرسال الرسالة ✓");}else toast(error(r));});}catch(Exception e){main.post(()->{input.setEnabled(true);toast("فشل الإرسال");});}});}
    private void startPolling(){stopPolling();poller=()->{if(activeId!=null)loadMessages();pollInbox();if(hasToken())main.postDelayed(poller,2500);};main.postDelayed(poller,500);}
    private void stopPolling(){if(poller!=null)main.removeCallbacks(poller);poller=null;}
    private void pollInbox(){if(!hasToken())return;io.execute(()->{try{request("GET","/api/messages/inbox",null,auth());}catch(Exception ignored){}});}
    private JSONArray localContacts(){try{return new JSONArray(prefs.getString(CONTACTS,"[]"));}catch(Exception e){return new JSONArray();}}
    private void saveLocal(String id,String name){try{JSONArray a=localContacts();for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null&&id.equals(x.optString("wethaq_id")))return;}JSONObject n=new JSONObject();n.put("wethaq_id",id);n.put("name",name);a.put(n);prefs.edit().putString(CONTACTS,a.toString()).apply();}catch(Exception ignored){}}
    private String deviceKey(){String k=prefs.getString("device_key","");if(k.length()>20)return k;k="wethaq-"+UUID.randomUUID();prefs.edit().putString("device_key",k).apply();return k;}
    private String auth(){return "Bearer "+prefs.getString(TOKEN,"");}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private String error(HttpResult r){try{JSONObject o=new JSONObject(r.body);String x=o.optString("error");if(!x.isEmpty())return x;x=o.optString("message");if(!x.isEmpty())return x;}catch(Exception ignored){}return "HTTP "+r.code;}
    private HttpResult request(String method,String path,String body,String authorization)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");if(authorization!=null)c.setRequestProperty("Authorization",authorization);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");byte[] x=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(x.length);try(OutputStream o=c.getOutputStream()){o.write(x);}}int code=c.getResponseCode();InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();}c.disconnect();return new HttpResult(code,new String(out.toByteArray(),StandardCharsets.UTF_8));}
    private static final class HttpResult{final int code;final String body;HttpResult(int c,String b){code=c;body=b;}}
}