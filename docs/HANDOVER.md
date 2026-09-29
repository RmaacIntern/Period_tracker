---
title: "Handover Note — Period Tracker"
date: 2026-09-29
developer: Adnan Arshad
reviewer: Muneeb (Program Manager), Shezrah Abbasi (Product Lead)
tip: b76d8c1
---

# Handover Note — Period Tracker

**Developer:** Adnan Arshad
**Internship period:** 2026-09-14 to 2026-09-30
**Repo:** https://github.com/RmaacIntern/Period_tracker
**Final commit:** b76d8c1

---

## 1. What Is Unfinished

This is the complete list — not the flattering subset.

| # | Item | Why it matters | Effort to finish |
|---|---|---|---|
| U01 | **SplashActivity onboarding check is bypassed** — app always goes to onboarding on every launch | A returning user sees onboarding every time they open the app | 30 minutes — one `if` statement and a DataStore read |
| U02 | **All AdMob unit IDs are Google test IDs** — no production ad revenue is possible | The app cannot be monetised until real IDs are in `AdConstants.kt` | 1 hour — obtain IDs from AdMob console, replace 6 constants, rebuild |
| U03 | **No release keystore** — no signed release APK exists | Play Store requires a signed APK | Half a day — generate keystore, configure `build.gradle.kts` release block, sign and verify |
| U04 | **No Play Store listing** — app has never been submitted | The app is not publicly available | 1–2 days — screenshots, store description, privacy policy, content rating |
| U05 | **Privacy policy does not exist** — required by Play Store for health data | Submission will be rejected without it | Half a day — legal template + hosting |
| U06 | **Production Firebase project not confirmed** — `google-services.json` is from a dev project | If the dev project is deleted or reconfigured, Firebase and ads break | 1 hour — create production project, register `com.aivigil.periodtracker`, download new `google-services.json` |
| U07 | **`isMinifyEnabled = false` in release build** — ProGuard/R8 not enabled | APK is larger than needed; code is not obfuscated | 2–4 hours — enable minification, add ProGuard rules for Room, AdMob, Firebase, test that nothing breaks |
| U08 | **No QA report** — the app has never been tested against a formal test matrix on multiple devices | Unknown failure modes on devices not tested | 1–2 days minimum |
| U09 | **Dark mode is a stub** — `values-night/themes.xml` has no colour tokens | App looks broken in system dark mode | 1 day — define dark palette tokens, test all screens |
| U10 | **Pregnancy mode / fertility UI does not exist** — goal is stored from onboarding but nothing in the app changes based on it | Users who selected "Ovulation & Fertility" get the same screens as everyone else | 2–3 days — design and build goal-specific UI |
| U11 | **Cycle List tab in Calendar is a placeholder** | Third tab in Calendar opens nothing | Half a day — build the list view of past cycles |
| U12 | **Year view in Calendar not built** | Second tab in Calendar opens nothing | 1 day |
| U13 | **CSV data export not built** | Profile has no data export other than PDF | Half a day |
| U14 | **BBT / LH / cervical fluid have no effect on predictions** — signals are logged and stored but ignored | Users who carefully log BBT and LH results get no benefit from doing so | 3–5 days — rebuild signal-based prediction layer on top of current formula baseline |
| U15 | **Package name `com.aivigil.periodtracker` — confirm `aivigil` is the correct company identifier** | Once published to Play Store the package name can never be changed | Decision needed from Muneeb before first submission |
| U16 | **No compliance worksheet** | Required by intern programme documentation standard | Half a day |

---

## 2. What I Would Do Next, In Priority Order

| Priority | Task | Reason |
|---|---|---|
| 1 | Fix U01 — restore onboarding check | Without this, no one can test the app as a real returning user. Everything else depends on being able to run the app normally. 30 minutes. |
| 2 | Fix U03 — create release keystore and signed build | Needed before any internal testing can happen on real devices without USB. Needed before Play Store. |
| 3 | Fix U02 — production AdMob IDs | Test IDs serve test ads only. Revenue requires real IDs. |
| 4 | Fix U06 — production Firebase project | The dev Firebase project should not be the production backend. A misconfiguration there breaks the whole app remotely. |
| 5 | Fix U05 + U04 — privacy policy and Play Store listing | These can be done in parallel. Neither requires code changes. |
| 6 | Fix U07 — enable ProGuard/R8 | Should be done before Play Store submission, not after. Enabling it late often surfaces new crashes. |
| 7 | U08 — QA on multiple devices | Minimum: test on API 26 (minSdk), API 30, API 33 (POST_NOTIFICATIONS runtime permission), one physical device. |
| 8 | U09 — dark mode | Low user impact for now but will generate negative reviews if left broken post-launch. |
| 9 | U10 — pregnancy/fertility UI | The goal is already captured in onboarding — building the UI is the logical next step to differentiate the app. |
| 10 | U14 — signal-based prediction | This is the most valuable long-term feature but also the highest risk. Do it after the app is stable and you have enough logged data to validate the model. |

---

## 3. What I Got Wrong and What It Cost

