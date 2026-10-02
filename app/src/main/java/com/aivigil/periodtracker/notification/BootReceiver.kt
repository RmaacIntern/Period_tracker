package com.aivigil.periodtracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aivigil.periodtracker.data.repository.CycleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Re-arms alarms after events that invalidate them.
 *
 * Exact alarms do not survive a reboot, and their absolute trigger times become
 * wrong when the device's timezone changes, so both are handled here.
 */
class BootReceiver : BroadcastReceiver() {

    private companion object { const val TAG = "BootReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.MY_PACKAGE_REPLACED" &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_DATE_CHANGED
        ) return

        Log.i(TAG, "$action — rescheduling alarms")

        val appContext = context.applicationContext
        val result = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prediction = CycleRepository.getInstance(appContext).getBestPrediction()
                if (prediction == null) {
                    Log.w(TAG, "no prediction available — nothing to reschedule")
                    // Still reschedule daily reminder even with no prediction
                    NotificationPrefs.syncDailyReminder(appContext)
                    return@launch
                }

                NotificationPrefs.rescheduleFromPrediction(
                    context    = appContext,
                    nextPeriod = prediction.nextPeriodDate,
                    ovulation  = prediction.ovulationDate,
                    today      = LocalDate.now()
                )
                NotificationPrefs.syncDailyReminder(appContext)

                Log.i(TAG, "rescheduled — nextPeriod=${prediction.nextPeriodDate} " +
                        "ovulation=${prediction.ovulationDate}")
            } catch (e: Exception) {
                Log.e(TAG, "error rescheduling alarms", e)
            } finally {
                result.finish()
            }
        }
    }
}