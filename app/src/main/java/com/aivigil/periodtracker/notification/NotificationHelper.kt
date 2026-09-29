package com.aivigil.periodtracker.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aivigil.periodtracker.MainActivity
import com.aivigil.periodtracker.R

object NotificationHelper {

    const val CHANNEL_PERIOD    = "period_reminder"
    const val CHANNEL_OVULATION = "ovulation_alert"
    const val CHANNEL_DAILY_LOG = "daily_log"

    const val NOTIF_ID_PERIOD    = 1001
    const val NOTIF_ID_OVULATION = 1002
    const val NOTIF_ID_DAILY_LOG = 1003

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_PERIOD, "Period Reminder",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Reminds you when your period is approaching" })

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_OVULATION, "Ovulation Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Alerts you about your fertile window" })

        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_DAILY_LOG, "Daily Log Reminder",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Daily reminder to log your symptoms" })
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showPeriodReminderNotification(context: Context) {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_PERIOD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Period Expected Tomorrow")
            .setContentText("Your period is due tomorrow. Tap to open Period Tracker.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_PERIOD, notif)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showPeriodStartedNotification(context: Context) {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val yesIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, PeriodConfirmReceiver::class.java).apply {
                action = PeriodConfirmReceiver.ACTION_PERIOD_YES
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notYetIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, PeriodConfirmReceiver::class.java).apply {
                action = PeriodConfirmReceiver.ACTION_PERIOD_NOT_YET
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_PERIOD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Period Day 🔴")
            .setContentText("Did your period start today?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .addAction(0, "✓ Yes, started", yesIntent)
            .addAction(0, "Not yet", notYetIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_PERIOD, notif)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showOvulationNotification(context: Context) {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_OVULATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Fertile Window Starting 🟡")
            .setContentText("Your fertile window starts today. Best days to conceive ahead.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_OVULATION, notif)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showDailyLogNotification(context: Context) {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_DAILY_LOG)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Daily Log 📝")
            .setContentText("Don't forget to log your symptoms today.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_DAILY_LOG, notif)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showPeriodLoggedConfirmation(context: Context) {
        val notif = NotificationCompat.Builder(context, CHANNEL_PERIOD)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Period Logged ✓")
            .setContentText("Your cycle has been updated.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_PERIOD + 10, notif)
    }
}