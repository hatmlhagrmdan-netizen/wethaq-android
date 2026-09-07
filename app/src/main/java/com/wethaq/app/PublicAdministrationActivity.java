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

public final class PublicAdministrationActivity extends Activity {
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setGravity(Gravity.RIGHT);return t;}
    private TextView card(String s,boolean important){TextView t=text(s,important?19:16,Color.WHITE);GradientDrawable d=new GradientDrawable();d.setColor(important?Color.rgb(45,36,12):Color.rgb(24,24,28));d.setCornerRadius(dp(16));d.setStroke(dp(2),important?Color.rgb(212,175,55):Color.rgb(70,70,75));t.setBackground(d);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);t.setLayoutParams(p);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(17);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable d=new GradientDrawable();d.setColor(Color.rgb(28,28,30));d.setCornerRadius(dp(14));d.setStroke(dp(2),Color.rgb(212,175,55));b.setBackground(d);b.setMinHeight(dp(62));return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(14));root.setBackgroundColor(Color.BLACK);TextView title=text("👥 وَثاق — الهرمية الإدارية",23,Color.rgb(212,175,55));title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title,new LinearLayout.LayoutParams(-1,dp(72)));ScrollView sc=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.addView(card("👑 المؤسس\nالعدد: 1\nالصلاحيات: جميع الصلاحيات. حماية كاملة للمنصب.\nالحظر: جميع الرتب والمستخدمين، مع عدم إمكانية حظر المؤسس.",true));
        body.addView(card("🏛 المدير التنفيذي — 100$ سنويًا\nالعدد: 1\nالصلاحيات: تعيين أعضاء الإدارة وأعضاء مميزين، الإدارة، الحظر والتنبيهات وبقية الصلاحيات الممنوحة للرتبة.\nالحظر: النائب الثالث وما دون.",false));
        body.addView(card("🥇 النائب الأول — 70$ سنويًا\nالعدد: 1\nالصلاحيات: تعيين 3 أعضاء إدارة وأعضاء مميزين، الحظر والتنبيهات وبقية الصلاحيات الممنوحة للرتبة.\nالحظر: المشرف وما دون.",false));
        body.addView(card("🥈 النائب الثاني — 50$ سنويًا\nالعدد: 1\nالصلاحيات: تعيين أعضاء إدارة وأعضاء مميزين، الحظر والتنبيهات وبقية الصلاحيات الممنوحة للرتبة.\nالحظر: المشرف وما دون.",false));
        body.addView(card("🥉 النائب الثالث — 30$ سنويًا\nالعدد: 1\nالصلاحيات: تعيين أعضاء مميزين فقط، الحظر والتنبيهات وبقية الصلاحيات الممنوحة للرتبة.\nالحظر: عضو الإدارة وما دون.",false));
        body.addView(card("🛡 المشرف — 15$ سنويًا\nالعدد: 9\nالصلاحيات: تعيين عدد محدد من الأعضاء المميزين، الحظر والتنبيهات وبقية الصلاحيات الممنوحة للرتبة.\nالحظر: الأعضاء المميزون وما دون.",false));
        body.addView(card("👥 عضو الإدارة — 7$ سنويًا\nالعدد: 27\nالصلاحيات: الحظر المؤقت والتحذير.\nالحظر: المستخدمون فقط.",false));
        body.addView(card("⭐ عضو مميز — 2$ سنويًا\nالصلاحيات: التحذير.\nالتحذير: المستخدمون فقط.",false));
        body.addView(card("🔐 قواعد إلزامية\n• لا يجوز لأي رتبة حظر المؤسس.\n• لا يجوز تجاوز نطاق الحظر المحدد للرتبة.\n• كل حظر مؤقت يحمل مدة وسببًا.\n• جميع الإجراءات الإدارية تسجل في Audit Log.\n• الرسوم سنوية، وانتهاء الاشتراك يسقط الصلاحيات الإدارية.",true));
        sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));Button back=button("رجوع");root.addView(back,new LinearLayout.LayoutParams(-1,dp(64)));back.setOnClickListener(v->finish());setContentView(root);}
}
