package com.example.periodtracker.onboarding

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.periodtracker.ads.NativeAdHelper
import com.example.periodtracker.databinding.FragmentOnboarding7Binding
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

        // Same gradient background as all other onboarding screens
        binding.onboarding7Root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#FDF2F5"),
                Color.parseColor("#FBF5F8"),
                Color.parseColor("#FFFFFF")
            )
        )

        nativeAdHelper = NativeAdHelper(requireContext())       // in Fragment

        nativeAdHelper?.loadInto(binding.nativeAdContainer)



        binding.calendarIconCircle.background = filledCircle("#FDE2E9")
        binding.startDateCard.background = roundedBg("#FFFFFF", 18f)
        binding.startDateIconCircle.background = filledCircle("#E6F5EC")

        binding.chipToday.background = chipBg(unselected = true)
        binding.chipYesterday.background = chipBg(unselected = true)
        binding.chipSelectedDate.background = chipBg(unselected = true)

        binding.btnEditDate.setOnClickListener { showDatePicker() }
        binding.chipToday.setOnClickListener { updateDate(LocalDate.now()) }
        binding.chipYesterday.setOnClickListener { updateDate(LocalDate.now().minusDays(1)) }
        binding.chipSelectedDate.setOnClickListener { showDatePicker() }

        selectedDate?.let { updateDate(it) } ?: run {
            binding.tvSelectedStartDate.text = "Tap to select date"
            binding.tvSelectedStartDate.setTextColor(Color.parseColor("#8A7A8F"))
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
        // Cannot select a future date — last period can't have started after today
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.show()
    }

    private fun updateDate(date: LocalDate) {
        selectedDate = date

        binding.tvSelectedStartDate.text = date.format(
            DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
        )
        binding.tvSelectedStartDate.setTextColor(Color.parseColor("#2D8A5F"))

        val today = LocalDate.now()
        binding.chipSelectedDate.text = when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        }

        // Highlight whichever quick-pick chip matches the current selection
        val isToday = date == today
        val isYesterday = date == today.minusDays(1)
        val isCustom = !isToday && !isYesterday

        binding.chipToday.background = chipBg(unselected = !isToday)
        binding.chipYesterday.background = chipBg(unselected = !isYesterday)
        binding.chipSelectedDate.background = chipBg(unselected = !isCustom)
        binding.chipSelectedDate.visibility = if (isCustom) View.VISIBLE else View.GONE
    }

    private fun chipBg(unselected: Boolean) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 16f * resources.displayMetrics.density
        setColor(Color.parseColor(if (unselected) "#F3F0F2" else "#FDE2E9"))
    }

    private fun filledCircle(colorHex: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(colorHex))
    }

    private fun roundedBg(colorHex: String, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(Color.parseColor(colorHex))
    }

    /** Call from the Activity's Continue button, and later when saving to Room. */
    fun getSelectedStartDate(): LocalDate? = selectedDate

    override fun onDestroyView() {
        super.onDestroyView()
        nativeAdHelper?.destroy()
        nativeAdHelper = null
        _binding = null
    }
}