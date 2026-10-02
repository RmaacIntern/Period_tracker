package com.aivigil.periodtracker

import android.os.Build
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
        const val TAG_HOME         = "tab_home"
        const val TAG_CALENDAR     = "tab_calendar"
        const val TAG_INSIGHTS     = "tab_insights"
        const val TAG_PROFILE      = "tab_profile"

        val TAB_TAGS = listOf(TAG_HOME, TAG_CALENDAR, TAG_INSIGHTS, TAG_PROFILE)
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navBinding: CustomBottomNavBinding

    private var systemBarBottom    = 0
    private var bannerAdHelper: BannerAdHelper? = null
    private var exitDialogShowing  = false
    private var clickInProgress    = false
    private var selectedTabId      = R.id.navHome
    private var alarmScheduledOnce = false   // ← prevents re-scheduling on rotation

    private val vm: CycleViewModel by viewModels { CycleViewModelFactory(application) }

    // ─────────────────────────────────────────────────────────────
    // LIFECYCLE
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding    = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navBinding = CustomBottomNavBinding.bind(binding.customBottomNav.root)

        setupWindowInsets()
        setupBannerAd()
        preloadMainAd()
        setupBottomNav()

        // Notification setup
        NotificationHelper.createChannels(this)

        // Respects the user's reminder toggle — does NOT unconditionally re-arm
        NotificationPrefs.syncDailyReminder(this)

        // Schedule period/ovulation alarms once, only when permission is granted
        // alarmScheduledOnce is a class field so rotation does not reset it
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            vm.prediction.observe(this) { pred ->
                if (pred != null && !alarmScheduledOnce) {
                    alarmScheduledOnce = true
                    NotificationPrefs.rescheduleFromPrediction(
                        context    = this,
                        nextPeriod = pred.nextPeriodDate,
                        ovulation  = pred.ovulationDate
                    )
                }
            }
        }

        // Handle tap from a notification (cold start)
        handleNotificationIntent(intent)

        // Restore selected tab after rotation — without this selectedTabId was
        // always reset to navHome and the highlighted tab was wrong
        selectedTabId = savedInstanceState?.getInt(KEY_SELECTED_TAB, R.id.navHome)
            ?: R.id.navHome

        if (savedInstanceState == null) {
            showTab(R.id.navHome)
        }
        updateTabColors(selectedTabId)

        // Back press — show ad then exit dialog
        onBackPressedDispatcher.addCallback(this) {
            when {
                supportFragmentManager.backStackEntryCount > 0 ->
                    supportFragmentManager.popBackStack()

                else -> {
                    if (!AdsRemoteConfig.show_back_press_interstitial) {
                        showExitDialog()
                        return@addCallback
                    }
                    ShowAds.showAdIfEligible(this@MainActivity) {
                        binding.mainRoot.postDelayed({
                            if (!isFinishing && !isDestroyed) showExitDialog()
                        }, 300)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clickInProgress   = false
        exitDialogShowing = false
        updateTabColors(selectedTabId)
        vm.onAppForegrounded()
        preloadMainAd()
        applyBottomInset()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, selectedTabId)
    }

    override fun onDestroy() {
        bannerAdHelper?.destroy()
        bannerAdHelper = null
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────
    // BOTTOM NAV
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

        val fm = supportFragmentManager

        // Pop any backstack fragments (LogSymptoms, PastLogHistory, etc.)
        if (fm.backStackEntryCount > 0) {
            fm.popBackStackImmediate(
                null,
                androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }

        // Hide everything immediately so nothing bleeds through
        val hideTx = fm.beginTransaction()
        fm.fragments.forEach { fragment ->
            if (fragment.isAdded && !fragment.isHidden) hideTx.hide(fragment)
        }
        hideTx.commitNow()

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
     * Fragments are created once and shown/hidden — never replaced.
     * This preserves scroll position, expanded state, and avoids
     * re-running DB queries and ad requests on every tab switch.
     */
    private fun showTab(tabId: Int) {
        val tag = tagFor(tabId)
        val fm  = supportFragmentManager
        if (fm.isStateSaved || isFinishing || isDestroyed) return

        val tx = fm.beginTransaction()

        // Hide only the 4 known tab fragments — never touch backstack fragments
        TAB_TAGS.forEach { existingTag ->
            fm.findFragmentByTag(existingTag)?.let { fragment ->
                if (fragment.isAdded && !fragment.isHidden) tx.hide(fragment)
            }
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

    // ─────────────────────────────────────────────────────────────
    // TAB COLORS
    // ─────────────────────────────────────────────────────────────

    private fun updateTabColors(selectedId: Int) {
        val selectedColor   = android.graphics.Color.parseColor("#FFFFFF")
        val unselectedColor = ContextCompat.getColor(this, R.color.text_secondary)

        // Reset all tabs to unselected state
        // INVISIBLE (not GONE) keeps label space reserved — prevents layout shift
        listOf(
            Triple(navBinding.navHome,     navBinding.iconHome,     navBinding.labelHome),
            Triple(navBinding.navCalendar, navBinding.iconCalendar, navBinding.labelCalendar),
            Triple(navBinding.navInsights, navBinding.iconInsights, navBinding.labelInsights),
            Triple(navBinding.navProfile,  navBinding.iconProfile,  navBinding.labelProfile)
        ).forEach { (tab, icon, label) ->
            tab.background   = null
            label.visibility = View.INVISIBLE
            setTabColor(icon, label, unselectedColor)
        }

        // Apply selected state — gradient pill background + white tint
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
            val systemBars  = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBarBottom = systemBars.bottom
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            applyBottomInset()
            insets
        }
    }

    private fun applyBottomInset() {
        val navPaddingStart  = 8.dpToPx()
        val navPaddingTop    = 10.dpToPx()
        val navPaddingEnd    = 8.dpToPx()
        val navPaddingBottom = 10.dpToPx()

        val bannerVisible = binding.smallAd.root.visibility == View.VISIBLE
        if (bannerVisible) {
            binding.customBottomNav.root.setPadding(
                navPaddingStart, navPaddingTop, navPaddingEnd, navPaddingBottom
            )
            binding.smallAd.root.setPadding(0, 0, 0, systemBarBottom)
        } else {
            binding.customBottomNav.root.setPadding(
                navPaddingStart, navPaddingTop, navPaddingEnd, navPaddingBottom + systemBarBottom
            )
            binding.smallAd.root.setPadding(0, 0, 0, 0)
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

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
    // INTERSTITIAL PRELOAD
    // ─────────────────────────────────────────────────────────────

    private fun preloadMainAd() {
        if (AdsRemoteConfig.show_main_interstitial ||
            AdsRemoteConfig.show_back_press_interstitial
        ) {
            LoadAds.preloadSharedInterstitialIfNeeded()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // EXIT DIALOG
    // ─────────────────────────────────────────────────────────────

    private fun showExitDialog() {
        if (exitDialogShowing) return
        if (isFinishing || isDestroyed) return
        exitDialogShowing = true

        val dialogBinding = DialogExitConfirmationBinding.inflate(LayoutInflater.from(this))

        val dialog = android.app.Dialog(this).apply {
            setContentView(dialogBinding.root)
            window?.apply {
                setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
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
    // NOTIFICATION INTENT HANDLING
    // ─────────────────────────────────────────────────────────────

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    /**
     * Reads the destination extra set by NotificationHelper and navigates
     * to the correct tab. Extra is consumed immediately so rotation or a
     * later onResume does not re-navigate.
     */
    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val destination = intent?.getStringExtra(NotificationHelper.EXTRA_DESTINATION)
            ?: return
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
}