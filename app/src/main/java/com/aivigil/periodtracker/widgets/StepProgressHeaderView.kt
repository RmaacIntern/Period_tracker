package com.aivigil.periodtracker.widgets

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.aivigil.periodtracker.R
import com.aivigil.periodtracker.databinding.ViewStepHeaderBinding

class StepProgressHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private val binding: ViewStepHeaderBinding =
        ViewStepHeaderBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        // ✅ FIX — replaced hardcoded hex with color resources so track
        // adapts to dark mode (light pink track → dark surface in dark mode)
        binding.progressTrack.background = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(context.getColor(R.color.surface_card_inner))
        }

        // Fill stays brand pink — correct in both modes
        binding.progressFill.background = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(context.getColor(R.color.brand_pink))
        }
    }

    fun setStep(current: Int, total: Int) {
        binding.tvStepLabel.text = "STEP $current OF $total"
        binding.progressTrack.post {
            val trackWidth = binding.progressTrack.width
            val fillWidth  = (trackWidth * (current.toFloat() / total.toFloat())).toInt()
            binding.progressFill.layoutParams = binding.progressFill.layoutParams.apply {
                width = fillWidth
            }
        }
    }

    fun setOnBackClickListener(action: () -> Unit) {
        binding.btnBack.setOnClickListener { action() }
    }
}