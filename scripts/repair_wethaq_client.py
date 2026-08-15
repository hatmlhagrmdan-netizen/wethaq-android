from pathlib import Path
import re

path = Path("app/src/main/java/com/wethaq/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# 1) الصورة الجديدة المعتمدة رسميًا في المستودع.
s = s.replace("R.drawable.profile_photo", "R.drawable.profile")

# 2) لا ندخل مباشرة إلى الشاشة الرئيسية ببيانات جلسة قديمة أو منتهية.
s = s.replace(
    'if (token.isBlank() || myId.isBlank()) welcome() else home()',
    'if (token.isBlank() || myId.isBlank()) welcome() else validateSession()'
)

# 3) نصوص عربية أوضح وأقل عرضة للقص داخل TextView.
s = s.replace("includeFontPadding = false", "includeFontPadding = true")

# 4) اجعل الأحجام الثابتة في مساعد add بوحدات dp بدل البكسل الخام.
old_add = '''    private fun add(parent: LinearLayout, view: View, width: Int = -1, height: Int = -2, weight: Float = 0f, margin: Int = 0) {
        val h = if (height == -2) LinearLayout.LayoutParams.WRAP_CONTENT else dp(height)
        val p = LinearLayout.LayoutParams(width, h, weight)
        if (margin > 0) p.setMargins(dp(margin), dp(margin), dp(margin), dp(margin))
        parent.addView(view, p)
    }'''
new_add = '''    private fun add(parent: LinearLayout, view: View, width: Int = -1, height: Int = -2, weight: Float = 0f, margin: Int = 0) {
        val h = if (height == -2) LinearLayout.LayoutParams.WRAP_CONTENT else dp(height)
        val actualWidth = when {
            width == -1 -> LinearLayout.LayoutParams.MATCH_PARENT
            width == 0 -> 0
            else -> dp(width)
        }
        val p = LinearLayout.LayoutParams(actualWidth, h, weight)
        if (margin > 0) p.setMargins(dp(margin), dp(margin), dp(margin), dp(margin))
        parent.addView(view, p)
    }'''
if old_add in s:
    s = s.replace(old_add, new_add)

# 5) جلسة موثوقة: إذا كان التوكن قديمًا نمسحه، وإذا كانت الشبكة منقطعة لا نحذف الهوية المحلية.
if "private fun validateSession()" not in s:
    marker = '    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()\n'
    function = '''
    private fun validateSession() {
        api("GET", "/api/me", null) { ok, body ->
            handler.post {
                if (ok) {
                    home()
                    return@post
                }
                when (body.optString("error")) {
                    "unauthorized", "invalid_token", "user_not_found" -> {
                        clearIdentity()
                        welcome()
                    }
                    else -> {
                        home()
                        toast("تم فتح وثاق محليًا، وسيُعاد الاتصال عند توفر الشبكة.")
                    }
                }
            }
        }
    }

    private fun clearIdentity() {
        val device = prefs.getString("device_key", "") ?: ""
        prefs.edit().clear().putString("device_key", device).apply()
        socket?.close(1000, "identity cleared")
        socket = null
    }
'''
    s = s.replace(marker, marker + function)

path.write_text(s, encoding="utf-8")
print("Wethaq client repair applied")
