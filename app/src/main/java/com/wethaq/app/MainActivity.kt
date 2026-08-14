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
import android.view.inputmethod.InputMethodManager
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
    private val navy = Color.rgb(12, 34, 50)
    private val teal = Color.rgb(0, 132, 119)
    private val tealSoft = Color.rgb(232, 247, 244)
    private val bg = Color.rgb(247, 249, 250)
    private val muted = Color.rgb(92, 108, 120)
    private val line = Color.rgb(222, 230, 233)
    private val danger = Color.rgb(190, 48, 58)
    private var page = "chats"
    private var chatId = ""

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (prefs.getBoolean("onboarded", false)) showApp() else showWelcome()
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
        maxLines = 4
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

    private fun field(hint: String, password: Boolean = false) = EditText(this).apply {
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
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun add(parent: LinearLayout, view: View, w: Int = -1, h: Int = -2, weight: Float = 0f, margin: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(w, if (h == -2) LinearLayout.LayoutParams.WRAP_CONTENT else dp(h), weight).apply {
            if (margin > 0) setMargins(dp(margin), dp(margin), dp(margin), dp(margin))
        })
    }

    private fun header(title: String, subtitle: String? = null, back: (() -> Unit)? = null): LinearLayout {
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(8))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        if (back != null) add(h, button("رجوع", back, navy), 72, 44, 0f, 4)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(box, text(title, 23f, navy, true), -1, 34)
        if (subtitle != null) add(box, text(subtitle, 13f, muted), -1, 24)
        add(h, box, 0, 62, 1f)
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
        add(content, text("مراسلة عربية خاصة ومستقلة", 19f, muted, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 36)
        add(content, text("هوية خاصة ومعرّف بدل رقم الهاتف", 15f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 32)
        val founder = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(Color.WHITE, 18, line); setPadding(dp(14), dp(10), dp(14), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(founder, avatar(true), 58, 58)
        val fb = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(12), 0, 0, 0) }
        add(fb, text("المؤسس", 13f, teal, true), -1, 22)
        add(fb, text("حاتم حسين الحاج رمضان", 17f, navy, true), -1, 28)
        add(fb, text("مؤسس ومطور تطبيق وَثاق", 12f, muted), -1, 22)
        add(founder, fb, 0, 72, 1f)
        add(content, founder, -1, 84, 0f, 8)
        val updates = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(Color.WHITE, 18, line); setPadding(dp(16), dp(12), dp(16), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(updates, text("أحدث ما وصل إليه وَثاق", 17f, navy, true), -1, 30)
        add(updates, text("• هوية خاصة لا تعتمد على رقم الهاتف", 14f, navy), -1, 28)
        add(updates, text("• محادثات محلية تعمل حتى دون خادم", 14f, navy), -1, 28)
        add(updates, text("• واجهة عربية بالكامل مع الخصوصية أولًا", 14f, navy), -1, 28)
        add(content, updates, -1, 120, 0f, 8)
        add(content, button("بدء الاستخدام", { showCreateProfile() }), -1, 56, 0f, 12)
        add(content, text("خصوصية أولًا  •  هوية خاصة  •  محادثات  •  تطوير مستمر", 12f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 44)
        scroll.addView(content)
        add(r, scroll, -1, 0, 1f)
        setContentView(r)
    }

    private fun showCreateProfile() {
        val r = root()
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(20), dp(22), dp(20)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(content, header("إنشاء هوية وَثاق", "لن نطلب رقم هاتفك", { showWelcome() }), -1, 70)
        add(content, avatar(true), 92, 92, 0f, 8)
        add(content, text("اختر الاسم الذي سيظهر للآخرين", 16f, navy, true), -1, 36, 0f, 8)
        val name = field("الاسم الظاهر")
        name.setText(prefs.getString("name", "") ?: "")
        add(content, name, -1, 54, 0f, 6)
        add(content, text("سيُنشأ معرّف وَثاق خاص بك تلقائيًا. يمكنك مشاركته بدل رقم الهاتف.", 13f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 58, 0f, 6)
        add(content, button("إنشاء هويتي", {
            val n = name.text.toString().trim()
            if (n.length < 2) Toast.makeText(this, "يرجى كتابة اسم من حرفين على الأقل", Toast.LENGTH_SHORT).show()
            else {
                val id = prefs.getString("id", "")?.ifBlank { generateId() } ?: generateId()
                prefs.edit().putBoolean("onboarded", true).putString("name", n).putString("id", id).apply()
                hideKeyboard(name)
                page = "chats"
                showApp()
            }
        }), -1, 56, 0f, 12)
        add(r, content, -1, 0, 1f)
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
            "updates" -> showUpdates()
            "settings" -> showSettings()
            else -> showChats()
        }
    }

    private fun showChats() {
        page = "chats"
        val r = root()
        add(r, header("المحادثات", "مرحبًا، ${prefs.getString("name", "مستخدم وَثاق")}"))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(16)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val contacts = getContacts()
        if (contacts.length() == 0) {
            val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = rounded(Color.WHITE, 18, line); setPadding(dp(20), dp(20), dp(20), dp(20)) }
            add(empty, text("لا توجد محادثات بعد", 20f, navy, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 40)
            add(empty, text("أضف جهة اتصال باستخدام معرّف وَثاق، ثم ابدأ أول محادثة.", 14f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 60)
            add(empty, button("إضافة جهة اتصال", { showContacts() }), -1, 52, 0f, 10)
            add(list, empty, -1, 200, 0f, 6)
        } else for (i in 0 until contacts.length()) add(list, contactRow(contacts.getJSONObject(i)), -1, 76, 0f, 5)
        val scroll = ScrollView(this); scroll.addView(list)
        add(r, scroll, -1, 0, 1f)
        add(r, nav("chats"), -1, 76)
        setContentView(r)
    }

    private fun contactRow(c: JSONObject): View {
        val id = c.optString("id")
        val name = c.optString("name", id)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(Color.WHITE, 16, line); setPadding(dp(12), dp(8), dp(12), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(row, avatar(), 52, 52)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(12), 0, 0, 0) }
        add(box, text(name, 17f, navy, true), -1, 28)
        add(box, text("المعرّف: @$id", 12f, muted), -1, 22)
        add(row, box, 0, 58, 1f)
        row.contentDescription = "فتح محادثة مع $name"
        row.setOnClickListener { showChat(id) }
        return row
    }

    private fun showContacts() {
        page = "contacts"
        val r = root()
        add(r, header("جهات الاتصال", "أضف الأشخاص باستخدام معرّف وَثاق"))
        val area = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val input = field("أدخل معرّف وَثاق")
        add(area, input, -1, 54, 0f, 4)
        add(area, button("إضافة جهة اتصال", {
            val id = input.text.toString().trim().lowercase()
            if (!Regex("^[a-z]+-[a-z]+-[0-9]{4,6}$").matches(id)) Toast.makeText(this, "صيغة المعرّف غير صحيحة", Toast.LENGTH_SHORT).show()
            else { addContact(id, id); input.setText(""); hideKeyboard(input); showContacts() }
        }), -1, 52, 0f, 6)
        add(area, text("لا تحتاج إلى رقم هاتف. شارك المعرّف فقط.", 13f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 40, 0f, 2)
        add(r, area, -1, 170)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val contacts = getContacts()
        if (contacts.length() == 0) add(list, text("لم تتم إضافة جهات اتصال بعد.", 14f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 90)
        for (i in 0 until contacts.length()) add(list, contactRow(contacts.getJSONObject(i)), -1, 76, 0f, 5)
        val scroll = ScrollView(this); scroll.addView(list)
        add(r, scroll, -1, 0, 1f)
        add(r, nav("contacts"), -1, 76)
        setContentView(r)
    }

    private fun showChat(id: String) {
        chatId = id
        val contact = findContact(id)
        val name = contact?.optString("name", id) ?: id
        val r = root()
        add(r, header(name, "@$id", { chatId = ""; showChats() }))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val messages = getMessages(id)
        if (messages.length() == 0) add(list, text("لا توجد رسائل بعد\nأرسل أول رسالة الآن.", 16f, muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 120, 0f, 8)
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val mine = m.optBoolean("mine")
            val bubble = text(m.optString("text"), 15f, if (mine) Color.WHITE else navy)
            bubble.background = rounded(if (mine) teal else Color.WHITE, 18, if (mine) null else line)
            bubble.setPadding(dp(14), dp(10), dp(14), dp(10))
            bubble.maxWidth = dp(300)
            list.addView(bubble, LinearLayout.LayoutParams(-2, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(4), dp(4), dp(4), dp(4)); gravity = if (mine) Gravity.START else Gravity.END })
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }; scroll.addView(list)
        add(r, scroll, -1, 0, 1f)
        val composer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL; background = ColorDrawableCompat(Color.WHITE) }
        val input = field("اكتب رسالتك")
        add(composer, input, 0, 52, 1f)
        add(composer, button("إرسال", {
            val message = input.text.toString().trim()
            if (message.isNotEmpty()) { addMessage(id, message); input.setText(""); hideKeyboard(input); showChat(id) }
        }), 92, 52, 0f, 6)
        add(r, composer, -1, 68)
        setContentView(r)
    }

    private fun ColorDrawableCompat(color: Int): GradientDrawable = rounded(color, 0)

    private fun showUpdates() {
        page = "updates"
        val r = root()
        add(r, header("التحديثات", "تطورات وَثاق خطوة بخطوة"))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(16)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        updateCard(list, "مكتمل", "هوية وَثاق الجديدة", "واجهة عربية أوضح، هوية المؤسس، والصورة المعتمدة داخل التطبيق.")
        updateCard(list, "مكتمل", "هوية بلا رقم هاتف", "إنشاء معرّف خاص ومشاركته بدل رقم الهاتف.")
        updateCard(list, "مكتمل", "محادثات محلية", "إرسال الرسائل وحفظها محليًا على الجهاز دون الاعتماد على الخادم.")
        updateCard(list, "قيد التطوير", "المزامنة الفورية", "ربط التطبيق بالخادم لإرسال واستقبال الرسائل بين الأجهزة في الوقت الحقيقي.")
        updateCard(list, "قريبًا", "الوسائط والملفات", "صور وملفات وصوت مع إدارة أفضل للحجم والحماية.")
        val scroll = ScrollView(this); scroll.addView(list)
        add(r, scroll, -1, 0, 1f)
        add(r, nav("updates"), -1, 76)
        setContentView(r)
    }

    private fun updateCard(parent: LinearLayout, tag: String, title: String, body: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(Color.WHITE, 18, line); setPadding(dp(16), dp(12), dp(16), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(row, text(tag, 12f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; background = rounded(tealSoft, 12); setPadding(dp(8), dp(5), dp(8), dp(5)) }, 94, 30)
        add(row, text(title, 17f, navy, true), 0, 32, 1f, 8)
        add(box, row, -1, 36)
        add(box, text(body, 13f, muted), -1, 54, 0f, 5)
        add(parent, box, -1, 110, 0f, 6)
    }

    private fun showProfile() {
        page = "profile"
        val r = root()
        add(r, header("ملفي الشخصي", "هويتك داخل وَثاق"))
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; background = rounded(Color.WHITE, 22, line); setPadding(dp(18), dp(20), dp(18), dp(20)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(card, avatar(true), 128, 128)
        add(card, text(prefs.getString("name", "حاتم حسين الحاج رمضان") ?: "حاتم حسين الحاج رمضان", 23f, navy, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 42, 0f, 8)
        add(card, text("@${prefs.getString("id", "غير مُنشأ")}", 15f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 30)
        add(card, text("مستخدم وَثاق", 13f, muted).apply { gravity = Gravity.CENTER }, -1, 28)
        add(card, button("نسخ المعرّف", { copyId() }), -1, 50, 0f, 10)
        add(card, button("الإعدادات", { showSettings() }, navy), -1, 50, 0f, 6)
        add(card, text("المؤسس: حاتم حسين الحاج رمضان", 13f, teal, true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER }, -1, 30, 0f, 8)
        val scroll = ScrollView(this); scroll.addView(card)
        add(r, scroll, -1, 0, 1f)
        add(r, nav("profile"), -1, 76)
        setContentView(r)
    }

    private fun showSettings() {
        page = "settings"
        val r = root()
        add(r, header("الإعدادات", "الخصوصية والتحكم", { showProfile() }))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(12)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        settingCard(list, "الخصوصية", "لا نطلب رقم هاتفك. الهوية تعتمد على معرّف وَثاق.")
        settingCard(list, "البيانات", "المحادثات المحلية محفوظة على جهازك في النسخة الحالية.")
        settingCard(list, "الخادم", "المزامنة الفورية تحتاج إلى نشر خادم الإنتاج وربط عنوانه بالتطبيق.")
        settingCard(list, "الإصدار", "وَثاق 1.3.0 • نسخة محسّنة")
        add(list, button("إعادة إنشاء الهوية", { prefs.edit().clear().apply(); chatId = ""; page = "chats"; showWelcome() }, danger), -1, 52, 0f, 8)
        val scroll = ScrollView(this); scroll.addView(list)
        add(r, scroll, -1, 0, 1f)
        add(r, nav("settings"), -1, 76)
        setContentView(r)
    }

    private fun settingCard(parent: LinearLayout, title: String, body: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = rounded(Color.WHITE, 16, line); setPadding(dp(16), dp(10), dp(16), dp(10)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(box, text(title, 16f, navy, true), -1, 28)
        add(box, text(body, 13f, muted), -1, 44)
        add(parent, box, -1, 84, 0f, 5)
    }

    private fun nav(active: String): LinearLayout {
        val n = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(6), dp(6), dp(8)); layoutDirection = View.LAYOUT_DIRECTION_RTL; background = ColorDrawableCompat(Color.WHITE) }
        navItem(n, "المحادثات", "chats", active == "chats") { page = "chats"; showChats() }
        navItem(n, "التحديثات", "updates", active == "updates") { page = "updates"; showUpdates() }
        navItem(n, "الجهات", "contacts", active == "contacts") { page = "contacts"; showContacts() }
        navItem(n, "ملفي", "profile", active == "profile") { page = "profile"; showProfile() }
        navItem(n, "الإعدادات", "settings", active == "settings") { page = "settings"; showSettings() }
        return n
    }

    private fun navItem(parent: LinearLayout, title: String, key: String, active: Boolean, action: () -> Unit) {
        val v = TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(if (active) Color.WHITE else navy)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            background = rounded(if (active) teal else Color.WHITE, 14, if (active) teal else line)
            contentDescription = title
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        parent.addView(v, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
    }

    private fun copyId() {
        val id = prefs.getString("id", "") ?: ""
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("معرّف وَثاق", id))
        Toast.makeText(this, "تم نسخ المعرّف", Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard(v: View) { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0) }

    private fun getContacts(): JSONArray = try { JSONArray(prefs.getString("contacts", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }

    private fun addContact(id: String, name: String) {
        val a = getContacts()
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("id") == id) {
            Toast.makeText(this, "جهة الاتصال مضافة مسبقًا", Toast.LENGTH_SHORT).show()
            return
        }
        a.put(JSONObject().apply { put("id", id); put("name", name) })
        prefs.edit().putString("contacts", a.toString()).apply()
        Toast.makeText(this, "تمت إضافة جهة الاتصال", Toast.LENGTH_SHORT).show()
    }

    private fun findContact(id: String): JSONObject? {
        val a = getContacts()
        for (i in 0 until a.length()) if (a.getJSONObject(i).optString("id") == id) return a.getJSONObject(i)
        return null
    }

    private fun getMessages(id: String): JSONArray = try { JSONArray(prefs.getString("messages_$id", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }

    private fun addMessage(id: String, message: String) {
        val a = getMessages(id)
        a.put(JSONObject().apply { put("text", message); put("mine", true) })
        prefs.edit().putString("messages_$id", a.toString()).apply()
    }
}
