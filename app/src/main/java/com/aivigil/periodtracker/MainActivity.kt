package com.aivigil.periodtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.aivigil.periodtracker.notification.NotificationHelper
import com.aivigil.periodtracker.notification.NotificationPrefs
import com.aivigil.periodtracker.profile.ProfileFragment
import com.aivigil.periodtracker.viewmodel.CycleViewModel
import com.aivigil.periodtracker.viewmodel.CycleViewModelFactory
import android.widget.ImageView
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private companion object {
        const val KEY_SELECTED_TAB = "selected_tab_id"
        const val TAG_HOME     = "tab_home"
        const val TAG_CALENDAR = "tab_calendar"
        const val TAG_INSIGHTS = "tab_insights"
        const val TAG_PROFILE  = "tab_profile"
        val TAB_TAGS = listOf(TAG_HOME, TAG_CALENDAR, TAG_INSIGHTS, TAG_PROFILE)
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navBinding: CustomBottomNavBinding
    private var bannerAdHelper: BannerAdHelper? = null
    private var exitDialogShowing = false
    private var clickInProgress = false
    private var selectedTabId = R.id.navHome

    private val vm: CycleViewModel by viewModels { CycleViewModelFactory(application) }

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
        // FIX: was scheduleDailyLogReminder() — an unconditional re-enqueue that
        // undid the user's "daily reminder off" choice on every single launch.
        // syncDailyReminder() enqueues or cancels to match the stored preference.
        NotificationPrefs.syncDailyReminder(this)
        handleNotificationIntent(intent)

        // FIX: selectedTabId was not persisted, so after a rotation it was -1 and
        // updateTabColors(-1) fell through to the else branch and highlighted Home
        // while the restored fragment was still Calendar/Insights/Profile.
        selectedTabId = savedInstanceState?.getInt(KEY_SELECTED_TAB, R.id.navHome)
            ?: R.id.navHome

        if (savedInstanceState == null) {
            showTab(R.id.navHome)
        }
        updateTabColors(selectedTabId)

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
        if (clickInProgress) return

        // Pop any detail screens (like PastLogHistoryFragment) before switching tabs
        val fm = supportFragmentManager
        if (fm.backStackEntryCount > 0) {
            fm.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        if (tabId == selectedTabId) {
            selectTab(tabId)
            return
        }

        clickInProgress = true
        updateTabColors(selectedTabId)

        ShowAds.showMainOnUserAction(this) {
            runOnUiThread {
                clickInProgress = false
                selectTab(tabId)
            }
        }
    }

    private fun selectTab(tabId: Int) {
        selectedTabId = tabId
        updateTabColors(tabId)
        showTab(tabId)
    }

    /**
     * Swaps the visible tab.
     *
     * FIX: this used to construct a brand-new Fragment on every tab tap and
     * `replace()` it, so switching away and back destroyed scroll position,
     * expanded sections and any in-progress input, and re-ran every DB query
     * and ad request from scratch. Fragments are now created once, kept in the
     * FragmentManager by tag, and shown/hidden — so tab state survives.
     */
    private fun showTab(tabId: Int) {
        val tag = tagFor(tabId)
        val fm = supportFragmentManager
        if (fm.isStateSaved || isFinishing || isDestroyed) return

        val tx = fm.beginTransaction()
        TAB_TAGS.forEach { existingTag ->
            fm.findFragmentByTag(existingTag)?.let { if (it.isAdded) tx.hide(it) }
        }
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            tx.add(R.id.mainFragmentContainer, newFragmentFor(tabId), tag)
        } else {
            tx.show(existing)
        }
        tx.commitNowAllowingStateLoss()
    }

    private fun tagFor(tabId: Int): String = when (tabId) {
        R.id.navCalendar -> TAG_CALENDAR
        R.id.navInsights -> TAG_INSIGHTS
        R.id.navProfile  -> TAG_PROFILE
        else             -> TAG_HOME
    }

    private fun newFragmentFor(tabId: Int) = when (tabId) {
        R.id.navCalendar -> CalendarFragment()
        R.id.navInsights -> InsightsFragment()
        R.id.navProfile  -> ProfileFragment()
        else             -> HomeFragment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedTabId)
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

        // FIX (midnight staleness): tells the shared ViewModel to re-read the
        // calendar date. Without this, an app left open overnight kept reporting
        // yesterday's cycle day, phase and fertility status until something
        // happened to touch the database.
        vm.onAppForegrounded()

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

    /**
     * FIX: this used to look for a "from_notification" extra that NOTHING in the
     * codebase ever set, so it was dead code — every notification dropped the user
     * on the Home tab with no context for why the app had opened. Notifications now
     * carry NotificationHelper.EXTRA_DESTINATION and land on the relevant screen.
     */
    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val destination = intent?.getStringExtra(NotificationHelper.EXTRA_DESTINATION)
            ?: return
        // Consume it so a rotation or a later onResume does not re-navigate.
        intent.removeExtra(NotificationHelper.EXTRA_DESTINATION)

        val tab = when (destination) {
            NotificationHelper.DEST_CALENDAR -> R.id.navCalendar
            NotificationHelper.DEST_LOG      -> R.id.navHome
            else                             -> R.id.navHome
        }
        android.util.Log.i("MainActivity", "opened from notification → $destination")
        selectedTabId = tab
        updateTabColors(tab)
        showTab(tab)
    }

    // Daily-log reminder scheduling now lives in NotificationPrefs so the
    // preference is honoured from every entry point (see NotificationPrefs).

    // ─────────────────────────────────────────────────────────────
    // DESTROY
    // ─────────────────────────────────────────────────────────────

    override fun onDestroy() {
        bannerAdHelper?.destroy()
        bannerAdHelper = null
        super.onDestroy()
    }
}