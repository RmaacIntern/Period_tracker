package com.aivigil.periodtracker.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(private val activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragments: List<() -> Fragment> = listOf(
        { OnboardingFragment1() },
        { OnboardingFragment2() },
        { OnboardingFragment3() },
        { OnboardingFragment4() },
        { OnboardingFragment5() },
        { OnboardingFragment6() },
        { OnboardingFragment7() },
        { OnboardingFragment8() }
    )

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]()

    /**
     * FragmentStateAdapter tags fragments as "f{itemId}".
     * Since we don't override getItemId(), itemId == position.
     */
    fun getFragment(position: Int): Fragment? {
        return activity.supportFragmentManager.findFragmentByTag("f$position")
    }
}