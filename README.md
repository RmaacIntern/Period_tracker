# 🌸 Period Tracker — Android App

A private, on-device period and cycle tracking app built with modern Android architecture. All data stays on the user's device — no cloud sync, no data sharing.

---

## 📱 Screenshots

> Add screenshots here after uploading to GitHub

---

## ✨ Features

- 🗓️ Period & cycle tracking with predictions
- 📊 Cycle insights and health analytics
- 👤 Personalized onboarding (8-step flow)
- 🔒 100% on-device privacy — no data leaves the phone
- 🔔 Daily log reminder notifications
- 📅 Calendar view with cycle visualization
- 💊 Health conditions & activity level tracking

---

## 🏗️ Architecture

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | View Binding + Fragments |
| Architecture | MVVM (ViewModel + LiveData) |
| Local Database | Room |
| Preferences | DataStore |
| Background Work | WorkManager |
| Navigation | ViewPager2 + FragmentManager |

---

## 📦 Dependencies

```gradle
// UI
com.google.android.material
androidx.constraintlayout

// Architecture
androidx.lifecycle:lifecycle-viewmodel-ktx
androidx.lifecycle:lifecycle-livedata-ktx
androidx.room:room-runtime + room-ktx

// Ads
com.google.android.gms:play-services-ads:25.5.0
com.google.firebase:firebase-bom:33.7.0
com.google.firebase:firebase-analytics
com.google.firebase:firebase-config-ktx

// Shimmer
io.github.usefulness:shimmer-android:0.6.0

// Background
androidx.work:work-runtime-ktx
```

---

## 💰 Ads Integration

Fully integrated Google AdMob (Next-Gen SDK) with Firebase Remote Config toggle for every ad type.

| Ad Type | Placement | Remote Config Key |
|---|---|---|
| App Open | App foreground | `show_app_open` |
| Splash Interstitial | Splash screen | `show_splash_interstitial` |
| Onboarding Interstitial | After step 8 | `show_onboarding_interstitial` |
| Native Ad | Onboarding fragments 1 & 2 | `show_native` |
| Banner | MainActivity bottom | `show_banner` |
| Main Interstitial | Bottom nav tab clicks | `show_main_interstitial` |
| Back Press Interstitial | Back button press | `show_back_press_interstitial` |

### Ad Files

```
ads/
├── AdConstants.kt          — Ad unit IDs + preload IDs
├── AdsRemoteConfig.kt      — Firebase Remote Config toggles
├── AppOpenAdManager.kt     — App open ad lifecycle
├── BannerAdHelper.kt       — Banner + collapsible banner
├── LoadAds.kt              — Splash + onboarding + shared preloader
├── NativeAdHelper.kt       — Native ad with shimmer loading state
├── ShowAds.kt              — All show logic (time/click based)
└── FullScreenAdState.kt    — Prevents App Open during interstitials
```

### Native Ad Layout

Custom native ad with shimmer placeholder:

```
res/layout/
├── layout_native_ad.xml         — Actual native ad view
└── layout_native_ad_shimmer.xml — Shimmer placeholder while loading
```

---

## 🚀 Setup

### 1. Clone the repo

```bash
git clone https://github.com/yourusername/period-tracker.git
cd period-tracker
```

### 2. Add your `google-services.json`

Download from [Firebase Console](https://console.firebase.google.com/) and place in:
```
app/google-services.json
```

### 3. Add your AdMob App ID

In `AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX" />
```

### 4. Update Ad Unit IDs

In `ads/AdConstants.kt` replace test IDs with your real IDs:
```kotlin
const val BANNER_AD_UNIT_ID       = "ca-app-pub-xxx/xxx"
const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-xxx/xxx"
const val NATIVE_AD_UNIT_ID       = "ca-app-pub-xxx/xxx"
const val APP_OPEN_AD_UNIT_ID     = "ca-app-pub-xxx/xxx"
```

### 5. Firebase Remote Config defaults

Set these keys in Firebase Console → Remote Config:

```
show_app_open                  = true
show_splash_interstitial       = true
show_onboarding_interstitial   = true
show_native                    = true
show_banner                    = true
show_main_interstitial         = true
show_back_press_interstitial   = true
interstitial_trigger           = "onclick"   // or "time"
ad_click_interval              = 3
timer_interval_seconds         = 60
show_ad_on_first_click         = false
max_splash_time_ms             = 8000
```

---

## 📁 Project Structure

```
com.example.periodtracker/
├── ads/                    — All AdMob ad helpers
├── calendar/               — Calendar fragment + views
├── homefragment/           — Home screen fragment
├── insights/               — Insights fragment
├── notification/           — WorkManager + NotificationHelper
├── onboarding/             — 8-step onboarding flow
│   ├── viewmodel/          — OnboardingViewModel
│   └── OnboardingFragment1-8.kt
├── profile/                — Profile fragment
├── viewmodel/              — CycleViewModel + Factory
├── widgets/                — Custom views (GradientButton etc.)
├── MainActivity.kt         — Main screen with custom bottom nav
└── MainApplication.kt      — App class with AppOpenAdManager
```

---

## ⚙️ Build Config

```gradle
compileSdk  = 37
minSdk      = 26
targetSdk   = 37
```

---

## 🔐 Privacy

- All cycle and health data is stored **locally on device** using Room database
- No data is sent to any server
- No user accounts required
- AdMob may collect anonymized ad interaction data per [Google's privacy policy](https://policies.google.com/privacy)

---

## 👨‍💻 Developer

**Adnan Arshad**
Android Developer Intern @ Markalytics

---

## 📄 License

```
Copyright 2026 Markalytics

Licensed under the Apache License, Version 2.0
```
