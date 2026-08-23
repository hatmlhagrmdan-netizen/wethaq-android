package com.wethaq.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public final class PremiumActivity extends Activity {
    private final int gold=Color.rgb(212,175,55), black=Color.rgb(6,6,8), panel=Color.rgb(20,20,24);
    private LinearLayout body;
    private GradientDrawable card(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(28);d.setStroke(1,Color.rgb(80,62,24));return d;}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(18,14,18,14);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setAllCaps(false);b.setBackground(card(Color.rgb(27,27,31)));return b;}
    private LinearLayout.LayoutParams lp(int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=bottom;return p;}
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(black);getWindow().setNavigationBarColor(black);show();}
    private void show(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(14,10,14,14);root.setBackgroundColor(black);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("وَثاق",26,gold);brand.setTypeface(null,1);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));TextView online=text("● مفتوح",13,Color.rgb(80,220,130));top.addView(online);root.addView(top);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        TextView hero=text("برنامج وَثاق للتواصل الاجتماعي\nتم بناؤه بتاريخ 2026/8/23\n\nالمؤسس وقائد العمل\nحاتم حسين الحاج رمضان",21,Color.WHITE);hero.setGravity(Gravity.CENTER);hero.setBackground(card(panel));body.addView(hero,lp(16));
        TextView security=text("🛡️ مركز الثقة\nهوية رقمية • محادثات خاصة • تجربة عربية حديثة",16,Color.WHITE);security.setBackground(card(Color.rgb(14,14,17)));body.addView(security,lp(14));
        Button chats=button("💬  فتح المحادثات الاحترافية");Button contacts=button("👥  جهات الاتصال");Button search=button("🔎  البحث عن مستخدم");Button settings=button("⚙️  الإعدادات");
        body.addView(chats,lp(8));body.addView(contacts,lp(8));body.addView(search,lp(8));body.addView(settings,lp(18));
        TextView footer=text("وَثاق — صُمم ليكون منصة تواصل عربية احترافية",13,Color.LTGRAY);footer.setGravity(Gravity.CENTER);body.addView(footer);
        chats.setOnClickListener(v->professionalConversation());contacts.setOnClickListener(v->openMain());search.setOnClickListener(v->openMain());settings.setOnClickListener(v->settings());
    }
    private void openMain(){startActivity(new Intent(this,MainActivity.class));}
    private void professionalConversation(){startActivity(new Intent(this,ProfessionalConversationActivity.class));}
    private void settings(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(8,4,8,4);
        Switch n=new Switch(this);n.setText("الإشعارات");n.setTextColor(Color.WHITE);n.setChecked(getPreferences(0).getBoolean("notifications",true));
        Switch s=new Switch(this);s.setText("صوت النقر");s.setTextColor(Color.WHITE);s.setChecked(getPreferences(0).getBoolean("sound",true));
        Button photo=button("🖼️ تغيير الصورة الشخصية");box.addView(n);box.addView(s);box.addView(photo);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("إعدادات وَثاق").setView(box).setPositiveButton("حفظ",null).create();
        n.setOnCheckedChangeListener((v,c)->getPreferences(0).edit().putBoolean("notifications",c).apply());s.setOnCheckedChangeListener((v,c)->getPreferences(0).edit().putBoolean("sound",c).apply());
        photo.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,71);});d.show();
    }
}
