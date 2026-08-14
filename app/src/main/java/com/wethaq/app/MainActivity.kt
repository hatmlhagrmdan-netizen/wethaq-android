package com.wethaq.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("wethaq", Context.MODE_PRIVATE) }
    private val navy = Color.rgb(17, 31, 49)
    private val teal = Color.rgb(0, 137, 123)
    private val tealDark = Color.rgb(0, 105, 92)
    private val bg = Color.rgb(246, 248, 250)
    private val card = Color.WHITE
    private val muted = Color.rgb(96, 108, 120)
    private val danger = Color.rgb(198, 40, 40)

    private var activePage = "chats"
    private var activeContactId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!prefs.getBoolean("onboarded", false)) showWelcome() else showApp()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun root(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun text(value: String, size: Float = 16f, color: Int = navy, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) typeface = Typeface.DEFAULT_BOLD
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun pillButton(label: String, action: () -> Unit, color: Int = teal): TextView = TextView(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(color, 18)
        setPadding(dp(18), dp(11), dp(18), dp(11))
        setOnClickListener { action() }
    }

    private fun rounded(color: Int, radius: Int = 16): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun cardView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(card)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun showWelcome() {
        val r = root()
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(28), dp(24), dp(28), 0) }
        val logo = text("وَثاق", 42f, navy, true).apply { gravity = Gravity.CENTER }
        top.addView(logo, LinearLayout.LayoutParams(-1, dp(80)))
        top.addView(text("تواصل عربي مستقل\nبهوية لا تحتاج إلى رقم هاتف", 18f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, dp(70)))
        val badge = text("خصوصية أولاً  •  هوية ثلاثية  •  محادثات", 13f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }
        top.addView(badge, LinearLayout.LayoutParams(-1, dp(50)))
        r.addView(top, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(28)) }
        bottom.addView(pillButton("بدء الاستخدام") { showCreateProfile() }, LinearLayout.LayoutParams(-1, dp(58)))
        bottom.addView(text("لا يوجد حساب جاهز مسبقًا. أنشئ هويتك داخل الجهاز خلال ثوانٍ.", 13f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, dp(58)))
        r.addView(bottom)
        setContentView(r)
    }

    private fun showCreateProfile() {
        val r = root()
        r.setPadding(dp(22), dp(28), dp(22), dp(22))
        r.addView(text("إنشاء هوية وَثاق", 28f, navy, true), LinearLayout.LayoutParams(-1, dp(58)))
        r.addView(text("لن نطلب رقم هاتفك. سيُنشأ لك معرّف من ثلاثة أجزاء تستطيع مشاركته مع من تريد.", 15f, muted).apply { textAlignment = View.TEXT_ALIGNMENT_CENTER; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(76)))

        val name = field("الاسم الظاهر", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        r.addView(name, marginParams(dp(56), 8))

        val preview = cardView()
        preview.addView(text("معرّف وَثاق", 13f, muted, true))
        val idPreview = text("سيُنشأ تلقائيًا بعد إدخال الاسم", 20f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }
        preview.addView(idPreview, LinearLayout.LayoutParams(-1, dp(58)))
        r.addView(preview, marginParams(dp(90), 8))

        val create = pillButton("إنشاء هويتي") {
            val n = name.text.toString().trim()
            if (n.length < 2) {
                Toast.makeText(this, "اكتب اسمًا من حرفين على الأقل", Toast.LENGTH_SHORT).show()
                return@pillButton
            }
            val id = generateId()
            prefs.edit().putBoolean("onboarded", true).putString("name", n).putString("id", id).apply()
            showApp()
        }
        r.addView(create, marginParams(dp(58), 14))
        r.addView(pillButton("رجوع", { showWelcome() }, navy), marginParams(dp(52), 8))
        setContentView(r)
    }

    private fun generateId(): String {
        val words = listOf("nour", "sama", "wafa", "saf", "huda", "aman", "sham", "ward", "zain", "mira", "sabr", "fajr")
        val a = words.random()
        val b = words.random()
        val c = Random.nextInt(1000, 9999)
        return "$a-$b-$c"
    }

    private fun field(hint: String, type: Int): EditText = EditText(this).apply {
        this.hint = hint
        inputType = type
        textSize = 16f
        setSingleLine(true)
        setTextColor(navy)
        setHintTextColor(Color.rgb(145, 153, 160))
        setPadding(dp(16), 0, dp(16), 0)
        background = rounded(Color.WHITE, 14)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun marginParams(height: Int, margin: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, height).apply {
        setMargins(0, dp(margin), 0, dp(margin))
    }

    private fun showApp() {
        if (activeContactId.isNotEmpty()) {
            showChat(activeContactId)
            return
        }
        when (activePage) {
            "contacts" -> showContacts()
            "profile" -> showProfile()
            "settings" -> showSettings()
            else -> showChats()
        }
    }

    private fun header(title: String, subtitle: String? = null, back: Boolean = false, backAction: (() -> Unit)? = null): LinearLayout {
        val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        if (back) {
            val b = text("‹", 34f, navy, true).apply { gravity = Gravity.CENTER; setOnClickListener { backAction?.invoke() } }
            h.addView(b, LinearLayout.LayoutParams(dp(46), dp(52)))
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        box.addView(text(title, 27f, navy, true), LinearLayout.LayoutParams(-1, dp(40)))
        if (subtitle != null) box.addView(text(subtitle, 13f, muted), LinearLayout.LayoutParams(-1, dp(26)))
        h.addView(box, LinearLayout.LayoutParams(0, dp(64), 1f))
        return h
    }

    private fun showChats() {
        activePage = "chats"
        val r = root()
        val name = prefs.getString("name", "مستخدم وَثاق") ?: "مستخدم وَثاق"
        r.addView(header("المحادثات", "مرحبًا، $name"))
        val search = field("بحث في المحادثات", InputType.TYPE_CLASS_TEXT)
        r.addView(search, marginParams(dp(50), 8))

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(16)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val conversations = getConversations()
        if (conversations.length == 0) {
            val empty = cardView()
            empty.gravity = Gravity.CENTER
            empty.addView(text("لا توجد محادثات بعد", 21f, navy, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, dp(54)))
            empty.addView(text("أضف جهة اتصال وابدأ أول محادثة في وَثاق.", 14f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, LinearLayout.LayoutParams(-1, dp(48)))
            list.addView(empty, marginParams(-2, 8))
        } else {
            for (i in 0 until conversations.length()) {
                val item = conversations.getJSONObject(i)
                list.addView(conversationRow(item), marginParams(-2, 5))
            }
        }
        scroll.addView(list)
        r.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        r.addView(bottomNav("chats"))
        setContentView(r)
    }

    private fun conversationRow(item: JSONObject): View {
        val id = item.optString("id")
        val display = item.optString("name", id)
        val last = item.optString("last", "ابدأ المحادثة")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)); background = rounded(card, 16); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val avatar = text(initials(display), 18f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = rounded(teal, 28) }
        row.addView(avatar, LinearLayout.LayoutParams(dp(52), dp(52)))
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        box.addView(text(display, 17f, navy, true), LinearLayout.LayoutParams(-1, dp(30)))
        box.addView(text(last, 13f, muted), LinearLayout.LayoutParams(-1, dp(28)))
        row.addView(box, LinearLayout.LayoutParams(0, dp(62), 1f))
        row.setOnClickListener { showChat(id) }
        return row
    }

    private fun initials(name: String): String = name.trim().take(1).ifEmpty { "و" }

    private fun showContacts() {
        activePage = "contacts"
        val r = root()
        r.addView(header("جهات الاتصال", "هويتك: @${prefs.getString("id", "")}"))
        val add = field("ابحث بمعرّف وَثاق أو أضف اسمًا محليًا", InputType.TYPE_CLASS_TEXT)
        r.addView(add, marginParams(dp(50), 8))
        r.addView(pillButton("إضافة جهة اتصال", {
            val id = add.text.toString().trim()
            if (id.length < 5) {
                Toast.makeText(this, "اكتب معرّف وَثاق صحيحًا", Toast.LENGTH_SHORT).show()
            } else {
                addContact(id, id)
                add.setText("")
                showContacts()
            }
        }), marginParams(dp(52), 6))

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(16)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val contacts = getContacts()
        if (contacts.length == 0) {
            list.addView(cardText("لا توجد جهات اتصال", "أضف أول جهة اتصال باستخدام المعرّف الثلاثي."), marginParams(-2, 8))
        } else {
            for (i in 0 until contacts.length()) {
                val c = contacts.getJSONObject(i)
                val row = conversationRow(JSONObject().apply { put("id", c.optString("id")); put("name", c.optString("name")); put("last", "جهة اتصال") })
                list.addView(row, marginParams(-2, 5))
            }
        }
        scroll.addView(list)
        r.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        r.addView(bottomNav("contacts"))
        setContentView(r)
    }

    private fun cardText(title: String, body: String): LinearLayout {
        val c = cardView()
        c.addView(text(title, 18f, navy, true), LinearLayout.LayoutParams(-1, dp(34)))
        c.addView(text(body, 14f, muted), LinearLayout.LayoutParams(-1, dp(52)))
        return c
    }

    private fun showChat(contactId: String) {
        activeContactId = contactId
        val contact = findContact(contactId)
        val display = contact?.optString("name") ?: contactId
        val r = root()
        r.addView(header(display, "@${contactId}", true) { activeContactId = ""; showChats() })

        val scroll = ScrollView(this)
        val messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val arr = getMessages(contactId)
        if (arr.length == 0) {
            messages.addView(cardText("ابدأ المحادثة", "هذه محادثة محلية على الجهاز. أرسل رسالة لتجربة واجهة وَثاق."), marginParams(-2, 8))
        } else {
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                messages.addView(messageBubble(m.optString("text"), m.optBoolean("mine")), marginParams(-2, 5))
            }
        }
        scroll.addView(messages)
        r.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val input = field("اكتب رسالة…", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        input.maxLines = 4
        composer.addView(input, LinearLayout.LayoutParams(0, dp(54), 1f))
        composer.addView(pillButton("إرسال", {
            val msg = input.text.toString().trim()
            if (msg.isNotEmpty()) {
                saveMessage(contactId, msg, true)
                input.setText("")
                showChat(contactId)
            }
        }), LinearLayout.LayoutParams(dp(92), dp(54)).apply { setMargins(dp(8), 0, 0, 0) })
        r.addView(composer)
        setContentView(r)
    }

    private fun messageBubble(message: String, mine: Boolean): TextView = TextView(this).apply {
        text = message
        textSize = 15f
        setTextColor(if (mine) Color.WHITE else navy)
        setPadding(dp(15), dp(11), dp(15), dp(11))
        background = rounded(if (mine) teal else Color.WHITE, 16)
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (mine) setMargins(dp(55), 0, 0, 0) else setMargins(0, 0, dp(55), 0)
        }
    }

    private fun showProfile() {
        activePage = "profile"
        val r = root()
        r.addView(header("ملفي الشخصي", "هويتك داخل وَثاق"))
        val name = prefs.getString("name", "") ?: ""
        val id = prefs.getString("id", "") ?: ""
        val profile = cardView()
        profile.gravity = Gravity.CENTER
        profile.addView(text(initials(name), 34f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = rounded(teal, 50) }, LinearLayout.LayoutParams(dp(90), dp(90)))
        profile.addView(text(name, 23f, navy, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, marginParams(dp(42), 8))
        profile.addView(text("@$id", 17f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, marginParams(dp(38), 2))
        r.addView(profile, marginParams(dp(245), 10))
        r.addView(pillButton("نسخ المعرّف", {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Wethaq ID", id))
            Toast.makeText(this, "تم نسخ المعرّف", Toast.LENGTH_SHORT).show()
        }), marginParams(dp(54), 8))
        r.addView(pillButton("تعديل الاسم", { showEditName() }, navy), marginParams(dp(54), 8))
        r.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        r.addView(bottomNav("profile"))
        setContentView(r)
    }

    private fun showEditName() {
        val r = root(); r.setPadding(dp(22), dp(28), dp(22), dp(22))
        r.addView(header("تعديل الاسم"))
        val f = field("الاسم الجديد", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        f.setText(prefs.getString("name", ""))
        r.addView(f, marginParams(dp(56), 10))
        r.addView(pillButton("حفظ") {
            val n = f.text.toString().trim()
            if (n.length >= 2) { prefs.edit().putString("name", n).apply(); showProfile() }
        }, marginParams(dp(56), 10))
        r.addView(pillButton("إلغاء", { showProfile() }, navy), marginParams(dp(52), 8))
        setContentView(r)
    }

    private fun showSettings() {
        activePage = "settings"
        val r = root()
        r.addView(header("الإعدادات", "تحكم في تجربة وَثاق"))
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(16)); layoutDirection = View.LAYOUT_DIRECTION_RTL }

        val notifyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)); background = rounded(card, 16); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val notifyText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        notifyText.addView(text("الإشعارات", 17f, navy, true), LinearLayout.LayoutParams(-1, dp(30)))
        notifyText.addView(text("تشغيل تنبيهات الرسائل الجديدة", 13f, muted), LinearLayout.LayoutParams(-1, dp(26)))
        val sw = Switch(this).apply { isChecked = prefs.getBoolean("notifications", true); setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("notifications", checked).apply() } }
        notifyRow.addView(sw, LinearLayout.LayoutParams(dp(70), dp(55)))
        notifyRow.addView(notifyText, LinearLayout.LayoutParams(0, dp(60), 1f))
        list.addView(notifyRow, marginParams(-2, 6))

        list.addView(cardText("الخصوصية", "وَثاق لا يحتاج رقم هاتف لإنشاء الهوية. في النسخة الحالية تُحفظ البيانات محليًا على الجهاز."), marginParams(-2, 6))
        list.addView(cardText("الأمان", "الاتصال بالخادم والتشفير الطرفي سيتم تفعيله في مرحلة البنية الخلفية قبل الإطلاق العام."), marginParams(-2, 6))
        list.addView(cardText("الإصدار", "وَثاق 1.1.0 • نسخة تطويرية"), marginParams(-2, 6))

        list.addView(pillButton("مسح بيانات هذا الجهاز", {
            prefs.edit().clear().apply()
            activeContactId = ""
            Toast.makeText(this, "تم مسح البيانات المحلية", Toast.LENGTH_SHORT).show()
            showWelcome()
        }, danger), marginParams(dp(54), 12))
        scroll.addView(list)
        r.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        r.addView(bottomNav("settings"))
        setContentView(r)
    }

    private fun bottomNav(selected: String): LinearLayout {
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(8), dp(8), dp(10)); background = Color.WHITE; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val items = listOf("الإعدادات" to "settings", "ملفي" to "profile", "جهات الاتصال" to "contacts", "المحادثات" to "chats")
        for ((label, page) in items) {
            val b = text(label, 13f, if (selected == page) teal else muted, selected == page).apply {
                gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(2, dp(8), 2, dp(8)); setOnClickListener { activeContactId = ""; activePage = page; showApp() }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(54), 1f))
        }
        return nav
    }

    private fun addContact(id: String, name: String) {
        val contacts = getContacts()
        for (i in 0 until contacts.length()) if (contacts.getJSONObject(i).optString("id") == id) return
        contacts.put(JSONObject().apply { put("id", id); put("name", name) })
        prefs.edit().putString("contacts", contacts.toString()).apply()
    }

    private fun findContact(id: String): JSONObject? {
        val contacts = getContacts()
        for (i in 0 until contacts.length()) if (contacts.getJSONObject(i).optString("id") == id) return contacts.getJSONObject(i)
        return null
    }

    private fun getContacts(): JSONArray = runCatching { JSONArray(prefs.getString("contacts", "[]")) }.getOrDefault(JSONArray())

    private fun getConversations(): JSONArray {
        val out = JSONArray()
        val contacts = getContacts()
        for (i in 0 until contacts.length()) {
            val c = contacts.getJSONObject(i)
            val id = c.optString("id")
            val messages = getMessages(id)
            val last = if (messages.length() == 0) "ابدأ المحادثة" else messages.getJSONObject(messages.length() - 1).optString("text")
            out.put(JSONObject().apply { put("id", id); put("name", c.optString("name", id)); put("last", last) })
        }
        return out
    }

    private fun getMessages(id: String): JSONArray = runCatching { JSONArray(prefs.getString("messages_$id", "[]")) }.getOrDefault(JSONArray())

    private fun saveMessage(id: String, message: String, mine: Boolean) {
        val arr = getMessages(id)
        arr.put(JSONObject().apply {
            put("text", message)
            put("mine", mine)
            put("time", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
        })
        prefs.edit().putString("messages_$id", arr.toString()).apply()
    }
}
