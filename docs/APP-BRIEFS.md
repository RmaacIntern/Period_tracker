# APP-BRIEFS.md — Period Tracker (LunaCycle)
> **Day-Zero Reference Document** — Written before development begins. Describes what this app is, how it is structured, what every screen does, and how every major system works. Keep this file at the root of the repository.

---

## 1. What We Are Building

**Period Tracker** is an Android menstrual-cycle tracker. Users log the start of their period, track daily symptoms and moods, and the app predicts future periods, fertile windows, and ovulation. Revenue is generated through AdMob ads whose visibility is controlled remotely via Firebase Remote Config — letting us turn ads on or off without releasing a new build.

| Field | Value |
|---|---|
| App Name | Period Tracker (package: `com.example.periodtracker`) |
| Platform | Android — minSdk 26, targetSdk 37 |
| Language | Kotlin (JVM 17) |
| UI | XML Views + ViewBinding (no Compose) |
| Database | Room 2.8.5 (SQLite), db name `lunacycle.db` ⚠️ (see §5 — hardcoded old name) |
| Architecture | Single-Activity + Fragments, ViewModel + LiveData, Coroutines |
| Build System | Gradle + KSP |
| Ads | Google Mobile Ads SDK 1.5.0 (AdMob) |
| Remote Config | Firebase Remote Config (controls ad flags) |
| Analytics | Firebase Analytics |

---

## 2. Project Structure

```
Period Tracker/
├── app/
│   ├── build.gradle.kts           ← Dependencies, SDK versions, AdMob meta-data
│   ├── google-services.json       ← Firebase project config
│   └── src/main/java/com/example/periodtracker/
│       ├── MainActivity.kt            ← Single host activity, custom bottom nav
│       ├── MainApplication.kt         ← App-level init, AppOpenAdManager reference
│       │
│       ├── ads/                       ← All AdMob logic
│       │   ├── AdConstants.kt         ← Ad unit IDs, preload keys, timing constants
│       │   ├── AdsRemoteConfig.kt     ← Firebase Remote Config flags (which ads show)
│       │   ├── AppOpenAdManager.kt    ← App-open ad lifecycle
│       │   ├── BannerAdHelper.kt      ← Banner ad loading/displaying
│       │   ├── LoadAds.kt             ← Pre-loading interstitials by context
│       │   ├── NativeAdHelper.kt      ← Native ad loading/binding
│       │   └── ShowAds.kt             ← Show interstitial/native with eligibility check
│       │
│       ├── calendar/                  ← Calendar tab
│       │   ├── CalendarFragment.kt
│       │   ├── CycleCalendarView.kt   ← Custom month grid with cycle coloring
│       │   └── SheetCalendarView.kt   ← Sheet picker variation
│       │
│       ├── data/                      ← Room database layer
│       │   ├── dao/                   ← DailyLogDao, PeriodEntryDao, UserSettingsDao
│       │   ├── db/AppDatabase.kt      ← DB singleton, 6 migrations defined
│       │   ├── entity/                ← DailyLog, PeriodEntry, UserSettings
│       │   └── repository/CycleRepository.kt  ← Single data access point
│       │
│       ├── dialog/
│       │   └── PeriodStartConfirmationDialog.kt
│       │
│       ├── domain/
│       │   └── CycleEngine.kt         ← All cycle math (pure, unit-testable)
│       │
│       ├── history/
│       │   └── PastLogHistoryFragment.kt  ← Scrollable history of past logs
│       │
│       ├── homefragment/
│       │   └── HomeFragment.kt        ← Dashboard: ring, stats, quick actions
│       │
│       ├── insights/
│       │   ├── InsightsFragment.kt    ← Charts, cycle summary, PDF export
│       │   └── CycleBarChartView.kt   ← Custom bar chart view
│       │
│       ├── logsymptoms/
│       │   └── LogSymptomsFragment.kt ← Daily log entry: flow, mood, symptoms, BBT
│       │
│       ├── model/
│       │   └── CalendarDayState.kt    ← Enum/state for calendar day coloring
│       │
│       ├── notification/              ← Alarms & push notifications
│       │   ├── AlarmReceiver.kt
│       │   ├── AlarmScheduler.kt
│       │   ├── BootReceiver.kt        ← Reschedules alarms after device reboot
│       │   ├── DailyLogReminderWorker.kt
│       │   ├── NotificationHelper.kt  ← Channel creation, notification builders
│       │   └── PeriodConfirmReceiver.kt ← "Did period start? Yes / Not yet" reply
│       │
│       ├── onboarding/                ← 8-step first-run wizard
│       │   ├── OnboardingActivity.kt
│       │   ├── OnboardingFragment1–8.kt
│       │   ├── OnboardingPagerAdapter.kt
│       │   └── viewmodel/OnboardingViewModel.kt
│       │
│       ├── profile/                   ← Profile tab + bottom sheets
│       │   ├── ProfileFragment.kt
│       │   └── sheets/                ← ActivitySheet, AgeSheet, ConditionsSheet,
│       │                                 CycleLengthSheet, EditProfileSheet, HeightSheet,
│       │                                 PdfReadySheet, PeriodStartSheet, WeightSheet
│       │
│       ├── splash/
│       │   └── SplashActivity.kt      ← Entry point: Firebase init → Ads init → nav
│       │
│       ├── util/                      ← Shared utilities
│       └── viewmodel/
│           ├── CycleViewModel.kt
│           └── CycleViewModelFactory.kt
│
└── gradle/
    └── libs.versions.toml             ← Centralized dependency versions
```

