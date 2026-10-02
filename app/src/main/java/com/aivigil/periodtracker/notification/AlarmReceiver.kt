package com.aivigil.periodtracker.notification

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PERIOD_REMINDER  = "com.aivigil.periodtracker.PERIOD_REMINDER"
        const val ACTION_PERIOD_DAY       = "com.aivigil.periodtracker.PERIOD_DAY"
        const val ACTION_OVULATION_ALERT  = "com.aivigil.periodtracker.OVULATION_ALERT"
        const val ACTION_DAILY_LOG        = "com.aivigil.periodtracker.ACTION_DAILY_LOG"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w("AlarmReceiver", "POST_NOTIFICATIONS not granted — skipping")
                return
            }
        }
        when (intent.action) {
            ACTION_PERIOD_REMINDER -> NotificationHelper.showPeriodReminderNotification(context)
            ACTION_PERIOD_DAY      -> NotificationHelper.showPeriodStartedNotification(context)
            ACTION_OVULATION_ALERT -> NotificationHelper.showOvulationNotification(context)
            ACTION_DAILY_LOG       -> {
                NotificationHelper.showDailyLogNotification(context)
                // Exact alarms fire once — reschedule for next day 8pm
                AlarmScheduler.scheduleDailyLogAlarm(context)
            }
        }
    }
}