package com.aivigil.periodtracker.ads


import android.app.Activity

import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader

object ShowAds {

    private const val TAG = "ShowAds"

    // Unified state tracking for both MainActivity and Back Press
    private var lastAdShownTimeMs: Long = 0L
    private var totalClickCount = 0

    /**
     * Blocks an interstitial while the user is in the middle of something they
     * must not be interrupted during — saving a log, confirming a period start,
     * or reading an exported medical report.
     *
     * Set this around those flows rather than relying on the time cooldown, which
     * says nothing about what the user is currently doing.
     */
    @Volatile
    var suppressInterstitials: Boolean = false

    fun interface OnAdClosedListener {
        fun onAdClosed()
    }

    // ============================================================
    // SPLASH INTERSTITIAL
    // ============================================================

    fun showSplash(
        activity: Activity,
        listener: OnAdClosedListener? = null
    ) {
        Log.d(TAG, "showSplash() called.")

        if (activity.isFinishing || activity.isDestroyed) {
            Log.d(TAG, "Splash: show() aborted - activity finishing/destroyed")
            listener?.onAdClosed()
            return
        }

        val ad = LoadAds.pollSplashAd()

        if (ad == null) {
            Log.d(TAG, "Splash: No ad ready.")
            listener?.onAdClosed()
            return
        }

        var callbackCalled = false
        fun notifyClosed() {
            if (callbackCalled) return
            callbackCalled = true
            FullScreenAdState.isShowing = false   // ← interstitial gone, App Open allowed again
            listener?.onAdClosed()
        }

        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Splash: Ad shown.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Splash: Ad dismissed.")
                notifyClosed()
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                Log.e(TAG, "Splash: Failed - ${fullScreenContentError.message}")
                notifyClosed()
            }

