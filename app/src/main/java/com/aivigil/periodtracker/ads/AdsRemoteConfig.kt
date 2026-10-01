package com.aivigil.periodtracker.ads


import android.util.Log
import com.aivigil.periodtracker.BuildConfig
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

    /**
     * Minimum seconds between two interstitials.
     *
     * FIX: the default was 10 seconds. In "time" mode an interstitial fires on
     * bottom-nav tab taps, so a user moving between Home / Calendar / Insights
     * could be shown a full-screen ad every 10 seconds of ordinary navigation.
     * That is well past the point of harming retention and is the kind of
     * placement density that attracts AdMob policy enforcement for interrupting
     * normal app use.
     *
     * 90 seconds is still commercially aggressive but leaves room to actually use
     * the app between ads. The value stays remotely tunable — it is now clamped
     * to MIN_INTERSTITIAL_GAP_SECONDS so a bad console value cannot reintroduce
     * the 10-second behaviour.
     */
    var timer_interval_seconds: Long = 90L
    var max_splash_time_ms: Long = 8000L

    /** Interstitials are never shown closer together than this, whatever Remote Config says. */
    const val MIN_INTERSTITIAL_GAP_SECONDS = 45L

    /** In "onclick" mode, show an ad every Nth qualifying interaction. */
    var ad_click_interval: Int = 4

    var show_ad_on_first_click: Boolean = false

    // ------------------------------------------------------------
    // FIREBASE
    // ------------------------------------------------------------

    private val remoteConfig: FirebaseRemoteConfig
        get() = FirebaseRemoteConfig.getInstance()

    /**
     * Longest the splash screen will wait for Remote Config before giving up and
     * continuing with the built-in defaults.
     *
     * FIX (P0 — app could hang on the splash screen forever): load() only called
     * onComplete() from inside the fetchAndActivate() completion listener. If
     * that listener never fired — captive-portal Wi-Fi, a network that accepts
     * the connection then stalls, Play Services mid-update — the splash screen
     * waited indefinitely with no timer and no fallback, and the app was simply
     * unusable until force-stopped. A watchdog now guarantees onComplete()
     * always runs exactly once.
     */
    private const val CONFIG_TIMEOUT_MS = 5_000L

    fun load(onComplete: () -> Unit) {

        // Guarantee onComplete() runs exactly once, from whichever path arrives
        // first: the Firebase callback, the catch block, or the watchdog.
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        fun finishOnce(reason: String) {
            if (!completed.compareAndSet(false, true)) return
            handler.removeCallbacksAndMessages(null)
            Log.d(TAG, "load: completing ($reason)")
            logSettings()
            onComplete()
        }

        handler.postDelayed({
            finishOnce("timeout after ${CONFIG_TIMEOUT_MS}ms — using defaults")
        }, CONFIG_TIMEOUT_MS)

        try {

            remoteConfig.setConfigSettingsAsync(
                remoteConfigSettings {
                    // FIX: was 1 second. A 1-second minimum fetch interval is a
                    // debug-only setting — in production it makes the app hit
                    // Firebase on essentially every launch, which gets throttled
                    // server-side anyway and wastes the user's data and battery.
                    minimumFetchIntervalInSeconds =
                        if (BuildConfig.DEBUG) 1 else 3600
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

                    // The watchdog may already have released the splash screen.
                    // Still apply the fetched values so they are correct for the
                    // rest of this session, but do not call onComplete() twice.
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

                    sanitise()
                    finishOnce("firebase callback")
                }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Remote Config error. Keeping defaults.",
                e
            )

            sanitise()
            finishOnce("exception: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Clamps remotely-supplied values into a sane range.
     *
     * Remote Config is an operational control, not a trust boundary — a typo or a
     * bad rollout could set timer_interval_seconds to 0 and show an interstitial
     * on every single tap. It could also arrive as 0 simply because the key is
     * missing from the console, since getLong() returns 0 rather than the default.
     */
    private fun sanitise() {
        if (interstitial_trigger !in listOf("time", "onclick")) {
            Log.w(TAG, "sanitise: unknown interstitial_trigger " +
                    "'$interstitial_trigger' → 'time'")
            interstitial_trigger = "time"
        }
        timer_interval_seconds = timer_interval_seconds
            .coerceIn(MIN_INTERSTITIAL_GAP_SECONDS, 3600L)
        max_splash_time_ms = max_splash_time_ms.coerceIn(2_000L, 15_000L)
        ad_click_interval = ad_click_interval.coerceIn(1, 50)
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