package com.wethaq.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AdminAccessActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private SharedPreferences prefs;
    private LinearLayout root;
    private String adminToken="";
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.RIGHT);t.setPadding(dp(14),dp(12),dp(14),dp(12));return t;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(Color.LTGRAY);e.setTextColor(Color.WHITE);e.setTextSize(18);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);GradientDrawable d=new GradientDrawable();d.setColor(Color.rgb(24,24,26));d.setCornerRadius(dp(14));d.setStroke(dp(2),Color.rgb(70,70,75));e.setBackground(d);return e;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(18);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable d=new GradientDrawable();d.setColor(Color.rgb(28,28,30));d.setCornerRadius(dp(14));d.setStroke(dp(2),Color.rgb(212,175,55));b.setBackground(d);b.setMinHeight(dp(64));return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("wethaq",MODE_PRIVATE);adminToken=prefs.getString("admin_token","");if(adminToken.isEmpty())showLogin();else loadRole();}
    private void base(){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(14));root.setBackgroundColor(Color.BLACK);setContentView(root);}
    private void showLogin(){base();TextView title=label("🔐 الدخول الإداري الآمن",24,Color.rgb(212,175,55));title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,dp(72)));root.addView(label("بعد التعيين، لا تستخدم الرمز الشخصي للدخول إلى لوحة المنصب. استخدم اسمك الثلاثي + سنة الميلاد + الرمز السري الذي صدر لك.",16,Color.WHITE));EditText name=field("الاسم الثلاثي كما هو مسجل في وَثاق");EditText year=field("سنة الميلاد");EditText code=field("الرمز السري للمنصب");code.setInputType(0x00000081);root.addView(name,new LinearLayout.LayoutParams(-1,dp(64)));root.addView(year,new LinearLayout.LayoutParams(-1,dp(64)));root.addView(code,new LinearLayout.LayoutParams(-1,dp(64)));Button login=button("دخول وتسجيل الخروج من الحساب الشخصي");root.addView(login,new LinearLayout.LayoutParams(-1,dp(68)));Button back=button("رجوع");root.addView(back,new LinearLayout.LayoutParams(-1,dp(64)));login.setOnClickListener(v->login(name.getText().toString().trim(),year.getText().toString().trim(),code.getText().toString().trim()));back.setOnClickListener(v->finish());}
    private void login(String name,String year,String code){if(name.split("\\s+").length<3){toast("اكتب الاسم الثلاثي");return;}JSONObject o=new JSONObject();try{o.put("name",name);o.put("birthYear",Integer.parseInt(year));o.put("adminCode",code);}catch(Exception e){toast("سنة الميلاد أو الرمز غير صحيح");return;}request("POST","/api/admin/login",o.toString(),r->{try{JSONObject z=new JSONObject(r);adminToken=z.getString("token");String role=z.optString("role");prefs.edit().remove("token").remove("wethaq_id").putString("admin_token",adminToken).putString("admin_role",role).apply();toast("تم تسجيل الخروج من الحساب الشخصي وتفعيل الدخول الإداري ✓");loadRole();}catch(Exception e){toast("بيانات الدخول الإداري غير صحيحة");}});}
    private void loadRole(){request("GET","/api/admin/role",null,r->{try{JSONObject z=new JSONObject(r);showPanel(z);}catch(Exception e){prefs.edit().remove("admin_token").remove("admin_role").apply();adminToken="";showLogin();}});}
    private void showPanel(JSONObject role){base();TextView title=label("🛡 لوحة التحكم الإدارية",24,Color.rgb(212,175,55));title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,dp(70)));String roleLabel=role.optString("label",role.optString("role",""));root.addView(card("المنصب الحالي\n"+role.optString("icon","")+" "+roleLabel+"\nالصلاحيات الفعلية يحددها الخادم فقط."));StringBuilder p=new StringBuilder("🔑 الصلاحيات\n");JSONArray a=role.optJSONArray("permissions");if(a!=null)for(int i=0;i<a.length();i++)p.append("• ").append(a.optString(i)).append("\n");root.addView(card(p.toString()));String expiry=role.optString("subscriptionExpiresAt","");root.addView(card("📅 حالة الاشتراك\n"+(expiry.isEmpty()?"غير محدد / بلا انتهاء":expiry)));Button structure=button("👥 عرض الهيكل الإداري");Button logout=button("🚪 تسجيل الخروج الإداري");root.addView(structure);root.addView(logout);structure.setOnClickListener(v->loadStructure());logout.setOnClickListener(v->{prefs.edit().remove("admin_token").remove("admin_role").apply();adminToken="";showLogin();});}
    private TextView card(String s){TextView t=label(s,17,Color.WHITE);GradientDrawable d=new GradientDrawable();d.setColor(Color.rgb(24,24,28));d.setCornerRadius(dp(16));d.setStroke(dp(2),Color.rgb(70,70,75));t.setBackground(d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);t.setLayoutParams(p);return t;}
    private void loadStructure(){request("GET","/api/admin/structure",null,r->{try{JSONObject z=new JSONObject(r);JSONArray roles=z.optJSONArray("roles");StringBuilder s=new StringBuilder("👥 الهيكل الإداري\n\n");if(roles!=null)for(int i=0;i<roles.length();i++){JSONObject x=roles.getJSONObject(i);s.append(x.optString("icon")).append(' ').append(x.optString("label")).append(" — ").append(x.optInt("activeCount")).append('/').append(x.optInt("capacity")).append('\n');JSONArray members=x.optJSONArray("members");if(members!=null)for(int j=0;j<members.length();j++){JSONObject m=members.getJSONObject(j);s.append("  • ").append(m.optString("name")).append(" — ").append(m.optString("wethaq_id")).append('\n').append("    ").append(m.optString("appointedBy","" )).append('\n');}s.append('\n');}new android.app.AlertDialog.Builder(this).setTitle("وَثاق — الإدارة").setMessage(s.toString()).setPositiveButton("إغلاق",null).show();}catch(Exception e){toast("تعذر تحميل الهيكل");}});}
    private interface CB{void ok(String s);}private void request(String method,String path,String body,CB cb){new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");if(!adminToken.isEmpty())c.setRequestProperty("Authorization","Bearer "+adminToken);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();}String text=new String(out.toByteArray(),StandardCharsets.UTF_8);runOnUiThread(()->{if(code>=200&&code<300)cb.ok(text);else toast(errorText(text));});}catch(Exception e){runOnUiThread(()->toast("تعذر الاتصال بالخادم"));}}).start();}
    private String errorText(String s){try{return "فشل: "+new JSONObject(s).optString("error",s);}catch(Exception e){return "فشل الطلب";}}
    private void toast(String s){android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show();}
}
