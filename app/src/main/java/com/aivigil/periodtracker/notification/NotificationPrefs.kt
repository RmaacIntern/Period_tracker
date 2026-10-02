package com.aivigil.periodtracker.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.time.LocalDate

object NotificationPrefs {

    private const val TAG = "NotificationPrefs"

    private const val PREFS = "reminder_prefs"
    const val KEY_PERIOD    = "period_reminder"
    const val KEY_OVULATION = "ovulation_reminder"
    const val KEY_DAILY     = "daily_reminder"



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

    fun rescheduleFromPrediction(
        context: Context,
        nextPeriod: LocalDate,
        ovulation: LocalDate,
        today: LocalDate = LocalDate.now()
    ) {
        val allowed = canPostNotifications(context)

        AlarmScheduler.cancelPeriodAlarms(context)
        if (allowed && isPeriodEnabled(context)) {
            if (!nextPeriod.isBefore(today)) {
                AlarmScheduler.schedulePeriodAlarms(context, nextPeriod)
            } else {
                Log.d(TAG, "period alarms: $nextPeriod already passed — not scheduling")
            }
        } else {
            Log.d(TAG, "period alarms: disabled (pref=${isPeriodEnabled(context)} canPost=$allowed)")
        }

        AlarmScheduler.cancelOvulationAlarm(context)
        if (allowed && isOvulationEnabled(context)) {
            val fertileStart = ovulation.minusDays(5)
            if (!fertileStart.isBefore(today)) {
                AlarmScheduler.scheduleOvulationAlarm(context, ovulation)
            } else {
                Log.d(TAG, "ovulation alarm: fertile window $fertileStart already started — not scheduling")
            }
        } else {
            Log.d(TAG, "ovulation alarm: disabled (pref=${isOvulationEnabled(context)} canPost=$allowed)")
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DAILY LOG REMINDER (AlarmManager)
    // ─────────────────────────────────────────────────────────────

    fun syncDailyReminder(context: Context) {
        if (isDailyEnabled(context) && canPostNotifications(context)) {
            AlarmScheduler.scheduleDailyLogAlarm(context)
            Log.d(TAG, "daily reminder: scheduled via AlarmManager at 20:00")
        } else {
            AlarmScheduler.cancelDailyLogAlarm(context)
            Log.d(TAG, "daily reminder: cancelled (pref=${isDailyEnabled(context)})")
        }
    }



}