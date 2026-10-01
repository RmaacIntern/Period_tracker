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
import com.aivigil.periodtracker.ads.ShowAds
import com.aivigil.periodtracker.databinding.FragmentLogSymptomsBinding
import com.aivigil.periodtracker.domain.CycleEngine
import com.aivigil.periodtracker.dialog.PeriodStartConfirmationDialog
import com.aivigil.periodtracker.dialog.PeriodStartResult
import com.aivigil.periodtracker.util.ThemeHelper
import com.aivigil.periodtracker.viewmodel.CycleViewModel
import com.aivigil.periodtracker.viewmodel.CycleViewModelFactory
import android.util.Log
import com.aivigil.periodtracker.ads.NativeAdHelper
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
    private var nativeAdHelper: NativeAdHelper? = null

    private val TAG = "LogSymptomsFragment"

    /**
     * The date this log will be written to.
     *
     * FIX: when no explicit date argument was supplied this defaulted to a `by
     * lazy` LocalDate.now(), while isToday / isPastDate compared against a LIVE
     * LocalDate.now(). Opening the screen at 23:58 and saving at 00:01 flipped the
     * header to "Editing · <yesterday>" and wrote the entry to yesterday's date.
     *
     * When the caller passes no date the screen means "today", so `today` is
     * re-read at save time rather than frozen at construction. An explicitly
     * passed date is honoured exactly and never drifts.
     */
    private val explicitDate: LocalDate? by lazy {
        arguments?.getString(ARG_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    private val targetDate: LocalDate get() = explicitDate ?: LocalDate.now()

    private val targetEntryId: Int by lazy {
        arguments?.getInt(ARG_ENTRY_ID, -1) ?: -1
    }
    private val scrollTo: String by lazy {
        arguments?.getString(ARG_SCROLL_TO) ?: "top"
    }
    private val isToday: Boolean get() = targetDate == LocalDate.now()
    private val isPastDate: Boolean get() = targetDate.isBefore(LocalDate.now())

    private var basalTemp = 98.0f
    private var basalTempChanged = false
    private var editingEntryId: Int? = null
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

        nativeAdHelper = NativeAdHelper(requireContext())
        nativeAdHelper?.loadInto(binding.nativeAdContainer)

        listenForPeriodConfirmation()
    }

    private fun applyBackgrounds() {
        binding.cycleBanner.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#EC4899"), Color.parseColor("#A855F7"))
        ).apply { cornerRadius = 16f * resources.displayMetrics.density }

        binding.basalTempCard.background = ThemeHelper.basalCardBg(requireContext(), 14f)
        binding.infoBanner.background    = ThemeHelper.insightBannerBg(requireContext(), 12f)
    }


    private fun bindTopBar() {
        val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        binding.tvDatePill.text = when {
            isToday    -> "Today · ${targetDate.format(dateFmt)}"
            isPastDate -> "Editing · ${targetDate.format(dateFmt)}"
            else       -> targetDate.format(dateFmt)
        }
    }


    private fun bindCycleBanner() {
        vm.prediction.observe(viewLifecycleOwner) { pred ->
            pred ?: return@observe
            val s = vm.settings.value ?: return@observe
            val dayNum = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength, targetDate)
            val phase  = CycleEngine.phase(dayNum, pred.cycleLength, s.periodDuration)
            binding.tvBannerCycleDay.text = "CYCLE DAY · DAY $dayNum"
            binding.tvBannerPhase.text    =
                "${CycleEngine.phaseName(phase)} · ${CycleEngine.phaseDescription(phase)}"
        }
        vm.settings.observe(viewLifecycleOwner) { s ->
            s ?: return@observe
            val pred = vm.prediction.value ?: return@observe
            val dayNum = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength, targetDate)
            val phase  = CycleEngine.phase(dayNum, pred.cycleLength, s.periodDuration)
            binding.tvBannerCycleDay.text = "CYCLE DAY · DAY $dayNum"
            binding.tvBannerPhase.text    =
                "${CycleEngine.phaseName(phase)} · ${CycleEngine.phaseDescription(phase)}"
        }
    }


    private fun loadExistingLogOnce() {
        viewLifecycleOwner.lifecycleScope.launch {
            val logs = vm.getLogsForDate(targetDate)
            if (logs.isEmpty()) return@launch

            val log = if (targetEntryId != -1) {
                editingEntryId = targetEntryId
                logs.firstOrNull { it.entryId == targetEntryId }
            } else {
                editingEntryId = null
                null
            }
            log ?: return@launch

            when (log.flow) {
                "None"     -> binding.chipFlowNone.isChecked     = true
                "Spotting" -> binding.chipFlowSpotting.isChecked = true
                "Light"    -> binding.chipFlowLight.isChecked    = true
                "Medium"   -> binding.chipFlowMedium.isChecked   = true
                "Heavy"    -> binding.chipFlowHeavy.isChecked    = true
            }

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

            val cervMap = mapOf(
                "Dry"               to binding.chipCervDry,
                "Sticky"            to binding.chipCervSticky,
                "Creamy"            to binding.chipCervCreamy,
                "Watery"            to binding.chipCervWatery,
                "Egg White Fertile" to binding.chipCervEggWhite
            )
            cervMap[log.cervicalFluid]?.isChecked = true

            try {
                when (log.lhTestResult) {
                    "Positive" -> binding.chipLhPositive.isChecked  = true
                    "Negative" -> binding.chipLhNegative.isChecked  = true
                    else       -> binding.chipLhNotTested.isChecked = true
                }
            } catch (_: Exception) {}

            log.basalTemp?.let {
                basalTemp = it
                basalTempChanged = false
                updateTempDisplay()
            }

            if (log.notes.isNotEmpty()) binding.etNotes.setText(log.notes)
            scrollToSection()
        }
    }


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
            target ?: return@post
            binding.root.smoothScrollTo(0, target.top)
        }
    }

    // ── Click listeners ───────────────────────────────────────────

    private fun bindClickListeners() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        val saveClick = View.OnClickListener {
            if (isSaving) return@OnClickListener
            binding.btnSave.isEnabled         = false
            binding.btnSaveDailyLog.isEnabled = false
            binding.btnSave.alpha             = 0.5f
            binding.btnSaveDailyLog.alpha     = 0.5f
            initiateSave()
        }
        binding.btnSave.setOnClickListener(saveClick)
        binding.btnSaveDailyLog.setOnClickListener(saveClick)
    }

    // ── Save flow ─────────────────────────────────────────────────

    private fun initiateSave() {
        if (isSaving) return
        isSaving = true

        val flow     = collectFlow()
        val moods    = collectMoods()
        val symptoms = collectSymptoms()
        val cervical = collectCervical()
        val lh       = collectLhResult()
        val bbt      = if (basalTempChanged) basalTemp else null
        val notes    = binding.etNotes.text.toString().trim()
        val shouldAsk = CycleEngine.shouldStartNewCycle(flow)

        if (shouldAsk) {
            // Stash what we are about to save so the answer can still be acted on
            // after a rotation destroys and recreates this fragment.
            pendingSave = PendingSave(flow, moods, symptoms, cervical, lh, bbt, notes)
            PeriodStartConfirmationDialog.show(
                childFragmentManager,
                onResult  = { /* handled by the fragment-result listener */ },
                onDismiss = { /* handled by the fragment-result listener */ }
            )
        } else {
            doSave(flow, moods, symptoms, cervical, lh, bbt, notes,
                periodConfirmed = false)
        }
    }

    private data class PendingSave(
        val flow: String,
        val moods: List<String>,
        val symptoms: List<String>,
        val cervical: String,
        val lh: String,
        val bbt: Float?,
        val notes: String
    )

    private var pendingSave: PendingSave? = null


    private fun listenForPeriodConfirmation() {
        childFragmentManager.setFragmentResultListener(
            PeriodStartConfirmationDialog.REQUEST_KEY, viewLifecycleOwner
        ) { _, bundle ->
            val raw = bundle.getString(PeriodStartConfirmationDialog.RESULT_KEY)
            val pending = pendingSave
            if (pending == null) {

                resetSaveButtons()
                return@setFragmentResultListener
            }
            if (raw == PeriodStartConfirmationDialog.RESULT_CANCELLED) {
                pendingSave = null
                resetSaveButtons()
                return@setFragmentResultListener
            }
            pendingSave = null
            doSave(
                pending.flow, pending.moods, pending.symptoms, pending.cervical,
                pending.lh, pending.bbt, pending.notes,
                periodConfirmed = raw == PeriodStartResult.PERIOD_STARTED.name
            )
        }
    }

    private fun doSave(
        flow: String, moods: List<String>, symptoms: List<String>,
        cervical: String, lh: String, bbt: Float?, notes: String,
        periodConfirmed: Boolean
    ) {
        val existingId = editingEntryId

        viewLifecycleOwner.lifecycleScope.launch {
            ShowAds.suppressInterstitials = true
            try {
                if (existingId != null) {
                    val existing = vm.getLogsForDate(targetDate)
                        .firstOrNull { it.entryId == existingId }
                    if (existing == null) {
                        resetSaveButtons()
                        showSaveError("That entry no longer exists.")
                        return@launch
                    }
                    vm.updateDailyLogAwait(existing.copy(
                        flow          = flow,
                        moods         = moods.joinToString(","),
                        symptoms      = symptoms.joinToString(","),
                        cervicalFluid = cervical,
                        basalTemp     = bbt ?: existing.basalTemp,
                        lhTestResult  = lh,
                        notes         = notes,
                        loggedAt      = System.currentTimeMillis()
                    ))
                } else {
                    vm.saveDailyLogAwait(
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
                }

                if (periodConfirmed && existingId != null) {
                    vm.logPeriodStartAwait(targetDate)
                }

                isSaving = false
                if (!isAdded) return@launch
                context?.let { ctx ->
                    Toast.makeText(
                        ctx,
                        when {
                            periodConfirmed     -> "Period logged ✓"
                            existingId != null  -> "Log updated ✓"
                            else                -> "Log saved ✓"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
                if (!parentFragmentManager.isStateSaved) {
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "doSave failed", e)
                resetSaveButtons()
                showSaveError("Couldn't save your log. Please try again.")
            } finally {
                ShowAds.suppressInterstitials = false
            }
        }
    }


    private fun showSaveError(message: String) {
        val root = _binding?.root ?: return
        com.google.android.material.snackbar.Snackbar
            .make(root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
            .show()
    }

    private fun resetSaveButtons() {
        isSaving = false
        _binding?.let {
            it.btnSave.isEnabled         = true
            it.btnSaveDailyLog.isEnabled = true
            it.btnSave.alpha             = 1.0f
            it.btnSaveDailyLog.alpha     = 1.0f
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

    override fun onDestroyView() {
        super.onDestroyView()
        nativeAdHelper?.destroy()
        nativeAdHelper = null
        _binding = null
    }
}