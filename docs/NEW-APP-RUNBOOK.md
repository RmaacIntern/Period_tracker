# NEW-APP-RUNBOOK.md — Period Tracker
> **Every-Day Checklist.** Run these 16 gates in order before you write a single line of code. Each gate is a question. If the answer is No, fix it before moving to the next gate. A gate does not pass until the answer is Yes.

---

## The 16 Gates

```
Gate 1  →  Repo & Branch
Gate 2  →  Build Passes Clean
Gate 3  →  Device / Emulator Alive
Gate 4  →  Firebase Connected
Gate 5  →  Remote Config Flags
Gate 6  →  Ads SDK Init
Gate 7  →  Room DB Migration
Gate 8  →  Onboarding Flow
Gate 9  →  Home Screen Data
Gate 10 →  Log Symptoms Save
Gate 11 →  Notifications
Gate 12 →  Design Tokens
Gate 13 →  No LunaCycle Regressions
Gate 14 →  No Hardcoded Test Ad IDs in New Code
Gate 15 →  Logcat Clean
Gate 16 →  Commit Message & Push
```

---

## Gate 1 — Repo & Branch

**Question: Am I on the right branch, with no uncommitted mess from yesterday?**

```bash
git status          # should show "nothing to commit" or only today's intended changes
git branch          # confirm you're on the correct feature branch, not main
git log --oneline -5  # last 5 commits — does yesterday's work show up?
```

| Check | Pass condition |
|---|---|
| No stray files from a different task | `git status` shows only files for today's task |
| Branch name matches today's task | e.g. `feat/calendar-fragment`, not `main` |
| Yesterday's commit is in the log | The last push is visible |

**If branch is wrong:** `git checkout -b feat/<today-task>` off the latest merged base.
**If stray files exist:** stash them — `git stash` — before starting.

---

## Gate 2 — Build Passes Clean

**Question: Does the project build without errors right now, before I touch anything?**

In Android Studio: **Build → Clean Project**, then **Build → Rebuild Project**.
Or from terminal:
```bash
./gradlew clean assembleDebug
```

| Check | Pass condition |
|---|---|
| Zero errors | Build output ends with `BUILD SUCCESSFUL` |
| Zero unresolved symbols | No red underlines in files you did not edit |
| KSP / Room compiles | No `error: [ksp]` lines in build output |

**If build is broken:** Do not start today's task. Fix the build first. Common causes:
- Someone merged a dependency bump that needs `./gradlew --refresh-dependencies`
- A Room entity changed without a migration — bump `version` in `AppDatabase` and add a `Migration` object
- KSP cache stale — delete `app/build/` and rebuild

---

## Gate 3 — Device / Emulator Alive

**Question: Is there a running target that matches the app's minSdk?**

| Check | Pass condition |
|---|---|
| Device/emulator visible in Android Studio | Green device name in toolbar |
| API level ≥ 26 | minSdk is 26 — do not test on API 24/25 |
| Screen unlocked | Locked screens silently drop intents |
| Developer options ON | USB debugging enabled on physical device |

**Recommended test matrix (pick at least one of each column):**

| Column A — Old | Column B — New |
|---|---|
| API 26–28 emulator (tests `WRITE_EXTERNAL_STORAGE` PDF path) | API 33–35 physical or emulator (tests `POST_NOTIFICATIONS` runtime permission) |

**If no device:** Do not write UI code. Write logic/unit tests instead.

---

## Gate 4 — Firebase Connected

**Question: Does the app reach Firebase on this device right now?**

Steps:
1. Run the app in debug mode.
2. Open Logcat, filter tag `AdsRemoteConfig`.
3. Look for the log line: `"=============================="` — this prints after Remote Config loads.

| Check | Pass condition |
|---|---|
| `google-services.json` present | File exists at `app/google-services.json` |
| Firebase init does not throw | No `FirebaseApp initialization failed` in Logcat |
| Remote Config fetch completes | `AdsRemoteConfig` tag appears in Logcat |