These are the mistakes that had the highest cost — in time, in broken features, or in
needing to undo and redo work. Full per-session details are in the session logs.

**1. Starting development without a repository.**
I wrote 15 days of code before pushing to GitHub. This meant: no backup, no version history
anyone else could verify, no way to diff what changed between sessions, and the gap analysis
score of 3/10 on documentation — because nothing was verifiable. **Cost: the entire two-day
remediation period.** If I had pushed on Day 1, the documentation gaps would have been caught
and fixed incrementally, not in one emergency session the day before the deadline.

**2. Using `date` as the Room PrimaryKey for `DailyLog`.**
I assumed one log entry per day was enough. By Day 4 (Sep 18) it was clear users need to log
multiple times per day. Changing the PK required dropping and recreating the table (MIGRATION_1_2)
and a second migration to add `loggedAt` (MIGRATION_2_3). **Cost: half a day of migration work
and testing** that could have been avoided by designing the schema correctly on Day 1.

**3. Building `CycleDataOverviewFragment` and then deleting it.**
I built a log history screen on Sep 18, then built a better version (`PastLogHistoryFragment`)
on Sep 22, then deleted the first one on Sep 24. Both screens did the same thing.
**Cost: one day of wasted work.** I should have designed the screen once before building it.

**4. The averaging prediction engine — building and then replacing it.**
I built a weighted-average prediction engine with BBT, cervical fluid, and LH signal detection.
It had three real bugs: it silently overwrote the user's cycle length setting with a computed
average, it caused ovulation alarms to fire on past dates, and the confidence score (10–95%)
was unexplainable to users. I replaced it on Sep 24 with a single formula.
**Cost: approximately three days of work discarded.** The signals are still logged and stored —
they can be used in a future version — but the prediction engine had to be rebuilt from scratch.

**5. Not testing notifications end-to-end until Sep 25.**
The alarms were scheduling correctly (visible in Logcat) but notifications never appeared on
device. The root cause was two missing `intent-filter` entries in `AndroidManifest.xml` —
`AlarmReceiver` and `PeriodConfirmReceiver` were never registered to receive their broadcasts.
**Cost: two hours of debugging** something that a one-minute manifest check would have caught.

**6. Not checking what the package rename does to XML files.**
After renaming the package, the build failed with a BOM (byte order mark) error on 12 layout
files. Android Studio's refactor tool re-saved them with a UTF-8 BOM at byte 0, which the
XML parser rejects. **Cost: three build attempts and a PowerShell script** to strip the BOMs
from all affected files.

---

## 4. What Took Longest and Why

| What | Time estimate | Why it took that long |
|---|---|---|
| Gap analysis remediation (documentation) | ~2 full days | 15 days of development with no repository meant everything had to be written retroactively in 48 hours — session logs, SPEC.md, README.md, this document, all session log amendments. None of this would have taken this long if documentation had been written alongside the code from Day 1. |
| Notification system debugging | ~4 hours across Sep 24–25 | Two separate bugs in two separate files (AlarmScheduler and BootReceiver) caused the alarm to fire on wrong dates. Then a missing manifest entry meant the alarms fired but nothing caught them. Each bug looked like the previous one was still present. |
| Package rename and BOM fix | ~3 hours | The rename itself was fast. Finding and fixing the BOM corruption on 12 files required multiple build attempts because the build stops at 2 errors at a time, not all of them at once. |
| Ads integration | ~1 day | Six classes, three activities, Remote Config, Firebase init sequence, SDK init on background thread, banner + interstitial + app-open + native — each with its own lifecycle. The classes were adapted from a previous project which saved approximately 2 days. |
| Prediction engine (built and rebuilt) | ~3 days total | One day to build the signal-based engine. One day to debug the alarm bugs it caused. One day to replace it with the simpler formula and verify predictions were correct. |

---

## 5. What I Would Tell the Next Developer

Three things that are not in any other document:

**First:** The prediction engine is intentionally simple. `nextPeriod = lastPeriodStart + cycleLength`.
Do not add complexity to it without first logging 6+ confirmed cycles from real users and
verifying that a signal-based model actually improves accuracy for them. The averaging engine
I built earlier was more sophisticated and less accurate in practice because it silently
overrode the user's own input.

**Second:** Every ad placement is controlled by a single Firebase Remote Config flag. Before
doing anything with ads — testing, disabling, changing frequency — change the flag in the
Firebase Console first. Do not hardcode ad behaviour in Kotlin.

**Third:** The `docs/` folder is the starting point, not an afterthought. `APP-BRIEFS.md`
has the full architecture. `DESIGN-STANDARD.md` has every colour token and component pattern.
`NEW-APP-RUNBOOK.md` has the 16 gates to run every morning. If you skip these and write code
directly, you will introduce inconsistencies that are expensive to fix later.

---

## Sign-off

**Developer:** Adnan Arshad
**Date:** 2026-09-29
**Repo at handback:** https://github.com/RmaacIntern/Period_tracker
**Final commit SHA:** b76d8c1
