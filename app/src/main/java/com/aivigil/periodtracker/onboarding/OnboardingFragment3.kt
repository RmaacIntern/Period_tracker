package com.aivigil.periodtracker.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aivigil.periodtracker.databinding.FragmentOnboarding3Binding
import com.aivigil.periodtracker.util.ThemeHelper

class OnboardingFragment3 : Fragment() {

    private var _binding: FragmentOnboarding3Binding? = null
    private val binding get() = _binding!!

    private var heightCm = 162
    private var weightKg = 60.0f

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding3Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ FIX — replaced hardcoded gradient and card backgrounds with ThemeHelper
        binding.onboarding3Root.background = ThemeHelper.onboardingGradient(requireContext())
        binding.heightCard.background      = ThemeHelper.cardBg(requireContext(), 20f)
        binding.weightCard.background      = ThemeHelper.cardBg(requireContext(), 20f)

        binding.heightSlider.value    = heightCm.toFloat()
        binding.tvHeightValue.text    = "$heightCm cm"
        binding.weightSlider.value    = weightKg
        binding.tvWeightValue.text    = formatWeight(weightKg)

        binding.heightSlider.addOnChangeListener { _, value, _ ->
            heightCm = value.toInt()
            binding.tvHeightValue.text = "$heightCm cm"
        }
        binding.weightSlider.addOnChangeListener { _, value, _ ->
            weightKg = value
            binding.tvWeightValue.text = formatWeight(weightKg)
        }
    }

    private fun formatWeight(kg: Float): String = "%.1f kg".format(kg)

    fun getSelectedHeightCm(): Int = heightCm
    fun getSelectedWeightKg(): Float = weightKg

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}