package com.aivigil.periodtracker.util

import android.content.Context
import android.graphics.drawable.GradientDrawable
import com.aivigil.periodtracker.R

/**
 * Central place for all GradientDrawable backgrounds that were previously
 * using hardcoded hex colors in Kotlin code.
 *
 * Every fragment that sets backgrounds in onViewCreated() should call
 * these helpers instead of Color.parseColor("#FDF2F5") etc.
 *
 * This ensures dark mode works correctly — Android resolves @color/xxx
 * from values-night/colors.xml automatically when the phone is in dark mode.
 */
object ThemeHelper {

    /**
     * The standard 3-stop gradient used on every onboarding screen background.
     * Light: #FDF2F5 → #FBF5F8 → #FFFFFF
     * Dark:  #1A1320 → #161019 → #121014
     */
    fun onboardingGradient(context: Context): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                context.getColor(R.color.onboarding_grad_start),
                context.getColor(R.color.onboarding_grad_mid),
                context.getColor(R.color.onboarding_grad_end)
            )
        )
    }

    /**
     * Standard white/dark card background with rounded corners.
     * Used by nameInputCard, startDateCard, cycleLengthCard, etc.
     */
    fun cardBg(context: Context, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * context.resources.displayMetrics.density
            setColor(context.getColor(R.color.surface_card))
        }
    }

    /**
     * Inner card / sub-row background (slightly different shade from card).
     */
    fun innerCardBg(context: Context, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * context.resources.displayMetrics.density
            setColor(context.getColor(R.color.surface_card_inner))
        }
    }

    /**
     * Soft pink icon circle background (used by calendar icon, period icon etc.)
     */
    fun iconCirclePink(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(context.getColor(R.color.icon_bg_pink))
        }
    }

    /**
     * Soft purple icon circle background.
     */
    fun iconCirclePurple(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(context.getColor(R.color.icon_bg_purple))
        }
    }

    /**
     * Soft green icon circle background.
     */
    fun iconCircleGreen(context: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(context.getColor(R.color.icon_bg_green))
        }
    }

    /**
     * Diagonal gradient used on the splash screen background.
     * Light: #F0E6FA → #FDE8F3 → #FFFFFF
     * Dark:  #1A1020 → #1E1320 → #121014
     */
    fun splashGradient(context: Context): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                context.getColor(R.color.splash_grad_start),
                context.getColor(R.color.splash_grad_mid),
                context.getColor(R.color.onboarding_grad_end)
            )
        )
    }

    /**
     * The insight/info banner background (purple tint).
     */
    fun insightBannerBg(context: Context, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * context.resources.displayMetrics.density
            setColor(context.getColor(R.color.insight_banner_bg))
        }
    }

    /**
     * Basal temp card background.
     */
    fun basalCardBg(context: Context, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = radiusDp * context.resources.displayMetrics.density
            setColor(context.getColor(R.color.basal_card_bg))
        }
    }
}