package com.aivigil.periodtracker.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aivigil.periodtracker.ads.NativeAdHelper
import com.aivigil.periodtracker.databinding.FragmentOnboarding1Binding
import com.aivigil.periodtracker.util.ThemeHelper

class OnboardingFragment1 : Fragment() {

    private var _binding: FragmentOnboarding1Binding? = null
    private val binding get() = _binding!!
    private var nativeAdHelper: NativeAdHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ FIX — replaced hardcoded Color.parseColor("#FDF2F5/FBF5F8/FFFFFF")
        binding.onboarding1Root.background = ThemeHelper.onboardingGradient(requireContext())

        // Icon circle — clipToOutline handled in XML already, kept for safety
        binding.iconCircle.clipToOutline = true
        binding.iconCircle.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        // ✅ FIX — replaced hardcoded Color.parseColor("#FDE2E9") rounded rect
        binding.badgePrivacy.background = ThemeHelper.cardBg(requireContext(), 20f)

        // ✅ FIX — replaced hardcoded Color.parseColor("#FFFFFF") rounded rect
        binding.nameInputCard.background = ThemeHelper.cardBg(requireContext(), 20f)

        binding.etName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollView.postDelayed({
                    binding.scrollView.smoothScrollTo(0, binding.nameInputCard.top)
                }, 100)
            }
        }

        nativeAdHelper = NativeAdHelper(requireContext())
        nativeAdHelper?.loadInto(binding.nativeAdContainer)
    }

    fun showNameError(message: String = "* Name is required") {
        binding.tvNameError.text = message
        binding.tvNameError.visibility = View.VISIBLE
        binding.etName.requestFocus()
    }

    fun hideNameError() {
        binding.tvNameError.visibility = View.GONE
    }

    fun getEnteredName(): String? {
        val value = binding.etName.text?.toString()?.trim()
        return value?.ifEmpty { null }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        nativeAdHelper?.destroy()
        nativeAdHelper = null
        _binding = null
    }
}