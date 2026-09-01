package com.wethaq.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class PublicAdministrationActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private LinearLayout body;
    private TextView status;
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setGravity(Gravity.RIGHT);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(17);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable d=new GradientDrawable();d.setColor(Color.rgb(28,28,30));d.setCornerRadius(dp(14));d.setStroke(dp(2),Color.rgb(212,175,55));b.setBackground(d);b.setMinHeight(dp(62));return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);renderShell();load();}
    private void renderShell(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(14));root.setBackgroundColor(Color.BLACK);TextView title=text("👥 الإدارة — الهيكل الإداري",23,Color.rgb(212,175,55));title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,dp(72)));status=text("⏳ جاري تحميل الهيكل الإداري…",15,Color.LTGRAY);root.addView(status,new LinearLayout.LayoutParams(-1,dp(52)));ScrollView sc=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));Button back=button("رجوع");root.addView(back,new LinearLayout.LayoutParams(-1,dp(64)));back.setOnClickListener(v->finish());setContentView(root);}
    private void load(){new Thread(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(API+"/api/public/administration").openConnection();c.setRequestMethod("GET");c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setRequestProperty("Accept","application/json");int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream o=new ByteArrayOutputStream();if(in!=null){byte[] z=new byte[4096];int n;while((n=in.read(z))!=-1)o.write(z,0,n);in.close();}String raw=new String(o.toByteArray(),StandardCharsets.UTF_8);runOnUiThread(()->{if(code>=200&&code<300){status.setText("🟢 الهيكل الإداري محدث");show(raw);}else{status.setText("🔴 تعذر الاتصال بالخادم");body.removeAllViews();body.addView(text("تعذر تحميل الهيكل الإداري. حاول مرة أخرى.",17,Color.LTGRAY));}});}catch(Exception e){runOnUiThread(()->{status.setText("🔴 تعذر الاتصال بالخادم");body.removeAllViews();body.addView(text("تعذر الاتصال بالخادم. حاول مرة أخرى.",17,Color.LTGRAY));});}finally{if(c!=null)c.disconnect();}}).start();}
    private void show(String raw){try{JSONObject o=new JSONObject(raw);body.removeAllViews();JSONObject f=o.optJSONObject("founder");body.addView(card("👑 المؤسس\n"+(f==null?"غير محدد":f.optString("name","غير محدد")),true));JSONObject d=o.optJSONObject("deputies");body.addView(card("🥇 النائب الأول\n"+nameOf(d==null?null:d.optJSONObject("deputy1")),false));body.addView(card("🥈 النائب الثاني\n"+nameOf(d==null?null:d.optJSONObject("deputy2")),false));body.addView(card("🥉 النائب الثالث\n"+nameOf(d==null?null:d.optJSONObject("deputy3")),false));body.addView(text("👥 أعضاء الإدارة — 10 خانات",20,Color.rgb(212,175,55)));JSONArray m=o.optJSONArray("members");for(int i=0;i<10;i++){JSONObject x=null;if(m!=null&&i<m.length())x=m.optJSONObject(i);String s=(i+1)+". "+(x==null?"غير معين":x.optString("name","غير معروف"))+"\nسنة الميلاد: "+(x==null?"—":x.optString("birth_year","—"))+"\nالرمز الشخصي: سري";body.addView(card(s,false));}body.addView(card("المستخدمون\nجميع المستخدمين خارج الهيكل الإداري",false));}catch(Exception e){status.setText("🔴 حدث خطأ أثناء قراءة البيانات");}}
    private String nameOf(JSONObject o){return o==null?"غير معين":o.optString("name","غير معروف");}
    private TextView card(String s,boolean founder){TextView t=text(s,founder?20:17,Color.WHITE);GradientDrawable d=new GradientDrawable();d.setColor(founder?Color.rgb(45,36,12):Color.rgb(24,24,28));d.setCornerRadius(dp(16));d.setStroke(dp(2),founder?Color.rgb(212,175,55):Color.rgb(70,70,75));t.setBackground(d);t.setPadding(dp(16),dp(14),dp(16),dp(14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);t.setLayoutParams(p);return t;}
}
