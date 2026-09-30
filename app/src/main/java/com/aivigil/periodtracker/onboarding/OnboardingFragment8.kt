package com.aivigil.periodtracker.onboarding

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.aivigil.periodtracker.R
import com.aivigil.periodtracker.databinding.FragmentOnboarding8Binding
import com.aivigil.periodtracker.onboarding.viewmodel.OnboardingViewModel
import com.aivigil.periodtracker.util.ThemeHelper
import com.google.android.material.slider.Slider

class OnboardingFragment8 : Fragment() {

    private var _binding: FragmentOnboarding8Binding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by activityViewModels()

    companion object {
        private const val CYCLE_MIN  = 21
        private const val CYCLE_MAX  = 45
        private const val PERIOD_MIN = 2
        private const val PERIOD_MAX = 10
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding8Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ FIX — replaced hardcoded gradient with ThemeHelper
        binding.onboarding8Root.background = ThemeHelper.onboardingGradient(requireContext())

        applyStyles()
        setupCycleLengthSlider()
        setupPeriodLengthSlider()
    }

    private fun applyStyles() {
        // ✅ FIX — icon badge backgrounds use context color resources
        // so they darken correctly in dark mode instead of staying light
        binding.moonBadge.background       = roundedBgFromColor(R.color.icon_bg_teal,   18f)
        binding.cycleIconBg.background     = roundedBgFromColor(R.color.icon_bg_teal,   16f)
        binding.periodIconBg.background    = roundedBgFromColor(R.color.icon_bg_pink,   16f)

        // ✅ FIX — card backgrounds use ThemeHelper instead of hardcoded #FFFFFF
        binding.cycleLengthCard.background  = ThemeHelper.cardBg(requireContext(), 20f)
        binding.periodLengthCard.background = ThemeHelper.cardBg(requireContext(), 20f)
    }

    private fun roundedBgFromColor(colorRes: Int, radiusDp: Float) = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(requireContext().getColor(colorRes))
    }

    private fun setupCycleLengthSlider() {
        val initial = viewModel.cycleLength.takeIf { it in CYCLE_MIN..CYCLE_MAX } ?: 28
        binding.seekCycleLength.value       = initial.toFloat()
        binding.tvCycleLengthValue.text     = "$initial days"
        viewModel.cycleLength               = initial

        binding.seekCycleLength.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            val days = value.toInt()
            binding.tvCycleLengthValue.text = "$days days"
            viewModel.cycleLength           = days
        })
    }

    private fun setupPeriodLengthSlider() {
        val initial = viewModel.periodDuration.takeIf { it in PERIOD_MIN..PERIOD_MAX } ?: 5
        binding.seekPeriodLength.value       = initial.toFloat()
        binding.tvPeriodLengthValue.text     = "$initial days"
        viewModel.periodDuration             = initial

        binding.seekPeriodLength.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            val days = value.toInt()
            binding.tvPeriodLengthValue.text = "$days days"
            viewModel.periodDuration         = days
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}