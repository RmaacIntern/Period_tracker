package com.aivigil.periodtracker.ads



import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader

object LoadAds {

    private const val TAG = "LoadAds"

    private var splashStarted = false
    private var splashAd: InterstitialAd? = null

    private var onboardingStarted = false
    private var sharedInterstitialStarted = false


    // ============================================================
    // SPLASH - single-shot, once per app process
    // NOTE: enabled-check removed — caller must check
    // AdsRemoteConfig.show_splash_interstitial before calling this.
    // ============================================================

    interface OnSplashAdLoadListener {
        fun onAdLoaded()
        fun onAdUnavailable()
    }

    fun isSplashAdAvailable(): Boolean = splashAd != null

    fun preloadSplash(
        listener: OnSplashAdLoadListener? = null
    ) {

        Log.d(TAG, "preloadSplash() called. splashStarted=$splashStarted isSplashAdAvailable=${isSplashAdAvailable()}")

        if (isSplashAdAvailable()) {
            Log.d(TAG, "Splash ad already loaded this session -> onAdLoaded()")
            listener?.onAdLoaded()
            return
        }

        if (splashStarted) {
            Log.d(TAG, "Splash load already in progress/finished this session -> ignoring duplicate call")
            return
        }

        splashStarted = true

        Log.d(TAG, "Requesting ONE splash ad (single-shot load, not preloader)")

        InterstitialAd.load(
            AdRequest.Builder(AdConstants.INTERSTITIAL_AD_UNIT_ID).build(),
            object : AdLoadCallback<InterstitialAd> {

                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Splash ad loaded (single-shot, once).")
                    splashAd = ad
                    listener?.onAdLoaded()
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Splash ad failed to load: ${adError.message}")
                    splashAd = null
                    listener?.onAdUnavailable()
                }
            }
        )
    }

    fun pollSplashAd(): InterstitialAd? {
        val ad = splashAd
        splashAd = null
        return ad
    }


    // ============================================================
    // ONBOARDING
    // NOTE: enabled-check removed — caller must check
    // AdsRemoteConfig.show_onboarding_interstitial before calling this.
    // ============================================================

    fun preloadOnboarding() {

        if (
            onboardingStarted ||
            isOnboardingAdAvailable()
        ) {
            Log.d(TAG, "Onboarding: already loading/ready -> skip")
            return
        }

        onboardingStarted = true

        startPreload(AdConstants.ONBOARDING_PRELOAD_ID)
    }


    // ============================================================
    // SHARED POOL: MAIN ACTIVITY + BACK PRESS
    // NOTE: enabled-check removed — caller must check
    // AdsRemoteConfig.show_main_interstitial /
    // show_back_press_interstitial before calling these.
    // ============================================================

    fun preloadMainActivity() {
        preloadSharedInterstitial("MainActivity")
    }

    fun preloadBackPress() {
        preloadSharedInterstitial("BackPress")
    }

    fun preloadSharedInterstitialIfNeeded() {
        preloadSharedInterstitial("MainActivity+BackPress")
    }

    private fun preloadSharedInterstitial(requestedBy: String) {

        if (sharedInterstitialStarted) {
            Log.d(TAG, "$requestedBy: shared interstitial already loading -> skip")
            return
        }

        if (isSharedInterstitialAdAvailable()) {
            Log.d(TAG, "$requestedBy: shared interstitial already ready -> skip")
            return
        }

        sharedInterstitialStarted = true

        Log.d(TAG, "$requestedBy: preloading shared interstitial pool.")

        startPreload(AdConstants.SHARED_INTERSTITIAL_PRELOAD_ID)
    }


    // ============================================================
    // START PRELOAD (shared low-level call to the SDK preloader)
    // ============================================================

    private fun startPreload(preloadId: String) {

        Log.d(TAG, "Starting preload: $preloadId")

        val adRequest = AdRequest.Builder(
            AdConstants.INTERSTITIAL_AD_UNIT_ID
        ).build()

        val configuration = PreloadConfiguration(adRequest)

        InterstitialAdPreloader.start(
            preloadId,
            configuration,
            object : PreloadCallback {

                override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                    resetStartedFlag(preloadId)
                    Log.d(TAG, "Ad READY: $preloadId")
                }

                override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
                    resetStartedFlag(preloadId)
                    Log.e(TAG, "Preload failed: $preloadId - ${adError.message}")
                }

                override fun onAdsExhausted(preloadId: String) {
                    Log.d(TAG, "Ad cache exhausted: $preloadId")
                }
            }
        )
    }


    // ============================================================
    // RESET LOADING FLAGS
    // ============================================================

    private fun resetStartedFlag(preloadId: String) {
        when (preloadId) {
            AdConstants.ONBOARDING_PRELOAD_ID -> onboardingStarted = false
            AdConstants.SHARED_INTERSTITIAL_PRELOAD_ID -> sharedInterstitialStarted = false
        }
    }


    // ============================================================
    // CHECK AVAILABILITY
    // ============================================================

    fun isOnboardingAdAvailable(): Boolean =
        InterstitialAdPreloader.isAdAvailable(AdConstants.ONBOARDING_PRELOAD_ID)

    fun isSharedInterstitialAdAvailable(): Boolean =
        InterstitialAdPreloader.isAdAvailable(AdConstants.SHARED_INTERSTITIAL_PRELOAD_ID)

    fun isMainActivityAdAvailable(): Boolean = isSharedInterstitialAdAvailable()
    fun isBackPressAdAvailable(): Boolean = isSharedInterstitialAdAvailable()


    // ============================================================
    // DESTROY
    // ============================================================

    fun destroyAll() {

        InterstitialAdPreloader.destroyAll()

        splashStarted = false
        splashAd = null
        onboardingStarted = false
        sharedInterstitialStarted = false

        Log.d(TAG, "All ad preloads destroyed.")
    }
}