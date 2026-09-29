package com.aivigil.periodtracker.ads


import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.aivigil.periodtracker.databinding.SmallBannerBinding
import com.aivigil.periodtracker.util.NetworkUtils

import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import io.github.usefulness.shimmer.android.ShimmerFrameLayout

class BannerAdHelper(
    private val activity: Activity,
    private val adUnitId: String
) {

    companion object {
        private const val TAG = "BannerAdHelper"
    }

    private var adView: AdView? = null
    private var isLoading = false
    private var destroyed = false

    fun loadInto(binding: SmallBannerBinding) {
        loadInto(binding.adContainer, binding.shimmerLayout, binding.mainAdsView)
    }

    fun loadInto(
        adContainer: ViewGroup,
        shimmerLayout: ShimmerFrameLayout,
        adRow: View? = null
    ) {
        if (!AdsRemoteConfig.show_banner) {
            Log.d(TAG, "Banner disabled by Remote Config.")
            hideBanner(adContainer, shimmerLayout, adRow)
            return
        }

        if (!isActivityValid()) {
            Log.d(TAG, "Activity is not valid.")
            return
        }

        if (!NetworkUtils.isInternetAvailable(activity)) {
            Log.d(TAG, "No internet. Banner not loaded.")
            hideBanner(adContainer, shimmerLayout, adRow)
            return
        }

        if (adView != null) {
            Log.d(TAG, "Banner already loaded.")
            showBanner(adContainer, shimmerLayout, adRow)
            return
        }

        if (isLoading) {
            Log.d(TAG, "Banner is already loading.")
            return
        }

        showLoading(adContainer, shimmerLayout, adRow)
        isLoading = true

        adContainer.post {
            if (!isActivityValid()) {
                isLoading = false
                return@post
            }

            val widthPx = adContainer.width
            if (widthPx <= 0) {
                Log.d(TAG, "Container width is 0. Retrying.")
                isLoading = false
                adContainer.postDelayed({ loadInto(adContainer, shimmerLayout, adRow) }, 200)
                return@post
            }

            loadBanner(adContainer, shimmerLayout, adRow)
        }
    }

    private fun loadBanner(
        adContainer: ViewGroup,
        shimmerLayout: ShimmerFrameLayout,
        adRow: View?
    ) {
        val widthPx = adContainer.width
        val density = activity.resources.displayMetrics.density
        val widthDp = (widthPx / density).toInt()

        Log.d(TAG, "Loading banner: ${widthDp}dp")

        try {
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
            if (adSize == null) {
                Log.e(TAG, "Could not create adaptive banner size.")
                isLoading = false
                hideBanner(adContainer, shimmerLayout, adRow)
                return
            }

            val request = BannerAdRequest.Builder(adUnitId, adSize).build()
            val newAdView = AdView(activity)
            newAdView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            newAdView.loadAd(request, object : AdLoadCallback<BannerAd> {

                override fun onAdLoaded(ad: BannerAd) {
                    Log.d(TAG, "Banner loaded successfully.")

                    if (!isActivityValid()) {
                        newAdView.destroy()
                        isLoading = false
                        return
                    }

                    activity.runOnUiThread {
                        if (!isActivityValid()) {
                            newAdView.destroy()
                            isLoading = false
                            return@runOnUiThread
                        }

                        adContainer.removeAllViews()
                        adView = newAdView
                        adContainer.addView(
                            newAdView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                        isLoading = false
                        showBanner(adContainer, shimmerLayout, adRow)
                        Log.d(TAG, "Banner displayed.")
                    }

                    setupCallbacks(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Banner failed: ${error.code} - ${error.message}")
                    newAdView.destroy()

                    activity.runOnUiThread {
                        isLoading = false
                        adView = null
                        hideBanner(adContainer, shimmerLayout, adRow)
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Banner loading exception.", e)
            isLoading = false
            hideBanner(adContainer, shimmerLayout, adRow)
        }
    }

    private fun setupCallbacks(ad: BannerAd) {
        ad.adEventCallback = object : BannerAdEventCallback {

            override fun onAdImpression() {
                Log.d(TAG, "Banner impression.")
            }

            override fun onAdClicked() {
                Log.d(TAG, "Banner clicked.")
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Banner showed full screen.")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Banner dismissed.")
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                Log.e(TAG, "Banner failed to show: ${error.message}")
            }
        }
    }

    private fun showLoading(
        adContainer: ViewGroup,
        shimmerLayout: ShimmerFrameLayout,
        adRow: View?
    ) {
        adContainer.visibility = View.VISIBLE
        shimmerLayout.visibility = View.VISIBLE
        shimmerLayout.startShimmer()
        adRow?.visibility = View.VISIBLE
    }

    private fun showBanner(
        adContainer: ViewGroup,
        shimmerLayout: ShimmerFrameLayout,
        adRow: View?
    ) {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        adContainer.visibility = View.VISIBLE
        adRow?.visibility = View.VISIBLE
    }

    private fun hideBanner(
        adContainer: ViewGroup,
        shimmerLayout: ShimmerFrameLayout,
        adRow: View?
    ) {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        adContainer.visibility = View.GONE
        adRow?.visibility = View.GONE
    }

    private fun isActivityValid(): Boolean {
        return !destroyed && !activity.isFinishing && !activity.isDestroyed
    }

    fun destroy() {
        Log.d(TAG, "Destroying banner.")
        destroyed = true
        isLoading = false

        adView?.let { view ->
            try {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying banner.", e)
            }
        }

        adView = null
    }

    // Collapsible banner
    fun loadCollapsibleBanner(
        adContainer: ViewGroup,
        shimmerLayout: ShimmerFrameLayout,
        adRow: View? = null,
        position: String = "bottom"
    ) {
        if (!AdsRemoteConfig.show_banner) {
            Log.d(TAG, "Banner disabled by Remote Config.")
            hideBanner(adContainer, shimmerLayout, adRow)
            return
        }

        if (!AdsRemoteConfig.show_collapsible_banner) {
            Log.d(TAG, "Collapsible banner disabled by Remote Config.")
            hideBanner(adContainer, shimmerLayout, adRow)
            return
        }

        if (!isActivityValid()) {
            Log.d(TAG, "Activity is not valid.")
            return
        }

        if (!NetworkUtils.isInternetAvailable(activity)) {
            Log.d(TAG, "No internet. Collapsible banner not loaded.")
            hideBanner(adContainer, shimmerLayout, adRow)
            return
        }

        if (adView != null) {
            Log.d(TAG, "Banner already loaded.")
            showBanner(adContainer, shimmerLayout, adRow)
            return
        }

        if (isLoading) {
            Log.d(TAG, "Banner is already loading.")
            return
        }

        showLoading(adContainer, shimmerLayout, adRow)
        isLoading = true

        adContainer.post {
            if (!isActivityValid()) {
                isLoading = false
                return@post
            }

            val widthPx = adContainer.width
            if (widthPx <= 0) {
                Log.d(TAG, "Container width is 0. Retrying.")
                isLoading = false
                adContainer.postDelayed({
                    loadCollapsibleBanner(adContainer, shimmerLayout, adRow, position)
                }, 200)
                return@post
            }

            loadCollapsibleBannerInternal(adContainer, shimmerLayout, adRow, position)
        }
    }

    private fun loadCollapsibleBannerInternal(
        adContainer: ViewGroup,
        shimmerLayout: ShimmerFrameLayout,
        adRow: View?,
        position: String
    ) {
        val widthPx = adContainer.width
        val density = activity.resources.displayMetrics.density
        val widthDp = (widthPx / density).toInt()

        Log.d(TAG, "Loading collapsible banner: ${widthDp}dp")

        try {
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp)
            if (adSize == null) {
                Log.e(TAG, "Could not create adaptive banner size.")
                isLoading = false
                hideBanner(adContainer, shimmerLayout, adRow)
                return
            }

            val extras = Bundle().apply {
                putString("collapsible", position)
            }
            Log.d(TAG, "Collapsible position = $position")

            val request = BannerAdRequest.Builder(adUnitId, adSize)
                .setGoogleExtrasBundle(extras)
                .build()

            val newAdView = AdView(activity)
            newAdView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            newAdView.loadAd(request, object : AdLoadCallback<BannerAd> {

                override fun onAdLoaded(ad: BannerAd) {
                    Log.d(TAG, "Collapsible banner loaded.")
                    Log.d(TAG, "Actually collapsible = ${ad.isCollapsible()}")

                    if (!isActivityValid()) {
                        newAdView.destroy()
                        isLoading = false
                        return
                    }

                    activity.runOnUiThread {
                        if (!isActivityValid()) {
                            newAdView.destroy()
                            isLoading = false
                            return@runOnUiThread
                        }

                        adContainer.removeAllViews()
                        adView = newAdView
                        adContainer.addView(
                            newAdView,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                        isLoading = false
                        showBanner(adContainer, shimmerLayout, adRow)
                        Log.d(TAG, "Collapsible banner displayed.")
                    }

                    setupCallbacks(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Collapsible banner failed: ${error.code} - ${error.message}")
                    newAdView.destroy()

                    activity.runOnUiThread {
                        isLoading = false
                        adView = null
                        hideBanner(adContainer, shimmerLayout, adRow)
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Collapsible banner loading exception.", e)
            isLoading = false
            hideBanner(adContainer, shimmerLayout, adRow)
        }
    }
}