package com.wethaq.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {
    private val background = Color.rgb(8, 27, 35)
    private val panel = Color.rgb(15, 48, 58)
    private val accent = Color.rgb(0, 190, 170)
    private val soft = Color.rgb(196, 220, 224)
    private val white = Color.WHITE
    private val handler = Handler(Looper.getMainLooper())
    private var opened = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = background
        window.navigationBarColor = background
        showEntrance()
        handler.postDelayed({ openApp() }, 2600)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(8), dp(4), dp(8), dp(4))
        layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun showEntrance() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(30), dp(24), dp(30))
            setBackgroundColor(background)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(28))
            background = rounded(panel, 28)
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_wethaq)
            contentDescription = "وَثاق"
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = rounded(Color.rgb(21, 67, 78), 60)
        }
        brand.addView(icon, LinearLayout.LayoutParams(dp(108), dp(108)))

        val title = text("وَثاق", 42f, white, true)
        title.alpha = 0f
        title.animate().alpha(1f).setDuration(700).start()
        brand.addView(title, LinearLayout.LayoutParams(-1, dp(64)))

        brand.addView(text("WETHAQ", 15f, accent, true), LinearLayout.LayoutParams(-1, dp(34)))
        brand.addView(text("منصة هوية وتواصل رقمية", 16f, soft), LinearLayout.LayoutParams(-1, dp(38)))

        val line = View(this).apply { setBackgroundColor(Color.rgb(42, 89, 98)) }
        val lineParams = LinearLayout.LayoutParams(dp(110), dp(1)).apply {
            topMargin = dp(10)
            bottomMargin = dp(10)
            gravity = Gravity.CENTER
        }
        brand.addView(line, lineParams)

        brand.addView(text("صاحب المشروع", 13f, Color.rgb(137, 176, 183), true), LinearLayout.LayoutParams(-1, dp(28)))
        brand.addView(text("حاتم حسين الحاج رمضان", 21f, white, true), LinearLayout.LayoutParams(-1, dp(40)))
        brand.addView(text("هوية رقمية • تواصل • ثقة", 13f, soft), LinearLayout.LayoutParams(-1, dp(32)))

        val enter = Button(this).apply {
            text = "الدخول إلى وَثاق"
            textSize = 16f
            setTextColor(white)
            isAllCaps = false
            background = rounded(accent, 16)
            setOnClickListener { openApp() }
        }
        val enterParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(18) }
        brand.addView(enter, enterParams)

        root.addView(brand, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(text("WETHAQ • إصدار Android", 12f, Color.rgb(115, 151, 158)), LinearLayout.LayoutParams(-1, dp(38)))
        setContentView(root)
    }

    private fun openApp() {
        if (opened) return
        opened = true
        handler.removeCallbacksAndMessages(null)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
