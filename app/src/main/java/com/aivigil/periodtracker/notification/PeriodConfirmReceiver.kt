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

        /** ISO date the notification was posted for. See the FIX note below. */
        const val EXTRA_FOR_DATE = "for_date"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            // ✅ FIX 1 — merged duplicate ACTION_PERIOD_YES branches into one.
            // The first branch was calling showPeriodLoggedConfirmation() without
            // the POST_NOTIFICATIONS permission check — crash on Android 13+ if
            // permission denied. Second branch had the check but never ran.
            ACTION_PERIOD_YES -> {
                val result = goAsync()
                val appContext = context.applicationContext
                // FIX: this always logged LocalDate.now(). The notification fires at
                // 9am; a user who taps "Yes, started" the next morning had her period
                // recorded a day late, shifting every subsequent prediction. The
                // notification now carries the date it was posted for, and that is
                // what gets recorded.
                val forDate = intent.getStringExtra(EXTRA_FOR_DATE)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: LocalDate.now()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        CycleRepository.getInstance(appContext)
                            .logPeriodStart(forDate)

                        NotificationManagerCompat.from(appContext)
                            .cancel(NotificationHelper.NOTIF_ID_PERIOD)

                        // ✅ Permission check before showing confirmation
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            NotificationHelper.showPeriodLoggedConfirmation(appContext)
                        }
                    } finally {
                        result.finish()
                    }
                }
            }

            // ✅ FIX 2 — ACTION_PERIOD_NOT_YET was completely unhandled.
            // The notification would stay visible after the user tapped "Not yet".
            // Now it is dismissed immediately.
            ACTION_PERIOD_NOT_YET -> {
                NotificationManagerCompat.from(context)
                    .cancel(NotificationHelper.NOTIF_ID_PERIOD)
            }
        }
    }
}