---

## 3. Navigation Flow

```
App Launch
    └── SplashActivity (LAUNCHER)
            ├── Checks if onboarding done (DataStore flag)
            ├── Initialises Firebase → AdsRemoteConfig.load()
            ├── If ads enabled → initialises AdMob SDK → loads banner on splash
            ├── Max splash timeout: 8 000 ms (AdConstants.MAX_SPLASH_TIME_MS)
            │
            ├── [First launch] ──► OnboardingActivity (8 fragments)
            │       └── On "Get Started" ──► MainActivity
            │
            └── [Returning user] ──► MainActivity
```

**MainActivity** hosts a **custom bottom navigation bar** (not the standard `BottomNavigationView`) with four tabs:

| Tab | Fragment | Purpose |
|---|---|---|
| Home | `HomeFragment` | Cycle ring, days countdown, quick stats, Log Today button |
| Calendar | `CalendarFragment` | Month view with period/fertile/ovulation day colouring |
| Insights | `InsightsFragment` | Bar chart of past cycles, PDF export, history link |
| Profile | `ProfileFragment` | User settings, cycle length, notifications, conditions |

Navigating between tabs **may show an interstitial ad** (controlled by Remote Config). Back-press from MainActivity also optionally shows an interstitial before an exit-confirmation dialog.

---

## 4. Screens in Detail

### 4.1 SplashActivity
- Gradient background: top-left `#F0E6FA` → `#FDE8F3` → white.
- Displays app logo centred with a circular clip.
- Loads Firebase Remote Config; if any ad is enabled, initialises the AdMob SDK on a background thread.
- Shows a small banner ad at the bottom while waiting.
- After max 8 s (or when ads are ready), navigates to Onboarding or MainActivity.

### 4.2 OnboardingActivity (8 Steps)
A `ViewPager2` (swipe disabled, navigation by Continue/Back buttons). Progress shown in a custom step-header. An interstitial ad can be shown between steps.

| Step | Fragment | Collects |
|---|---|---|
| 1 | `OnboardingFragment1` | User's name |
| 2 | `OnboardingFragment2` | Goal (track cycle / plan pregnancy / avoid pregnancy) |
| 3 | `OnboardingFragment3` | Age |
| 4 | `OnboardingFragment4` | Height |
| 5 | `OnboardingFragment5` | Weight |
| 6 | `OnboardingFragment6` | Last period start date |
| 7 | `OnboardingFragment7` | Average cycle length |
| 8 | `OnboardingFragment8` | Activity level, health conditions |

Collected data is saved to `UserSettings` (Room) via `OnboardingViewModel` + `CycleViewModel`. On "Get Started", marks onboarding complete in DataStore and opens `MainActivity`.

### 4.3 HomeFragment
- **Cycle ring**: animated circular progress showing current cycle day vs. cycle length.
- **Phase label**: e.g. "Menstrual", "Follicular", "Ovulation", "Luteal".
- **Days countdown**: "X days until next period" or "Period is X days late".
- **Quick stats cards**: last period date, cycle length, period duration.
- **Log Today button**: opens `LogSymptomsFragment` for today's date.
- **Period Start button**: opens `PeriodStartSheet` to log a new period.
- Requests `POST_NOTIFICATIONS` permission on first run (Android 13+).
- Observes `CycleViewModel.cycleState` (LiveData) for all above data.

