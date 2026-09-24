package com.wethaq.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PublicAdministrationActivity extends Activity {
    private static final String API=WethaqConfig.API;
    private LinearLayout body;
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setGravity(Gravity.RIGHT);return t;}
    private TextView card(String s,boolean important){TextView t=text(s,important?18:16,Color.WHITE);GradientDrawable d=new GradientDrawable();d.setColor(important?Color.rgb(45,36,12):Color.rgb(24,24,28));d.setCornerRadius(dp(16));d.setStroke(dp(2),important?Color.rgb(212,175,55):Color.rgb(70,70,75));t.setBackground(d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);t.setLayoutParams(p);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(17);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable d=new GradientDrawable();d.setColor(Color.rgb(28,28,30));d.setCornerRadius(dp(14));d.setStroke(dp(2),Color.rgb(212,175,55));b.setBackground(d);b.setMinHeight(dp(62));return b;}
    private TextView pricingCard(){return card("💳 المناصب الإدارية في وَثاق بنظام اشتراك سنوي\n\n🏛 المدير التنفيذي: 100$ سنويًا\n🥇 النائب الأول: 70$ سنويًا\n🥈 النائب الثاني: 50$ سنويًا\n🥉 النائب الثالث: 30$ سنويًا\n🛡 المشرف: 15$ سنويًا\n👥 عضو الإدارة: 7$ سنويًا\n⭐ عضو مميز: 2$ سنويًا\n\n📌 ملاحظة: شغل أي منصب إداري مدفوع يتطلب اشتراكًا سنويًا صالحًا، وتظهر حالة الصلاحية وتاريخ الانتهاء للمستخدم وصاحب المنصب حسب مستوى الوصول.",true);}
    @Override public void onCreate(Bundle b){super.onCreate(b);build();loadPublicAdministration();}
    private void build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(14));root.setBackgroundColor(Color.BLACK);TextView title=text("👥 وَثاق — الإدارة العامة",23,Color.rgb(212,175,55));title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,dp(72)));Button access=button("🔐 دخول الإدارة / تفعيل المنصب");root.addView(access,new LinearLayout.LayoutParams(-1,dp(64)));access.setOnClickListener(v->startActivity(new Intent(this,AdminAccessActivity.class)));ScrollView sc=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.addView(pricingCard());body.addView(card("⏳ جاري تحميل شاغلي المناصب الحاليين من خادم وَثاق...",true));sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));Button back=button("رجوع");root.addView(back,new LinearLayout.LayoutParams(-1,dp(64)));back.setOnClickListener(v->finish());setContentView(root);}
    private LinearLayout memberRow(String roleLabel,String roleIcon,String name,String id,String appointedAt,String appointedBy){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        row.setPadding(dp(10),dp(8),dp(10),dp(8));
        GradientDrawable d=new GradientDrawable();
        d.setColor(Color.rgb(20,28,34));d.setCornerRadius(dp(14));d.setStroke(dp(1),Color.rgb(70,90,100));row.setBackground(d);

        ImageView avatar=new ImageView(this);
        avatar.setImageDrawable(defaultAvatar());
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(avatar,new LinearLayout.LayoutParams(dp(58),dp(58)));

        LinearLayout details=new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.RIGHT);

        TextView n=text((roleIcon==null||roleIcon.isEmpty()?"👤":roleIcon)+" "+roleLabel+" — "+name,18,Color.WHITE);
        n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        details.addView(n);
        details.addView(text("🆔 "+id,13,Color.LTGRAY));
        details.addView(text("📅 تولي المنصب: "+appointedAt,13,Color.LTGRAY));
        if(appointedBy!=null&&!appointedBy.isEmpty())details.addView(text("👑 عيّنه: "+appointedBy,13,Color.LTGRAY));

        row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        String token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");
        if(!token.isEmpty()&&!id.isEmpty())WethaqUi.loadAvatar(id,token,avatar);
        return row;
    }

    private Drawable defaultAvatar(){
        GradientDrawable d=new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);d.setColor(Color.rgb(48,48,54));
        d.setStroke(dp(2),Color.rgb(212,175,55));d.setSize(dp(52),dp(52));
        return d;
    }

    private void loadPublicAdministration(){
        request("GET","/api/admin/structure",r->{
            try{
                JSONObject z=new JSONObject(r);
                JSONArray roles=z.optJSONArray("roles");
                body.removeAllViews();
                body.addView(pricingCard());

                ImageView visual=new ImageView(this);
                visual.setImageResource(R.drawable.wethaq_identity);
                visual.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                body.addView(visual,new LinearLayout.LayoutParams(-1,dp(120)));

                body.addView(card("✅ الهيكل الإداري الحالي — صور شاغلي المناصب وأسماؤهم",true));
                if(roles==null||roles.length()==0){
                    body.addView(card("لا توجد مناصب مشغولة حاليًا.",false));
                    return;
                }

                for(int i=0;i<roles.length();i++){
                    JSONObject role=roles.getJSONObject(i);
                    StringBuilder h=new StringBuilder();
                    h.append(role.optString("icon","👤")).append(" ").append(role.optString("label"));
                    int cap=role.optInt("capacity",-1);
                    h.append("\nالإشغال: ").append(role.optInt("activeCount")).append(cap<0?"":"/"+cap);
                    body.addView(card(h.toString(),"founder".equals(role.optString("role"))));

                    JSONArray members=role.optJSONArray("members");
                    if(members==null||members.length()==0){
                        body.addView(card("لا يوجد شاغل حاليًا.",false));
                    }else{
                        for(int j=0;j<members.length();j++){
                            JSONObject m=members.getJSONObject(j);
                            body.addView(memberRow(
                                 role.optString("label","منصب إداري"),
                                 role.optString("icon","👤"),
                                 m.optString("name","غير متوفر"),
                                 m.optString("wethaq_id","غير متوفر"),
                                 m.optString("appointedAt","غير متوفر"),
                                 m.optString("appointedBy","")
                             ));
                            body.addView(new android.view.View(this),new LinearLayout.LayoutParams(-1,dp(8)));
                        }
                    }
                }
                body.addView(card("📌 ملاحظة: تعرض هذه اللوحة اسم شاغل المنصب ومعرف وَثاق وصورته عند توفرها، ولا تعرض سنة الميلاد أو الرمز الشخصي أو رمز المنصب السري.",true));
            }catch(Exception e){
                showLoadError();
            }
        });
    }

    private void showLoadError(){Toast.makeText(this,"تعذر تحميل أسماء الإدارة من الخادم",Toast.LENGTH_LONG).show();}
    private void request(String method,String path,CB cb){new Thread(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("Accept","application/json");int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);in.close();}String s=new String(out.toByteArray(),StandardCharsets.UTF_8);runOnUiThread(()->{if(code>=200&&code<300)cb.ok(s);else showLoadError();});}catch(Exception e){runOnUiThread(this::showLoadError);}}).start();}
    private interface CB{void ok(String s);}
}