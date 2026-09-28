package com.example.periodtracker.onboarding

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.periodtracker.databinding.FragmentOnboarding8Binding
import com.example.periodtracker.onboarding.viewmodel.OnboardingViewModel
import com.google.android.material.slider.Slider

class OnboardingFragment8 : Fragment() {

    private var _binding: FragmentOnboarding8Binding? = null
    private val binding get() = _binding!!

    // Shared with all onboarding fragments via the hosting Activity
    private val viewModel: OnboardingViewModel by activityViewModels()

    companion object {
        private const val CYCLE_MIN = 21
        private const val CYCLE_MAX = 45
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

        // Same gradient background as all other onboarding screens
        binding.onboarding8Root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#FDF2F5"),
                Color.parseColor("#FBF5F8"),
                Color.parseColor("#FFFFFF")
            )
        )

        applyStyles()
        setupCycleLengthSlider()
        setupPeriodLengthSlider()
    }

    private fun applyStyles() {
        binding.moonBadge.background = roundedBg("#FBEFDD", 18f)
        binding.cycleIconBg.background = roundedBg("#DFF3F0", 16f)
        binding.periodIconBg.background = roundedBg("#FCE4EC", 16f)
        binding.cycleLengthCard.background = roundedBg("#FFFFFF", 20f)
        binding.periodLengthCard.background = roundedBg("#FFFFFF", 20f)
        // Slider colors (thumb/track) are set directly in XML via
        // app:thumbColor / app:trackColorActive / app:trackColorInactive.
    }

    private fun setupCycleLengthSlider() {
        val initial = viewModel.cycleLength.takeIf { it in CYCLE_MIN..CYCLE_MAX } ?: 28
        binding.seekCycleLength.value = initial.toFloat()
        binding.tvCycleLengthValue.text = "$initial days"
        viewModel.cycleLength = initial

        binding.seekCycleLength.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            val days = value.toInt()
            binding.tvCycleLengthValue.text = "$days days"
            viewModel.cycleLength = days
        })
    }

    private fun setupPeriodLengthSlider() {
        val initial = viewModel.periodDuration.takeIf { it in PERIOD_MIN..PERIOD_MAX } ?: 5
        binding.seekPeriodLength.value = initial.toFloat()
        binding.tvPeriodLengthValue.text = "$initial days"
        viewModel.periodDuration = initial

        binding.seekPeriodLength.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            val days = value.toInt()
            binding.tvPeriodLengthValue.text = "$days days"
            viewModel.periodDuration = days
        })
    }

    private fun roundedBg(colorHex: String, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(Color.parseColor(colorHex))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}