### 4.4 CalendarFragment
- Custom `CycleCalendarView`: month grid with colour-coded days:
  - 🔴 Period days
  - 🩷 Predicted period days
  - 🟢 Fertile window
  - 🟡 Ovulation day
  - ⚪ Regular days
- Tapping a day opens `LogSymptomsFragment` for that date (past, today, or future preview).
- Month navigation: swipe or prev/next arrows.
- Legend strip below the grid.

### 4.5 InsightsFragment
- **Bar chart** (`CycleBarChartView`): one bar per past cycle, height = cycle length in days.
- **Summary stats**: average cycle length, average period duration, shortest/longest cycle.
- **Ovulation detection indicators**: BBT shift detected, cervical fluid progression, LH surge.
- **Export to PDF** button: generates a PDF report of cycle history (saved to Downloads or shared via FileProvider on older Android).
- **View History** button: opens `PastLogHistoryFragment` (scrollable list of all past daily logs grouped by date).

### 4.6 LogSymptomsFragment
Accepts arguments: `date` (LocalDate string), `entryId` (Int, -1 = new), `scrollTo` (String, section to auto-scroll).

Sections:
- **Cycle banner**: shows current cycle day, phase, and whether period is active.
- **Flow**: chip group — Spotting / Light / Medium / Heavy.
- **Moods**: multi-select chips — Happy, Calm, Sensitive, Sad, Irritable, Anxious, Tired, etc.
- **Symptoms**: multi-select chips — Cramps, Bloating, Headache, Tender Breasts, Acne, Back Pain, Nausea, etc.
- **Cervical Fluid**: chip group — Dry / Creamy / Watery / Egg White.
- **LH Test Result**: chip group — Not Tested / Negative / Positive.
- **Basal Body Temperature (BBT)**: number picker (°F), default 97.8°F.
- **Notes**: free-text.
- **Save button**: saves/updates `DailyLog` row; if flow is logged and no active period, prompts `PeriodStartConfirmationDialog`.

### 4.7 ProfileFragment
Displays and allows editing of all user settings via bottom sheets:

| Section | Bottom Sheet |
|---|---|
| Name / profile photo | `EditProfileSheet` |
| Age | `AgeSheet` |
| Height | `HeightSheet` |
| Weight | `WeightSheet` |
| Cycle length | `CycleLengthSheet` |
| Period duration | `PeriodDurationSheet` (via `PeriodStartSheet`) |
| Last period date | `LastPeriodSheet` |
| Activity level | `ActivitySheet` |
| Health conditions | `ConditionsSheet` |
| Notification toggles | In-screen switches (period reminder, ovulation alert, daily log) |
| Export report | Opens `PdfReadySheet` → generates PDF |

---

## 5. Data Layer

### 5.1 Entities (Room Tables)

**`UserSettings`** — one row per user
```
id INT (PK)          userName TEXT        goal TEXT
age INT              heightCm FLOAT       weightKg FLOAT
cycleLength INT      periodDuration INT   lastPeriodStart TEXT (ISO date)
activityLevel TEXT   conditions TEXT      onboardingComplete BOOLEAN
notifyPeriod BOOLEAN notifyOvulation BOOLEAN notifyDailyLog BOOLEAN
```

**`PeriodEntry`** — one row per period start
```
id INT (PK, autoincrement)
startDate TEXT (UNIQUE)     endDate TEXT
cycleLength INT              flow TEXT
notes TEXT
```

**`DailyLog`** — multiple rows per date (entryNumber distinguishes them)
```
entryId INT (PK, autoincrement)
date TEXT                    entryNumber INT
flow TEXT                    moods TEXT (comma-separated)
symptoms TEXT (comma-separated)
cervicalFluid TEXT           lhTestResult TEXT
basalTemp REAL               notes TEXT
loggedAt INT (epoch ms)
```

### 5.2 Database Migrations (v1 → v6)
| Version | Change |
|---|---|
| 1→2 | Recreated `daily_logs` — PK changed from `date` to autoincrement `entryId` + added `entryNumber` |
| 2→3 | Added `loggedAt` timestamp column to `daily_logs` |
| 3→4 | Added `lhTestResult` column to `daily_logs` |
| 4→5 | Added `flow` column to `period_entries` |
| 5→6 | Added `UNIQUE` constraint on `period_entries.startDate`, deduped existing rows |

