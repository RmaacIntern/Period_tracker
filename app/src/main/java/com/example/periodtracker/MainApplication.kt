package com.example.periodtracker

import android.app.Application
import com.example.periodtracker.ads.AppOpenAdManager


class MainApplication : Application() {

    lateinit var appOpenAdManager: AppOpenAdManager

    override fun onCreate() {
        super.onCreate()
        // App Open Ads
        appOpenAdManager =
            AppOpenAdManager(this)
    }
}