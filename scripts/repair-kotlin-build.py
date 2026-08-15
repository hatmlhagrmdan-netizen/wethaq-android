from pathlib import Path
import re

path = Path("app/src/main/java/com/wethaq/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

s = s.replace('private fun text(value: String, size: Float = 16f, color: Int = navy, bold: Boolean = false)', 'private fun text(value: String?, size: Float = 16f, color: Int = navy, bold: Boolean = false)')
s = s.replace('map[ch]?.let{append(it)}?:if(ch.isLetterOrDigit())append(ch)', 'map[ch]?.let{append(it)} ?: run { if(ch.isLetterOrDigit()) append(ch) }')
s = s.replace('name.replace("ـ", "").split(Regex("[\\\\s،,]+"), " ").filter { it.isNotBlank() }.map { w -> known[w] ?: w.mapNotNull { map[it] }.joinToString("").replaceFirstChar { it.uppercase() } }.filter { it.isNotBlank() }', 'name.replace("ـ", "").split(Regex("[\\\\s،,]+" )).filter { word -> word.isNotBlank() }.map { word -> known[word] ?: word.map { ch -> map[ch] ?: "" }.joinToString("").replaceFirstChar { ch -> ch.uppercase() } }.filter { part -> part.isNotBlank() }')
s = s.replace('background = white;', 'setBackgroundColor(white);')
s = s.replace('background=white;', 'setBackgroundColor(white);')
s = s.replace('putString("wethaq_id", u.optString("wethaq_id")).putString("name", u.optString("name"))', 'putString("wethaq_id", u.optString("wethaq_id")).putString("server_id", u.optString("id")).putString("name", u.optString("name"))')

# Use the approved founder portrait. Do not turn profile_photo into profile_photo_photo.
s = re.sub(r'R\.drawable\.profile(?!_photo)', 'R.drawable.profile_photo', s)
s = s.replace('R.drawable.profile_photo_photo', 'R.drawable.profile_photo')

# Explicit passwordless login entry point.
old = 'add(c, button("إنشاء هويتي", { createIdentity() }), -1, 58, 0f, 8); add(c, center("لا توجد كلمة مرور ولا رقم هاتف", 12f, muted), -1, 30)'
new = '''add(c, button("تسجيل الدخول", { login() }), -1, 58, 0f, 8)
        add(c, outline("إنشاء هوية جديدة", { createIdentity() }), -1, 54, 0f, 6)
        add(c, center("الاسم الثلاثي + سنة الميلاد فقط • بدون كلمة مرور • بدون رقم هاتف", 12f, muted), -1, 42)'''
if old in s:
    s = s.replace(old, new, 1)

# Login screen: same identity endpoint, no password.
marker = '    private fun createIdentity() {'
if marker in s and 'private fun login() {' not in s:
    login_method = '''    private fun login() {
        val r = root(); val scroll = ScrollView(this)
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(24)); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        add(c, header("تسجيل الدخول", "الاسم الثلاثي وسنة الميلاد فقط", { welcome() }))
        add(c, avatar(92, true), 92, 92, 0f, 4)
        val name = edit("الاسم الثلاثي")
        val year = edit("سنة الميلاد، مثال: 1995", true)
        add(c, text("الاسم الكامل", 14f, navy, true), -1, 28, 0f, 3)
        add(c, name, -1, 56, 0f, 3)
        add(c, text("سنة الميلاد", 14f, navy, true), -1, 28, 0f, 3)
        add(c, year, -1, 56, 0f, 3)
        val preview = center("سيظهر معرفك هنا", 14f, teal, true)
        preview.background = shape(Color.WHITE, 16, border)
        add(c, preview, -1, 58, 0f, 8)
        fun refresh() {
            val n = name.text.toString().trim(); val y = year.text.toString().trim()
            preview.text = if (n.isBlank() || y.isBlank()) "سيظهر معرفك هنا" else "@${localId(n, y)}"
        }
        name.addTextChangedListener { refresh() }
        year.addTextChangedListener { refresh() }
        add(c, button("دخول إلى وَثاق", {
            val n = name.text.toString().trim()
            val yi = year.text.toString().trim().toIntOrNull()
            val words = n.split(Regex("\\\\s+")).filter { it.isNotBlank() }
            val max = Calendar.getInstance().get(Calendar.YEAR)
            if (words.size < 3) { toast("اكتب الاسم الثلاثي كاملًا"); return@button }
            if (yi == null || yi !in 1900..max) { toast("سنة الميلاد غير صحيحة"); return@button }
            api("POST", "/api/identity", JSONObject().put("name", n).put("birthYear", yi).put("deviceKey", deviceKey())) { ok, body ->
                handler.post {
                    if (ok) { saveIdentity(body); home() }
                    else toast(error(body))
                }
            }
        }), -1, 58, 0f, 8)
        add(c, outline("إنشاء هوية جديدة", { createIdentity() }), -1, 54, 0f, 6)
        scroll.addView(c); add(r, scroll, -1, 0, 1f); setContentView(r)
    }

'''
    s = s.replace(marker, login_method + marker, 1)

path.write_text(s, encoding="utf-8")
print("Wethaq Kotlin/UI repairs applied.")
