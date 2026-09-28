package com.example.periodtracker.ads


import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.example.periodtracker.R
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

class NativeAdHelper(
    private val context: Context
) {

    companion object {
        private const val TAG = "NativeAdHelper"
    }

    private var nativeAd: NativeAd? = null
    private var nativeAdView: NativeAdView? = null

    private var isLoading = false

    /**
     * Load and display a native ad inside the supplied container.
     *
     * If native ads are disabled from Firebase Remote Config,
     * the container is hidden and no ad request is made.
     */
    fun loadInto(container: FrameLayout) {

        // Firebase Remote Config control
        if (!AdsRemoteConfig.show_native) {
            container.removeAllViews()
            container.visibility = View.GONE
            return
        }

        // Prevent duplicate requests
        if (isLoading) {
            return
        }

        container.visibility = View.VISIBLE
        container.removeAllViews()

        showShimmer(container)

        isLoading = true

        val adRequest = NativeAdRequest.Builder(
            AdConstants.NATIVE_AD_UNIT_ID,
            listOf(NativeAd.NativeAdType.NATIVE)
        ).build()

        NativeAdLoader.load(
            adRequest,
            object : NativeAdLoaderCallback {

                override fun onNativeAdLoaded(
                    ad: NativeAd
                ) {
                    isLoading = false

                    container.post {
                        if (!AdsRemoteConfig.show_native) {
                            ad.destroy()
                            hideContainer(container)
                            return@post
                        }

                        showNativeAd(
                            container = container,
                            ad = ad
                        )
                    }
                }

                override fun onAdFailedToLoad(
                    adError: LoadAdError
                ) {
                    isLoading = false

                    container.post {
                        hideContainer(container)
                    }
                }
            }
        )
    }

    /**
     * Displays shimmer while the native ad is loading.
     */
    private fun showShimmer(container: FrameLayout) {

        container.removeAllViews()

        val shimmerView = LayoutInflater.from(context)
            .inflate(
                R.layout.layout_native_ad_shimmer,
                container,
                false
            )

        shimmerView.findViewById<View>(R.id.nativeShimmer)

        container.addView(shimmerView)
    }

    /**
     * Bind the loaded NativeAd to the NativeAdView.
     */
    private fun showNativeAd(
        container: FrameLayout,
        ad: NativeAd
    ) {

        destroyCurrentAd()

        nativeAd = ad

        container.removeAllViews()

        val adView = LayoutInflater.from(context)
            .inflate(
                R.layout.layout_native_ad,
                container,
                false
            ) as NativeAdView

        nativeAdView = adView

        bindAdFields(
            adView = adView,
            ad = ad
        )

        /*
         * Your XML intentionally does not contain a MediaView.
         *
         * Passing null tells the Next-Gen SDK that the native ad
         * should be rendered without a media view.
         */
        adView.registerNativeAd(
            ad,
            null
        )

        container.addView(adView)
    }

    /**
     * Bind all fields that exist in layout_native_ad.xml.
     */
    private fun bindAdFields(
        adView: NativeAdView,
        ad: NativeAd
    ) {

        val appIcon = adView.findViewById<ImageView>(R.id.adAppIcon)
        val headline = adView.findViewById<TextView>(R.id.adHeadline)
        val advertiser = adView.findViewById<TextView>(R.id.adAdvertiser)
        val body = adView.findViewById<TextView>(R.id.adBody)
        val callToAction = adView.findViewById<TextView>(R.id.adCallToAction)
        val stars = adView.findViewById<RatingBar>(R.id.adStars)
        val price = adView.findViewById<TextView>(R.id.adPrice)
        val store = adView.findViewById<TextView>(R.id.adStore)

        // --- Register each asset view with the NativeAdView ---
        adView.headlineView = headline
        adView.bodyView = body
        adView.callToActionView = callToAction
        adView.iconView = appIcon
        adView.advertiserView = advertiser
        adView.starRatingView = stars
        adView.priceView = price
        adView.storeView = store

        // ... your existing text/visibility binding logic stays the same ...


        // -------------------------
        // HEADLINE
        // -------------------------

        headline.text = ad.headline
        headline.visibility =
            if (ad.headline.isNullOrBlank()) {
                View.GONE
            } else {
                View.VISIBLE
            }

        // -------------------------
        // BODY
        // -------------------------

        val bodyText = ad.body

        if (bodyText.isNullOrBlank()) {
            body.visibility = View.GONE
        } else {
            body.text = bodyText
            body.visibility = View.VISIBLE
        }

        // -------------------------
        // CALL TO ACTION
        // -------------------------

        val ctaText = ad.callToAction

        if (ctaText.isNullOrBlank()) {
            callToAction.visibility = View.GONE
        } else {
            callToAction.text = ctaText
            callToAction.visibility = View.VISIBLE
        }

        // -------------------------
        // APP ICON
        // -------------------------

        val icon = ad.icon

        if (icon == null) {
            appIcon.visibility = View.GONE
        } else {
            appIcon.setImageDrawable(icon.drawable)
            appIcon.visibility = View.VISIBLE
        }

        // -------------------------
        // ADVERTISER
        // -------------------------

        val advertiserText = ad.advertiser

        if (advertiserText.isNullOrBlank()) {
            advertiser.visibility = View.GONE
        } else {
            advertiser.text = advertiserText
        }

        /*
         * Your XML intentionally keeps advertiser hidden.
         * Keep it hidden because the current UI does not display it.
         */
        advertiser.visibility = View.GONE

        // -------------------------
        // STAR RATING
        // -------------------------

        val rating = ad.starRating

        if (rating == null || rating <= 0.0) {
            stars.visibility = View.GONE
        } else {
            stars.rating = rating.toFloat()
            stars.visibility = View.VISIBLE
        }

        // -------------------------
        // PRICE
        // -------------------------

        val priceText = ad.price

        if (priceText.isNullOrBlank()) {
            price.visibility = View.GONE
        } else {
            price.text = priceText
            price.visibility = View.VISIBLE
        }

        // -------------------------
        // STORE
        // -------------------------

        val storeText = ad.store

        if (storeText.isNullOrBlank()) {
            store.visibility = View.GONE
        } else {
            store.text = storeText
            store.visibility = View.VISIBLE
        }
    }

    /**
     * Hide the native ad container after a failed load
     * or when Remote Config disables native ads.
     */
    private fun hideContainer(
        container: FrameLayout
    ) {

        destroyCurrentAd()

        container.removeAllViews()
        container.visibility = View.GONE
    }

    /**
     * Destroy the currently displayed native ad.
     */
    private fun destroyCurrentAd() {

        nativeAdView?.destroy()
        nativeAd?.destroy()

        nativeAdView = null
        nativeAd = null
    }

    /**
     * Call this from Activity/Fragment onDestroy().
     */
    fun destroy() {

        destroyCurrentAd()

        isLoading = false
    }
}