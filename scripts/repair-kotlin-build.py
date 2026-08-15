from pathlib import Path

path = Path("app/src/main/java/com/wethaq/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Keep the source resilient to nullable SharedPreferences values.
s = s.replace(
    'private fun text(value: String, size: Float = 16f, color: Int = navy, bold: Boolean = false)',
    'private fun text(value: String?, size: Float = 16f, color: Int = navy, bold: Boolean = false)'
)

# Kotlin requires an else branch when an if-expression is used on the RHS of Elvis.
s = s.replace(
    'map[ch]?.let{append(it)}?:if(ch.isLetterOrDigit())append(ch)',
    'map[ch]?.let{append(it)} ?: run { if(ch.isLetterOrDigit()) append(ch) }'
)

# Store the server-side user id locally so incoming/outgoing message bubbles
# are aligned correctly without exposing the id in the UI.
s = s.replace(
    'putString("wethaq_id", u.optString("wethaq_id")).putString("name", u.optString("name"))',
    'putString("wethaq_id", u.optString("wethaq_id")).putString("server_id", u.optString("id")).putString("name", u.optString("name"))'
)

path.write_text(s, encoding="utf-8")
print("Wethaq Kotlin build repairs applied.")
