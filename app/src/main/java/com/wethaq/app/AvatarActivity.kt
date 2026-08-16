package com.wethaq.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*

class AvatarActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("wethaq_v10", MODE_PRIVATE) }
    private val teal = Color.rgb(0, 137, 123)
    private val navy = Color.rgb(17, 38, 54)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(40, 40, 40, 40); setBackgroundColor(Color.rgb(247,249,250)) }
        val title = TextView(this).apply { text = "الصورة الشخصية"; textSize = 24f; setTextColor(navy); gravity = Gravity.CENTER }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(Color.WHITE) }
        val current = prefs.getString("avatar_uri", "") ?: ""
        if (current.isNotBlank()) try { image.setImageURI(Uri.parse(current)) } catch (_: Exception) {}
        else image.setImageResource(android.R.drawable.ic_menu_gallery)
        val choose = Button(this).apply { text = "اختيار صورة"; setOnClickListener { pick() } }
        val remove = Button(this).apply { text = "حذف الصورة"; setOnClickListener { prefs.edit().remove("avatar_uri").apply(); image.setImageResource(android.R.drawable.ic_menu_gallery); Toast.makeText(this@AvatarActivity,"تم حذف الصورة",Toast.LENGTH_SHORT).show() } }
        val back = Button(this).apply { text = "رجوع"; setOnClickListener { finish() } }
        root.addView(title, LinearLayout.LayoutParams(-1, 70))
        root.addView(image, LinearLayout.LayoutParams(420, 420).apply { setMargins(0,20,0,20) })
        root.addView(choose, LinearLayout.LayoutParams(-1, 56).apply { setMargins(0,8,0,8) })
        root.addView(remove, LinearLayout.LayoutParams(-1, 56).apply { setMargins(0,8,0,8) })
        root.addView(back, LinearLayout.LayoutParams(-1, 56).apply { setMargins(0,8,0,8) })
        setContentView(root)
    }
    private fun pick() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }
        startActivityForResult(intent, 1001)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            prefs.edit().putString("avatar_uri", uri.toString()).apply()
            setResult(RESULT_OK); recreate()
        }
    }
}
