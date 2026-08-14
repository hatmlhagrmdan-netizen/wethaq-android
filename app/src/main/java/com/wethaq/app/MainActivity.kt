package com.wethaq.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("wethaq", Context.MODE_PRIVATE) }
    private val navy = Color.rgb(16, 28, 46)
    private val teal = Color.rgb(0, 137, 123)
    private val bg = Color.rgb(248, 250, 252)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (prefs.getBoolean("onboarded", false)) showHome() else showWelcome()
    }

    private fun base(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
        setPadding(28, 40, 28, 28)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun title(text: String, size: Float = 28f): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(navy)
        gravity = Gravity.CENTER
        setPadding(8, 12, 8, 12)
    }

    private fun button(text: String, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(Color.WHITE)
        setBackgroundColor(teal)
        setOnClickListener { action() }
        isAllCaps = false
    }

    private fun showWelcome() {
        val root = base()
        val space = View(this)
        root.addView(space, LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(title("وَثاق", 42f))
        root.addView(title("تواصل عربي مستقل\nبهوية بسيطة وخصوصية أولاً", 18f))
        root.addView(View(this), LinearLayout.LayoutParams(1, 24))
        root.addView(button("بدء الاستخدام") { showCreateProfile() }, LinearLayout.LayoutParams(-1, 58))
        val info = TextView(this).apply {
            text = "لا يوجد حساب جاهز بعد. أنشئ هويتك في وَثاق للمتابعة."
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 22, 8, 8)
        }
        root.addView(info)
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        setContentView(root)
    }

    private fun showCreateProfile() {
        val root = base()
        root.addView(title("إنشاء هوية وَثاق"))
        val intro = TextView(this).apply {
            text = "اختر اسمك الظاهر، ثم أنشئ معرّفًا من ثلاثة أجزاء بدل رقم الهاتف."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 20)
        }
        root.addView(intro)

        val display = field("الاسم الظاهر")
        val first = field("المعرّف الأول")
        val second = field("المعرّف الثاني")
        val third = field("المعرّف الثالث")
        root.addView(display)
        root.addView(first)
        root.addView(second)
        root.addView(third)
        root.addView(View(this), LinearLayout.LayoutParams(1, 16))
        root.addView(button("إنشاء حساب محلي") {
            val name = display.text.toString().trim()
            val a = first.text.toString().trim()
            val b = second.text.toString().trim()
            val c = third.text.toString().trim()
            if (name.isEmpty() || a.isEmpty() || b.isEmpty() || c.isEmpty()) {
                Toast.makeText(this, "أكمل جميع الحقول أولاً", Toast.LENGTH_SHORT).show()
                return@button
            }
            prefs.edit().putBoolean("onboarded", true).putString("name", name)
                .putString("id", "$a-$b-$c").apply()
            showHome()
        }, LinearLayout.LayoutParams(-1, 58))
        root.addView(button("رجوع") { showWelcome() }, LinearLayout.LayoutParams(-1, 52))
        setContentView(root)
    }

    private fun field(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 16f
        setSingleLine(true)
        setPadding(20, 4, 20, 4)
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        val p = LinearLayout.LayoutParams(-1, 56)
        p.setMargins(0, 5, 0, 5)
    }

    private fun showHome() {
        val root = base()
        val name = prefs.getString("name", "مستخدم وَثاق") ?: "مستخدم وَثاق"
        val id = prefs.getString("id", "") ?: ""
        root.addView(title("وَثاق", 30f))
        val header = TextView(this).apply {
            text = "مرحبًا، $name\n@$id"
            textSize = 18f
            setTextColor(navy)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 24)
        }
        root.addView(header)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        content.addView(section("المحادثات", "لا توجد محادثات بعد. أضف أول جهة اتصال باستخدام معرّف وَثاق."))
        content.addView(section("جهات الاتصال", "ابحث عن الأشخاص بواسطة المعرّف الثلاثي، دون الحاجة إلى رقم هاتف."))
        content.addView(section("الملف الشخصي", "الاسم: $name\nالمعرّف: @$id"))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button("إضافة جهة اتصال") {
            Toast.makeText(this, "ميزة البحث عن جهات الاتصال ستُربط بالخادم في المرحلة التالية", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(-1, 56))
        root.addView(button("الإعدادات") {
            Toast.makeText(this, "الإعدادات الأساسية ستُضاف مع نظام الحسابات والمزامنة", Toast.LENGTH_LONG).show()
        }, LinearLayout.LayoutParams(-1, 52))
        setContentView(root)
    }

    private fun section(head: String, body: String): TextView = TextView(this).apply {
        text = "$head\n$body"
        textSize = 16f
        setTextColor(navy)
        setBackgroundColor(Color.WHITE)
        setPadding(22, 22, 22, 22)
        layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, 14)
        }
    }
}
