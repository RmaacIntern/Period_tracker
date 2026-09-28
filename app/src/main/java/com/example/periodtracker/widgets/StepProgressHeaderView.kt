package com.example.periodtracker.widgets

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.periodtracker.databinding.ViewStepHeaderBinding

class StepProgressHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private val binding: ViewStepHeaderBinding =
        ViewStepHeaderBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        // Track (background pill) — built in code, no drawable file
        binding.progressTrack.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(Color.parseColor("#F0E8ED"))
        }

        // Fill (pink pill) — built in code, no drawable file
        binding.progressFill.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(Color.parseColor("#F28B9D"))
        }
    }

    /** Call this on every onboarding screen with its step number. */
    fun setStep(current: Int, total: Int) {
        binding.tvStepLabel.text = "STEP $current OF $total"

        binding.progressTrack.post {
            val trackWidth = binding.progressTrack.width
            val fillWidth = (trackWidth * (current.toFloat() / total.toFloat())).toInt()
            binding.progressFill.layoutParams = binding.progressFill.layoutParams.apply {
                width = fillWidth
            }
        }
    }

    fun setOnBackClickListener(action: () -> Unit) {
        binding.btnBack.setOnClickListener { action() }
    }
}