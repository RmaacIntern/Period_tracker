package com.example.periodtracker.onboarding

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.periodtracker.MainActivity
import com.example.periodtracker.ads.AdsRemoteConfig
import com.example.periodtracker.ads.LoadAds
import com.example.periodtracker.ads.ShowAds
import com.example.periodtracker.databinding.ActivityOnboardingBinding
import com.example.periodtracker.onboarding.viewmodel.OnboardingViewModel
import com.example.periodtracker.viewmodel.CycleViewModel
import com.example.periodtracker.viewmodel.CycleViewModelFactory
import java.time.LocalDate

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val onboardingViewModel: OnboardingViewModel by viewModels()
    private val cycleViewModel: CycleViewModel by viewModels {
        CycleViewModelFactory(application)
    }

    private val totalSteps = 8
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(
                bars.left,
                bars.top,
                bars.right,
                if (imeVisible) imeHeight else bars.bottom
            )
            insets
        }

        // ✅ Preload onboarding interstitial early
        if (AdsRemoteConfig.show_onboarding_interstitial) {
            LoadAds.preloadOnboarding()
        }

        val adapter = OnboardingPagerAdapter(this)
        binding.onboardingViewPager.adapter = adapter
        binding.onboardingViewPager.isUserInputEnabled = false
        binding.stepHeader.setStep(1, totalSteps)
        binding.btnContinue.text = "Continue"

        binding.onboardingViewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    binding.stepHeader.setStep(position + 1, totalSteps)
                    binding.btnContinue.text =
                        if (position == totalSteps - 1) "Get Started" else "Continue"
                }
                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        isNavigating = false
                        binding.btnContinue.isEnabled = true
                    }
                }
            }
        )

        binding.stepHeader.setOnBackClickListener {
            if (isNavigating) return@setOnBackClickListener
            isNavigating = true
            binding.btnContinue.isEnabled = false
            val current = binding.onboardingViewPager.currentItem
            if (current > 0) {
                binding.onboardingViewPager.currentItem = current - 1
            } else {
                isNavigating = false
                binding.btnContinue.isEnabled = true
                onBackPressedDispatcher.onBackPressed()
            }
        }

        binding.btnContinue.setOnClickListener {
            if (isNavigating) return@setOnClickListener

            val current = binding.onboardingViewPager.currentItem

            val error = validateStep(current, adapter)
            if (error != null) {
                if (current == 0) {
                    val frag = adapter.getFragment(0) as? OnboardingFragment1
                    frag?.showNameError("* $error")
                } else {
                    showError(error)
                }
                return@setOnClickListener
            }

            if (current == 0) {
                val frag = adapter.getFragment(0) as? OnboardingFragment1
                frag?.hideNameError()
            }

            isNavigating = true
            binding.btnContinue.isEnabled = false
            collectFragmentData(current, adapter)

            if (current < totalSteps - 1) {
                // ✅ Normal navigation for steps 1-7
                binding.onboardingViewPager.currentItem = current + 1
            } else {
                // ✅ Last step (step 8) — show onboarding interstitial then proceed
                showOnboardingAdThenProceed()
            }
        }
    }

    // ============================================================
    // ONBOARDING INTERSTITIAL ON LAST STEP
    // ============================================================

    private fun showOnboardingAdThenProceed() {
        if (!AdsRemoteConfig.show_onboarding_interstitial) {
            // Ad disabled — go straight to app
            saveAndProceed()
            return
        }

        ShowAds.showOnboarding(this) {
            // Called after ad dismissed or if no ad available
            runOnUiThread {
                saveAndProceed()
            }
        }
    }

    // ── Validation per step ───────────────────────────────────────

    private fun validateStep(step: Int, adapter: OnboardingPagerAdapter): String? {
        val fragment = adapter.getFragment(step)

        return when (step) {
            0 -> {
                val name = (fragment as? OnboardingFragment1)?.getEnteredName()
                when {
                    name.isNullOrBlank() -> "Please enter your name to continue"
                    name.length < 3      -> "Name must be at least 3 characters"
                    name.length > 15     -> "Name must be 15 characters or less"
                    else                 -> null
                }
            }
            1 -> null
            2 -> null
            3 -> {
                val f = fragment as? OnboardingFragment4
                val hasConditions = f?.getSelectedConditions()?.isNotEmpty() == true
                val noneSelected  = f?.isNoneSelected() == true
                if (!hasConditions && !noneSelected) {
                    "Please select your health conditions or choose \"None of these\""
                } else null
            }
            4 -> {
                val f = fragment as? OnboardingFragment5
                if (f == null) "Please select your activity level" else null
            }
            5 -> {
                val f = fragment as? OnboardingFragment6
                if (f == null) "Please select your goal" else null
            }
            6 -> {
                val date = (fragment as? OnboardingFragment7)?.getSelectedStartDate()
                when {
                    date == null -> "Please select when your last period started"
                    date.isAfter(LocalDate.now()) ->
                        "Last period start cannot be in the future"
                    date.isBefore(LocalDate.now().minusDays(90)) ->
                        "Please enter a date within the last 90 days"
                    else -> null
                }
            }
            7 -> null
            else -> null
        }
    }

    // ── Collect data after validation passes ──────────────────────

    private fun collectFragmentData(completedStep: Int, adapter: OnboardingPagerAdapter) {
        val fragment = adapter.getFragment(completedStep) ?: return
        when (completedStep) {
            0 -> onboardingViewModel.userName =
                (fragment as? OnboardingFragment1)?.getEnteredName() ?: ""
            1 -> onboardingViewModel.age =
                (fragment as? OnboardingFragment2)?.getSelectedAge() ?: 27
            2 -> (fragment as? OnboardingFragment3)?.let {
                onboardingViewModel.heightCm = it.getSelectedHeightCm()
                onboardingViewModel.weightKg = it.getSelectedWeightKg()
            }
            3 -> (fragment as? OnboardingFragment4)?.let {
                onboardingViewModel.conditions   = it.getSelectedConditions().joinToString(", ")
                onboardingViewModel.noneSelected = it.isNoneSelected()
            }
            4 -> onboardingViewModel.activityLevel =
                (fragment as? OnboardingFragment5)?.getSelectedActivityLevel()
            5 -> onboardingViewModel.goal =
                (fragment as? OnboardingFragment6)?.getSelectedGoal()
            6 -> onboardingViewModel.lastPeriodStart =
                (fragment as? OnboardingFragment7)?.getSelectedStartDate()
        }
    }

    // ── Save to Room and launch MainActivity ──────────────────────

    private fun saveAndProceed() {
        val vm = onboardingViewModel
        val lastPeriod = vm.lastPeriodStart ?: LocalDate.now()

        cycleViewModel.saveOnboardingData(
            userName        = vm.userName.ifEmpty { "User" },
            age             = vm.age,
            heightCm        = vm.heightCm,
            weightKg        = vm.weightKg,
            activityLevel   = vm.activityLevel?.name ?: "Balanced",
            goal            = vm.goal?.name ?: "Track My Cycle",
            conditions      = vm.conditions,
            cycleLength     = vm.cycleLength,
            periodDuration  = vm.periodDuration,
            lastPeriodStart = lastPeriod
        )

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    // ── Show error toast ──────────────────────────────────────────

    private fun showError(message: String) {
        val toast = Toast(this)
        val tv = TextView(this).apply {
            text     = "⚠ $message"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(40, 24, 40, 24)
            gravity  = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#2D1B33"))
        }
        @Suppress("DEPRECATION")
        toast.view     = tv
        toast.duration = Toast.LENGTH_LONG
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 160)
        toast.show()
    }
}