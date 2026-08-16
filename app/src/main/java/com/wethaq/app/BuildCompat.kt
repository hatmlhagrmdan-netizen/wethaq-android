package com.wethaq.app

import android.view.View
import android.widget.LinearLayout

/**
 * Compatibility overload for calls written as add(parent, button(...){...}, ...).
 * Kotlin otherwise binds the trailing lambda to add instead of the nested view factory.
 */
fun MainActivity.add(
    parent: LinearLayout,
    view: View,
    height: Int = -2,
    weight: Float = 0f,
    margin: Int = 0,
    action: () -> Unit
) {
    val lp = LinearLayout.LayoutParams(
        -1,
        if (height < 0) -2 else (height * resources.displayMetrics.density).toInt(),
        weight
    )
    if (margin > 0) lp.setMargins(
        (margin * resources.displayMetrics.density).toInt(),
        (margin * resources.displayMetrics.density).toInt(),
        (margin * resources.displayMetrics.density).toInt(),
        (margin * resources.displayMetrics.density).toInt()
    )
    view.setOnClickListener { action() }
    parent.addView(view, lp)
}
