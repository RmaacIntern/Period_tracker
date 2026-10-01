package com.aivigil.periodtracker.homefragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.aivigil.periodtracker.R
import com.aivigil.periodtracker.databinding.FragmentHomeBinding
import com.aivigil.periodtracker.databinding.ItemHomeQuickStatBinding
import com.aivigil.periodtracker.domain.CycleEngine
import com.aivigil.periodtracker.logsymptoms.LogSymptomsFragment
import com.aivigil.periodtracker.notification.NotificationPrefs
import com.aivigil.periodtracker.util.ThemeHelper
import com.aivigil.periodtracker.viewmodel.CycleViewModel
import com.aivigil.periodtracker.viewmodel.CycleViewModelFactory
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private companion object {
        /** Set once we have shown the POST_NOTIFICATIONS prompt, so we never nag. */
        const val KEY_NOTIF_ASKED = "notif_permission_asked"
    }

    private var _binding: FragmentHomeBinding? = null
    private var bindJob: kotlinx.coroutines.Job? = null
    private val binding get() = _binding!!

    private val vm: CycleViewModel by activityViewModels {
        CycleViewModelFactory(requireActivity().application)
    }

    private val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "notificationPermission: granted=$granted")
        val ctx = context ?: return@registerForActivityResult
        if (granted) {
            // Alarms are only armed when the OS will actually deliver them, so a
            // fresh grant has to trigger a reschedule — otherwise the user gets no
            // reminders until something else happens to touch the database.
            vm.onAppForegrounded()
        } else {
            // FIX: a denial used to be logged and otherwise ignored, while the
            // reminder switches in Profile stayed ON — so the app promised
            // reminders it could never deliver. Reflect reality instead.
            NotificationPrefs.setPeriodEnabled(ctx, false)
            NotificationPrefs.setOvulationEnabled(ctx, false)
            NotificationPrefs.setDailyEnabled(ctx, false)
            NotificationPrefs.syncDailyReminder(ctx)
            _binding?.root?.let { root ->
                com.google.android.material.snackbar.Snackbar.make(
                    root,
                    "Reminders are off. You can turn them on any time in Profile.",
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Asks for POST_NOTIFICATIONS at most once per install, and only with context.
     *
     * FIX: this used to fire `requestNotifPermission.launch(...)` unconditionally
     * from onViewCreated — so the system permission dialog appeared the instant
     * Home first rendered, immediately after the onboarding interstitial ad, before
     * the user had seen a single thing the app does or knew what she would be
     * notified about. That is the classic way to earn a permanent denial. It also
     * re-ran on every tab return, because tab switching recreated the fragment.
     *
     * The ask is now deferred to the first time the user has data worth being
     * reminded about, and a denial is recorded so she is never re-prompted
     * automatically (Android auto-denies after two refusals anyway).
     */
    private fun maybeAskForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val ctx = context ?: return

        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return

        val prefs = ctx.getSharedPreferences("reminder_prefs", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIF_ASKED, false)) {
            Log.d(TAG, "notificationPermission: already asked once — not re-prompting")
            return
        }

        // Only ask once there is a cycle to remind her about.
        if (vm.prediction.value == null) {
            Log.d(TAG, "notificationPermission: deferring — no prediction yet")
            return
        }

        prefs.edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
        Log.d(TAG, "requesting POST_NOTIFICATIONS permission")
        requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        Log.d(TAG, "onViewCreated")

        maybeAskForNotificationPermission()
        applyBackgrounds()
        observeData()
        bindClickListeners()
    }

    // ── Observe ───────────────────────────────────────────────────

    private fun observeData() {
        vm.prediction.observe(viewLifecycleOwner) { pred ->
            Log.d(TAG, "prediction updated: ${pred?.lastPeriodStart} cycleLen=${pred?.cycleLength}")
            tryBind()
            // Now that there is a cycle to remind her about, it is a reasonable
            // moment to ask about notifications (see maybeAskForNotificationPermission).
            maybeAskForNotificationPermission()
        }
        // Recompute date-derived text when the day rolls over.
        vm.today.observe(viewLifecycleOwner) { tryBind() }
        vm.settings.observe(viewLifecycleOwner) { s ->
            Log.d(TAG, "settings updated: user=${s?.userName} cycleLen=${s?.cycleLength}")
            tryBind()
        }

        vm.periodEntries.observe(viewLifecycleOwner) { entries ->
            if (entries.isNullOrEmpty()) {
                Log.w(TAG, "EDGE CASE: periodEntries is empty — onboarding may not have run")
                return@observe
            }
            val latest = entries.maxByOrNull { it.startDate }!!
            Log.d(TAG, "periodEntries: count=${entries.size} latest=${latest.startDate} " +
                    "end=${latest.endDate ?: "open"} cycleLen=${latest.cycleLength ?: "null(first)"}")

            val dates = entries.map { it.startDate }
            if (dates.size != dates.distinct().size)
                Log.e(TAG, "EDGE CASE: duplicate period start dates — $dates")
        }

        vm.todayLog.observe(viewLifecycleOwner) { log ->
            if (log == null) {
                Log.d(TAG, "todayLog: no log yet for today — showing empty state")
                bindQuickStat(ItemHomeQuickStatBinding.bind(binding.statFlow.root),
                    R.drawable.ic_flow, "Flow", "Not logged")
                bindQuickStat(ItemHomeQuickStatBinding.bind(binding.statMood.root),
                    R.drawable.ic_mood, "Mood", "Not logged")
                bindQuickStat(ItemHomeQuickStatBinding.bind(binding.statSymptoms.root),
                    R.drawable.ic_symptoms, "Symptoms", "None noted")
                bindQuickStat(ItemHomeQuickStatBinding.bind(binding.statNotes.root),
                    R.drawable.ic_notes, "BBT", "Not logged")
                binding.tvBasalValue.text    = "Not logged"
                binding.tvFlowValue.text     = "Not logged"
                binding.tvMoodValue.text     = "Not logged"
                binding.tvSymptomsValue.text = "None noted"
                return@observe
            }

            val flowVal = log.flow.takeIf { it.isNotEmpty() } ?: "Not logged"

            val moodPriority = listOf("😢 Sad","😤 Irritable","😰 Anxious","Mixed feelings","😊 Happy","😌 Calm","⚡ Energetic")
            val selectedMoods = log.moods.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val moodVal = if (selectedMoods.isEmpty()) "Not logged"
            else moodPriority.firstOrNull { it in selectedMoods } ?: selectedMoods[0]

            val sympPriority = listOf("Cramps","Headache","Backache","Bloating","Nausea","Fatigue","Tender Breasts","Acne","Cravings")
            val selectedSymp = log.symptoms.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val sympVal = if (selectedSymp.isEmpty()) "None noted"
            else sympPriority.firstOrNull { it in selectedSymp } ?: selectedSymp[0]

            val basalVal = log.basalTemp?.let { "%.1f°".format(it) } ?: "Not logged"

            Log.d(TAG, "todayLog: entry#${log.entryNumber} flow=$flowVal " +
                    "mood=$moodVal symptoms=$sympVal bbt=$basalVal")

            if (log.entryNumber > 3)
                Log.w(TAG, "EDGE CASE: ${log.entryNumber} entries today — user logging multiple times")
            if (flowVal in listOf("Heavy", "Medium", "Light"))
                Log.i(TAG, "NOTE: flow=$flowVal logged today — period confirmation should have been asked")

            bindQuickStat(ItemHomeQuickStatBinding.bind(binding.statFlow.root),
                R.drawable.ic_flow, "Flow", flowVal)
            bindQuickStat(ItemHomeQuickStatBinding.bind(binding.statMood.root),
                R.drawable.ic_mood, "Mood", moodVal)
            bindQuickStat(ItemHomeQuickStatBinding.bind(binding.statSymptoms.root),
                R.drawable.ic_symptoms, "Symptoms", sympVal)
            bindQuickStat(ItemHomeQuickStatBinding.bind(binding.statNotes.root),
                R.drawable.ic_notes, "BBT", basalVal)

            binding.tvBasalValue.text    = basalVal
            binding.tvFlowValue.text     = flowVal
            binding.tvMoodValue.text     = moodVal
            binding.tvSymptomsValue.text = sympVal
        }
    }

    // ── tryBind ───────────────────────────────────────────────────

    private fun tryBind() {
        bindJob?.cancel()
        bindJob = viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(50)
            val pred = vm.prediction.value ?: run {
                Log.w(TAG, "tryBind: prediction not ready yet")
                return@launch
            }
            val s = vm.settings.value ?: run {
                Log.w(TAG, "tryBind: settings not ready yet")
                return@launch
            }
            val today = LocalDate.now()

            val day       = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength)
            val phase     = CycleEngine.phase(day, pred.cycleLength, s.periodDuration)
            val nextP     = pred.nextPeriodDate
            val ovDate    = pred.ovulationDate
            val fertStart = pred.fertileStart
            val fertEnd   = pred.fertileEnd
            val daysToP   = ChronoUnit.DAYS.between(today, nextP).toInt()
            val daysToOv  = ChronoUnit.DAYS.between(today, ovDate).toInt()
            val isFertile = CycleEngine.isFertile(today, fertStart, fertEnd)
            val isLate    = CycleEngine.isPeriodLate(pred.lastPeriodStart, pred.cycleLength)
            val daysLate  = CycleEngine.daysLate(pred.lastPeriodStart, pred.cycleLength)

            Log.d(TAG, "tryBind: day=$day phase=${CycleEngine.phaseName(phase)} " +
                    "nextPeriod=$nextP daysToP=$daysToP ovulation=$ovDate daysToOv=$daysToOv")

            binding.cycleRing.setProgress(
                day            = day,
                totalCycleDays = pred.cycleLength,
                periodDays     = s.periodDuration
            )

            val name = s.userName?.takeIf { it.isNotBlank() } ?: "there"
            binding.tvGreeting.text = "Hi $name 👋"

            binding.tvPeriodCardValue.text = nextP.format(fmt)
            binding.tvPeriodCardSub.text = when {
                isLate       -> "$daysLate day${if (daysLate == 1) "" else "s"} late"
                daysToP == 0 -> "Due today"
                daysToP > 0  -> "in $daysToP days"
                else         -> "Overdue"
            }

            binding.tvFertileCardValue.text = if (isFertile) "Today" else fertStart.format(fmt)
            binding.tvFertileCardSub.text   = if (isFertile) "High chance" else
                "${ChronoUnit.DAYS.between(today, fertStart).toInt().coerceAtLeast(0)}d away"

            binding.tvOvulationCardValue.text = ovDate.format(fmt)
            binding.tvOvulationCardSub.text   = when {
                daysToOv == 0 -> "Peak today"
                daysToOv > 0  -> "in $daysToOv days"
                else          -> "Passed"
            }

            binding.tvLastPeriodDates.text = if (isLate)
                "$daysLate day${if (daysLate == 1) "" else "s"} overdue · ${nextP.format(fmt)}"
            else
                "in $daysToP days · ${nextP.format(fmt)}"
            binding.tvLastPeriodDetail.text = "Based on your ${pred.cycleLength}-day cycle"
            binding.tvNextPeriodDate.text   = "${fertStart.format(fmt)} – ${fertEnd.format(fmt)}"
            binding.tvNextPeriodDetail.text = "Calculated from your ${pred.cycleLength}-day avg"

            Log.d(TAG, "tryBind: UI render complete ✓")
        }
    }

    // ── Backgrounds ───────────────────────────────────────────────

    private fun applyBackgrounds() {
        // ✅ FIX — replaced hardcoded Color.parseColor hex with ThemeHelper
        binding.statCardsRow.background       = ThemeHelper.cardBg(requireContext(), 16f)
        binding.todayTrackingCard.background  = ThemeHelper.basalCardBg(requireContext(), 16f)
        binding.lastPeriodCard.background     = ThemeHelper.insightBannerBg(requireContext(), 14f)
        binding.nextPeriodCard.background     = ThemeHelper.cardBg(requireContext(), 14f)
        binding.tagLoggedPeriod.background    = ThemeHelper.iconCirclePink(requireContext())
            .apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE; cornerRadius = 20f * resources.displayMetrics.density }
        binding.tagEstimatedPeriod.background = ThemeHelper.cardBg(requireContext(), 20f)
        listOf(binding.statFlow, binding.statMood, binding.statSymptoms, binding.statNotes)
            .forEach { it.root.background = ThemeHelper.basalCardBg(requireContext(), 14f) }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun bindQuickStat(b: ItemHomeQuickStatBinding, iconRes: Int, label: String, value: String) {
        b.statIcon.setImageResource(iconRes)
        b.statLabel.text = label
        b.statValue.text = value
    }

    private fun bindClickListeners() {
        binding.btnLogPeriod.setOnClickListener {
            Log.i(TAG, "btnLogPeriod clicked — opening quick log (flow section)")
            openQuickLog("flow")
        }
        binding.statFlow.root.setOnClickListener     { openQuickLog("flow") }
        binding.statMood.root.setOnClickListener     { openQuickLog("mood") }
        binding.statSymptoms.root.setOnClickListener { openQuickLog("symptoms") }
        binding.statNotes.root.setOnClickListener    { openQuickLog("notes") }
    }

    private fun openQuickLog(scrollTo: String = "top") {
        Log.d(TAG, "openQuickLog: scrollTo=$scrollTo")
        if (parentFragmentManager.findFragmentByTag("logSymptoms") != null) {
            Log.w(TAG, "openQuickLog: already open — skipping")
            return
        }
        val fragment = LogSymptomsFragment().apply {
            arguments = Bundle().apply {
                putString(LogSymptomsFragment.ARG_SCROLL_TO, scrollTo)
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.mainFragmentContainer, fragment)
            .addToBackStack("logSymptoms")
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView")
    }
}