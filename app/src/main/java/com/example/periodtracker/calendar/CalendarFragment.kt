package com.example.periodtracker.calendar

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.periodtracker.databinding.FragmentCalendarBinding
import com.example.periodtracker.domain.CycleEngine
import com.example.periodtracker.logsymptoms.LogSymptomsFragment
import com.example.periodtracker.viewmodel.CycleViewModel
import com.example.periodtracker.viewmodel.CycleViewModelFactory
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val vm: CycleViewModel by activityViewModels {
        CycleViewModelFactory(requireActivity().application)
    }

    private var displayMonth = YearMonth.now()
    private var selectedDate = LocalDate.now()
    private var chosenPeriodStart: LocalDate? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyBackgrounds()
        bindMonthHeader()
        observeData()
        bindClickListeners()
    }

    // ── Observe ───────────────────────────────────────────────────

    private fun observeData() {
        vm.prediction.observe(viewLifecycleOwner) { _ -> tryBind() }
        vm.settings.observe(viewLifecycleOwner)   { _ -> tryBind() }
    }

    private fun tryBind() {
        val pred = vm.prediction.value ?: return
        val s    = vm.settings.value   ?: return

        // ✅ Log all calendar data so we can verify correct dates
        val today = LocalDate.now()
        val periodDays        = CycleEngine.periodDays(pred.lastPeriodStart, s.periodDuration)
        val fertileWindowDays = CycleEngine.fertileWindowDays(pred.fertileStart, pred.fertileEnd)
        val predictedDays     = CycleEngine.predictedPeriodDays(pred.lastPeriodStart, pred.cycleLength, s.periodDuration)
        val currentDay        = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength)

        android.util.Log.i("CalendarFragment", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.i("CalendarFragment", "tryBind: today=$today")
        android.util.Log.i("CalendarFragment", "tryBind: lastPeriodStart=${pred.lastPeriodStart}")
        android.util.Log.i("CalendarFragment", "tryBind: cycleLength=${pred.cycleLength} periodDuration=${s.periodDuration}")
        android.util.Log.i("CalendarFragment", "tryBind: currentCycleDay=$currentDay")
        android.util.Log.i("CalendarFragment", "tryBind: periodDays=$periodDays")
        android.util.Log.i("CalendarFragment", "tryBind: predictedPeriodDays=$predictedDays")
        android.util.Log.i("CalendarFragment", "tryBind: fertileWindow=${pred.fertileStart}→${pred.fertileEnd}")
        android.util.Log.i("CalendarFragment", "tryBind: fertileWindowDays=$fertileWindowDays")
        android.util.Log.i("CalendarFragment", "tryBind: ovulationDay=${pred.ovulationDate}")
        android.util.Log.i("CalendarFragment", "tryBind: nextPeriod=${pred.nextPeriodDate}")
        android.util.Log.i("CalendarFragment", "tryBind: isFertileToday=${CycleEngine.isFertile(today, pred.fertileStart, pred.fertileEnd)}")
        android.util.Log.i("CalendarFragment", "tryBind: isPeriodLate=${CycleEngine.isPeriodLate(pred.lastPeriodStart, pred.cycleLength)}")
        android.util.Log.i("CalendarFragment", "tryBind: daysLate=${CycleEngine.daysLate(pred.lastPeriodStart, pred.cycleLength)}")

        // Edge case guards
        if (periodDays.isEmpty())
            android.util.Log.e("CalendarFragment", "EDGE CASE: periodDays is empty!")
        if (fertileWindowDays.isEmpty())
            android.util.Log.e("CalendarFragment", "EDGE CASE: fertileWindowDays is empty!")
        if (pred.fertileStart.isAfter(pred.fertileEnd))
            android.util.Log.e("CalendarFragment", "EDGE CASE: fertileStart=${pred.fertileStart} is after fertileEnd=${pred.fertileEnd}!")
        if (pred.ovulationDate.isBefore(pred.lastPeriodStart))
            android.util.Log.e("CalendarFragment", "EDGE CASE: ovulationDate=${pred.ovulationDate} is before lastPeriodStart!")
        if (pred.ovulationDate.isAfter(pred.nextPeriodDate))
            android.util.Log.e("CalendarFragment", "EDGE CASE: ovulationDate=${pred.ovulationDate} is after nextPeriodDate!")

        binding.cycleCalendarView.apply {
            displayMonth        = this@CalendarFragment.displayMonth
            selectedDate        = this@CalendarFragment.selectedDate
            this.periodDays          = periodDays
            this.fertileWindowDays   = fertileWindowDays
            ovulationDay        = pred.ovulationDate
            this.predictedPeriodDays = predictedDays
            onDateSelected = { date ->
                this@CalendarFragment.selectedDate = date
                bindSelectedDay(date, pred, s.periodDuration)
            }
        }

        binding.tvEstDaysLabel.text = "Est. ${pred.cycleLength} Days"
        bindSelectedDay(selectedDate, pred, s.periodDuration)

        val day = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength)
        bindDailyTip(day, pred.cycleLength, s.periodDuration)
    }

    private fun bindSelectedDay(
        date: LocalDate,
        pred: CycleEngine.CyclePrediction,
        periodDuration: Int
    ) {
        val fullFmt  = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
        val dayNum   = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength, date)
        val phase    = CycleEngine.phase(dayNum, pred.cycleLength, periodDuration)
        val daysToNext = ChronoUnit.DAYS.between(
            date, CycleEngine.nextPeriodDate(pred.lastPeriodStart, pred.cycleLength)
        ).toInt().coerceAtLeast(0)
        val isLate   = CycleEngine.isPeriodLate(pred.lastPeriodStart, pred.cycleLength)

        // ✅ Log selected day details
        android.util.Log.d("CalendarFragment", "bindSelectedDay: date=$date " +
                "dayNum=$dayNum phase=${CycleEngine.phaseName(phase)} " +
                "daysToNext=$daysToNext isLate=$isLate")
        android.util.Log.d("CalendarFragment", "bindSelectedDay: " +
                "inPeriod=${date in CycleEngine.periodDays(pred.lastPeriodStart, periodDuration)} " +
                "inFertile=${CycleEngine.isFertile(date, pred.fertileStart, pred.fertileEnd)} " +
                "isOvulation=${date == pred.ovulationDate} " +
                "inPredicted=${date in CycleEngine.predictedPeriodDays(pred.lastPeriodStart, pred.cycleLength, periodDuration)}")

        // ... rest of your existing bindSelectedDay code unchanged
        binding.tvSelectedDayFull.text = date.format(fullFmt)
        binding.tvSelectedDaySub.text  = when {
            isLate && date == LocalDate.now() ->
                "Cycle Day $dayNum · Period is ${CycleEngine.daysLate(pred.lastPeriodStart, pred.cycleLength)} days late"
            daysToNext == 0 ->
                "Cycle Day $dayNum · Period due today"
            else ->
                "Cycle Day $dayNum · $daysToNext days until next period"
        }
        binding.tvPhaseBadge.text = "☀ ${CycleEngine.phaseName(phase)}"

        chosenPeriodStart = date
        val isAlreadyPeriodStart = date == pred.lastPeriodStart
        binding.tvPeriodStartStatus.text = if (isAlreadyPeriodStart) "✓ Already set" else "Not set"
        binding.tvPeriodStartStatus.setTextColor(
            Color.parseColor(if (isAlreadyPeriodStart) "#3B7A57" else "#8A7A8F")
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val logs = vm.getLogsForDate(date)
            val log  = logs.maxByOrNull { it.loggedAt }

            val moods    = log?.moods?.split(",")?.map { it.trim() } ?: emptyList()
            val symptoms = log?.symptoms?.split(",")?.map { it.trim() } ?: emptyList()

            android.util.Log.d("CalendarFragment", "bindSelectedDay: log for $date — " +
                    "found=${log != null} flow=${log?.flow} moods=$moods symptoms=$symptoms")

            val energy = when {
                "⚡ Energetic" in moods                        -> "High"
                "Fatigue" in symptoms                          -> "Low"
                "😢 Sad" in moods || "😰 Anxious" in moods    -> "Low"
                "😊 Happy" in moods || "😌 Calm" in moods     -> "Normal"
                log != null                                    -> "Normal"
                phase == CycleEngine.Phase.FOLLICULAR ||
                        phase == CycleEngine.Phase.OVULATION   -> "High"
                phase == CycleEngine.Phase.MENSTRUAL           -> "Low"
                phase == CycleEngine.Phase.LUTEAL_LATE         -> "Low"
                else                                           -> "Normal"
            }

            val libido = when (phase) {
                CycleEngine.Phase.OVULATION   -> "Peak"
                CycleEngine.Phase.FOLLICULAR  -> "High"
                CycleEngine.Phase.MENSTRUAL   -> "Low"
                CycleEngine.Phase.LUTEAL_LATE -> "Low"
                else                          -> "Normal"
            }

            val sleep = when {
                "Fatigue" in symptoms                          -> "Need more"
                "😰 Anxious" in moods                         -> "Restless"
                "😌 Calm" in moods                            -> "Good"
                log != null                                   -> "Normal"
                phase == CycleEngine.Phase.MENSTRUAL          -> "8.0 hrs"
                phase == CycleEngine.Phase.LUTEAL_LATE        -> "Restless"
                phase == CycleEngine.Phase.OVULATION ||
                        phase == CycleEngine.Phase.FOLLICULAR -> "7.5 hrs"
                else                                          -> "7.0 hrs"
            }

            val loggedColor    = Color.parseColor("#3B7A57")
            val estimatedColor = Color.parseColor("#A855F7")

            val energyFromLog = log != null && (
                    "⚡ Energetic" in moods || "Fatigue" in symptoms ||
                            "😢 Sad" in moods || "😰 Anxious" in moods ||
                            "😊 Happy" in moods || "😌 Calm" in moods)

            val sleepFromLog = log != null && (
                    "Fatigue" in symptoms ||
                            "😰 Anxious" in moods ||
                            "😌 Calm" in moods)

            android.util.Log.d("CalendarFragment", "bindSelectedDay: " +
                    "energy=$energy(fromLog=$energyFromLog) " +
                    "libido=$libido sleep=$sleep(fromLog=$sleepFromLog)")

            binding.tvEnergyValue.text  = energy
            binding.tvLibidoValue.text  = libido
            binding.tvSleepValue.text   = sleep

            binding.tvEnergySource.text = if (energyFromLog) "from log" else "estimated"
            binding.tvEnergySource.setTextColor(if (energyFromLog) loggedColor else estimatedColor)
            binding.tvLibidoSource.text = "estimated"
            binding.tvLibidoSource.setTextColor(estimatedColor)
            binding.tvSleepSource.text = if (sleepFromLog) "from log" else "estimated"
            binding.tvSleepSource.setTextColor(if (sleepFromLog) loggedColor else estimatedColor)
        }

        val (cervical, chance) = when (phase) {
            CycleEngine.Phase.OVULATION  -> Pair("Egg white · Stretchy",   "Chances: Peak")
            CycleEngine.Phase.FOLLICULAR -> Pair("Creamy · Low viscosity", "Chances: High")
            CycleEngine.Phase.MENSTRUAL  -> Pair("Menstrual flow",          "N/A")
            else                         -> Pair("Dry · Minimal",           "Chances: Low")
        }
        binding.tvCervicalValue.text   = cervical
        binding.tvFertilityChance.text = chance
    }

    // ── Daily tip — scales with cycle length ──────────────────────

    /**
     * Tips are now proportional to cycle length instead of hardcoded day numbers.
     * For a 28-day cycle: same as before.
     * For a 25-day cycle: thresholds shift down proportionally.
     * For a 35-day cycle: thresholds shift up proportionally.
     */
    private fun bindDailyTip(day: Int, cycleLength: Int, periodDuration: Int) {
        // ✅ Calculate phase boundaries dynamically from cycle length
        val ovDay        = (cycleLength - 14).coerceAtLeast(periodDuration + 2)
        val fertileStart = (ovDay - 5).coerceAtLeast(periodDuration + 1)
        val lutealEnd    = ovDay + 3
        val pmsStart     = lutealEnd + 1

        val (title, body) = when {
            day <= periodDuration -> Pair(
                "Rest & Restore",
                "Your body is cleansing. Prioritize gentle movement and iron-rich foods."
            )
            day < fertileStart -> Pair(
                "High Estrogen Vitality",
                "Your follicular surge brings peak cognitive focus. Great window for demanding tasks."
            )
            day == ovDay -> Pair(
                "Ovulation Peak",
                "Fertility is at its highest today. You may feel more confident and social."
            )
            day in fertileStart..ovDay -> Pair(
                "Fertile Window",
                "You are in your fertile window. Egg white cervical fluid is common now."
            )
            day <= lutealEnd -> Pair(
                "Progesterone Rising",
                "Great time for planning and detail-oriented work. Nesting instinct increases."
            )
            day >= pmsStart -> Pair(
                "Pre-Menstrual Phase",
                "Take it easier and prioritize self-care. Cravings and mood shifts are normal."
            )
            else -> Pair(
                "Luteal Phase",
                "Progesterone is rising. Focus on rest and nourishing foods."
            )
        }

        binding.tvTipTitle.text = title
        binding.tvTipBody.text  = body
    }

    // ── Month header ──────────────────────────────────────────────

    private fun bindMonthHeader() {
        binding.tvMonthYear.text = displayMonth.format(
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        )
    }

    // ── Backgrounds ───────────────────────────────────────────────

    private fun applyBackgrounds() {
        binding.navRow.background = roundedBg("#FFFFFF", 20f)
        binding.tabMonth.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#EC4899"), Color.parseColor("#A855F7"))
        ).apply { cornerRadius = 100f * resources.displayMetrics.density }
        binding.calendarCard.background       = roundedBg("#FFFFFF", 20f)
        binding.tvMonthDot.background         = filledCircle("#EC4899")
        binding.legendPeriodDot.background    = filledCircle("#C2185B")
        binding.legendOvulationDot.background = filledCircle("#E66A28")
        binding.legendFertileDot.background   = filledCircle("#3B7A57")
        binding.legendPredictedDot.background = filledCircle("#FBCFE8")
        binding.selectedDayCard.background    = roundedBg("#FFFFFF", 18f)
        binding.tvPhaseBadge.background       = roundedBg("#F1E7FB", 20f)
        listOf(binding.cardEnergy, binding.cardLibido, binding.cardSleep)
            .forEach { it.background = roundedBg("#FDF0F5", 12f) }
        binding.cervicalRow.background        = roundedBg("#FDF0F5", 12f)
        binding.dailyTipCard.background       = roundedBg("#FFFFFF", 16f)
        binding.ivTipImage.background         = roundedBg("#FDE2E9", 12f)
    }

    // ── Click listeners ───────────────────────────────────────────

    private fun bindClickListeners() {
        binding.btnPrevMonth.setOnClickListener {
            displayMonth = displayMonth.minusMonths(1)
            binding.cycleCalendarView.displayMonth = displayMonth
            bindMonthHeader()
        }
        binding.btnNextMonth.setOnClickListener {
            displayMonth = displayMonth.plusMonths(1)
            binding.cycleCalendarView.displayMonth = displayMonth
            bindMonthHeader()
        }

        binding.btnLogPeriodStart.setOnClickListener {
            if (parentFragmentManager.findFragmentByTag(
                    com.example.periodtracker.profile.sheets.PeriodStartSheet.TAG) != null) {
                return@setOnClickListener
            }

            binding.btnLogPeriodStart.isEnabled = false
            binding.btnLogPeriodStart.alpha = 0.5f

            val initial = chosenPeriodStart ?: selectedDate
            val periods = vm.periodEntries.value ?: emptyList()
            val fmt     = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

            val sheet = com.example.periodtracker.profile.sheets.PeriodStartSheet(
                initialDate     = initial,
                existingPeriods = periods,
                onConfirm = { date ->
                    vm.logPeriodStart(date)
                    android.widget.Toast.makeText(requireContext(),
                        "Period start logged for ${date.format(fmt)} ✓",
                        android.widget.Toast.LENGTH_SHORT).show()
                },
                onDelete  = { id -> vm.deletePeriodEntry(id) },
                onCorrect = { id, newDate ->
                    vm.updatePeriodStartDate(id, newDate)
                    android.widget.Toast.makeText(requireContext(),
                        "Period date corrected to ${newDate.format(fmt)} ✓",
                        android.widget.Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                    binding.btnLogPeriodStart.isEnabled = true
                    binding.btnLogPeriodStart.alpha = 1.0f
                }
            )

            sheet.show(parentFragmentManager,
                com.example.periodtracker.profile.sheets.PeriodStartSheet.TAG)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun filledCircle(colorHex: String) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(colorHex))
    }

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