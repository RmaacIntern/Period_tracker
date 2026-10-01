package com.aivigil.periodtracker.calendar

import com.aivigil.periodtracker.R

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
import com.aivigil.periodtracker.databinding.FragmentCalendarBinding
import com.aivigil.periodtracker.domain.CycleEngine
import com.aivigil.periodtracker.logsymptoms.LogSymptomsFragment
import com.aivigil.periodtracker.util.ThemeHelper
import com.aivigil.periodtracker.viewmodel.CycleViewModel
import com.aivigil.periodtracker.viewmodel.CycleViewModelFactory
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

    private var displayMonth    = YearMonth.now()
    private var selectedDate    = LocalDate.now()
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
        // FIX: the calendar used to draw only the CURRENT cycle, recomputed in the
        // fragment. These two feeds come from the ViewModel and cover all history
        // plus the full 3-month prediction window.
        vm.loggedPeriodDays.observe(viewLifecycleOwner)    { _ -> tryBind() }
        vm.predictedPeriodDays.observe(viewLifecycleOwner) { _ -> tryBind() }
        // Re-bind when the calendar date advances past midnight.
        vm.today.observe(viewLifecycleOwner) { _ -> tryBind() }
    }

    private fun tryBind() {
        if (_binding == null) return
        val pred = vm.prediction.value ?: return
        val s    = vm.settings.value   ?: return

        // Named `todayDate` rather than `today`: CycleCalendarView also has a
        // private `today` field, and this value is read inside an apply{} block on
        // that view, where a bare `today` is a needless resolution hazard.
        val todayDate = LocalDate.now()

        // FIX: was CycleEngine.periodDays(pred.lastPeriodStart, …), which returns
        // exactly periodDuration days from ONE start date — so a user with six
        // months of logged periods saw pink days only in her current cycle and a
        // completely blank calendar when she scrolled back. vm.loggedPeriodDays is
        // built from every period_entries row.
        val allLoggedDays = vm.loggedPeriodDays.value ?: emptySet()
        // Days actually confirmed by a recorded entry (start dates), so the view
        // can distinguish them from days filled in from the expected duration.
        val confirmedStarts = vm.periodEntries.value
            ?.mapNotNull { runCatching { LocalDate.parse(it.startDate) }.getOrNull() }
            ?.toSet()
            ?: emptySet()

        val fertileWindowDays = CycleEngine.fertileWindowDays(pred.fertileStart, pred.fertileEnd)
        val predictedDays     = (vm.predictedPeriodDays.value ?: emptySet()) - allLoggedDays

        binding.cycleCalendarView.apply {
            displayMonth         = this@CalendarFragment.displayMonth
            selectedDate         = this@CalendarFragment.selectedDate
            this.periodDays      = allLoggedDays
            this.confirmedPeriodDays = allLoggedDays.intersect(
                // A recorded day is one inside a logged entry's real span; start
                // dates are always recorded.
                confirmedStarts + allLoggedDays.filter { d ->
                    confirmedStarts.any { s0 -> !d.isBefore(s0) && d <= todayDate }
                }
            )
            this.fertileWindowDays = fertileWindowDays
            ovulationDay         = pred.ovulationDate
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
        val fullFmt    = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
        val dayNum     = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength, date)
        val phase      = CycleEngine.phase(dayNum, pred.cycleLength, periodDuration)
        val daysToNext = ChronoUnit.DAYS.between(
            date, CycleEngine.nextPeriodDate(pred.lastPeriodStart, pred.cycleLength)
        ).toInt().coerceAtLeast(0)
        val isLate     = CycleEngine.isPeriodLate(pred.lastPeriodStart, pred.cycleLength)

        binding.tvSelectedDayFull.text = date.format(fullFmt)
        binding.tvSelectedDaySub.text  = when {
            isLate && date == LocalDate.now() ->
                "Cycle Day $dayNum · Period is ${CycleEngine.daysLate(pred.lastPeriodStart, pred.cycleLength)} days late"
            daysToNext == 0 -> "Cycle Day $dayNum · Period due today"
            else            -> "Cycle Day $dayNum · $daysToNext days until next period"
        }
        binding.tvPhaseBadge.text = "☀ ${CycleEngine.phaseName(phase)}"

        // FIX: this accepted ANY tapped date, including one in a future month, and
        // the "Log Period Start" sheet then confirmed it — writing a future
        // lastPeriodStart that broke every downstream calculation (cycleDay
        // coerced to 1, isPeriodLate permanently false, daysUntilNextPeriod about
        // two cycles out). A period cannot start in the future.
        val isFuture = date.isAfter(LocalDate.now())
        chosenPeriodStart = if (isFuture) null else date

        val isAlreadyPeriodStart = date == pred.lastPeriodStart
        binding.tvPeriodStartStatus.text = when {
            isAlreadyPeriodStart -> "✓ Already set"
            isFuture             -> "Future date"
            else                 -> "Not set"
        }
        binding.tvPeriodStartStatus.setTextColor(
            requireContext().getColor(
                if (isAlreadyPeriodStart) R.color.cycle_fertile else R.color.text_secondary
            )
        )
        // Disable the action outright for a future date rather than letting the
        // user tap it and silently doing the wrong thing.
        binding.btnLogPeriodStart.isEnabled = !isFuture
        binding.btnLogPeriodStart.alpha     = if (isFuture) 0.45f else 1f

        viewLifecycleOwner.lifecycleScope.launch {
            val logs = vm.getLogsForDate(date)
            val log  = logs.maxByOrNull { it.loggedAt }

            val moods    = log?.moods?.split(",")?.map { it.trim() } ?: emptyList()
            val symptoms = log?.symptoms?.split(",")?.map { it.trim() } ?: emptyList()

            val energy = when {
                "⚡ Energetic" in moods                       -> "High"
                "Fatigue" in symptoms                         -> "Low"
                "😢 Sad" in moods || "😰 Anxious" in moods   -> "Low"
                "😊 Happy" in moods || "😌 Calm" in moods    -> "Normal"
                log != null                                   -> "Normal"
                phase == CycleEngine.Phase.FOLLICULAR ||
                        phase == CycleEngine.Phase.OVULATION  -> "High"
                phase == CycleEngine.Phase.MENSTRUAL          -> "Low"
                phase == CycleEngine.Phase.LUTEAL_LATE        -> "Low"
                else                                          -> "Normal"
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

            val loggedColor    = requireContext().getColor(R.color.cycle_fertile)
            val estimatedColor = Color.parseColor("#A855F7")

            val energyFromLog = log != null && (
                    "⚡ Energetic" in moods || "Fatigue" in symptoms ||
                            "😢 Sad" in moods || "😰 Anxious" in moods ||
                            "😊 Happy" in moods || "😌 Calm" in moods)
            val sleepFromLog  = log != null && (
                    "Fatigue" in symptoms || "😰 Anxious" in moods || "😌 Calm" in moods)

            binding.tvEnergyValue.text  = energy
            binding.tvLibidoValue.text  = libido
            binding.tvSleepValue.text   = sleep
            binding.tvEnergySource.text = if (energyFromLog) "from log" else "estimated"
            binding.tvEnergySource.setTextColor(if (energyFromLog) loggedColor else estimatedColor)
            binding.tvLibidoSource.text = "estimated"
            binding.tvLibidoSource.setTextColor(estimatedColor)
            binding.tvSleepSource.text  = if (sleepFromLog) "from log" else "estimated"
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

    private fun bindDailyTip(day: Int, cycleLength: Int, periodDuration: Int) {
        // Delegate rather than re-deriving — see CycleEngine.ovulationDay().
        val ovDay        = CycleEngine.ovulationDay(cycleLength, periodDuration)
        val fertileStart = CycleEngine.fertileStartDay(cycleLength, periodDuration)
        val lutealEnd    = ovDay + 3
        val pmsStart     = lutealEnd + 1

        val (title, body) = when {
            day <= periodDuration -> Pair("Rest & Restore",
                "Your body is cleansing. Prioritize gentle movement and iron-rich foods.")
            day < fertileStart   -> Pair("High Estrogen Vitality",
                "Your follicular surge brings peak cognitive focus. Great window for demanding tasks.")
            day == ovDay         -> Pair("Ovulation Peak",
                "Fertility is at its highest today. You may feel more confident and social.")
            day in fertileStart..ovDay -> Pair("Fertile Window",
                "You are in your fertile window. Egg white cervical fluid is common now.")
            day <= lutealEnd     -> Pair("Progesterone Rising",
                "Great time for planning and detail-oriented work. Nesting instinct increases.")
            day >= pmsStart      -> Pair("Pre-Menstrual Phase",
                "Take it easier and prioritize self-care. Cravings and mood shifts are normal.")
            else                 -> Pair("Luteal Phase",
                "Progesterone is rising. Focus on rest and nourishing foods.")
        }
        binding.tvTipTitle.text = title
        binding.tvTipBody.text  = body
    }

    private fun bindMonthHeader() {
        binding.tvMonthYear.text = displayMonth.format(
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        )
    }

    // ── Backgrounds ───────────────────────────────────────────────

    private fun applyBackgrounds() {
        // ✅ FIX — replaced all hardcoded Color.parseColor with ThemeHelper
        binding.navRow.background          = ThemeHelper.cardBg(requireContext(), 20f)
        binding.tabMonth.background        = GradientDrawable(    // brand gradient — stays
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#EC4899"), Color.parseColor("#A855F7"))
        ).apply { cornerRadius = 100f * resources.displayMetrics.density }
        binding.calendarCard.background    = ThemeHelper.cardBg(requireContext(), 20f)
        binding.selectedDayCard.background = ThemeHelper.cardBg(requireContext(), 18f)
        binding.dailyTipCard.background    = ThemeHelper.cardBg(requireContext(), 16f)
        binding.tvPhaseBadge.background    = ThemeHelper.insightBannerBg(requireContext(), 20f)
        binding.ivTipImage.background      = ThemeHelper.iconCirclePink(requireContext())
            .apply { shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 12f * resources.displayMetrics.density }
        listOf(binding.cardEnergy, binding.cardLibido, binding.cardSleep)
            .forEach { it.background = ThemeHelper.basalCardBg(requireContext(), 12f) }
        binding.cervicalRow.background     = ThemeHelper.basalCardBg(requireContext(), 12f)

        // Legend dots — semantic medical colors, kept hardcoded
        binding.tvMonthDot.background         = filledCircle("#EC4899")
        binding.legendPeriodDot.background    = filledCircle("#C2185B")
        binding.legendOvulationDot.background = filledCircle("#E66A28")
        binding.legendFertileDot.background   = filledCircle(requireContext().getColor(R.color.cycle_fertile))
        binding.legendPredictedDot.background = filledCircle(requireContext().getColor(R.color.cycle_predicted))
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
                    com.aivigil.periodtracker.profile.sheets.PeriodStartSheet.TAG) != null) {
                return@setOnClickListener
            }
            binding.btnLogPeriodStart.isEnabled = false
            binding.btnLogPeriodStart.alpha = 0.5f

            // Never seed the sheet with a future date — the sheet clamped only its
            // date picker's maxDate, not the initial value it confirms with.
            val initial = (chosenPeriodStart ?: selectedDate)
                .coerceAtMost(LocalDate.now())
            val periods = vm.periodEntries.value ?: emptyList()
            val fmt     = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

            val sheet = com.aivigil.periodtracker.profile.sheets.PeriodStartSheet(
                initialDate     = initial,
                existingPeriods = periods,
                onConfirm = { date ->
                    // Second guard: the repository clamps too, but rejecting here
                    // lets the user know instead of silently changing her input.
                    if (date.isAfter(LocalDate.now())) {
                        android.widget.Toast.makeText(requireContext(),
                            "A period can't start in the future",
                            android.widget.Toast.LENGTH_SHORT).show()
                        return@PeriodStartSheet
                    }
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
                    // Guard against the view being gone — the sheet can be
                    // dismissed after this fragment's view is destroyed.
                    _binding?.btnLogPeriodStart?.let {
                        it.isEnabled = true
                        it.alpha = 1.0f
                    }
                }
            )
            sheet.show(parentFragmentManager,
                com.aivigil.periodtracker.profile.sheets.PeriodStartSheet.TAG)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private fun filledCircle(colorHex: String) = filledCircle(Color.parseColor(colorHex))

    private fun filledCircle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}