**If Firebase fails:** Check internet permission is declared in `AndroidManifest.xml` and the device has a network connection. Confirm `google-services.json` is the correct file for this Firebase project (not a file copied from another project).

---

## Gate 5 — Remote Config Flags

**Question: Are the ad flags in the state I expect for today's work?**

All flags default to `true` in `AdsRemoteConfig.kt`. If you are working on a screen and the ads are interrupting your testing:

Open `AdsRemoteConfig.kt` and temporarily set the relevant flags to `false` locally — **do not commit this change**:

```kotlin
// Temporary local override for testing — REVERT BEFORE COMMIT
var show_main_interstitial = false
var show_back_press_interstitial = false
```

| Flag | What it controls |
|---|---|
| `show_splash_interstitial` | Interstitial shown during splash timer |
| `show_onboarding_interstitial` | Interstitial between onboarding steps |
| `show_main_interstitial` | Interstitial on tab navigation in MainActivity |
| `show_back_press_interstitial` | Interstitial on back-press before exit dialog |
| `show_app_open_ad` | App-open ad when app foregrounded |
| `show_banner` | Standard banner at bottom of screen |
| `show_collapsible_banner` | Collapsible banner variant |
| `show_native` | Native ad placements |
| `interstitial_trigger` | `"time"` or `"onclick"` — how often interstitials fire |
| `timer_interval_seconds` | Seconds between time-based interstitials (default `10`) |
| `ad_click_interval` | Number of clicks before click-based interstitial fires (default `3`) |
| `max_splash_time_ms` | Maximum splash wait time in ms (default `8000`) |

**Before committing:** Confirm with Logcat that all flags are back to their intended production values.

---

## Gate 6 — Ads SDK Init

**Question: Does the AdMob SDK initialise without crashing?**

Filter Logcat by tag `SplashActivity`. You should see this sequence on a fresh app launch:

```
SplashActivity  D  Ads SDK initialized
SplashActivity  D  loadBannerAd()
SplashActivity  D  startSplash()
```

| Check | Pass condition |
|---|---|
| `MobileAds.initialize()` completes | "Ads SDK initialized" in Logcat |
| No `IllegalStateException` | SDK was not called before `MobileAds.initialize()` |
| AdMob App ID in manifest | `com.google.android.gms.ads.APPLICATION_ID` meta-data present |
| Test IDs in use | `AdConstants` shows `ca-app-pub-3940256099942544~…` — these are the Google test IDs |

**Current ad unit IDs (all test — do not use in production builds):**

| Type | Constant | Test ID |
|---|---|---|
| App Open | `APP_OPEN_AD_UNIT_ID` | `ca-app-pub-3940256099942544/9257395921` |
| Banner | `BANNER_AD_UNIT_ID` | `ca-app-pub-3940256099942544/9214589741` |
| Interstitial | `INTERSTITIAL_AD_UNIT_ID` | `ca-app-pub-3940256099942544/1033173712` |
| Native | `NATIVE_AD_UNIT_ID` | `ca-app-pub-3940256099942544/2247696110` |
| Rewarded | `REWARDED_AD_UNIT_ID` | `ca-app-pub-3940256099942544/5224354917` |

**Preload ID → where it fires:**

| Preload ID | Context |
|---|---|
| `splash_interstitial` | SplashActivity only |
| `onboarding_interstitial` | OnboardingActivity between steps |
| `shared_interstitial_preload` | MainActivity tab clicks + back press (shared pool) |
| `app_open_ad` | AppOpenAdManager (app foreground) |

---

## Gate 7 — Room DB Migration

**Question: If I changed any `@Entity` today, did I write the migration?**

Current DB version: **6**. DB name: `lunacycle.db`.

**Every time you change any field in `UserSettings`, `PeriodEntry`, or `DailyLog`:**

