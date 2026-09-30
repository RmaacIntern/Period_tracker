package com.aivigil.periodtracker.onboarding

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.aivigil.periodtracker.R
import com.aivigil.periodtracker.databinding.FragmentOnboarding5Binding
import com.aivigil.periodtracker.util.ThemeHelper

class OnboardingFragment5 : Fragment() {

    private var _binding: FragmentOnboarding5Binding? = null
    private val binding get() = _binding!!

    enum class ActivityLevel { GENTLE, BALANCED, VERY_ACTIVE }

    private var selectedLevel = ActivityLevel.BALANCED

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding5Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ FIX — replaced hardcoded gradient and icon circle with ThemeHelper
        binding.onboarding5Root.background = ThemeHelper.onboardingGradient(requireContext())
        binding.moveIconCircle.background  = ThemeHelper.iconCirclePink(requireContext())

        binding.cardGentle.setOnClickListener   { selectLevel(ActivityLevel.GENTLE) }
        binding.cardBalanced.setOnClickListener { selectLevel(ActivityLevel.BALANCED) }
        binding.cardActive.setOnClickListener   { selectLevel(ActivityLevel.VERY_ACTIVE) }

        renderSelection()
    }

    private fun selectLevel(level: ActivityLevel) {
        selectedLevel = level
        renderSelection()
    }

    private fun renderSelection() {
        // ✅ FIX — unselected card bg uses ThemeHelper.cardBg() so dark mode
        // shows a dark card instead of white
        binding.cardGentle.background   = ThemeHelper.cardBg(requireContext(), 18f)
        binding.cardBalanced.background = ThemeHelper.cardBg(requireContext(), 18f)
        binding.cardActive.background   = ThemeHelper.cardBg(requireContext(), 18f)

        // ✅ FIX — unselected text colors use context.getColor() from color resources
        val textPrimary   = requireContext().getColor(R.color.text_primary)
        val textSecondary = requireContext().getColor(R.color.text_secondary)

        binding.tvGentleLabel.setTextColor(textPrimary)
        binding.tvGentleDesc.setTextColor(textSecondary)
        binding.tvBalancedLabel.setTextColor(textPrimary)
        binding.tvBalancedDesc.setTextColor(textSecondary)
        binding.tvActiveLabel.setTextColor(textPrimary)
        binding.tvActiveDesc.setTextColor(textSecondary)

        setUnchecked(binding.checkGentle, binding.ivCheckGentle)
        setUnchecked(binding.checkBalanced, binding.ivCheckBalanced)
        setUnchecked(binding.checkActive, binding.ivCheckActive)

        val (card, labelView, descView, circleView, iconView) = when (selectedLevel) {
            ActivityLevel.GENTLE      -> CardRefs(binding.cardGentle,   binding.tvGentleLabel,   binding.tvGentleDesc,   binding.checkGentle,   binding.ivCheckGentle)
            ActivityLevel.BALANCED    -> CardRefs(binding.cardBalanced, binding.tvBalancedLabel, binding.tvBalancedDesc, binding.checkBalanced, binding.ivCheckBalanced)
            ActivityLevel.VERY_ACTIVE -> CardRefs(binding.cardActive,   binding.tvActiveLabel,   binding.tvActiveDesc,   binding.checkActive,   binding.ivCheckActive)
        }

        // Selected card always uses brand gradient — looks correct in both modes
        card.background = gradientBg()
        labelView.setTextColor(Color.WHITE)
        descView.setTextColor(Color.parseColor("#F5E6F0"))
        setChecked(circleView, iconView)
    }

    private fun setChecked(circle: View, icon: ImageView) {
        circle.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        icon.setImageResource(R.drawable.ic_check)
        icon.setColorFilter(Color.parseColor("#E63A5E"))
    }

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

    fun getSelectedActivityLevel(): ActivityLevel = selectedLevel

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}