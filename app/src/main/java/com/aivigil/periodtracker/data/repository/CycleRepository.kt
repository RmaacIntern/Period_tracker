package com.aivigil.periodtracker.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.LiveData
import com.aivigil.periodtracker.data.dao.DailyLogDao
import com.aivigil.periodtracker.data.dao.PeriodEntryDao
import com.aivigil.periodtracker.data.dao.UserSettingsDao
import com.aivigil.periodtracker.data.db.AppDatabase
import com.aivigil.periodtracker.data.entity.DailyLog
import com.aivigil.periodtracker.data.entity.PeriodEntry
import com.aivigil.periodtracker.data.entity.UserSettings
import com.aivigil.periodtracker.domain.CycleEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Context.dataStore by preferencesDataStore(name = "prefs")

class CycleRepository(context: Context) {

    private val TAG = "CycleRepository"

    private val db         = AppDatabase.getInstance(context)
    private val settingsDao: UserSettingsDao = db.userSettingsDao()
    private val periodDao: PeriodEntryDao    = db.periodEntryDao()
    private val logDao: DailyLogDao          = db.dailyLogDao()
    private val ds                           = context.dataStore

    companion object {
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        /** Closed periods required before the app will adjust a user-set duration. */
        private const val MIN_CYCLES_BEFORE_LEARNING = 3

        @Volatile private var INSTANCE: CycleRepository? = null

        fun getInstance(context: Context): CycleRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CycleRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── Onboarding ────────────────────────────────────────────────

    val isOnboardingComplete: Flow<Boolean> =
        ds.data.map { it[KEY_ONBOARDING_DONE] ?: false }

    suspend fun markOnboardingComplete() {
        ds.edit { it[KEY_ONBOARDING_DONE] = true }
    }

    /**
     * Writes the complete onboarding result and only then marks onboarding done.
     *
     * Ordering matters and is the point of this function: `markOnboardingComplete()`
     * is what stops SplashActivity sending the user back through the flow, so it
     * must be the LAST thing that happens. If any earlier write throws, the flag
     * is never set and the user is asked to complete onboarding again — annoying,
     * but recoverable, unlike an app that opens with no settings at all.
     *
     * Callers should invoke this from a non-cancellable context; see
     * OnboardingActivity.persistOnboarding().
     */
    suspend fun saveOnboarding(
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
    ) {
        val safeStart = if (lastPeriodStart.isAfter(LocalDate.now())) LocalDate.now()
        else lastPeriodStart

        settingsDao.save(
            UserSettings(
                id              = 1,
                userName        = userName,
                age             = age.coerceIn(10, 70),
                heightCm        = heightCm.coerceIn(100, 220),
                weightKg        = weightKg.coerceIn(25f, 250f),
                activityLevel   = activityLevel,
                goal            = goal,
                conditions      = conditions,
                cycleLength     = cycleLength.coerceIn(18, 60),
                periodDuration  = periodDuration.coerceIn(2, 10),
                lastPeriodStart = safeStart.toString(),
                memberSince     = LocalDate.now().toString()
            )
        )
        logPeriodStart(safeStart)
        markOnboardingComplete()
        Log.i(TAG, "saveOnboarding: complete — lastPeriodStart=$safeStart " +
                "cycleLength=$cycleLength periodDuration=$periodDuration")
    }

    // ── User settings ─────────────────────────────────────────────

    fun observeSettings(): LiveData<UserSettings?> = settingsDao.observe()
    suspend fun getSettings(): UserSettings?        = settingsDao.get()
    suspend fun saveSettings(s: UserSettings)       = settingsDao.save(s)
    suspend fun updateCycleLength(len: Int)         = settingsDao.updateCycleLength(len)
    suspend fun updatePeriodDuration(dur: Int)      = settingsDao.updatePeriodDuration(dur)
    suspend fun updateConditions(conditions: String) = settingsDao.updateConditions(conditions)

    suspend fun updateProfile(name: String, age: Int, heightCm: Int, weightKg: Float) {
        val s = settingsDao.get() ?: return
        settingsDao.save(s.copy(userName = name, age = age, heightCm = heightCm, weightKg = weightKg))
        Log.i(TAG, "updateProfile: name=$name age=$age height=$heightCm weight=$weightKg")
    }

    /**
     * PRIVATE — only syncs UserSettings.lastPeriodStart.
     * Never touches period_entries. Used internally by logPeriodStart()
     * and getBestPrediction() to avoid triggering LiveData loops.
     */
    private suspend fun syncSettingsLastPeriodStart(dateStr: String) {
        settingsDao.updateLastPeriodStart(dateStr)
        Log.d(TAG, "syncSettingsLastPeriodStart: $dateStr")
    }

    /**
     * PUBLIC — called only when the user explicitly corrects their
     * last period date from the Profile/Settings screen.
     * Updates settings AND moves the latest period entry's startDate.
     * Never creates a new period entry.
     */
    suspend fun updateLastPeriodStart(date: LocalDate) {
        val dateStr = date.toString()
        Log.i(TAG, "updateLastPeriodStart: user correction → $dateStr")

        // Update settings
        settingsDao.updateLastPeriodStart(dateStr)

        // Move the latest period entry's startDate to match
        // Only updates — never creates a new entry
        val latest = periodDao.getLatest()
        if (latest != null) {
            periodDao.update(latest.copy(startDate = dateStr))
            Log.i(TAG, "updateLastPeriodStart: moved latest period entry ${latest.startDate} → $dateStr")
        } else {
            Log.w(TAG, "updateLastPeriodStart: no period entries exist — settings updated only")
        }
    }

    // ── Period entries ────────────────────────────────────────────

    fun observePeriodEntries(): LiveData<List<PeriodEntry>> = periodDao.observeAll()
    suspend fun getPeriodEntries(): List<PeriodEntry>       = periodDao.getAll()

    // ✅ NEW — fetch a single PeriodEntry by id.
    // Used by CycleViewModel.updatePeriodStartDate() to avoid
    // loading all entries just to find one.
    // Requires PeriodEntryDao.getById(id) — see note below.
    suspend fun getPeriodEntryById(id: Int): PeriodEntry? = periodDao.getById(id)

    /**
     * Confirms and records a new period start.
     *
     * Guards:
     * – Duplicate date: skips silently and returns existing entry.
     * – Closes any open previous period entry.
     * – Syncs UserSettings.lastPeriodStart via private sync (no LiveData loop).
     */
    suspend fun logPeriodStart(requestedDate: LocalDate = LocalDate.now()): PeriodEntry {
        // FIX: a future date corrupts every downstream calculation —
        // CycleEngine.cycleDay() coerces to 1, isPeriodLate() can never become
        // true, and daysUntilNextPeriod() reports roughly two cycles. The
        // calendar's "Log Period Start" path allowed this. A period cannot start
        // in the future, so clamp to today at the last line of defence.
        val date = if (requestedDate.isAfter(LocalDate.now())) {
            Log.w(TAG, "logPeriodStart: $requestedDate is in the future — clamping to today")
            LocalDate.now()
        } else requestedDate

        val dateStr = date.toString()

        // Guard: no duplicate for same date
        periodDao.getByDate(dateStr)?.let {
            Log.w(TAG, "logPeriodStart: already exists for $date — skipping")
            return it
        }

        val latest = periodDao.getLatest()
        Log.d(TAG, "logPeriodStart: prev=${latest?.startDate} new=$date")

        val actualCycleLength: Int? = latest?.let {
            val prevStart = LocalDate.parse(it.startDate)
            if (prevStart == date) return@let null
            val days = ChronoUnit.DAYS.between(prevStart, date).toInt()
            days.takeIf { d -> d in 18..60 }
        }

        if (latest != null && latest.endDate == null) {
            val prevStart = LocalDate.parse(latest.startDate)
            val endDate   = date.minusDays(1)
            if (!endDate.isBefore(prevStart) && prevStart != date) {
                periodDao.update(latest.copy(endDate = endDate.toString()))
                Log.d(TAG, "logPeriodStart: closed ${latest.startDate} → $endDate")
            }
        }

        // ✅ try-catch handles rare concurrent duplicate inserts
        return try {
            val entry = PeriodEntry(
                startDate   = dateStr,
                endDate     = null,
                cycleLength = actualCycleLength
            )
            val id = periodDao.insert(entry).toInt()
            Log.i(TAG, "logPeriodStart: inserted id=$id date=$dateStr cycleLen=$actualCycleLength")
            syncSettingsLastPeriodStart(dateStr)
            entry.copy(id = id)
        } catch (e: Exception) {
            Log.w(TAG, "logPeriodStart: insert failed — likely concurrent duplicate for $dateStr")
            periodDao.getByDate(dateStr) ?: throw e
        }
    }

    suspend fun logPeriodStartToday(): PeriodEntry = logPeriodStart(LocalDate.now())

    suspend fun deletePeriodEntry(id: Int)              = periodDao.deleteById(id)
    suspend fun getAllPeriodEntries(): List<PeriodEntry> = periodDao.getAll()
    suspend fun updatePeriodEntry(entry: PeriodEntry)   = periodDao.update(entry)

    /**
     * Marks the current open PeriodEntry as ended on [date].
     * Recalculates average periodDuration from all confirmed periods.
     */
    suspend fun logPeriodEnd(date: LocalDate = LocalDate.now()): Boolean {
        val open = periodDao.getLatest()
        if (open == null || open.endDate != null) {
            Log.w(TAG, "logPeriodEnd: no open period to close")
            return false
        }
        val startDate = LocalDate.parse(open.startDate)
        if (date.isBefore(startDate)) {
            Log.e(TAG, "logPeriodEnd: endDate $date is before startDate $startDate — rejected")
            return false
        }
        periodDao.update(open.copy(endDate = date.toString()))
        Log.i(TAG, "logPeriodEnd: closed ${open.startDate} → $date")

        // Learn the average period duration from observed history.
        //
        // FIX: this used to overwrite settings.periodDuration from a SINGLE
        // closed period. A user who set 5 days in onboarding and whose first
        // logged period ran 3 days had her setting silently rewritten to 3,
        // which shifted every ovulation and fertile-window date — with no way to
        // discover what happened or get the original value back.
        //
        // Now it needs at least 3 observed periods before it will adjust a
        // user-entered value, and it never moves by more than 1 day at a time.
        val allDurations = periodDao.getAll()
            .filter { it.endDate != null }
            .mapNotNull { entry ->
                val s   = parseDateOrNull(entry.startDate) ?: return@mapNotNull null
                val e   = parseDateOrNull(entry.endDate)   ?: return@mapNotNull null
                if (e.isBefore(s)) return@mapNotNull null
                val dur = ChronoUnit.DAYS.between(s, e).toInt() + 1
                dur.takeIf { it in 2..10 }
            }

        if (allDurations.size >= MIN_CYCLES_BEFORE_LEARNING) {
            val observed = allDurations.takeLast(6).average()
                .let { Math.round(it).toInt() }
                .coerceIn(2, 10)
            val current = settingsDao.get()?.periodDuration ?: observed
            if (observed != current) {
                // Move one day at a time so a single unusual period cannot swing
                // the whole prediction model.
                val next = (current + (observed - current).coerceIn(-1, 1)).coerceIn(2, 10)
                settingsDao.updatePeriodDuration(next)
                Log.i(TAG, "logPeriodEnd: periodDuration $current → $next " +
                        "(observed avg $observed from ${allDurations.size} periods)")
            }
        } else {
            Log.d(TAG, "logPeriodEnd: only ${allDurations.size} closed periods — " +
                    "keeping the user's periodDuration setting untouched")
        }
        return true
    }

    /** Returns true if there is an open PeriodEntry (endDate == null). */
    suspend fun isCurrentlyOnPeriod(): Boolean {
        val latest = periodDao.getLatest() ?: return false
        return latest.endDate == null
    }

    // ── Daily logs ────────────────────────────────────────────────

    fun observeAllLogs(): LiveData<List<DailyLog>> = logDao.observeAll()

    /**
     * FIX (midnight staleness): these used to call LocalDate.now() at the moment
     * the repository singleton wired them up — once per process. The resulting
     * LiveData was permanently bound to that one date string, so an app left
     * open past midnight kept showing YESTERDAY's log as "today", and a log
     * saved after midnight never appeared on Home.
     *
     * The date is now a parameter. CycleViewModel re-binds these through a
     * switchMap whenever its `today` trigger advances.
     */
    fun observeLogFor(date: LocalDate): LiveData<DailyLog?> =
        logDao.observeLatestByDate(date.toString())

    fun observeLogsFor(date: LocalDate): LiveData<List<DailyLog>> =
        logDao.observeByDate(date.toString())

    suspend fun getLogForDate(date: LocalDate): List<DailyLog> = logDao.getByDate(date.toString())

    /**
     * Saves a daily log entry.
     *
     * Period creation is EXPLICIT — caller sets [periodConfirmed] = true
     * only after the user answers "Yes, period started" in the dialog.
     * This function NEVER auto-creates a period.
     */
    suspend fun saveDailyLog(
        date: LocalDate = LocalDate.now(),
        flow: String,
        moods: List<String>,
        symptoms: List<String>,
        cervicalFluid: String,
        basalTemp: Float?,
        lhTestResult: String = "Not Tested",
        notes: String,
        periodConfirmed: Boolean = false
    ) {
        val existingCount = logDao.countByDate(date.toString())
        val entryNumber   = existingCount + 1

        val log = DailyLog(
            date          = date.toString(),
            entryNumber   = entryNumber,
            flow          = flow,
            moods         = moods.joinToString(","),
            symptoms      = symptoms.joinToString(","),
            cervicalFluid = cervicalFluid,
            basalTemp     = basalTemp,
            lhTestResult  = lhTestResult,
            notes         = notes
        )
        logDao.insert(log)
        Log.i(TAG, "saveDailyLog: date=$date entry=#$entryNumber flow=$flow " +
                "moods=${log.moods} symptoms=${log.symptoms} cervical=$cervicalFluid " +
                "bbt=$basalTemp lh=$lhTestResult confirmed=$periodConfirmed")

        if (periodConfirmed) {
            Log.i(TAG, "saveDailyLog: periodConfirmed=true → logPeriodStart($date)")
            logPeriodStart(date)
        } else {
            Log.d(TAG, "saveDailyLog: periodConfirmed=false → no PeriodEntry created")
        }
    }

    // ── Prediction (single source of truth for ViewModel) ─────────

    /**
     * Builds the canonical CyclePrediction.
     *
     * Period anchor: latest confirmed PeriodEntry.startDate (authoritative).
     * Cycle length:  always from UserSettings (user controls this).
     *
     * If settings and period history are out of sync, syncs settings ONLY
     * via private syncSettingsLastPeriodStart() — never touches period_entries,
     * never triggers a LiveData loop.
     */
    suspend fun getBestPrediction(): CycleEngine.CyclePrediction? {
        val settings = settingsDao.get() ?: run {
            Log.e(TAG, "getBestPrediction: settings is null — returning null")
            return null
        }

        val latestPeriodStart = periodDao.getLatest()
            ?.startDate
            ?.let { LocalDate.parse(it) }
            ?: LocalDate.parse(settings.lastPeriodStart)

        // ✅ Sync settings only — never touch period_entries here
        if (latestPeriodStart.toString() != settings.lastPeriodStart) {
            Log.w(TAG, "getBestPrediction: mismatch — " +
                    "periodEntry=$latestPeriodStart settings=${settings.lastPeriodStart} " +
                    "— syncing settings only")
            syncSettingsLastPeriodStart(latestPeriodStart.toString())
        }

        // ── Bio signals ───────────────────────────────────────────
        // FIX: the BBT / LH / cervical engine in CycleEngine was fully built but
        // never called from anywhere, so every prediction fell back to the
        // formula and confidence was permanently pinned at 70 / "Formula".
        // Only logs from the CURRENT cycle are considered — a positive LH test
        // from two cycles ago must not move this cycle's ovulation date.
        val cycleLogs = logDao.getLogsOnOrAfter(latestPeriodStart.toString())

        // Dedupe by date, keeping the most recent entry per day, so a user who
        // logs twice in one day cannot destabilise the BBT baseline window.
        val latestPerDay = cycleLogs
            .groupBy { it.date }
            .mapNotNull { (_, sameDay) -> sameDay.maxByOrNull { it.loggedAt } }

        val bbtLogs = latestPerDay.mapNotNull { log ->
            val temp = log.basalTemp ?: return@mapNotNull null
            parseDateOrNull(log.date)?.let { CycleEngine.BbtLog(it, temp) }
        }
        val lhLogs = latestPerDay.mapNotNull { log ->
            if (log.lhTestResult.isBlank() || log.lhTestResult == "Not Tested") return@mapNotNull null
            parseDateOrNull(log.date)?.let { CycleEngine.LhLog(it, log.lhTestResult) }
        }
        val cervicalLogs = latestPerDay.mapNotNull { log ->
            if (log.cervicalFluid.isBlank()) return@mapNotNull null
            parseDateOrNull(log.date)?.let { it to log.cervicalFluid }
        }

        val bbtOvulation   = CycleEngine.detectOvulationFromBBT(bbtLogs)
        val lhSignal       = CycleEngine.lhEvidence(lhLogs)
        val cervicalSignal = CycleEngine.cervicalFertilityScore(cervicalLogs)

        return CycleEngine.bestPrediction(
            lastPeriodStart = latestPeriodStart,
            avgCycleLength  = settings.cycleLength,
            periodDuration  = settings.periodDuration,
            bbtOvulation    = bbtOvulation,
            lhSignal        = lhSignal,
            cervicalSignal  = cervicalSignal
        ).also {
            Log.i(TAG, "getBestPrediction: lastPeriod=$latestPeriodStart " +
                    "cycleLen=${settings.cycleLength} nextPeriod=${it.nextPeriodDate} " +
                    "ovulation=${it.ovulationDate} fertile=${it.fertileStart}→${it.fertileEnd} " +
                    "confidence=${it.confidence} source=${it.dataSource} " +
                    "(bbt=${bbtLogs.size} lh=${lhLogs.size} cf=${cervicalLogs.size} logs)")
        }
    }

    /**
     * Never throws. UserSettings.lastPeriodStart and DailyLog.date are plain
     * Strings with no DB-level format constraint, so a malformed value must not
     * take down a prediction or crash a list bind.
     */
    private fun parseDateOrNull(raw: String?): LocalDate? =
        raw?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // ── Delete all ────────────────────────────────────────────────

    suspend fun deleteAllData() {
        db.dailyLogDao().deleteAll()
        db.periodEntryDao().deleteAll()
        db.userSettingsDao().deleteAll()
        ds.edit { it[KEY_ONBOARDING_DONE] = false }
        Log.i(TAG, "deleteAllData: all data wiped")
    }

    suspend fun deleteDailyLog(log: DailyLog) = logDao.delete(log)
    suspend fun deleteDailyLogById(id: Int)   = logDao.deleteById(id)
    suspend fun updateDailyLog(log: DailyLog) = logDao.update(log)

    /** Backs the Undo action after a log deletion in the history screen. */
    suspend fun restoreDailyLog(log: DailyLog) {
        logDao.insert(log)
        Log.i(TAG, "restoreDailyLog: restored entry for ${log.date}")
    }
}