1. Bump `version` in `AppDatabase.kt` (e.g. 6 → 7)
2. Add a new `val MIGRATION_6_7 = object : Migration(6, 7) { ... }` in the companion object
3. Register it in `.addMigrations(...)` inside `getInstance()`
4. Write the minimum SQL — `ALTER TABLE … ADD COLUMN …` for new columns, or full table recreation for structural changes

| Check | Pass condition |
|---|---|
| No entity field added/removed/renamed without a migration | Check git diff on all `data/entity/` files |
| `AppDatabase.version` matches the highest migration | If you added `MIGRATION_6_7`, version must be `7` |
| App installs clean on a device that had version 5 or 6 | No `IllegalStateException: Room cannot verify the data integrity` crash |
| App also installs clean on a fresh device (no prior DB) | Fallback to version 1 is handled by migration chain |

**If you get a migration crash:** Never call `fallbackToDestructiveMigration()` in production code. Write the migration properly.

---

## Gate 8 — Onboarding Flow

**Question: Does the first-run flow complete without errors on a clean install?**

Run this check whenever you touch any onboarding file (`OnboardingActivity`, `OnboardingFragment1–8`, `OnboardingPagerAdapter`, `OnboardingViewModel`):

1. Clear app data: **Settings → Apps → Period Tracker → Clear Data** (or wipe emulator).
2. Launch app. It should go to `SplashActivity` → `OnboardingActivity`.
3. Walk all 8 steps.

| Step | Fragment | Collects | Validation |
|---|---|---|---|
| 1 | `OnboardingFragment1` | Name | Required — shows inline error if empty |
| 2 | `OnboardingFragment2` | Goal | Must select one option before Continue |
| 3 | `OnboardingFragment3` | Age | Must select |
| 4 | `OnboardingFragment4` | Height | Must select |
| 5 | `OnboardingFragment5` | Weight | Must select |
| 6 | `OnboardingFragment6` | Last period date | Must select |
| 7 | `OnboardingFragment7` | Cycle length | Must select |
| 8 | `OnboardingFragment8` | Activity level + conditions | Conditions optional |

| Check | Pass condition |
|---|---|
| All 8 steps reachable | ViewPager2 advances on Continue, retreats on Back |
| Step counter updates | "STEP X OF 8" label and progress bar fill correctly |
| Continue on step 8 opens MainActivity | Not OnboardingActivity again |
| Returning user (data already set) skips onboarding | Splash goes straight to MainActivity |
| `UserSettings` row exists in DB after completion | Verify via database inspector or a log statement |

---

## Gate 9 — Home Screen Data

**Question: Does the Home screen show real data from the database, not placeholder text?**

| UI element | What it shows | Where data comes from |
|---|---|---|
| `tvAppLabel` | "Period Tracker" | Hardcoded string — check for "LunaCycle" regression |
| `tvGreeting` | "Hi [name] 👋" | `UserSettings.userName` via `CycleViewModel` |
| `tvPhaseSubheading` | Current phase label + description | `CycleEngine` computed from `lastPeriodStart` |
| `cycleRing` | Animated ring, % filled = cycleDay / cycleLength | `CycleEngine.cycleDay()` |
| `tvPeriodCardValue` | Predicted next period date | `CycleEngine.nextPeriodDate()` |
| `tvFertileCardValue` | Fertile window start | Computed in `CycleViewModel` |
| `tvOvulationCardValue` | Ovulation date | Computed in `CycleViewModel` |
| `tvFlowValue`, `tvMoodValue` etc. | Today's log summary | `DailyLog` for today from Room |

| Check | Pass condition |
|---|---|
| No placeholder text visible ("Oct 30", "Energetic", default XML values) | All TextViews bound to real LiveData |
| Ring fills to a non-zero value | `CycleEngine.cycleDay()` returning > 0 |
| Greeting uses the user's actual name | `UserSettings.userName` loaded from Room |
| Phase label makes sense for today's date | Matches the cycle day number |

