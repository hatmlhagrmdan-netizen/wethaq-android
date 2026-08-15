from pathlib import Path

path = Path("app/src/main/java/com/wethaq/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# Use the single approved photo that actually exists in the repository.
s = s.replace("R.drawable.profile)", "R.drawable.profile_photo)")
s = s.replace("R.drawable.profile,", "R.drawable.profile_photo,")
s = s.replace("R.drawable.profile;", "R.drawable.profile_photo;")
s = s.replace("R.drawable.profile)", "R.drawable.profile_photo)")
# Repair an accidental double suffix if an older version of this script already ran.
s = s.replace("R.drawable.profile_photo_photo", "R.drawable.profile_photo")

# Never regress the session flow to the old direct-home behavior.
s = s.replace(
    'if(token.isBlank()||myId.isBlank()) welcome() else home()',
    'if(token.isBlank()||myId.isBlank()) welcome() else validateSession()'
)
s = s.replace(
    'if (token.isBlank() || myId.isBlank()) welcome() else home()',
    'if (token.isBlank() || myId.isBlank()) welcome() else validateSession()'
)

# Keep Arabic text vertically readable.
s = s.replace("includeFontPadding=false", "includeFontPadding=true")
s = s.replace("includeFontPadding = false", "includeFontPadding = true")

# Ensure the add() helper treats width=0 as a weighted zero width and -1 as match-parent.
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

path.write_text(s, encoding="utf-8")
print("Wethaq client repair applied: approved profile photo + stable session flow")
