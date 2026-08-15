from pathlib import Path

path = Path("app/src/main/java/com/wethaq/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Kotlin nullability: SharedPreferences#getString returns String?, while the
# UI helper previously required a non-null String.
s = s.replace(
    'private fun text(value: String, size: Float = 16f, color: Int = navy, bold: Boolean = false)',
    'private fun text(value: String?, size: Float = 16f, color: Int = navy, bold: Boolean = false)'
)

# Kotlin requires an else branch when an if-expression is used on the RHS of
# the Elvis operator. Keep the transliteration behaviour while making it a
# statement inside run {}.
s = s.replace(
    'map[ch]?.let{append(it)}?:if(ch.isLetterOrDigit())append(ch)',
    'map[ch]?.let{append(it)} ?: run { if(ch.isLetterOrDigit()) append(ch) }'
)

path.write_text(s, encoding="utf-8")
print("Wethaq Kotlin build repairs applied.")