            override fun onAdImpression() {
                Log.d(TAG, "Splash: Impression.")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Splash: Clicked.")
            }
        }

        try {
            FullScreenAdState.isShowing = true    // ← block App Open until dismissed
            ad.show(activity)
            Log.d(TAG, "Splash: ad.show() invoked successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Splash: Show exception.", e)
            notifyClosed()
        }
    }

    // ============================================================
    // ONBOARDING INTERSTITIAL
    // ============================================================

    fun showOnboarding(
        activity: Activity,
        listener: OnAdClosedListener? = null
    ) {
        Log.d(TAG, "showOnboarding() called.")
        show(activity, AdConstants.ONBOARDING_PRELOAD_ID, "Onboarding", listener)
    }

    // ============================================================
    // UNIFIED AD ELIGIBILITY & SHOW LOGIC
    // ============================================================

    /**
     * Dual-mode ad controller ("time" vs "onclick").
     */
    fun showAdIfEligible(activity: Activity, onAdDismissed: () -> Unit) {

        if (suppressInterstitials) {
            Log.d(TAG, "Interstitial suppressed — user is mid-task")
            onAdDismissed.invoke()
            return
        }

        totalClickCount++

        when (AdsRemoteConfig.interstitial_trigger) {

            "time" -> {
                if (totalClickCount == 1 && !AdsRemoteConfig.show_ad_on_first_click) {
                    Log.d(TAG, "Time Mode: First click -> FREE")
                    onAdDismissed.invoke()
                    return
                }

                val currentTime = System.currentTimeMillis()
                // Hard floor enforced here as well as in AdsRemoteConfig.sanitise():
                // a missing Remote Config key makes getLong() return 0, which
                // would otherwise mean an interstitial on every navigation.
                val intervalSeconds = AdsRemoteConfig.timer_interval_seconds
                    .coerceAtLeast(AdsRemoteConfig.MIN_INTERSTITIAL_GAP_SECONDS)
                val timerIntervalMs = intervalSeconds * 1000L
                val timePassed = currentTime - lastAdShownTimeMs

                if (lastAdShownTimeMs > 0 && timePassed < timerIntervalMs) {
                    val remaining = (timerIntervalMs - timePassed) / 1000
                    Log.d(TAG, "Time Mode: Cooldown active (${remaining}s remaining) -> FREE")
                    onAdDismissed.invoke()
                    return
                }

                Log.d(TAG, "Time Mode: Interval passed -> Showing Ad")
                displaySharedAd(activity) {
                    lastAdShownTimeMs = System.currentTimeMillis()
                    onAdDismissed.invoke()
                }
            }

            "onclick" -> {
                val interval = AdsRemoteConfig.ad_click_interval.coerceAtLeast(1)

                if (AdsRemoteConfig.show_ad_on_first_click) {
                    // =========================================================
                    // FIRST CLICK ENABLED
                    // Click 1 -> SHOW AD
                    // Clicks 2, 3, 4... -> Follow interval (Click 1 + N)
                    // =========================================================
                    if (totalClickCount == 1) {
                        Log.d(TAG, "OnClick Mode: Click 1 -> Showing Ad (First Click Enabled)")
                        displaySharedAd(activity) { onAdDismissed.invoke() }
                        return
                    }

                    val clicksAfterFirst = totalClickCount - 1
                    if (clicksAfterFirst % interval == 0) {
                        Log.d(TAG, "OnClick Mode: Click $totalClickCount (Interval match) -> Showing Ad")
                        displaySharedAd(activity) { onAdDismissed.invoke() }
                    } else {
                        Log.d(TAG, "OnClick Mode: Click $totalClickCount -> FREE")
                        onAdDismissed.invoke()
                    }

                } else {
                    // =========================================================
                    // FIRST CLICK DISABLED
                    // Click 1 -> FREE
                    // Click 2 -> SHOW AD
                    // Clicks 3, 4, 5... -> Follow interval (Click 2 + N)
                    // =========================================================
                    if (totalClickCount == 1) {
                        Log.d(TAG, "OnClick Mode: Click 1 -> FREE")
                        onAdDismissed.invoke()
                        return
                    }

                    if (totalClickCount == 2) {
                        Log.d(TAG, "OnClick Mode: Click 2 -> Showing Ad (Mandatory 2nd Click)")
                        displaySharedAd(activity) { onAdDismissed.invoke() }
                        return
                    }

                    val clicksAfterSecond = totalClickCount - 2
                    if (clicksAfterSecond % interval == 0) {
                        Log.d(TAG, "OnClick Mode: Click $totalClickCount (Interval match) -> Showing Ad")
                        displaySharedAd(activity) { onAdDismissed.invoke() }
                    } else {
                        Log.d(TAG, "OnClick Mode: Click $totalClickCount -> FREE")
                        onAdDismissed.invoke()
                    }
                }
            }

            else -> {
                Log.w(TAG, "Unknown trigger: ${AdsRemoteConfig.interstitial_trigger} -> Navigating without ad")
                onAdDismissed.invoke()
            }
        }
    }

    fun showMainOnUserAction(activity: Activity, onAdDismissed: () -> Unit) {
        if (!AdsRemoteConfig.show_main_interstitial) {
            Log.d(TAG, "Main interstitial disabled via Remote Config")
            onAdDismissed.invoke()
            return
        }
        showAdIfEligible(activity, onAdDismissed)
    }

    fun backPress(activity: Activity) {
        if (!AdsRemoteConfig.show_back_press_interstitial) {
            Log.d(TAG, "Back press interstitial disabled via Remote Config")
            activity.finish()
            return
        }
        showAdIfEligible(activity) {
            activity.finish()
        }
    }

    private fun displaySharedAd(activity: Activity, onAdClosed: () -> Unit) {
        show(activity, AdConstants.SHARED_INTERSTITIAL_PRELOAD_ID, "SharedInterstitial") {
            onAdClosed.invoke()
        }
    }

    private fun show(
        activity: Activity,
        preloadId: String,
        placementName: String,
        listener: OnAdClosedListener?
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            listener?.onAdClosed()
            return
        }

        val ad = InterstitialAdPreloader.pollAd(preloadId)
        if (ad == null) {
            Log.d(TAG, "$placementName: No preloaded ad available.")
            listener?.onAdClosed()
            return
        }

        var callbackCalled = false
        fun notifyClosed() {
            if (callbackCalled) return
            callbackCalled = true
            FullScreenAdState.isShowing = false   // ← interstitial gone, App Open allowed again
            listener?.onAdClosed()
        }

        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() { Log.d(TAG, "$placementName: Ad shown.") }
            override fun onAdDismissedFullScreenContent() {
                if (preloadId == AdConstants.SHARED_INTERSTITIAL_PRELOAD_ID) {
                    LoadAds.preloadSharedInterstitialIfNeeded()
                }
                notifyClosed()
            }
            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                if (preloadId == AdConstants.SHARED_INTERSTITIAL_PRELOAD_ID) {
                    LoadAds.preloadSharedInterstitialIfNeeded()
                }
                notifyClosed()
            }
            override fun onAdImpression() { Log.d(TAG, "$placementName: Impression.") }
            override fun onAdClicked() { Log.d(TAG, "$placementName: Clicked.") }
        }

        try {
            FullScreenAdState.isShowing = true    // ← block App Open until dismissed
            ad.show(activity)
        } catch (e: Exception) {
            if (preloadId == AdConstants.SHARED_INTERSTITIAL_PRELOAD_ID) {
                LoadAds.preloadSharedInterstitialIfNeeded()
            }
            notifyClosed()
        }
    }
}