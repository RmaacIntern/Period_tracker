---
title: "SPEC — Period Tracker"
date: 2026-09-29
developer: Adnan Arshad
app: Period Tracker
package: com.aivigil.periodtracker
version: 1.0 (versionCode 1)
tip: b76d8c1
status: in development
---

# SPEC — Period Tracker

---

## 1. What It Does

Period Tracker is an Android app that predicts a user's next period, fertile window, and
ovulation date from two inputs: the date their last period started, and their average cycle
length. Users log daily symptoms, mood, flow, cervical fluid, LH test results, and basal body
temperature (BBT). The app sends push notifications one day before the predicted period and
at the start of the fertile window. A PDF report of cycle history can be exported for a
physician. All data is stored on-device only — nothing is uploaded.

**Prediction formula (fixed):**
- Next period = `lastPeriodStart + cycleLength`
- Ovulation = `nextPeriod − 14 days`
- Fertile window = `ovulation − 5 days` to `ovulation + 1 day`

The user's cycle length setting is never overwritten by the app — it is only changed when the
user explicitly edits it in Profile.

---

## 2. Who It Is For

**Primary user:** Women aged 18–45 who want to track their menstrual cycle and receive
predictions without learning a complex interface. The app assumes no medical knowledge.

**Secondary use case:** Users trying to conceive who want to know their fertile window dates
and ovulation day.

**Not for:** Clinical diagnosis, contraception planning, or users who need a medically
validated prediction model. The app's predictions are estimates based on the user's own
stated cycle length — they are not derived from biological signal analysis.

---

## 3. Features

Each "Done when" is a verifiable condition, not a description.

