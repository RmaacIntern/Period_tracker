package com.example.periodtracker.onboarding

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.periodtracker.R
import com.example.periodtracker.databinding.FragmentOnboarding5Binding

class OnboardingFragment5 : Fragment() {

    private var _binding: FragmentOnboarding5Binding? = null
    private val binding get() = _binding!!

    enum class ActivityLevel { GENTLE, BALANCED, VERY_ACTIVE }

    private var selectedLevel = ActivityLevel.BALANCED // matches screenshot's default

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding5Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Same gradient background as all other onboarding screens
        binding.onboarding5Root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#FDF2F5"),
                Color.parseColor("#FBF5F8"),
                Color.parseColor("#FFFFFF")
            )
        )

        binding.moveIconCircle.background = filledCircle("#FDE2E9")

        binding.cardGentle.setOnClickListener { selectLevel(ActivityLevel.GENTLE) }
        binding.cardBalanced.setOnClickListener { selectLevel(ActivityLevel.BALANCED) }
        binding.cardActive.setOnClickListener { selectLevel(ActivityLevel.VERY_ACTIVE) }

        renderSelection()
    }

    private fun selectLevel(level: ActivityLevel) {
        selectedLevel = level
        renderSelection()
    }

    private fun renderSelection() {
        // Reset all cards to unselected (white) state first
        binding.cardGentle.background = roundedBg("#FFFFFF", 18f)
        binding.cardBalanced.background = roundedBg("#FFFFFF", 18f)
        binding.cardActive.background = roundedBg("#FFFFFF", 18f)

        binding.tvGentleLabel.setTextColor(Color.parseColor("#2D1B33"))
        binding.tvGentleDesc.setTextColor(Color.parseColor("#8A7A8F"))
        binding.tvBalancedLabel.setTextColor(Color.parseColor("#2D1B33"))
        binding.tvBalancedDesc.setTextColor(Color.parseColor("#8A7A8F"))
        binding.tvActiveLabel.setTextColor(Color.parseColor("#2D1B33"))
        binding.tvActiveDesc.setTextColor(Color.parseColor("#8A7A8F"))

        // Unchecked state for all: outline circle, no background fill, muted grey icon
        setUnchecked(binding.checkGentle, binding.ivCheckGentle)
        setUnchecked(binding.checkBalanced, binding.ivCheckBalanced)
        setUnchecked(binding.checkActive, binding.ivCheckActive)

        // Highlight the selected card with the gradient + white text + white filled checkmark
        val (card, labelView, descView, circleView, iconView) = when (selectedLevel) {
            ActivityLevel.GENTLE -> CardRefs(binding.cardGentle, binding.tvGentleLabel, binding.tvGentleDesc, binding.checkGentle, binding.ivCheckGentle)
            ActivityLevel.BALANCED -> CardRefs(binding.cardBalanced, binding.tvBalancedLabel, binding.tvBalancedDesc, binding.checkBalanced, binding.ivCheckBalanced)
            ActivityLevel.VERY_ACTIVE -> CardRefs(binding.cardActive, binding.tvActiveLabel, binding.tvActiveDesc, binding.checkActive, binding.ivCheckActive)
        }

        card.background = gradientBg()
        labelView.setTextColor(Color.WHITE)
        descView.setTextColor(Color.parseColor("#F5E6F0"))
        setChecked(circleView, iconView)
    }

    /** Selected state: white filled circle, colored checkmark on top of the gradient card. */
    private fun setChecked(circle: View, icon: ImageView) {
        circle.background = filledCircle("#FFFFFF")
        icon.setImageResource(R.drawable.ic_check)
        icon.setColorFilter(Color.parseColor("#E63A5E"))
    }

    /** Unselected state: no fill, muted outline circle from ic_uncheck's own stroke color. */
    private fun setUnchecked(circle: View, icon: ImageView) {
        circle.background = null
        icon.setImageResource(R.drawable.ic_uncheck)
        icon.clearColorFilter()
    }

    private data class CardRefs(
        val card: View,
        val label: TextView,
        val desc: TextView,
        val circle: View,
        val icon: ImageView
    )

    private fun gradientBg() = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.parseColor("#E63A5E"), Color.parseColor("#A855F7"))
    ).apply {
        cornerRadius = 18f * resources.displayMetrics.density
    }

    private fun filledCircle(colorHex: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(colorHex))
    }

    private fun roundedBg(colorHex: String, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(Color.parseColor(colorHex))
    }

    /** Call from the Activity's Continue button, and later when saving to Room. */
    fun getSelectedActivityLevel(): ActivityLevel = selectedLevel

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}