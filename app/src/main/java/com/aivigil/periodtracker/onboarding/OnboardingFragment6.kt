package com.aivigil.periodtracker.onboarding

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aivigil.periodtracker.R
import com.aivigil.periodtracker.ads.NativeAdHelper
import com.aivigil.periodtracker.databinding.FragmentOnboarding6Binding

class OnboardingFragment6 : Fragment() {

    private var _binding: FragmentOnboarding6Binding? = null
    private val binding get() = _binding!!
    private var nativeAdHelper: NativeAdHelper? = null

    // Single goal — both features are always included
    enum class Goal { TRACK_CYCLE }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding6Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.onboarding6Root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#FDF2F5"),
                Color.parseColor("#FBF5F8"),
                Color.parseColor("#FFFFFF")
            )
        )

        nativeAdHelper = NativeAdHelper(requireContext())       // in Fragment

        nativeAdHelper?.loadInto(binding.nativeAdContainer)

        binding.goalIconCircle.background = filledCircle("#FDE2E9")

        // Card is always selected — apply gradient and tick once
        binding.cardGoal.background = gradientBg()
        binding.checkGoal.background = filledCircle("#FFFFFF")
        binding.ivCheckGoal.setImageResource(R.drawable.ic_check)
        binding.ivCheckGoal.setColorFilter(Color.parseColor("#E63A5E"))
    }

    fun getSelectedGoal(): Goal = Goal.TRACK_CYCLE

    private fun gradientBg() = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.parseColor("#E63A5E"), Color.parseColor("#A855F7"))
    ).apply {
        cornerRadius = 18f * resources.displayMetrics.density
    }

    private fun filledCircle(colorHex: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(colorHex))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        nativeAdHelper?.destroy()
        nativeAdHelper = null
        _binding = null
    }
}