| # | Feature | Status | Done when |
|---|---|---|---|
| F01 | Splash screen | ✅ Done | App launches, logo is visible, Firebase Remote Config loads within 8 seconds, and app navigates to onboarding or home. Verified on API 26 and API 33. |
| F02 | Onboarding — 8 steps | ✅ Done | A fresh install completes all 8 steps, saves data to Room `user_settings` table (verified via DB inspector), and navigates to MainActivity without crashing. Continue is blocked on step 1 if name is empty or < 2 chars. |
| F03 | Home screen — cycle ring | ✅ Done | Ring fills to `cycleDay / cycleLength` as a fraction. On day 1 of a 28-day cycle the ring shows ~3.6% filled. On day 28 it shows 100%. |
| F04 | Home screen — phase label | ✅ Done | Phase label matches `CycleEngine.phase()` output for the current cycle day. Changes as cycle day advances. |
| F05 | Home screen — stat cards | ✅ Done | Period, Fertile, and Ovulation cards show correct dates derived from `CycleEngine` — verified by comparing displayed dates to manual calculation for a known lastPeriodStart and cycleLength. |
| F06 | Home screen — late period detection | ✅ Done | When `rawCycleDay > cycleLength`, the ring shows day label as "Late" and `daysLate()` returns a positive integer. Verified by setting lastPeriodStart to 35 days ago with cycleLength=28. |
| F07 | Log Symptoms — save | ✅ Done | Tapping Save writes one `DailyLog` row to Room. Re-opening the screen on the same date shows the saved selections pre-filled. Saving twice does not create two rows — `isSaving` flag and `OnConflictStrategy.IGNORE` prevent duplicates. |
| F08 | Log Symptoms — multiple entries per day | ✅ Done | A second save on the same date creates a second row with `entryNumber = 2`. Both rows visible in Past Log History. |
| F09 | Calendar — month grid | ✅ Done | Correct month displayed. Period days shown in red, fertile window in green, ovulation in orange, predicted period in pink. Tapping a day opens Log Symptoms for that date. |
| F10 | Calendar — date colouring accuracy | ✅ Done | Calendar colours match `CycleEngine` prediction for a known input — verified manually for a 28-day cycle starting 2026-09-03. |
| F11 | Insights — cycle bar chart | ✅ Done | One bar per past confirmed period cycle. Bar height represents cycle length in days. Current in-progress cycle shown with a lighter bar and day badge. |
| F12 | Insights — PDF export | ✅ Done | Tapping Export generates a PDF file and opens the Android share sheet. File is readable when opened. Verified on API 28 and API 33. |
| F13 | Profile — edit settings | ✅ Done | Cycle length, period duration, last period date, age, height, weight, activity level, and conditions are all editable via bottom sheets. Changes persist after app restart. |
| F14 | Profile — notification toggles | ✅ Done | On Android 13+, toggling a switch requests `POST_NOTIFICATIONS` permission if not granted. If denied, switch returns to off. If granted, alarm is scheduled. Verified on API 33 device. |
| F15 | Profile — delete all data | ✅ Done | Tapping Delete All → confirming wipes all three Room tables and resets the DataStore onboarding flag. Next launch goes to onboarding. App does not crash after deletion. |
| F16 | Period reminder notification | ✅ Done | Notification appears the day before the predicted period at 9am. Verified by setting `nextPeriodDate` to tomorrow and triggering `AlarmScheduler.schedulePeriodAlarms()`. |
| F17 | Ovulation / fertile window notification | ✅ Done | Notification appears on the first day of the fertile window (ovulationDate − 5) at 9am. Verified by ADB. |
| F18 | Daily log reminder | ✅ Done | WorkManager `PeriodicWorkRequest` fires daily. Notification appears in the daily_log channel. |
| F19 | "Did period start? Yes / Not yet" notification actions | ✅ Done | Tapping "Yes" from the notification calls `CycleRepository.logPeriodStart()` — previous period is closed, new entry created, settings updated. Verified on physical device. |
| F20 | Boot persistence — alarms reschedule | ✅ Done | `BootReceiver` catches `BOOT_COMPLETED` and reschedules all alarms. Verified by rebooting device and confirming alarms are present via ADB. |
| F21 | AdMob banner ads | ✅ Done | Banner loads on splash and MainActivity. Shimmer shown while loading. Banner hidden if Remote Config `show_banner = false`. |
| F22 | AdMob interstitial — splash | ✅ Done | Interstitial shows once per app launch during splash if Remote Config `show_splash_interstitial = true`. |
| F23 | AdMob interstitial — tab navigation | ✅ Done | Interstitial shows on tab switch based on time-mode or click-mode as set by Remote Config. |
| F24 | AdMob interstitial — back press | ✅ Done | Interstitial shows on back press before exit dialog. Counter is independent of tab click counter. |
| F25 | AdMob app-open ad | ✅ Done | App-open ad shows when app is foregrounded from background after splash is finished. |
| F26 | AdMob native ads | ✅ Done | Native ad loads into FrameLayout containers. Shimmer while loading. Container hidden on failure. No MediaView (passes null per Next-Gen SDK docs). |
| F27 | Firebase Remote Config — ad control | ✅ Done | All 8 ad flags fetchable from Firebase Console. Change takes effect within 1 hour without an app update. Verified by toggling `show_banner = false` in Console and confirming banner disappears. |
| F28 | Past log history | ✅ Done | PastLogHistoryFragment shows all logs grouped by date, newest first. Each entry shows time, flow, mood, symptoms, BBT, notes. Delete and edit buttons work. |
| F29 | Exit confirmation dialog | ✅ Done | Back press from home screen shows "Stay / Exit" dialog. Back press from a fragment pops the fragment first without showing the dialog. |
| F30 | Edge-to-edge display | ✅ Done | App renders behind system bars. Window insets applied correctly on all screens. Keyboard does not push banner off screen in onboarding. |

---

## 4. NOT Building This Week

These items were identified during development but are explicitly out of scope for the
current submission. They belong in a future version.

