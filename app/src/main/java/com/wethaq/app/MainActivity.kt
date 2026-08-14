package com.wethaq.app

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48)
        }

        val logo = TextView(this).apply {
            text = "وَثاق"
            textSize = 36f
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "تطبيق وَثاق\nنسخة الإنتاج"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 32)
        }

        val enter = Button(this).apply {
            text = "بدء الاستخدام"
            setOnClickListener { }
        }

        root.addView(logo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(subtitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(enter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }
}
