package com.example.periodtracker.onboarding

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.periodtracker.databinding.FragmentOnboarding1Binding

class OnboardingFragment1 : Fragment() {

    private var _binding: FragmentOnboarding1Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.onboarding1Root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#FDF2F5"),
                Color.parseColor("#FBF5F8"),
                Color.parseColor("#FFFFFF")
            )
        )

        binding.iconCircle.clipToOutline = true
        binding.iconCircle.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        binding.badgePrivacy.background = roundedBg("#FDE2E9", 20f)
        binding.nameInputCard.background = roundedBg("#FFFFFF", 20f)

        binding.etName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollView.postDelayed({
                    binding.scrollView.smoothScrollTo(0, binding.nameInputCard.top)
                }, 100)
            }
        }

// Handle keyboard show/hide
        binding.etName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.scrollView.postDelayed({
                    binding.scrollView.smoothScrollTo(0, binding.nameInputCard.top)
                }, 100)
            }
        }
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
        _binding = null
    }
}