package com.example.periodtracker.ads


import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

object AdsRemoteConfig {

    private const val TAG = "AdsRemoteConfig"

    // ------------------------------------------------------------
    // AD ENABLE / DISABLE
    // ------------------------------------------------------------

    var show_splash_interstitial = true
    var show_onboarding_interstitial = true
    var show_main_interstitial = true
    var show_back_press_interstitial = true

    var show_app_open_ad = true

    var show_banner = true
    var show_collapsible_banner = true
    var show_native = true

    // ------------------------------------------------------------
    // INTERSTITIAL SETTINGS
    // ------------------------------------------------------------

    // "time" OR "onclick"
    var interstitial_trigger = "time"

    var timer_interval_seconds: Long = 10L
    var max_splash_time_ms: Long = 8000L  // <-- ADDED

    var ad_click_interval: Int = 3

    var show_ad_on_first_click: Boolean = false

    // ------------------------------------------------------------
    // FIREBASE
    // ------------------------------------------------------------

    private val remoteConfig: FirebaseRemoteConfig
        get() = FirebaseRemoteConfig.getInstance()

    fun load(onComplete: () -> Unit) {

        try {

            remoteConfig.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 1
                }
            )

            remoteConfig.setDefaultsAsync(
                mapOf(

                    // Interstitial
                    "show_splash_interstitial"
                            to show_splash_interstitial,

                    "show_onboarding_interstitial"
                            to show_onboarding_interstitial,

                    "show_main_interstitial"
                            to show_main_interstitial,

                    "show_back_press_interstitial"
                            to show_back_press_interstitial,

                    // App Open
                    "show_app_open_ad"
                            to show_app_open_ad,

                    // Other ads
                    "show_banner"
                            to show_banner,

                    "show_collapsible_banner"
                            to show_collapsible_banner,

                    "show_native"
                            to show_native,

                    // Interstitial settings
                    "interstitial_trigger"
                            to interstitial_trigger,

                    "timer_interval_seconds"
                            to timer_interval_seconds,

                    "max_splash_time_ms"      // <-- ADDED
                            to max_splash_time_ms,

                    "ad_click_interval"
                            to ad_click_interval,

                    "show_ad_on_first_click"
                            to show_ad_on_first_click
                )
            )

            remoteConfig.fetchAndActivate()
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {

                        // ------------------------------------------------
                        // INTERSTITIAL
                        // ------------------------------------------------

                        show_splash_interstitial =
                            remoteConfig.getBoolean(
                                "show_splash_interstitial"
                            )

                        show_onboarding_interstitial =
                            remoteConfig.getBoolean(
                                "show_onboarding_interstitial"
                            )

                        show_main_interstitial =
                            remoteConfig.getBoolean(
                                "show_main_interstitial"
                            )

                        show_back_press_interstitial =
                            remoteConfig.getBoolean(
                                "show_back_press_interstitial"
                            )

                        // ------------------------------------------------
                        // APP OPEN
                        // ------------------------------------------------

                        show_app_open_ad =
                            remoteConfig.getBoolean(
                                "show_app_open_ad"
                            )

                        // ------------------------------------------------
                        // OTHER ADS
                        // ------------------------------------------------

                        show_banner =
                            remoteConfig.getBoolean(
                                "show_banner"
                            )

                        show_collapsible_banner =
                            remoteConfig.getBoolean(
                                "show_collapsible_banner"
                            )

                        show_native =
                            remoteConfig.getBoolean(
                                "show_native"
                            )

                        // ------------------------------------------------
                        // INTERSTITIAL SETTINGS
                        // ------------------------------------------------

                        interstitial_trigger =
                            remoteConfig
                                .getString("interstitial_trigger")
                                .trim()
                                .lowercase()

                        timer_interval_seconds =
                            remoteConfig.getLong(
                                "timer_interval_seconds"
                            )

                        max_splash_time_ms =              // <-- ADDED
                            remoteConfig.getLong(
                                "max_splash_time_ms"
                            )

                        ad_click_interval =
                            remoteConfig
                                .getLong("ad_click_interval")
                                .toInt()

                        show_ad_on_first_click =
                            remoteConfig.getBoolean(
                                "show_ad_on_first_click"
                            )

                        Log.d(
                            TAG,
                            "Firebase settings loaded successfully"
                        )

                    } else {

                        Log.d(
                            TAG,
                            "Firebase failed. Keeping defaults."
                        )
                    }

                    logSettings()

                    onComplete()
                }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Remote Config error. Keeping defaults.",
                e
            )

            logSettings()

            onComplete()
        }
    }

    // ------------------------------------------------------------
    // LOG SETTINGS
    // ------------------------------------------------------------

    private fun logSettings() {

        Log.d(TAG, "==============================")

        Log.d(
            TAG,
            "show_splash_interstitial = " +
                    show_splash_interstitial
        )

        Log.d(
            TAG,
            "show_onboarding_interstitial = " +
                    show_onboarding_interstitial
        )

        Log.d(
            TAG,
            "show_main_interstitial = " +
                    show_main_interstitial
        )

        Log.d(
            TAG,
            "show_back_press_interstitial = " +
                    show_back_press_interstitial
        )

        Log.d(
            TAG,
            "show_app_open_ad = " +
                    show_app_open_ad
        )

        Log.d(
            TAG,
            "show_banner = " +
                    show_banner
        )

        Log.d(
            TAG,
            "show_collapsible_banner = " +
                    show_collapsible_banner
        )

        Log.d(
            TAG,
            "show_native = " +
                    show_native
        )

        Log.d(
            TAG,
            "interstitial_trigger = " +
                    interstitial_trigger
        )

        Log.d(
            TAG,
            "timer_interval_seconds = " +
                    timer_interval_seconds
        )

        Log.d(                                  // <-- ADDED
            TAG,
            "max_splash_time_ms = " +
                    max_splash_time_ms
        )

        Log.d(
            TAG,
            "ad_click_interval = " +
                    ad_click_interval
        )

        Log.d(
            TAG,
            "show_ad_on_first_click = " +
                    show_ad_on_first_click
        )

        Log.d(TAG, "==============================")
    }

    // ------------------------------------------------------------
    // CHECK IF ANY AD IS ENABLED
    // ------------------------------------------------------------

    fun isAnyAdEnabled(): Boolean {

        return show_splash_interstitial ||
                show_onboarding_interstitial ||
                show_main_interstitial ||
                show_back_press_interstitial ||
                show_app_open_ad ||
                show_banner ||
                show_collapsible_banner ||
                show_native
    }
}