> ⚠️ **Leftover "LunaCycle" name** — the SQLite file on-device is still named `lunacycle.db` (line 115 of `AppDatabase.kt`). This is invisible to users but should be renamed to `period_tracker.db` (with a migration strategy) before any production release so the branding is consistent.

### 5.3 CycleRepository
Single source of truth. Wraps all three DAOs. Key methods:
- `getSettings()` / `saveSettings()`
- `insertPeriodEntry()` / `getAllPeriodEntries()`
- `saveDailyLog()` / `getLogsForDate()` / `getAllLogs()`

### 5.4 CycleEngine (Pure Logic)
Stateless `object` — no Android imports, fully unit-testable.

**A. Cycle Engine**
- `cycleDay(lastStart, cycleLength)` — 1-based day clamped to [1, cycleLength]
- `isPeriodLate(lastStart, cycleLength)` — true if raw day > cycleLength
- `daysLate(...)` — days past expected start
- `nextPeriodDate(lastStart, cycleLength)` — lastStart + cycleLength
- `daysUntilNextPeriod(...)` — days remaining
- `weightedAverageCycleLength(list)` — last 6 cycles, newest weighted most; outliers (< 18 or > 60 days) ignored; default 28

**B. Ovulation Engine**
- BBT sustained shift detection (3-day rule)
- Cervical fluid progression scoring
- LH surge detection
- Fertile window: typically cycle days ~10–17 (configurable)

**C. Symptom Engine**
- PMS detection (symptom cluster in luteal phase)
- Never modifies period or ovulation dates

---

## 6. Ads System

All ad logic lives in `ads/`. Remote Config controls which ad types are active — no re-release needed to enable/disable ads.

### 6.1 Ad Unit IDs (AdConstants.kt)
Currently using **AdMob test IDs** — replace with production IDs before release.

| Ad Type | Constant | Test Unit ID |
|---|---|---|
| App Open | `APP_OPEN_AD_UNIT_ID` | `ca-app-pub-3940256099942544/9257395921` |
| Banner | `BANNER_AD_UNIT_ID` | `ca-app-pub-3940256099942544/9214589741` |
| Interstitial | `INTERSTITIAL_AD_UNIT_ID` | `ca-app-pub-3940256099942544/1033173712` |
| Native | `NATIVE_AD_UNIT_ID` | `ca-app-pub-3940256099942544/2247696110` |
| Rewarded | `REWARDED_AD_UNIT_ID` | `ca-app-pub-3940256099942544/5224354917` |

### 6.2 Preload Keys
| Key | Where Used |
|---|---|
| `splash_interstitial` | SplashActivity |
| `onboarding_interstitial` | OnboardingActivity |
| `main_activity_interstitial` | Tab navigation in MainActivity |
| `back_press_interstitial` | Back-press in MainActivity |
| `shared_interstitial_preload` | Shared general preload |
| `app_open_ad` | AppOpenAdManager |

### 6.3 Remote Config Flags (AdsRemoteConfig.kt)
Firebase Remote Config keys (all Boolean, default `false`):
- `show_splash_banner`
- `show_onboarding_interstitial`
- `show_main_activity_interstitial`
- `show_back_press_interstitial`
- `show_app_open_ad`
- `show_native_ad`
- *(helper: `isAnyAdEnabled()` — OR of all flags)*

### 6.4 Ad Flow
```
App Launch
  └── SplashActivity.initializeFirebase()
        └── AdsRemoteConfig.load()   ← fetches & activates Remote Config
              └── if any ad enabled → initializeAds() (background thread)
                    └── MobileAds.initialize()
                          └── loadBannerAd() + startSplash()
```

### 6.5 AppOpenAdManager
- Loaded once after SDK init.
- Shown when app is foregrounded from background (uses `LifecycleObserver`).
- Only shows if `show_app_open_ad` flag is true.

---

## 7. Notifications

Three notification channels:

| Channel | ID | Importance | When Triggered |
|---|---|---|---|
| Period Reminder | `period_reminder` | HIGH | Day before predicted period ⚠️ |
| Ovulation Alert | `ovulation_alert` | HIGH | Predicted ovulation day |
| Daily Log Reminder | `daily_log` | DEFAULT | Every day at user-configured time |

> ⚠️ **Leftover "LunaCycle" name** — `NotificationHelper.kt` line 56 hardcodes the notification body text as *"Tap to open LunaCycle."* This must be changed to *"Tap to open Period Tracker."* before release.

