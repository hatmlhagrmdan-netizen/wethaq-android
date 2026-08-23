package com.wethaq.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class PremiumActivity extends Activity {
    private static final int GOLD=Color.rgb(212,175,55), BG=Color.rgb(5,5,5), CARD=Color.rgb(18,18,18);
    private LinearLayout root,body; private SharedPreferences prefs;
    @Override protected void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("wethaq",MODE_PRIVATE);if(prefs.getString("token","").length()<10){startActivity(new Intent(this,MainActivity.class));finish();return;}home();}
    private TextView text(String s,float size){TextView t=new TextView(this);t.setText(s);t.setTextColor(GOLD);t.setTextSize(size);t.setGravity(Gravity.CENTER);t.setPadding(18,18,18,18);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(GOLD);b.setTextSize(16);b.setAllCaps(false);b.setBackgroundColor(CARD);return b;}
    private void shell(String title){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(14,14,14,14);root.addView(text(title,22),new LinearLayout.LayoutParams(-1,-2));ScrollView sc=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);}
    private void add(View v){body.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void home(){shell("المؤسس: حاتم حسين الحاج رمضان");add(text("وَثاق",30));Button a=button("🔎  البحث");add(a);a.setOnClickListener(v->search());Button b=button("👥  جهات الاتصال");add(b);b.setOnClickListener(v->contacts());Button c=button("⚙  الإعدادات");add(c);c.setOnClickListener(v->settings());Button d=button("👤  المعلومات الشخصية");add(d);d.setOnClickListener(v->profile());Button e=button("🖼  المحفوظات");add(e);e.setOnClickListener(v->archive());add(text("منصة تواصل رقمية آمنة",14));}
    private void back(){home();}
    private void search(){shell("البحث عن مستخدم");EditText q=new EditText(this);q.setHint("الاسم أو معرف وَثاق");q.setTextColor(GOLD);q.setHintTextColor(Color.GRAY);add(q);Button find=button("بحث");add(find);Button save=button("حفظ في جهات الاتصال");add(save);save.setOnClickListener(v->{});Button bt=button("← العودة");add(bt);bt.setOnClickListener(v->back());}
    private void contacts(){shell("جهات الاتصال");String id=prefs.getString("wethaq_id","");String name=prefs.getString("name","لا توجد جهة اتصال");Button contact=button(name+"\n"+id);add(contact);contact.setOnClickListener(v->chat(name));Button add=button("＋ إضافة جهة اتصال");add(add);add.setOnClickListener(v->search());Button bt=button("← العودة");add(bt);bt.setOnClickListener(v->back());}
    private void chat(String name){shell("المحادثة");add(text("صورة شخصية",13));add(text(name,22));LinearLayout area=new LinearLayout(this);area.setOrientation(LinearLayout.VERTICAL);TextView r=text("✓  رسالة الطرف الآخر",16);r.setGravity(Gravity.RIGHT);area.addView(r);TextView s=text("رسالتك  □",16);s.setGravity(Gravity.LEFT);area.addView(s);add(area);LinearLayout bar=new LinearLayout(this);EditText input=new EditText(this);input.setHint("اكتب رسالة…");input.setTextColor(GOLD);input.setHintTextColor(Color.GRAY);Button mic=button("🎙");Button gallery=button("🖼");Button send=button("إرسال");bar.addView(mic,new LinearLayout.LayoutParams(0,-2,1));bar.addView(gallery,new LinearLayout.LayoutParams(0,-2,1));bar.addView(input,new LinearLayout.LayoutParams(0,-2,3));bar.addView(send,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar,new LinearLayout.LayoutParams(-1,-2));Button bt=button("← جهات الاتصال");root.addView(bt,new LinearLayout.LayoutParams(-1,-2));bt.setOnClickListener(v->contacts());}
    private void settings(){shell("الإعدادات");add(button("🔔 الإشعارات"));add(button("🔊 صوت النقر"));add(text("الملف الشخصي",20));EditText name=new EditText(this);name.setHint("الاسم الثلاثي");name.setText(prefs.getString("name",""));name.setTextColor(GOLD);add(name);EditText birth=new EditText(this);birth.setHint("المواليد");birth.setTextColor(GOLD);add(birth);add(text("المعرف الشخصي\n"+prefs.getString("wethaq_id","يظهر تلقائياً"),16));Button save=button("حفظ");add(save);save.setOnClickListener(v->prefs.edit().putString("name",name.getText().toString()).apply());add(button("📷 الصورة الشخصية"));Button bt=button("← العودة");add(bt);bt.setOnClickListener(v->back());}
    private void profile(){shell("المعلومات الشخصية");add(text("المعرف الشخصي\n"+prefs.getString("wethaq_id",""),18));add(text("الاسم الثلاثي\n"+prefs.getString("name",""),18));add(text("المواليد",18));Button bt=button("← العودة");add(bt);bt.setOnClickListener(v->back());}
    private void archive(){shell("المحفوظات");add(text("الصور المرسلة والمستلمة",20));add(text("لا توجد صور محفوظة بعد",16));Button bt=button("← العودة");add(bt);bt.setOnClickListener(v->back());}
}
