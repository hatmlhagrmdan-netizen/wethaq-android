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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("wethaq", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val json = "application/json; charset=utf-8".toMediaType()
    private val navy = Color.rgb(13, 36, 53)
    private val teal = Color.rgb(0, 137, 123)
    private val bg = Color.rgb(247, 249, 250)
    private val muted = Color.rgb(95, 110, 121)
    private val border = Color.rgb(220, 228, 231)
    private var socket: WebSocket? = null
    private var activeId = ""
    private var activeName = ""
    private var messagesBox: LinearLayout? = null
    private var messagesScroll: ScrollView? = null

    private val token get() = prefs.getString("token", "") ?: ""
    private val myId get() = prefs.getString("wethaq_id", "") ?: ""
    private val myName get() = prefs.getString("name", "") ?: ""
    private val myYear get() = prefs.getInt("birth_year", 0)
    private val apiBase get() = (prefs.getString("api_base", "https://wethaq-backend.onrender.com") ?: "").trimEnd('/')

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (token.isBlank() || myId.isBlank()) welcome() else home()
    }

    override fun onDestroy() {
        socket?.close(1000, "closed")
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun text(s: String, size: Float = 16f, color: Int = navy, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL; includeFontPadding = false
        layoutDirection = View.LAYOUT_DIRECTION_RTL; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun center(s: String, size: Float = 16f, color: Int = navy, bold: Boolean = false) = text(s, size, color, bold).apply {
        gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    private fun button(s: String, action: () -> Unit, color: Int = teal) = TextView(this).apply {
        text = s; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        background = shape(color, 16); setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { action() }
    }

    private fun outline(s: String, action: () -> Unit) = TextView(this).apply {
        text = s; textSize = 14f; setTextColor(navy); gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        background = shape(Color.WHITE, 16, border); setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { action() }
    }

    private fun edit(hint: String, number: Boolean = false) = EditText(this).apply {
        this.hint = hint; textSize = 16f; setTextColor(navy); setHintTextColor(muted); setSingleLine(true)
        background = shape(Color.WHITE, 16, border); setPadding(dp(15), 0, dp(15), 0); gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL; textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }

    private fun add(parent: LinearLayout, view: View, width: Int = -1, height: Int = -2, weight: Float = 0f, margin: Int = 0) {
        val h = if (height == -2) LinearLayout.LayoutParams.WRAP_CONTENT else dp(height)
        val p = LinearLayout.LayoutParams(width, h, weight)
        if (margin > 0) p.setMargins(dp(margin), dp(margin), dp(margin), dp(margin))
        parent.addView(view, p)
    }

    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); layoutDirection = View.LAYOUT_DIRECTION_RTL; fitsSystemWindows = true
    }

    private fun avatar(size: Int = 58, founder: Boolean = false) = ImageView(this).apply {
        setImageResource(if (founder) R.drawable.profile_photo else android.R.drawable.ic_menu_myplaces)
        scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(if (founder) Color.WHITE else Color.rgb(235, 240, 242), 100, if (founder) teal else border)
        clipToOutline = true; contentDescription = if (founder) "صورة مؤسس وَثاق" else "صورة المستخدم"
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)); if (founder) setPadding(dp(2), dp(2), dp(2), dp(2))
    }

    private fun header(title: String, sub: String? = null, back: (() -> Unit)? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL
        val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(box, text(title, 23f, navy, true), -1, 34); if (sub != null) add(box, text(sub, 12f, muted), -1, 24)
        add(this, box, 0, 60, 1f); if (back != null) add(this, outline("رجوع", back), 76, 44, 0f, 4)
    }

    private fun welcome() {
        val r = root(); val scroll = ScrollView(this)
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(20), dp(22), dp(20), dp(28)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(c, avatar(132, true), 132, 132, 0f, 6); add(c, center("وَثاق", 40f, navy, true), -1, 56)
        add(c, center("تواصل عربي مستقل", 19f, muted, true), -1, 34); add(c, center("هوية شخصية بدون رقم هاتف وبدون كلمة مرور", 14f, muted), -1, 44)
        val founder = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(Color.WHITE, 20, border); setPadding(dp(12), dp(10), dp(12), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(founder, avatar(62, true), 62, 62); add(founder, text("المؤسس\nحاتم حسين الحاج رمضان\nمؤسس ومطور وَثاق", 14f, navy, true), 0, 64, 1f, 10); add(c, founder, -1, 84, 0f, 8)
        val features = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(Color.WHITE, 20, border); setPadding(dp(16), dp(12), dp(16), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(features, text("جاهز للتواصل", 17f, navy, true), -1, 30); add(features, text("• اسمك الكامل + سنة الميلاد\n• معرف تواصل فريد وقابل للمشاركة\n• بحث وإضافة جهات اتصال\n• محادثات فورية وحفظ للرسائل\n• ملف شخصي وتحديثات وإعدادات", 13f, muted), -1, 112); add(c, features, -1, 154, 0f, 4)
        add(c, button("إنشاء هويتي", { createIdentity() }), -1, 58, 0f, 8); add(c, center("لا توجد كلمة مرور ولا رقم هاتف", 12f, muted), -1, 30)
        scroll.addView(c); add(r, scroll, -1, 0, 1f); setContentView(r)
    }

    private fun createIdentity() {
        val r = root(); val scroll = ScrollView(this)
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(c, header("إنشاء الهوية", "الاسم الثلاثي وسنة الميلاد فقط", { welcome() })); add(c, avatar(92, true), 92, 92, 0f, 4)
        val name = edit("الاسم الثلاثي، مثال: حاتم حسين الحاج رمضان"); val year = edit("سنة الميلاد، مثال: 1995", true)
        add(c, text("الاسم الكامل", 14f, navy, true), -1, 28, 0f, 3); add(c, name, -1, 56, 0f, 3)
        add(c, text("سنة الميلاد", 14f, navy, true), -1, 28, 0f, 3); add(c, year, -1, 56, 0f, 3)
        val preview = center("سيظهر معرفك هنا", 14f, teal, true); preview.background = shape(Color.WHITE, 16, border); add(c, preview, -1, 58, 0f, 8)
        fun refreshPreview() { val n = name.text.toString().trim(); val y = year.text.toString().trim(); preview.text = if (n.isBlank() || y.isBlank()) "سيظهر معرفك هنا" else "@${localId(n, y)}" }
        name.addTextChangedListener { refreshPreview() }; year.addTextChangedListener { refreshPreview() }
        add(c, button("إنشاء معرف وَثاق", {
            val n = name.text.toString().trim(); val yi = year.text.toString().trim().toIntOrNull(); val words = n.split(Regex("\\s+")).filter { it.isNotBlank() }; val max = Calendar.getInstance().get(Calendar.YEAR)
            if (words.size < 3) { toast("اكتب الاسم الثلاثي على الأقل"); return@button }
            if (yi == null || yi !in 1900..max) { toast("سنة الميلاد غير صحيحة"); return@button }
            api("POST", "/api/identity", JSONObject().put("name", n).put("birthYear", yi).put("deviceKey", deviceKey())) { ok, body -> handler.post { if (ok) { saveIdentity(body); home() } else toast(error(body)) } }
        }), -1, 58, 0f, 8)
        scroll.addView(c); add(r, scroll, -1, 0, 1f); setContentView(r)
    }

    private fun localId(name: String, year: String): String {
        val known = mapOf("حاتم" to "Hatem", "حسين" to "Hussin", "الحاج" to "Al_Haj", "رمضان" to "Ramadan", "محمد" to "Mohammad", "أحمد" to "Ahmad", "علي" to "Ali", "خالد" to "Khaled", "سارة" to "Sara", "سما" to "Sama", "نور" to "Nour", "هدى" to "Huda", "وسام" to "Wisam", "يامن" to "Yamen", "هشام" to "Hisham", "أيمن" to "Ayman", "حسام" to "Hossam")
        val chars = mapOf('ا' to "a", 'أ' to "a", 'إ' to "i", 'آ' to "a", 'ب' to "b", 'ت' to "t", 'ث' to "th", 'ج' to "j", 'ح' to "h", 'خ' to "kh", 'د' to "d", 'ذ' to "dh", 'ر' to "r", 'ز' to "z", 'س' to "s", 'ش' to "sh", 'ص' to "s", 'ض' to "d", 'ط' to "t", 'ظ' to "z", 'ع' to "a", 'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ك' to "k", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ه' to "h", 'و' to "w", 'ي' to "y", 'ى' to "a", 'ة' to "h")
        val parts = name.replace("ـ", "").split(Regex("[\\s،,]+" )).filter { it.isNotBlank() }.map { word -> known[word] ?: word.map { ch -> chars[ch] ?: if (ch.isLetterOrDigit()) ch.toString() else "" }.joinToString("").replaceFirstChar { it.uppercase() } }.filter { it.isNotBlank() }
        return (parts.joinToString("_") + year).replace(Regex("[^A-Za-z0-9_]"), "_")
    }

    private fun deviceKey(): String { val old = prefs.getString("device_key", "") ?: ""; if (old.length >= 24) return old; val bytes = ByteArray(32); SecureRandom().nextBytes(bytes); val key = bytes.joinToString("") { "%02x".format(it) }; prefs.edit().putString("device_key", key).apply(); return key }

    private fun saveIdentity(body: JSONObject) { val u = body.optJSONObject("user") ?: return; prefs.edit().putString("token", body.optString("token")).putString("wethaq_id", u.optString("wethaq_id")).putString("server_id", u.optString("id")).putString("name", u.optString("name")).putInt("birth_year", u.optInt("birth_year")).apply() }

    private fun home() {
        connectSocket(); val r = root(); add(r, header("المحادثات", "مرحبًا $myName"), -1, 70)
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val search = edit("ابحث بالاسم أو معرف وَثاق"); val sr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(sr, search, 0, 54, 1f, 2); add(sr, button("بحث", { searchUsers(search.text.toString()) }), 78, 54, 0f, 2); add(c, sr, -1, 60); add(c, center("ابدأ محادثة بإضافة جهة اتصال", 13f, muted), -1, 44)
        val scroll = ScrollView(this); scroll.addView(c); add(r, scroll, -1, 0, 1f); add(r, nav("home"), -1, 64); setContentView(r); loadContacts(c)
    }

    private fun loadContacts(container: LinearLayout) { api("GET", "/api/contacts", null) { ok, body -> handler.post { if (!ok) { toast(error(body)); return@post }; val arr = body.optJSONArray("contacts") ?: JSONArray(); for (i in 0 until arr.length()) contactRow(container, arr.optJSONObject(i) ?: JSONObject()) } } }

    private fun contactRow(container: LinearLayout, u: JSONObject) {
        val id = u.optString("wethaq_id"); val name = u.optString("name"); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(Color.WHITE, 18, border); setPadding(dp(10), dp(9), dp(10), dp(9)); layoutDirection = View.LAYOUT_DIRECTION_RTL; setOnClickListener { openChat(id, name) } }
        add(row, avatar(52), 52, 52); val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }; add(b, text(name, 16f, navy, true), -1, 28); add(b, text("@$id", 11f, muted), -1, 22); add(row, b, 0, 52, 1f, 10); add(container, row, -1, 72, 0f, 4)
    }

    private fun searchUsers(q: String) { if (q.trim().length < 2) { toast("اكتب حرفين على الأقل"); return }; api("GET", "/api/search?q=${URLEncoder.encode(q.trim(), "UTF-8")}", null) { ok, body -> handler.post { if (!ok) { toast(error(body)); return@post }; val r = root(); add(r, header("نتائج البحث", "اختر شخصًا لإضافته", { home() })); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_RTL }; val arr = body.optJSONArray("users") ?: JSONArray(); if (arr.length() == 0) add(c, center("لا توجد نتائج", 15f, muted), -1, 70); for (i in 0 until arr.length()) searchRow(c, arr.optJSONObject(i) ?: JSONObject()); val s = ScrollView(this); s.addView(c); add(r, s, -1, 0, 1f); add(r, nav("home"), -1, 64); setContentView(r) } } }

    private fun searchRow(c: LinearLayout, u: JSONObject) { val id = u.optString("wethaq_id"); val name = u.optString("name"); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = shape(Color.WHITE, 18, border); setPadding(dp(10), dp(9), dp(10), dp(9)); layoutDirection = View.LAYOUT_DIRECTION_RTL }; add(row, avatar(52), 52, 52); add(row, text("$name\n@$id", 14f, navy, true), 0, 54, 1f, 10); add(row, button("إضافة", { addContact(id) }), 82, 48); add(c, row, -1, 70, 0f, 4) }

    private fun addContact(id: String) { if (id.isBlank()) { toast("أدخل معرف وَثاق"); return }; api("POST", "/api/contacts", JSONObject().put("wethaqId", id)) { ok, body -> handler.post { if (ok) { toast("تمت إضافة جهة الاتصال"); home() } else toast(error(body)) } } }

    private fun contacts() {
        val r = root(); add(r, header("جهات الاتصال", "أضف أشخاصًا باستخدام معرف وَثاق")); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val id = edit("أدخل معرف وَثاق"); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }; add(row, id, 0, 54, 1f, 2); add(row, button("إضافة", { addContact(id.text.toString().trim()) }), 82, 54, 0f, 2); add(c, row, -1, 60); add(c, center("مثال: Hatem_Hussin_Al_Haj_Ramadan1995", 12f, muted), -1, 42)
        val s = ScrollView(this); s.addView(c); add(r, s, -1, 0, 1f); add(r, nav("contacts"), -1, 64); setContentView(r); loadContacts(c)
    }

    private fun openChat(id: String, name: String) { activeId = id; activeName = name; chat() }

    private fun chat() {
        val r = root(); val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(9), dp(8), dp(9), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(top, avatar(48), 48, 48); add(top, text("$activeName\n@$activeId", 14f, navy, true), 0, 50, 1f, 10); add(top, outline("رجوع", { home() }), 72, 44); add(r, top, -1, 64)
        messagesScroll = ScrollView(this); messagesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }; messagesScroll!!.addView(messagesBox); add(r, messagesScroll!!, -1, 0, 1f)
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(Color.WHITE); setPadding(dp(7), dp(7), dp(7), dp(7)); layoutDirection = View.LAYOUT_DIRECTION_RTL }; val field = edit("اكتب رسالتك"); add(composer, field, 0, 54, 1f, 2); add(composer, button("إرسال", { send(field) }), 78, 54, 0f, 2); add(r, composer, -1, 68); setContentView(r); loadMessages()
    }

    private fun loadMessages() { api("GET", "/api/messages/${URLEncoder.encode(activeId, "UTF-8")}", null) { ok, body -> handler.post { if (!ok) { toast(error(body)); return@post }; render(body.optJSONArray("messages") ?: JSONArray()) } } }

    private fun render(arr: JSONArray) { val box = messagesBox ?: return; box.removeAllViews(); val mineId = prefs.getString("server_id", ""); for (i in 0 until arr.length()) { val m = arr.optJSONObject(i) ?: JSONObject(); bubble(m.optString("body"), m.optString("sender_id") == mineId, m.optString("created_at")) }; messagesScroll?.post { messagesScroll?.fullScroll(View.FOCUS_DOWN) } }

    private fun bubble(body: String, mine: Boolean, time: String) { val b = text(body, 15f, if (mine) Color.WHITE else navy); b.background = shape(if (mine) teal else Color.WHITE, 18, if (mine) null else border); b.setPadding(dp(14), dp(10), dp(14), dp(10)); val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = if (mine) Gravity.END else Gravity.START; layoutDirection = View.LAYOUT_DIRECTION_RTL }; wrap.addView(b); wrap.addView(text(if (time.length >= 16) time.substring(11, 16) else "", 10f, muted)); messagesBox?.addView(wrap, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }) }

    private fun send(field: EditText) { val body = field.text.toString().trim(); if (body.isBlank()) return; field.setText(""); hideKeyboard(field); api("POST", "/api/messages", JSONObject().put("to", activeId).put("body", body)) { ok, data -> handler.post { if (ok) { val m = data.optJSONObject("message") ?: JSONObject(); bubble(body, true, m.optString("created_at")); messagesScroll?.post { messagesScroll?.fullScroll(View.FOCUS_DOWN) } } else toast(error(data)) } } }

    private fun profile() {
        val r = root(); add(r, header("ملفي", "هويتك في وَثاق")); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(18), dp(12), dp(18), dp(20)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(c, avatar(124, true), 124, 124, 0f, 5); add(c, center(myName, 24f, navy, true), -1, 48); add(c, center("مواليد $myYear", 14f, muted), -1, 30)
        val id = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = shape(Color.WHITE, 20, border); layoutDirection = View.LAYOUT_DIRECTION_RTL }; add(id, center("معرف التواصل", 12f, teal, true), -1, 28); add(id, center("@$myId", 16f, navy, true), -1, 40); add(id, button("نسخ المعرف", { copy(myId); toast("تم نسخ المعرف") }), -1, 48, 0f, 3); add(c, id, -1, 130, 0f, 8)
        add(c, center("المؤسس: حاتم حسين الحاج رمضان", 13f, muted, true), -1, 42); val s = ScrollView(this); s.addView(c); add(r, s, -1, 0, 1f); add(r, nav("profile"), -1, 64); setContentView(r)
    }

    private fun updates() {
        val r = root(); add(r, header("التحديثات", "آخر أخبار وَثاق")); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val cards = listOf("هوية بلا كلمة مرور" to "إنشاء الحساب يعتمد على الاسم الثلاثي وسنة الميلاد فقط.", "معرف التواصل" to "يُنشأ المعرف تلقائيًا ويمكن مشاركته مع الآخرين.", "محادثات فورية" to "إرسال وحفظ الرسائل مع اتصال لحظي عند توفر الشبكة.", "الخصوصية" to "لا يطلب وَثاق رقم هاتف أو كلمة مرور.")
        for ((a, b) in cards) { val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(Color.WHITE, 18, border); setPadding(dp(15), dp(10), dp(15), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }; add(card, text(a, 17f, navy, true), -1, 30); add(card, text(b, 13f, muted), -1, 44); add(c, card, -1, 82, 0f, 5) }
        val s = ScrollView(this); s.addView(c); add(r, s, -1, 0, 1f); add(r, nav("updates"), -1, 64); setContentView(r)
    }

    private fun settings() {
        val r = root(); add(r, header("الإعدادات", "إدارة هوية وَثاق")); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(8), dp(15), dp(15)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(c, text("الحساب", 18f, navy, true), -1, 34); add(c, text("لا توجد كلمة مرور. يحتفظ التطبيق بمفتاح جهاز محلي لتأكيد ملكية الهوية.", 14f, muted), -1, 58, 0f, 4); add(c, button("نسخ معرفي", { copy(myId); toast("تم نسخ المعرف") }), -1, 52, 0f, 6); add(c, outline("مسح الهوية من هذا الجهاز", { prefs.edit().clear().apply(); socket?.close(1000, "logout"); welcome() }), -1, 52, 0f, 3); add(c, center("وَثاق • إصدار 3.0", 12f, muted), -1, 42, 0f, 10)
        val s = ScrollView(this); s.addView(c); add(r, s, -1, 0, 1f); add(r, nav("settings"), -1, 64); setContentView(r)
    }

    private fun nav(active: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setBackgroundColor(Color.WHITE); setPadding(dp(7), dp(7), dp(7), dp(7)); layoutDirection = View.LAYOUT_DIRECTION_RTL
        val items = listOf("المحادثات" to "home", "التحديثات" to "updates", "الجهات" to "contacts", "ملفي" to "profile", "الإعدادات" to "settings")
        for ((label, key) in items) { val v = if (key == active) button(label, { navigate(key) }) else outline(label, { navigate(key) }); v.textSize = 11f; add(this, v, 0, 50, 1f, 2) }
    }

    private fun navigate(key: String) { when (key) { "home" -> home(); "updates" -> updates(); "contacts" -> contacts(); "profile" -> profile(); "settings" -> settings() } }

    private fun connectSocket() {
        if (token.isBlank()) return
        socket?.close(1000, "reconnect")
        val base = apiBase.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        val req = Request.Builder().url("$base/ws?token=${URLEncoder.encode(token, "UTF-8")}").build()
        socket = http.newWebSocket(req, object : WebSocketListener() { override fun onOpen(ws: WebSocket, response: Response) {}; override fun onMessage(ws: WebSocket, text: String) { handler.post { if (activeId.isNotBlank()) loadMessages() } }; override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {} })
    }

    private fun api(method: String, path: String, body: JSONObject?, done: (Boolean, JSONObject) -> Unit) {
        val builder = Request.Builder().url(apiBase + path); if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        when (method) { "GET" -> builder.get(); "POST" -> builder.post((body?.toString() ?: "{}").toRequestBody(json)) }
        http.newCall(builder.build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { done(false, JSONObject().put("error", "network_error")) }
            override fun onResponse(call: okhttp3.Call, response: Response) { val raw = response.body?.string() ?: "{}"; val o = try { JSONObject(raw) } catch (_: Exception) { JSONObject().put("error", raw) }; done(response.isSuccessful, o) }
        })
    }

    private fun hideKeyboard(v: View) { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0) }
    private fun copy(s: String) { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Wethaq", s)) }
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }
    private fun error(o: JSONObject) = when (o.optString("error")) {
        "identity_claimed" -> "هذه الهوية مرتبطة بجهاز آخر."
        "invalid_identity" -> "البيانات غير صحيحة. استخدم اسمًا ثلاثيًا وسنة ميلاد صحيحة."
        "user_not_found" -> "المستخدم غير موجود."
        "cannot_add_self" -> "لا يمكنك إضافة نفسك."
        "invalid_message" -> "الرسالة غير صالحة."
        "network_error" -> "تعذر الاتصال بالخادم. تحقق من الإنترنت."
        "unauthorized", "invalid_token" -> "انتهت جلسة الهوية. أعد إنشاء الهوية على هذا الجهاز."
        "rate_limited" -> "تم تجاوز الحد المؤقت للمحاولات. حاول لاحقًا."
        else -> o.optString("error").ifBlank { "حدث خطأ غير متوقع." }
    }
}