---

## Gate 10 — Log Symptoms Save

**Question: Can I log symptoms for today and have them persist across a restart?**

Test flow:
1. On Home, tap **Log Today's Symptoms**.
2. Select a flow (e.g. Light), two moods, two symptoms.
3. Set BBT to a non-default value.
4. Tap **Save**.
5. Close the app fully. Reopen.
6. Navigate back to Log Symptoms for today.

| Check | Pass condition |
|---|---|
| Save does not crash | No exception in Logcat on save |
| `isSaving` guard prevents double-save | Tapping Save twice does not create two rows |
| Reopening shows the same selections | Chips re-check, BBT shows the saved value |
| Home screen today's summary reflects the save | `tvFlowValue`, `tvMoodValue` update |
| If flow is logged and no active period, dialog appears | `PeriodStartConfirmationDialog` shows |
| Past date logging works | Open `LogSymptomsFragment` with a date 3 days ago, save, reopen — data persists |

**Key entity fields to verify (`DailyLog`):**
- `date` — ISO string `"YYYY-MM-DD"`
- `entryNumber` — `1` for first entry of the day
- `loggedAt` — epoch milliseconds, not `0`
- `flow`, `moods`, `symptoms` — comma-separated strings, not empty arrays

---

## Gate 11 — Notifications

**Question: Are the notification channels registered and do alarms schedule without crashing?**

| Check | How to verify |
|---|---|
| Three channels exist | Filter Logcat for `NotificationHelper` on fresh launch — channels created in `onCreate` |
| Period reminder schedules at D-1, 9am | Open `AlarmScheduler` in debugger; verify `triggerMillis` > `System.currentTimeMillis()` |
| Ovulation alarm schedules at ovulationDate − 5 days, 9am | Same check |
| Past alarms are skipped, not scheduled | `AlarmScheduler.scheduleExact()` logs a warning and returns for past dates |
| `BootReceiver` declared in manifest | Check `AndroidManifest.xml` — `BOOT_COMPLETED` receiver must be `android:exported="true"` |
| `POST_NOTIFICATIONS` requested at runtime | On API 33+, permission dialog appears on first Home launch |
| "Yes / Not Yet" actions in period notification work | Requires a physical device; tap action button, verify `PeriodConfirmReceiver` fires |

**Notification channels:**

| Channel ID | Name | Importance |
|---|---|---|
| `period_reminder` | Period Reminder | HIGH |
| `ovulation_alert` | Ovulation Alert | HIGH |
| `daily_log` | Daily Log Reminder | DEFAULT |

**Fix the notification text before release** — `NotificationHelper.kt` line 56 still says "Tap to open LunaCycle." Change to "Tap to open Period Tracker."

---

## Gate 12 — Design Tokens

**Question: Does every new view I added today use only the values from `DESIGN-STANDARD.md`?**

Run a visual check on every layout file you created or edited:

| Token category | Allowed values — no others |
|---|---|
| **Background colours** | `#FBF5F8` (page), `#FDF0F5` (hero), `#FFFFFF` (card/sheet), tinted via `roundedBg()` in Kotlin |
| **Primary text** | `#2D1B33` |
| **Secondary text** | `#8A7A8F` |
| **Pink accent** | `#EC4899` |
| **Purple accent** | `#A855F7` |
| **Deep purple** | `#7C3AED` |
| **Text sizes** | `8sp 9sp 10sp 11sp 12sp 13sp 14sp 15sp 16sp 18sp 20sp 22sp 26sp 28sp 32sp` |
| **Corner radii** | `4dp 10dp 12dp 14dp 16dp 20dp 22dp 50dp 100dp` (via `roundedBg()` in Kotlin) |
| **Card elevation** | `0dp` — no elevation on cards |
| **Bottom nav elevation** | `12dp` only |
| **Horizontal screen padding** | `20dp` (fragment), `24dp` (sheet), `16dp` (chip row), `40dp` (onboarding) |

