package com.aivigil.periodtracker.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

object AlarmScheduler {

    private const val REQ_PERIOD_REMINDER = 100
    private const val REQ_PERIOD_DAY      = 101
    private const val REQ_OVULATION_ALERT = 102
    private const val REQ_DAILY_LOG       = 103

    // ─── public API ───────────────────────────────────────────────

    fun schedulePeriodAlarms(context: Context, nextPeriodDate: LocalDate) {
        val am = alarmManager(context)
        scheduleExact(context, am, dateToMillis(nextPeriodDate.minusDays(1), 9),
            AlarmReceiver.ACTION_PERIOD_REMINDER, REQ_PERIOD_REMINDER)
        scheduleExact(context, am, dateToMillis(nextPeriodDate, 9),
            AlarmReceiver.ACTION_PERIOD_DAY, REQ_PERIOD_DAY)
    }

    fun scheduleOvulationAlarm(context: Context, ovulationDate: LocalDate) {
        scheduleExact(context, alarmManager(context),
            dateToMillis(ovulationDate.minusDays(5), 9),
            AlarmReceiver.ACTION_OVULATION_ALERT, REQ_OVULATION_ALERT)
    }

    fun scheduleDailyLogAlarm(context: Context) {
        val am = alarmManager(context)
        val now = System.currentTimeMillis()
        var trigger = dateToMillis(LocalDate.now(), 20)
        if (trigger <= now) trigger = dateToMillis(LocalDate.now().plusDays(1), 20)

        val pi = PendingIntent.getBroadcast(
            context, REQ_DAILY_LOG,
            Intent(context, AlarmReceiver::class.java).apply { action = AlarmReceiver.ACTION_DAILY_LOG },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !am.canScheduleExactAlarms()
        ) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            Log.w("AlarmScheduler", "scheduleDailyLogAlarm: using inexact fallback")
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
        Log.i("AlarmScheduler", "daily log scheduled at ${java.util.Date(trigger)}")
    }

    fun cancelPeriodAlarms(context: Context) {
        val am = alarmManager(context)
        cancelAlarm(context, am, REQ_PERIOD_REMINDER, AlarmReceiver.ACTION_PERIOD_REMINDER)
        cancelAlarm(context, am, REQ_PERIOD_DAY,      AlarmReceiver.ACTION_PERIOD_DAY)
    }

    fun cancelOvulationAlarm(context: Context) =
        cancelAlarm(context, alarmManager(context),
            REQ_OVULATION_ALERT, AlarmReceiver.ACTION_OVULATION_ALERT)

    fun cancelDailyLogAlarm(context: Context) =
        cancelAlarm(context, alarmManager(context),
            REQ_DAILY_LOG, AlarmReceiver.ACTION_DAILY_LOG)

    fun cancelAll(context: Context) {
        cancelPeriodAlarms(context)
        cancelOvulationAlarm(context)
        cancelDailyLogAlarm(context)
    }

    // ─── core helpers ─────────────────────────────────────────────

    private fun scheduleExact(
        context: Context, am: AlarmManager,
        triggerMillis: Long, action: String, requestCode: Int
    ) {
        if (triggerMillis < System.currentTimeMillis()) {
            Log.w("AlarmScheduler", "past alarm skipped — $action at ${java.util.Date(triggerMillis)}")
            return
        }

        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Use exact when permitted, fall back to inexact (fires within ~15 min)
        // instead of silently returning — previously nothing fired at all
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !am.canScheduleExactAlarms()
        ) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            Log.w("AlarmScheduler", "scheduleExact: exact alarm not permitted — " +
                    "using inexact fallback for action=$action")
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
        Log.i("AlarmScheduler", "scheduleExact: scheduled action=$action " +
                "at=${java.util.Date(triggerMillis)}")
    }

    private fun cancelAlarm(context: Context, am: AlarmManager, requestCode: Int, action: String) {
        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        am.cancel(pi)
        Log.i("AlarmScheduler", "cancelled $action")
    }

    private fun alarmManager(context: Context) =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun dateToMillis(date: LocalDate, hourOfDay: Int): Long =
        date.atTime(hourOfDay, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}