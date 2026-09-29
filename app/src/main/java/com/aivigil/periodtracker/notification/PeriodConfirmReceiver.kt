package com.aivigil.periodtracker.notification

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.aivigil.periodtracker.data.repository.CycleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class PeriodConfirmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PERIOD_YES     = "com.aivigil.periodtracker.PERIOD_YES"
        const val ACTION_PERIOD_NOT_YET = "com.aivigil.periodtracker.PERIOD_NOT_YET"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            ACTION_PERIOD_YES -> {
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Use CycleRepository so all period logic runs:
                        // closes previous period, calculates cycle length,
                        // updates UserSettings, recalculates average
                        CycleRepository.getInstance(context)
                            .logPeriodStart(LocalDate.now())

                        NotificationManagerCompat.from(context)
                            .cancel(NotificationHelper.NOTIF_ID_PERIOD)

                        NotificationHelper.showPeriodLoggedConfirmation(context)
                    } finally {
                        result.finish()
                    }
                }
            }

            ACTION_PERIOD_YES -> {
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        CycleRepository.getInstance(context).logPeriodStart(LocalDate.now())
                        NotificationManagerCompat.from(context).cancel(NotificationHelper.NOTIF_ID_PERIOD)

                        // ✅ Check permission before showing confirmation
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {
                            NotificationHelper.showPeriodLoggedConfirmation(context)
                        }
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }
}