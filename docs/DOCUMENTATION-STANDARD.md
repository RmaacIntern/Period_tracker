# DOCUMENTATION-STANDARD.md — Period Tracker
> **Every-Evening Reference.** Fill in a session log at the end of every working day. This document defines the template, the rules for filling it in, and the file naming convention. Do not write the log from memory — write it while the IDE is still open.

---

## 1. Purpose

A session log is the written record of one day's work. It exists for three reasons:

1. **Handover** — if someone else picks up this project tomorrow, they can read the log and know exactly where you left off without asking you anything.
2. **Gap-analysis evidence** — the intern program scores documentation. A log that is vague, late, or missing counts as a zero. A log that is specific, dated, and committed counts as a ten.
3. **Your own continuity** — the runbook (NEW-APP-RUNBOOK.md) tells you what gates to run each morning. The session log tells you *why* you opened the file you'll be staring at.

---

## 2. File Naming

One file per day. Saved to the `docs/session-logs/` folder at the root of the repo.

```
docs/
└── session-logs/
    ├── SESSION-2026-09-14.md
    ├── SESSION-2026-09-15.md
    ├── SESSION-2026-09-16.md
    └── ...
```

**Format:** `SESSION-YYYY-MM-DD.md`
**Date:** The calendar date the session happened. If you worked past midnight, use the date you *started*, not the date you finished.
**Never:** `session1.md`, `day3.md`, `log.md`, `notes.md` — these are unsortable and untraceable.

---

## 3. The Template

Copy this block exactly. Fill in every section. Do not delete sections you think are not relevant — write "None" or "N/A" in them instead.

---

```markdown
# SESSION LOG — YYYY-MM-DD
**Developer:** Adnan Arshad
**Day number:** [e.g. Day 3 of 8]
**Session start:** HH:MM
**Session end:** HH:MM
**Total hours:** H.H

---

## 1. Goal for Today
<!-- One sentence. What did you set out to build or fix this morning? -->


---

## 2. What Was Completed

### 2a. Files Created
<!-- List every new file. One line each: path + one sentence on what it does. -->
<!-- If none: write "None" -->

| File | What it does |
|---|---|
| `app/src/main/java/…/FileName.kt` | One sentence |

### 2b. Files Modified
<!-- List every file you touched. One line each: path + what changed. -->
<!-- If none: write "None" -->

| File | Change made |
|---|---|
| `app/src/main/java/…/FileName.kt` | Added X, removed Y, fixed Z |

### 2c. Layouts Created or Modified
<!-- Separate table for XML because layouts are often forgotten in logs. -->
<!-- If none: write "None" -->

| Layout file | Change made |
|---|---|
| `res/layout/fragment_xyz.xml` | Added chip group for symptoms |

### 2d. Database Changes
<!-- If you changed any @Entity or bumped AppDatabase.version, document it here. -->
<!-- If none: write "None — DB version stays at [current version]" -->

| Item | Detail |
|---|---|
| DB version before | 6 |
| DB version after | 7 |
| Migration added | `MIGRATION_6_7` — ALTER TABLE daily_logs ADD COLUMN … |
| Entity changed | `DailyLog.kt` — added field `xyz: String` |

---

## 3. Known Issues & Bugs Found
<!-- Anything broken, incomplete, or behaving unexpectedly that you did NOT fix today. -->
<!-- Be specific: file name + line number + what it does wrong + what you think causes it. -->
<!-- If none: write "None found today" -->

| # | File | Line | Description | Suspected cause |
|---|---|---|---|---|
| 1 | `NotificationHelper.kt` | 56 | Text says "LunaCycle" | Hardcoded string, not yet renamed |

---

## 4. Decisions Made
<!-- Any choice you made today that a future developer needs to understand. -->
<!-- Include WHY, not just WHAT. "I chose X because Y and Z" — not just "I used X". -->
<!-- If none: write "No significant decisions today" -->

1. **[Decision title]** — [What you chose and why. What the alternative was and why you rejected it.]

---

## 5. Blockers
<!-- Anything that stopped or slowed you down. -->
<!-- If none: write "No blockers today" -->

| Blocker | How it was resolved (or: still open) |
|---|---|
| Room migration crash on API 26 emulator | Added `fallbackToDestructiveMigration()` temporarily — **must revert before release** |

---

## 6. Tomorrow's Starting Point
<!-- The single most important thing to do first tomorrow morning. -->
<!-- Finish this sentence: "Tomorrow morning, open ___ and do ___." -->
<!-- This section is mandatory — it is what you read at Gate 1 of the runbook. -->

Tomorrow morning, open `___` and ___. 

Then check: ___

---

## 7. Gates Run Today
<!-- Tick the gates from NEW-APP-RUNBOOK.md that you actually ran. -->
<!-- Do not tick a gate you skipped. If you skipped one, note why in the Blockers section. -->

- [ ] Gate 1  — Repo & branch
- [ ] Gate 2  — Build passes clean
- [ ] Gate 3  — Device / emulator alive
- [ ] Gate 4  — Firebase connected
- [ ] Gate 5  — Remote Config flags
- [ ] Gate 6  — Ads SDK init
- [ ] Gate 7  — Room DB migration
- [ ] Gate 8  — Onboarding flow
- [ ] Gate 9  — Home screen data
- [ ] Gate 10 — Log Symptoms save
- [ ] Gate 11 — Notifications
- [ ] Gate 12 — Design tokens
- [ ] Gate 13 — No LunaCycle regressions
- [ ] Gate 14 — No hardcoded ad IDs
- [ ] Gate 15 — Logcat clean
- [ ] Gate 16 — Commit & push

---

## 8. Commit(s) Made Today
<!-- List every commit pushed today. Copy the exact commit hash and message. -->
<!-- If none (session ended without a commit): write "None — work in progress, will commit tomorrow" -->
<!-- and explain what state the working tree is in. -->

| Hash (short) | Message |
|---|---|
| `abc1234` | `feat(home): add cycle ring animation and phase label` |

---

## 9. What the App Can Do Right Now
<!-- This is a capability snapshot — not a to-do list. -->
<!-- One bullet per working feature. Only list things that actually work end-to-end. -->
<!-- Update this every day. The last session log always has the complete current state. -->

- [ ] Splash screen with gradient background and logo
- [ ] Firebase Remote Config loads and controls ad flags
- [ ] AdMob SDK initialises; banner shows on splash
- [ ] Onboarding (8 steps) collects name, goal, age, height, weight, last period, cycle length, conditions
- [ ] MainActivity with custom bottom nav (Home, Calendar, Insights, Profile)
- [ ] Home: cycle ring, phase label, stat cards, predictions, Log Today button
- [ ] Calendar: month grid with period/fertile/ovulation colour coding
- [ ] Log Symptoms: flow, mood, symptoms, cervical fluid, LH test, BBT, notes — saves to Room
- [ ] Insights: bar chart of past cycles, PDF export
- [ ] Profile: edit all settings via bottom sheets, notification toggles
- [ ] Period reminder notification: D-1 at 9am
- [ ] Ovulation alert notification: fertile window start at 9am
- [ ] Daily log reminder via WorkManager
- [ ] App-open ad on foreground
- [ ] Interstitial: splash, onboarding, tab navigation, back-press
```

