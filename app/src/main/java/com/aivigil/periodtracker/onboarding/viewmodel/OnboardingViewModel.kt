package com.aivigil.periodtracker.onboarding.viewmodel

import androidx.lifecycle.ViewModel
import com.aivigil.periodtracker.onboarding.OnboardingFragment5
import com.aivigil.periodtracker.onboarding.OnboardingFragment6
import java.time.LocalDate

class OnboardingViewModel : ViewModel() {
    // Fragment 1
    var userName: String = ""

    // Fragment 2
    var age: Int = 27

    // Fragment 3
    var heightCm: Int = 162
    var weightKg: Float = 60f

    // Fragment 4
    var conditions: String = ""
    var noneSelected: Boolean = false

    // Fragment 5
    var activityLevel: OnboardingFragment5.ActivityLevel? = null

    // Fragment 6
    var goal: OnboardingFragment6.Goal? = null

    // Fragment 7
    var lastPeriodStart: LocalDate? = null

    // Fragment 8 (written directly by the fragment)
    var cycleLength: Int = 28
    var periodDuration: Int = 5
}