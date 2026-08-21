package com.wethaq.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private static final String PREFS="wethaq", TOKEN="token", USER_ID="wethaq_id", NAME="name", CONTACTS="saved_contacts";
    private static final String FOUNDER="المؤسس: حاتم حسين الحاج رمضان";
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root,content,messages;
    private TextView status;
    private EditText messageInput;
    private String activeId,activeName;
    private Runnable poller;
    private final int BG=Color.rgb(9,17,29), PANEL=Color.rgb(18,31,48), PANEL2=Color.rgb(25,42,63), WHITE=Color.WHITE, MUTED=Color.rgb(180,194,208), ACCENT=Color.rgb(70,145,170);

    @Override protected void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences(PREFS,MODE_PRIVATE);if(hasToken())home();else loginScreen();}
    @Override protected void onPause(){super.onPause();stopPolling();}
    @Override protected void onDestroy(){stopPolling();io.shutdownNow();super.onDestroy();}
    private boolean hasToken(){return prefs.getString(TOKEN,"").length()>10;}

    private void base(String heading){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);root.setBackgroundColor(BG);
        TextView h=tv(heading,22,WHITE,Typeface.BOLD);h.setGravity(Gravity.CENTER);h.setPadding(12,16,12,16);h.setBackgroundColor(PANEL);root.addView(h,lp(-1,-2,0,0));
        status=tv("",13,MUTED,Typeface.NORMAL);status.setGravity(Gravity.CENTER);root.addView(status,lp(-1,-2,0,4));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(content);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void loginScreen(){base("وَثاق");TextView w=tv("هويتك الرقمية الآمنة",22,WHITE,Typeface.BOLD);w.setGravity(Gravity.CENTER);content.addView(w,lp(-1,-2,0,20));EditText n=field("الاسم الثلاثي"),y=field("سنة الميلاد");y.setInputType(2);content.addView(n,lp(-1,-2,0,8));content.addView(y,lp(-1,-2,0,12));Button b=button("دخول / إنشاء الهوية");content.addView(b,lp(-1,-2,0,10));b.setOnClickListener(v->{String name=n.getText().toString().trim(),year=y.getText().toString().trim();if(name.split("\\s+").length<3||year.length()!=4){setStatus("أدخل الاسم الثلاثي وسنة الميلاد.",true);return;}login(name,year);});}

    private void login(String name,String year){io.execute(()->{try{JSONObject body=new JSONObject();body.put("name",name);body.put("birthYear",Integer.parseInt(year));body.put("deviceKey",deviceKey());HttpResult r=request("POST","/api/identity",body.toString(),null);if(r.code>=200&&r.code<300){JSONObject d=new JSONObject(r.body),u=d.getJSONObject("user");prefs.edit().putString(TOKEN,d.getString("token")).putString(USER_ID,u.getString("wethaq_id")).putString(NAME,u.getString("name")).apply();main.post(this::home);}else main.post(()->setStatus(error(r),true));}catch(Exception e){main.post(()->setStatus("تعذر الاتصال بالخادم. تحقق من الإنترنت.",true));}});}

    private void home(){stopPolling();activeId=null;base(FOUNDER);LinearLayout me=card();me.addView(tv(prefs.getString(NAME,"مستخدم وَثاق"),19,WHITE,Typeface.BOLD));me.addView(tv("المعرف: "+prefs.getString(USER_ID,""),14,MUTED,Typeface.NORMAL),lp(-1,-2,0,4));content.addView(me,lp(-1,-2,0,12));Button search=button("البحث عن مستخدم"),contacts=button("جهات الاتصال"),logout=button("تسجيل الخروج");content.addView(search,lp(-1,-2,0,7));content.addView(contacts,lp(-1,-2,0,7));content.addView(logout,lp(-1,-2,0,7));search.setOnClickListener(v->searchScreen());contacts.setOnClickListener(v->contactsScreen());logout.setOnClickListener(v->{prefs.edit().clear().apply();loginScreen();});setStatus(isOnline()?"متصل":"غير متصل",false);}

    private void searchScreen(){base("البحث عن مستخدم");Button back=button("← العودة");EditText q=field("الاسم أو معرف وَثاق");Button go=button("بحث");LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);content.addView(back,lp(-1,-2,0,8));content.addView(q,lp(-1,-2,0,8));content.addView(go,lp(-1,-2,0,12));content.addView(results,lp(-1,-2,0,0));back.setOnClickListener(v->home());go.setOnClickListener(v->search(q.getText().toString().trim(),results));}

    private void search(String q,LinearLayout results){if(q.length()<2){setStatus("أدخل حرفين على الأقل.",true);return;}setStatus("جاري البحث…",false);io.execute(()->{try{HttpResult r=request("GET","/api/search?q="+URLEncoder.encode(q,"UTF-8"),null,null);if(r.code==200){JSONArray a=new JSONObject(r.body).optJSONArray("users");main.post(()->{results.removeAllViews();if(a==null||a.length()==0)results.addView(tv("لا توجد نتائج.",16,MUTED,Typeface.NORMAL));else for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u!=null)addResult(results,u);}});}else main.post(()->setStatus(error(r),true));}catch(Exception e){main.post(()->setStatus("فشل البحث. تحقق من الإنترنت.",true));}});}

    private void addResult(LinearLayout list,JSONObject u){String id=u.optString("wethaq_id"),name=u.optString("name");LinearLayout c=card();c.addView(tv(name,18,WHITE,Typeface.BOLD));c.addView(tv(id+(u.optBoolean("online")?" • متصل":""),13,MUTED,Typeface.NORMAL),lp(-1,-2,0,8));Button save=button("حفظ في جهات الاتصال");c.addView(save);list.addView(c,lp(-1,-2,0,8));save.setOnClickListener(v->saveContact(id,name,save));}

    private void saveContact(String id,String name,Button save){io.execute(()->{try{JSONObject body=new JSONObject();body.put("wethaqId",id);HttpResult r=request("POST","/api/contacts",body.toString(),auth());if(r.code>=200&&r.code<300){saveLocal(id,name);main.post(()->{save.setText("تم الحفظ ✓");save.setEnabled(false);setStatus("تم حفظ "+name+" في جهات الاتصال.",false);});}else main.post(()->setStatus(error(r),true));}catch(Exception e){main.post(()->setStatus("تعذر حفظ جهة الاتصال.",true));}});}

    private void contactsScreen(){base("جهات الاتصال");Button back=button("← العودة");LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);content.addView(back,lp(-1,-2,0,10));content.addView(list,lp(-1,-2,0,0));back.setOnClickListener(v->home());renderContacts(list);if(isOnline())io.execute(()->{try{HttpResult r=request("GET","/api/contacts",null,auth());if(r.code==200){JSONArray a=new JSONObject(r.body).optJSONArray("contacts");if(a!=null)for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u!=null)saveLocal(u.optString("wethaq_id"),u.optString("name"));}main.post(()->renderContacts(list));}}catch(Exception ignored){}});}

    private void renderContacts(LinearLayout list){list.removeAllViews();JSONArray a=localContacts();if(a.length()==0){list.addView(tv("لا توجد جهات اتصال محفوظة بعد.",16,MUTED,Typeface.NORMAL));return;}for(int i=0;i<a.length();i++){JSONObject u=a.optJSONObject(i);if(u!=null){String id=u.optString("wethaq_id"),name=u.optString("name");Button b=button(name+"\n"+id);b.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);list.addView(b,lp(-1,-2,0,6));b.setOnClickListener(v->conversation(id,name));}}}

    private JSONArray localContacts(){try{return new JSONArray(prefs.getString(CONTACTS,"[]"));}catch(Exception e){return new JSONArray();}}
    private void saveLocal(String id,String name){try{JSONArray old=localContacts(),out=new JSONArray();boolean found=false;for(int i=0;i<old.length();i++){JSONObject x=old.optJSONObject(i);if(x==null)continue;if(id.equals(x.optString("wethaq_id"))){JSONObject n=new JSONObject();n.put("wethaq_id",id);n.put("name",name);out.put(n);found=true;}else out.put(x);}if(!found){JSONObject n=new JSONObject();n.put("wethaq_id",id);n.put("name",name);out.put(n);}prefs.edit().putString(CONTACTS,out.toString()).apply();}catch(Exception ignored){}}

    private void conversation(String id,String name){stopPolling();activeId=id;activeName=name;base(name);Button back=button("← جهات الاتصال");content.addView(back,lp(-1,-2,0,6));messages=new LinearLayout(this);messages.setOrientation(LinearLayout.VERTICAL);ScrollView s=new ScrollView(this);s.addView(messages);content.addView(s,new LinearLayout.LayoutParams(-1,0,1));LinearLayout bar=new LinearLayout(this);messageInput=field("اكتب رسالتك…");Button send=button("إرسال");bar.addView(messageInput,new LinearLayout.LayoutParams(0,-2,1));bar.addView(send,new LinearLayout.LayoutParams(-2,-2));root.addView(bar,new LinearLayout.LayoutParams(-1,-2));back.setOnClickListener(v->contactsScreen());send.setOnClickListener(v->sendMessage());loadMessages();startPolling();}

    private void loadMessages(){if(!isOnline()){setStatus("يجب الاتصال بالإنترنت لعرض المحادثة.",true);return;}io.execute(()->{try{HttpResult r=request("GET","/api/messages/"+URLEncoder.encode(activeId,"UTF-8"),null,auth());if(r.code==200){JSONObject d=new JSONObject(r.body);final JSONArray a=d.optJSONArray("messages");main.post(()->renderMessages(a));}else main.post(()->setStatus(error(r),true));}catch(Exception e){main.post(()->setStatus("تعذر تحميل المحادثة.",true));}});}

    private void renderMessages(JSONArray a){if(messages==null)return;messages.removeAllViews();if(a==null||a.length()==0){messages.addView(tv("لا توجد رسائل. ابدأ المحادثة.",15,MUTED,Typeface.NORMAL));return;}String me=prefs.getString("numeric_id","");for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;boolean mine=String.valueOf(m.optInt("sender_id",-1)).equals(me);LinearLayout b=card();b.setBackgroundColor(mine?PANEL2:PANEL);b.addView(tv(m.optString("body"),16,WHITE,Typeface.NORMAL));b.addView(tv(m.optString("status")+" • "+m.optString("created_at"),11,MUTED,Typeface.NORMAL));messages.addView(b,lp(mine?60:0,5,mine?0:60,5));}}

    private void sendMessage(){String body=messageInput.getText().toString().trim();if(body.length()==0)return;if(!isOnline()){setStatus("يجب الاتصال بالإنترنت لإرسال الرسائل.",true);return;}messageInput.setText("");io.execute(()->{try{JSONObject b=new JSONObject();b.put("to",activeId);b.put("body",body);HttpResult r=request("POST","/api/messages",b.toString(),auth());if(r.code>=200&&r.code<300)main.post(this::loadMessages);else main.post(()->setStatus(error(r),true));}catch(Exception e){main.post(()->setStatus("انقطع الاتصال أثناء الإرسال.",true));}});}

    private void startPolling(){stopPolling();poller=()->{if(activeId!=null&&isOnline())loadMessages();if(activeId!=null)main.postDelayed(poller,5000);};main.postDelayed(poller,5000);}
    private void stopPolling(){if(poller!=null)main.removeCallbacks(poller);poller=null;}
    private String deviceKey(){String k=prefs.getString("device_key","");if(k.length()>20)return k;k="wethaq-"+UUID.randomUUID();prefs.edit().putString("device_key",k).apply();return k;}
    private String auth(){return "Bearer "+prefs.getString(TOKEN,"");}
    private boolean isOnline(){try{ConnectivityManager c=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);NetworkInfo n=c==null?null:c.getActiveNetworkInfo();return n!=null&&n.isConnected();}catch(Exception e){return false;}}

    private HttpResult request(String method,String path,String body,String authorization)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");if(authorization!=null)c.setRequestProperty("Authorization",authorization);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");byte[] x=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(x.length);try(OutputStream o=c.getOutputStream()){o.write(x);}}int code=c.getResponseCode();InputStream in=code>=400?c.getErrorStream():c.getInputStream();String response=read(in);c.disconnect();return new HttpResult(code,response);}
    private String read(InputStream in)throws Exception{if(in==null)return "";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)b.append(l);}return b.toString();}
    private String error(HttpResult r){try{String e=new JSONObject(r.body).optString("error","");if("unauthorized".equals(e)||"invalid_token".equals(e))return"انتهت الجلسة. سجّل الدخول من جديد.";if("user_not_found".equals(e))return"المستخدم غير موجود.";return e.length()>0?e:"حدث خطأ في الخادم ("+r.code+").";}catch(Exception e){return"حدث خطأ في الخادم ("+r.code+").";}}
    private TextView tv(String s,float size,int color,int style){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.DEFAULT,style);t.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);return t;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.rgb(130,145,160));e.setTextColor(WHITE);e.setTextSize(16);e.setPadding(18,14,18,14);e.setBackgroundColor(PANEL);e.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(WHITE);b.setTextSize(15);b.setAllCaps(false);b.setPadding(18,12,18,12);b.setBackgroundColor(ACCENT);return b;}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(18,16,18,16);c.setBackgroundColor(PANEL);return c;}
    private LinearLayout.LayoutParams lp(int w,int h,int l,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(l,6,0,b);return p;}
    private void setStatus(String s,boolean e){if(status!=null){status.setText(s);status.setTextColor(e?Color.rgb(255,125,125):MUTED);}}
    private static final class HttpResult{final int code;final String body;HttpResult(int c,String b){code=c;body=b;}}
}
