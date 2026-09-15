package com.wethaq.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class PublicAdministrationActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private LinearLayout body;
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setGravity(Gravity.RIGHT);return t;}
    private TextView card(String s,boolean important){TextView t=text(s,important?18:16,Color.WHITE);GradientDrawable d=new GradientDrawable();d.setColor(important?Color.rgb(45,36,12):Color.rgb(24,24,28));d.setCornerRadius(dp(16));d.setStroke(dp(2),important?Color.rgb(212,175,55):Color.rgb(70,70,75));t.setBackground(d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);t.setLayoutParams(p);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(17);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable d=new GradientDrawable();d.setColor(Color.rgb(28,28,30));d.setCornerRadius(dp(14));d.setStroke(dp(2),Color.rgb(212,175,55));b.setBackground(d);b.setMinHeight(dp(62));return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);build();loadPublicAdministration();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(14));root.setBackgroundColor(Color.BLACK);TextView title=text("👥 وَثاق — الإدارة العامة",23,Color.rgb(212,175,55));title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,dp(72)));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);ScrollView sc=new ScrollView(this);sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));Button access=button("🔐 دخول الإدارة / تفعيل المنصب");root.addView(access,new LinearLayout.LayoutParams(-1,dp(64)));Button back=button("رجوع");root.addView(back,new LinearLayout.LayoutParams(-1,dp(64)));access.setOnClickListener(v->startActivity(new Intent(this,AdminAccessActivity.class)));back.setOnClickListener(v->finish());setContentView(root);}
    private void loadPublicAdministration(){body.removeAllViews();body.addView(card("💳 المناصب الإدارية التالية تعمل بنظام اشتراك سنوي مدفوع. تُعرض قيمة الاشتراك بوضوح قبل التعيين، ويظل المؤسس بلا اشتراك.",true));body.addView(card("🏛 المدير التنفيذي — 100 دولار سنويًا\n🥇 النائب الأول — 70 دولار سنويًا\n🥈 النائب الثاني — 50 دولارًا سنويًا\n🥉 النائب الثالث — 30 دولارًا سنويًا\n🛡 المشرف — 15 دولارًا سنويًا\n👥 عضو الإدارة — 7 دولارات سنويًا\n⭐ عضو مميز — 2 دولار سنويًا",true));body.addView(card("⏳ جاري تحميل شاغلي المناصب الحاليين من الخادم...",false));request("GET","/api/admin/structure",r->{try{JSONObject z=new JSONObject(r);JSONArray roles=z.optJSONArray("roles");body.removeAllViews();body.addView(card("💳 نظام الاشتراكات: كل منصب مدفوع مدة صلاحيته سنة واحدة، وتظهر قيمة الاشتراك داخل بطاقة المنصب.",true));body.addView(card("👑 المؤسس — بدون اشتراك سنوي\nالصلاحيات: جميع الصلاحيات وإدارة النظام.",true));if(roles!=null){for(int i=0;i<roles.length();i++){JSONObject role=roles.getJSONObject(i);String label=role.optString("label","منصب");String icon=role.optString("icon","👤");double fee=role.optDouble("fee",-1);String feeText=fee<0?"بدون اشتراك":(fee==0?"بدون اشتراك":String.format(java.util.Locale.US,"%.0f دولار سنويًا",fee));StringBuilder s=new StringBuilder(icon).append(' ').append(label).append("\n").append("💳 الاشتراك: ").append(feeText).append("\n").append("📌 المدة: ").append(fee<0||fee==0?"غير مطلوبة":"سنة واحدة").append("\n").append("الإشغال: ").append(role.optInt("activeCount"));int cap=role.optInt("capacity",-1);if(cap>=0)s.append('/').append(cap);JSONArray m=role.optJSONArray("members");if(m!=null)for(int j=0;j<m.length();j++){JSONObject x=m.getJSONObject(j);s.append("\n\n👤 ").append(x.optString("name","غير متوفر")).append("\n🆔 ").append(x.optString("wethaq_id","غير متوفر")).append("\n📅 تولي المنصب: ").append(x.optString("appointedAt","غير متوفر"));}body.addView(card(s.toString(),"founder".equals(role.optString("role"))));}}
body.addView(card("🔐 الحماية: رموز المنصب والرموز الشخصية لا تُعرض في الصفحة العامة.",true));}catch(Exception e){showLoadError();}});}
    private void showLoadError(){Toast.makeText(this,"تعذر تحميل الإدارة من الخادم، لكن جدول الاشتراكات ظاهر",Toast.LENGTH_LONG).show();}
    private void request(String method,String path,CB cb){new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setUseCaches(false);c.setRequestProperty("Accept","application/json");int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[]buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();}String s=new String(out.toByteArray(),StandardCharsets.UTF_8);runOnUiThread(()->{if(code>=200&&code<300)cb.ok(s);else showLoadError();});}catch(Exception e){runOnUiThread(this::showLoadError);}}).start();}
    private interface CB{void ok(String s);}
}
