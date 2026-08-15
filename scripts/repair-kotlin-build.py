from pathlib import Path

path = Path("app/src/main/java/com/wethaq/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

s = s.replace(
    'private fun text(value: String, size: Float = 16f, color: Int = navy, bold: Boolean = false)',
    'private fun text(value: String?, size: Float = 16f, color: Int = navy, bold: Boolean = false)'
)
s = s.replace(
    'map[ch]?.let{append(it)}?:if(ch.isLetterOrDigit())append(ch)',
    'map[ch]?.let{append(it)} ?: run { if(ch.isLetterOrDigit()) append(ch) }'
)
s = s.replace(
    'name.replace("ـ", "").split(Regex("[\\\\s،,]+"), " ").filter { it.isNotBlank() }.map { w -> known[w] ?: w.mapNotNull { map[it] }.joinToString("").replaceFirstChar { it.uppercase() } }.filter { it.isNotBlank() }',
    'name.replace("ـ", "").split(Regex("[\\\\s،,]+" )).filter { word -> word.isNotBlank() }.map { word -> known[word] ?: word.map { ch -> map[ch] ?: "" }.joinToString("").replaceFirstChar { ch -> ch.uppercase() } }.filter { part -> part.isNotBlank() }'
)
# TextView.background expects a Drawable.
s = s.replace('background = white;', 'setBackgroundColor(white);')
s = s.replace('background=white;', 'setBackgroundColor(white);')
# Correctly classify sent messages as the current user's messages.
s = s.replace(
    'putString("wethaq_id", u.optString("wethaq_id")).putString("name", u.optString("name"))',
    'putString("wethaq_id", u.optString("wethaq_id")).putString("server_id", u.optString("id")).putString("name", u.optString("name"))'
)
path.write_text(s, encoding="utf-8")
print("Wethaq Kotlin/UI repairs applied.")
