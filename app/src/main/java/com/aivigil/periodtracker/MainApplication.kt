package com.aivigil.periodtracker

import android.app.Application
import com.aivigil.periodtracker.ads.AppOpenAdManager
import com.aivigil.periodtracker.notification.NotificationHelper

class MainApplication : Application() {

    lateinit var appOpenAdManager: AppOpenAdManager

    override fun onCreate() {
        super.onCreate()

        // FIX: notification channels were only created in MainActivity.onCreate().
        // On Android 8+ posting to a channel that does not exist SILENTLY DROPS the
        // notification, so any alarm or WorkManager job that fired before the user
        // had ever reached MainActivity produced nothing at all — no notification,
        // no error. Creating them here means they exist for every entry point
        // (BootReceiver, AlarmReceiver, DailyLogReminderWorker) regardless of which
        // Activity the process started for. Channel creation is idempotent.
        NotificationHelper.createChannels(this)

        // App Open Ads
        appOpenAdManager = AppOpenAdManager(this)
    }
}