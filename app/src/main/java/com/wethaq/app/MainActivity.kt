package com.wethaq.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
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
import java.security.SecureRandom
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("wethaq_v7", Context.MODE_PRIVATE) }
    private val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).writeTimeout(12, TimeUnit.SECONDS).build()
    private val apiBase = "https://wethaq-backend-production.up.railway.app"
    private val teal = Color.rgb(0,137,123)
    private val navy = Color.rgb(17,38,54)
    private val bg = Color.rgb(247,249,250)
    private val line = Color.rgb(220,228,231)
    private val gray = Color.rgb(98,112,122)
    private val myName get() = prefs.getString("name","") ?: ""
    private val myYear get() = prefs.getInt("year",0)
    private val myId get() = prefs.getString("id","") ?: ""
    private val token get() = prefs.getString("token","") ?: ""

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor=Color.WHITE
        window.navigationBarColor=bg
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if(myName.isBlank() || myId.isBlank()) welcome() else home()
    }
    override fun onResume(){ super.onResume(); if(myId.isNotBlank()){ syncIdentity(); flushPendingMessages() } }

    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun box(c:Int,r:Int=18,stroke:Int?=null)=android.graphics.drawable.GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat();if(stroke!=null)setStroke(dp(1),stroke)}
    private fun root()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg);layoutDirection=View.LAYOUT_DIRECTION_RTL}
    private fun label(s:String,size:Float=16f,c:Int=navy,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size;setTextColor(c);gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;textAlignment=View.TEXT_ALIGNMENT_VIEW_START;if(bold)typeface=Typeface.DEFAULT_BOLD}
    private fun centered(s:String,size:Float=16f,c:Int=navy,bold:Boolean=false)=label(s,size,c,bold).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER}
    private fun button(s:String,a:()->Unit,c:Int=teal)=TextView(this).apply{text=s;textSize=15f;setTextColor(Color.WHITE);gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD;background=box(c,16);setPadding(dp(12),dp(8),dp(12),dp(8));setOnClickListener{a()}}
    private fun outline(s:String,a:()->Unit)=TextView(this).apply{text=s;textSize=14f;setTextColor(navy);gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD;background=box(Color.WHITE,16,line);setOnClickListener{a()}}
    private fun input(h:String,num:Boolean=false)=EditText(this).apply{hint=h;textSize=16f;setTextColor(navy);setHintTextColor(gray);setSingleLine(true);inputType=if(num)InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;background=box(Color.WHITE,16,line);setPadding(dp(15),0,dp(15),0);gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
    private fun add(p:LinearLayout,v:View,h:Int=-2,w:Float=0f,m:Int=0){val lp=LinearLayout.LayoutParams(-1,if(h<0)-2 else dp(h),w);if(m>0)lp.setMargins(dp(m),dp(m),dp(m),dp(m));p.addView(v,lp)}
    private fun avatar(size:Int=70)=ImageView(this).apply{setImageResource(R.drawable.profile_photo);scaleType=ImageView.ScaleType.CENTER_CROP;background=box(Color.WHITE,100,teal);setPadding(dp(2),dp(2),dp(2),dp(2))}
    private fun header(t:String,s:String="",back:(()->Unit)?=null)=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(14),dp(8),dp(14),dp(8));val x=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL};add(x,label(t,22f,navy,true),30);if(s.isNotBlank())add(x,label(s,12f,gray),22);add(this,x,0,1f);if(back!=null)addView(outline("‹ رجوع",back),LinearLayout.LayoutParams(dp(82),dp(44)))}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()

    private fun welcome(){
        val r=root();val sc=ScrollView(this);val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(22),dp(28),dp(22),dp(28))}
        add(c,avatar(128),128,0f,4);add(c,centered("وَثاق",40f,navy,true),54);add(c,centered("هوية وتواصل عربي مستقل",18f,gray,true),34)
        add(c,centered("ابحث عن المعرفات، احفظ جهات الاتصال، وافتح المحادثات حتى دون اتصال.",14f,gray),58)
        add(c,button("⌁  تسجيل الدخول",{login()}),58,0f,5);add(c,button("＋  إنشاء هوية جديدة",{register()},Color.rgb(28,71,91)),58,0f,5)
        sc.addView(c);add(r,sc,0,1f);setContentView(r)
    }

    private fun identityForm(title:String,sub:String,action:String,submit:(String,Int)->Unit){
        val r=root();val sc=ScrollView(this);val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(20),dp(10),dp(20),dp(24))}
        add(c,header(title,sub){welcome()});add(c,avatar(96),96,0f,8);add(c,label("الاسم الثلاثي",14f,navy,true),30,0f,2)
        val n=input("مثال: حاتم حسين الحاج رمضان");add(c,n,56,0f,2);add(c,label("سنة الميلاد",14f,navy,true),30,0f,6)
        val y=input("مثال: 1995",true);add(c,y,56,0f,2);val id=centered("سيظهر معرفك هنا",14f,teal,true);id.background=box(Color.WHITE,16,line);add(c,id,58,0f,10)
        val update={val a=n.text.toString().trim();val b=y.text.toString().toIntOrNull();id.text=if(a.isBlank()||b==null)"سيظهر معرفك هنا" else "@${makeId(a,b)}"};n.setOnFocusChangeListener{_,_->update()};y.setOnFocusChangeListener{_,_->update()}
        add(c,button(action,{val a=n.text.toString().trim();val b=y.text.toString().toIntOrNull();val words=a.split(Regex("\\s+")).filter{it.isNotBlank()};val max=Calendar.getInstance().get(Calendar.YEAR);if(words.size<3){toast("اكتب الاسم الثلاثي");return@button};if(b==null||b !in 1900..max){toast("سنة الميلاد غير صحيحة");return@button};submit(a,b)}),58,0f,6)
        add(c,outline("إلغاء",{welcome()}),50,0f,3);sc.addView(c);add(r,sc,0,1f);setContentView(r)
    }
    private fun login(){identityForm("تسجيل الدخول","الاسم الثلاثي + سنة الميلاد","دخول إلى وَثاق"){n,y->if(myName==n&&myYear==y&&myId.isNotBlank()){home();return@identityForm};request("POST","/api/login",JSONObject().put("name",n).put("birthYear",y).put("deviceKey",deviceKey()),false){ok,b->runOnUiThread{if(ok){saveServer(b);home()}else toast("تعذر تسجيل الدخول. تحقق من الإنترنت والبيانات.")}}}}
    private fun register(){identityForm("إنشاء هوية وَثاق","المعرف سيظهر من أي هاتف عند البحث عنه","إنشاء المعرف والدخول"){n,y->request("POST","/api/identity",JSONObject().put("name",n).put("birthYear",y).put("deviceKey",deviceKey()),false){ok,b->runOnUiThread{if(ok){saveServer(b);home()}else{saveLocalIdentity(n,y);toast("حُفظت الهوية محليًا وستتم مزامنتها عند عودة الإنترنت.");home()}}}}}

    private fun home(){
        val r=root();add(r,header("وَثاق","مرحبًا $myName"));val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(14),dp(8),dp(14),dp(14))}
        val p=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;background=box(Color.WHITE,20,line);setPadding(dp(10),dp(10),dp(10),dp(10))};p.addView(avatar(58),LinearLayout.LayoutParams(dp(58),dp(58)));add(p,label("$myName\n@$myId",13f,navy,true),0,1f,10);add(c,p,80,0f,4)
        val q=input("ابحث بالاسم أو معرف وَثاق");add(c,q,54,0f,4);add(c,button("⌕  بحث عن شخص",{search(q.text.toString())}),54,0f,4);add(c,button("◉  جهات الاتصال",{contacts()},Color.rgb(28,71,91)),54,0f,4);add(c,button("◎  ملفي ومعرفي",{profile()},Color.rgb(70,90,100)),54,0f,4);add(c,centered("✓ جهات الاتصال والمحادثات المحفوظة متاحة دون إنترنت.",12f,gray),50,0f,6);add(c,outline("↪  تسجيل الخروج",{prefs.edit().clear().apply();welcome()}),50,0f,4);add(r,c,0,1f);setContentView(r);flushPendingMessages()
    }

    private fun profile(){val r=root();val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(20),dp(12),dp(20),dp(20))};add(c,header("ملفي","هوية وَثاق",{home()}));add(c,avatar(110),110,0f,12);add(c,centered(myName,22f,navy,true),38);add(c,centered("@$myId",15f,teal,true),42);add(c,button("⧉  نسخ المعرف",{val cm=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;cm.setPrimaryClip(ClipData.newPlainText("Wethaq ID",myId));toast("تم نسخ المعرف")}),54,0f,8);add(c,centered("المعرف محفوظ على الهاتف ويمكن عرضه دون اتصال.",13f,gray),42,0f,5);add(r,c,0,1f);setContentView(r)}

    private fun search(value:String){val q=value.trim().removePrefix("@").trim();if(q.length<2){toast("اكتب اسمًا أو معرفًا");return};val local=readUsers().filter{it.optString("wethaq_id").contains(q,true)||it.optString("name").contains(q,true)};if(local.isNotEmpty())showResults(local.toJSONArray(),"نتائج محفوظة محليًا",q) else serverSearch(q)}
    private fun serverSearch(q:String){request("GET","/api/search?q=${Uri.encode(q)}",null,false){ok,b->runOnUiThread{if(ok){val arr=b.optJSONArray("users")?:JSONArray();cacheUsers(arr);showResults(arr,"نتائج البحث على الخادم",q)}else toast("لا يوجد اتصال بالخادم. النتائج المحلية ما زالت متاحة.")}}}
    private fun showResults(arr:JSONArray,title:String,query:String){val r=root();val sc=ScrollView(this);val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(14),dp(8),dp(14),dp(20))};add(c,header("البحث",title,{home()}));if(arr.length()==0){add(c,centered("لم يتم العثور على هذا المعرف.",16f,navy,true),70,0f,8);add(c,button("↻ البحث عبر الإنترنت",{serverSearch(query)}),54,0f,5)}else{for(i in 0 until arr.length()){val u=arr.optJSONObject(i)?:continue;val id=u.optString("wethaq_id");val name=u.optString("name");val item=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;background=box(Color.WHITE,18,line);setPadding(dp(14),dp(10),dp(14),dp(10))};add(item,label(name,17f,navy,true),30);add(item,label("@$id",14f,teal,true),28);add(item,button("＋ إضافة إلى جهات الاتصال",{addContact(id)}),48,0f,4);add(c,item,0,1f,5)}};sc.addView(c);add(r,sc,0,1f);setContentView(r)}

    private fun addContact(id:String){if(id.isBlank())return;val cached=readUsers().firstOrNull{it.optString("wethaq_id")==id};if(cached!=null)cacheContact(cached);if(!hasNetwork()){toast("تم حفظ جهة الاتصال محليًا. ستتم المزامنة عند عودة الإنترنت.");contacts();return};request("POST","/api/contacts",JSONObject().put("wethaqId",id),true){ok,b->runOnUiThread{if(ok){val contact=b.optJSONObject("contact")?:cached?:JSONObject().put("wethaq_id",id);cacheContact(contact);toast("تمت إضافة جهة الاتصال");contacts()}else{toast("تعذر الإضافة الآن، لكن بياناتها المحلية محفوظة.");contacts()}}}}
    private fun contacts(){val r=root();val sc=ScrollView(this);val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(14),dp(8),dp(14),dp(20))};add(c,header("جهات الاتصال","متاحة دون إنترنت",{home()}));val arr=readContacts();if(arr.length()==0)add(c,centered("لا توجد جهات اتصال محفوظة بعد.",16f,gray),80,0f,8);for(i in 0 until arr.length()){val u=arr.optJSONObject(i)?:continue;val id=u.optString("wethaq_id");val name=u.optString("name",id);val item=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;background=box(Color.WHITE,18,line);setPadding(dp(10),dp(8),dp(10),dp(8))};item.addView(avatar(52),LinearLayout.LayoutParams(dp(52),dp(52)));add(item,label("$name\n@$id",14f,navy,true),0,1f,10);item.addView(button("محادثة",{chat(u)}),LinearLayout.LayoutParams(dp(82),dp(46)));add(c,item,68,0f,5)};sc.addView(c);add(r,sc,0,1f);setContentView(r)}

    private fun chat(user:JSONObject){val id=user.optString("wethaq_id");val name=user.optString("name",id);val r=root();add(r,header(name,"@$id",{contacts()}));val sc=ScrollView(this);val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(12),dp(8),dp(12),dp(8))};val messages=readMessages(id);if(messages.length()==0)add(c,centered("ابدأ المحادثة. الرسائل تُحفظ محليًا عند انقطاع الإنترنت.",13f,gray),60,0f,5);for(i in 0 until messages.length()){val m=messages.optJSONObject(i)?:continue;val mine=m.optString("from")==myId;val bubble=label(m.optString("text"),15f,if(mine)Color.WHITE else navy);bubble.background=box(if(mine)teal else Color.WHITE,18,if(mine)null else line);bubble.setPadding(dp(14),dp(10),dp(14),dp(10));add(c,bubble,0,0f,5)};sc.addView(c);add(r,sc,0,1f);val composer=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(10),dp(8),dp(10),dp(8));setBackgroundColor(Color.WHITE)};val field=input("اكتب رسالة…");add(composer,field,52,1f);composer.addView(button("إرسال",{sendMessage(id,field.text.toString());field.setText("")}),LinearLayout.LayoutParams(dp(86),dp(52)));add(r,composer,68);setContentView(r)}
    private fun sendMessage(to:String,text:String){val value=text.trim();if(value.isBlank())return;val m=JSONObject().put("id",deviceKey()+System.currentTimeMillis()).put("from",myId).put("to",to).put("text",value).put("createdAt",System.currentTimeMillis()).put("pending",true);appendMessage(to,m);if(!hasNetwork()){enqueue(m);toast("حُفظت الرسالة وستُرسل عند عودة الإنترنت.");return};flushPendingMessages();toast("تم حفظ الرسالة وإرسالها عند توفر الاتصال.")}

    private fun request(method:String,path:String,json:JSONObject?,auth:Boolean,callback:(Boolean,JSONObject)->Unit){Thread{try{val b=Request.Builder().url(apiBase.trimEnd('/')+path);if(auth&&token.isNotBlank())b.header("Authorization","Bearer $token");when(method){"GET"->b.get();"POST"->b.post((json?.toString()?:"{}").toRequestBody("application/json; charset=utf-8".toMediaType()));else->b.get()};http.newCall(b.build()).execute().use{response->val raw=response.body?.string().orEmpty();val body=try{JSONObject(raw)}catch(_:Exception){JSONObject().put("raw",raw)};callback(response.isSuccessful,body)}}catch(_:Exception){callback(false,JSONObject())}}.start()}
    private fun makeId(name:String,year:Int):String{val clean=name.lowercase().replace(Regex("[^a-z0-9]"),"");return(if(clean.isBlank())"user" else clean.take(10))+year.toString().takeLast(2)}
    private fun deviceKey():String{val old=prefs.getString("device",null);if(old!=null)return old;val bytes=ByteArray(16);SecureRandom().nextBytes(bytes);return bytes.joinToString(""){ "%02x".format(it)}.also{prefs.edit().putString("device",it).apply()}}
    private fun saveServer(body:JSONObject){val id=body.optString("wethaq_id",body.optString("id"));val name=body.optString("name",body.optString("fullName"));val year=body.optInt("birthYear",body.optInt("year",myYear));val t=body.optString("token","");if(id.isNotBlank())prefs.edit().putString("id",id).putString("name",name).putInt("year",year).putString("token",t).apply()}
    private fun saveLocalIdentity(name:String,year:Int){prefs.edit().putString("name",name).putInt("year",year).putString("id",makeId(name,year)).apply()}
    private fun syncIdentity(){if(!hasNetwork())return;request("POST","/api/identity/sync",JSONObject().put("name",myName).put("birthYear",myYear).put("wethaqId",myId).put("deviceKey",deviceKey()),false){ok,b->if(ok)saveServer(b)}}
    private fun hasNetwork():Boolean=try{(getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork!=null}catch(_:Exception){false}
    private fun readUsers():List<JSONObject>{val a=try{JSONArray(prefs.getString("users","[]"))}catch(_:Exception){JSONArray()};return(0 until a.length()).mapNotNull{a.optJSONObject(it)}}
    private fun cacheUsers(arr:JSONArray){val all=readUsers().associateBy{it.optString("wethaq_id")}.toMutableMap();for(i in 0 until arr.length()){val u=arr.optJSONObject(i)?:continue;val id=u.optString("wethaq_id");if(id.isNotBlank())all[id]=u};prefs.edit().putString("users",JSONArray(all.values.toList()).toString()).apply()}
    private fun cacheContact(user:JSONObject){val id=user.optString("wethaq_id");if(id.isBlank())return;val a=readContacts();val list=(0 until a.length()).mapNotNull{a.optJSONObject(it)}.filter{it.optString("wethaq_id")!=id}.toMutableList();list.add(user);prefs.edit().putString("contacts",JSONArray(list).toString()).apply();cacheUsers(JSONArray(list))}
    private fun readContacts():JSONArray=try{JSONArray(prefs.getString("contacts","[]"))}catch(_:Exception){JSONArray()}
    private fun readMessages(id:String):JSONArray=try{JSONArray(prefs.getString("messages_$id","[]"))}catch(_:Exception){JSONArray()}
    private fun appendMessage(to:String,m:JSONObject){val a=readMessages(to);a.put(m);prefs.edit().putString("messages_$to",a.toString()).apply()}
    private fun enqueue(m:JSONObject){val a=try{JSONArray(prefs.getString("outbox","[]"))}catch(_:Exception){JSONArray()};a.put(m);prefs.edit().putString("outbox",a.toString()).apply()}
    private fun flushPendingMessages(){if(!hasNetwork())return;val a=try{JSONArray(prefs.getString("outbox","[]"))}catch(_:Exception){JSONArray()};if(a.length()==0)return;Thread{val remaining=JSONArray();for(i in 0 until a.length()){val m=a.optJSONObject(i)?:continue;if(!sendPending(m))remaining.put(m)};prefs.edit().putString("outbox",remaining.toString()).apply()}.start()}
    private fun sendPending(m:JSONObject):Boolean=try{val r=Request.Builder().url(apiBase.trimEnd('/')+"/api/messages").header("Authorization",if(token.isBlank())"" else "Bearer $token").post(m.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build();http.newCall(r).execute().use{it.isSuccessful}}catch(_:Exception){false}
}
private fun <T> List<T>.toJSONArray():JSONArray{val a=JSONArray();for(item in this)a.put(item);return a}