**Red flags — fix immediately if found:**
- `android:background="#FFFFFF"` on a card in XML → move to `roundedBg("#FFFFFF", 22f)` in Kotlin
- `CardView` used anywhere → replace with `ConstraintLayout` + `roundedBg()`
- A colour not in the token list → remove it
- `visibility="gone"` on a bottom nav label → change to `visibility="invisible"`
- `elevation` on a card → remove it

---

## Gate 13 — No LunaCycle Regressions

**Question: Did I accidentally introduce new "LunaCycle" references today?**

Run a quick grep before every commit:

```bash
grep -rn "LunaCycle\|lunacycle\|luna_cycle\|LunaCycle" \
  app/src/main/java/ \
  app/src/main/res/ \
  app/src/main/AndroidManifest.xml
```

**Known existing occurrences to fix (do not add more):**

| File | Line | Issue | Fix |
|---|---|---|---|
| `notification/NotificationHelper.kt` | 56 | `"Tap to open LunaCycle."` | Change to `"Tap to open Period Tracker."` |
| `data/db/AppDatabase.kt` | 115 | `"lunacycle.db"` | Rename to `"period_tracker.db"` with a migration strategy |
| `values-night/themes.xml` | stub | `Base.Theme.LunaCycle` | Rename to `Base.Theme.PeriodTracker` |

**Pass condition:** `grep` returns zero results for any file you edited today. The existing known occurrences in untouched files are tracked — they do not block today's gate, but do not add to them.

---

## Gate 14 — No Hardcoded Test Ad IDs in New Code

**Question: Did any new file I created today hardcode an ad unit ID as a string literal?**

```bash
grep -rn "ca-app-pub-" app/src/main/java/
```

**Pass condition:** Every `ca-app-pub-…` string lives only in `AdConstants.kt`. No new file references ad IDs directly. Any new ad placement pulls from `AdConstants`:

```kotlin
// ✅ Correct
AdConstants.BANNER_AD_UNIT_ID

// ❌ Wrong — never do this in a new file
"ca-app-pub-3940256099942544/9214589741"
```

This also prevents accidentally committing test IDs when production IDs are swapped in for a release.

---

## Gate 15 — Logcat Clean

**Question: After running the app end-to-end, is Logcat free of errors and unexpected warnings for the code I touched?**

Filter Logcat: **No Filters → select "Error"** first. Then review **Warn**.

| What to look for | Action |
|---|---|
| `FATAL EXCEPTION` / crash | Fix before committing — never commit a crasher |
| `Room cannot verify data integrity` | Missing migration — see Gate 7 |
| `IllegalStateException` from AdMob | SDK called before `MobileAds.initialize()` — check call order in `SplashActivity` |
| `WindowManager: android.view.WindowLeaked` | Dialog/ad shown after Activity is destroyed — add `isFinishing` / `isDestroyed` guard |
| `NetworkSecurityConfig: No Network Security Config` | Not critical; ignore |
| `W/AdsRemoteConfig` | Remote Config fetch failed — check network on device |
| `AlarmScheduler: skipping past alarm` | Expected for past dates — not an error |
| Any tag from code you wrote today with `E/` prefix | Investigate and resolve |

**Pass condition:** Zero `E/` (error) lines from your own code tags. Any framework-level errors that existed before your changes do not block this gate, but document them.

---

## Gate 16 — Commit Message & Push

**Question: Is my commit message clear, scoped, and pushed to the remote?**

Commit message format:
```
<type>(<scope>): <short description>

<optional body — what changed and why, not how>
```

**Types:**
- `feat` — new feature or screen
- `fix` — bug fix
- `refactor` — code reorganisation, no behaviour change
- `style` — design/layout change only
- `docs` — documentation only (SPEC.md, README, this file)
- `chore` — build config, dependency update, cleanup

