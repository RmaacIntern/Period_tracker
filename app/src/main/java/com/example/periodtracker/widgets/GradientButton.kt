package com.example.periodtracker.widgets

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatButton


class GradientButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {

    init {
        setupStyle()
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density

    private fun setupStyle() {
        isAllCaps = false
        setTextColor(Color.WHITE)
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(24).toInt(), dp(16).toInt(), dp(24).toInt(), dp(16).toInt())
        stateListAnimator = null

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            colors = intArrayOf(
                Color.parseColor("#EC4899"),
                Color.parseColor("#A855F7")
            )
            cornerRadius = dp(100) // large enough to always render as a pill
        }

        // subtle press feedback since stateListAnimator/ripple is stripped
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().alpha(0.85f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().alpha(1f).setDuration(120).start()
                    v.performClick()
                }
            }
            false
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }
}