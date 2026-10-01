package com.aivigil.periodtracker.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for whether a reminder is allowed to be scheduled.
 *
 * WHY THIS EXISTS
 * ---------------
 * The reminder switches in ProfileFragment wrote to SharedPreferences and
 * cancelled their alarms, but nothing else in the app ever read those keys:
 *
 *   • CycleViewModel.refresh() called AlarmScheduler.schedulePeriodAlarms() and
 *     scheduleOvulationAlarm() unconditionally on every settings / period / log
 *     change, re-arming alarms the user had just turned off.
 *   • MainActivity.onCreate() re-enqueued the daily-log WorkManager job
 *     unconditionally on every launch, undoing a cancelled daily reminder.
 *
 * The switches were therefore decorative: turning a reminder off held only until
 * the next app launch or the next database write. Every scheduling path now goes
 * through this object, so the stored preference is actually authoritative.
 */
object NotificationPrefs {

    private const val TAG = "NotificationPrefs"

    private const val PREFS = "reminder_prefs"
    const val KEY_PERIOD    = "period_reminder"
    const val KEY_OVULATION = "ovulation_reminder"
    const val KEY_DAILY     = "daily_reminder"

    const val DAILY_WORK_NAME = "daily_log_reminder"

    /** Hour of day (24h) reminders fire at. */
    private const val REMINDER_HOUR = 9
    private const val DAILY_LOG_HOUR = 20

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPeriodEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PERIOD, true)

    fun isOvulationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVULATION, true)

    fun isDailyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DAILY, true)

    fun setPeriodEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_PERIOD, enabled).apply()

    fun setOvulationEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_OVULATION, enabled).apply()

    fun setDailyEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_DAILY, enabled).apply()

    /**
     * True when the OS will actually deliver a notification. On Android 13+ this
     * requires POST_NOTIFICATIONS; on every version the user can disable the
     * app's notifications from system settings.
     *
     * Checked before scheduling so the app does not burn exact-alarm budget on
     * notifications that would be silently dropped.
     */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return false
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // ─────────────────────────────────────────────────────────────
    // PREDICTION-DRIVEN ALARMS
    // ─────────────────────────────────────────────────────────────

    /**
     * Re-arms the period and ovulation alarms to match a fresh prediction,
     * honouring both the user's switches and the OS permission state.
     *
     * Always cancels first so a stale alarm from an earlier prediction can never
     * survive a cycle-length edit or a corrected period date.
     */
    fun rescheduleFromPrediction(
        context: Context,
        nextPeriod: LocalDate,
        ovulation: LocalDate,
        today: LocalDate = LocalDate.now()
    ) {
        val allowed = canPostNotifications(context)

        // ── Period reminder (D-1) + period day (D-0) ──────────────
        AlarmScheduler.cancelPeriodAlarms(context)
        if (allowed && isPeriodEnabled(context)) {
            // Schedule if EITHER the D-1 reminder or the D-0 notification is
            // still ahead of us. The old code required D-1 to be in the future,
            // which silently dropped the "period due today" notification for
            // anyone opening the app on the day before their period.
            if (!nextPeriod.isBefore(today)) {
                AlarmScheduler.schedulePeriodAlarms(context, nextPeriod)
            } else {
                Log.d(TAG, "period alarms: $nextPeriod already passed — not scheduling")
            }
        } else {
            Log.d(TAG, "period alarms: disabled (pref=${isPeriodEnabled(context)} " +
                    "canPost=$allowed)")
        }

        // ── Fertile window start (ovulation − 5) ──────────────────
        AlarmScheduler.cancelOvulationAlarm(context)
        if (allowed && isOvulationEnabled(context)) {
            val fertileStart = ovulation.minusDays(5)
            if (!fertileStart.isBefore(today)) {
                AlarmScheduler.scheduleOvulationAlarm(context, ovulation)
            } else {
                Log.d(TAG, "ovulation alarm: fertile window $fertileStart already " +
                        "started — not scheduling")
            }
        } else {
            Log.d(TAG, "ovulation alarm: disabled (pref=${isOvulationEnabled(context)} " +
                    "canPost=$allowed)")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DAILY LOG REMINDER (WorkManager)
    // ─────────────────────────────────────────────────────────────

    /**
     * Enqueues or cancels the daily-log reminder to match the stored preference.
     *
     * Safe to call on every launch: with the preference ON and the work already
     * enqueued, KEEP makes this a no-op; with the preference OFF it cancels
     * instead of re-enqueueing, which is what MainActivity used to get wrong.
     */
    fun syncDailyReminder(context: Context) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (isDailyEnabled(context) && canPostNotifications(context)) {
            val request = PeriodicWorkRequestBuilder<DailyLogReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(millisUntilHour(DAILY_LOG_HOUR), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(DAILY_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            Log.d(TAG, "daily reminder: enqueued (KEEP)")
        } else {
            wm.cancelUniqueWork(DAILY_WORK_NAME)
            Log.d(TAG, "daily reminder: cancelled (pref=${isDailyEnabled(context)})")
        }
    }

    /**
     * Milliseconds from now until the next occurrence of [hour] local time.
     * Recomputed at call time, so it follows the device's current timezone.
     */
    private fun millisUntilHour(hour: Int): Long {
        val now = LocalDateTime.now()
        var target = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(0)
    }

    /** Exposed for AlarmScheduler so the reminder hour lives in one place. */
    fun reminderHour(): Int = REMINDER_HOUR
}