---

## 4. Filling Rules

### Rule 1 — Write it before you close the IDE
The moment you close Android Studio, you start to forget which line you were on and why. Section 6 (Tomorrow's Starting Point) is especially useless if written from memory an hour later.

### Rule 2 — Be specific in every field
Bad: `"Fixed a bug in HomeFragment"`
Good: `"HomeFragment.kt line 142 — moved tvGreeting binding to after LiveData is observed; it was reading a null value because the observer hadn't fired yet"`

Bad: `"Updated some layouts"`
Good: `"fragment_home.xml — added legendContainer LinearLayout below cycle ring; fragment_log_symptoms.xml — added lhTestResult ChipGroup between cervicalFluid and basalTemp"`

### Rule 3 — Section 2d (Database Changes) is mandatory if any entity changed
A missing migration is the most common crash on a fresh install. If you changed *anything* in `data/entity/`, you must document the migration in the log. If you didn't change any entity, write "None — DB version stays at 6."

### Rule 4 — Section 3 (Known Issues) is not optional
If you found a bug and didn't fix it, it must be in the log. A bug that isn't written down doesn't exist as far as the next developer is concerned — which means they'll either hit it again and waste time, or ship it.

### Rule 5 — Section 6 (Tomorrow's Starting Point) must be one concrete sentence
Bad: `"Continue working on Calendar"`
Good: `"Tomorrow morning, open CalendarFragment.kt line 210 and finish the selectedDayCard binding — the card background is set but tvSelectedDate is not updating when a day is tapped."`

### Rule 6 — Section 9 (What the App Can Do) is cumulative
Copy the list from yesterday's log. Add new items. Remove items if a feature broke. The last session log in the repo should always describe exactly what a working build does today.

### Rule 7 — Commit the log in the same push as the code
The log belongs in the repo, not on your laptop. If your code commit for the day was `abc1234`, the log should be committed in the same push or the very next one. A session log committed three days later is not useful — it will be wrong.

---

## 5. What Makes a Good Log vs. a Bad Log

| Bad log | Good log |
|---|---|
| "Worked on home screen" | "Added CycleProgressRingView to fragment_home.xml, bound it to CycleViewModel.cycleDay LiveData in HomeFragment.kt — ring now animates from 0 to current day on first load" |
| "Fixed some crashes" | "Fixed NullPointerException in LogSymptomsFragment.kt line 89 — binding.btnSave was accessed before onViewCreated completed; moved listener registration to after view inflation" |
| "Tomorrow: keep going" | "Tomorrow morning, open ProfileFragment.kt line 340 and wire up the notification toggle switches — the UI exists but onCheckedChangeListener is not yet saving to UserSettings" |
| Lists 14 gates all ticked | Lists only gates actually run — e.g. Gates 1–7 ticked, Gates 8–16 unticked with a note "ran out of time, will complete tomorrow" |
| Section 3 blank | Every bug found is listed, even ones not fixed: "NotificationHelper.kt:56 — text still says 'LunaCycle', tracked in gate 13" |
| Section 2d blank after adding a field to DailyLog | "Added `lhTestResult: String` to DailyLog entity; bumped DB to version 4; added MIGRATION_3_4 — ALTER TABLE daily_logs ADD COLUMN lhTestResult TEXT NOT NULL DEFAULT 'Not Tested'" |

---

## 6. Reference: Existing Known Issues (as of Day 1)

These are already tracked. Every session log should check if they have been fixed and update accordingly.

| ID | File | Issue | Status |
|---|---|---|---|
| K-01 | `NotificationHelper.kt:56` | Push notification body says "Tap to open LunaCycle." | Open |
| K-02 | `AppDatabase.kt:115` | SQLite file named `lunacycle.db` — should be `period_tracker.db` | Open |
| K-03 | `values-night/themes.xml` | Dark theme style still named `Base.Theme.LunaCycle` | Open |
| K-04 | `AdConstants.kt` | All ad unit IDs are Google test IDs — production IDs not yet set | Open — intentional for dev |
| K-05 | `com.example.periodtracker` | Package name must change to production package before Play Store | Open — intentional for dev |

When you fix one, mark it **Resolved** in the day's log and remove it from the open list in that log's Section 3.

---

## 7. End-of-Internship Handover Note

On the final day, the session log is replaced by a **Handover Note**. It follows the same template but adds one extra section after Section 9:

```markdown
## 10. Handover — For the Next Developer

### 10a. What is production-ready
[List what can ship as-is]

### 10b. What must be done before Play Store submission
[Known issues K-01 to K-05, package rename, production ad IDs, etc.]

### 10c. What was intentionally left out of scope
[Features in the goal list that were not built and why]

### 10d. Credentials and access
[Firebase project name, AdMob account, GitHub org, who has access to what]
<!-- Do NOT write passwords here — write who to ask -->

### 10e. Signed off by
**Developer:** Adnan Arshad
**Program Manager:** Muneeb
**Product Lead:** Shezrah Abbasi
**Date:** YYYY-MM-DD
```

---

## 8. Quick Reference — Session Log Checklist

Before closing the IDE each evening:

```
□  Named correctly:         SESSION-YYYY-MM-DD.md in docs/session-logs/
□  Section 1  filled:       Goal for today (one sentence)
□  Section 2a filled:       Every new .kt / .java file listed
□  Section 2b filled:       Every modified .kt / .java file listed
□  Section 2c filled:       Every new or modified XML layout listed
□  Section 2d filled:       DB version documented (even if unchanged)
□  Section 3  filled:       Known issues — "None found today" is valid
□  Section 4  filled:       Decisions made — "No significant decisions" is valid
□  Section 5  filled:       Blockers — "No blockers today" is valid
□  Section 6  filled:       Tomorrow's starting point — ONE concrete sentence
□  Section 7  filled:       Only gates actually run are ticked
□  Section 8  filled:       Every commit hash + message listed
□  Section 9  filled:       Cumulative capability snapshot up to date
□  Committed and pushed:    docs/session-logs/SESSION-YYYY-MM-DD.md in the repo
```
