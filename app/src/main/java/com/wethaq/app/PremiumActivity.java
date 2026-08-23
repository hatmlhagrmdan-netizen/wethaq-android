package com.wethaq.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import java.io.File;
import java.util.*;
import org.json.*;

public final class PremiumActivity extends Activity {
    private static final int GOLD=0xFFD4AF37, BLACK=0xFF050507, PANEL=0xFF151519;
    private LinearLayout body;
    private MediaRecorder recorder;
    private boolean recording;
    private String currentTarget="";
    private final ArrayList<String> contacts=new ArrayList<>();
    private final ArrayList<Uri> gallery=new ArrayList<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Runnable notifyLoop;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        showHome();
        loadContacts();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            new Handler(Looper.getMainLooper()).postDelayed(()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},90),500);
        }
    }
    private GradientDrawable bg(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(r);d.setStroke(1,0x665C481B);return d;}
    private TextView tv(String s,float z,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);t.setPadding(18,14,18,14);return t;}
    private Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(15);b.setAllCaps(false);b.setBackground(bg(0xFF1B1B20,28));return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(0xFF777777);e.setTextColor(Color.WHITE);e.setSingleLine(true);e.setPadding(16,8,16,8);e.setBackground(bg(0xFF19191E,28));return e;}
    private LinearLayout root(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(12,8,12,12);r.setBackgroundColor(BLACK);return r;}
    private void base(String title,View.OnClickListener back){LinearLayout r=root();LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);Button b=btn("‹");b.setOnClickListener(back);h.addView(b,new LinearLayout.LayoutParams(58,58));TextView t=tv(title,22,GOLD);t.setTypeface(null,1);t.setGravity(Gravity.CENTER);h.addView(t,new LinearLayout.LayoutParams(0,-2,1));h.addView(tv("وَثاق",14,GOLD));r.addView(h);ScrollView s=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);s.addView(body);r.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(r);}
    private void showHome(){LinearLayout r=root();TextView top=tv("وَثاق    •    برنامج التواصل الاجتماعي\nالمؤسس حاتم حسين الحاج رمضان",22,GOLD);top.setGravity(Gravity.CENTER);top.setBackground(bg(PANEL,28));r.addView(top,new LinearLayout.LayoutParams(-1,-2));ScrollView s=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);s.addView(body);r.addView(s,new LinearLayout.LayoutParams(-1,0,1));addMenu("🔎  البحث",v->showSearch());addMenu("👥  جهات الاتصال",v->showContacts());addMenu("⚙️  الإعدادات",v->showSettings());addMenu("🪪  المعلومات الشخصية",v->showPersonal());addMenu("🖼️  المحفوظات",v->showArchive());TextView trust=tv("🛡️ مركز الثقة\nهوية رقمية • خصوصية • محادثات عربية حديثة",15,Color.WHITE);trust.setBackground(bg(0xFF101014,28));body.addView(trust,margin(0,16,0,0));setContentView(r);}
    private void addMenu(String s,View.OnClickListener l){Button b=btn(s);b.setTextSize(17);b.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);b.setOnClickListener(l);body.addView(b,margin(0,8,0,0));}
    private LinearLayout.LayoutParams margin(int a,int b,int c,int d){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(a,b,c,d);return p;}
    private void showSearch(){base("البحث عن مستخدم",v->showHome());EditText q=input("معرف وَثاق للمستخدم");Button search=btn("🔎 بحث");Button save=btn("＋ حفظ في جهات الاتصال");TextView result=tv("أدخل المعرف ثم اضغط بحث",15,Color.LTGRAY);body.addView(q,margin(0,10,0,8));body.addView(search,margin(0,0,0,8));body.addView(result,margin(0,0,0,8));body.addView(save);search.setOnClickListener(v->{String id=q.getText().toString().trim();result.setText(id.isEmpty()?"أدخل معرفًا صحيحًا":"تم العثور على المستخدم: "+id);});save.setOnClickListener(v->{String id=q.getText().toString().trim();if(id.length()<2){toast("أدخل المعرف أولاً");return;}if(!contacts.contains(id)){contacts.add(id);saveContacts();}toast("تم حفظ جهة الاتصال ✓");showContacts();});}
    private void showContacts(){base("جهات الاتصال",v->showHome());if(contacts.isEmpty()){body.addView(tv("لا توجد جهات اتصال محفوظة بعد.",16,Color.LTGRAY));return;}for(String id:contacts){Button b=btn("👤  "+id);b.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);b.setOnClickListener(v->showChat(id));body.addView(b,margin(0,0,0,8));}}
    private void showChat(String id){currentTarget=id;base("المراسلة",v->showContacts());LinearLayout profile=new LinearLayout(this);profile.setOrientation(LinearLayout.VERTICAL);profile.setGravity(Gravity.CENTER);TextView photo=tv("◉",42,GOLD);photo.setGravity(Gravity.CENTER);profile.addView(photo);TextView name=tv(id,20,Color.WHITE);name.setGravity(Gravity.CENTER);profile.addView(name);profile.setBackground(bg(PANEL,28));body.addView(profile,margin(0,8,0,8));ScrollView sc=new ScrollView(this);LinearLayout msgs=new LinearLayout(this);msgs.setOrientation(LinearLayout.VERTICAL);sc.addView(msgs);body.addView(sc,new LinearLayout.LayoutParams(-1,0,1));addMessage(msgs,"مرحباً بك في محادثة وَثاق",false);LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);EditText text=input("اكتب رسالة…");Button mic=btn("🎙️");Button gal=btn("🖼️");Button send=btn("إرسال");bar.addView(text,new LinearLayout.LayoutParams(0,-2,1));bar.addView(mic,new LinearLayout.LayoutParams(58,-2));bar.addView(gal,new LinearLayout.LayoutParams(58,-2));bar.addView(send,new LinearLayout.LayoutParams(-2,-2));body.addView(bar);send.setOnClickListener(v->{String m=text.getText().toString().trim();if(m.isEmpty())return;addMessage(msgs,m,true);text.setText("");});mic.setOnClickListener(v->toggleRecord(mic,msgs));gal.setOnClickListener(v->pickImage());startIncomingNotifications();}
    private void addMessage(LinearLayout box,String m,boolean mine){LinearLayout row=new LinearLayout(this);row.setGravity(mine?Gravity.END:Gravity.START);TextView bubble=tv(m+"\n"+(mine?"□":"✓"),15,Color.WHITE);bubble.setBackground(bg(mine?0xFF4A3913:0xFF25252B,22));row.addView(bubble,new LinearLayout.LayoutParams(-2,-2));box.addView(row,margin(0,4,0,4));}
    private void toggleRecord(Button mic,LinearLayout msgs){if(recording){try{recorder.stop();recorder.release();recorder=null;recording=false;mic.setText("🎙️");addMessage(msgs,"🎵 مقطع صوتي — تم تسجيله",true);toast("تم تسجيل المقطع الصوتي");}catch(Exception e){recording=false;toast("تعذر إنهاء التسجيل");}return;}try{if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},91);return;}File f=new File(getCacheDir(),"wethaq_voice_"+System.currentTimeMillis()+".3gp");recorder=new MediaRecorder(this);recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);recorder.setOutputFile(f.getAbsolutePath());recorder.prepare();recorder.start();recording=true;mic.setText("⏹️");toast("جارٍ التسجيل… اضغط مرة أخرى للإيقاف");}catch(Exception e){recorder=null;toast("تعذر بدء التسجيل");}}
    private void pickImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,72);}
    private void pickProfile(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,71);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==72&&res==RESULT_OK&&data!=null&&data.getData()!=null){gallery.add(data.getData());getPreferences(0).edit().putString("archive_count",String.valueOf(gallery.size())).apply();toast("تمت إضافة الصورة إلى المحفوظات");}}
    private void showSettings(){base("الإعدادات",v->showHome());Switch n=new Switch(this);n.setText("الإشعارات");n.setTextColor(Color.WHITE);n.setChecked(getPreferences(0).getBoolean("notify",true));Switch s=new Switch(this);s.setText("صوت النقر");s.setTextColor(Color.WHITE);s.setChecked(getPreferences(0).getBoolean("click",true));body.addView(n);body.addView(s);EditText name=input("الاسم الثلاثي");EditText birth=input("سنة الميلاد");TextView id=tv("المعرف الشخصي: "+getPreferences(0).getString("id","سيُنشأ عند الحفظ"),15,Color.LTGRAY);Button save=btn("حفظ البيانات");Button photo=btn("🖼️ الصورة الشخصية");body.addView(name,margin(0,10,0,8));body.addView(birth,margin(0,0,0,8));body.addView(id,margin(0,0,0,8));body.addView(save,margin(0,0,0,8));body.addView(photo);n.setOnCheckedChangeListener((v,c)->getPreferences(0).edit().putBoolean("notify",c).apply());s.setOnCheckedChangeListener((v,c)->getPreferences(0).edit().putBoolean("click",c).apply());save.setOnClickListener(v->{String generated=getPreferences(0).getString("id","");if(generated.isEmpty())generated="WTH-"+(System.currentTimeMillis()%1000000);getPreferences(0).edit().putString("name",name.getText().toString()).putString("birth",birth.getText().toString()).putString("id",generated).apply();id.setText("المعرف الشخصي: "+generated);toast("تم حفظ المعلومات ✓");});photo.setOnClickListener(v->pickProfile());}
    private void showPersonal(){base("المعلومات الشخصية",v->showHome());String id=getPreferences(0).getString("id","غير محفوظ");String name=getPreferences(0).getString("name","غير محفوظ");String birth=getPreferences(0).getString("birth","غير محفوظ");TextView t=tv("🪪 المعرف الشخصي\n"+id+"\n\n👤 الاسم الثلاثي\n"+name+"\n\n🎂 المواليد\n"+birth,18,Color.WHITE);t.setBackground(bg(PANEL,28));body.addView(t);}
    private void showArchive(){base("المحفوظات",v->showHome());String c=getPreferences(0).getString("archive_count","0");body.addView(tv("🖼️ الصور المرسلة والمستلمة\nعدد العناصر المحفوظة: "+c,18,Color.WHITE));if(gallery.isEmpty())body.addView(tv("ستظهر الصور التي تختارها من المحادثات هنا.",15,Color.LTGRAY));else for(Uri u:gallery){ImageView im=new ImageView(this);im.setImageURI(u);im.setAdjustViewBounds(true);body.addView(im,margin(0,8,0,8));}}
    private void loadContacts(){String raw=getPreferences(0).getString("contacts","[]");try{JSONArray a=new JSONArray(raw);for(int i=0;i<a.length();i++)contacts.add(a.getString(i));}catch(Exception ignored){}}
    private void saveContacts(){JSONArray a=new JSONArray();for(String s:contacts)a.put(s);getPreferences(0).edit().putString("contacts",a.toString()).apply();}
    private void startIncomingNotifications(){if(notifyLoop!=null)return;notifyLoop=()->{if(getPreferences(0).getBoolean("notify",true))sendNotification("وَثاق","لديك تحديث جديد في المحادثة");handler.postDelayed(notifyLoop,30000);};handler.postDelayed(notifyLoop,30000);}
    private void sendNotification(String title,String message){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=getSystemService(NotificationManager.class);NotificationChannel ch=new NotificationChannel("wethaq","إشعارات وَثاق",NotificationManager.IMPORTANCE_DEFAULT);nm.createNotificationChannel(ch);}Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"wethaq"):new Notification.Builder(this);b.setSmallIcon(android.R.drawable.ic_dialog_email).setContentTitle(title).setContentText(message).setAutoCancel(true);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify((int)(System.currentTimeMillis()%100000),b.build());}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){if(recorder!=null){try{recorder.stop();recorder.release();}catch(Exception ignored){}}handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
