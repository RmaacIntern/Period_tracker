# DOCUMENTATION REWRITE PLAN
> Internal working note — not a deliverable. Documents what action to take on each uploaded log.
> Delete this file after all session logs are rewritten and committed.

---

## The Problem Summary

Seven session logs exist across the project but none of them follow the DOCUMENTATION-STANDARD.md
template. They have four specific problems:

1. **Wrong file names** — not all follow SESSION-YYYY-MM-DD.md
2. **App name inconsistency** — early logs say "LunaCycle", later say "Period Tracker"
   because the team lead asked for the name change partway through development
3. **Missing context** — logs don't explain WHY decisions were made, only WHAT changed
4. **Gaps** — some entries are architecture docs not session logs; some days have no log at all

---

## File Rename Map

| Old filename | Correct name | Note |
|---|---|---|
| `day2_md.md` | `SESSION-2026-09-15.md` | Day 2, architecture & project setup doc |
| `session_log_sep18.md` | `SESSION-2026-09-18.md` | Session Day 4 |
| `lunacycle-2026-09-21.md` | `SESSION-2026-09-21.md` | Session Day 7 |
| `LunaCycle_Session_2026-09-22.md` | `SESSION-2026-09-22.md` | Session Day 8 |
| `LunaCycle_Session_2026-09-24.md` | `SESSION-2026-09-24.md` | Session Day 10 |
| `period_tracker_session_2026_09_25.md` | `SESSION-2026-09-25.md` | Session Day 11 |
| `period-tracker-session-2026-09-28.md` | `SESSION-2026-09-28.md` | Session Day 14 |

---

## App Name Timeline

| Date | App name in log | Why |
|---|---|---|
| Sep 15 – Sep 22 | LunaCycle | Original name chosen during development |
| Sep 22 | Still LunaCycle | Last day using that name |
| Sep 24 onwards | Period Tracker | Team lead instructed name change |
| Sep 25 onwards | Period Tracker | Consistent |
| Sep 28 onwards | Period Tracker | Final name confirmed |

The name change is a legitimate product decision — logs from Sep 15–22 are correct
to say LunaCycle for their era. The rewritten logs just need a note explaining the change.

---

## Prediction Logic Change Explanation (Sep 24)

This is the biggest technical decision that needs explaining in the log.

**Old system (up to Sep 22):**
- `CycleEngine.bestPrediction()` ran a weighted average of last 6 cycles
- BBT thermal shift, cervical fluid progression, and LH surge detection
  all fed into an ovulation prediction signal hierarchy
- `logPeriodStart()` recalculated and overwrote the user's cycle length
  setting with a computed average every time a new period was confirmed
- Confidence score: 10–95% based on how many signals agreed

**New system (Sep 24 onwards):**
- `nextPeriod = lastPeriodStart + user's cycleLength setting` (one line)
- `ovulation = nextPeriod − 14 days` (fixed biological luteal phase)
- `fertileStart = ovulation − 5 days`
- `fertileEnd = ovulation + 1 day`
- BBT/LH/cervical still LOGGED for the user's reference but have zero
  effect on prediction dates
- Confidence fixed at 85%, label = "Based on your data"

**Why the change was made:**
The averaging system had three real bugs that caused user-facing problems:
1. `logPeriodStart()` silently overwrote whatever cycle length the user
   had set in their profile — if a user set 35 days but had one short cycle
   of 26 days, the app would predict future periods 6 days earlier than
   the user expected, with no explanation why
2. The ovulation alarms were firing on already-passed dates (wrong day fix
   is documented in Sep 24 session) because the signal hierarchy was
   producing dates in the past
3. The confidence score (10–95%) was confusing — users didn't know what
   affected it or how to improve it

The simpler system is more predictable, easier to debug, and matches what
users expect: "I said my cycle is 28 days, predict 28 days from now."
Biological accuracy (luteal phase fixed at 14 days) is standard across
all period tracking apps. The signals are preserved in the log for a
future ML feature, not deleted.
