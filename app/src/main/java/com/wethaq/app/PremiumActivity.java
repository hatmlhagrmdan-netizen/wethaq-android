package com.wethaq.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public final class PremiumActivity extends Activity {
    private static final int GOLD=Color.rgb(212,175,55), BG=Color.rgb(5,5,5), CARD=Color.rgb(18,18,18);
    private LinearLayout root,body; private SharedPreferences prefs;
    @Override protected void onCreate(Bundle state){super.onCreate(state);prefs=getSharedPreferences("wethaq",MODE_PRIVATE);if(prefs.getString("token","").length()<10){startActivity(new Intent(this,MainActivity.class));finish();return;}home();}
    private TextView text(String s,float size){TextView t=new TextView(this);t.setText(s);t.setTextColor(GOLD);t.setTextSize(size);t.setGravity(Gravity.CENTER);t.setPadding(18,18,18,18);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(GOLD);b.setTextSize(16);b.setAllCaps(false);b.setBackgroundColor(CARD);return b;}
    private void shell(String title){root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(14,14,14,14);root.addView(text(title,22),new LinearLayout.LayoutParams(-1,-2));ScrollView sc=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);sc.addView(body);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);}
    private void add(android.view.View v){body.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void home(){shell("المؤسس: حاتم حسين الحاج رمضان");add(text("وَثاق",30));add(text(prefs.getString("wethaq_id",""),13));Button a=button("🔎  البحث\nالبحث عن مستخدم وحفظه");add(a);a.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));Button b=button("👥  جهات الاتصال\nالمحفوظة وبدء المحادثة");add(b);b.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));Button c=button("⚙  الإعدادات\nالإشعارات والصوت والهوية والصورة");add(c);c.setOnClickListener(v->settings());Button d=button("👤  المعلومات الشخصية\nالمعرف والاسم والمواليد");add(d);d.setOnClickListener(v->profile());Button e=button("🖼  المحفوظات\nالصور والوسائط");add(e);e.setOnClickListener(v->archive());Button chat=button("💬  المراسلة الاحترافية\nفتح المحادثة الفعلية");add(chat);chat.setOnClickListener(v->startActivity(new Intent(this,ProfessionalConversationActivity.class)));add(text("تواصل رقمي آمن • Wethaq",13));}
    private void settings(){shell("الإعدادات");Switch n=new Switch(this);n.setText("🔔 الإشعارات\nتنبيه عند وصول رسالة");n.setTextColor(Color.WHITE);n.setChecked(true);add(n);Switch sound=new Switch(this);sound.setText("🔊 صوت النقر\nأصوات الواجهة");sound.setTextColor(Color.WHITE);sound.setChecked(true);add(sound);add(text("الهوية الشخصية",20));EditText name=new EditText(this);name.setHint("الاسم الثلاثي");name.setText(prefs.getString("name",""));name.setTextColor(GOLD);name.setHintTextColor(Color.GRAY);add(name);EditText birth=new EditText(this);birth.setHint("المواليد");birth.setText(prefs.getString("birth_year",""));birth.setTextColor(GOLD);birth.setHintTextColor(Color.GRAY);add(birth);add(text("المعرف الشخصي\n"+prefs.getString("wethaq_id","يظهر تلقائياً"),16));Button save=button("💾 حفظ");add(save);save.setOnClickListener(v->{prefs.edit().putString("name",name.getText().toString().trim()).putString("birth_year",birth.getText().toString().trim()).apply();Toast.makeText(this,"تم حفظ الإعدادات ✓",Toast.LENGTH_SHORT).show();});Button image=button("📷 الصورة الشخصية\nاختيار صورة من الجهاز");add(image);image.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,100);});Button back=button("← العودة");add(back);back.setOnClickListener(v->home());}
    private void profile(){shell("المعلومات الشخصية");add(text("المعرف الشخصي\n"+prefs.getString("wethaq_id",""),18));add(text("الاسم الثلاثي\n"+prefs.getString("name",""),18));add(text("المواليد\n"+prefs.getString("birth_year",""),18));Button back=button("← العودة");add(back);back.setOnClickListener(v->home());}
    private void archive(){shell("المحفوظات");add(text("🖼 الصور والوسائط",20));add(text("ستظهر هنا الوسائط المرسلة والمستلمة بعد ربط سجل الوسائط بالمحادثات.",16));Button back=button("← العودة");add(back);back.setOnClickListener(v->home());}
    @Override public void onBackPressed(){home();}
}
