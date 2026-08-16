package com.wethaq.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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
    private val name get() = prefs.getString("name", "") ?: ""
    private val id get() = prefs.getString("id", "") ?: ""
    private val token get() = prefs.getString("token", "") ?: ""
    private val teal = Color.rgb(0, 137, 123)
    private val dark = Color.rgb(20, 40, 52)
    private val gray = Color.rgb(100, 115, 125)
    private val page = Color.rgb(247, 249, 250)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (name.isBlank() || id.isBlank()) welcome() else home()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun root(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(page)
        layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
    }

    private fun text(value: String, size: Float = 16f, color: Int = dark, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        layoutDirection = LinearLayout.LAYOUT_DIRECTION_RTL
    }

    private fun field(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 16f
        setSingleLine(true)
        setTextColor(dark)
        setHintTextColor(gray)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setPadding(dp(14), 0, dp(14), 0)
    }

    private fun button(label: String, action: () -> Unit, color: Int = teal): Button = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setBackgroundColor(color)
        setOnClickListener { action() }
    }

    private fun add(parent: LinearLayout, view: android.view.View, height: Int = -1) {
        val h = if (height < 0) LinearLayout.LayoutParams.WRAP_CONTENT else dp(height)
        parent.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h))
    }

    private fun welcome() {
        val r = root()
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }
        add(c, text("وَثاق", 38f, dark, true), 60)
        add(c, text("هوية وتواصل عربي مستقل", 18f, gray, true), 45)
        add(c, button("تسجيل الدخول", { identityForm(false) }), 58)
        add(c, button("إنشاء هوية جديدة", { identityForm(true) }, Color.rgb(35, 70, 90)), 58)
        r.addView(c, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        setContentView(r)
    }

    private fun identityForm(register: Boolean) {
        val r = root()
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        add(c, text(if (register) "إنشاء هوية وَثاق" else "تسجيل الدخول", 24f, dark, true), 55)
        val n = field("الاسم الثلاثي")
        val y = field("سنة الميلاد")
        y.inputType = InputType.TYPE_CLASS_NUMBER
        add(c, n, 56)
        add(c, y, 56)
        add(c, button(if (register) "إنشاء" else "دخول", {
            val enteredName = n.text.toString().trim()
            val year = y.text.toString().toIntOrNull()
            if (enteredName.split(Regex("\\s+")).size < 3 || year == null) {
                toast("تحقق من الاسم الثلاثي وسنة الميلاد")
            } else if (register) {
                api("POST", "/api/identity", JSONObject().put("name", enteredName).put("birthYear", year).put("deviceKey", deviceKey()), false) { ok, body ->
                    runOnUiThread {
                        if (ok && saveIdentity(body)) home() else toast("تعذر إنشاء الهوية. تحقق من الإنترنت والبيانات.")
                    }
                }
            } else {
                api("POST", "/api/login", JSONObject().put("name", enteredName).put("birthYear", year).put("deviceKey", deviceKey()), false) { ok, body ->
                    runOnUiThread {
                        if (ok && saveIdentity(body)) home() else toast("تعذر تسجيل الدخول. تحقق من الإنترنت والبيانات.")
                    }
                }
            }
        }), 58)
        add(c, button("رجوع", { welcome() }, Color.DKGRAY), 52)
        r.addView(c, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        setContentView(r)
    }

    private fun home() {
        val r = root()
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        add(c, text("وَثاق", 26f, dark, true), 55)
        add(c, text("@$id", 16f, teal, true), 40)
        val q = field("ابحث بالاسم أو المعرف")
        add(c, q, 56)
        add(c, button("بحث", { search(q.text.toString()) }), 56)
        add(c, button("جهات الاتصال", { contacts() }, Color.rgb(35, 70, 90)), 56)
        add(c, button("ملفي والصورة الشخصية", { profile() }, Color.rgb(75, 90, 100)), 56)
        add(c, button("تسجيل الخروج", { prefs.edit().clear().apply(); welcome() }, Color.DKGRAY), 52)
        r.addView(c, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        setContentView(r)
    }

    private fun profile() {
        val r = root()
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(18)) }
        add(c, text("ملفي", 25f, dark, true), 55)
        add(c, text(name, 20f, dark, true), 45)
        add(c, text("@$id", 17f, teal, true), 45)
        add(c, button("تغيير الصورة الشخصية", { startActivity(Intent(this, AvatarActivity::class.java)) }), 56)
        add(c, button("نسخ المعرف", {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("Wethaq ID", id))
            toast("تم نسخ المعرف")
        }, Color.rgb(35, 70, 90)), 56)
        add(c, button("رجوع", { home() }, Color.DKGRAY), 52)
        r.addView(c, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        setContentView(r)
    }

    private fun search(value: String) {
        val q = value.trim().removePrefix("@")
        if (q.length < 2) { toast("اكتب اسمًا أو معرفًا"); return }
        api("GET", "/api/search?q=${URLEncoder.encode(q, "UTF-8")}", null, false) { ok, body ->
            runOnUiThread { if (ok) results(body.optJSONArray("users") ?: JSONArray()) else toast("تعذر البحث الآن") }
        }
    }

    private fun results(users: JSONArray) {
        val r = root()
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        add(c, text("نتائج البحث", 24f, dark, true), 55)
        if (users.length() == 0) add(c, text("لم يتم العثور على مستخدم", 16f, gray), 60)
        for (i in 0 until users.length()) {
            val u = users.optJSONObject(i) ?: continue
            val uid = u.optString("wethaq_id")
            val uname = u.optString("name").ifBlank { "مستخدم وَثاق" }
            add(c, text("$uname\n@$uid", 17f, dark, true), 60)
            add(c, button("حفظ ومراسلة", { saveContact(uid, uname); chat(uid, uname) }), 52)
        }
        add(c, button("رجوع", { home() }, Color.DKGRAY), 52)
        r.addView(c, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        setContentView(r)
    }

    private fun saveContact(uid: String, uname: String) {
        if (uid.isBlank()) return
        val arr = JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        var exists = false
        for (i in 0 until arr.length()) if (arr.optJSONObject(i)?.optString("id") == uid) exists = true
        if (!exists) arr.put(JSONObject().put("id", uid).put("name", uname))
        prefs.edit().putString("contacts", arr.toString()).apply()
        if (token.isNotBlank()) api("POST", "/api/contacts", JSONObject().put("wethaqId", uid), true) { _, _ -> }
        toast("تم حفظ $uname\n@$uid")
    }

    private fun contacts() {
        val r = root()
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        add(c, text("جهات الاتصال", 24f, dark, true), 55)
        val arr = JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        if (arr.length() == 0) add(c, text("لا توجد جهات اتصال محفوظة", 16f, gray), 60)
        for (i in 0 until arr.length()) {
            val u = arr.optJSONObject(i) ?: continue
            val uid = u.optString("id")
            val uname = u.optString("name").ifBlank { "مستخدم وَثاق" }
            add(c, text("$uname\n@$uid", 17f, dark, true), 60)
            add(c, button("مراسلة", { chat(uid, uname) }), 50)
        }
        add(c, button("رجوع", { home() }, Color.DKGRAY), 52)
        r.addView(c, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        setContentView(r)
    }

    private fun chat(uid: String, uname: String) {
        val r = root()
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        add(c, text("$uname\n@$uid", 21f, dark, true), 65)
        val messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        add(c, messages, -1)
        val input = field("اكتب رسالتك")
        add(c, input, 55)
        add(c, button("إرسال", {
            val body = input.text.toString().trim()
            if (body.isNotBlank()) {
                input.setText("")
                bubble(messages, body, "جاري الإرسال")
                send(uid, body, messages)
            }
        }), 52)
        add(c, button("رجوع", { contacts() }, Color.DKGRAY), 48)
        r.addView(c, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        setContentView(r)
        loadMessages(uid, messages)
    }

    private fun bubble(parent: LinearLayout, body: String, status: String) {
        val v = text("$body\n$status", 15f, dark)
        v.setPadding(dp(12), dp(8), dp(12), dp(8))
        parent.addView(v, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun send(uid: String, body: String, messages: LinearLayout) {
        if (!network() || token.isBlank()) { queue(uid, body); updateLast(messages, "محفوظة للإرسال"); return }
        api("POST", "/api/messages", JSONObject().put("to", uid).put("body", body), true) { ok, _ ->
            runOnUiThread {
                if (ok) updateLast(messages, "تم الإرسال") else { queue(uid, body); updateLast(messages, "محفوظة للإرسال") }
            }
        }
    }

    private fun loadMessages(uid: String, messages: LinearLayout) {
        if (!network() || token.isBlank()) return
        api("GET", "/api/messages/${URLEncoder.encode(uid, "UTF-8")}", null, true) { ok, body ->
            if (ok) runOnUiThread {
                messages.removeAllViews()
                val arr = body.optJSONArray("messages") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    bubble(messages, m.optString("body"), m.optString("status", "تم الاستلام"))
                }
            }
        }
    }

    private fun updateLast(messages: LinearLayout, status: String) {
        if (messages.childCount == 0) return
        val v = messages.getChildAt(messages.childCount - 1) as? TextView ?: return
        val old = v.text.toString().substringBeforeLast("\n")
        v.text = "$old\n$status"
    }

    private fun queue(uid: String, body: String) {
        val arr = JSONArray(prefs.getString("pending", "[]") ?: "[]")
        arr.put(JSONObject().put("to", uid).put("body", body))
        prefs.edit().putString("pending", arr.toString()).apply()
        toast("حُفظت الرسالة وسترسل عند عودة الإنترنت")
    }

    private fun network(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetwork != null
    }

    private fun deviceKey(): String {
        val old = prefs.getString("deviceKey", "") ?: ""
        if (old.isNotBlank()) return old
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val key = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("deviceKey", key).apply()
        return key
    }

    private fun saveIdentity(body: JSONObject): Boolean {
        val newId = body.optString("wethaq_id", body.optString("id"))
        if (newId.isBlank()) return false
        prefs.edit().putString("name", body.optString("name", name)).putString("id", newId).putString("token", body.optString("token", token)).apply()
        return true
    }

    private fun api(method: String, path: String, json: JSONObject?, auth: Boolean, callback: (Boolean, JSONObject) -> Unit) {
        Thread {
            try {
                val builder = Request.Builder().url(baseUrl + path)
                if (auth && token.isNotBlank()) builder.header("Authorization", "Bearer $token")
                if (method == "POST") {
                    val body = (json?.toString() ?: "{}").toRequestBody("application/json".toMediaType())
                    builder.post(body)
                } else builder.get()
                client.newCall(builder.build()).execute().use { response ->
                    val raw = response.body?.string() ?: "{}"
                    val obj = try { JSONObject(raw) } catch (_: Exception) { JSONObject().put("raw", raw) }
                    callback(response.isSuccessful, obj)
                }
            } catch (e: Exception) {
                callback(false, JSONObject().put("error", "network").put("message", e.message ?: ""))
            }
        }.start()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
