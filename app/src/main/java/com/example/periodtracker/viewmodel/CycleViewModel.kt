package com.example.periodtracker.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.periodtracker.data.entity.DailyLog
import com.example.periodtracker.data.entity.PeriodEntry
import com.example.periodtracker.data.entity.UserSettings
import com.example.periodtracker.data.repository.CycleRepository
import com.example.periodtracker.domain.CycleEngine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class CycleViewModel(app: Application) : AndroidViewModel(app) {

    private val TAG  = "CycleViewModel"
    private val repo = CycleRepository.getInstance(app)

    // ── Raw live data ─────────────────────────────────────────────

    val settings: LiveData<UserSettings?>          = repo.observeSettings()
    val periodEntries: LiveData<List<PeriodEntry>> = repo.observePeriodEntries()
    val allLogs: LiveData<List<DailyLog>>          = repo.observeAllLogs()
    val todayLog: LiveData<DailyLog?>              = repo.observeTodayLog()
    val todayLogs: LiveData<List<DailyLog>>        = repo.observeTodayLogs()

    // ── SINGLE PREDICTION SOURCE ──────────────────────────────────
    //
    // All cycle/ovulation/fertile-window values exposed to the UI
    // are derived from this one MediatorLiveData.  Nothing calls
    // CycleEngine directly with raw UserSettings anymore.

    private val _prediction = MediatorLiveData<CycleEngine.CyclePrediction?>()
    val prediction: LiveData<CycleEngine.CyclePrediction?> = _prediction

    init {
        fun refresh() = viewModelScope.launch {
            val pred = repo.getBestPrediction() ?: return@launch
            _prediction.postValue(pred)
            val today = LocalDate.now()

            // Only schedule period alarm if the reminder date (D-1) is still in the future
            pred.nextPeriodDate?.let { nextPeriod ->
                if (nextPeriod.minusDays(1).isAfter(today)) {
                    com.example.periodtracker.notification.AlarmScheduler
                        .schedulePeriodAlarms(getApplication(), nextPeriod)
                }
            }

            // Only schedule ovulation alarm if the fertile window start (ovulation-5)
            // is still in the future — prevents sending "fertile window starts today"
            // when the window has already passed
            pred.ovulationDate?.let { ovulation ->
                val fertileStart = ovulation.minusDays(5)
                if (fertileStart.isAfter(today)) {
                    com.example.periodtracker.notification.AlarmScheduler
                        .scheduleOvulationAlarm(getApplication(), ovulation)
                }
            }
        }
        _prediction.addSource(settings)      { refresh() }
        _prediction.addSource(periodEntries) { refresh() }
        _prediction.addSource(allLogs)       { refresh() }
    }

    // ── Derived: cycle basics ─────────────────────────────────────

    val cycleDay: LiveData<Int> = _prediction.map { pred ->
        pred?.let { CycleEngine.cycleDay(it.lastPeriodStart, it.cycleLength) } ?: 1
    }

    val nextPeriodDate: LiveData<LocalDate?> = _prediction.map { it?.nextPeriodDate }
    val ovulationDate: LiveData<LocalDate?>  = _prediction.map { it?.ovulationDate }

    val daysUntilNextPeriod: LiveData<Int> = _prediction.map { pred ->
        pred?.let { CycleEngine.daysUntilNextPeriod(it.lastPeriodStart, it.cycleLength) } ?: 0
    }

    val isPeriodLate: LiveData<Boolean> = _prediction.map { pred ->
        pred?.let { CycleEngine.isPeriodLate(it.lastPeriodStart, it.cycleLength) } ?: false
    }

    val daysLate: LiveData<Int> = _prediction.map { pred ->
        pred?.let { CycleEngine.daysLate(it.lastPeriodStart, it.cycleLength) } ?: 0
    }

    // ── Derived: phase ────────────────────────────────────────────

    val currentPhase: LiveData<CycleEngine.Phase> = MediatorLiveData<CycleEngine.Phase>().apply {
        fun calc() {
            val pred = _prediction.value ?: return
            val dur  = settings.value?.periodDuration ?: 5
            val day  = CycleEngine.cycleDay(pred.lastPeriodStart, pred.cycleLength)
            value    = CycleEngine.phase(day, pred.cycleLength, dur)
        }
        addSource(_prediction) { calc() }
        addSource(settings)    { calc() }
    }

    // ── Derived: fertility ────────────────────────────────────────

    val isFertileToday: LiveData<Boolean> = _prediction.map { pred ->
        pred ?: return@map false
        CycleEngine.isFertile(LocalDate.now(), pred.fertileStart, pred.fertileEnd)
    }

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

    val predictedPeriodDays: LiveData<Set<LocalDate>> = MediatorLiveData<Set<LocalDate>>().apply {
        fun calc() {
            val pred = _prediction.value ?: run { value = emptySet(); return }
            val dur  = settings.value?.periodDuration ?: 5
            value    = CycleEngine.predictedPeriodDays(pred.lastPeriodStart, pred.cycleLength, dur)
        }
        addSource(_prediction) { calc() }
        addSource(settings)    { calc() }
    }

    // ── Derived: confidence ───────────────────────────────────────

    val predictionConfidence: LiveData<Int> = _prediction.map { it?.confidence ?: 0 }

    // ── Period actions ────────────────────────────────────────────

    fun logPeriodStartToday() = viewModelScope.launch { repo.logPeriodStartToday() }

    fun logPeriodStart(date: LocalDate) = viewModelScope.launch { repo.logPeriodStart(date) }

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

    /** Correct a wrong period start date — updates the existing PeriodEntry in place */
    fun updatePeriodStartDate(id: Int, newDate: LocalDate) = viewModelScope.launch {
        val entries = repo.getAllPeriodEntries()
        val entry   = entries.firstOrNull { it.id == id } ?: return@launch
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

    fun updateCycleLength(len: Int) = viewModelScope.launch { repo.updateCycleLength(len) }
    fun updatePeriodDuration(dur: Int) = viewModelScope.launch { repo.updatePeriodDuration(dur) }
    fun updateLastPeriodStart(date: LocalDate) = viewModelScope.launch { repo.updateLastPeriodStart(date) }
    fun updateConditions(c: String) = viewModelScope.launch { repo.updateConditions(c) }

    fun updateProfile(name: String, age: Int, heightCm: Int, weightKg: Float) = viewModelScope.launch {
        val current = repo.getSettings() ?: return@launch
        repo.saveSettings(current.copy(userName = name, age = age, heightCm = heightCm, weightKg = weightKg))
    }

    fun updateActivityLevel(level: String) = viewModelScope.launch {
        val current = repo.getSettings() ?: return@launch
        repo.saveSettings(current.copy(activityLevel = level))
    }

    fun deleteAllData() = viewModelScope.launch {
        com.example.periodtracker.notification.AlarmScheduler.cancelAll(getApplication())
        repo.deleteAllData()
    }
    fun deleteDailyLog(log: DailyLog)    = viewModelScope.launch { repo.deleteDailyLog(log) }
    fun deleteDailyLogById(id: Int)      = viewModelScope.launch { repo.deleteDailyLogById(id) }
    fun updateDailyLog(log: DailyLog)    = viewModelScope.launch { repo.updateDailyLog(log) }
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

    // ── Insights: bar chart data ───────────────────────────────────

    fun getBarChartData(): LiveData<List<Pair<String, Int>>> = periodEntries.map { entries ->
        if (entries.size < 2) return@map emptyList()
        val sorted = entries.sortedBy { it.startDate }
        val fmt    = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
        val result = mutableListOf<Pair<String, Int>>()

        for (i in 0 until sorted.size - 1) {
            val start = LocalDate.parse(sorted[i].startDate)
            val next  = LocalDate.parse(sorted[i + 1].startDate)
            val days  = java.time.temporal.ChronoUnit.DAYS.between(start, next).toInt()
            result.add(Pair(start.format(fmt), days))
        }

        val latest = sorted.lastOrNull()
        if (latest != null && latest.endDate == null) {
            val start = LocalDate.parse(latest.startDate)
            val cur   = java.time.temporal.ChronoUnit.DAYS.between(start, LocalDate.now()).toInt() + 1
            result.add(Pair(start.format(fmt), cur))
        }

        result.takeLast(6)
    }
}

class CycleViewModelFactory(private val app: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CycleViewModel(app) as T
    }
}