package com.example.periodtracker.history

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.periodtracker.R
import com.example.periodtracker.data.entity.DailyLog
import com.example.periodtracker.databinding.FragmentPastLogHistoryBinding
import com.example.periodtracker.domain.CycleEngine
import com.example.periodtracker.logsymptoms.LogSymptomsFragment
import com.example.periodtracker.viewmodel.CycleViewModel
import com.example.periodtracker.viewmodel.CycleViewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * PastLogHistoryFragment
 *
 * Shows all daily logs grouped by date, newest first.
 * Each day row has:
 *   - date label + cycle-day badge
 *   - entry count
 *   - "Edit" button → opens LogSymptomsFragment for that specific date
 *   - per-entry rows with summary + 🗑 delete button
 *
 * No LiveData observers re-fill UI while user is viewing — list refreshes
 * only on fragment resume or after a delete action completes.
 */
class PastLogHistoryFragment : Fragment() {

    private var _binding: FragmentPastLogHistoryBinding? = null
    private val binding get() = _binding!!

    private val vm: CycleViewModel by activityViewModels {
        CycleViewModelFactory(requireActivity().application)
    }

    private val adapter = DayGroupAdapter(
        onEdit   = { date, entryId -> openEditLog(date, entryId) },
        onDelete = { log -> confirmDelete(log) }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPastLogHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        binding.recyclerLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLogs.adapter = adapter

        observeLogs()
    }

    private fun observeLogs() {
        vm.allLogs.observe(viewLifecycleOwner) { logs ->
            if (logs.isNullOrEmpty()) {
                binding.emptyState.visibility  = View.VISIBLE
                binding.recyclerLogs.visibility = View.GONE
                binding.tvTotalDays.text    = "0 days logged"
                binding.tvTotalEntries.text = "0 entries"
                return@observe
            }

            binding.emptyState.visibility  = View.GONE
            binding.recyclerLogs.visibility = View.VISIBLE

            val uniqueDays = logs.map { it.date }.distinct().size
            binding.tvTotalDays.text    = "$uniqueDays day${if (uniqueDays == 1) "" else "s"} logged"
            binding.tvTotalEntries.text = "${logs.size} entr${if (logs.size == 1) "y" else "ies"}"

            // Group by date, newest first; within each date sort by entryNumber ascending
            val grouped: List<DayGroup> = logs
                .groupBy { it.date }
                .entries
                .sortedByDescending { it.key }
                .map { (date, entries) ->
                    DayGroup(date, entries.sortedBy { it.entryNumber })
                }

            val settings = vm.settings.value
            adapter.submitList(grouped, settings?.lastPeriodStart, settings?.cycleLength ?: 28)
        }
    }

    // ── Navigation ────────────────────────────────────────────────

