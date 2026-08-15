package com.wethaq.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("wethaq", Context.MODE_PRIVATE) }
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val navy = Color.rgb(12, 34, 50)
    private val teal = Color.rgb(0, 132, 119)
    private val tealDark = Color.rgb(0, 104, 95)
    private val bg = Color.rgb(247, 249, 250)
    private val muted = Color.rgb(92, 108, 120)
    private val line = Color.rgb(222, 230, 233)
    private val danger = Color.rgb(190, 48, 58)
    private var page = "chats"
    private var chatId = ""
    private var socket: WebSocket? = null
    private val apiBase: String get() = (prefs.getString("api_base", "https://wethaq-backend.onrender.com") ?: "").trim().trimEnd('/')

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (prefs.getString("token", "").isNullOrBlank()) showWelcome() else showApp()
    }

    override fun onDestroy() {
        socket?.close(1000, "activity_closed")
        client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        fitsSystemWindows = true
    }

    private fun text(value: String, size: Float = 16f, color: Int = navy, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        includeFontPadding = false
        maxLines = 5
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radius: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun button(value: String, action: () -> Unit, fill: Int = teal) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        background = rounded(fill, 16)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun outlineButton(value: String, action: () -> Unit) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(navy)
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(Color.WHITE, 16, line)
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setOnClickListener { action() }
    }

    private fun field(hint: String, password: Boolean = false, numeric: Boolean = false) = EditText(this).apply {
        this.hint = hint
        textSize = 16f
        setSingleLine(true)
        setTextColor(navy)
        setHintTextColor(muted)
        includeFontPadding = false
        background = rounded(Color.WHITE, 16, line)
        setPadding(dp(16), 0, dp(16), 0)
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        inputType = when {
            numeric -> InputType.TYPE_CLASS_NUMBER
            password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
    }

    private fun add(parent: LinearLayout, view: View, w: Int = -1, h: Int = -2, weight: Float = 0f, margin: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(w, if (h == -2) LinearLayout.LayoutParams.WRAP_CONTENT else dp(h), weight).apply {
            if (margin > 0) setMargins(dp(margin), dp(margin), dp(margin), dp(margin))
        })
    }

    private fun header(title: String, subtitle: String? = null, back: (() -> Unit)? = null): LinearLayout {
        val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(box, text(title, 23f, navy, true), -1, 34)
        if (subtitle != null) add(box, text(subtitle, 13f, muted), -1, 24)
        add(h, box, 0, 62, 1f)
        if (back != null) add(h, outlineButton("رجوع", back), 74, 44, 0f, 4)
        return h
    }

    private fun avatar(founder: Boolean = false): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.profile)
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = rounded(Color.WHITE, 100, if (founder) teal else line)
        clipToOutline = true
        contentDescription = if (founder) "صورة مؤسس وَثاق" else "صورة الملف الشخصي"
        if (founder) setPadding(dp(2), dp(2), dp(2), dp(2))
    }

    private fun showWelcome() {
        val r = root()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(22), dp(24), dp(22), dp(28)) }
        add(content, avatar(true), 146, 146, 0f, 8)
        add(content, text("وَثاق", 40f, navy, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 58, 0f, 4)
        add(content, text("تواصل عربي خاص ومستقل", 19f, muted, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 36)
        add(content, text("هوية رقمية باسمك وسنة ميلادك، دون الحاجة إلى رقم هاتف", 15f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 50)
        val founder = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(Color.WHITE, 18, line); setPadding(dp(14), dp(10), dp(14), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(founder, avatar(true), 58, 58)
        val fb = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(12), 0, 0, 0) }
        add(fb, text("المؤسس", 13f, teal, true), -1, 22)
        add(fb, text("حاتم حسين الحاج رمضان", 17f, navy, true), -1, 28)
        add(fb, text("مؤسس ومطور تطبيق وَثاق", 12f, muted), -1, 22)
        add(founder, fb, 0, 72, 1f)
        add(content, founder, -1, 84, 0f, 8)
        val updates = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(Color.WHITE, 18, line); setPadding(dp(16), dp(12), dp(16), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(updates, text("مزايا وَثاق", 17f, navy, true), -1, 30)
        add(updates, text("• هوية خاصة بدل رقم الهاتف", 14f, navy), -1, 28)
        add(updates, text("• محادثات فردية لحظية عبر الإنترنت", 14f, navy), -1, 28)
        add(updates, text("• العربية أولًا، والخصوصية في الأساس", 14f, navy), -1, 28)
        add(content, updates, -1, 120, 0f, 8)
        add(content, button("إنشاء هوية جديدة", { showCreateProfile() }), -1, 56, 0f, 8)
        add(content, outlineButton("لديّ حساب بالفعل", { showLogin() }), -1, 52, 0f, 4)
        add(content, text("وَثاق • خصوصية • هوية • محادثات", 12f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 40, 0f, 8)
        scroll.addView(content)
        add(r, scroll, -1, 0, 1f)
        setContentView(r)
    }

    private fun showCreateProfile() {
        val r = root()
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(18), dp(22), dp(24)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(content, header("إنشاء هوية وَثاق", "اسمك + سنة ميلادك = معرّفك", { showWelcome() }))
        add(content, avatar(true), 88, 88, 0f, 6)
        val name = field("الاسم الكامل الظاهر")
        val year = field("سنة الميلاد، مثال 1995", numeric = true)
        val password = field("كلمة مرور قوية، 8 أحرف على الأقل", password = true)
        add(content, text("الاسم", 14f, navy, true), -1, 28, 0f, 4)
        add(content, name, -1, 54, 0f, 4)
        add(content, text("سنة الميلاد", 14f, navy, true), -1, 28, 0f, 4)
        add(content, year, -1, 54, 0f, 4)
        add(content, text("كلمة المرور", 14f, navy, true), -1, 28, 0f, 4)
        add(content, password, -1, 54, 0f, 4)
        val preview = text("المعرّف المقترح: اكتب الاسم وسنة الميلاد", 14f, teal, true)
        preview.gravity = Gravity.CENTER
        preview.textAlignment = View.TEXT_ALIGNMENT_CENTER
        add(content, preview, -1, 48, 0f, 5)
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview(name, year, preview) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        name.addTextChangedListener(watcher); year.addTextChangedListener(watcher)
        add(content, text("المعرّف قابل للمشاركة بدل رقم الهاتف. سنة الميلاد جزء من المعرّف، لذلك لا تشاركها إلا مع من تثق بهم.", 12f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 62, 0f, 4)
        add(content, button("إنشاء الحساب", {
            val n = name.text.toString().trim(); val y = year.text.toString().trim(); val p = password.text.toString()
            if (n.length < 2) { toast("اكتب اسمًا صحيحًا"); return@button }
            val yi = y.toIntOrNull()
            val current = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            if (yi == null || yi !in 1900..current) { toast("سنة الميلاد غير صحيحة"); return@button }
            if (p.length < 8) { toast("كلمة المرور يجب أن تكون 8 أحرف على الأقل"); return@button }
            setBusy(content, true)
            Api.register(n, yi, p) { ok, data ->
                main.post {
                    setBusy(content, false)
                    if (ok) { saveSession(data); showApp() } else toast(apiError(data))
                }
            }
        }), -1, 56, 0f, 10)
        add(content, outlineButton("لديّ حساب بالفعل", { showLogin() }), -1, 50, 0f, 4)
        scroll.addView(content); add(r, scroll, -1, 0, 1f); setContentView(r)
    }

    private fun updatePreview(name: EditText, year: EditText, out: TextView) {
        val n = name.text.toString().trim(); val y = year.text.toString().trim()
        out.text = if (n.isBlank() || y.isBlank()) "المعرّف المقترح: اكتب الاسم وسنة الميلاد" else "المعرّف المقترح: ${makeLocalId(n, y)}"
    }

    private fun showLogin() {
        val r = root(); val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(22)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(content, header("تسجيل الدخول", "استخدم معرّف وَثاق وكلمة المرور", { showWelcome() }))
        val id = field("معرّف وَثاق")
        val pass = field("كلمة المرور", password = true)
        add(content, text("المعرّف", 14f, navy, true), -1, 28, 0f, 4); add(content, id, -1, 54, 0f, 4)
        add(content, text("كلمة المرور", 14f, navy, true), -1, 28, 0f, 4); add(content, pass, -1, 54, 0f, 4)
        add(content, button("دخول", {
            val a=id.text.toString().trim(); val p=pass.text.toString(); if(a.isBlank()||p.isBlank()){toast("أدخل المعرّف وكلمة المرور");return@button}
            setBusy(content,true); Api.login(a,p){ok,data->main.post{setBusy(content,false);if(ok){saveSession(data);showApp()}else toast(apiError(data))}}
        }), -1, 56, 0f, 10)
        add(content, outlineButton("إنشاء حساب جديد", { showCreateProfile() }), -1, 50, 0f, 4)
        add(r, content, -1, 0, 1f); setContentView(r)
    }

    private fun saveSession(data: JSONObject) {
        val user = data.optJSONObject("user") ?: return
        prefs.edit().putString("token", data.optString("token"))
            .putString("id", user.optString("wethaq_id"))
            .putString("name", user.optString("name"))
            .putInt("birth_year", user.optInt("birth_year", 0))
            .putInt("numeric_id", user.optInt("id", -1)).apply()
        connectSocket()
    }

    private fun connectSocket() {
        val token = prefs.getString("token", "") ?: return
        if (token.isBlank() || apiBase.isBlank()) return
        socket?.close(1000, "reconnect")
        val url = apiBase.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/ws?token=" + java.net.URLEncoder.encode(token, "UTF-8")
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val o=JSONObject(text)
                    if(o.optString("type")=="message") {
                        val m=o.optJSONObject("message") ?: return
                        if(chatId.isNotBlank()) cacheIncoming(m, chatId)
                        main.post { if(chatId.isNotBlank()) showChat(chatId) else showChats() }
                    }
                } catch (_: Exception) {}
            }
        })
    }

    private fun currentUserNumericId(): Int = prefs.getInt("numeric_id", -1)

    private fun showApp() {
        if(chatId.isNotBlank()){showChat(chatId);return}
        when(page){"contacts"->showContacts();"profile"->showProfile();"settings"->showSettings();else->showChats()}
        if(page=="chats"||page=="contacts") refreshContactsSilently()
    }

    private fun showChats() {
        page="chats"; val r=root(); add(r,header("المحادثات","مرحبًا، ${prefs.getString("name","مستخدم وَثاق")}"))
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(16));layoutDirection=View.LAYOUT_DIRECTION_RTL}
        val cached=getCachedContacts(); if(cached.length()==0){val empty=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=rounded(Color.WHITE,18,line);setPadding(dp(20),dp(20),dp(20),dp(20))};add(empty,text("لا توجد محادثات بعد",20f,navy,true).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER},-1,40);add(empty,text("أضف جهة اتصال باستخدام معرّف وَثاق وابدأ أول محادثة.",14f,muted).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER},-1,60);add(empty,button("إضافة جهة اتصال",{showContacts()}),-1,52,0f,10);add(list,empty,-1,200,0f,6)} else for(i in 0 until cached.length()) add(list,contactRow(cached.getJSONObject(i)),-1,78,0f,5)
        val scroll=ScrollView(this);scroll.addView(list);add(r,scroll,-1,0,1f);add(r,nav("chats"),-1,76);setContentView(r)
    }

    private fun contactRow(c: JSONObject): View { val id=c.optString("wethaq_id",c.optString("id"));val name=c.optString("name",id);val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=rounded(Color.WHITE,16,line);setPadding(dp(12),dp(8),dp(12),dp(8));layoutDirection=View.LAYOUT_DIRECTION_RTL};add(row,avatar(),52,52);val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(12),0,0,0)};add(box,text(name,17f,navy,true),-1,28);add(box,text("@$id",12f,muted),-1,22);add(row,box,0,58,1f);row.setOnClickListener{showChat(id)};return row }

    private fun showContacts() {
        page="contacts";val r=root();add(r,header("جهات الاتصال","أضف الأشخاص باستخدام معرّف وَثاق"));val area=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(4),dp(14),dp(8));layoutDirection=View.LAYOUT_DIRECTION_RTL};val input=field("أدخل معرّف وَثاق");add(area,input,-1,54,0f,4);add(area,button("إضافة جهة اتصال",{val id=input.text.toString().trim();if(!Regex("^[A-Za-z0-9_]+[A-Za-z0-9_]*[0-9]{4,}$").matches(id)){toast("صيغة المعرّف غير صحيحة");return@button};setBusy(area,true);Api.addContact(id){ok,data->main.post{setBusy(area,false);if(ok){cacheContact(data.optJSONObject("contact")?:JSONObject());input.setText("");hideKeyboard(input);showContacts()}else toast(apiError(data))}}}),-1,52,0f,6);add(area,text("لا تحتاج إلى رقم هاتف. شارك المعرّف فقط.",13f,teal,true).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER},-1,34);val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(2),dp(14),dp(12));layoutDirection=View.LAYOUT_DIRECTION_RTL};val cached=getCachedContacts();for(i in 0 until cached.length())add(list,contactRow(cached.getJSONObject(i)),-1,78,0f,5);val scroll=ScrollView(this);scroll.addView(list);add(r,area);add(r,scroll,-1,0,1f);add(r,nav("contacts"),-1,76);setContentView(r) }

    private fun showChat(id:String){chatId=id;page="chats";val r=root();val contact=getCachedContacts().let{arr->var x=JSONObject();for(i in 0 until arr.length())if(arr.getJSONObject(i).optString("wethaq_id")==id)x=arr.getJSONObject(i);x};val name=contact.optString("name",id);add(r,header(name,"@$id",{chatId="";showChats()}));val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(8));layoutDirection=View.LAYOUT_DIRECTION_RTL};val scroll=ScrollView(this);scroll.isFillViewport=true;scroll.addView(list);add(r,scroll,-1,0,1f);val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8));layoutDirection=View.LAYOUT_DIRECTION_RTL;background=android.graphics.drawable.ColorDrawable(Color.WHITE)};val input=field("اكتب رسالتك");add(bar,input,0,52,1f,4);val send=button("إرسال",{val body=input.text.toString().trim();if(body.isBlank())return@button;input.setText("");Api.sendMessage(id,body){ok,data->main.post{if(!ok){toast(apiError(data));return@post};val m=data.optJSONObject("message")?:JSONObject();val a=getCachedMessages(id);a.put(m);saveMessages(id,a);addMessageBubble(list,m,false);scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}}});add(bar,send,84,52,0f,4);add(r,bar);setContentView(r);loadMessages(id,list,scroll) }

    private fun loadMessages(id:String,list:LinearLayout,scroll:ScrollView){val cached=getCachedMessages(id);renderMessages(list,cached);scroll.post{scroll.fullScroll(View.FOCUS_DOWN)};Api.messages(id){ok,data->main.post{if(ok){val arr=data.optJSONArray("messages")?:JSONArray();saveMessages(id,arr);renderMessages(list,arr);scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}else if(cached.length()==0)toast(apiError(data))}}}
    private fun renderMessages(list:LinearLayout,arr:JSONArray){list.removeAllViews();for(i in 0 until arr.length()){val m=arr.getJSONObject(i);addMessageBubble(list,m,m.optInt("sender_id")==currentUserNumericId())}}
    private fun addMessageBubble(list:LinearLayout,m:JSONObject,mine:Boolean){val b=text(m.optString("body"),15f,if(mine)Color.WHITE else navy,false);b.setPadding(dp(14),dp(10),dp(14),dp(10));b.background=rounded(if(mine)teal else Color.WHITE,18);val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=if(mine)Gravity.END else Gravity.START;setPadding(dp(4),dp(3),dp(4),dp(3));layoutDirection=View.LAYOUT_DIRECTION_RTL};add(row,b,-2,-2);add(list,row,-1,-2)}

    private fun showProfile(){page="profile";val r=root();add(r,header("ملفي","هويتك على وَثاق"));val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(20),dp(14),dp(20),dp(20));layoutDirection=View.LAYOUT_DIRECTION_RTL};add(c,avatar(true),112,112,0f,8);add(c,text(prefs.getString("name","مستخدم وَثاق"),22f,navy,true).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER},-1,42);val id=prefs.getString("id","")?:"";add(c,text("@$id",15f,teal,true).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER},-1,34);add(c,button("نسخ المعرّف",{copy(id);toast("تم نسخ المعرّف")},tealDark),-1,50,0f,6);add(c,text("سنة الميلاد: ${prefs.getInt("birth_year",0).takeIf{it>0}?:"غير متاحة"}",13f,muted).apply{gravity=Gravity.CENTER},-1,34,0f,4);add(r,c,-1,0,1f);add(r,nav("profile"),-1,76);setContentView(r)}

    private fun showSettings(){page="settings";val r=root();add(r,header("الإعدادات","التحكم بالحساب والاتصال"));val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(10),dp(16),dp(16));layoutDirection=View.LAYOUT_DIRECTION_RTL};add(c,text("حالة الاتصال",15f,navy,true),-1,34);val status=text(if(apiBase.isBlank())"غير مضبوط" else "الخادم: $apiBase",13f,muted);add(c,status,-1,42,0f,4);add(c,outlineButton("نسخ معرّفي",{copy(prefs.getString("id","")?:"");toast("تم نسخ المعرّف")}),-1,50,0f,4);add(c,button("تسجيل الخروج",{prefs.edit().remove("token").apply();socket?.close(1000,"logout");showWelcome()},danger),-1,52,0f,16);add(c,text("وَثاق 1.4.0\nهوية عربية • مراسلة لحظية • خصوصية أولًا",13f,muted).apply{gravity=Gravity.CENTER;textAlignment=View.TEXT_ALIGNMENT_CENTER},-1,70,0f,8);add(r,c,-1,0,1f);add(r,nav("settings"),-1,76);setContentView(r)}

    private fun nav(active:String):LinearLayout{val n=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(dp(8),dp(6),dp(8),dp(6));setBackgroundColor(Color.WHITE);layoutDirection=View.LAYOUT_DIRECTION_RTL};val items=listOf("المحادثات" to "chats","الجهات" to "contacts","ملفي" to "profile","الإعدادات" to "settings");for((label,key)in items){val v=if(key==active)button(label,{navigate(key)})else outlineButton(label,{navigate(key)});add(n,v,0,58,1f,3)};return n}
    private fun navigate(to:String){chatId="";page=to;showApp()}

    private fun refreshContactsSilently(){Api.contacts{ok,data->if(ok){val a=data.optJSONArray("contacts")?:JSONArray();saveContacts(a);main.post{if(page=="chats")showChats();else if(page=="contacts")showContacts()}}}}
    private fun cacheContact(c:JSONObject){val arr=getCachedContacts();val id=c.optString("wethaq_id");var found=false;for(i in 0 until arr.length())if(arr.getJSONObject(i).optString("wethaq_id")==id)found=true;if(!found)arr.put(c);saveContacts(arr)}
    private fun getCachedContacts():JSONArray=try{JSONArray(prefs.getString("contacts","[]")?:"[]")}catch(_:Exception){JSONArray()}
    private fun saveContacts(a:JSONArray){prefs.edit().putString("contacts",a.toString()).apply()}
    private fun cacheIncoming(m:JSONObject,contactId:String){if(contactId.isBlank())return;val a=getCachedMessages(contactId);a.put(m);saveMessages(contactId,a)}
    private fun getCachedMessages(id:String):JSONArray=try{JSONArray(prefs.getString("messages_$id","[]")?:"[]")}catch(_:Exception){JSONArray()}
    private fun saveMessages(id:String,a:JSONArray){prefs.edit().putString("messages_$id",a.toString()).apply()}

    private fun setBusy(parent:LinearLayout,busy:Boolean){parent.alpha=if(busy)0.65f else 1f;parent.isEnabled=!busy}
    private fun hideKeyboard(v:View){(getSystemService(INPUT_METHOD_SERVICE)as InputMethodManager).hideSoftInputFromWindow(v.windowToken,0)}
    private fun copy(v:String){(getSystemService(CLIPBOARD_SERVICE)as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Wethaq ID",v))}
    private fun toast(v:String){Toast.makeText(this,v,Toast.LENGTH_SHORT).show()}
    private fun apiError(o:JSONObject):String=when(o.optString("error")){"name_and_password_required"->"الاسم وكلمة المرور مطلوبان";"invalid_credentials"->"المعرّف أو كلمة المرور غير صحيحة";"wethaq_id_taken"->"هذا المعرّف مستخدم بالفعل";"user_not_found"->"لم يتم العثور على هذا المستخدم";"cannot_add_self"->"لا يمكنك إضافة نفسك";"invalid_birth_year"->"سنة الميلاد غير صحيحة";"rate_limited"->"محاولات كثيرة، حاول لاحقًا";"network_error"->"تعذر الاتصال بالخادم";else->o.optString("message",o.optString("error","تعذر تنفيذ العملية"))}

    private fun makeLocalId(name:String,year:String):String{val special=mapOf("حاتم" to "Hatem","حسين" to "Hussin","الحاج" to "Al_Haj","رمضان" to "Ramadan","عبد" to "Abd","محمد" to "Mohammad","أحمد" to "Ahmad","علي" to "Ali","خالد" to "Khaled","سارة" to "Sara","سما" to "Sama");val words=name.replace("ـ","").split(Regex("\\s+|[،,]"));val out=words.map{w->special[w]?:latinize(w)}.filter{it.isNotBlank()};return out.joinToString("_")+year}
    private fun latinize(s:String):String{val map=mapOf('ا' to "a",'أ' to "a",'إ' to "i",'آ' to "a",'ب' to "b",'ت' to "t",'ث' to "th",'ج' to "j",'ح' to "h",'خ' to "kh",'د' to "d",'ذ' to "dh",'ر' to "r",'ز' to "z",'س' to "s",'ش' to "sh",'ص' to "s",'ض' to "d",'ط' to "t",'ظ' to "z",'ع' to "a",'غ' to "gh",'ف' to "f",'ق' to "q",'ك' to "k",'ل' to "l",'م' to "m",'ن' to "n",'ه' to "h",'و' to "w",'ي' to "y",'ى' to "a",'ة' to "h");return buildString{for(ch in s){map[ch]?.let{append(it)}?:if(ch.isLetterOrDigit())append(ch)}}.replaceFirstChar{it.uppercase()}}

    private object Api {
        private lateinit var host:MainActivity
        fun bind(h:MainActivity){host=h}
        fun register(name:String,birthYear:Int,password:String,cb:(Boolean,JSONObject)->Unit){host.request("POST","/api/register",JSONObject().put("name",name).put("birthYear",birthYear).put("password",password),cb)}
        fun login(id:String,password:String,cb:(Boolean,JSONObject)->Unit){host.request("POST","/api/login",JSONObject().put("wethaqId",id).put("password",password),cb)}
        fun contacts(cb:(Boolean,JSONObject)->Unit){host.request("GET","/api/contacts",null,cb)}
        fun addContact(id:String,cb:(Boolean,JSONObject)->Unit){host.request("POST","/api/contacts",JSONObject().put("wethaqId",id),cb)}
        fun messages(id:String,cb:(Boolean,JSONObject)->Unit){host.request("GET","/api/messages/${java.net.URLEncoder.encode(id,"UTF-8")}",null,cb)}
        fun sendMessage(id:String,body:String,cb:(Boolean,JSONObject)->Unit){host.request("POST","/api/messages",JSONObject().put("to",id).put("body",body),cb)}
    }

    private fun request(method:String,path:String,body:JSONObject?,cb:(Boolean,JSONObject)->Unit){val token=prefs.getString("token","")?:"";val builder=Request.Builder().url(apiBase+path).addHeader("Accept","application/json");if(token.isNotBlank())builder.addHeader("Authorization","Bearer $token");if(body!=null)builder.method(method,body.toString().toRequestBody(jsonType))else builder.method(method,null);client.newCall(builder.build()).enqueue(object:okhttp3.Callback{override fun onFailure(call:okhttp3.Call,e:java.io.IOException){cb(false,JSONObject().put("error","network_error").put("message","تعذر الاتصال بالخادم"))};override fun onResponse(call:okhttp3.Call,response:Response){response.use{val raw=it.body?.string().orEmpty();val o=try{JSONObject(raw)}catch(_:Exception){JSONObject().put("error","server_error")};cb(it.isSuccessful,o)}}})}

    private object ColorDrawableCompat { fun white():android.graphics.drawable.ColorDrawable=android.graphics.drawable.ColorDrawable(Color.WHITE) }

    init { Api.bind(this) }
}
