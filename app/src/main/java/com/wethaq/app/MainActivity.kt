package com.wethaq.app

import android.content.*
import android.graphics.Color
import android.graphics.Typeface
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
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
    private val baseUrl = "https://wethaq-backend-production.up.railway.app"
    private val myName get() = prefs.getString("name", "") ?: ""
    private val myId get() = prefs.getString("id", "") ?: ""
    private val token get() = prefs.getString("token", "") ?: ""
    private val bg = Color.rgb(246, 249, 250)
    private val ink = Color.rgb(18, 38, 48)
    private val teal = Color.rgb(0, 137, 123)
    private val tealDark = Color.rgb(0, 96, 82)
    private val muted = Color.rgb(98, 116, 124)
    private val white = Color.WHITE

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (myName.isBlank() || myId.isBlank() || token.isBlank()) welcome() else home()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun label(s: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(4), dp(2), dp(4), dp(2))
    }

    private fun field(hint: String, numeric: Boolean = false) = EditText(this).apply {
        this.hint = hint
        textSize = 16f
        setSingleLine(true)
        setTextColor(ink)
        setHintTextColor(muted)
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setPadding(dp(14), 0, dp(14), 0)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(white)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(220, 228, 231))
        }
    }

    private fun button(s: String, action: () -> Unit, color: Int = teal) = Button(this).apply {
        text = s
        textSize = 15f
        setTextColor(Color.WHITE)
        setBackgroundColor(color)
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun add(parent: LinearLayout, view: View, height: Int = -1, margin: Int = 6) {
        val h = if (height < 0) LinearLayout.LayoutParams.WRAP_CONTENT else dp(height)
        val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
        p.setMargins(0, dp(margin), 0, dp(margin))
        parent.addView(view, p)
    }

    private fun fill(parent: LinearLayout, view: View) {
        parent.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun header(title: String, subtitle: String = "") = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(12), dp(18), dp(12))
        setBackgroundColor(ink)
        addView(label(title, 25f, white, true), LinearLayout.LayoutParams(-1, dp(42)))
        if (subtitle.isNotBlank()) addView(label(subtitle, 14f, Color.rgb(190, 210, 216), false), LinearLayout.LayoutParams(-1, dp(32)))
    }

    private fun welcome() {
        val r = root()
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(35), dp(22), dp(22))
        }
        add(c, label("وَثاق", 42f, ink, true), 62, 4)
        add(c, label("WETHAQ  •  هوية وتواصل مستقل", 14f, tealDark, true), 38, 2)
        add(c, label("هوية رقمية عربية بسيطة، آمنة، وقابلة للاستخدام حتى عند انقطاع الإنترنت.", 16f, muted), 70, 14)
        add(c, button("تسجيل الدخول") { identityForm(false) }, 56, 8)
        add(c, button("إنشاء هوية جديدة", { identityForm(true) }, tealDark), 56, 8)
        fill(r, c)
        add(r, label("وَثاق  •  Wethaq", 13f, muted, true), 40, 0)
        setContentView(r)
    }

    private fun identityForm(register: Boolean) {
        val r = root()
        r.addView(header(if (register) "إنشاء هوية وَثاق" else "تسجيل الدخول", "الاسم الثلاثي + سنة الميلاد"))
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        val n = field("الاسم الثلاثي")
        val y = field("سنة الميلاد", true)
        add(c, n, 56, 6)
        add(c, y, 56, 6)
        add(c, label("سيُنشأ المعرف تلقائيًا من الاسم وسنة الميلاد.", 13f, muted), 42, 4)
        add(c, button(if (register) "إنشاء الهوية" else "دخول", {
            val entered = n.text.toString().trim().replace(Regex("\\s+"), " ")
            val year = y.text.toString().toIntOrNull()
            val words = entered.split(" ").filter { it.isNotBlank() }
            if (words.size < 3 || year == null || year !in 1900..java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) {
                toast("تحقق من الاسم الثلاثي وسنة الميلاد")
                return@button
            }
            val path = if (register) "/api/identity" else "/api/login"
            val data = JSONObject().put("name", entered).put("birthYear", year).put("deviceKey", deviceKey())
            request("POST", path, data, false) { ok, body ->
                runOnUiThread {
                    if (ok && saveIdentity(body)) home() else toast(serverError(body, if (register) "تعذر إنشاء الهوية" else "تعذر تسجيل الدخول"))
                }
            }
        }, teal), 56, 10)
        add(c, button("رجوع", { welcome() }, Color.rgb(65, 78, 84)), 50, 4)
        fill(r, c)
        setContentView(r)
    }

    private fun home() {
        val r = root()
        r.addView(header("وَثاق", "@$myId"))
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        add(c, label("مرحبًا، $myName", 20f, ink, true), 46, 2)
        val q = field("ابحث بالاسم أو المعرف")
        add(c, q, 54, 5)
        add(c, button("بحث عن مستخدم") { search(q.text.toString()) }, 52, 5)
        add(c, button("جهات الاتصال", { contacts() }, tealDark), 52, 5)
        add(c, button("ملفي والمعرف", { profile() }, Color.rgb(67, 85, 94)), 52, 5)
        add(c, button("إرسال الرسائل المحفوظة", { flushPending() }, Color.rgb(86, 105, 113)), 48, 5)
        fill(r, c)
        add(r, button("تسجيل الخروج", { prefs.edit().clear().apply(); welcome() }, Color.rgb(50, 58, 62)), 48, 0)
        setContentView(r)
    }

    private fun profile() {
        val r = root()
        r.addView(header("ملفي", "هويتي في وَثاق"))
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }
        add(c, label(myName, 23f, ink, true), 48, 4)
        add(c, label("@$myId", 18f, tealDark, true), 42, 4)
        add(c, label("المعرف ثابت ويمكن مشاركته مع أي مستخدم وَثاق.", 14f, muted), 55, 4)
        add(c, button("نسخ المعرف", {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Wethaq ID", myId))
            toast("تم نسخ المعرف")
        }), 52, 8)
        add(c, button("تغيير الصورة الشخصية", { startActivity(Intent(this, AvatarActivity::class.java)) }, tealDark), 52, 6)
        add(c, button("رجوع", { home() }, Color.DKGRAY), 50, 6)
        fill(r, c)
        setContentView(r)
    }

    private fun search(value: String) {
        val q = value.trim().removePrefix("@").trim()
        if (q.length < 2) { toast("اكتب اسمًا أو معرفًا من حرفين على الأقل"); return }
        request("GET", "/api/search?q=${URLEncoder.encode(q, "UTF-8")}", null, false) { ok, body ->
            runOnUiThread { if (ok) showResults(body.optJSONArray("users") ?: JSONArray()) else toast(serverError(body, "تعذر البحث الآن")) }
        }
    }

    private fun showResults(users: JSONArray) {
        val r = root()
        r.addView(header("نتائج البحث", "العثور على مستخدمي وَثاق"))
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        if (users.length() == 0) add(c, label("لم يتم العثور على مستخدم بهذا الاسم أو المعرف.", 16f, muted), 70, 6)
        for (i in 0 until users.length()) {
            val u = users.optJSONObject(i) ?: continue
            val uid = u.optString("wethaq_id")
            val uname = u.optString("name").ifBlank { "مستخدم وَثاق" }
            val online = if (u.optBoolean("online", false)) "متصل الآن" else "غير متصل"
            add(c, label("$uname\n@$uid\n$online", 16f, ink, true), 82, 5)
            if (uid.isNotBlank() && uid != myId) add(c, button("حفظ وفتح المحادثة", { saveContact(uid, uname); chat(uid, uname) }), 50, 4)
        }
        add(c, button("رجوع", { home() }, Color.DKGRAY), 50, 8)
        fill(r, c)
        setContentView(r)
    }

    private fun saveContact(uid: String, uname: String) {
        if (uid.isBlank() || uid == myId) return
        val arr = JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        var exists = false
        for (i in 0 until arr.length()) if (arr.optJSONObject(i)?.optString("id") == uid) exists = true
        if (!exists) arr.put(JSONObject().put("id", uid).put("name", uname))
        prefs.edit().putString("contacts", arr.toString()).apply()
        if (token.isNotBlank()) request("POST", "/api/contacts", JSONObject().put("wethaqId", uid), true) { _, _ -> }
    }

    private fun contacts() {
        val r = root()
        r.addView(header("جهات الاتصال", "المعرفات المحفوظة على هذا الجهاز"))
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10)) }
        val arr = JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        if (arr.length() == 0) add(c, label("لا توجد جهات اتصال محفوظة. ابحث عن شخص أولًا.", 16f, muted), 70, 6)
        for (i in 0 until arr.length()) {
            val u = arr.optJSONObject(i) ?: continue
            val uid = u.optString("id")
            val uname = u.optString("name").ifBlank { "مستخدم وَثاق" }
            add(c, label("$uname\n@$uid", 16f, ink, true), 65, 5)
            add(c, button("مراسلة", { chat(uid, uname) }), 48, 3)
        }
        add(c, button("رجوع", { home() }, Color.DKGRAY), 50, 8)
        fill(r, c)
        setContentView(r)
    }

    private fun chat(uid: String, uname: String) {
        val r = root()
        r.addView(header(uname, "@$uid"))
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)) }
        val messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fill(c, messages)
        val input = field("اكتب رسالتك")
        add(c, input, 54, 4)
        add(c, button("إرسال") {
            val body = input.text.toString().trim()
            if (body.isNotBlank()) { input.setText(""); bubble(messages, body, "جاري الإرسال"); send(uid, body, messages) }
        }, 52, 4)
        add(c, button("رجوع", { contacts() }, Color.DKGRAY), 46, 3)
        fill(r, c)
        setContentView(r)
        loadMessages(uid, messages)
    }

    private fun bubble(parent: LinearLayout, body: String, status: String) {
        val v = label("$body\n$status", 15f, ink)
        v.setPadding(dp(12), dp(8), dp(12), dp(8))
        v.background = android.graphics.drawable.GradientDrawable().apply { setColor(white); cornerRadius = dp(12).toFloat() }
        parent.addView(v, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(3), 0, dp(3)) })
    }

    private fun updateLast(parent: LinearLayout, status: String) {
        if (parent.childCount == 0) return
        val v = parent.getChildAt(parent.childCount - 1) as? TextView ?: return
        val old = v.text.toString().substringBeforeLast("\n")
        v.text = "$old\n$status"
    }

    private fun send(uid: String, body: String, messages: LinearLayout) {
        if (!network() || token.isBlank()) { queue(uid, body); updateLast(messages, "محفوظة محليًا للإرسال"); return }
        request("POST", "/api/messages", JSONObject().put("to", uid).put("body", body), true) { ok, response ->
            runOnUiThread {
                if (ok) updateLast(messages, "تم الإرسال") else { queue(uid, body); updateLast(messages, "محفوظة محليًا للإرسال"); toast(serverError(response, "تعذر الإرسال، حُفظت الرسالة محليًا")) }
            }
        }
    }

    private fun loadMessages(uid: String, messages: LinearLayout) {
        if (!network() || token.isBlank()) return
        request("GET", "/api/messages/${URLEncoder.encode(uid, "UTF-8")}", null, true) { ok, body ->
            if (ok) runOnUiThread {
                messages.removeAllViews()
                val arr = body.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    bubble(messages, m.optString("body"), when (m.optString("status")) { "read" -> "مقروءة"; "sent" -> "مرسلة"; else -> "مستلمة" })
                }
            }
        }
    }

    private fun queue(uid: String, body: String) {
        val arr = JSONArray(prefs.getString("pending", "[]") ?: "[]")
        arr.put(JSONObject().put("to", uid).put("body", body).put("createdAt", System.currentTimeMillis()))
        prefs.edit().putString("pending", arr.toString()).apply()
        toast("حُفظت الرسالة وستُرسل عند عودة الإنترنت")
    }

    private fun flushPending() {
        if (!network() || token.isBlank()) { toast("لا يوجد اتصال بالخادم الآن"); return }
        val old = JSONArray(prefs.getString("pending", "[]") ?: "[]")
        if (old.length() == 0) { toast("لا توجد رسائل معلقة"); return }
        val remaining = JSONArray()
        var sent = 0
        for (i in 0 until old.length()) {
            val m = old.optJSONObject(i) ?: continue
            val to = m.optString("to")
            val body = m.optString("body")
            if (to.isBlank() || body.isBlank()) continue
            request("POST", "/api/messages", JSONObject().put("to", to).put("body", body), true) { ok, _ ->
                synchronized(remaining) {
                    if (ok) sent++ else remaining.put(m)
                    if (i == old.length() - 1) runOnUiThread {
                        prefs.edit().putString("pending", remaining.toString()).apply()
                        toast("تم إرسال $sent رسالة معلقة")
                    }
                }
            }
        }
    }

    private fun network(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork != null
    }

    private fun deviceKey(): String {
        val old = prefs.getString("deviceKey", "") ?: ""
        if (old.isNotBlank()) return old
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        val k = b.joinToString("") { String.format("%02x", it) }
        prefs.edit().putString("deviceKey", k).apply()
        return k
    }

    private fun saveIdentity(body: JSONObject): Boolean {
        val user = body.optJSONObject("user") ?: body
        val newId = user.optString("wethaq_id").ifBlank { user.optString("id") }
        val name = user.optString("name").ifBlank { myName }
        val newToken = body.optString("token").ifBlank { token }
        if (newId.isBlank() || newToken.isBlank()) return false
        prefs.edit().putString("name", name).putString("id", newId).putString("token", newToken).apply()
        return true
    }

    private fun serverError(body: JSONObject, fallback: String): String {
        return when (body.optString("error")) {
            "user_not_found" -> "الهوية غير موجودة. اختر إنشاء هوية جديدة إذا لم تسجل سابقًا."
            "identity_claimed" -> "هذه الهوية مرتبطة بجهاز آخر."
            "invalid_identity" -> "بيانات الهوية غير صحيحة."
            "rate_limited" -> "تم تجاوز عدد المحاولات. حاول بعد قليل."
            "unauthorized", "invalid_token" -> "انتهت جلسة الدخول. سجل الدخول من جديد."
            else -> fallback
        }
    }

    private fun request(method: String, path: String, json: JSONObject?, auth: Boolean, cb: (Boolean, JSONObject) -> Unit) {
        Thread {
            try {
                val req = Request.Builder().url(baseUrl + path).header("Accept", "application/json")
                if (method == "POST") req.post((json?.toString() ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType())) else req.get()
                if (auth && token.isNotBlank()) req.header("Authorization", "Bearer $token")
                client.newCall(req.build()).execute().use { res ->
                    val raw = res.body?.string() ?: "{}"
                    val body = try { JSONObject(raw) } catch (_: Exception) { JSONObject().put("raw", raw) }
                    cb(res.isSuccessful, body)
                }
            } catch (e: Exception) {
                cb(false, JSONObject().put("error", "network").put("message", e.message ?: ""))
            }
        }.start()
    }
}
