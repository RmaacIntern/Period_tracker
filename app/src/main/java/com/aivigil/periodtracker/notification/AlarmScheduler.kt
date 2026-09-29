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

    private const val REQ_PERIOD_REMINDER  = 100
    private const val REQ_PERIOD_DAY       = 101
    private const val REQ_OVULATION_ALERT  = 102

    // Call this whenever prediction updates in CycleViewModel
    fun schedulePeriodAlarms(context: Context, nextPeriodDate: LocalDate) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // D-1 at 9am
        val reminderDate = nextPeriodDate.minusDays(1)
        scheduleExact(
            context, am,
            dateToMillis(reminderDate, 9),
            AlarmReceiver.ACTION_PERIOD_REMINDER,
            REQ_PERIOD_REMINDER
        )

        // D-0 at 9am
        scheduleExact(
            context, am,
            dateToMillis(nextPeriodDate, 9),
            AlarmReceiver.ACTION_PERIOD_DAY,
            REQ_PERIOD_DAY
        )
    }

    fun scheduleOvulationAlarm(context: Context, ovulationDate: LocalDate) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Fire at the start of the fertile window (ovulation - 5 days) at 9am
        // so the notification says "Your fertile window starts today"
        val alertDate = ovulationDate.minusDays(5)
        scheduleExact(
            context, am,
            dateToMillis(alertDate, 9),
            AlarmReceiver.ACTION_OVULATION_ALERT,
            REQ_OVULATION_ALERT
        )
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(
            REQ_PERIOD_REMINDER to AlarmReceiver.ACTION_PERIOD_REMINDER,
            REQ_PERIOD_DAY      to AlarmReceiver.ACTION_PERIOD_DAY,
            REQ_OVULATION_ALERT to AlarmReceiver.ACTION_OVULATION_ALERT
        ).forEach { (req, action) ->
            val pi = PendingIntent.getBroadcast(
                context, req,
                Intent(context, AlarmReceiver::class.java).apply { this.action = action },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { am.cancel(it) }
        }
    }

    // FIX — add to scheduleExact()
    private fun scheduleExact(
        context: Context, am: AlarmManager,
        triggerMillis: Long, action: String, requestCode: Int
    ) {
        // ✅ Skip past alarms — no point scheduling something already missed
        if (triggerMillis < System.currentTimeMillis()) {
            Log.w("AlarmScheduler", "scheduleExact: skipping past alarm — action=$action " +
                    "time=${java.util.Date(triggerMillis)}")
            return
        }

        // ✅ Android 12+ requires SCHEDULE_EXACT_ALARM permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!am.canScheduleExactAlarms()) {
                Log.w("AlarmScheduler", "scheduleExact: canScheduleExactAlarms=false — skipping")
                return
            }
        }

        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, AlarmReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        Log.i("AlarmScheduler", "scheduleExact: scheduled action=$action " +
                "at=${java.util.Date(triggerMillis)}")
    }
    private fun dateToMillis(date: LocalDate, hourOfDay: Int): Long {
        return date.atTime(hourOfDay, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
    fun cancelPeriodAlarms(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(
            REQ_PERIOD_REMINDER to AlarmReceiver.ACTION_PERIOD_REMINDER,
            REQ_PERIOD_DAY      to AlarmReceiver.ACTION_PERIOD_DAY
        ).forEach { (req, action) ->
            val pi = PendingIntent.getBroadcast(
                context, req,
                Intent(context, AlarmReceiver::class.java).apply { this.action = action },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pi?.let { am.cancel(it) }
        }
    }

    fun cancelOvulationAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, REQ_OVULATION_ALERT,
            Intent(context, AlarmReceiver::class.java).apply {
                this.action = AlarmReceiver.ACTION_OVULATION_ALERT
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { am.cancel(it) }
    }
}