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

    /**
     * Where a tapped notification should take the user.
     *
     * FIX: every notification used a bare `Intent(context, MainActivity)` with no
     * extras, so all of them dropped the user on the Home tab with no indication
     * of why the app had opened. MainActivity even had a handleNotificationIntent()
     * reading a "from_notification" extra that nothing ever set — dead code.
     */
    const val EXTRA_DESTINATION = "notification_destination"
    const val DEST_LOG      = "log"
    const val DEST_CALENDAR = "calendar"
    const val DEST_HOME     = "home"

    /**
     * A PendingIntent that brings the existing task forward rather than stacking a
     * second copy of MainActivity, and tells it which screen to open.
     */
    private fun openAppIntent(context: Context, destination: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DESTINATION, destination)
        }
        return PendingIntent.getActivity(
            context,
            // Distinct request codes per destination so FLAG_UPDATE_CURRENT does
            // not have one notification's extras overwrite another's.
            destination.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

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
        val openIntent = openAppIntent(context, DEST_CALENDAR)
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
        val today = java.time.LocalDate.now().toString()
        val openIntent = openAppIntent(context, DEST_LOG)

        // FIX: the action buttons carry the date the notification was posted for.
        // PeriodConfirmReceiver previously recorded LocalDate.now() whenever the
        // button was tapped, so answering "Yes, started" the next morning logged
        // the period a day late and shifted every later prediction.
        val yesIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, PeriodConfirmReceiver::class.java).apply {
                action = PeriodConfirmReceiver.ACTION_PERIOD_YES
                putExtra(PeriodConfirmReceiver.EXTRA_FOR_DATE, today)
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
            .setContentTitle("Period expected today")
            .setContentText("Did your period start today?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .addAction(0, "Yes, it started", yesIntent)
            .addAction(0, "Not yet", notYetIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_PERIOD, notif)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showOvulationNotification(context: Context) {
        val openIntent = openAppIntent(context, DEST_CALENDAR)
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
        val openIntent = openAppIntent(context, DEST_LOG)
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