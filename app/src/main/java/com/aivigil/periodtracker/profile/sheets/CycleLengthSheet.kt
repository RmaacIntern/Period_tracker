package com.aivigil.periodtracker.profile.sheets

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.aivigil.periodtracker.R
import com.aivigil.periodtracker.databinding.BottomSheetCycleLengthBinding
import com.aivigil.periodtracker.databinding.BottomSheetPeriodDurationBinding
import com.aivigil.periodtracker.databinding.BottomSheetLastPeriodBinding
import com.aivigil.periodtracker.calendar.SheetCalendarView
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom.current

// ─────────────────────────────────────────────────────────────────────────────
// 1. CYCLE LENGTH BOTTOM SHEET  (18 – 45 days)
// ─────────────────────────────────────────────────────────────────────────────

class CycleLengthSheet(
    private val current: Int,
    private val onSave: (Int) -> Unit
) : BottomSheetDialogFragment() {

    private var _b: BottomSheetCycleLengthBinding? = null
    private val b get() = _b!!
    private var selected = current

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        BottomSheetCycleLengthBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.tvCycleLengthSelected.text = "$selected days"

        val items = (18..45).toList()
        val adapter = NumberPickerAdapter(items, selected) { value ->
            selected = value
            b.tvCycleLengthSelected.text = "$value days"
        }
        b.rvCycleLength.layoutManager = LinearLayoutManager(requireContext())
        b.rvCycleLength.adapter = adapter

        // Scroll to current
        val idx = items.indexOf(selected)
        if (idx >= 0) b.rvCycleLength.scrollToPosition(idx)

        b.btnCycleLengthCancel.setOnClickListener { dismiss() }
        b.btnCycleLengthOk.setOnClickListener {
            onSave(selected)
            dismiss()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    companion object { const val TAG = "CycleLengthSheet" }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. PERIOD DURATION BOTTOM SHEET  (2 – 10 days)
// ─────────────────────────────────────────────────────────────────────────────

class PeriodDurationSheet(
    private val current: Int,
    private val onSave: (Int) -> Unit
) : BottomSheetDialogFragment() {

    private var _b: BottomSheetPeriodDurationBinding? = null
    private val b get() = _b!!
    private var selected = current

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        BottomSheetPeriodDurationBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.tvPeriodDurSelected.text = "$selected days"

        val items = (2..10).toList()
        val adapter = NumberPickerAdapter(items, selected) { value ->
            selected = value
            b.tvPeriodDurSelected.text = "$value days"
        }
        b.rvPeriodDuration.layoutManager = LinearLayoutManager(requireContext())
        b.rvPeriodDuration.adapter = adapter

        val idx = items.indexOf(selected)
        if (idx >= 0) b.rvPeriodDuration.scrollToPosition(idx)

        b.btnPeriodDurCancel.setOnClickListener { dismiss() }
        b.btnPeriodDurOk.setOnClickListener {
            onSave(selected)
            dismiss()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    companion object { const val TAG = "PeriodDurationSheet" }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. LAST PERIOD DATE BOTTOM SHEET  (calendar picker)
// ─────────────────────────────────────────────────────────────────────────────

class LastPeriodSheet(
    private val current: LocalDate,
    private val onSave: (LocalDate) -> Unit
) : BottomSheetDialogFragment() {

    private var _b: BottomSheetLastPeriodBinding? = null
    private val b get() = _b!!
    private var selectedDate = current
    private var displayMonth = YearMonth.from(current)

    private val fullFmt  = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
        BottomSheetLastPeriodBinding.inflate(i, c, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        styleEditButton()
        updateHeader()
        updateMonthLabel()

        b.sheetCalendarView.displayMonth = displayMonth
        b.sheetCalendarView.selectedDate = selectedDate
        b.sheetCalendarView.maxDate      = LocalDate.now()   // can't pick future
        b.sheetCalendarView.onDateSelected = { date ->
            selectedDate = date
            updateHeader()
        }

        b.btnCalSheetPrev.setOnClickListener {
            displayMonth = displayMonth.minusMonths(1)
            b.sheetCalendarView.displayMonth = displayMonth
            updateMonthLabel()
        }
        b.btnCalSheetNext.setOnClickListener {
            if (displayMonth < YearMonth.now()) {
                displayMonth = displayMonth.plusMonths(1)
                b.sheetCalendarView.displayMonth = displayMonth
                updateMonthLabel()
            }
        }

        b.btnPeriodDateCancel.setOnClickListener { dismiss() }
        b.btnPeriodDateOk.setOnClickListener {
            onSave(selectedDate)
            dismiss()
        }
    }

    private fun updateHeader() {
        b.tvSelectedPeriodDate.text = selectedDate.format(fullFmt)
    }

    private fun updateMonthLabel() {
        b.tvCalSheetMonthYear.text = displayMonth.format(monthFmt)
    }

//    private fun styleEditButton() {
//        b.btnEditDate.background = GradientDrawable(
//            GradientDrawable.Orientation.LEFT_RIGHT,
//            intArrayOf(Color.parseColor("#EC4899"), Color.parseColor("#A855F7"))
//        ).apply {
//            shape = GradientDrawable.OVAL
//        }
//    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }

    companion object { const val TAG = "LastPeriodSheet" }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. HEALTH CONDITIONS BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

//class ConditionsSheet(    private val current: List<String>,    private val onSave: (List<String>) -> Unit) : BottomSheetDialogFragment() {
//
//    private var _b: BottomSheetCondationsBinding? = null
//    private val b get() = _b!!
//
//    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?) =
//        BottomSheetCondationsBinding.inflate(i, c, false).also { _b = it }.root
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        // Pre-check saved conditions
//        val checks = mapOf(
//            "PCOS"            to b.checkPcos,
//            "Endometriosis"   to b.checkEndometriosis,
//            "Thyroid"         to b.checkThyroid,
//            "Diabetes"        to b.checkDiabetes,
//            "Blood Pressure"  to b.checkBloodPressure,
//            "Anemia"          to b.checkAnemia,
//            "Migraines"       to b.checkMigraines,
//            "Anxiety"         to b.checkAnxiety
//        )
//
//        checks.forEach { (name, cb) ->
//            cb.isChecked = name in current
//        }
//        b.checkNone.isChecked = current.isEmpty()
//        updateCount(checks.values.count { it.isChecked })
//
//        // Row click listeners
//        mapOf(
//            b.rowPcos          to b.checkPcos,
//            b.rowEndometriosis to b.checkEndometriosis,
//            b.rowThyroid       to b.checkThyroid,
//            b.rowDiabetes      to b.checkDiabetes,
//            b.rowBloodPressure to b.checkBloodPressure,
//            b.rowAnemia        to b.checkAnemia,
//            b.rowMigraines     to b.checkMigraines,
//            b.rowAnxiety       to b.checkAnxiety
//        ).forEach { (row, cb) ->
//            row.setOnClickListener {
//                cb.isChecked = !cb.isChecked
//                if (cb.isChecked) b.checkNone.isChecked = false
//                updateCount(checks.values.count { it.isChecked })
//            }
//        }
//
//        b.rowNone.setOnClickListener {
//            b.checkNone.isChecked = !b.checkNone.isChecked
//            if (b.checkNone.isChecked) checks.values.forEach { it.isChecked = false }
//            updateCount(0)
//        }
//
//        b.btnConditionsSave.setOnClickListener {
//            val selected = checks.filter { (_, cb) -> cb.isChecked }.keys.toList()
//            onSave(selected)
//            dismiss()
//        }
//    }
//
//    private fun updateCount(n: Int) {
//        b.tvConditionsCount.text = if (n == 0) "None selected" else "$n selected"
//    }
//
//    override fun onDestroyView() { super.onDestroyView(); _b = null }
//
//    companion object { const val TAG = "ConditionsSheet" }
//}

// ─────────────────────────────────────────────────────────────────────────────
// SHARED: Number Picker RecyclerView Adapter
// ─────────────────────────────────────────────────────────────────────────────

class NumberPickerAdapter(
    private val items: List<Int>,
    private var selected: Int,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<NumberPickerAdapter.VH>() {

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, 56.dpToPx(parent.context)
            )
            textSize = 16f
            gravity  = android.view.Gravity.CENTER
            isClickable = true
            isFocusable = true
        }
        return VH(tv)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val value = items[position]
        val isSelected = value == selected
        holder.tv.text      = "$value days"
        holder.tv.setTextColor(
            if (isSelected) Color.parseColor("#EC4899") else holder.tv.context.getColor(R.color.text_primary)
        )
        holder.tv.textSize  = if (isSelected) 18f else 15f
        holder.tv.alpha     = if (isSelected) 1f else 0.55f
        holder.tv.typeface  = if (isSelected)
            android.graphics.Typeface.DEFAULT_BOLD
        else
            android.graphics.Typeface.DEFAULT

        holder.tv.setOnClickListener {
            val prev = items.indexOf(selected)
            selected = value
            onSelect(value)
            notifyItemChanged(prev)
            notifyItemChanged(position)
        }
    }

    private fun Int.dpToPx(ctx: android.content.Context) =
        (this * ctx.resources.displayMetrics.density).toInt()
}