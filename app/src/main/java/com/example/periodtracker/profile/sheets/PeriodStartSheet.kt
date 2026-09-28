package com.example.periodtracker.profile.sheets

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.periodtracker.data.entity.PeriodEntry
import com.example.periodtracker.databinding.BottomSheetPeriodStartBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * PeriodStartSheet
 *
 * Same calendar UI as LastPeriodSheet.
 * Also shows last 3 logged periods so user can:
 *   - ✏️ correct a wrong start date
 *   - 🗑 delete a wrong entry
 */
class PeriodStartSheet(
    private val initialDate: LocalDate = LocalDate.now(),
    private val existingPeriods: List<PeriodEntry> = emptyList(),
    private val onConfirm: (LocalDate) -> Unit,
    private val onDelete: (Int) -> Unit = {},
    private val onCorrect: (Int, LocalDate) -> Unit = { _, _ -> },
    private val onDismiss: (() -> Unit)? = null   // ✅ ADD THIS
) : BottomSheetDialogFragment() {

    private var _b: BottomSheetPeriodStartBinding? = null
    private val b get() = _b!!

    private var selectedDate = initialDate
    private var displayMonth = YearMonth.from(initialDate)

    private val fullFmt  = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val shortFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = BottomSheetPeriodStartBinding.inflate(inflater, container, false)
        .also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        styleEditButton()
        updateHeader()
        updateMonthLabel()
        buildRecentPeriods()

        b.periodStartCalendar.displayMonth   = displayMonth
        b.periodStartCalendar.selectedDate   = selectedDate
        b.periodStartCalendar.maxDate        = LocalDate.now()
        b.periodStartCalendar.onDateSelected = { date ->
            selectedDate = date
            updateHeader()
        }

        b.btnPeriodStartPrev.setOnClickListener {
            displayMonth = displayMonth.minusMonths(1)
            b.periodStartCalendar.displayMonth = displayMonth
            updateMonthLabel()
        }
        b.btnPeriodStartNext.setOnClickListener {
            if (displayMonth < YearMonth.now()) {
                displayMonth = displayMonth.plusMonths(1)
                b.periodStartCalendar.displayMonth = displayMonth
                updateMonthLabel()
            }
        }

        b.btnPeriodStartCancel.setOnClickListener { dismiss() }
        b.btnPeriodStartOk.setOnClickListener {
            onConfirm(selectedDate)
            dismiss()
        }
    }

    private fun buildRecentPeriods() {
        val recent = existingPeriods
            .sortedByDescending { it.startDate }
            .take(3)

        if (recent.isEmpty()) {
            b.recentPeriodsSection.visibility = View.GONE
            return
        }

        b.recentPeriodsSection.visibility = View.VISIBLE
        b.recentPeriodsList.removeAllViews()

        recent.forEach { entry ->
            val row = LayoutInflater.from(requireContext())
                .inflate(android.R.layout.simple_list_item_1, b.recentPeriodsList, false)

            // Build the row manually for full control
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 12)
            }

            // Date label
            val tvDate = TextView(requireContext()).apply {
                val start = LocalDate.parse(entry.startDate).format(shortFmt)
                val end   = entry.endDate?.let { " → ${LocalDate.parse(it).format(shortFmt)}" } ?: " (ongoing)"
                text      = "🩸 $start$end"
                textSize  = 13f
                setTextColor(Color.parseColor("#2D1B33"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Edit button
            val btnEdit = TextView(requireContext()).apply {
                text      = "✏️"
                textSize  = 16f
                setPadding(16, 0, 18, 0)
                setOnClickListener {
                    // Open a sub-calendar to pick a correction date
                    showCorrectDatePicker(entry)
                }
            }

            // Delete button
            val btnDelete = TextView(requireContext()).apply {
                text      = "🗑"
                textSize  = 16f
                setPadding(8, 0, 0, 0)
                setOnClickListener {
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Delete this period?")
                        .setMessage("Remove the period starting ${LocalDate.parse(entry.startDate).format(shortFmt)}? This cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            onDelete(entry.id)
                            Toast.makeText(requireContext(), "Period entry deleted", Toast.LENGTH_SHORT).show()
                            dismiss()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }

            rowLayout.addView(tvDate)
            rowLayout.addView(btnEdit)
            rowLayout.addView(btnDelete)

            // Divider
            val divider = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.setMargins(0, 0, 0, 0) }
                setBackgroundColor(Color.parseColor("#F5EEF8"))
            }

            b.recentPeriodsList.addView(rowLayout)
            b.recentPeriodsList.addView(divider)
        }
    }

    private fun showCorrectDatePicker(entry: PeriodEntry) {
        val current = LocalDate.parse(entry.startDate)
        // Reuse the same SheetCalendarView inline — show a simple DatePickerDialog for correction
        android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val corrected = LocalDate.of(year, month + 1, day)
                onCorrect(entry.id, corrected)
                Toast.makeText(
                    requireContext(),
                    "Period date corrected to ${corrected.format(shortFmt)}",
                    Toast.LENGTH_SHORT
                ).show()
                dismiss()
            },
            current.year, current.monthValue - 1, current.dayOfMonth
        ).also { it.datePicker.maxDate = System.currentTimeMillis() }
            .show()
    }

    private fun updateHeader() {
        b.tvPeriodStartDate.text = selectedDate.format(fullFmt)
    }

    private fun updateMonthLabel() {
        b.tvPeriodStartMonth.text = displayMonth.format(monthFmt)
    }

//    private fun styleEditButton() {
//        b.btnPeriodStartEdit.background = GradientDrawable(
//            GradientDrawable.Orientation.LEFT_RIGHT,
//            intArrayOf(Color.parseColor("#EC4899"), Color.parseColor("#A855F7"))
//        ).apply { shape = GradientDrawable.OVAL }
//    }
    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismiss?.invoke()  // ✅ fires when sheet closes for ANY reason
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }



    companion object { const val TAG = "PeriodStartSheet" }
}