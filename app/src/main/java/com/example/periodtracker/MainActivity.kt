package com.example.periodtracker

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.periodtracker.calendar.CalendarFragment
import com.example.periodtracker.databinding.ActivityMainBinding
import com.example.periodtracker.databinding.DialogExitConfirmationBinding
import com.example.periodtracker.homefragment.HomeFragment
import com.example.periodtracker.insights.InsightsFragment
import com.example.periodtracker.notification.DailyLogReminderWorker
import com.example.periodtracker.notification.NotificationHelper
import com.example.periodtracker.profile.ProfileFragment
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var exitDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        NotificationHelper.createChannels(this)
        scheduleDailyLogReminder()
        handleNotificationIntent(intent)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, HomeFragment())
                .commit()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_home     -> HomeFragment()
                R.id.nav_calendar -> CalendarFragment()
                R.id.nav_insights -> InsightsFragment()
                R.id.nav_profile  -> ProfileFragment()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, fragment)
                .commit()
            true
        }

        // ✅ New back press API — works on Android 13+ and all older versions
        onBackPressedDispatcher.addCallback(this) {
            when {
                // Fragment has back stack — pop it first (e.g. LogSymptomsFragment)
                supportFragmentManager.backStackEntryCount > 0 -> {
                    supportFragmentManager.popBackStack()
                }
                // No back stack — show exit dialog
                else -> showExitDialog()
            }
        }
    }

    // ── Exit dialog ───────────────────────────────────────────────

    private fun showExitDialog() {
        if (exitDialogShowing) return
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

        dialogBinding.btnStayInApp.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnExit.setOnClickListener {
            dialog.dismiss()
            finishAffinity()
        }

        dialog.show()
    }

    // ── Notification intent ───────────────────────────────────────

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

    // ── Daily log reminder ────────────────────────────────────────

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
}