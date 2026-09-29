---
title: "README — Period Tracker"
date: 2026-09-29
developer: Adnan Arshad
tip: b76d8c1
---

# Period Tracker — Android App

Private, on-device menstrual cycle tracker. Predicts next period, fertile window, and
ovulation from the user's last period date and their stated cycle length. All data stays
on the device — nothing is uploaded.

**Package:** `com.aivigil.periodtracker`
**Min SDK:** 26 (Android 8.0) · **Target SDK:** 37
**Version:** 1.0 (versionCode 1)
**Repo:** https://github.com/RmaacIntern/Period_tracker

---

## Where Everything Is

| What | Location | Who has access |
|---|---|---|
| Source code | `RmaacIntern/Period_tracker` (this repo) — `main` branch | Adnan, Muneeb, Shezrah |
| Play Store listing | Not yet submitted | Muneeb to create |
| Firebase project | Firebase Console — project name used during dev; **production project not yet created** | Adnan currently; Muneeb to own production |
| AdMob account | All ad unit IDs in `app/src/main/java/com/aivigil/periodtracker/ads/AdConstants.kt` are **Google test IDs** — production IDs not yet created | Muneeb to obtain production IDs |
| Release keystore | **Does not exist yet** — no release build has been configured | Must be created before Play Store submission; Muneeb to own |
| `google-services.json` | Excluded from repo (in `.gitignore`) — dev version used locally | Adnan holds dev file; production file needed from Muneeb's Firebase project |
| `local.properties` | Excluded from repo (in `.gitignore`) — contains local SDK path only | Each developer generates their own |
| Privacy policy | **Does not exist yet** — required by Play Store for an app that collects health data | Muneeb / Shezrah to create before submission |

---

## Three Files That Matter Most

If you are picking up this project for the first time, read these three before touching anything else:

| # | File | Why |
|---|---|---|
| 1 | `docs/APP-BRIEFS.md` | Full architecture — every screen, every data table, every ad placement, the notification system, all decisions. The fastest way to understand the whole app. |
| 2 | `app/src/main/java/com/aivigil/periodtracker/domain/CycleEngine.kt` | All cycle maths live here — pure Kotlin, no Android deps. If a prediction is wrong, the bug is here or in how `CycleRepository` feeds it. |
| 3 | `app/src/main/java/com/aivigil/periodtracker/ads/AdsRemoteConfig.kt` | Controls every ad type. To turn off any ad without a release, change the value in Firebase Console — the key names are all in this file. |

---

## How to Build

