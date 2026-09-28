package com.example.periodtracker.onboarding

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.periodtracker.databinding.FragmentOnboarding2Binding

class OnboardingFragment2 : Fragment() {

    private var _binding: FragmentOnboarding2Binding? = null
    private val binding get() = _binding!!

    private var selectedAge = 27

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Root gradient background
        binding.onboarding2Root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#FDF2F5"),
                Color.parseColor("#FBF5F8"),
                Color.parseColor("#FFFFFF")
            )
        )

        // Age card — white with rounded corners
        binding.ageCard.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 22f * resources.displayMetrics.density
            setColor(Color.parseColor("#FFFFFF"))
        }

        // Init slider + label
        binding.ageSlider.value = selectedAge.toFloat()
        binding.tvAgeValue.text = selectedAge.toString()

        binding.ageSlider.addOnChangeListener { _, value, _ ->
            selectedAge = value.toInt()
            binding.tvAgeValue.text = selectedAge.toString()
        }
    }

    /** Called from the Activity's Continue button before saving to Room. */
    fun getSelectedAge(): Int = selectedAge

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}