**Scope** = the module/screen: `splash`, `onboarding`, `home`, `calendar`, `insights`, `log`, `profile`, `ads`, `db`, `notif`, `design`

**Examples:**
```
feat(home): add cycle ring animation and phase label binding
fix(ads): guard showSplash against destroyed activity
style(home): apply DESIGN-STANDARD chip colours to log section
docs(project): add DESIGN-STANDARD.md and NEW-APP-RUNBOOK.md
chore(db): bump Room to v7, add MIGRATION_6_7 for conditions column
```

**Push:**
```bash
git add -p          # review every hunk — no debug code, no local overrides
git commit -m "feat(calendar): colour-code period/fertile/ovulation days"
git push origin feat/calendar-fragment
```

| Check | Pass condition |
|---|---|
| No `show_main_interstitial = false` debug lines committed | Check `AdsRemoteConfig.kt` |
| No `TODO: remove` comments left in committed code | Search for `TODO` before staging |
| No `Log.d` calls with sensitive user data | Logs show IDs and states, not personal health data |
| Push succeeds | `git push` exits 0, branch visible on remote |

---

## Daily Gate Summary Card

Print or pin this. Check each box before closing Android Studio.

```
□  Gate 1   Repo & branch correct, no stray files
□  Gate 2   Build passes clean — zero errors
□  Gate 3   Device / emulator running, API ≥ 26
□  Gate 4   Firebase connects, Remote Config loads
□  Gate 5   Ad flags in the right state for today's work
□  Gate 6   AdMob SDK initialises, test IDs confirmed
□  Gate 7   Every entity change has a migration
□  Gate 8   Onboarding 8-step flow completes (if touched)
□  Gate 9   Home screen shows real data, not placeholders
□  Gate 10  Log Symptoms saves and persists across restart
□  Gate 11  Notification channels registered, alarms schedule
□  Gate 12  All new views use DESIGN-STANDARD tokens only
□  Gate 13  Zero new "LunaCycle" references in edited files
□  Gate 14  Zero hardcoded ad IDs outside AdConstants.kt
□  Gate 15  Logcat clean — zero errors from your code
□  Gate 16  Commit message formatted, pushed to remote
```

---

## Appendix A — Quick Reference: Key Files

| File | Why you'd open it |
|---|---|
| `AdConstants.kt` | Change ad unit IDs, preload keys, timing constants |
| `AdsRemoteConfig.kt` | Toggle ad types on/off; add a new Remote Config flag |
| `AppDatabase.kt` | Bump DB version; add a migration |
| `CycleEngine.kt` | Change any cycle maths — pure Kotlin, fully testable |
| `CycleRepository.kt` | Add a new DAO query, combine data from multiple tables |
| `NotificationHelper.kt` | Edit notification text/channel; fix "LunaCycle" line 56 |
| `AlarmScheduler.kt` | Change notification timing (D-1 reminder at 9am, etc.) |
| `DESIGN-STANDARD.md` | Before adding any new colour, size, or component |
| `APP-BRIEFS.md` | Remind yourself what a screen is supposed to do |

## Appendix B — Quick Reference: Package Rename (Outstanding Task)

The package is currently `com.example.periodtracker`. Before Play Store submission it must be `com.aivigil.periodtracker` (or the confirmed production package).

Steps when ready:
1. Android Studio → right-click package → **Refactor → Rename**
2. Update `namespace` and `applicationId` in `app/build.gradle.kts`
3. Update `applicationId` in `AndroidManifest.xml` provider authority: `"${applicationId}.provider"`
4. Replace all `com.example.periodtracker` string literals in manifest actions (broadcast receivers)
5. Update `google-services.json` with the matching Firebase project entry for the new package
6. Update `AdConstants.APP_ID` with the production AdMob App ID

Do not attempt this rename in the middle of a feature branch.
