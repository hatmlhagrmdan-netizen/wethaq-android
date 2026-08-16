package com.wethaq.app

import android.content.*
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("wethaq", MODE_PRIVATE) }
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val baseUrl = "https://wethaq-backend-production.up.railway.app"
    private val myName get() = prefs.getString("name", "") ?: ""
    private val myId get() = prefs.getString("id", "") ?: ""
    private val token get() = prefs.getString("token", "") ?: ""
    private val teal = Color.rgb(0,137,123)
    private val dark = Color.rgb(20,40,52)
    private val gray = Color.rgb(100,115,125)
    private val page = Color.rgb(247,249,250)

    override fun onCreate(state: Bundle?) { super.onCreate(state); if (myName.isBlank() || myId.isBlank()) welcome() else home() }
    private fun dp(v:Int)= (v * resources.displayMetrics.density).toInt()
    private fun root() = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(page); layoutDirection=View.LAYOUT_DIRECTION_RTL }
    private fun text(s:String,size:Float=16f,color:Int=dark,bold:Boolean=false)=TextView(this).apply { text=s; textSize=size; setTextColor(color); gravity=Gravity.CENTER_VERTICAL; if(bold) typeface=android.graphics.Typeface.DEFAULT_BOLD; layoutDirection=View.LAYOUT_DIRECTION_RTL }
    private fun field(h:String,numeric:Boolean=false)=EditText(this).apply { hint=h; textSize=16f; setSingleLine(true); setTextColor(dark); setHintTextColor(gray); inputType=if(numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; setPadding(dp(14),0,dp(14),0) }
    private fun button(s:String, action:()->Unit, color:Int=teal)=Button(this).apply { text=s; setTextColor(Color.WHITE); setBackgroundColor(color); setOnClickListener{action()} }
    private fun add(parent:LinearLayout, view:View, height:Int=-1) { val h=if(height<0) LinearLayout.LayoutParams.WRAP_CONTENT else dp(height); parent.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,h)) }
    private fun fill(parent:LinearLayout, view:View) { parent.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f)) }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()

    private fun welcome(){
        val r=root(); val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(20),dp(40),dp(20),dp(20))}
        add(c,text("وَثاق",38f,dark,true),60); add(c,text("هوية وتواصل عربي مستقل",18f,gray,true),45)
        add(c,button("تسجيل الدخول"){identityForm(false)},58); add(c,button("إنشاء هوية جديدة",{identityForm(true)},Color.rgb(35,70,90)),58); fill(r,c); setContentView(r)
    }

    private fun identityForm(register:Boolean){
        val r=root(); val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(20),dp(20),dp(20))}
        add(c,text(if(register)"إنشاء هوية وَثاق" else "تسجيل الدخول",24f,dark,true),55)
        val n=field("الاسم الثلاثي"); val y=field("سنة الميلاد",true); add(c,n,56); add(c,y,56)
        add(c,button(if(register)"إنشاء" else "دخول",{
            val entered=n.text.toString().trim(); val year=y.text.toString().toIntOrNull()
            if(entered.split(Regex("\\s+")).filter{it.isNotBlank()}.size<3 || year==null){toast("تحقق من الاسم الثلاثي وسنة الميلاد");return@button}
            val path=if(register)"/api/identity" else "/api/login"; val data=JSONObject().put("name",entered).put("birthYear",year).put("deviceKey",deviceKey())
            request("POST",path,data,false){ok,body->runOnUiThread{if(ok&&saveIdentity(body))home()else toast(if(register)"تعذر إنشاء الهوية. تحقق من الإنترنت والبيانات." else "تعذر تسجيل الدخول. تحقق من الإنترنت والبيانات.")}}
        }),58)
        add(c,button("رجوع",{welcome()},Color.DKGRAY),52); fill(r,c); setContentView(r)
    }

    private fun home(){
        val r=root(); val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16))}
        add(c,text("وَثاق",26f,dark,true),55); add(c,text("@$myId",16f,teal,true),40)
        val q=field("ابحث بالاسم أو المعرف"); add(c,q,56); add(c,button("بحث"){search(q.text.toString())},56)
        add(c,button("جهات الاتصال",{contacts()},Color.rgb(35,70,90)),56); add(c,button("ملفي والصورة الشخصية",{profile()},Color.rgb(75,90,100)),56)
        add(c,button("تسجيل الخروج",{prefs.edit().clear().apply();welcome()},Color.DKGRAY),52); fill(r,c); setContentView(r)
    }

    private fun profile(){
        val r=root(); val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18))}
        add(c,text("ملفي",25f,dark,true),55); add(c,text(myName,20f,dark,true),45); add(c,text("@$myId",17f,teal,true),45)
        add(c,button("تغيير الصورة الشخصية"){startActivity(Intent(this,AvatarActivity::class.java))},56)
        add(c,button("نسخ المعرف",{val m=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;m.setPrimaryClip(ClipData.newPlainText("Wethaq ID",myId));toast("تم نسخ المعرف")},Color.rgb(35,70,90)),56)
        add(c,button("رجوع",{home()},Color.DKGRAY),52); fill(r,c); setContentView(r)
    }

    private fun search(value:String){
        val q=value.trim().removePrefix("@"); if(q.length<2){toast("اكتب اسمًا أو معرفًا");return}
        request("GET","/api/search?q=${URLEncoder.encode(q,"UTF-8")}",null,false){ok,body->runOnUiThread{if(ok)showResults(body.optJSONArray("users")?:JSONArray())else toast("تعذر البحث الآن")}}
    }

    private fun showResults(users:JSONArray){
        val r=root(); val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12))}; add(c,text("نتائج البحث",24f,dark,true),55)
        if(users.length()==0)add(c,text("لم يتم العثور على مستخدم",16f,gray),60)
        for(i in 0 until users.length()){
            val u=users.optJSONObject(i)?:continue; val uid=u.optString("wethaq_id"); val uname=u.optString("name").ifBlank{"مستخدم وَثاق"}
            add(c,text("$uname\n@$uid",17f,dark,true),62); add(c,button("حفظ ومراسلة",{saveContact(uid,uname);chat(uid,uname)}),52)
        }
        add(c,button("رجوع",{home()},Color.DKGRAY),52); fill(r,c); setContentView(r)
    }

    private fun saveContact(uid:String,uname:String){
        if(uid.isBlank())return; val arr=JSONArray(prefs.getString("contacts","[]")?:"[]"); var exists=false
        for(i in 0 until arr.length())if(arr.optJSONObject(i)?.optString("id")==uid)exists=true
        if(!exists)arr.put(JSONObject().put("id",uid).put("name",uname)); prefs.edit().putString("contacts",arr.toString()).apply(); toast("تم حفظ $uname\n@$uid")
        if(token.isNotBlank())request("POST","/api/contacts",JSONObject().put("wethaqId",uid),true){_,_->}
    }

    private fun contacts(){
        val r=root(); val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(12),dp(14),dp(12))}; add(c,text("جهات الاتصال",24f,dark,true),55)
        val arr=JSONArray(prefs.getString("contacts","[]")?:"[]"); if(arr.length()==0)add(c,text("لا توجد جهات اتصال محفوظة",16f,gray),60)
        for(i in 0 until arr.length()){val u=arr.optJSONObject(i)?:continue;val uid=u.optString("id");val uname=u.optString("name").ifBlank{"مستخدم وَثاق"};add(c,text("$uname\n@$uid",17f,dark,true),62);add(c,button("مراسلة",{chat(uid,uname)}),50)}
        add(c,button("رجوع",{home()},Color.DKGRAY),52); fill(r,c); setContentView(r)
    }

    private fun chat(uid:String,uname:String){
        val r=root(); val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10))}; add(c,text("$uname\n@$uid",21f,dark,true),65)
        val messages=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}; fill(c,messages)
        val input=field("اكتب رسالتك"); add(c,input,55); add(c,button("إرسال",{
            val body=input.text.toString().trim(); if(body.isNotBlank()){input.setText("");bubble(messages,body,"جاري الإرسال");send(uid,body,messages)}
        }),52); add(c,button("رجوع",{contacts()},Color.DKGRAY),48); fill(r,c); setContentView(r); loadMessages(uid,messages)
    }

    private fun bubble(parent:LinearLayout,body:String,status:String){val v=text("$body\n$status",15f,dark);v.setPadding(dp(12),dp(8),dp(12),dp(8));parent.addView(v,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT))}
    private fun updateLast(parent:LinearLayout,status:String){if(parent.childCount==0)return;val v=parent.getChildAt(parent.childCount-1) as? TextView?:return;val old=v.text.toString().substringBeforeLast("\n");v.text="$old\n$status"}
    private fun send(uid:String,body:String,messages:LinearLayout){if(!network()||token.isBlank()){queue(uid,body);updateLast(messages,"محفوظة للإرسال");return};request("POST","/api/messages",JSONObject().put("to",uid).put("body",body),true){ok,_->runOnUiThread{if(ok)updateLast(messages,"تم الإرسال")else{queue(uid,body);updateLast(messages,"محفوظة للإرسال")}}}}
    private fun loadMessages(uid:String,messages:LinearLayout){if(!network()||token.isBlank())return;request("GET","/api/messages/${URLEncoder.encode(uid,"UTF-8")}",null,true){ok,body->if(ok)runOnUiThread{messages.removeAllViews();val arr=body.optJSONArray("messages")?:JSONArray();for(i in 0 until arr.length()){val m=arr.optJSONObject(i)?:continue;bubble(messages,m.optString("body"),m.optString("status","تم الاستلام"))}}}}
    private fun queue(uid:String,body:String){val arr=JSONArray(prefs.getString("pending","[]")?:"[]");arr.put(JSONObject().put("to",uid).put("body",body));prefs.edit().putString("pending",arr.toString()).apply();toast("حُفظت الرسالة وسترسل عند عودة الإنترنت")}
    private fun network():Boolean{val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;return cm.activeNetwork!=null}
    private fun deviceKey():String{val old=prefs.getString("deviceKey","")?:"";if(old.isNotBlank())return old;val b=ByteArray(16);SecureRandom().nextBytes(b);val k=b.joinToString(""){String.format("%02x",it)};prefs.edit().putString("deviceKey",k).apply();return k}
    private fun saveIdentity(body:JSONObject):Boolean{val newId=body.optString("wethaq_id",body.optString("id"));if(newId.isBlank())return false;prefs.edit().putString("name",body.optString("name",myName)).putString("id",newId).putString("token",body.optString("token",token)).apply();return true}
    private fun request(method:String,path:String,json:JSONObject?,auth:Boolean,cb:(Boolean,JSONObject)->Unit){Thread{try{val req=Request.Builder().url(baseUrl+path);if(method=="POST")req.post((json?.toString()? : "{}").toRequestBody("application/json".toMediaType()))else req.get();if(auth&&token.isNotBlank())req.header("Authorization","Bearer $token");client.newCall(req.build()).execute().use{res->val raw=res.body?.string()? : "{}";val body=try{JSONObject(raw)}catch(_:Exception){JSONObject().put("raw",raw)};cb(res.isSuccessful,body)}}catch(e:Exception){cb(false,JSONObject().put("error","network"))}}.start()}
}
