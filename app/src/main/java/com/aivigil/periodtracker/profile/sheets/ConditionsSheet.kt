package com.aivigil.periodtracker.profile.sheets

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.aivigil.periodtracker.data.entity.DailyLog
import com.aivigil.periodtracker.databinding.BottomSheetConditionsBinding

class ConditionsSheet(
    private val logs: List<DailyLog>,
    private val onboardingConditions: String = ""   // comma-separated from settings
) : BottomSheetDialogFragment() {

    private var _b: BottomSheetConditionsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = BottomSheetConditionsBinding.inflate(inflater, container, false)
        .also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── Onboarding conditions chips ──────────────────────────────────────
        val conditions = onboardingConditions
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && it.lowercase() != "none" }

        if (conditions.isNotEmpty()) {
            b.layoutOnboardingConditions.visibility = View.VISIBLE
            conditions.forEach { label ->
                val chip = Chip(requireContext()).apply {
                    text = label
                    isClickable = false
                    isCheckable = false
                    chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FCE4EC"))
                    setTextColor(Color.parseColor("#E63A5E"))
                    textSize = 12f
                    chipStrokeWidth = 0f
                }
                b.conditionsChipGroup.addView(chip)
            }
        } else {
            b.layoutOnboardingConditions.visibility = View.GONE
        }

        // ── Logged symptoms ──────────────────────────────────────────────────
        if (logs.isEmpty()) {
            b.layoutNoLogs.visibility   = View.VISIBLE
            b.layoutSymptoms.visibility = View.GONE
            return
        }

        val counts = mutableMapOf<String, Int>()
        logs.forEach { log ->
            log.symptoms
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }

        val total = logs.size.coerceAtLeast(1)

        b.layoutNoLogs.visibility   = View.GONE
        b.layoutSymptoms.visibility = View.VISIBLE

        fun bind(count: Int, bar: ProgressBar, tv: TextView) {
            bar.progress = ((count.toFloat() / total) * 100).toInt()
            tv.text      = "${count}x"
        }

        bind(counts["Cramps"]         ?: 0, b.barCramps,        b.tvCrampsCount)
        bind(counts["Headache"]       ?: 0, b.barHeadache,      b.tvHeadacheCount)
        bind(counts["Bloating"]       ?: 0, b.barBloating,      b.tvBloatingCount)
        bind(counts["Fatigue"]        ?: 0, b.barFatigue,       b.tvFatigueCount)
        bind(counts["Tender Breasts"] ?: 0, b.barTenderBreasts, b.tvTenderBreastsCount)
        bind(counts["Backache"]       ?: 0, b.barBackache,      b.tvBackacheCount)
        bind(counts["Acne"]           ?: 0, b.barAcne,          b.tvAcneCount)
        bind(counts["Nausea"]         ?: 0, b.barNausea,        b.tvNauseaCount)
        bind(counts["Cravings"]       ?: 0, b.barCravings,      b.tvCravingsCount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        const val TAG = "ConditionsSheet"
    }
}