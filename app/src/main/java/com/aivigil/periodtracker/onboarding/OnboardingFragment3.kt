package com.aivigil.periodtracker.onboarding

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aivigil.periodtracker.databinding.FragmentOnboarding3Binding

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

        binding.onboarding3Root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#FDF2F5"),
                Color.parseColor("#FBF5F8"),
                Color.parseColor("#FFFFFF")
            )
        )
        binding.heightCard.background = roundedBg("#FFFFFF", 20f)
        binding.weightCard.background = roundedBg("#FFFFFF", 20f)

        binding.heightSlider.value = heightCm.toFloat()
        binding.tvHeightValue.text = "$heightCm cm"

        binding.weightSlider.value = weightKg
        binding.tvWeightValue.text = formatWeight(weightKg)

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

    private fun roundedBg(colorHex: String, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(Color.parseColor(colorHex))
    }

    /** Call from the Activity's Continue button, and later when saving to Room. */
    fun getSelectedHeightCm(): Int = heightCm
    fun getSelectedWeightKg(): Float = weightKg

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}