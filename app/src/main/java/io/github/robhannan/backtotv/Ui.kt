package io.github.robhannan.backtotv

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers for the code-built screens (no layout XML, no libraries). */
internal fun Context.text(s: String, size: Float = 20f, color: Int = Color.WHITE): TextView = TextView(this).apply {
    text = s
    textSize = size
    setTextColor(color)
}

internal fun Context.column(vararg views: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(96, 64, 96, 64)
    views.forEach { v ->
        addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 })
    }
}

internal const val GREY = 0xFFB0B8C8.toInt()
internal const val BG = 0xFF101522.toInt()
