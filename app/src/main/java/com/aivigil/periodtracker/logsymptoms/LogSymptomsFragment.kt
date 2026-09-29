package com.aivigil.periodtracker.logsymptoms

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.aivigil.periodtracker.databinding.FragmentLogSymptomsBinding
import com.aivigil.periodtracker.domain.CycleEngine
import com.aivigil.periodtracker.dialog.PeriodStartConfirmationDialog
import com.aivigil.periodtracker.dialog.PeriodStartResult
import com.aivigil.periodtracker.viewmodel.CycleViewModel
import com.aivigil.periodtracker.viewmodel.CycleViewModelFactory
import android.util.Log
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class LogSymptomsFragment : Fragment() {

    companion object {
        const val ARG_DATE      = "date"
        const val ARG_ENTRY_ID  = "entryId"
        const val ARG_SCROLL_TO = "scrollTo"
    }

    private var _binding: FragmentLogSymptomsBinding? = null
    private val binding get() = _binding!!

    private val vm: CycleViewModel by activityViewModels {
        CycleViewModelFactory(requireActivity().application)
    }

    private val TAG = "LogSymptomsFragment"

    // ── Arguments ─────────────────────────────────────────────────
    private val targetDate: LocalDate by lazy {
        arguments?.getString(ARG_DATE)?.let { LocalDate.parse(it) } ?: LocalDate.now()
    }
    private val targetEntryId: Int by lazy {
        arguments?.getInt(ARG_ENTRY_ID, -1) ?: -1
    }
    private val scrollTo: String by lazy {
        arguments?.getString(ARG_SCROLL_TO) ?: "top"
    }
    private val isToday: Boolean get() = targetDate == LocalDate.now()
    private val isPastDate: Boolean get() = targetDate.isBefore(LocalDate.now())

    // ── State ─────────────────────────────────────────────────────
    private var basalTemp = 97.8f
    private var basalTempChanged = false
    private var editingEntryId: Int? = null

    // ✅ Guard against double save
    private var isSaving = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogSymptomsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyBackgrounds()
        bindTopBar()
        bindCycleBanner()
        bindChipListeners()
        bindBasalTemp()
        bindClickListeners()
        loadExistingLogOnce()
    }

    // ── Backgrounds ───────────────────────────────────────────────

    private fun applyBackgrounds() {
        binding.cycleBanner.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#EC4899"), Color.parseColor("#A855F7"))
        ).apply { cornerRadius = 16f * resources.displayMetrics.density }

        binding.basalTempCard.background = roundedBg("#FDF0F5", 14f)
        binding.infoBanner.background    = roundedBg("#F3EEFF", 12f)
    }

    // ── Top bar ───────────────────────────────────────────────────

    private fun bindTopBar() {
        val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        binding.tvDatePill.text = when {
            isToday    -> "Today · ${targetDate.format(dateFmt)}"
            isPastDate -> "Editing · ${targetDate.format(dateFmt)}"
            else       -> targetDate.format(dateFmt)
        }
    }

    // ── Cycle banner ──────────────────────────────────────────────

    private fun bindCycleBanner() {
        vm.settings.observe(viewLifecycleOwner) { s ->
            s ?: return@observe
            val lastPeriod = LocalDate.parse(s.lastPeriodStart)
            val dayNum = (java.time.temporal.ChronoUnit.DAYS
                .between(lastPeriod, targetDate).toInt() + 1).coerceAtLeast(1)
            val phase = CycleEngine.phase(dayNum, s.cycleLength, s.periodDuration)
            binding.tvBannerCycleDay.text = "CYCLE DAY · DAY $dayNum"
            binding.tvBannerPhase.text    = "${CycleEngine.phaseName(phase)} · ${CycleEngine.phaseDescription(phase)}"
        }
    }

    // ── One-shot load ─────────────────────────────────────────────

    private fun loadExistingLogOnce() {
        viewLifecycleOwner.lifecycleScope.launch {
            val logs = vm.getLogsForDate(targetDate)
            if (logs.isEmpty()) {
                Log.d(TAG, "loadExistingLogOnce: no log for $targetDate — blank form")
                return@launch
            }

            val log = if (targetEntryId != -1) {
                editingEntryId = targetEntryId
                logs.firstOrNull { it.entryId == targetEntryId }
            } else {
                editingEntryId = null
                null
            }

            log ?: return@launch
            Log.d(TAG, "loadExistingLogOnce: pre-filling entryId=${log.entryId} for $targetDate")

            // Flow
            when (log.flow) {
                "None"     -> binding.chipFlowNone.isChecked     = true
                "Spotting" -> binding.chipFlowSpotting.isChecked = true
                "Light"    -> binding.chipFlowLight.isChecked    = true
                "Medium"   -> binding.chipFlowMedium.isChecked   = true
                "Heavy"    -> binding.chipFlowHeavy.isChecked    = true
            }

            // Moods
            val moodMap = mapOf(
                "😊 Happy"       to binding.chipMoodHappy,
                "😌 Calm"        to binding.chipMoodCalm,
                "⚡ Energetic"   to binding.chipMoodEnergetic,
                "😰 Anxious"     to binding.chipMoodAnxious,
                "😤 Irritable"   to binding.chipMoodIrritable,
                "😢 Sad"         to binding.chipMoodSad,
                "Mixed feelings" to binding.chipMoodMixed
            )
            log.moods.split(",").map { it.trim() }.forEach { moodMap[it]?.isChecked = true }

            // Symptoms
            val sympMap = mapOf(
                "Cramps"         to binding.chipSympCramps,
                "Headache"       to binding.chipSympHeadache,
                "Bloating"       to binding.chipSympBloating,
                "Fatigue"        to binding.chipSympFatigue,
                "Tender Breasts" to binding.chipSympTenderBreasts,
                "Backache"       to binding.chipSympBackache,
                "Acne"           to binding.chipSympAcne,
                "Cravings"       to binding.chipSympCravings,
                "Nausea"         to binding.chipSympNausea
            )
            log.symptoms.split(",").map { it.trim() }.forEach { sympMap[it]?.isChecked = true }

            // Cervical
            val cervMap = mapOf(
                "Dry"               to binding.chipCervDry,
                "Sticky"            to binding.chipCervSticky,
                "Creamy"            to binding.chipCervCreamy,
                "Watery"            to binding.chipCervWatery,
                "Egg White Fertile" to binding.chipCervEggWhite
            )
            cervMap[log.cervicalFluid]?.isChecked = true

            // LH
            try {
                when (log.lhTestResult) {
                    "Positive" -> binding.chipLhPositive.isChecked  = true
                    "Negative" -> binding.chipLhNegative.isChecked  = true
                    else       -> binding.chipLhNotTested.isChecked = true
                }
            } catch (_: Exception) {}

            // BBT
            log.basalTemp?.let {
                basalTemp = it
                basalTempChanged = false
                updateTempDisplay()
            }

            // Notes
            if (log.notes.isNotEmpty()) binding.etNotes.setText(log.notes)

            scrollToSection()
        }
    }

    // ── Chip listeners ────────────────────────────────────────────

    private fun bindChipListeners() {
        binding.chipGroupFlow.setOnCheckedStateChangeListener { group, _ ->
            binding.tvFlowCount.text = "${group.checkedChipIds.size} selected"
        }
        binding.chipGroupMood.setOnCheckedStateChangeListener { group, _ ->
            binding.tvMoodCount.text = "${group.checkedChipIds.size} selected"
        }
        binding.chipGroupSymptoms.setOnCheckedStateChangeListener { group, _ ->
            binding.tvSymptomsCount.text = "${group.checkedChipIds.size} selected"
        }
    }

    // ── Basal temp ────────────────────────────────────────────────

    private fun bindBasalTemp() {
        updateTempDisplay()
        binding.btnTempDown.setOnClickListener {
            basalTemp = (basalTemp - 0.1f).coerceAtLeast(95.0f)
            basalTempChanged = true
            updateTempDisplay()
        }
        binding.btnTempUp.setOnClickListener {
            basalTemp = (basalTemp + 0.1f).coerceAtMost(104.0f)
            basalTempChanged = true
            updateTempDisplay()
        }
    }

    private fun updateTempDisplay() {
        binding.tvBasalTemp.text = "%.1f°F".format(basalTemp)
    }

    // ── Scroll ────────────────────────────────────────────────────

    private fun scrollToSection() {
        if (scrollTo == "top") return
        binding.root.post {
            val target: View? = when (scrollTo) {
                "flow"     -> binding.sectionFlow
                "mood"     -> binding.sectionMood
                "symptoms" -> binding.sectionSymptoms
                "notes"    -> binding.sectionNotes
                else       -> null
            }
            target?.let { binding.root.smoothScrollTo(0, it.top) }
        }
    }

    // ── Click listeners ───────────────────────────────────────────

    private fun bindClickListeners() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        // ✅ Single shared listener — both buttons do the same thing
        val saveClick = View.OnClickListener {
            if (isSaving) {
                Log.w(TAG, "bindClickListeners: save already in progress — ignoring tap")
                return@OnClickListener
            }
            // ✅ Disable both buttons immediately to prevent double tap
            binding.btnSave.isEnabled       = false
            binding.btnSaveDailyLog.isEnabled = false
            binding.btnSave.alpha           = 0.5f
            binding.btnSaveDailyLog.alpha   = 0.5f
            initiateSave()
        }

        binding.btnSave.setOnClickListener(saveClick)
        binding.btnSaveDailyLog.setOnClickListener(saveClick)
    }

    // ── Save flow ─────────────────────────────────────────────────

    private fun initiateSave() {
        // ✅ Double guard — catches any path that bypasses the click listener
        if (isSaving) {
            Log.w(TAG, "initiateSave: already saving — ignoring duplicate call")
            return
        }
        isSaving = true

        val flow     = collectFlow()
        val moods    = collectMoods()
        val symptoms = collectSymptoms()
        val cervical = collectCervical()
        val lh       = collectLhResult()
        val bbt      = if (basalTempChanged) basalTemp else null
        val notes    = binding.etNotes.text.toString().trim()

        Log.d(TAG, "initiateSave: date=$targetDate isPast=$isPastDate " +
                "editId=$editingEntryId flow=$flow moods=$moods symptoms=$symptoms")

        val shouldAsk = CycleEngine.shouldStartNewCycle(flow)
        Log.d(TAG, "initiateSave: shouldAsk=$shouldAsk flow='$flow'")

        if (shouldAsk) {
            Log.i(TAG, "initiateSave: showing period start dialog")
            PeriodStartConfirmationDialog.show(childFragmentManager) { result ->
                Log.i(TAG, "initiateSave: dialog result=$result")
                viewLifecycleOwner.lifecycleScope.launch {
                    val confirmed = result == PeriodStartResult.PERIOD_STARTED
                    doSave(flow, moods, symptoms, cervical, lh, bbt, notes,
                        periodConfirmed = confirmed)
                }
            }
        } else {
            Log.i(TAG, "initiateSave: skipping dialog — flow='$flow' not period-starting")
            doSave(flow, moods, symptoms, cervical, lh, bbt, notes,
                periodConfirmed = false)
        }
    }

    private fun doSave(
        flow: String, moods: List<String>, symptoms: List<String>,
        cervical: String, lh: String, bbt: Float?, notes: String,
        periodConfirmed: Boolean
    ) {
        val existingId = editingEntryId

        if (existingId != null) {
            // ── EDIT MODE ─────────────────────────────────────────
            Log.i(TAG, "doSave: UPDATE entryId=$existingId date=$targetDate " +
                    "confirmed=$periodConfirmed")
            viewLifecycleOwner.lifecycleScope.launch {
                val existing = vm.getLogsForDate(targetDate)
                    .firstOrNull { it.entryId == existingId }
                if (existing == null) {
                    Log.e(TAG, "doSave: entryId=$existingId not found — aborting")
                    resetSaveButtons()
                    return@launch
                }
                vm.updateDailyLog(
                    existing.copy(
                        flow          = flow,
                        moods         = moods.joinToString(","),
                        symptoms      = symptoms.joinToString(","),
                        cervicalFluid = cervical,
                        basalTemp     = bbt ?: existing.basalTemp,
                        lhTestResult  = lh,
                        notes         = notes,
                        loggedAt      = System.currentTimeMillis()
                    )
                )
                if (periodConfirmed) {
                    Log.i(TAG, "doSave: UPDATE + periodConfirmed → logPeriodStart($targetDate)")
                    vm.logPeriodStart(targetDate)
                }
                if (!isAdded) return@launch
                val ctx = context ?: return@launch
                Toast.makeText(ctx,
                    if (periodConfirmed) "Period logged ✓" else "Log updated ✓",
                    Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        } else {
            // ── NEW MODE ──────────────────────────────────────────
            Log.i(TAG, "doSave: INSERT date=$targetDate flow=$flow " +
                    "confirmed=$periodConfirmed")
            viewLifecycleOwner.lifecycleScope.launch {
                vm.saveDailyLog(
                    date            = targetDate,
                    flow            = flow,
                    moods           = moods,
                    symptoms        = symptoms,
                    cervicalFluid   = cervical,
                    basalTemp       = bbt,
                    lhTestResult    = lh,
                    notes           = notes,
                    periodConfirmed = periodConfirmed
                )
                if (!isAdded) return@launch
                val ctx = context ?: return@launch
                Toast.makeText(ctx,
                    if (periodConfirmed) "Period logged ✓" else "Log saved ✓",
                    Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    // ✅ Re-enable buttons if save fails or is aborted
    private fun resetSaveButtons() {
        isSaving = false
        _binding?.let {
            it.btnSave.isEnabled        = true
            it.btnSaveDailyLog.isEnabled  = true
            it.btnSave.alpha            = 1.0f
            it.btnSaveDailyLog.alpha    = 1.0f
        }
    }

    // ── Collectors ────────────────────────────────────────────────

    private fun collectFlow() = when (binding.chipGroupFlow.checkedChipId) {
        binding.chipFlowNone.id     -> "None"
        binding.chipFlowSpotting.id -> "Spotting"
        binding.chipFlowLight.id    -> "Light"
        binding.chipFlowMedium.id   -> "Medium"
        binding.chipFlowHeavy.id    -> "Heavy"
        else                        -> ""
    }

    private fun collectMoods() = listOf(
        binding.chipMoodHappy     to "😊 Happy",
        binding.chipMoodCalm      to "😌 Calm",
        binding.chipMoodEnergetic to "⚡ Energetic",
        binding.chipMoodAnxious   to "😰 Anxious",
        binding.chipMoodIrritable to "😤 Irritable",
        binding.chipMoodSad       to "😢 Sad",
        binding.chipMoodMixed     to "Mixed feelings"
    ).filter { (chip, _) -> chip.isChecked }.map { (_, label) -> label }

    private fun collectSymptoms() = listOf(
        binding.chipSympCramps        to "Cramps",
        binding.chipSympHeadache      to "Headache",
        binding.chipSympBloating      to "Bloating",
        binding.chipSympFatigue       to "Fatigue",
        binding.chipSympTenderBreasts to "Tender Breasts",
        binding.chipSympBackache      to "Backache",
        binding.chipSympAcne          to "Acne",
        binding.chipSympCravings      to "Cravings",
        binding.chipSympNausea        to "Nausea"
    ).filter { (chip, _) -> chip.isChecked }.map { (_, label) -> label }

    private fun collectCervical() = when (binding.chipGroupCervical.checkedChipId) {
        binding.chipCervDry.id      -> "Dry"
        binding.chipCervSticky.id   -> "Sticky"
        binding.chipCervCreamy.id   -> "Creamy"
        binding.chipCervWatery.id   -> "Watery"
        binding.chipCervEggWhite.id -> "Egg White Fertile"
        else                        -> ""
    }

    private fun collectLhResult() = try {
        when {
            binding.chipLhPositive.isChecked -> "Positive"
            binding.chipLhNegative.isChecked -> "Negative"
            else                             -> "Not Tested"
        }
    } catch (_: Exception) { "Not Tested" }

    // ── Helpers ───────────────────────────────────────────────────

    private fun roundedBg(colorHex: String, radiusDp: Float) = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(Color.parseColor(colorHex))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}