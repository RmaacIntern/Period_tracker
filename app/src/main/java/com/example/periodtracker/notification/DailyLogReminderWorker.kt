package com.example.periodtracker.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyLogReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        NotificationHelper.showDailyLogNotification(context)
        return Result.success()
    }
}