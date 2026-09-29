package com.aivigil.periodtracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

import com.aivigil.periodtracker.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("BootReceiver", "BOOT_COMPLETED — rescheduling alarms")

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db       = AppDatabase.getInstance(context)
                val settings = db.userSettingsDao().get() ?: run {
                    Log.w("BootReceiver", "settings null — skipping alarm reschedule")
                    return@launch
                }

                val lastPeriod   = LocalDate.parse(settings.lastPeriodStart)
                val nextPeriod   = lastPeriod.plusDays(settings.cycleLength.toLong())
                val ovulation    = nextPeriod.minusDays(14)
                val fertileStart = ovulation.minusDays(5)
                val today        = LocalDate.now()

                Log.d("BootReceiver", "nextPeriod=$nextPeriod ovulation=$ovulation fertileStart=$fertileStart")

                if (nextPeriod.isAfter(today)) {
                    AlarmScheduler.schedulePeriodAlarms(context, nextPeriod)
                    Log.i("BootReceiver", "period alarms rescheduled for $nextPeriod")
                } else {
                    Log.w("BootReceiver", "nextPeriod=$nextPeriod is in the past — skipping")
                }

                if (fertileStart.isAfter(today)) {
                    AlarmScheduler.scheduleOvulationAlarm(context, ovulation)
                    Log.i("BootReceiver", "ovulation alarm rescheduled for $ovulation")
                } else {
                    Log.w("BootReceiver", "fertileStart=$fertileStart is in the past — skipping")
                }

            } catch (e: Exception) {
                Log.e("BootReceiver", "error rescheduling alarms", e)
            } finally {
                result.finish()
            }
        }
    }
}