package com.aivigil.periodtracker.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.aivigil.periodtracker.data.entity.DailyLog
import com.aivigil.periodtracker.data.entity.PeriodEntry
import com.aivigil.periodtracker.data.entity.UserSettings
import com.aivigil.periodtracker.data.repository.CycleRepository
import com.aivigil.periodtracker.domain.CycleEngine
import com.aivigil.periodtracker.notification.NotificationPrefs
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class CycleViewModel(app: Application) : AndroidViewModel(app) {

    private val TAG  = "CycleViewModel"
    private val repo = CycleRepository.getInstance(app)

    // ── Raw live data ─────────────────────────────────────────────

    val settings: LiveData<UserSettings?>          = repo.observeSettings()
    val periodEntries: LiveData<List<PeriodEntry>> = repo.observePeriodEntries()
    val allLogs: LiveData<List<DailyLog>>          = repo.observeAllLogs()

    // ── TODAY ─────────────────────────────────────────────────────
    //
    // FIX (midnight staleness): every date-derived value in this ViewModel used
    // to call LocalDate.now() inside a .map{} on _prediction, which only
    // re-emits when the DB changes. An app left open past midnight therefore
    // reported yesterday's cycle day, yesterday's fertility status, and
    // yesterday's "today's log" — indefinitely.
    //
    // `today` is now an explicit trigger. Every date-dependent value derives
    // from it, and onAppForegrounded() advances it. That makes the date an
    // observable input instead of a hidden side effect.
    private val _today = MutableLiveData(LocalDate.now())
    val today: LiveData<LocalDate> = _today

    /**
     * Call from Activity.onResume() and from an ACTION_DATE_CHANGED /
     * ACTION_TIMEZONE_CHANGED receiver. Cheap and idempotent: it only emits when
     * the calendar date has actually moved.
     */
    fun onAppForegrounded() {
        val now = LocalDate.now()
        if (_today.value != now) {
            Log.i(TAG, "onAppForegrounded: date advanced ${_today.value} → $now — recomputing")
            _today.value = now
        }
        refreshPrediction()
    }

    // Today's logs re-bind whenever the date advances, so they always query the
    // real current date instead of a string captured once at process start.
    val todayLog: LiveData<DailyLog?> = _today.switchMap { repo.observeLogFor(it) }
    val todayLogs: LiveData<List<DailyLog>> = _today.switchMap { repo.observeLogsFor(it) }

    // ── SINGLE PREDICTION SOURCE ──────────────────────────────────
    //
    // All cycle/ovulation/fertile-window values exposed to the UI
    // are derived from this one MediatorLiveData. Nothing calls
    // CycleEngine directly with raw UserSettings anymore.

    private val _prediction    = MediatorLiveData<CycleEngine.CyclePrediction?>()
    val prediction: LiveData<CycleEngine.CyclePrediction?> = _prediction

    // ✅ FIX 2 — isFertileToday computed inside refresh() so LocalDate.now()
    // is always fresh. The old .map { LocalDate.now() } version was computed
    // once and never updated if the app stayed open past midnight.
    private val _isFertileToday = MutableLiveData<Boolean>()
    val isFertileToday: LiveData<Boolean> = _isFertileToday

    private fun refreshPrediction() = viewModelScope.launch {
        // ✅ FIX 3 — explicitly post null so UI can show empty state
        // instead of silently doing nothing when there is no period data yet
        val pred = repo.getBestPrediction()
        if (pred == null) {
            Log.w(TAG, "refresh: getBestPrediction returned null — no period data yet")
            _prediction.postValue(null)
            _isFertileToday.postValue(false)
            return@launch
        }

        _prediction.postValue(pred)

        val today = LocalDate.now()
        _isFertileToday.postValue(
            CycleEngine.isFertile(today, pred.fertileStart, pred.fertileEnd)
        )

        // FIX (user consent): alarms used to be (re)scheduled here unconditionally
        // on every settings / period / log change. That silently undid the
        // reminder switches in Profile — a user who turned period reminders OFF
        // had them re-armed the next time anything touched the database.
        // NotificationPrefs is now the single gate for all scheduling.
        NotificationPrefs.rescheduleFromPrediction(
            context      = getApplication(),
            nextPeriod   = pred.nextPeriodDate,
            ovulation    = pred.ovulationDate,
            today        = today
        )

        Log.d(TAG, "refresh: prediction updated — " +
                "lastPeriod=${pred.lastPeriodStart} " +
                "cycleLen=${pred.cycleLength} " +
                "nextPeriod=${pred.nextPeriodDate} " +
                "ovulation=${pred.ovulationDate} " +
                "fertile=${pred.fertileStart}→${pred.fertileEnd} " +
                "confidence=${pred.confidence} source=${pred.dataSource}")
    }

    init {
        _prediction.addSource(settings)      { refreshPrediction() }
        _prediction.addSource(periodEntries) { refreshPrediction() }
        // Date rollover must recompute the prediction-derived alarm schedule too.
        _prediction.addSource(_today)        { refreshPrediction() }

        // Only recalculate for logs that actually affect the ovulation engine
        // (BBT / LH / cervical fluid). Flow, mood and symptom logs do not.
        //
        // FIX: this used to inspect only the single newest log, so DELETING a BBT
        // entry left a stale prediction in place. It now looks at whether any
        // bio-signal data exists at all, which covers inserts, edits and deletes.
        _prediction.addSource(allLogs) { logs ->
            val bioSignalCount = logs.count { log ->
                log.basalTemp != null ||
                        (log.lhTestResult.isNotBlank() && log.lhTestResult != "Not Tested") ||
                        log.cervicalFluid.isNotBlank()
            }
            if (bioSignalCount != lastBioSignalCount) {
                Log.d(TAG, "refresh: bio-signal log count $lastBioSignalCount → $bioSignalCount")
                lastBioSignalCount = bioSignalCount
                refreshPrediction()
            }
        }
    }

    private var lastBioSignalCount = -1

    // ── Derived: cycle basics ─────────────────────────────────────

    // Every value below takes `today` as an explicit input and is wired to the
    // _today trigger, so all of them recompute on a date rollover instead of
    // silently reporting yesterday's numbers.

    private fun <T> derive(default: T, block: (CycleEngine.CyclePrediction, LocalDate) -> T)
            : LiveData<T> = MediatorLiveData<T>().apply {
        fun calc() {
            val pred = _prediction.value
            val day  = _today.value ?: LocalDate.now()
            value = if (pred == null) default else block(pred, day)
        }
        addSource(_prediction) { calc() }
        addSource(_today)      { calc() }
    }

    val cycleDay: LiveData<Int> = derive(1) { p, today ->
        CycleEngine.cycleDay(p.lastPeriodStart, p.cycleLength, today)
    }

    val nextPeriodDate: LiveData<LocalDate?> = _prediction.map { it?.nextPeriodDate }
    val ovulationDate: LiveData<LocalDate?>  = _prediction.map { it?.ovulationDate }

    val daysUntilNextPeriod: LiveData<Int> = derive(0) { p, today ->
        CycleEngine.daysUntilNextPeriod(p.lastPeriodStart, p.cycleLength, today)
    }

    val isPeriodLate: LiveData<Boolean> = derive(false) { p, today ->
        CycleEngine.isPeriodLate(p.lastPeriodStart, p.cycleLength, today)
    }

    val daysLate: LiveData<Int> = derive(0) { p, today ->
        CycleEngine.daysLate(p.lastPeriodStart, p.cycleLength, today)
    }

    // ── Derived: phase ────────────────────────────────────────────

    val currentPhase: LiveData<CycleEngine.Phase> = MediatorLiveData<CycleEngine.Phase>().apply {
        fun calc() {
            val pred = _prediction.value ?: return
            val dur  = settings.value?.periodDuration ?: 5
            val day  = CycleEngine.cycleDay(
                pred.lastPeriodStart, pred.cycleLength, _today.value ?: LocalDate.now()
            )
            value    = CycleEngine.phase(day, pred.cycleLength, dur)
        }
        addSource(_prediction) { calc() }
        addSource(settings)    { calc() }
        addSource(_today)      { calc() }
    }

    /**
     * True only when the user has enough real history for predictions to mean
     * something. UI must show an explicit empty state rather than the
     * placeholder numbers baked into the layout XML when this is false.
     */
    val hasEnoughDataForPredictions: LiveData<Boolean> = periodEntries.map { it.isNotEmpty() }

    // ── Derived: fertility ────────────────────────────────────────

    // isFertileToday is now a MutableLiveData updated inside refresh()
    // See _isFertileToday declaration above — ✅ FIX 2

    val fertileWindowDays: LiveData<Set<LocalDate>> = _prediction.map { pred ->
        pred?.let { CycleEngine.fertileWindowDays(it.fertileStart, it.fertileEnd) } ?: emptySet()
    }

    // ── Derived: calendar sets ────────────────────────────────────

    val periodDays: LiveData<Set<LocalDate>> = MediatorLiveData<Set<LocalDate>>().apply {
        fun calc() {
            val pred = _prediction.value ?: run { value = emptySet(); return }
            val dur  = settings.value?.periodDuration ?: 5
            value    = CycleEngine.periodDays(pred.lastPeriodStart, dur)
        }
        addSource(_prediction) { calc() }
        addSource(settings)    { calc() }
    }

    // ✅ FIX 1 — now returns 3 months of future periods instead of just the next one.
    // CalendarFragment and any other consumer can show the full upcoming schedule.
    val predictedPeriodDays: LiveData<Set<LocalDate>> = MediatorLiveData<Set<LocalDate>>().apply {
        fun calc() {
            val pred = _prediction.value ?: run { value = emptySet(); return }
            val dur  = settings.value?.periodDuration ?: 5
            value    = CycleEngine.futurePeriodDays(
                pred.lastPeriodStart, pred.cycleLength, dur,
                monthsAhead = 3, today = _today.value ?: LocalDate.now()
            ).values.flatten().toSet()
        }
        addSource(_prediction) { calc() }
        addSource(settings)    { calc() }
        addSource(_today)      { calc() }
    }

    /**
     * Every day the user has ACTUALLY recorded a period on, across all history —
     * derived from period_entries rather than from the single latest prediction.
     *
     * FIX: CalendarFragment used to call CycleEngine.periodDays(pred.lastPeriodStart, …)
     * itself, which only ever produced the current cycle. A user with six months
     * of logged periods saw pink days in the current month and nothing at all
     * when she scrolled back.
     *
     * Closed periods use their real recorded length; an open period is filled to
     * the expected duration and no further.
     */
    val loggedPeriodDays: LiveData<Set<LocalDate>> = MediatorLiveData<Set<LocalDate>>().apply {
        fun calc() {
            val entries = periodEntries.value ?: run { value = emptySet(); return }
            val dur     = settings.value?.periodDuration ?: 5
            val today   = _today.value ?: LocalDate.now()
            val out     = mutableSetOf<LocalDate>()
            entries.forEach { entry ->
                val start = runCatching { LocalDate.parse(entry.startDate) }.getOrNull()
                    ?: return@forEach
                val end = entry.endDate
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: minOf(CycleEngine.periodEndDate(start, dur), today)
                var d = start
                // Bounded so a corrupt endDate cannot spin here.
                while (!d.isAfter(end) && ChronoUnit.DAYS.between(start, d) < 15) {
                    out.add(d); d = d.plusDays(1)
                }
            }
            value = out
        }
        addSource(periodEntries) { calc() }
        addSource(settings)      { calc() }
        addSource(_today)        { calc() }
    }

    // ── Derived: confidence ───────────────────────────────────────

    val predictionConfidence: LiveData<Int> = _prediction.map { it?.confidence ?: 0 }

    // ── Period actions ────────────────────────────────────────────

    fun logPeriodStartToday() = viewModelScope.launch { repo.logPeriodStartToday() }

    fun logPeriodStart(date: LocalDate) = viewModelScope.launch { repo.logPeriodStart(date) }

    // ── Awaitable variants ────────────────────────────────────────
    //
    // The fire-and-forget versions above launch in viewModelScope and return
    // immediately, so a caller that navigates away straight after (e.g.
    // LogSymptomsFragment.popBackStack()) was racing the database write, and any
    // exception surfaced as an uncaught crash rather than something the screen
    // could report. These suspend variants let the caller await the write and
    // handle failure.

    suspend fun logPeriodStartAwait(date: LocalDate) = repo.logPeriodStart(date)

    suspend fun saveDailyLogAwait(
        flow: String,
        moods: List<String>,
        symptoms: List<String>,
        cervicalFluid: String,
        basalTemp: Float?,
        lhTestResult: String = "Not Tested",
        notes: String,
        date: LocalDate = LocalDate.now(),
        periodConfirmed: Boolean = false
    ) {
        Log.i(TAG, "saveDailyLogAwait: date=$date flow=$flow lh=$lhTestResult " +
                "bbt=$basalTemp confirmed=$periodConfirmed")
        repo.saveDailyLog(
            date, flow, moods, symptoms, cervicalFluid,
            basalTemp, lhTestResult, notes, periodConfirmed
        )
    }

    suspend fun updateDailyLogAwait(log: DailyLog) = repo.updateDailyLog(log)

    /**
     * Marks the current open period as ended today.
     * Returns true if a period was open and was successfully closed.
     * Caller should show a confirmation dialog before calling this.
     */
    fun logPeriodEnd(onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val success = repo.logPeriodEnd()
        onResult(success)
    }

    /**
     * LiveData that emits true when there is an open PeriodEntry (endDate == null).
     * Re-evaluated whenever periodEntries list changes.
     * Home screen observes this to switch between "Period Ended" and "Log Today" buttons.
     */
    val isCurrentlyOnPeriod: LiveData<Boolean> = periodEntries.map { entries ->
        entries.maxByOrNull { it.startDate }?.endDate == null
    }

    fun deletePeriodEntry(id: Int) = viewModelScope.launch { repo.deletePeriodEntry(id) }

    // ✅ FIX 5 — fetch only the single entry by id instead of loading all entries
    // Requires repo.getPeriodEntryById(id) — add to CycleRepository and PeriodDao if missing
    fun updatePeriodStartDate(id: Int, newDate: LocalDate) = viewModelScope.launch {
        val entry = repo.getPeriodEntryById(id) ?: run {
            Log.w(TAG, "updatePeriodStartDate: entry id=$id not found")
            return@launch
        }
        repo.updatePeriodEntry(entry.copy(startDate = newDate.toString()))
    }

    // ── Log actions ───────────────────────────────────────────────

    /**
     * Saves a daily log.
     * [periodConfirmed] must be true only when the user answered
     * "Yes, period started" in the UI confirmation dialog.
     */
    fun saveDailyLog(
        flow: String,
        moods: List<String>,
        symptoms: List<String>,
        cervicalFluid: String,
        basalTemp: Float?,
        lhTestResult: String = "Not Tested",
        notes: String,
        date: LocalDate = LocalDate.now(),
        periodConfirmed: Boolean = false
    ) = viewModelScope.launch {
        Log.i(TAG, "saveDailyLog: date=$date flow=$flow lh=$lhTestResult bbt=$basalTemp confirmed=$periodConfirmed")
        repo.saveDailyLog(
            date, flow, moods, symptoms, cervicalFluid,
            basalTemp, lhTestResult, notes, periodConfirmed
        )
    }

    // ── Settings actions ──────────────────────────────────────────

    fun updateCycleLength(len: Int)              = viewModelScope.launch { repo.updateCycleLength(len) }
    fun updatePeriodDuration(dur: Int)           = viewModelScope.launch { repo.updatePeriodDuration(dur) }
    fun updateLastPeriodStart(date: LocalDate)   = viewModelScope.launch { repo.updateLastPeriodStart(date) }
    fun updateConditions(c: String)              = viewModelScope.launch { repo.updateConditions(c) }

    fun updateProfile(name: String, age: Int, heightCm: Int, weightKg: Float) = viewModelScope.launch {
        val current = repo.getSettings() ?: return@launch
        repo.saveSettings(current.copy(userName = name, age = age, heightCm = heightCm, weightKg = weightKg))
    }

    fun updateActivityLevel(level: String) = viewModelScope.launch {
        val current = repo.getSettings() ?: return@launch
        repo.saveSettings(current.copy(activityLevel = level))
    }

    fun deleteAllData() = viewModelScope.launch {
        com.aivigil.periodtracker.notification.AlarmScheduler.cancelAll(getApplication())
        repo.deleteAllData()
    }

    fun deleteDailyLog(log: DailyLog)       = viewModelScope.launch { repo.deleteDailyLog(log) }
    fun deleteDailyLogById(id: Int)         = viewModelScope.launch { repo.deleteDailyLogById(id) }

    /**
     * Re-inserts a deleted log, backing the Undo action in the history screen.
     * entryId is reset to 0 so Room assigns a fresh autoGenerate id rather than
     * colliding with anything inserted since the delete.
     */
    fun restoreDailyLog(log: DailyLog) = viewModelScope.launch {
        repo.restoreDailyLog(log.copy(entryId = 0))
    }
    fun updateDailyLog(log: DailyLog)       = viewModelScope.launch { repo.updateDailyLog(log) }
    suspend fun getLogsForDate(date: LocalDate) = repo.getLogForDate(date)

    // ── Onboarding ────────────────────────────────────────────────

    fun saveOnboardingData(
        userName: String,
        age: Int,
        heightCm: Int,
        weightKg: Float,
        activityLevel: String,
        goal: String,
        conditions: String,
        cycleLength: Int,
        periodDuration: Int,
        lastPeriodStart: LocalDate
    ) = viewModelScope.launch {
        repo.saveSettings(
            UserSettings(
                id              = 1,
                userName        = userName,
                age             = age,
                heightCm        = heightCm,
                weightKg        = weightKg,
                activityLevel   = activityLevel,
                goal            = goal,
                conditions      = conditions,
                cycleLength     = cycleLength,
                periodDuration  = periodDuration,
                lastPeriodStart = lastPeriodStart.toString(),
                memberSince     = LocalDate.now().toString()
            )
        )
        repo.logPeriodStart(lastPeriodStart)
        repo.markOnboardingComplete()
    }

    // ── Insights: bar chart data ──────────────────────────────────

    /**
     * Completed cycle lengths for the history chart, newest last, max 6.
     *
     * FIX 1 — this was a `fun` returning `periodEntries.map { … }`, so every call
     * built a NEW LiveData transformation. A fragment calling it from
     * onViewCreated leaked one observer chain per view recreation.
     * It is now a single `val`.
     *
     * FIX 2 — the in-progress cycle is no longer appended. Mixing "days elapsed
     * so far" into a chart of completed cycle lengths always rendered the current
     * cycle as an abnormally short one, and on the day a period started it drew a
     * zero-height bar that looked like a rendering failure.
     *
     * FIX 3 — LocalDate.parse is no longer allowed to throw; a malformed row is
     * skipped rather than crashing the Insights screen.
     */
    @get:JvmName("getBarChartDataLiveData")
    val barChartData: LiveData<List<Pair<String, Int>>> = periodEntries.map { entries ->
        val fmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        val sorted = entries
            .mapNotNull { e -> runCatching { LocalDate.parse(e.startDate) }.getOrNull() }
            .sorted()
        if (sorted.size < 2) return@map emptyList()

        (0 until sorted.size - 1)
            .mapNotNull { i ->
                val days = ChronoUnit.DAYS.between(sorted[i], sorted[i + 1]).toInt()
                // Drop physiologically impossible gaps rather than drawing them.
                if (days in 18..60) sorted[i].format(fmt) to days else null
            }
            .takeLast(6)
    }


}

class CycleViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CycleViewModel(app) as T
    }
}