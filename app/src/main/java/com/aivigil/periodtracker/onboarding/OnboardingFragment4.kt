package com.aivigil.periodtracker.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.aivigil.periodtracker.databinding.FragmentOnboarding4Binding
import com.aivigil.periodtracker.onboarding.viewmodel.OnboardingViewModel
import com.aivigil.periodtracker.util.ThemeHelper

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

        // ✅ FIX — replaced hardcoded gradient and icon circle with ThemeHelper
        binding.onboarding4Root.background   = ThemeHelper.onboardingGradient(requireContext())
        binding.heartIconCircle.background   = ThemeHelper.iconCirclePink(requireContext())

        conditionChips = listOf(
            binding.chipPcos, binding.chipEndometriosis, binding.chipThyroid,
            binding.chipDiabetes, binding.chipBloodPressure, binding.chipAnemia,
            binding.chipMigraines, binding.chipAnxiety
        )

        conditionChips.forEach { chip ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) binding.chipNoneOfThese.isChecked = false
                saveToViewModel()
            }
        }

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

    fun getSelectedConditions(): List<String> =
        conditionChips.filter { it.isChecked }.map { it.text.toString() }

    fun isNoneSelected(): Boolean = binding.chipNoneOfThese.isChecked

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}