**AlarmScheduler**: sets exact alarms (`setExactAndAllowWhileIdle`) for period and ovulation notifications. Uses `AlarmManager`.

**BootReceiver**: listens for `BOOT_COMPLETED` and re-schedules all alarms (they are cleared on device restart).

**PeriodConfirmReceiver**: handles notification action buttons ("Did your period start? **Yes** / **Not Yet**") — logs a new `PeriodEntry` on "Yes" without the user opening the app.

**DailyLogReminderWorker**: `PeriodicWorkRequest` via WorkManager, fires daily.

---

## 8. Permissions

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Push notifications (runtime, Android 13+) |
| `RECEIVE_BOOT_COMPLETED` | Reschedule alarms after reboot |
| `SCHEDULE_EXACT_ALARM` | Precise period/ovulation alarms |
| `USE_EXACT_ALARM` | Android 13+ variant of above |
| `WRITE_EXTERNAL_STORAGE` | PDF export (Android ≤ 9 only, maxSdkVersion 28) |
| `INTERNET` | Firebase, AdMob |
| `ACCESS_NETWORK_STATE` | Ad SDK network checks |

---

## 9. Theming & Design Language

**Primary colour palette:**
| Name | Hex | Usage |
|---|---|---|
| `btncolor` | `#E4A850` | Primary buttons, active states |
| `textcolor` | `#2A2A2A` | Body text |
| `card_bg` | `#FFFFFF` | Card backgrounds |
| `card_text` | `#4C4C4C` | Secondary card text |
| `hintTextColor` | `#4C4C4C` | Input hints |
| `blue` | `#197AEF` | Links, info |
| `gray` | `#808080` | Disabled states |

**Gradient backgrounds** used on most screens:
- Splash: `#F0E6FA` → `#FDE8F3` → `#FFFFFF` (top-left to bottom-right)
- Fragments: similar soft lavender/pink gradients

**Custom Components:**
- `CycleCalendarView` — fully custom month grid
- `CycleBarChartView` — custom bar chart (no third-party charting lib)
- `CustomBottomNavBinding` — custom layout replacing standard `BottomNavigationView`
- `ViewStepHeader` — onboarding step indicator with back button
- Shimmer placeholder for native ads while loading

**Edge-to-edge** display is enabled everywhere with proper `WindowInsetsCompat` handling.

---

## 10. Key Dependencies

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 1.9.24 | Language |
| AGP | 9.3.2 | Build tooling |
| AndroidX Core KTX | 1.19.0 | Core extensions |
| Fragment KTX | 1.9.0 | Fragment APIs |
| ViewPager2 | 1.1.0 | Onboarding pager |
| Room | 2.8.5 | Local database |
| ViewModel + LiveData | 2.11.0 | Reactive UI state |
| Coroutines Android | 1.11.0 | Async/background work |
| DataStore Preferences | 1.2.1 | Lightweight key-value (onboarding flag) |
| WorkManager KTX | 2.11.2 | Daily log reminder |
| Material Components | 1.14.0 | Material chips, dialogs, sheets |
| ConstraintLayout | 2.2.2 | Layouts |
| Firebase BoM | 33.7.0 | Firebase version management |
| Firebase Analytics | — | User analytics |
| Firebase Remote Config | — | Ad flag control |
| AdMob SDK | 1.5.0 | Monetisation |
| Shimmer Android | 1.0.0 | Ad loading placeholder |

---

## 11. What Is NOT Yet Built (Future Work)

- Pregnancy mode UI (goal captured in onboarding, not yet surfaced)
- Partner / sharing features
- Backup & restore to cloud
- Dark mode full implementation (values-night has only a stub theme)
- Wear OS / widget companion
- Production AdMob unit IDs (currently using test IDs throughout)
- App signing & release keystore configuration

---

## 12. Release Checklist (Before Publishing)

- [ ] Replace all test AdMob unit IDs in `AdConstants.kt` with production IDs
- [ ] Set Firebase Remote Config default values for ad flags
- [ ] Enable ProGuard/R8 minification (`isMinifyEnabled = true` in release build type)
- [ ] Configure release signing in `build.gradle.kts`
- [ ] Increment `versionCode` and `versionName`
- [ ] Verify `google-services.json` is for the production Firebase project
- [ ] Test all notification channels on a physical device
- [ ] Test "Did period start? Yes / Not yet" notification actions
- [ ] Confirm PDF export works on Android 9 and Android 13+
- [ ] Run full onboarding flow on a clean install
