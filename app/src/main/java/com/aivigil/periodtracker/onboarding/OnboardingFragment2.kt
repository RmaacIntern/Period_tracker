package com.aivigil.periodtracker.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aivigil.periodtracker.ads.NativeAdHelper
import com.aivigil.periodtracker.databinding.FragmentOnboarding2Binding
import com.aivigil.periodtracker.util.ThemeHelper

class OnboardingFragment2 : Fragment() {

    private var _binding: FragmentOnboarding2Binding? = null
    private val binding get() = _binding!!

    private var selectedAge = 27
    private var nativeAdHelper: NativeAdHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ FIX — replaced hardcoded gradient with ThemeHelper
        binding.onboarding2Root.background = ThemeHelper.onboardingGradient(requireContext())

        // ✅ FIX — replaced hardcoded #FFFFFF card background with ThemeHelper
        binding.ageCard.background = ThemeHelper.cardBg(requireContext(), 22f)

        binding.ageSlider.value = selectedAge.toFloat()
        binding.tvAgeValue.text = selectedAge.toString()
        binding.ageSlider.addOnChangeListener { _, value, _ ->
            selectedAge = value.toInt()
            binding.tvAgeValue.text = selectedAge.toString()
        }

        nativeAdHelper = NativeAdHelper(requireContext())
        nativeAdHelper?.loadInto(binding.nativeAdContainer)
    }

    fun getSelectedAge(): Int = selectedAge

    override fun onDestroyView() {
        super.onDestroyView()
        nativeAdHelper?.destroy()
        nativeAdHelper = null
        _binding = null
    }
}