**Requirements:**
- Android Studio Hedgehog or later
- JDK 17 (use Android Studio's bundled JDR — `File → Project Structure → SDK Location → JDK`)
- `google-services.json` placed at `app/google-services.json` (get from Muneeb)

**Steps:**
```bash
git clone https://github.com/RmaacIntern/Period_tracker.git
cd "Period Tracker"
# Place google-services.json in app/
./gradlew assembleDebug
```

**If `JAVA_HOME is not set` error appears:**
```powershell
# Windows — point to Android Studio's bundled JDK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
./gradlew assembleDebug
```

---

## Project Structure

```
app/src/main/java/com/aivigil/periodtracker/
├── ads/                    AdMob — 6 classes (AdConstants, AdsRemoteConfig,
│                           BannerAdHelper, LoadAds, ShowAds, AppOpenAdManager)
├── calendar/               CalendarFragment + CycleCalendarView (custom canvas View)
├── data/
│   ├── dao/                UserSettingsDao, PeriodEntryDao, DailyLogDao
│   ├── db/                 AppDatabase (Room, version 7, 6 migrations)
│   ├── entity/             UserSettings, PeriodEntry, DailyLog
│   └── repository/         CycleRepository — single data access point
├── dialog/                 PeriodStartConfirmationDialog
├── domain/                 CycleEngine — all cycle maths, pure Kotlin
├── history/                PastLogHistoryFragment
├── homefragment/           HomeFragment — ring, phase, stats, predictions
├── insights/               InsightsFragment + CycleBarChartView
├── logsymptoms/            LogSymptomsFragment — flow, mood, symptoms, BBT, notes
├── notification/           AlarmReceiver, AlarmScheduler, BootReceiver,
│                           DailyLogReminderWorker, NotificationHelper,
│                           PeriodConfirmReceiver
├── onboarding/             OnboardingActivity + 8 fragments + OnboardingViewModel
├── profile/                ProfileFragment + 9 bottom sheets
├── splash/                 SplashActivity — entry point, Firebase + AdMob init
├── viewmodel/              CycleViewModel (shared across all fragments)
└── widgets/                GradientButton, CycleProgressRingView, StepProgressHeaderView

docs/
├── APP-BRIEFS.md           Full architecture reference (read this first)
├── SPEC.md                 Feature spec with Done-when criteria
├── DESIGN-STANDARD.md      All colour tokens, typography, spacing — read before any UI work
├── NEW-APP-RUNBOOK.md      16 gates to run every morning before writing code
├── DOCUMENTATION-STANDARD.md  Session log template
├── DOCUMENTATION-GAP-EXPLANATION.md  Explains why docs were not standard from day 1
└── session-logs/           SESSION-2026-09-15.md through SESSION-2026-09-29.md
```

---

## Database

**File name:** `period_tracker.db` (renamed from `lunacycle.db` on 2026-09-29 — see `AppDatabase.kt` and `MainApplication.kt`)
**ORM:** Room 2.8.5
**Current version:** 7

| Table | Purpose | Key fields |
|---|---|---|
| `user_settings` | Singleton row — one per user | userName, cycleLength, periodDuration, lastPeriodStart, goal, conditions |
| `period_entries` | One row per confirmed period start | startDate (UNIQUE), endDate, flow |
| `daily_logs` | Daily symptom entries — multiple per date | date, entryNumber, flow, moods, symptoms, cervicalFluid, lhTestResult, basalTemp, loggedAt |

**Migration history:** v1→2 (DailyLog PK change), v2→3 (loggedAt), v3→4 (lhTestResult),
v4→5 (PeriodEntry.flow), v5→6 (UNIQUE on startDate + dedup), v6→7 (file rename no-op).

---

## Ads

All ad types are controlled by Firebase Remote Config — no release needed to turn any ad off.

| Key | Default | Effect |
|---|---|---|
| `show_splash_interstitial` | true | Interstitial during splash screen |
| `show_onboarding_interstitial` | true | Interstitial between onboarding steps |
| `show_main_interstitial` | true | Interstitial on tab navigation |
| `show_back_press_interstitial` | true | Interstitial on back press |
| `show_app_open_ad` | true | App-open ad on foreground |
| `show_banner` | true | Banner on splash and MainActivity |
| `show_collapsible_banner` | true | Collapsible banner in onboarding |
| `show_native` | true | Native ads in feed placements |
| `interstitial_trigger` | `"time"` | `"time"` or `"onclick"` mode |
| `timer_interval_seconds` | 10 | Seconds between time-based interstitials |
| `ad_click_interval` | 3 | Tab clicks between click-based interstitials |

**All unit IDs are Google test IDs.** Replace in `AdConstants.kt` before any release.

---

## Notifications

Three channels created in `NotificationHelper.createChannels()` called from `MainActivity.onCreate()`:

| Channel | ID | Fires |
|---|---|---|
| Period Reminder | `period_reminder` | Day before predicted period at 9am |
| Ovulation Alert | `ovulation_alert` | First day of fertile window at 9am |
| Daily Log | `daily_log` | Daily via WorkManager |

Alarms use `setExactAndAllowWhileIdle`. `BootReceiver` reschedules them after device restart.
The "Yes / Not yet" notification actions are handled by `PeriodConfirmReceiver`.

**Required permissions:** `POST_NOTIFICATIONS` (runtime, Android 13+), `SCHEDULE_EXACT_ALARM`,
`USE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`.

---

## Gotchas

These are issues that cost significant debugging time. Read before touching the relevant code.

**1. AGP 9.3.2 — do not add `kotlin.android` plugin alias**
AGP 9.3.2 bundles Kotlin. Adding `alias(libs.plugins.kotlin.android)` to `build.gradle.kts`
causes "Cannot add extension 'kotlin'" and breaks the build. Remove it if it appears.

**2. AGP 9.3.2 — `kotlinOptions` block is unresolved**
Use `kotlin { jvmToolchain(17) }` *outside* the `android {}` block instead of
`kotlinOptions { jvmTarget = "17" }` inside it.

**3. KSP + AGP 9.3.2 — `kotlin.sourceSets` error**
Add this to root `gradle.properties`:
```
android.disallowKotlinSourceSets=false
```
Without it, KSP cannot compile Room's generated DAOs.

**4. `android:layout_marginHorizontal` — do not use**
Even though minSdk is 26, the XML inflater rejects `marginHorizontal` at compile time.
Use `layout_marginStart` + `layout_marginEnd` on every layout file.

**5. Package rename corrupts XML files with UTF-8 BOM**
Android Studio's Refactor → Rename re-saves some XML files with a UTF-8 BOM (`\xEF\xBB\xBF`)
at byte 0. The build fails with `line 1:0 mismatched input '﻿'`. Fix with:
```powershell
Get-ChildItem -Path "app\src\main\res" -Filter "*.xml" -Recurse | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $noBom = $bytes[3..($bytes.Length - 1)]
        [System.IO.File]::WriteAllBytes($_.FullName, $noBom)
    }
}
```

**6. `BottomNavigationView` is replaced — do not use it**
The standard `BottomNavigationView` was drawing behind fragment content. The app uses
`custom_bottom_nav.xml` with `CustomBottomNavBinding`. Do not reintroduce `BottomNavigationView`.

**7. `onBackPressed()` is deprecated — do not override it**
Use `onBackPressedDispatcher.addCallback(this) { ... }` in `MainActivity`. Overriding
`onBackPressed()` is silently ignored on Android 13+ on some devices.

**8. `windowTranslucentStatus` belongs in the theme, not a widget style**
This attribute only takes effect when set on an Activity's theme. Placing it inside a
`<style>` for a widget (e.g. `CycleChip`) is silently ignored and causes edge-to-edge
display to break. Set it in `Base.Theme.PeriodTracker` in `themes.xml`.

**9. Database file was named `lunacycle.db` — it has been renamed**
As of 2026-09-29 the file is `period_tracker.db`. The rename is handled in
`MainApplication.onCreate()` before Room opens the connection. Do not change the name
in `AppDatabase.kt` again without adding a corresponding migration.

**10. `AdMob CTA button gradient` — clear `backgroundTintList` first**
`AppCompatButton` inherits a Material colour tint that overrides `android:background`.
Setting a `GradientDrawable` as background has no visible effect unless you call
`callToAction.backgroundTintList = null` first. See `NativeAdHelper.kt`.

**11. `google-services.json` is gitignored — builds will fail without it**
The file is excluded from the repo. Get it from Muneeb (Firebase Console →
Project Settings → Your apps → `com.aivigil.periodtracker` → Download).

---

## Known Issues Outstanding

| ID | File | Description |
|---|---|---|
| K-04 | `AdConstants.kt` | All ad unit IDs are Google test IDs — must replace before Play Store |
| K-05 | `SplashActivity.kt` | Onboarding check bypassed — always navigates to onboarding on every launch |

*K-01 (LunaCycle notification text), K-02 (lunacycle.db), K-03 (Base.Theme.LunaCycle) — all resolved 2026-09-29.*

---

## Commit Convention

```
<type>(<scope>): <description>

Types: feat fix refactor style docs chore
Scopes: splash onboarding home calendar insights log profile ads db notif design
```

Example: `fix(ads): guard showSplash against destroyed activity`
