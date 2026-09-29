package com.aivigil.periodtracker.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aivigil.periodtracker.splash.SplashActivity
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdPreloader
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo

/**
 * Shared flag so App Open never shows on top of an interstitial / rewarded ad.
 * Set it from ShowAds when any other full-screen ad shows / dismisses / fails.
 */
object FullScreenAdState {
    @Volatile
    var isShowing: Boolean = false
}

class AppOpenAdManager(
    private val application: Application
) : Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppOpenAdManager"

        // Minimum gap between two App Open ads
        private const val MIN_INTERVAL_MS = 10_000L
    }

    // Screens where App Open must never appear (add OnboardingActivity etc. if needed)
    private val excludedActivities: Set<Class<out Activity>> = setOf(
        SplashActivity::class.java
    )

    private var currentActivity: Activity? = null

    @Volatile private var isShowingAd = false
    @Volatile private var isSdkInitialized = false
    @Volatile private var isSplashFinished = false
    @Volatile private var isPreloadStarted = false
    @Volatile private var skipNext = false

    private var lastShownAt = 0L

    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // ─────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────

    /** Call once, right after MobileAds.initialize() succeeds. */
    fun onSdkInitialized() {
        if (isSdkInitialized) {
            Log.d(TAG, "SDK already marked initialized")
            return
        }
        isSdkInitialized = true
        Log.d(TAG, "SDK READY")
        startPreloading()
    }

    /** Call when Splash navigates to the next screen. */
    fun setSplashFinished() {
        isSplashFinished = true
        Log.d(TAG, "Splash finished")
    }

    /**
     * Call before intentionally sending the user out of the app
     * (share sheet, file picker, Play Store rating, email, etc.)
     * so the ad doesn't appear when they come back.
     */
    fun skipNextShow() {
        skipNext = true
    }

    // ─────────────────────────────────────────────────────────────
    // PRELOAD (started once — the preloader refills its own buffer)
    // ─────────────────────────────────────────────────────────────

    private fun startPreloading() {
        if (!isSdkInitialized) {
            Log.d(TAG, "Cannot preload App Open - SDK not ready")
            return
        }
        if (!AdsRemoteConfig.show_app_open_ad) {
            Log.d(TAG, "App Open disabled by Remote Config")
            return
        }
        if (isPreloadStarted) return
        isPreloadStarted = true

        Log.d(TAG, "Starting App Open preload")

        val adRequest = AdRequest.Builder(AdConstants.APP_OPEN_AD_UNIT_ID).build()
        val configuration = PreloadConfiguration(adRequest)

        AppOpenAdPreloader.start(
            AdConstants.APP_OPEN_PRELOAD_ID,
            configuration,
            object : PreloadCallback {
                override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                    Log.d(TAG, "App Open Ad READY")
                }

                override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
                    Log.e(TAG, "App Open preload failed: ${adError.message}")
                }

                override fun onAdsExhausted(preloadId: String) {
                    Log.d(TAG, "App Open buffer exhausted")
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────
    // FOREGROUND → SHOW
    // ─────────────────────────────────────────────────────────────

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "Application foreground")

        if (skipNext) {
            skipNext = false
            Log.d(TAG, "Skipped (skipNextShow)")
            return
        }
        if (!isSdkInitialized) { Log.d(TAG, "SDK not ready"); return }
        if (!isSplashFinished) { Log.d(TAG, "Splash not finished"); return }
        if (!AdsRemoteConfig.show_app_open_ad) return

        showAdIfAvailable()
    }

    private fun showAdIfAvailable() {
        val activity = currentActivity ?: return

        if (activity.isFinishing || activity.isDestroyed) return
        if (isShowingAd || FullScreenAdState.isShowing) {
            Log.d(TAG, "Another full-screen ad is showing")
            return
        }
        if (activity.javaClass in excludedActivities) {
            Log.d(TAG, "Excluded screen: ${activity.javaClass.simpleName}")
            return
        }
        // Never show on top of the SDK's own ad activity
        if (activity.javaClass.name.startsWith("com.google.android.libraries.ads")) return

        val now = SystemClock.elapsedRealtime()
        if (lastShownAt != 0L && now - lastShownAt < MIN_INTERVAL_MS) {
            Log.d(TAG, "Cooldown active")
            return
        }

        val ad = AppOpenAdPreloader.pollAd(AdConstants.APP_OPEN_PRELOAD_ID)
        if (ad == null) {
            Log.d(TAG, "No App Open Ad ready")
            startPreloading() // no-op if already running
            return
        }

        Log.d(TAG, "Showing App Open Ad")
        isShowingAd = true
        FullScreenAdState.isShowing = true
        lastShownAt = now

        // NOTE: these callbacks can arrive on a background thread
        ad.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open Ad showed")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App Open Ad dismissed")
                resetShowingState()
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.e(TAG, "App Open Ad failed: ${error.message}")
                resetShowingState()
            }

            override fun onAdImpression() {
                Log.d(TAG, "App Open impression")
            }

            override fun onAdClicked() {
                Log.d(TAG, "App Open clicked")
            }
        }

        ad.show(activity)
    }

    private fun resetShowingState() {
        isShowingAd = false
        FullScreenAdState.isShowing = false
    }

    // ─────────────────────────────────────────────────────────────
    // ACTIVITY TRACKING
    // ─────────────────────────────────────────────────────────────

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}