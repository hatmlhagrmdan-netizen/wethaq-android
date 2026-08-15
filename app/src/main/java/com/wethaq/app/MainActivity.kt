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
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("wethaq", Context.MODE_PRIVATE) }
    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val navy = Color.rgb(12, 34, 50)
    private val teal = Color.rgb(0, 132, 119)
    private val tealDark = Color.rgb(0, 105, 95)
    private val bg = Color.rgb(247, 249, 250)
    private val muted = Color.rgb(91, 107, 119)
    private val line = Color.rgb(220, 229, 232)
    private val white = Color.WHITE
    private val danger = Color.rgb(190, 52, 62)
    private var page = "chats"
    private var activeChatId = ""
    private var activeChatName = ""
    private var socket: WebSocket? = null
    private var chatMessages: LinearLayout? = null
    private var chatScroll: ScrollView? = null
    private val apiBase: String get() = (prefs.getString("api_base", "https://wethaq-backend.onrender.com") ?: "").trim().trimEnd('/')
    private val token: String get() = prefs.getString("token", "") ?: ""
    private val myName: String get() = prefs.getString("name", "") ?: ""
    private val myId: String get() = prefs.getString("wethaq_id", "") ?: ""
    private val myBirth: Int get() = prefs.getInt("birth_year", 0)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = white
        window.navigationBarColor = white
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (token.isBlank() || myId.isBlank()) showWelcome() else showApp()
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

    private fun txt(value: String, size: Float = 16f, color: Int = navy, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START; layoutDirection = View.LAYOUT_DIRECTION_RTL
        includeFontPadding = false; maxLines = 8
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun centerTxt(value: String, size: Float = 16f, color: Int = navy, bold: Boolean = false) = txt(value, size, color, bold).apply {
        gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    private fun bgShape(color: Int, radius: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun button(value: String, action: () -> Unit, fill: Int = teal) = TextView(this).apply {
        text = value; textSize = 15f; setTextColor(white); gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER; typeface = Typeface.DEFAULT_BOLD; includeFontPadding = false
        background = bgShape(fill, 16); setPadding(dp(12), dp(10), dp(12), dp(10)); isClickable = true
        setOnClickListener { action() }
    }

    private fun outline(value: String, action: () -> Unit) = TextView(this).apply {
        text = value; textSize = 14f; setTextColor(navy); gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER; typeface = Typeface.DEFAULT_BOLD
        background = bgShape(white, 16, line); setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { action() }
    }

    private fun input(hint: String, numeric: Boolean = false) = EditText(this).apply {
        this.hint = hint; textSize = 16f; setSingleLine(true); setTextColor(navy); setHintTextColor(muted)
        includeFontPadding = false; background = bgShape(white, 16, line); setPadding(dp(16), 0, dp(16), 0)
        gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        inputType = if (numeric) android.text.InputType.TYPE_CLASS_NUMBER else android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }

    private fun add(parent: LinearLayout, view: View, w: Int = -1, h: Int = -2, weight: Float = 0f, margin: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(w, if (h == -2) LinearLayout.LayoutParams.WRAP_CONTENT else dp(h), weight).apply {
            if (margin > 0) setMargins(dp(margin), dp(margin), dp(margin), dp(margin))
        })
    }

    private fun avatar(size: Int = 56, founder: Boolean = false) = ImageView(this).apply {
        setImageResource(R.drawable.profile)
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = bgShape(white, 100, if (founder) teal else line)
        clipToOutline = true; contentDescription = if (founder) "صورة مؤسس وَثاق" else "صورة المستخدم"
        if (founder) setPadding(dp(2), dp(2), dp(2), dp(2))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun header(title: String, subtitle: String? = null, back: (() -> Unit)? = null): LinearLayout {
        val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(b, txt(title, 23f, navy, true), -1, 34)
        if (subtitle != null) add(b, txt(subtitle, 13f, muted), -1, 24)
        add(h, b, 0, 62, 1f)
        if (back != null) add(h, outline("رجوع", back), 76, 44, 0f, 4)
        return h
    }

    private fun showWelcome() {
        val r = root(); val scroll = ScrollView(this).apply { isFillViewport = true }
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(22), dp(26), dp(22), dp(28)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(c, avatar(142, true), 142, 142, 0f, 6)
        add(c, centerTxt("وَثاق", 42f, navy, true), -1, 58, 0f, 4)
        add(c, centerTxt("تواصل عربي مستقل", 20f, muted, true), -1, 36)
        add(c, centerTxt("هوية رقمية باسمك وسنة ميلادك، دون كلمة مرور أو رقم هاتف", 15f, muted), -1, 58, 0f, 2)
        val founder = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = bgShape(white, 20, line); setPadding(dp(14), dp(10), dp(14), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(founder, avatar(64, true), 64, 64)
        val fb = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(12), 0, 0, 0) }
        add(fb, txt("المؤسس", 13f, teal, true), -1, 22)
        add(fb, txt("حاتم حسين الحاج رمضان", 17f, navy, true), -1, 28)
        add(fb, txt("مؤسس ومطور تطبيق وَثاق", 12f, muted), -1, 22)
        add(founder, fb, 0, 70, 1f)
        add(c, founder, -1, 88, 0f, 8)
        val features = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bgShape(white, 20, line); setPadding(dp(16), dp(12), dp(16), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(features, txt("منصة التواصل", 17f, navy, true), -1, 30)
        add(features, txt("• معرف فريد قابل للمشاركة بدل رقم الهاتف", 14f), -1, 28)
        add(features, txt("• محادثات فردية لحظية مع جهات الاتصال", 14f), -1, 28)
        add(features, txt("• بحث بالمعرف وإضافة جهات بسهولة", 14f), -1, 28)
        add(features, txt("• ملف شخصي وتحديثات وإعدادات وخصوصية", 14f), -1, 28)
        add(c, features, -1, 148, 0f, 6)
        add(c, button("إنشاء هويتي", { showCreateIdentity() }), -1, 56, 0f, 8)
        add(c, txt("لا توجد كلمة مرور. تُحفظ هوية الجهاز محليًا لتجنب فقدان الحساب.", 12f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 42)
        scroll.addView(c); add(r, scroll, -1, 0, 1f); setContentView(r)
    }

    private fun showCreateIdentity() {
        val r = root(); val scroll = ScrollView(this).apply { isFillViewport = true }
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(18), dp(22), dp(24)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(c, header("إنشاء هويتك", "الاسم وسنة الميلاد فقط", { showWelcome() }))
        add(c, avatar(92, true), 92, 92, 0f, 4)
        add(c, centerTxt("وَثاق", 30f, navy, true), -1, 44, 0f, 4)
        add(c, txt("اكتب اسمك الكامل كما تريد أن يظهر للآخرين. نستخدم الاسم وسنة الميلاد لإنشاء معرف التواصل.", 14f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 56, 0f, 2)
        val name = input("الاسم الكامل، مثال: حاتم حسين الحاج رمضان")
        val year = input("سنة الميلاد، مثال: 1995", true)
        add(c, txt("الاسم الكامل", 14f, navy, true), -1, 28, 0f, 3)
        add(c, name, -1, 56, 0f, 3)
        add(c, txt("سنة الميلاد", 14f, navy, true), -1, 28, 0f, 3)
        add(c, year, -1, 56, 0f, 3)
        val preview = centerTxt("المعرف: سيظهر هنا", 14f, teal, true)
        preview.background = bgShape(white, 16, line)
        add(c, preview, -1, 58, 0f, 8)
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { preview.text = makeIdPreview(name.text.toString(), year.text.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        name.addTextChangedListener(watcher); year.addTextChangedListener(watcher)
        add(c, button("إنشاء معرف وَثاق", {
            val n = name.text.toString().trim(); val y = year.text.toString().trim(); val yi = y.toIntOrNull(); val current = Calendar.getInstance().get(Calendar.YEAR)
            val words = n.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size < 3) { toast("اكتب الاسم الكامل بثلاث كلمات على الأقل"); return@button }
            if (yi == null || yi !in 1900..current) { toast("سنة الميلاد غير صحيحة"); return@button }
            setBusy(c, true)
            val device = getDeviceKey()
            api("POST", "/api/identity", JSONObject().put("name", n).put("birthYear", yi).put("deviceKey", device)) { ok, body ->
                main.post { setBusy(c, false); if (ok) { saveIdentity(body); showApp() } else toast(errorText(body)) }
            }
        }), -1, 58, 0f, 8)
        add(c, centerTxt("المعرف مثال: Hatem_Hussin_Al_Haj_Ramadan1995", 12f, muted), -1, 42)
        scroll.addView(c); add(r, scroll, -1, 0, 1f); setContentView(r)
    }

    private fun makeIdPreview(name: String, year: String): String {
        if (name.isBlank() || year.isBlank()) return "المعرف: سيظهر هنا"
        return "المعرف: ${makeLocalId(name, year)}"
    }

    private fun makeLocalId(name: String, year: String): String {
        val known = mapOf("حاتم" to "Hatem", "حسين" to "Hussin", "الحاج" to "Al_Haj", "رمضان" to "Ramadan", "محمد" to "Mohammad", "أحمد" to "Ahmad", "علي" to "Ali", "خالد" to "Khaled", "سارة" to "Sara", "سما" to "Sama", "نور" to "Nour", "هدى" to "Huda", "وسام" to "Wisam", "يامن" to "Yamen", "هشام" to "Hisham", "أيمن" to "Ayman", "حسام" to "Hossam")
        val map = mapOf('ا' to "a",'أ' to "a",'إ' to "i",'آ' to "a",'ب' to "b",'ت' to "t",'ث' to "th",'ج' to "j",'ح' to "h",'خ' to "kh",'د' to "d",'ذ' to "dh",'ر' to "r",'ز' to "z",'س' to "s",'ش' to "sh",'ص' to "s",'ض' to "d",'ط' to "t",'ظ' to "z",'ع' to "a",'غ' to "gh",'ف' to "f",'ق' to "q",'ك' to "k",'ل' to "l",'م' to "m",'ن' to "n",'ه' to "h",'و' to "w",'ي' to "y",'ى' to "a",'ة' to "h")
        val parts = name.replace("ـ", "").split(Regex("[\\s،,]+"), " ").filter { it.isNotBlank() }.map { w -> known[w] ?: w.mapNotNull { map[it] }.joinToString("").replaceFirstChar { it.uppercase() } }.filter { it.isNotBlank() }
        return (parts.joinToString("_") + year).replace(Regex("[^A-Za-z0-9_]"), "_")
    }

    private fun saveIdentity(body: JSONObject) {
        val u = body.optJSONObject("user") ?: return
        prefs.edit().putString("token", body.optString("token")).putString("wethaq_id", u.optString("wethaq_id")).putString("name", u.optString("name")).putInt("birth_year", u.optInt("birth_year")).apply()
    }

    private fun getDeviceKey(): String {
        val existing = prefs.getString("device_key", "") ?: ""
        if (existing.length >= 24) return existing
        val bytes = ByteArray(32); SecureRandom().nextBytes(bytes)
        val key = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("device_key", key).apply(); return key
    }

    private fun showApp() {
        page = "chats"; activeChatId = ""; connectSocket(); showChats()
    }

    private fun navBar(active: String): LinearLayout {
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; background = white; setPadding(dp(8), dp(7), dp(8), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val items = listOf("المحادثات" to "chats", "التحديثات" to "updates", "الجهات" to "contacts", "ملفي" to "profile", "الإعدادات" to "settings")
        items.forEach { (label,key) -> val b = if (key == active) button(label, { navigate(key) }) else outline(label, { navigate(key) }); b.textSize = 12f; add(nav,b,0,50,1f,2) }
        return nav
    }

    private fun basePage(title: String, subtitle: String? = null, active: String = page): LinearLayout {
        val r = root(); add(r, header(title, subtitle), -1, 70); return r
    }

    private fun showChats() {
        page = "chats"; val r = basePage("المحادثات", "تواصلك مع جهات وَثاق", "chats")
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val addRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val search = input("ابحث بالمعرف أو الاسم")
        add(addRow, search, 0, 54, 1f, 2); add(addRow, button("بحث", { searchUsers(search.text.toString()) }), 78, 54, 0f, 2)
        add(list, addRow, -1, 60)
        val info = centerTxt("أضف شخصًا من صفحة الجهات ثم ابدأ المحادثة.", 14f, muted); add(list, info, -1, 52, 0f, 3)
        val scroll = ScrollView(this); scroll.addView(list); add(r, scroll, -1, 0, 1f); add(r, navBar("chats"), -1, 64); setContentView(r)
        loadContactsInto(list, true)
    }

    private fun loadContactsInto(list: LinearLayout, asChats: Boolean) {
        api("GET", "/api/contacts", null) { ok, body -> main.post { if (!ok) { toast(errorText(body)); return@post }; val arr = body.optJSONArray("contacts") ?: JSONArray(); while (list.childCount > 2) list.removeViewAt(2); for(i in 0 until arr.length()) addContactRow(list, arr.optJSONObject(i) ?: JSONObject(), asChats) } }
    }

    private fun addContactRow(list: LinearLayout, u: JSONObject, asChats: Boolean) {
        val id = u.optString("wethaq_id"); val name = u.optString("name"); val online = u.optBoolean("online")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = bgShape(white, 18, line); setPadding(dp(12), dp(10), dp(12), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL; setOnClickListener { openChat(id,name) } }
        add(row, avatar(52), 52, 52)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(12),0,0,0) }
        add(box, txt(name, 17f, navy, true), -1, 28); add(box, txt(if(online) "متصل الآن" else "@${id}", 12f, if(online) teal else muted), -1, 24)
        add(row, box, 0, 54, 1f)
        add(list,row,-1,76,0f,4)
    }

    private fun searchUsers(query: String) {
        if(query.trim().length < 2){toast("اكتب حرفين على الأقل للبحث");return}
        api("GET", "/api/search?q=${java.net.URLEncoder.encode(query.trim(),"UTF-8")}", null) { ok, body -> main.post { if(!ok){toast(errorText(body));return@post}; showSearchResults(body.optJSONArray("users") ?: JSONArray()) } }
    }

    private fun showSearchResults(arr: JSONArray) {
        val r = basePage("نتائج البحث", "اختر شخصًا لإضافته", page)
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(12));layoutDirection=View.LAYOUT_DIRECTION_RTL}
        if(arr.length()==0)add(list,centerTxt("لا توجد نتائج مطابقة",15f,muted),-1,70)
        for(i in 0 until arr.length()){
            val u=arr.optJSONObject(i)?:JSONObject(); val id=u.optString("wethaq_id"); val name=u.optString("name")
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=bgShape(white,18,line);setPadding(dp(12),dp(10),dp(12),dp(10));layoutDirection=View.LAYOUT_DIRECTION_RTL}
            add(row,avatar(52),52,52); val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(12),0,0,0)}
            add(b,txt(name,16f,navy,true),-1,28);add(b,txt("@${id}",12f,muted),-1,24);add(row,b,0,54,1f);add(row,button("إضافة",{addContact(id)}),80,48);add(list,row,-1,72,0f,4)
        }
        val scroll=ScrollView(this);scroll.addView(list);add(r,scroll,-1,0,1f);add(r,navBar(page),-1,64);setContentView(r)
    }

    private fun addContact(id:String){api("POST","/api/contacts",JSONObject().put("wethaqId",id)){ok,body->main.post{if(ok){toast("تمت إضافة جهة الاتصال");showChats()}else toast(errorText(body))}}}

    private fun showContacts(){
        page="contacts";val r=basePage("جهات الاتصال","أضف الأشخاص باستخدام معرف وَثاق","contacts")
        val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(4),dp(14),dp(10));layoutDirection=View.LAYOUT_DIRECTION_RTL}
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL};val id=input("أدخل معرف وَثاق");add(row,id,0,54,1f,2);add(row,button("إضافة جهة",{addContact(id.text.toString().trim())}),104,54,0f,2);add(c,row,-1,60)
        val help=centerTxt("مثال: Hatem_Hussin_Al_Haj_Ramadan1995",12f,muted);add(c,help,-1,38)
        val scroll=ScrollView(this);scroll.addView(c);add(r,scroll,-1,0,1f);add(r,navBar("contacts"),-1,64);setContentView(r);loadContactsInto(c,false)
    }

    private fun openChat(id:String,name:String){activeChatId=id;activeChatName=name;showChat()}

    private fun showChat(){
        val r=root();val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=white;setPadding(dp(10),dp(8),dp(10),dp(8));layoutDirection=View.LAYOUT_DIRECTION_RTL}
        add(top,avatar(48),48,48);val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL;setPadding(dp(10),0,0,0)};add(b,txt(activeChatName,18f,navy,true),-1,27);add(b,txt("@${activeChatId}",11f,muted),-1,20);add(top,b,0,50,1f);add(top,outline("رجوع",{showChats()}),70,44,0f);add(r,top,-1,66)
        chatScroll=ScrollView(this).apply{isFillViewport=true};chatMessages=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.BOTTOM;setPadding(dp(12),dp(12),dp(12),dp(12));layoutDirection=View.LAYOUT_DIRECTION_RTL};chatScroll!!.addView(chatMessages);add(r,chatScroll!!,-1,0,1f)
        val composer=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=white;setPadding(dp(8),dp(8),dp(8),dp(8));layoutDirection=View.LAYOUT_DIRECTION_RTL};val message=input("اكتب رسالتك");add(composer,message,0,54,1f,2);add(composer,button("إرسال",{sendMessage(message)}),78,54,0f,2);add(r,composer,-1,70);setContentView(r);loadMessages()
    }

    private fun loadMessages(){api("GET","/api/messages/${java.net.URLEncoder.encode(activeChatId,"UTF-8")}",null){ok,body->main.post{if(!ok){toast(errorText(body));return@post};renderMessages(body.optJSONArray("messages")?:JSONArray())}}

    private fun renderMessages(arr:JSONArray){val box=chatMessages?:return;box.removeAllViews();for(i in 0 until arr.length()){val m=arr.optJSONObject(i)?:JSONObject();addMessageBubble(box,m.optString("body"),m.optInt("sender_id").toString()==prefs.getString("server_id",""),m.optString("created_at"))};chatScroll?.post{chatScroll?.fullScroll(View.FOCUS_DOWN)}}

    private fun addMessageBubble(box:LinearLayout,body:String,mine:Boolean,time:String){val wrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=if(mine)Gravity.END else Gravity.START;layoutDirection=View.LAYOUT_DIRECTION_RTL};val bubble=txt(body,15f,if(mine)white else navy);bubble.background=bgShape(if(mine)teal else white,18,if(mine)null else line);bubble.setPadding(dp(14),dp(10),dp(14),dp(10));bubble.maxWidth=dp(300);wrap.addView(bubble);val t=txt(if(time.length>=16)time.substring(11,16) else "",10f,muted);t.gravity=if(mine)Gravity.END else Gravity.START;wrap.addView(t);box.addView(wrap,LinearLayout.LayoutParams(-1,-2).apply{setMargins(dp(4),dp(4),dp(4),dp(4))})}

    private fun sendMessage(field:EditText){val body=field.text.toString().trim();if(body.isBlank())return;field.setText("");hideKeyboard(field);api("POST","/api/messages",JSONObject().put("to",activeChatId).put("body",body)){ok,data->main.post{if(!ok){toast(errorText(data));return@post};val m=data.optJSONObject("message")?:return@post;addMessageBubble(chatMessages?:return@post,body,true,m.optString("created_at"));chatScroll?.post{chatScroll?.fullScroll(View.FOCUS_DOWN)}}}}

    private fun showUpdates(){page="updates";val r=basePage("التحديثات","آخر ما وصل إلى وَثاق","updates");val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(6),dp(14),dp(12));layoutDirection=View.LAYOUT_DIRECTION_RTL};val cards=listOf("وَثاق 2.0" to "هوية بدون كلمة مرور، بالاسم وسنة الميلاد ومعرف قابل للمشاركة.","المحادثات" to "إرسال واستقبال الرسائل لحظيًا مع حفظ سجل المحادثة.","الجهات" to "بحث بالاسم أو المعرف وإضافة الأشخاص إلى قائمة جهات الاتصال.","الخصوصية" to "لا نطلب رقم هاتف. مفتاح الجهاز يُحفظ محليًا للمحافظة على هوية الحساب.","واجهة عربية" to "تصميم RTL واضح ومتجاوب ومناسب لشاشات Android.");for((t,d)in cards){val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=bgShape(white,18,line);setPadding(dp(16),dp(12),dp(16),dp(12));layoutDirection=View.LAYOUT_DIRECTION_RTL};add(card,txt(t,17f,navy,true),-1,30);add(card,txt(d,13f,muted),-1,48);add(c,card,-1,88,0f,5)};val scroll=ScrollView(this);scroll.addView(c);add(r,scroll,-1,0,1f);add(r,navBar("updates"),-1,64);setContentView(r)}

    private fun showProfile(){page="profile";val r=basePage("ملفي","هويتك في وَثاق","profile");val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(18),dp(12),dp(18),dp(18));layoutDirection=View.LAYOUT_DIRECTION_RTL};add(c,avatar(118),118,118,0f,4);add(c,centerTxt(myName,24f,navy,true),-1,48,0f,4);add(c,centerTxt("مواليد $myBirth",14f,muted),-1,32);val idBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=bgShape(white,20,line);setPadding(dp(12),dp(12),dp(12),dp(12));layoutDirection=View.LAYOUT_DIRECTION_RTL};add(idBox,centerTxt("معرف التواصل",12f,teal,true),-1,26);add(idBox,centerTxt("@$myId",16f,navy,true),-1,40);add(idBox,button("نسخ المعرف",{copy(myId);toast("تم نسخ المعرف")}),-1,48,0f,4);add(c,idBox,-1,130,0f,10);val founder=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=bgShape(white,18,line);setPadding(dp(12),dp(10),dp(12),dp(10));layoutDirection=View.LAYOUT_DIRECTION_RTL};add(founder,avatar(58,true),58,58);add(founder,txt("وَثاق\nمؤسس التطبيق: حاتم حسين الحاج رمضان",14f,navy,true),0,58,1f,10);add(c,founder,-1,80,0f,5);val scroll=ScrollView(this);scroll.addView(c);add(r,scroll,-1,0,1f);add(r,navBar("profile"),-1,64);setContentView(r)}

    private fun showSettings(){page="settings";val r=basePage("الإعدادات","التحكم في تجربة وَثاق","settings");val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(16));layoutDirection=View.LAYOUT_DIRECTION_RTL};val rows=listOf("الخصوصية" to "لا نستخدم رقم هاتف لإنشاء الهوية.","الهوية" to "المعرف مرتبط باسمك وسنة ميلادك على الخادم.","الجهاز" to "مفتاح الجهاز محفوظ محليًا ولا يظهر في الملف الشخصي.","الإشعارات" to "الرسائل الفورية تعتمد على اتصال التطبيق بالخادم.");for((a,b)in rows){val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;background=bgShape(white,18,line);setPadding(dp(16),dp(10),dp(16),dp(10));layoutDirection=View.LAYOUT_DIRECTION_RTL};add(row,txt(a,16f,navy,true),-1,28);add(row,txt(b,13f,muted),-1,38);add(c,row,-1,78,0f,5)};add(c,button("نسخ معرفي",{copy(myId);toast("تم نسخ المعرف")}),-1,52,0f,6);add(c,outline("مسح الهوية من هذا الجهاز",{prefs.edit().clear().apply();socket?.close(1000,"logout");showWelcome()}),-1,52,0f,3);add(c,centerTxt("وَثاق • إصدار 2.0.0",12f,muted),-1,40,0f,10);val scroll=ScrollView(this);scroll.addView(c);add(r,scroll,-1,0,1f);add(r,navBar("settings"),-1,64);setContentView(r)}

    private fun navigate(key:String){when(key){"chats"->showChats();"updates"->showUpdates();"contacts"->showContacts();"profile"->showProfile();"settings"->showSettings()}}

    private fun connectSocket(){if(token.isBlank())return;socket?.close(1000,"reconnect");val wsUrl=apiBase.replaceFirst("https://","wss://").replaceFirst("http://","ws://")+"/ws?token=${java.net.URLEncoder.encode(token,"UTF-8")}";val req=Request.Builder().url(wsUrl).build();socket=client.newWebSocket(req,object:WebSocketListener(){override fun onOpen(webSocket:WebSocket,response:Response){ }override fun onMessage(webSocket:WebSocket,text:String){try{val o=JSONObject(text);if(o.optString("type")=="message"){val m=o.optJSONObject("message")?:return;main.post{if(activeChatId.isNotBlank()&&m.optString("sender_id")!=prefs.getString("server_id","")&&activeChatId.isNotBlank()){loadMessages()}}}}catch(_:Exception){}}override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){} })}

    private fun api(method:String,path:String,body:JSONObject?,callback:(Boolean,JSONObject)->Unit){val b=body?.toString()?.toRequestBody(jsonType);val builder=Request.Builder().url(apiBase+path);if(token.isNotBlank())builder.header("Authorization","Bearer $token");when(method){"GET"->builder.get();"POST"->builder.post(b?:"{}".toRequestBody(jsonType));"PUT"->builder.put(b?:"{}".toRequestBody(jsonType))};client.newCall(builder.build()).enqueue(object:okhttp3.Callback{override fun onFailure(call:okhttp3.Call,e:java.io.IOException){callback(false,JSONObject().put("error","network_error"))}override fun onResponse(call:okhttp3.Call,response:Response){val raw=response.body?.string()?:("{}");val obj=try{JSONObject(raw)}catch(_:Exception){JSONObject().put("error",raw)};callback(response.isSuccessful,obj)}})}

    private fun setBusy(parent:LinearLayout,busy:Boolean){parent.isEnabled=!busy;for(i in 0 until parent.childCount)parent.getChildAt(i).isEnabled=!busy}
    private fun hideKeyboard(v:View){(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken,0)}
    private fun copy(value:String){(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Wethaq",value))}
    private fun toast(value:String){Toast.makeText(this,value,Toast.LENGTH_LONG).show()}
    private fun errorText(o:JSONObject):String=when(o.optString("error")){"identity_claimed"->"هذا المعرف مرتبط بجهاز آخر.";"invalid_identity"->"تحقق من الاسم الكامل وسنة الميلاد.";"user_not_found"->"لم يتم العثور على المستخدم.";"cannot_add_self"->"لا يمكنك إضافة نفسك.";"network_error"->"تعذر الاتصال بالخادم. تحقق من الإنترنت.";"rate_limited"->"تم تجاوز عدد المحاولات، حاول لاحقًا.";else->o.optString("message",o.optString("error","حدث خطأ غير متوقع"))}
}
