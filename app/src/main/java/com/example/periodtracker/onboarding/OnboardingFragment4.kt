package com.example.periodtracker.onboarding

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.example.periodtracker.databinding.FragmentOnboarding4Binding
import com.example.periodtracker.onboarding.viewmodel.OnboardingViewModel

class OnboardingFragment4 : Fragment() {

    private var _binding: FragmentOnboarding4Binding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by activityViewModels()

    private lateinit var conditionChips: List<Chip>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding4Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.onboarding4Root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#FDF2F5"),
                Color.parseColor("#FBF5F8"),
                Color.parseColor("#FFFFFF")
            )
        )

        binding.heartIconCircle.background = filledCircle("#FDE2E9")

        conditionChips = listOf(
            binding.chipPcos, binding.chipEndometriosis, binding.chipThyroid,
            binding.chipDiabetes, binding.chipBloodPressure, binding.chipAnemia,
            binding.chipMigraines, binding.chipAnxiety
        )

        // Selecting any real condition clears "None of these"
        conditionChips.forEach { chip ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) binding.chipNoneOfThese.isChecked = false
                saveToViewModel()
            }
        }

        // Selecting "None of these" clears every condition chip
        binding.chipNoneOfThese.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) conditionChips.forEach { it.isChecked = false }
            saveToViewModel()
        }
    }

    private fun saveToViewModel() {
        viewModel.conditions = if (binding.chipNoneOfThese.isChecked) {
            ""
        } else {
            conditionChips.filter { it.isChecked }.joinToString(", ") { it.text.toString() }
        }
    }

    /** Still available if Activity needs it directly */
    fun getSelectedConditions(): List<String> =
        conditionChips.filter { it.isChecked }.map { it.text.toString() }

    fun isNoneSelected(): Boolean = binding.chipNoneOfThese.isChecked

    private fun filledCircle(colorHex: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(colorHex))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}