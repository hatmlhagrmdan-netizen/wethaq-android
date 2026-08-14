package com.wethaq.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("wethaq", Context.MODE_PRIVATE) }
    private val navy = Color.rgb(17, 31, 49)
    private val teal = Color.rgb(0, 137, 123)
    private val bg = Color.rgb(246, 248, 250)
    private val muted = Color.rgb(96, 108, 120)
    private val white = Color.WHITE
    private var page = "chats"
    private var chatId = ""

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (prefs.getBoolean("onboarded", false)) showApp() else showWelcome()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun label(value: String, size: Float = 16f, color: Int = navy, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(value: String, action: () -> Unit, color: Int = teal) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(white)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setBackgroundColor(color)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        setOnClickListener { action() }
    }

    private fun params(height: Int, margin: Int = 6) = LinearLayout.LayoutParams(-1, height).apply {
        setMargins(dp(margin), dp(margin), dp(margin), dp(margin))
    }

    private fun field(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        textSize = 16f
        setSingleLine(true)
        setTextColor(navy)
        setHintTextColor(muted)
        setPadding(dp(14), 0, dp(14), 0)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun header(title: String, subtitle: String? = null, back: (() -> Unit)? = null): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(6))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        if (back != null) h.addView(button("‹", back, navy), LinearLayout.LayoutParams(dp(50), dp(48)))
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        box.addView(label(title, 25f, navy, true), LinearLayout.LayoutParams(-1, dp(38)))
        if (subtitle != null) box.addView(label(subtitle, 13f, muted), LinearLayout.LayoutParams(-1, dp(24)))
        h.addView(box, LinearLayout.LayoutParams(0, dp(62), 1f))
        return h
    }

    private fun showWelcome() {
        val r = root()
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(20), dp(28), 0) }
        val photo = ImageView(this).apply { setImageResource(R.drawable.profile); scaleType = ImageView.ScaleType.CENTER_CROP }
        top.addView(photo, LinearLayout.LayoutParams(dp(120), dp(120)).apply { gravity = Gravity.CENTER })
        top.addView(label("وَثاق", 40f, navy, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, dp(70)))
        top.addView(label("تواصل عربي مستقل\nبهوية لا تحتاج إلى رقم هاتف", 17f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, dp(70)))
        r.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(28)) }
        bottom.addView(button("بدء الاستخدام", { showCreateProfile() }), LinearLayout.LayoutParams(-1, dp(56)))
        bottom.addView(label("خصوصية أولاً • هوية ثلاثية • محادثات", 13f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, dp(48)))
        r.addView(bottom)
        setContentView(r)
    }

    private fun showCreateProfile() {
        val r = root()
        r.setPadding(dp(20), dp(24), dp(20), dp(20))
        r.addView(header("إنشاء هوية وَثاق", "لن نطلب رقم هاتفك"))
        val name = field("الاسم الظاهر")
        r.addView(name, params(dp(56), 8))
        r.addView(label("سيُنشأ معرّفك الثلاثي تلقائيًا", 16f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, params(dp(64), 8))
        r.addView(button("إنشاء هويتي", {
            val n = name.text.toString().trim()
            if (n.length < 2) Toast.makeText(this, "اكتب اسمًا من حرفين على الأقل", Toast.LENGTH_SHORT).show()
            else {
                prefs.edit().putBoolean("onboarded", true).putString("name", n).putString("id", generateId()).apply()
                showApp()
            }
        }), params(dp(56), 12))
        r.addView(button("رجوع", { showWelcome() }, navy), params(dp(52), 6))
        setContentView(r)
    }

    private fun generateId(): String {
        val words = listOf("nour", "sama", "wafa", "aman", "sham", "ward", "zain", "fajr", "sabr", "huda")
        return "${words.random()}-${words.random()}-${Random.nextInt(1000, 10000)}"
    }

    private fun showApp() {
        if (chatId.isNotEmpty()) { showChat(chatId); return }
        when (page) {
            "contacts" -> showContacts()
            "profile" -> showProfile()
            "settings" -> showSettings()
            else -> showChats()
        }
    }

    private fun showChats() {
        page = "chats"
        val r = root()
        r.addView(header("المحادثات", "مرحبًا، ${prefs.getString("name", "مستخدم وَثاق")}"))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val contacts = getContacts()
        if (contacts.length() == 0) {
            list.addView(label("لا توجد محادثات بعد\nاذهب إلى جهات الاتصال وأضف شخصًا للبدء.", 18f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, params(dp(150), 8))
        } else {
            for (i in 0 until contacts.length()) list.addView(contactRow(contacts.getJSONObject(i)), params(dp(70), 5))
        }
        val scroll = ScrollView(this); scroll.addView(list)
        r.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        r.addView(nav("chats"))
        setContentView(r)
    }

    private fun contactRow(c: JSONObject): View {
        val id = c.optString("id")
        val name = c.optString("name", id)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); setBackgroundColor(white); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val avatar = label(name.take(1), 18f, white, true).apply { gravity = Gravity.CENTER; setBackgroundColor(teal) }
        row.addView(avatar, LinearLayout.LayoutParams(dp(52), dp(52)))
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        box.addView(label(name, 17f, navy, true), LinearLayout.LayoutParams(-1, dp(30)))
        box.addView(label("@$id", 13f, muted), LinearLayout.LayoutParams(-1, dp(24)))
        row.addView(box, LinearLayout.LayoutParams(0, dp(60), 1f))
        row.setOnClickListener { showChat(id) }
        return row
    }

    private fun showContacts() {
        page = "contacts"
        val r = root()
        r.addView(header("جهات الاتصال", "معرّفك: @${prefs.getString("id", "")}"))
        val input = field("اكتب معرّف وَثاق")
        r.addView(input, params(dp(52), 8))
        r.addView(button("إضافة جهة اتصال", {
            val id = input.text.toString().trim()
            if (id.length < 5) Toast.makeText(this, "أدخل معرّفًا صحيحًا", Toast.LENGTH_SHORT).show()
            else { addContact(id, id); showContacts() }
        }), params(dp(52), 6))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val contacts = getContacts()
        for (i in 0 until contacts.length()) list.addView(contactRow(contacts.getJSONObject(i)), params(dp(70), 5))
        val scroll = ScrollView(this); scroll.addView(list)
        r.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        r.addView(nav("contacts"))
        setContentView(r)
    }

    private fun showChat(id: String) {
        chatId = id
        val contact = findContact(id)
        val name = contact?.optString("name", id) ?: id
        val r = root()
        r.addView(header(name, "@$id", { chatId = ""; showChats() }))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val messages = getMessages(id)
        if (messages.length() == 0) list.addView(label("ابدأ أول رسالة في هذه المحادثة", 16f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, params(dp(100), 8))
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val mine = m.optBoolean("mine")
            val bubble = label(m.optString("text"), 15f, if (mine) white else navy)
            bubble.setPadding(dp(14), dp(10), dp(14), dp(10))
            bubble.setBackgroundColor(if (mine) teal else white)
            list.addView(bubble, params(-2, 4))
        }
        val scroll = ScrollView(this); scroll.addView(list)
        r.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val input = field("اكتب رسالتك")
        val send = button("إرسال", {
            val message = input.text.toString().trim()
            if (message.isNotEmpty()) { addMessage(id, message); input.setText(""); showChat(id) }
        })
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        composer.addView(send, LinearLayout.LayoutParams(dp(82), dp(52)))
        r.addView(composer)
        setContentView(r)
    }

    private fun showProfile() {
        page = "profile"
        val r = root()
        r.addView(header("ملفي الشخصي"))
        val image = ImageView(this).apply { setImageResource(R.drawable.profile); scaleType = ImageView.ScaleType.CENTER_CROP }
        r.addView(image, LinearLayout.LayoutParams(dp(150), dp(150)).apply { gravity = Gravity.CENTER; setMargins(0, dp(20), 0, dp(20)) })
        r.addView(label(prefs.getString("name", "حاتم حسين الحاج رمضان") ?: "حاتم حسين الحاج رمضان", 24f, navy, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, params(dp(50), 4))
        r.addView(label("@${prefs.getString("id", "")}", 16f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, params(dp(40), 4))
        r.addView(button("نسخ المعرّف", { copyId() }), params(dp(52), 12))
        r.addView(nav("profile"))
        setContentView(r)
    }

    private fun showSettings() {
        page = "settings"
        val r = root()
        r.addView(header("الإعدادات"))
        r.addView(label("وَثاق\nالإصدار 1.0.0\n\nالرسائل الحالية محفوظة محليًا على الجهاز. ربط الخادم سيتم عند نشر Backend.", 16f, muted).apply { setPadding(dp(20), dp(20), dp(20), dp(20)) }, params(dp(180), 8))
        r.addView(button("إعادة إنشاء الهوية", { prefs.edit().clear().apply(); chatId = ""; showWelcome() }, Color.rgb(198, 40, 40)), params(dp(52), 8))
        r.addView(nav("settings"))
        setContentView(r)
    }

    private fun nav(active: String): LinearLayout {
        val n = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(6), dp(6), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        n.addView(button("المحادثات", { page = "chats"; showChats() }, if (active == "chats") teal else navy), LinearLayout.LayoutParams(0, dp(50), 1f))
        n.addView(button("جهات الاتصال", { page = "contacts"; showContacts() }, if (active == "contacts") teal else navy), LinearLayout.LayoutParams(0, dp(50), 1f))
        n.addView(button("ملفي", { page = "profile"; showProfile() }, if (active == "profile") teal else navy), LinearLayout.LayoutParams(0, dp(50), 1f))
        n.addView(button("الإعدادات", { page = "settings"; showSettings() }, if (active == "settings") teal else navy), LinearLayout.LayoutParams(0, dp(50), 1f))
        return n
    }

    private fun copyId() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Wethaq ID", prefs.getString("id", "")))
        Toast.makeText(this, "تم نسخ المعرّف", Toast.LENGTH_SHORT).show()
    }

    private fun getContacts(): JSONArray = JSONArray(prefs.getString("contacts", "[]") ?: "[]")
    private fun addContact(id: String, name: String) {
        val a = getContacts()
        var exists = false
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("id") == id) exists = true
        if (!exists) a.put(JSONObject().apply { put("id", id); put("name", name) })
        prefs.edit().putString("contacts", a.toString()).apply()
    }
    private fun findContact(id: String): JSONObject? {
        val a = getContacts()
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("id") == id) return a.getJSONObject(i)
        return null
    }
    private fun messagesKey(id: String) = "messages_$id"
    private fun getMessages(id: String): JSONArray = JSONArray(prefs.getString(messagesKey(id), "[]") ?: "[]")
    private fun addMessage(id: String, value: String) {
        val a = getMessages(id)
        a.put(JSONObject().apply { put("text", value); put("mine", true) })
        prefs.edit().putString(messagesKey(id), a.toString()).apply()
    }
}
