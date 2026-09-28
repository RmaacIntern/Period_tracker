package com.example.periodtracker.homefragment

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import com.example.periodtracker.R
import com.example.periodtracker.databinding.FragmentHomeBinding
import com.example.periodtracker.databinding.ItemHomeQuickStatBinding
import com.example.periodtracker.domain.CycleEngine
import com.example.periodtracker.logsymptoms.LogSymptomsFragment
import com.example.periodtracker.notification.NotificationHelper
import com.example.periodtracker.viewmodel.CycleViewModel
import com.example.periodtracker.viewmodel.CycleViewModelFactory
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private var _binding: FragmentHomeBinding? = null
    // Add at top of class
    private var bindJob: kotlinx.coroutines.Job? = null
    private val binding get() = _binding!!

    private val vm: CycleViewModel by activityViewModels {
        CycleViewModelFactory(requireActivity().application)
    }

    private val fmt   = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val today = LocalDate.now()
    private var isTesting = false

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "notificationPermission: granted=$granted")
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "requesting POST_NOTIFICATIONS permission")
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        applyBackgrounds()
        observeData()
        bindClickListeners()

        // ✅ TEST — fires all 3 notifications immediately
// Add flag at top of class

// Test alarm fires in 30 seconds — remove after testing
//        binding.btnTestNotifications.setOnClickListener {
//            val am = requireContext().getSystemService(android.content.Context.ALARM_SERVICE)
//                    as android.app.AlarmManager
//
//            val pi = android.app.PendingIntent.getBroadcast(
//                requireContext(), 999,
//                android.content.Intent(requireContext(),
//                    com.example.periodtracker.notification.AlarmReceiver::class.java).apply {
//                    action = com.example.periodtracker.notification.AlarmReceiver.ACTION_PERIOD_DAY
//                },
//                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
//                        android.app.PendingIntent.FLAG_IMMUTABLE
//            )
//            am.setExactAndAllowWhileIdle(
//                android.app.AlarmManager.RTC_WAKEUP,
//                System.currentTimeMillis() + 30_000, // ← fires in 30 seconds
//                pi
//            )
//            android.widget.Toast.makeText(requireContext(),
//                "Alarm set — lock your phone and wait 30 seconds",
//                android.widget.Toast.LENGTH_LONG).show()
//            android.util.Log.i("TEST", "Test alarm scheduled for 30 seconds from now")
//        }
    }

    // ── Observe ───────────────────────────────────────────────────

    private fun observeData() {
        // ✅ Both observers call tryBind() — whichever arrives last triggers full render
        vm.prediction.observe(viewLifecycleOwner) { pred ->
            Log.d(TAG, "prediction updated: ${pred?.lastPeriodStart} cycleLen=${pred?.cycleLength}")
            tryBind()
        }
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

    // ── tryBind — only runs when BOTH prediction + settings are ready ──

    private fun tryBind() {
        bindJob?.cancel()
        bindJob = viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(50) // ✅ wait 50ms — collapses all rapid fires into 1
            val pred = vm.prediction.value ?: run {
                Log.w(TAG, "tryBind: prediction not ready yet")
                return@launch
            }
            val s = vm.settings.value ?: run {
                Log.w(TAG, "tryBind: settings not ready yet")
                return@launch
            }

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
            Log.d(TAG, "tryBind: fertileWindow=$fertStart→$fertEnd isFertile=$isFertile " +
                    "isLate=$isLate daysLate=$daysLate")

            if (day < 1)
                Log.e(TAG, "EDGE CASE: cycleDay=$day — should never be < 1")
            if (day > pred.cycleLength)
                Log.e(TAG, "EDGE CASE: cycleDay=$day > cycleLength=${pred.cycleLength} — period may be late")
            if (daysToP < 0 && !isLate)
                Log.e(TAG, "EDGE CASE: daysToP=$daysToP but isLate=false — mismatch")
            if (isLate && daysLate == 0)
                Log.e(TAG, "EDGE CASE: isLate=true but daysLate=0 — mismatch")
            if (pred.cycleLength !in 18..60)
                Log.e(TAG, "EDGE CASE: cycleLength=${pred.cycleLength} — outside valid range 18–60")
            if (isFertile)
                Log.i(TAG, "NOTE: today is in fertile window ($fertStart → $fertEnd)")
            if (daysToOv == 0)
                Log.i(TAG, "NOTE: today is estimated ovulation day")
            if (daysToP == 0)
                Log.i(TAG, "NOTE: period is due today")

            binding.cycleRing.setProgress(
                day            = day,
                totalCycleDays = pred.cycleLength,
                periodDays     = s.periodDuration
            )
            Log.d(TAG, "tryBind: ring set day=$day totalCycleDays=${pred.cycleLength} periodDays=${s.periodDuration}")

            val name = s.userName?.takeIf { it.isNotBlank() } ?: "there"
            binding.tvGreeting.text = "Hi $name 👋"
            //binding.tvPhaseSubtitle.text = "${CycleEngine.phaseName(phase)} · ${CycleEngine.phaseDescription(phase)}"

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
        binding.statCardsRow.background       = roundedBg("#FFFFFF", 16f)
        binding.todayTrackingCard.background  = roundedBg("#FDF0F5", 16f)
        binding.lastPeriodCard.background     = roundedBg("#FFF5F7", 14f)
        binding.nextPeriodCard.background     = roundedBg("#F8F0FF", 14f)
        binding.tagLoggedPeriod.background    = roundedBg("#FDE2E9", 20f)
        binding.tagEstimatedPeriod.background = roundedBg("#F1E7FB", 20f)
        listOf(binding.statFlow, binding.statMood, binding.statSymptoms, binding.statNotes)
            .forEach { it.root.background = roundedBg("#FDF0F5", 14f) }
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

    private fun roundedBg(colorHex: String, radiusDp: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radiusDp * resources.displayMetrics.density
        setColor(Color.parseColor(colorHex))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView")
    }



}