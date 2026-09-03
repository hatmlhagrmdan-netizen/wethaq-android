package com.wethaq.app;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AdminActivity extends Activity {
    private static final String API="https://wethaq-backend-production.up.railway.app";
    private String token, role="";
    private LinearLayout body;
    private EditText target, minutes, reason;

    private int gold(){return Color.rgb(212,175,55);}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}

    private Button b(String s){
        Button x=new Button(this);
        x.setText(s);x.setTextColor(Color.WHITE);x.setTextSize(17);x.setAllCaps(false);
        x.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        x.setBackground(round(Color.rgb(28,28,32),gold()));x.setMinHeight(dp(64));
        return x;
    }

    private EditText f(String s){
        EditText e=new EditText(this);
        e.setHint(s);e.setHintTextColor(Color.LTGRAY);e.setTextColor(Color.WHITE);e.setTextSize(17);
        e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);e.setBackground(round(Color.rgb(24,24,27),Color.rgb(78,78,84)));e.setMinHeight(dp(60));
        return e;
    }

    private GradientDrawable round(int c,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(13));d.setStroke(dp(2),stroke);return d;
    }

    private LinearLayout.LayoutParams lp(int w,int h,int m){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.bottomMargin=dp(m);return p;
    }

    @Override public void onCreate(Bundle z){
        super.onCreate(z);
        token=getSharedPreferences("wethaq",MODE_PRIVATE).getString("token","");
        if(token.length()<10){finish();return;}
        showLoading();
        req("GET","/api/admin/role",null,r->{
            try{
                JSONObject o=new JSONObject(r);role=o.optString("role","");
                if(role.isEmpty())throw new Exception();
                if("founder".equals(role))render();else askCode();
            }catch(Exception e){toast("لا تملك صلاحية الإدارة");finish();}
        });
    }

    private void askCode(){
        EditText code=f("الرمز الإداري");
        AlertDialog d=new AlertDialog.Builder(this)
            .setTitle("التحقق الإداري")
            .setMessage("أدخل الرمز الذي استلمته عند تعيينك.")
            .setView(code).setNegativeButton("إلغاء",(q,w)->finish()).setPositiveButton("تحقق",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(q->{
            String value=code.getText().toString().trim();
            if(value.isEmpty()){code.setError("أدخل الرمز");return;}
            try{
                JSONObject o=new JSONObject();o.put("code",value);
                req("POST","/api/admin/verify-code",o.toString(),r->{
                    try{
                        JSONObject x=new JSONObject(r);role=x.optString("role",role);d.dismiss();render();
                    }catch(Exception e){toast("الرمز غير صحيح");}
                });
            }catch(Exception e){toast("تعذر التحقق");}
        }));
        d.show();
    }

    private String label(){
        switch(role){
            case "founder":return "مدير ومؤسس وثاق";
            case "executive":return "المدير التنفيذي";
            case "deputy1":return "النائب الأول";
            case "deputy2":return "النائب الثاني";
            case "deputy3":return "النائب الثالث";
            case "supervisor":return "المشرف";
            case "admin_member":return "عضو إدارة";
            case "premium":return "عضو مميز";
            default:return "الإدارة";
        }
    }

    private int price(){
        switch(role){
            case "founder":return 3000;
            case "executive":return 100;
            case "deputy1":return 80;
            case "deputy2":return 60;
            case "deputy3":return 40;
            case "supervisor":return 20;
            case "admin_member":return 10;
            case "premium":return 2;
            default:return 0;
        }
    }

    private String banLabel(){
        if("premium".equals(role))return "تنبيه فقط";
        if("supervisor".equals(role)||"admin_member".equals(role))return "حظر مؤقت + تنبيه";
        return "حظر مؤقت أو نهائي + تنبيه";
    }

    private boolean canAppoint(){return Arrays.asList("founder","executive","deputy1","deputy2","deputy3").contains(role);}

    private void render(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(14));root.setBackgroundColor(Color.BLACK);
        TextView h=new TextView(this);h.setText("🛡 لوحة التحكم — "+label());h.setTextColor(gold());h.setTextSize(23);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);h.setGravity(Gravity.CENTER);
        root.addView(h,lp(-1,72,8));
        ScrollView s=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);s.addView(body);root.addView(s,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        LinearLayout info=card("المنصب: "+label()+"\nالسعر السنوي: $"+price()+"\nصلاحيات الإجراءات: "+banLabel());body.addView(info,lp(-1,-2,10));
        loadStructure();
        if(canAppoint())addAppointment();
        addModeration();
        if("founder".equals(role))addAudit();
        Button back=b("رجوع");root.addView(back,lp(-1,64,4));back.setOnClickListener(v->finish());
    }

    private LinearLayout card(String s){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(10),dp(8),dp(10),dp(8));c.setBackground(round(Color.rgb(20,20,24),Color.rgb(68,68,74)));
        TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(16);t.setGravity(Gravity.RIGHT);t.setPadding(dp(8),dp(8),dp(8),dp(8));c.addView(t);
        return c;
    }

    private void loadStructure(){
        req("GET","/api/admin/structure",null,r->{
            try{
                JSONObject o=new JSONObject(r);StringBuilder s=new StringBuilder("👥 الهيكل الحالي\n");
                s.append("\n👑 المؤسس: ").append(nameOf(o.optJSONObject("founder")));
                s.append("\n🏛 التنفيذي: ").append(nameOf(o.optJSONObject("executive")));
                JSONObject d=o.optJSONObject("deputies");
                s.append("\n🥇 نائب أول: ").append(nameOf(d==null?null:d.optJSONObject("deputy1")));
                s.append("\n🥈 نائب ثانٍ: ").append(nameOf(d==null?null:d.optJSONObject("deputy2")));
                s.append("\n🥉 نائب ثالث: ").append(nameOf(d==null?null:d.optJSONObject("deputy3")));
                s.append("\n🛡 المشرف: ").append(nameOf(o.optJSONObject("supervisor")));
                JSONArray m=o.optJSONArray("members");s.append("\n\n👥 أعضاء الإدارة: ").append(m==null?0:m.length()).append("/10");
                LinearLayout c=card(s.toString());body.addView(c,0,lp(-1,-2,10));
            }catch(Exception e){toast("تعذر قراءة الهيكل");}
        });
    }

    private String nameOf(JSONObject o){return o==null?"غير معين":o.optString("name","غير معروف");}

    private void addAppointment(){
        LinearLayout c=card("➕ التعيين وفق صلاحية رتبتك");
        body.addView(c,lp(-1,-2,10));
        target=f("معرف وَثاق للشخص");Spinner sp=new Spinner(this);
        ArrayList<String> labels=new ArrayList<>(),rolesList=new ArrayList<>();
        if("founder".equals(role)){
            labels.addAll(Arrays.asList("المدير التنفيذي — $100","النائب الأول — $80","النائب الثاني — $60","النائب الثالث — $40","المشرف — $20","عضو إدارة — $10","عضو مميز — $2"));
            rolesList.addAll(Arrays.asList("executive","deputy1","deputy2","deputy3","supervisor","admin_member","premium"));
        }else{
            labels.add("عضو إدارة — $10");labels.add("عضو مميز — $2");rolesList.add("admin_member");rolesList.add("premium");
        }
        sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        Button assign=b("تعيين وإصدار رمز إداري");c.addView(target,lp(-1,60,6));c.addView(sp,lp(-1,60,6));c.addView(assign,lp(-1,68,6));
        assign.setOnClickListener(v->{
            try{
                JSONObject q=new JSONObject();q.put("wethaqId",target.getText().toString().trim());q.put("role",rolesList.get(sp.getSelectedItemPosition()));
                req("POST","/api/admin/assign",q.toString(),r->{
                    try{
                        JSONObject o=new JSONObject(r);
                        new AlertDialog.Builder(this).setTitle("تم التعيين ✓").setMessage("المنصب: "+o.optString("role_label")+"\nرمز الدخول الإداري:\n"+o.optString("admin_code")+"\n\nتم إرسال إشعار ورسالة إلى الشخص المعين.").setPositiveButton("إغلاق",null).show();
                        loadStructure();
                    }catch(Exception e){toast("تعذر قراءة نتيجة التعيين");}
                });
            }catch(Exception e){toast("بيانات التعيين غير صحيحة");}
        });
    }

    private void addModeration(){
        LinearLayout c=card("🚫 الحظر والتنبيه");body.addView(c,lp(-1,-2,10));
        target=f("معرف وَثاق للمستخدم");minutes=f("الدقائق — 0 = نهائي");reason=f("سبب الإجراء");
        c.addView(target,lp(-1,60,6));c.addView(minutes,lp(-1,60,6));c.addView(reason,lp(-1,60,6));
        Button ban=b("🚫 تنفيذ الحظر"),unban=b("✅ إلغاء الحظر"),alert=b("⚠️ تنبيه المستخدم");
        c.addView(ban,lp(-1,68,6));
        if(Arrays.asList("founder","executive","deputy1","deputy2","deputy3").contains(role))c.addView(unban,lp(-1,68,6));
        c.addView(alert,lp(-1,68,4));
        ban.setOnClickListener(v->{
            try{
                int m=minutes.getText().toString().trim().isEmpty()?0:Integer.parseInt(minutes.getText().toString().trim());
                if("premium".equals(role)){toast("العضو المميز يملك التنبيه فقط");return;}
                if(("supervisor".equals(role)||"admin_member".equals(role))&&m<=0){toast("هذه الرتبة تستخدم الحظر المؤقت فقط");return;}
                JSONObject q=new JSONObject();q.put("wethaqId",target.getText().toString().trim());q.put("minutes",m);q.put("reason",reason.getText().toString().trim());
                req("POST","/api/admin/rbac/ban",q.toString(),r->toast("تم تنفيذ الحظر ✓"));
            }catch(Exception e){toast("المدة غير صحيحة");}
        });
        unban.setOnClickListener(v->{try{JSONObject q=new JSONObject();q.put("wethaqId",target.getText().toString().trim());req("POST","/api/admin/unban",q.toString(),r->toast("تم إلغاء الحظر ✓"));}catch(Exception ignored){}});
        alert.setOnClickListener(v->{try{JSONObject q=new JSONObject();q.put("wethaqId",target.getText().toString().trim());q.put("reason",reason.getText().toString().trim());req("POST","/api/admin/alert",q.toString(),r->toast("تم إرسال التنبيه ✓"));}catch(Exception ignored){}});
    }

    private void addAudit(){
        Button x=b("📋 سجل الإدارة");body.addView(x,lp(-1,68,10));
        x.setOnClickListener(v->req("GET","/api/admin/audit-log",null,r->{
            try{
                JSONArray a=new JSONObject(r).optJSONArray("logs");StringBuilder s=new StringBuilder();
                if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);s.append(o.optString("created_at")).append(" — ").append(o.optString("actor_name")).append(" → ").append(o.optString("action")).append(" → ").append(o.optString("target_name")).append("\n");}
                new AlertDialog.Builder(this).setTitle("📋 سجل الإدارة").setMessage(s.length()==0?"لا يوجد سجل":s.toString()).setPositiveButton("إغلاق",null).show();
            }catch(Exception e){toast("فشل قراءة السجل");}
        }));
    }

    private void showLoading(){TextView t=new TextView(this);t.setText("جارٍ التحقق من الصلاحيات…");t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);setContentView(t);}

    private interface CB{void ok(String s);}
    private void req(String m,String p,String body,CB cb){
        new Thread(()->{
            HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)new URL(API+p).openConnection();c.setRequestMethod(m);c.setConnectTimeout(10000);c.setReadTimeout(15000);
                c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/json");
                if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.getBytes(StandardCharsets.UTF_8));}}
                int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();ByteArrayOutputStream o=new ByteArrayOutputStream();
                if(in!=null){byte[] z=new byte[4096];int n;while((n=in.read(z))!=-1)o.write(z,0,n);in.close();}
                String raw=new String(o.toByteArray(),StandardCharsets.UTF_8);
                runOnUiThread(()->{if(code>=200&&code<300)cb.ok(raw);else{try{toast("فشل: "+new JSONObject(raw).optString("error","HTTP "+code));}catch(Exception e){toast("فشل HTTP "+code);}}});
            }catch(Exception e){runOnUiThread(()->toast("تعذر الاتصال بالخادم"));}
            finally{if(c!=null)c.disconnect();}
        }).start();
    }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
