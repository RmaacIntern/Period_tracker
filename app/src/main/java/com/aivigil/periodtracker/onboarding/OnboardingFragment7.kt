package com.aivigil.periodtracker.onboarding

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aivigil.periodtracker.ads.NativeAdHelper
import com.aivigil.periodtracker.databinding.FragmentOnboarding7Binding
import com.aivigil.periodtracker.util.ThemeHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class OnboardingFragment7 : Fragment() {

    private var _binding: FragmentOnboarding7Binding? = null
    private var nativeAdHelper: NativeAdHelper? = null
    private val binding get() = _binding!!

    private var selectedDate: LocalDate? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding7Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ✅ FIX — replaced hardcoded gradient and circle backgrounds with ThemeHelper
        binding.onboarding7Root.background      = ThemeHelper.onboardingGradient(requireContext())
        binding.calendarIconCircle.background   = ThemeHelper.iconCirclePink(requireContext())
        binding.startDateCard.background        = ThemeHelper.cardBg(requireContext(), 18f)
        binding.startDateIconCircle.background  = ThemeHelper.iconCircleGreen(requireContext())

        // ✅ FIX — chip backgrounds use context color resources
        binding.chipToday.background        = chipBg(unselected = true)
        binding.chipYesterday.background    = chipBg(unselected = true)
        binding.chipSelectedDate.background = chipBg(unselected = true)

        nativeAdHelper = NativeAdHelper(requireContext())
        nativeAdHelper?.loadInto(binding.nativeAdContainer)

        binding.btnEditDate.setOnClickListener      { showDatePicker() }
        binding.chipToday.setOnClickListener        { updateDate(LocalDate.now()) }
        binding.chipYesterday.setOnClickListener    { updateDate(LocalDate.now().minusDays(1)) }
        binding.chipSelectedDate.setOnClickListener { showDatePicker() }

        selectedDate?.let { updateDate(it) } ?: run {
            binding.tvSelectedStartDate.text = "Tap to select date"
            // ✅ FIX — use color resource instead of hardcoded hex
            binding.tvSelectedStartDate.setTextColor(
                requireContext().getColor(com.aivigil.periodtracker.R.color.text_secondary)
            )
        }
    }

    private fun showDatePicker() {
        val initial = selectedDate ?: LocalDate.now()
        val dialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                updateDate(LocalDate.of(year, month + 1, dayOfMonth))
            },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        )
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun updateDate(date: LocalDate) {
        selectedDate = date

        binding.tvSelectedStartDate.text = date.format(
            DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
        )
        // Green date color — lighter green in dark mode for contrast
        binding.tvSelectedStartDate.setTextColor(requireContext().getColor(com.aivigil.periodtracker.R.color.text_success))

        val today = LocalDate.now()
        binding.chipSelectedDate.text = when (date) {
            today              -> "Today"
            today.minusDays(1) -> "Yesterday"
            else               -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        }

        val isToday     = date == today
        val isYesterday = date == today.minusDays(1)
        val isCustom    = !isToday && !isYesterday

        val whiteColor = Color.WHITE
        val primaryColor = requireContext().getColor(com.aivigil.periodtracker.R.color.text_primary)

        binding.chipToday.background        = chipBg(unselected = !isToday)
        binding.chipToday.setTextColor(if (isToday) whiteColor else primaryColor)

        binding.chipYesterday.background    = chipBg(unselected = !isYesterday)
        binding.chipYesterday.setTextColor(if (isYesterday) whiteColor else primaryColor)

        binding.chipSelectedDate.background = chipBg(unselected = !isCustom)
        binding.chipSelectedDate.setTextColor(if (isCustom) whiteColor else primaryColor)
        binding.chipSelectedDate.visibility = if (isCustom) View.VISIBLE else View.GONE
    }

    // ✅ FIX — chip colors use context resources for dark mode compatibility
    private fun chipBg(unselected: Boolean) = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = 16f * resources.displayMetrics.density
        setColor(
            if (unselected) requireContext().getColor(com.aivigil.periodtracker.R.color.surface_card_inner)
            else            requireContext().getColor(com.aivigil.periodtracker.R.color.brand_pink)
        )
    }

    fun getSelectedStartDate(): LocalDate? = selectedDate

    override fun onDestroyView() {
        super.onDestroyView()
        nativeAdHelper?.destroy()
        nativeAdHelper = null
        _binding = null
    }
}