| # | Feature | Why not this week | Who decides to add it |
|---|---|---|---|
| N01 | QA report and compliance worksheet | Not at Gate 10 — app has not been tested against a formal test matrix | Muneeb |
| N02 | Production AdMob unit IDs | Test IDs in use — production IDs require AdMob account approval | Muneeb / Shezrah |
| N03 | Production package signing — release keystore | No release build configured; `isMinifyEnabled = false` in release build type | Muneeb |
| N04 | Dark mode | `values-night/themes.xml` has only a stub; no dark-mode colour tokens defined | Adnan — next version |
| N05 | Pregnancy mode UI | Goal "Ovulation & Fertility" is stored from onboarding but the app has no dedicated UI path for it | Adnan — next version |
| N06 | Cycle list tab in Calendar | Tab exists in the nav bar but opens a placeholder | Adnan — next version |
| N07 | Year view in Calendar | Not built | Adnan — next version |
| N08 | CSV data export | Not built | Adnan — next version |
| N09 | Cloud backup / restore | All data is on-device only | Future version |
| N10 | BBT / LH / cervical fluid as prediction signals | Signals are logged and stored but have zero effect on prediction dates | Deliberate decision — see SESSION-2026-09-24.md §4 |
| N11 | Prediction confidence score | Removed when prediction engine was simplified | Deliberate decision — see SESSION-2026-09-24.md §4 |
| N12 | Partner / sharing features | Not scoped | Future version |
| N13 | Wear OS / home screen widget | Not scoped | Future version |
| N14 | `SplashActivity` onboarding check restored | Currently bypassed for development — always goes to onboarding | Must fix before Play Store — Adnan |
| N15 | `google-services.json` production project | Currently dev Firebase project | Must verify before Play Store — Muneeb |

---

## 5. Monetization

| Ad type | Placement | Remote Config key | Default | Test unit ID |
|---|---|---|---|---|
| Banner | Splash screen bottom | `show_banner` | true | `ca-app-pub-3940256099942544/9214589741` |
| Banner | MainActivity bottom | `show_banner` | true | `ca-app-pub-3940256099942544/9214589741` |
| Collapsible banner | Onboarding screens | `show_collapsible_banner` | true | `ca-app-pub-3940256099942544/9214589741` |
| Interstitial | Splash (once per launch) | `show_splash_interstitial` | true | `ca-app-pub-3940256099942544/1033173712` |
| Interstitial | Between onboarding steps | `show_onboarding_interstitial` | true | `ca-app-pub-3940256099942544/1033173712` |
| Interstitial | Tab navigation | `show_main_interstitial` | true | `ca-app-pub-3940256099942544/1033173712` |
| Interstitial | Back press before exit | `show_back_press_interstitial` | true | `ca-app-pub-3940256099942544/1033173712` |
| Native | In-feed placements | `show_native` | true | `ca-app-pub-3940256099942544/2247696110` |
| App-open | App foreground | `show_app_open_ad` | true | `ca-app-pub-3940256099942544/9257395921` |

**All IDs above are Google test IDs.** Production IDs must be obtained from the AdMob console
and replaced in `AdConstants.kt` before any release build.

**Interstitial trigger mode** is controlled by Remote Config key `interstitial_trigger`:
- `"time"` — shows after `timer_interval_seconds` (default 10s) since last ad
- `"onclick"` — shows every `ad_click_interval` (default 3) tab clicks

---

## 6. Open Questions

| # | Question | Who decides | By when |
|---|---|---|---|
| Q1 | Which Firebase project is production? The current `google-services.json` is from a dev project. A production project with `com.aivigil.periodtracker` as the registered app is needed before any release build. | Muneeb | Before Play Store submission |
| Q2 | Should the SplashActivity onboarding check be restored now, or remain bypassed until the app is ready for release testing? Currently always goes to onboarding on every launch. | Muneeb | Before any user testing |
| Q3 | Production AdMob account — which account owns the production ad units? Test IDs are in use; production IDs are needed. | Muneeb / Shezrah | Before Play Store submission |
| Q4 | Play Store listing package is `com.aivigil.periodtracker`. Is `aivigil` the correct company identifier, or should it be something else? Once published, the package name can never be changed. | Muneeb | Before first Play Store submission |
| Q5 | The app's prediction engine uses a fixed luteal phase of 14 days. Should a medical disclaimer be shown to users clarifying that predictions are estimates, not medical advice? | Shezrah (Product Lead) | Before Play Store submission |
| Q6 | `local.properties` contains the Android SDK path — it is excluded from git. Does anyone else need to build this project, and if so, what SDK setup documentation is needed? | Muneeb | If another developer joins |
