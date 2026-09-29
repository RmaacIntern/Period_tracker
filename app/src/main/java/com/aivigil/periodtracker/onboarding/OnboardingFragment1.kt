package com.aivigil.periodtracker.onboarding

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aivigil.periodtracker.ads.NativeAdHelper
import com.aivigil.periodtracker.databinding.FragmentOnboarding1Binding

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

        // Root gradient background
        binding.onboarding1Root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#FDF2F5"),
                Color.parseColor("#FBF5F8"),
                Color.parseColor("#FFFFFF")
            )
        )

        // Icon circle
        binding.iconCircle.clipToOutline = true
        binding.iconCircle.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        // Backgrounds
        binding.badgePrivacy.background = roundedBg("#FDE2E9", 20f)
        binding.nameInputCard.background = roundedBg("#FFFFFF", 20f)

        // Scroll to input on focus — ✅ single listener, no duplicate
        binding.etName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollView.postDelayed({
                    binding.scrollView.smoothScrollTo(0, binding.nameInputCard.top)
                }, 100)
            }
        }

        // Native ad
        nativeAdHelper = NativeAdHelper(requireContext())
        nativeAdHelper?.loadInto(binding.nativeAdContainer)
    }

    private fun roundedBg(colorHex: String, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(Color.parseColor(colorHex))
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