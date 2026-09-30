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
import com.aivigil.periodtracker.util.ThemeHelper

class OnboardingFragment6 : Fragment() {

    private var _binding: FragmentOnboarding6Binding? = null
    private val binding get() = _binding!!
    private var nativeAdHelper: NativeAdHelper? = null

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

        // ✅ FIX — replaced hardcoded gradient and icon circle with ThemeHelper
        binding.onboarding6Root.background = ThemeHelper.onboardingGradient(requireContext())
        binding.goalIconCircle.background  = ThemeHelper.iconCirclePink(requireContext())

        nativeAdHelper = NativeAdHelper(requireContext())
        nativeAdHelper?.loadInto(binding.nativeAdContainer)

        // Card is always selected — brand gradient + white tick. Fine in both modes.
        binding.cardGoal.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#E63A5E"), Color.parseColor("#A855F7"))
        ).apply { cornerRadius = 18f * resources.displayMetrics.density }

        binding.checkGoal.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
        }
        binding.ivCheckGoal.setImageResource(R.drawable.ic_check)
        binding.ivCheckGoal.setColorFilter(Color.parseColor("#E63A5E"))
    }

    fun getSelectedGoal(): Goal = Goal.TRACK_CYCLE

    override fun onDestroyView() {
        super.onDestroyView()
        nativeAdHelper?.destroy()
        nativeAdHelper = null
        _binding = null
    }
}