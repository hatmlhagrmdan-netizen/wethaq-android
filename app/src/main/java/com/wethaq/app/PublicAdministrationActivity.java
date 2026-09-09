package com.wethaq.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PublicAdministrationActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setGravity(Gravity.RIGHT);return t;}
    private TextView card(String s,boolean important){TextView t=text(s,important?18:16,Color.WHITE);GradientDrawable d=new GradientDrawable();d.setColor(important?Color.rgb(45,36,12):Color.rgb(24,24,28));d.setCornerRadius(dp(16));d.setStroke(dp(2),important?Color.rgb(212,175,55):Color.rgb(70,70,75));t.setBackground(d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);t.setLayoutParams(p);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(17);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable d=new GradientDrawable();d.setColor(Color.rgb(28,28,30));d.setCornerRadius(dp(14));d.setStroke(dp(2),Color.rgb(212,175,55));b.setBackground(d);b.setMinHeight(dp(62));return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);build();loadPublicAdministration();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(14));root.setBackgroundColor(Color.BLACK);TextView title=text("👥 وَثاق — الإدارة العامة",23,Color.rgb(212,175,55));title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,dp(72)));Button access=button("🔐 دخول الإدارة / تفعيل المنصب");root.addView(access,new LinearLayout.LayoutParams(-1,dp(64)));access.setOnClickListener(v->startActivity(new Intent(this,AdminAccessActivity.class)));ScrollView sc=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setTag("body");
        body.addView(card("⏳ جاري تحميل شاغلي المناصب الحاليين من خادم وَثاق...",true));
        body.addView(card("👑 المؤسس\nالعدد: 1\nالصلاحيات: جميع الصلاحيات، وإدارة وتعيين المناصب.",false));
        body.addView(card("🏛 المدير التنفيذي — 100$ سنويًا\nالعدد: 1",false));
        body.addView(card("🥇 النائب الأول — 70$ سنويًا\nالعدد: 1",false));
        body.addView(card("🥈 النائب الثاني — 50$ سنويًا\nالعدد: 1",false));
        body.addView(card("🥉 النائب الثالث — 30$ سنويًا\nالعدد: 1",false));
        body.addView(card("🛡 المشرف — 15$ سنويًا\nالعدد: 9",false));
        body.addView(card("👥 عضو الإدارة — 7$ سنويًا\nالعدد: 27",false));
        body.addView(card("⭐ عضو مميز — 2$ سنويًا",false));
        body.addView(card("🔐 حماية السجلات: رموز الدخول الشخصية والرموز السرية لا تظهر للعامة. تظهر فقط حالة حماية الرمز، بينما تبقى القيم السرية غير قابلة للعرض.",true));
        sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));Button back=button("رجوع");root.addView(back,new LinearLayout.LayoutParams(-1,dp(64)));back.setOnClickListener(v->finish());setContentView(root);}
    private void loadPublicAdministration(){request("GET","/api/admin/structure",null,r->{try{JSONObject z=new JSONObject(r);JSONArray roles=z.optJSONArray("roles");LinearLayout body=(LinearLayout)((ScrollView)findScrollable());body.removeAllViews();body.addView(card("✅ الهيكل الإداري الحالي — أسماء شاغلي المناصب الفعليين",true));if(roles==null||roles.length()==0){body.addView(card("لا توجد مناصب مشغولة حاليًا.",false));return;}for(int i=0;i<roles.length();i++){JSONObject role=roles.getJSONObject(i);StringBuilder s=new StringBuilder();s.append(role.optString("icon","👤")).append(' ').append(role.optString("label")).append("\n");int cap=role.optInt("capacity",-1);s.append("الإشغال: ").append(role.optInt("activeCount")).append(cap<0?"":"/"+cap).append("\n");JSONArray members=role.optJSONArray("members");if(members==null||members.length()==0){s.append("لا يوجد شاغل حاليًا.");}else{for(int j=0;j<members.length();j++){JSONObject m=members.getJSONObject(j);s.append("\n👤 الاسم: ").append(m.optString("name","غير متوفر"));s.append("\n🆔 معرف وَثاق: ").append(m.optString("wethaq_id","غير متوفر"));s.append("\n📅 تاريخ تولي المنصب: ").append(m.optString("appointedAt","غير متوفر"));String by=m.optString("appointedBy","");if(!by.isEmpty())s.append("\n👑 عيّنه: ").append(by);s.append("\n🔐 الرمز الشخصي: محمي ولا يُعرض").append("\n");}}body.addView(card(s.toString(),"founder".equals(role.optString("role"))));}body.addView(card("📌 ملاحظة: هذه اللوحة عامة؛ تعرض الاسم والمعرف والمنصب وتاريخ التولي فقط. لا تعرض سنة الميلاد أو الرمز الشخصي أو رمز المنصب السري.",true));}catch(Exception e){showLoadError();}});}
    private android.view.View findScrollable(){android.widget.ScrollView sc=(android.widget.ScrollView)findViewById(android.R.id.content).findViewWithTag("wethaq_scroll");return sc;}
    private void showLoadError(){android.widget.Toast.makeText(this,"تعذر تحميل أسماء الإدارة من الخادم",android.widget.Toast.LENGTH_LONG).show();}
    private void request(String method,String path,String body,CB cb){new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();}String s=new String(out.toByteArray(),StandardCharsets.UTF_8);runOnUiThread(()->{if(code>=200&&code<300)cb.ok(s);else showLoadError();});}catch(Exception e){runOnUiThread(this::showLoadError);}}).start();}
    private interface CB{void ok(String s);}
}
