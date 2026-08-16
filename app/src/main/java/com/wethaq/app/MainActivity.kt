package com.wethaq.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
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
    private val prefs by lazy { getSharedPreferences("wethaq_v10", Context.MODE_PRIVATE) }
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build()
    private val apiBase = "https://wethaq-backend-production.up.railway.app"
    private val teal = Color.rgb(0, 137, 123); private val navy = Color.rgb(17, 38, 54); private val bg = Color.rgb(247, 249, 250); private val line = Color.rgb(220, 228, 231); private val gray = Color.rgb(98, 112, 122)
    private val myName get() = prefs.getString("name", "") ?: ""
    private val myYear get() = prefs.getInt("year", 0)
    private val myId get() = prefs.getString("id", "") ?: ""
    private val token get() = prefs.getString("token", "") ?: ""

    override fun onCreate(state: Bundle?) { super.onCreate(state); window.statusBarColor = Color.WHITE; window.navigationBarColor = bg; if (myName.isBlank() || myId.isBlank()) welcome() else home() }
    override fun onResume() { super.onResume(); if (myId.isNotBlank()) { syncIdentity(); flushPendingMessages() } }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun card() = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat(); setStroke(dp(1), line) }
    private fun root() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); layoutDirection = View.LAYOUT_DIRECTION_RTL }
    private fun txt(s: String, size: Float = 16f, color: Int = navy, bold: Boolean = false) = TextView(this).apply { text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD }
    private fun center(s: String, size: Float = 16f, color: Int = navy, bold: Boolean = false) = txt(s, size, color, bold).apply { gravity = Gravity.CENTER }
    private fun button(s: String, action: () -> Unit, color: Int = teal) = TextView(this).apply { text = s; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; background = android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = dp(15).toFloat() }; setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { action.invoke() } }
    private fun outline(s: String, action: () -> Unit) = TextView(this).apply { text = s; textSize = 14f; setTextColor(navy); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; background = card(); setOnClickListener { action.invoke() } }
    private fun field(hint: String, numeric: Boolean = false) = EditText(this).apply { this.hint = hint; textSize = 16f; setTextColor(navy); setHintTextColor(gray); setSingleLine(true); inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; background = card(); setPadding(dp(14), 0, dp(14), 0); layoutDirection = View.LAYOUT_DIRECTION_RTL }
    private fun add(p: LinearLayout, v: View, h: Int = -2, w: Float = 0f, m: Int = 0) { val lp = LinearLayout.LayoutParams(-1, if (h < 0) -2 else dp(h), w); if (m > 0) lp.setMargins(dp(m), dp(m), dp(m), dp(m)); p.addView(v, lp) }
    private fun header(title: String, subtitle: String = "", back: (() -> Unit)? = null) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(14), dp(8), dp(14), dp(8)); val b = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }; add(b, txt(title, 22f, navy, true), 30); if (subtitle.isNotBlank()) add(b, txt(subtitle, 12f, gray), 22); add(this, b, 0, 1f); if (back != null) addView(outline("‹ رجوع") { back.invoke() }, LinearLayout.LayoutParams(dp(82), dp(44))) }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun welcome() { val r = root(); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(22), dp(35), dp(22), dp(22)) }; add(c, center("وَثاق", 40f, navy, true), 58); add(c, center("هوية وتواصل عربي مستقل", 18f, gray, true), 40); add(c, center("البحث، جهات الاتصال والمراسلة حتى مع انقطاع الإنترنت.", 14f, gray), 58); add(c, button("⌁  تسجيل الدخول") { login() }, 56, 0f, 6); add(c, button("＋  إنشاء هوية جديدة", { register() }, Color.rgb(28, 71, 91)), 56, 0f, 6); add(r, c, 0, 1f); setContentView(r) }

    private fun identityForm(title: String, subtitle: String, actionText: String, submit: (String, Int) -> Unit) {
        val r = root(); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(20), dp(10), dp(20), dp(20)) }
        add(c, header(title, subtitle) { welcome() }); add(c, txt("الاسم الثلاثي", 14f, navy, true), 32, 0f, 4)
        val name = field("مثال: حاتم حسين الحاج رمضان"); add(c, name, 56, 0f, 2); add(c, txt("سنة الميلاد", 14f, navy, true), 32, 0f, 4)
        val year = field("مثال: 1995", true); add(c, year, 56, 0f, 2)
        add(c, button(actionText) {
            val n = name.text.toString().trim(); val y = year.text.toString().toIntOrNull(); val count = n.split(Regex("\\s+")).count { it.isNotBlank() }; val max = Calendar.getInstance().get(Calendar.YEAR)
            if (count < 3) toast("اكتب الاسم الثلاثي") else if (y == null || y < 1900 || y > max) toast("سنة الميلاد غير صحيحة") else submit.invoke(n, y)
        }, 56, 0f, 8)
        add(c, outline("إلغاء") { welcome() }, 50, 0f, 3); add(r, c, 0, 1f); setContentView(r)
    }

    private fun login() = identityForm("تسجيل الدخول", "الاسم الثلاثي + سنة الميلاد", "دخول إلى وَثاق") { n, y ->
        if (myName == n && myYear == y && myId.isNotBlank() && token.isNotBlank()) home() else request("POST", "/api/login", JSONObject().put("name", n).put("birthYear", y).put("deviceKey", deviceKey()), false) { ok, body -> runOnUiThread { if (ok && saveServer(body)) home() else toast(loginError(body)) } }
    }
    private fun register() = identityForm("إنشاء هوية وَثاق", "سيظهر معرفك من أي هاتف عند البحث عنه", "إنشاء المعرف والدخول") { n, y ->
        request("POST", "/api/identity", JSONObject().put("name", n).put("birthYear", y).put("deviceKey", deviceKey()), false) { ok, body -> runOnUiThread { if (ok && saveServer(body)) home() else { saveLocalIdentity(n, y); toast("حُفظت الهوية محليًا وستتم مزامنتها عند عودة الإنترنت"); home() } } }
    }
    private fun loginError(body: JSONObject) = when (body.optString("error")) { "user_not_found" -> "لم يتم العثور على الهوية. تحقق من الاسم وسنة الميلاد."; "rate_limited" -> "محاولات كثيرة، حاول بعد قليل."; "invalid_identity" -> "بيانات الهوية غير صحيحة."; else -> "تعذر الاتصال بخادم وَثاق. تحقق من الإنترنت وحاول مجددًا." }

    private fun home() { val r = root(); add(r, header("وَثاق", "مرحبًا $myName")); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(14), dp(8), dp(14), dp(14)) }; add(c, center("@$myId", 16f, teal, true), 45, 0f, 5); val q = field("ابحث بالاسم أو معرف وَثاق"); add(c, q, 54, 0f, 4); add(c, button("⌕  بحث عن شخص") { search(q.text.toString()) }, 54, 0f, 4); add(c, button("◉  جهات الاتصال", { contacts() }, Color.rgb(28, 71, 91)), 54, 0f, 4); add(c, button("◎  ملفي ومعرفي", { profile() }, Color.rgb(70, 90, 100)), 54, 0f, 4); add(c, outline("↪  تسجيل الخروج") { prefs.edit().clear().apply(); welcome() }, 50, 0f, 5); add(r, c, 0, 1f); setContentView(r); flushPendingMessages() }
    private fun profile() { val r = root(); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(20), dp(12), dp(20), dp(20)) }; add(c, header("ملفي", "هوية وَثاق") { home() }); add(c, center(myName, 23f, navy, true), 45); add(c, center("@$myId", 16f, teal, true), 45); add(c, button("⧉  نسخ المعرف") { val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("Wethaq ID", myId)); toast("تم نسخ المعرف") }, 54, 0f, 8); add(r, c, 0, 1f); setContentView(r) }

    private fun search(value: String) { val q = value.trim().removePrefix("@"); if (q.length < 2) { toast("اكتب اسمًا أو معرفًا"); return }; request("GET", "/api/search?q=${java.net.URLEncoder.encode(q, "UTF-8")}", null, false) { ok, body -> runOnUiThread { if (ok) showResults(body.optJSONArray("users") ?: JSONArray()) else toast("تعذر البحث الآن") } } }
    private fun showResults(arr: JSONArray) { val r = root(); val sc = ScrollView(this); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(14), dp(8), dp(14), dp(20)) }; add(c, header("نتائج البحث", "اختر الشخص لإضافته أو مراسلته") { home() }); if (arr.length() == 0) add(c, center("لم يتم العثور على المستخدم", 16f, gray), 80); for (i in 0 until arr.length()) { val u = arr.optJSONObject(i) ?: continue; val id = u.optString("wethaq_id"); val name = u.optString("name").ifBlank { "مستخدم وَثاق" }; val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; background = card(); setPadding(dp(14), dp(10), dp(14), dp(10)) }; add(item, txt(name, 17f, navy, true), 30); add(item, txt("@$id", 14f, teal, true), 28); add(item, button("＋ حفظ جهة الاتصال") { saveContact(u); contacts() }, 48, 0f, 4); add(item, outline("✉ مراسلة") { openChat(id, name) }, 44, 0f, 3); add(c, item, 0, 1f, 5) }; sc.addView(c); add(r, sc, 0, 1f); setContentView(r) }

    private fun saveContact(user: JSONObject) { val id = user.optString("wethaq_id"); if (id.isBlank()) return; val normalized = JSONObject().put("wethaq_id", id).put("name", user.optString("name").ifBlank { "مستخدم وَثاق" }).put("birth_year", user.optInt("birth_year", 0)); val contacts = JSONArray(prefs.getString("contacts", "[]") ?: "[]"); var found = false; for (i in 0 until contacts.length()) if (contacts.optJSONObject(i)?.optString("wethaq_id") == id) found = true; if (!found) contacts.put(normalized); prefs.edit().putString("contacts", contacts.toString()).apply(); if (token.isNotBlank() && hasNetwork()) request("POST", "/api/contacts", JSONObject().put("wethaqId", id), true) { _, _ -> }; toast("تم حفظ ${normalized.optString("name")}\n@$id") }
    private fun contacts() { val r = root(); val sc = ScrollView(this); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(14), dp(8), dp(14), dp(20)) }; add(c, header("جهات الاتصال", "الاسم والمعرف محفوظان معًا") { home() }); val local = JSONArray(prefs.getString("contacts", "[]") ?: "[]"); if (local.length() == 0) add(c, center("لا توجد جهات اتصال محفوظة", 16f, gray), 70); for (i in 0 until local.length()) { val u = local.optJSONObject(i) ?: continue; val id = u.optString("wethaq_id"); val name = u.optString("name").ifBlank { "مستخدم وَثاق" }; val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; background = card(); setPadding(dp(14), dp(9), dp(14), dp(9)) }; add(row, txt(name, 17f, navy, true), 30); add(row, txt("@$id", 14f, teal, true), 28); add(row, button("✉  مراسلة") { openChat(id, name) }, 46, 0f, 4); add(c, row, 0, 1f, 5) }; sc.addView(c); add(r, sc, 0, 1f); setContentView(r); if (token.isNotBlank() && hasNetwork()) pullContacts() }
    private fun pullContacts() { request("GET", "/api/contacts", null, true) { ok, body -> if (ok) { val arr = body.optJSONArray("contacts") ?: JSONArray(); prefs.edit().putString("contacts", arr.toString()).apply() } } }

    private fun openChat(id: String, name: String) { val r = root(); val sc = ScrollView(this); val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL; setPadding(dp(12), dp(6), dp(12), dp(10)) }; add(c, header(name, "@$id") { contacts() }); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }; add(c, box, 0, 1f); val input = field("اكتب رسالتك..."); input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES; add(c, input, 55, 0f, 3); add(c, button("➤ إرسال") { val body = input.text.toString().trim(); if (body.isBlank()) toast("اكتب رسالة أولًا") else { input.setText(""); addBubble(box, body, true, "جارٍ الإرسال"); sendMessage(id, body) } }, 52, 0f, 2); sc.addView(c); add(r, sc, 0, 1f); setContentView(r); loadMessages(id, box) }
    private fun addBubble(box: LinearLayout, body: String, mine: Boolean, status: String) { val v = TextView(this).apply { text = "$body\n$status"; textSize = 15f; setTextColor(if (mine) Color.WHITE else navy); background = android.graphics.drawable.GradientDrawable().apply { setColor(if (mine) teal else Color.WHITE); cornerRadius = dp(14).toFloat(); setStroke(dp(1), line) }; setPadding(dp(13), dp(9), dp(13), dp(9)); gravity = Gravity.CENTER_VERTICAL }; val lp = LinearLayout.LayoutParams(-2, -2); lp.gravity = if (mine) Gravity.END else Gravity.START; lp.setMargins(dp(5), dp(4), dp(5), dp(4)); box.addView(v, lp) }
    private fun loadMessages(id: String, box: LinearLayout) { if (!hasNetwork() || token.isBlank()) return; request("GET", "/api/messages/${java.net.URLEncoder.encode(id, "UTF-8")}", null, true) { ok, body -> if (ok) runOnUiThread { box.removeAllViews(); val arr = body.optJSONArray("messages") ?: JSONArray(); for (i in 0 until arr.length()) { val m = arr.optJSONObject(i) ?: continue; addBubble(box, m.optString("body"), m.optString("sender_wethaq_id") == myId, if (m.optString("sender_wethaq_id") == myId) m.optString("status", "sent") else "مستلمة") } } } }

    private fun sendMessage(to: String, body: String) { if (!hasNetwork() || token.isBlank()) { queueMessage(to, body); toast("حُفظت الرسالة وسترسل عند عودة الإنترنت"); return }; request("POST", "/api/messages", JSONObject().put("to", to).put("body", body), true) { ok, _ -> runOnUiThread { if (ok) toast("تم إرسال الرسالة") else { queueMessage(to, body); toast("تعذر الإرسال الآن، حُفظت الرسالة للإرسال لاحقًا") } } } }
    private fun queueMessage(to: String, body: String) { val arr = JSONArray(prefs.getString("pending", "[]") ?: "[]"); arr.put(JSONObject().put("to", to).put("body", body)); prefs.edit().putString("pending", arr.toString()).apply() }
    private fun flushPendingMessages() { if (!hasNetwork() || token.isBlank()) return; val old = JSONArray(prefs.getString("pending", "[]") ?: "[]"); if (old.length() == 0) return; for (i in 0 until old.length()) { val m = old.optJSONObject(i) ?: continue; request("POST", "/api/messages", JSONObject().put("to", m.optString("to")).put("body", m.optString("body")), true) { _, _ -> } }; prefs.edit().putString("pending", "[]").apply() }
    private fun syncIdentity() { if (!hasNetwork() || myName.isBlank() || token.isBlank()) return; request("POST", "/api/identity/sync", JSONObject().put("name", myName).put("birthYear", myYear).put("wethaqId", myId).put("deviceKey", deviceKey()), false) { ok, body -> if (ok) saveServer(body) } }

    private fun request(method: String, path: String, json: JSONObject?, auth: Boolean, callback: (Boolean, JSONObject) -> Unit) { Thread { try { val b = Request.Builder().url(apiBase + path); if (method == "GET") b.get() else b.method(method, (json?.toString() ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType())); if (auth && token.isNotBlank()) b.header("Authorization", "Bearer $token"); http.newCall(b.build()).execute().use { response -> val raw = response.body?.string() ?: "{}"; callback(response.isSuccessful, try { JSONObject(raw) } catch (_: Exception) { JSONObject().put("error", "bad_response") }) } } catch (_: Exception) { callback(false, JSONObject().put("error", "network")) } }.start() }
    private fun saveServer(body: JSONObject): Boolean { val u = body.optJSONObject("user") ?: return false; val id = u.optString("wethaq_id"); val name = u.optString("name"); if (id.isBlank() || name.isBlank()) return false; prefs.edit().putString("id", id).putString("name", name).putInt("year", u.optInt("birth_year", 0)).putString("token", body.optString("token")).apply(); return true }
    private fun saveLocalIdentity(name: String, year: Int) { prefs.edit().putString("id", makeLocalId(name, year)).putString("name", name).putInt("year", year).apply() }
    private fun makeLocalId(name: String, year: Int) = name.split(Regex("\\s+")).filter { it.isNotBlank() }.take(3).joinToString("_") + year
    private fun deviceKey(): String { val old = prefs.getString("device_key", ""); if (!old.isNullOrBlank()) return old; val b = ByteArray(24); SecureRandom().nextBytes(b); val value = b.joinToString("") { "%02x".format(it) }; prefs.edit().putString("device_key", value).apply(); return value }
    private fun hasNetwork(): Boolean = try { (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork != null } catch (_: Exception) { true }
}
