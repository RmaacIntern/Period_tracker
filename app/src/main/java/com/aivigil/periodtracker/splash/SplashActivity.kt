package com.aivigil.periodtracker.splash

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.aivigil.periodtracker.MainActivity
import com.aivigil.periodtracker.MainApplication
import com.aivigil.periodtracker.ads.AdConstants
import com.aivigil.periodtracker.ads.AdsRemoteConfig
import com.aivigil.periodtracker.ads.BannerAdHelper
import com.aivigil.periodtracker.ads.LoadAds
import com.aivigil.periodtracker.ads.ShowAds
import com.aivigil.periodtracker.databinding.ActivitySplashBinding
import com.aivigil.periodtracker.databinding.SmallBannerBinding
import com.aivigil.periodtracker.onboarding.OnboardingActivity
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    private val appOpenAdManager
        get() = (application as MainApplication).appOpenAdManager

    private lateinit var binding: ActivitySplashBinding
    private var bannerAdHelper: BannerAdHelper? = null


    private val handler = Handler(Looper.getMainLooper())

    private var adReady = false
    private var opened = false

    companion object {
        private const val TAG = "SplashActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.splashRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply bottom padding to root so ALL content shifts up
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom  // ← this pushes banner above nav bar
            )
            insets
        }





        binding.splashRoot.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#F0E6FA"),
                Color.parseColor("#FDE8F3"),
                Color.parseColor("#FFFFFF")
            )
        )

        binding.logoCircle.clipToOutline = true
        binding.logoCircle.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        initializeFirebase()
    }

    // ─────────────────────────────────────────────────────────────
    // FIREBASE
    // ─────────────────────────────────────────────────────────────

    private fun initializeFirebase() {
        try {
            FirebaseApp.getInstance()
        } catch (e: Exception) {
            if (FirebaseApp.initializeApp(this) == null) {
                Log.e(TAG, "Firebase initialization failed")
                startSplash()
                return
            }
        }

        AdsRemoteConfig.load {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread

                if (AdsRemoteConfig.isAnyAdEnabled()) {
                    initializeAds()
                } else {
                    startSplash()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // ADS INITIALIZATION
    // ─────────────────────────────────────────────────────────────

    private fun initializeAds() {
        if (MobileAds.isInitialized) {
            appOpenAdManager.onSdkInitialized()
            Log.d(TAG, "Ads SDK already initialized")
            loadBannerAd()
            startSplash()

            return
        }

        Thread {
            try {
                val config = InitializationConfig.Builder(
                    AdConstants.APP_ID
                ).build()

                MobileAds.initialize(applicationContext, config)

                Log.d(TAG, "Ads SDK initialized")
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    appOpenAdManager.onSdkInitialized()   // ← add
                    loadBannerAd()
                    startSplash()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Ads SDK initialization failed", e)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    startSplash()
                }
            }
        }.start()
    }

    // ─────────────────────────────────────────────────────────────
    // BANNER AD
    // ─────────────────────────────────────────────────────────────

    private fun loadBannerAd() {
        if (isFinishing || isDestroyed) return

        val smallBannerBinding = SmallBannerBinding.bind(binding.smallAd.root)

        bannerAdHelper = BannerAdHelper(
            activity = this,
            adUnitId = AdConstants.BANNER_AD_UNIT_ID
        )

        bannerAdHelper?.loadInto(smallBannerBinding)
    }

    // ─────────────────────────────────────────────────────────────
    // SPLASH — timer + interstitial load in parallel
    // ─────────────────────────────────────────────────────────────

    private fun startSplash() {
        if (isFinishing || isDestroyed) return

        Log.d(TAG, "Splash started")

        adReady = false
        binding.progressBar.progress = 0

        // ── Load interstitial in parallel with timer ───────────────
        if (AdsRemoteConfig.show_splash_interstitial) {

            Log.d(TAG, "Loading splash interstitial")

            LoadAds.preloadSplash(
                object : LoadAds.OnSplashAdLoadListener {

                    override fun onAdLoaded() {
                        runOnUiThread {
                            if (isFinishing || isDestroyed || opened) return@runOnUiThread

                            Log.d(TAG, "Ad loaded → showing immediately")

                            adReady = true
                            // Timer keeps running in background (DO NOT stop it)
                            // Show ad RIGHT NOW
                           showSplashAd()
                        }
                    }

                    override fun onAdUnavailable() {
                        runOnUiThread {
                            if (isFinishing || isDestroyed || opened) return@runOnUiThread

                            Log.d(TAG, "Ad unavailable → timer continues")

                            // Timer is still running → let it finish naturally
                            adReady = false
                        }
                    }
                }
            )
        }

        // ── Timer runs as fallback ─────────────────────────────────
        startSplashTimer()
    }

    // ─────────────────────────────────────────────────────────────
    // SPLASH TIMER — fallback if ad never loads
    // ─────────────────────────────────────────────────────────────

    private fun startSplashTimer() {
        val duration = AdsRemoteConfig.max_splash_time_ms

        val startTime = System.currentTimeMillis()

        val progressRunnable = object : Runnable {
            override fun run() {
                if (opened) return

                val elapsed = System.currentTimeMillis() - startTime
                val progress = ((elapsed.toFloat() / duration) * 100)
                    .toInt()
                    .coerceIn(0, 100)

                binding.progressBar.progress = progress

                binding.tvLoading.text = when {
                    progress < 30 -> "Loading..."
                    progress < 60 -> "Setting things up..."
                    progress < 85 -> "Almost ready..."
                    else          -> "Just a moment..."
                }

                if (elapsed >= duration) {
                    // ← Only navigate if ad is NOT showing
                    if (!adReady) {
                        Log.d(TAG, "Timer done, no ad → going next")
                        openNextScreen()
                    } else {
                        Log.d(TAG, "Timer done, ad is showing → waiting for dismiss")
                        // Do nothing — openNextScreen() will be called
                        // from ShowAds.showSplash dismiss callback
                    }
                } else {
                    handler.postDelayed(this, 50)
                }
            }
        }

        handler.post(progressRunnable)
    }

    // ─────────────────────────────────────────────────────────────
    // SHOW INTERSTITIAL
    // ─────────────────────────────────────────────────────────────

    private fun showSplashAd() {
        if (opened) return
        if (!adReady) {
            openNextScreen()
            return
        }

        Log.d(TAG, "Showing splash interstitial")

        // ← Keep adReady = true while ad is ON SCREEN
        // so timer knows not to navigate away

        ShowAds.showSplash(this) {
            runOnUiThread {
                Log.d(TAG, "Ad dismissed → now safe to go next")
                adReady = false  // ← reset only after dismiss
                openNextScreen()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // OPEN NEXT SCREEN
    // ─────────────────────────────────────────────────────────────

    private fun openNextScreen() {
        if (opened) return
        opened = true

        handler.removeCallbacksAndMessages(null)
        appOpenAdManager.setSplashFinished()

        lifecycleScope.launch {
            val repo = com.aivigil.periodtracker.data.repository.CycleRepository
                .getInstance(applicationContext)
            val done = repo.isOnboardingComplete.first()

            Log.d(TAG, "Opening next screen — onboardingDone=$done")

            val dest = if (done) MainActivity::class.java
            else      OnboardingActivity::class.java

            startActivity(Intent(this@SplashActivity, dest))
            finish()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DESTROY
    // ─────────────────────────────────────────────────────────────

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        bannerAdHelper?.destroy()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────
// ANIMATE PROGRESS TO 100% THEN CALLBACK
// ─────────────────────────────────────────────────────────────


}