    private fun openEditLog(dateStr: String, entryId: Int? = null) {
        val fragment = LogSymptomsFragment().apply {
            arguments = Bundle().apply {
                putString(LogSymptomsFragment.ARG_DATE, dateStr)
                if (entryId != null) {
                    putInt(LogSymptomsFragment.ARG_ENTRY_ID, entryId)
                }
                putString(LogSymptomsFragment.ARG_SCROLL_TO, "top")
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.mainFragmentContainer, fragment)
            .addToBackStack("editLog")
            .commit()
    }

    private fun confirmDelete(log: DailyLog) {
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        val timeStr = if (log.loggedAt > 0) {
            Instant.ofEpochMilli(log.loggedAt)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(timeFmt)
        } else "entry #${log.entryNumber}"

        AlertDialog.Builder(requireContext())
            .setTitle("Delete entry?")
            .setMessage("Delete the log from $timeStr on ${log.date}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                vm.deleteDailyLogById(log.entryId)
                Toast.makeText(requireContext(), "Entry deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────
    // DATA MODELS
    // ─────────────────────────────────────────────────────────────

    data class DayGroup(val date: String, val entries: List<DailyLog>)

    // ─────────────────────────────────────────────────────────────
    // ADAPTER
    // ─────────────────────────────────────────────────────────────

    class DayGroupAdapter(
        private val onEdit: (String, Int?) -> Unit,
        private val onDelete: (DailyLog) -> Unit
    ) : RecyclerView.Adapter<DayGroupAdapter.DayVH>() {

        private var items: List<DayGroup> = emptyList()
        private var lastPeriodStart: String? = null
        private var cycleLength: Int = 28

        fun submitList(list: List<DayGroup>, lastPeriodStart: String?, cycleLength: Int) {
            this.items           = list
            this.lastPeriodStart = lastPeriodStart
            this.cycleLength     = cycleLength
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_past_log_day, parent, false)
            return DayVH(view)
        }

        override fun onBindViewHolder(holder: DayVH, position: Int) {
            holder.bind(items[position], lastPeriodStart, cycleLength, onEdit, onDelete)
        }

        class DayVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

            private val tvDate      = itemView.findViewById<TextView>(R.id.tvDayDate)
            private val tvCycleDay  = itemView.findViewById<TextView>(R.id.tvDayCycleDay)
            private val tvCount     = itemView.findViewById<TextView>(R.id.tvEntryCount)
            private val btnEdit     = itemView.findViewById<TextView>(R.id.btnEditDay)
            private val cardEntries = itemView.findViewById<LinearLayout>(R.id.cardEntries)

            fun bind(
                group: DayGroup,
                lastPeriodStart: String?,
                cycleLength: Int,
                onEdit: (String, Int?) -> Unit,
                onDelete: (DailyLog) -> Unit
            ) {
                val date    = LocalDate.parse(group.date)
                val today   = LocalDate.now()
                val dayDiff = ChronoUnit.DAYS.between(date, today)

                val dateLabel = when (dayDiff) {
                    0L   -> "TODAY"
                    1L   -> "YESTERDAY"
                    else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())).uppercase()
                }
                val yearLabel = if (date.year != today.year) ", ${date.year}" else ""
                tvDate.text = "$dateLabel$yearLabel"

                // Cycle day badge
                if (lastPeriodStart != null) {
                    val lps = LocalDate.parse(lastPeriodStart)
                    val day = (ChronoUnit.DAYS.between(lps, date).toInt() + 1).coerceAtLeast(1)
                    tvCycleDay.text = "Day $day"
                    tvCycleDay.visibility = View.VISIBLE
                } else {
                    tvCycleDay.visibility = View.GONE
                }

                val count = group.entries.size
                tvCount.text = "$count entr${if (count == 1) "y" else "ies"}"

                // Card background
                cardEntries.background = GradientDrawable().apply {
                    shape        = GradientDrawable.RECTANGLE
                    cornerRadius = 12f * itemView.resources.displayMetrics.density
                    setColor(Color.parseColor("#FDF9FE"))
                    setStroke(
                        (1f * itemView.resources.displayMetrics.density).toInt(),
                        Color.parseColor("#F0E9F7")
                    )
                }

                // Header "+ Add Log" / Edit button → creates a NEW log for this date
                btnEdit.setOnClickListener { onEdit(group.date, null) }

                // Build entry rows inside card
                cardEntries.removeAllViews()
                group.entries.forEachIndexed { idx, log ->
                    val row = LayoutInflater.from(itemView.context)
                        .inflate(R.layout.item_log_entry_row, cardEntries, false)

                    // Time
                    val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                    val timeStr = if (log.loggedAt > 0L) {
                        Instant.ofEpochMilli(log.loggedAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalTime()
                            .format(timeFmt)
                    } else {
                        "Entry #${log.entryNumber}"
                    }
                    row.findViewById<TextView>(R.id.tvEntryTime).text = timeStr

                    // Type label (morning/midday/evening/night)
                    row.findViewById<TextView>(R.id.tvEntryType).text = entryLabel(log.loggedAt, log.entryNumber)

                    // Summary text
                    val parts = mutableListOf<String>()
                    if (log.flow.isNotEmpty() && log.flow != "None") parts.add(log.flow)
                    log.moods.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { parts.add(it) }
                    log.symptoms.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { parts.add(it) }
                    if (log.cervicalFluid.isNotEmpty()) parts.add(log.cervicalFluid)
                    if (log.lhTestResult != "Not Tested" && log.lhTestResult.isNotEmpty()) parts.add("LH: ${log.lhTestResult}")
                    row.findViewById<TextView>(R.id.tvEntrySummary).text =
                        if (parts.isEmpty()) "No data logged" else parts.joinToString("  ·  ")

                    // BBT row
                    val bbtRow = row.findViewById<View>(R.id.bbtRow)
                    val tvBbt  = row.findViewById<TextView>(R.id.tvBbt)
                    if (log.basalTemp != null) {
                        tvBbt.text = "${"%.1f".format(log.basalTemp)}°F"
                        bbtRow.visibility = View.VISIBLE
                    } else {
                        bbtRow.visibility = View.GONE
                    }

                    // Notes
                    val tvNotes = row.findViewById<TextView>(R.id.tvNotes)
                    if (log.notes.isNotEmpty()) {
                        tvNotes.text = "\"${log.notes}\""
                        tvNotes.visibility = View.VISIBLE
                    } else {
                        tvNotes.visibility = View.GONE
                    }

                    // Divider between entries (not after last)
                    row.findViewById<View>(R.id.dividerEntry).visibility =
                        if (idx < group.entries.size - 1) View.VISIBLE else View.GONE

                    // Click entry row -> edit THIS specific entry
                    row.setOnClickListener { onEdit(log.date, log.entryId) }

                    // Delete entry
                    row.findViewById<TextView>(R.id.btnDeleteEntry).setOnClickListener { onDelete(log) }

                    cardEntries.addView(row)
                }
            }

            private fun entryLabel(epochMillis: Long, entryNumber: Int): String {
                if (epochMillis == 0L) return when (entryNumber) {
                    1 -> "Morning Log"; 2 -> "Midday Log"; else -> "Evening Log"
                }
                val hour = Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneId.systemDefault()).toLocalTime().hour
                return when (hour) {
                    in 5..11  -> "Morning Log"
                    in 12..16 -> "Midday Log"
                    in 17..20 -> "Evening Log"
                    else      -> "Night Log"
                }
            }
        }
    }

    companion object {
        fun newInstance() = PastLogHistoryFragment()
    }
}