package com.aivigil.periodtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aivigil.periodtracker.ads.AdConstants
import com.aivigil.periodtracker.ads.AdsRemoteConfig
import com.aivigil.periodtracker.ads.BannerAdHelper
import com.aivigil.periodtracker.ads.LoadAds
import com.aivigil.periodtracker.ads.ShowAds
import com.aivigil.periodtracker.calendar.CalendarFragment
import com.aivigil.periodtracker.databinding.ActivityMainBinding
import com.aivigil.periodtracker.databinding.CustomBottomNavBinding
import com.aivigil.periodtracker.databinding.DialogExitConfirmationBinding
import com.aivigil.periodtracker.databinding.SmallBannerBinding
import com.aivigil.periodtracker.homefragment.HomeFragment
import com.aivigil.periodtracker.insights.InsightsFragment
import com.aivigil.periodtracker.notification.DailyLogReminderWorker
import com.aivigil.periodtracker.notification.NotificationHelper
import com.aivigil.periodtracker.profile.ProfileFragment
import android.widget.ImageView
import android.widget.TextView
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navBinding: CustomBottomNavBinding
    private var bannerAdHelper: BannerAdHelper? = null
    private var exitDialogShowing = false
    private var clickInProgress = false
    private var selectedTabId = -1  // ← -1 means nothing selected yet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navBinding = CustomBottomNavBinding.bind(binding.customBottomNav.root)

        setupWindowInsets()
        setupBannerAd()
        preloadMainAd()
        setupBottomNav()

        NotificationHelper.createChannels(this)
        scheduleDailyLogReminder()
        handleNotificationIntent(intent)

        // AFTER
        if (savedInstanceState == null) {
            selectedTabId = R.id.navHome
            updateTabColors(R.id.navHome)
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, HomeFragment())
                .commit()
        }

        onBackPressedDispatcher.addCallback(this) {
            when {
                supportFragmentManager.backStackEntryCount > 0 -> {
                    supportFragmentManager.popBackStack()
                }
                else -> {
                    if (!AdsRemoteConfig.show_back_press_interstitial) {
                        showExitDialog()
                        return@addCallback
                    }

                    ShowAds.showAdIfEligible(this@MainActivity) {
                        // ← Small delay lets activity fully resume before showing dialog
                        binding.mainRoot.postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                showExitDialog()
                            }
                        }, 300)
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CUSTOM BOTTOM NAV
    // ─────────────────────────────────────────────────────────────

    private fun setupBottomNav() {
        navBinding.navHome.setOnClickListener     { onTabClicked(R.id.navHome) }
        navBinding.navCalendar.setOnClickListener { onTabClicked(R.id.navCalendar) }
        navBinding.navInsights.setOnClickListener { onTabClicked(R.id.navInsights) }
        navBinding.navProfile.setOnClickListener  { onTabClicked(R.id.navProfile) }

        updateTabColors(R.id.navHome)
    }
    private fun onTabClicked(tabId: Int) {
        if (clickInProgress) return  // ✅ removed tabId == selectedTabId check

        // If same tab clicked — no ad, just refresh fragment
        if (tabId == selectedTabId) {
            selectTab(tabId)
            return
        }

        clickInProgress = true

        // Keep current tab visually selected while ad is showing
        updateTabColors(selectedTabId)

        ShowAds.showMainOnUserAction(this) {
            runOnUiThread {  // ✅ ensure UI runs on main thread
                clickInProgress = false
                selectTab(tabId)
            }
        }
    }

    private fun selectTab(tabId: Int) {
        selectedTabId = tabId
        updateTabColors(tabId)

        val fragment = when (tabId) {
            R.id.navHome     -> HomeFragment()
            R.id.navCalendar -> CalendarFragment()
            R.id.navInsights -> InsightsFragment()
            R.id.navProfile  -> ProfileFragment()
            else             -> HomeFragment()
        }

        // ✅ commitAllowingStateLoss prevents crash after ad dismiss
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainFragmentContainer, fragment)
            .commitAllowingStateLoss()
    }

    private fun updateTabColors(selectedId: Int) {
        val selectedColor   = android.graphics.Color.parseColor("#FFFFFF")
        val unselectedColor = ContextCompat.getColor(this, R.color.text_secondary)  // dark-mode aware

        // Reset all to unselected — INVISIBLE keeps space reserved
        listOf(
            Triple(navBinding.navHome,     navBinding.iconHome,     navBinding.labelHome),
            Triple(navBinding.navCalendar, navBinding.iconCalendar, navBinding.labelCalendar),
            Triple(navBinding.navInsights, navBinding.iconInsights, navBinding.labelInsights),
            Triple(navBinding.navProfile,  navBinding.iconProfile,  navBinding.labelProfile)
        ).forEach { (tab, icon, label) ->
            tab.background   = null
            label.visibility = View.INVISIBLE  // ← INVISIBLE not GONE — space always reserved
            setTabColor(icon, label, unselectedColor)
        }

        // Set selected tab — gradient pill + white
        val (tab, icon, label) = when (selectedId) {
            R.id.navHome     -> Triple(navBinding.navHome,     navBinding.iconHome,     navBinding.labelHome)
            R.id.navCalendar -> Triple(navBinding.navCalendar, navBinding.iconCalendar, navBinding.labelCalendar)
            R.id.navInsights -> Triple(navBinding.navInsights, navBinding.iconInsights, navBinding.labelInsights)
            R.id.navProfile  -> Triple(navBinding.navProfile,  navBinding.iconProfile,  navBinding.labelProfile)
            else             -> Triple(navBinding.navHome,     navBinding.iconHome,     navBinding.labelHome)
        }

        tab.background   = ContextCompat.getDrawable(this, R.drawable.bg_nav_selected)
        label.visibility = View.VISIBLE
        setTabColor(icon, label, selectedColor)
    }

    private fun setTabColor(icon: ImageView, label: TextView, color: Int) {
        icon.colorFilter = BlendModeColorFilterCompat
            .createBlendModeColorFilterCompat(color, BlendModeCompat.SRC_IN)
        label.setTextColor(color)
    }

    // ─────────────────────────────────────────────────────────────
    // WINDOW INSETS
    // ─────────────────────────────────────────────────────────────

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.smallAd.root.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    // ─────────────────────────────────────────────────────────────
    // BANNER AD
    // ─────────────────────────────────────────────────────────────

    private fun setupBannerAd() {
        if (!AdsRemoteConfig.show_banner) return
        bannerAdHelper = BannerAdHelper(this, AdConstants.BANNER_AD_UNIT_ID)
        val smallBannerBinding = SmallBannerBinding.bind(binding.smallAd.root)
        bannerAdHelper?.loadInto(smallBannerBinding)
    }

    // ─────────────────────────────────────────────────────────────
    // PRELOAD INTERSTITIAL
    // ─────────────────────────────────────────────────────────────

    private fun preloadMainAd() {
        if (AdsRemoteConfig.show_main_interstitial ||
            AdsRemoteConfig.show_back_press_interstitial
        ) {
            LoadAds.preloadSharedInterstitialIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        clickInProgress = false
        exitDialogShowing = false
        updateTabColors(selectedTabId)

        // ✅ Re-select current tab to ensure correct fragment is showing
        if (selectedTabId != -1) {
            val currentFragment = supportFragmentManager
                .findFragmentById(R.id.mainFragmentContainer)
            val expectedFragment = when (selectedTabId) {
                R.id.navHome     -> HomeFragment::class.java
                R.id.navCalendar -> CalendarFragment::class.java
                R.id.navInsights -> InsightsFragment::class.java
                R.id.navProfile  -> ProfileFragment::class.java
                else             -> HomeFragment::class.java
            }
            // Only replace if wrong fragment is showing
            if (currentFragment?.javaClass != expectedFragment) {
                selectTab(selectedTabId)
            }
        }

        preloadMainAd()
    }

    // ─────────────────────────────────────────────────────────────
    // EXIT DIALOG
    // ─────────────────────────────────────────────────────────────

    private fun showExitDialog() {
        if (exitDialogShowing) return
        if (isFinishing || isDestroyed) return  // ← ADD this guard
        exitDialogShowing = true

        val dialogBinding = DialogExitConfirmationBinding
            .inflate(LayoutInflater.from(this))

        val dialog = android.app.Dialog(this).apply {
            setContentView(dialogBinding.root)
            window?.apply {
                setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(
                        android.graphics.Color.TRANSPARENT
                    )
                )
                setLayout(
                    (resources.displayMetrics.widthPixels * 0.88).toInt(),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setDimAmount(0.4f)
            }
            setCancelable(true)
            setOnDismissListener { exitDialogShowing = false }
        }

        dialogBinding.btnStayInApp.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnExit.setOnClickListener {
            dialog.dismiss()
            finishAffinity()
        }

        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────
    // NOTIFICATION INTENT
    // ─────────────────────────────────────────────────────────────

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        when (intent?.getStringExtra("from_notification")) {
            "period_confirmed" -> {
                android.util.Log.i("MainActivity",
                    "opened from period confirmed notification")
                android.widget.Toast.makeText(
                    this,
                    "Period logged ✓ Cycle updated",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // DAILY LOG REMINDER
    // ─────────────────────────────────────────────────────────────

    private fun scheduleDailyLogReminder() {
        val request = PeriodicWorkRequestBuilder<DailyLogReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(calculateDelayUntil8pm(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_log_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun calculateDelayUntil8pm(): Long {
        val now = java.time.LocalDateTime.now()
        var target = now.withHour(20).withMinute(0).withSecond(0)
        if (now.isAfter(target)) target = target.plusDays(1)
        return java.time.Duration.between(now, target).toMillis()
    }

    // ─────────────────────────────────────────────────────────────
    // DESTROY
    // ─────────────────────────────────────────────────────────────

    override fun onDestroy() {
        bannerAdHelper?.destroy()
        bannerAdHelper = null
        super.onDestroy()
    }
}