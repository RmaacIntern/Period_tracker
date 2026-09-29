package com.aivigil.periodtracker.profile.sheets

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.aivigil.periodtracker.R
import com.aivigil.periodtracker.onboarding.OnboardingFragment5.ActivityLevel

class ActivitySheet(
    current: ActivityLevel,
    private val onSave: (ActivityLevel) -> Unit,
) : BottomSheetDialogFragment() {

    private var selected = current

    private lateinit var cardGentle: View
    private lateinit var cardBalanced: View
    private lateinit var cardActive: View

    private lateinit var tvGentleLabel: TextView
    private lateinit var tvBalancedLabel: TextView
    private lateinit var tvActiveLabel: TextView

    private lateinit var tvGentleDesc: TextView
    private lateinit var tvBalancedDesc: TextView
    private lateinit var tvActiveDesc: TextView

    private lateinit var checkGentle: View
    private lateinit var checkBalanced: View
    private lateinit var checkActive: View

    private lateinit var ivCheckGentle: ImageView
    private lateinit var ivCheckBalanced: ImageView
    private lateinit var ivCheckActive: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_activity_level, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cardGentle = view.findViewById(R.id.cardGentle)
        cardBalanced = view.findViewById(R.id.cardBalanced)
        cardActive = view.findViewById(R.id.cardActive)

        tvGentleLabel = view.findViewById(R.id.tvGentleLabel)
        tvBalancedLabel = view.findViewById(R.id.tvBalancedLabel)
        tvActiveLabel = view.findViewById(R.id.tvActiveLabel)

        tvGentleDesc = view.findViewById(R.id.tvGentleDesc)
        tvBalancedDesc = view.findViewById(R.id.tvBalancedDesc)
        tvActiveDesc = view.findViewById(R.id.tvActiveDesc)

        checkGentle = view.findViewById(R.id.checkGentle)
        checkBalanced = view.findViewById(R.id.checkBalanced)
        checkActive = view.findViewById(R.id.checkActive)

        ivCheckGentle = view.findViewById(R.id.ivCheckGentle)
        ivCheckBalanced = view.findViewById(R.id.ivCheckBalanced)
        ivCheckActive = view.findViewById(R.id.ivCheckActive)

        cardGentle.setOnClickListener { select(ActivityLevel.GENTLE) }
        cardBalanced.setOnClickListener { select(ActivityLevel.BALANCED) }
        cardActive.setOnClickListener { select(ActivityLevel.VERY_ACTIVE) }

        render()
    }

    private fun select(level: ActivityLevel) {
        selected = level
        render()
        onSave(selected)
        dismiss()
    }

    private fun render() {
        // Reset all
        listOf(cardGentle, cardBalanced, cardActive).forEach {
            it.background = roundedBg("#F7F0F5", 14f)
        }
        listOf(tvGentleLabel, tvBalancedLabel, tvActiveLabel).forEach {
            it.setTextColor(Color.parseColor("#2D1B33"))
        }
        listOf(tvGentleDesc, tvBalancedDesc, tvActiveDesc).forEach {
            it.setTextColor(Color.parseColor("#8A7A8F"))
        }
        setUnchecked(checkGentle, ivCheckGentle)
        setUnchecked(checkBalanced, ivCheckBalanced)
        setUnchecked(checkActive, ivCheckActive)

        // Highlight selected
        val (card, label, desc, circle, icon) = when (selected) {
            ActivityLevel.GENTLE -> Refs(cardGentle, tvGentleLabel, tvGentleDesc, checkGentle, ivCheckGentle)
            ActivityLevel.BALANCED -> Refs(cardBalanced, tvBalancedLabel, tvBalancedDesc, checkBalanced, ivCheckBalanced)
            ActivityLevel.VERY_ACTIVE -> Refs(cardActive, tvActiveLabel, tvActiveDesc, checkActive, ivCheckActive)
        }
        card.background = gradientBg()
        label.setTextColor(Color.WHITE)
        desc.setTextColor(Color.parseColor("#F5E6F0"))
        setChecked(circle, icon)
    }

    private fun setChecked(circle: View, icon: ImageView) {
        circle.background = filledCircle("#FFFFFF")
        icon.setImageResource(R.drawable.ic_check)
        icon.setColorFilter(Color.parseColor("#E63A5E"))
    }

    private fun setUnchecked(circle: View, icon: ImageView) {
        circle.background = null
        icon.setImageResource(R.drawable.ic_uncheck)
        icon.clearColorFilter()
    }

    private data class Refs(
        val card: View,
        val label: TextView,
        val desc: TextView,
        val circle: View,
        val icon: ImageView
    )

    private fun gradientBg() = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.parseColor("#E63A5E"), Color.parseColor("#A855F7"))
    ).apply { cornerRadius = 14f * resources.displayMetrics.density }

    private fun roundedBg(hex: String, r: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = r * resources.displayMetrics.density
        setColor(Color.parseColor(hex))
    }

    private fun filledCircle(hex: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(hex))
    }

    companion object {
        const val TAG = "ActivitySheet"
    }
}