# DOCUMENTATION-GAP-EXPLANATION.md
**Project:** Period Tracker
**Developer:** Adnan Arshad
**Prepared for:** Muneeb (Program Manager), Shezrah Abbasi (Product Lead)
**Date:** 2026-09-29
**Purpose:** Formal explanation of why documentation standards were not followed from Day 1, why session logs are missing for some days, and why the codebase shows evidence of multiple AI tools.

---

## Summary

Three separate gaps exist in this project's documentation record. Each has a distinct cause. This document explains each gap honestly, provides the evidence that supports the explanation, and describes what has been done to correct it going forward.

---

## Gap 1 — Documentation standards were not followed from the start

### What the gap looks like
The session logs from Days 2 through 14 (September 15–28) do not follow the `DOCUMENTATION-STANDARD.md` template. They have different file names, inconsistent structures, some are architecture notes rather than session logs, and none contain the 9-section format with gates checklist.

### Why this happened
The `DOCUMENTATION-STANDARD.md` file, the `NEW-APP-RUNBOOK.md`, and the `APP-BRIEFS.md` were not created at the start of the project. They were created on **September 29** — the day this explanation is being written.

The reason they were not created at the start is that I did not have access to a company Claude account during the early weeks of development. The company account was provided to me **partway through the development timeline**, not on Day 1. Before that account was available, I was working across multiple AI tools — ChatGPT, Gemini, and others — and those tools do not have a shared memory or consistent project context between sessions. Each session on each tool started from scratch with whatever context I could manually paste in.

Without a single consistent AI tool that held the project context, and without the project documentation standards being defined yet, the session logs I did write were informal notes designed for my own reference — not structured handover documents. They captured what I needed to remember for the next session, not what a reviewer or future developer would need to understand the project.

### What has been corrected
All seven existing session logs have been retroactively rewritten to the `DOCUMENTATION-STANDARD.md` format and renamed to the `SESSION-YYYY-MM-DD.md` convention. They are now in `docs/session-logs/`. Today's log (`SESSION-2026-09-29.md`) is the first one written to the standard from the start of the session. Every log from today forward will follow the standard.

---

## Gap 2 — Session logs are missing for some days

### What the gap looks like
The app was started on September 14. Session logs exist for: Sep 15, 18, 21, 22, 24, 25, 28, 29. That leaves several working days with no log at all: Sep 14, 16, 17, 19, 20, 23, 26, 27.

### Why this happened
Two separate reasons:

**Reason A — Multiple AI tools, no persistent session state.**
When working across ChatGPT, Gemini, and other tools in the same day, the conversation exists only inside that tool's chat window. There is no automatic log, no memory that carries across sessions, and no shared document that all the tools write to. At the end of a session I had to manually record what was done. On days when the work session was short, or when I switched between tools multiple times in one day, I often did not write a separate log — the context was partly in one chat window and partly in another.

**Reason B — Claude account was not available at the start.**
The Claude company account, which has memory across sessions and was used to build `DOCUMENTATION-STANDARD.md` and the other project standards, was not available to me during the first part of the project. Once I had it, I could establish a consistent working method. Before that, I was improvising documentation alongside the development work.

### What cannot be recovered
The working days that have no log cannot be accurately reconstructed. I do not have reliable records of exactly what changed on September 14, 16, 17, 19, 20, 23, 26, or 27. The code that was written on those days is visible in the repository — the git commit history and the final codebase are the record of that work. But the *reasoning* behind decisions made on those days cannot be retroactively documented with confidence.

Writing fabricated logs for those days — inventing timestamps, decisions, and file lists — would be dishonest and would create a documentation record that appears complete but is not accurate. I have chosen not to do that.

### What has been done instead
The seven logs that did exist have been rewritten with full detail including decisions and reasoning. The codebase itself (reviewed in `APP-BRIEFS.md`) documents the complete current state of the project. The gaps in the log history are acknowledged here rather than papered over.

---

## Gap 3 — The codebase shows code from multiple AI tools and sources

### What the gap looks like
A reviewer looking at the codebase may notice: inconsistencies in code style between files, some areas that are more polished than others, and in the session logs — references to the ads classes being "adapted from a previous project." The git history shows the development was not linear and uniform.

### Why this happened
**Multiple AI tools were used.** During the period when I did not have a company Claude account, I used ChatGPT, Gemini, and other available AI tools to generate code. Different tools produce different styles:
- Some tools prefer verbose comments, others prefer clean code with no comments.
- Some tools use different naming conventions for the same concept.
- Some tools generate more boilerplate; others are more concise.

When code from different tools is combined into one project without a unifying style guide enforced from the start, the inconsistencies show.

**Claude account limits were hit during working hours.** Even after receiving the company Claude account, there were sessions during working hours where the usage limit was reached. When that happened mid-task, I continued the work using another available tool to avoid stopping development entirely. The code from those continuation sessions was then integrated back into the project.

**The ads classes were adapted from an existing codebase.** The six AdMob classes (`AdConstants`, `AdsRemoteConfig`, `BannerAdHelper`, `LoadAds`, `ShowAds`, `AppOpenAdManager`) were not written from scratch. They were taken from a previous project where they were already built and tested, then adapted — changing ad unit IDs, preload keys, Remote Config flag names, and the navigation flow integration. This is documented in `SESSION-2026-09-28.md` under Decisions Made. Reusing tested infrastructure code is a standard professional practice; what matters is that the adaptation is correct and that it is disclosed, which it is.

### What has been done to address this
Going forward:
- `DESIGN-STANDARD.md` provides a unified token system for all UI work regardless of which tool generates it.
- `NEW-APP-RUNBOOK.md` Gate 12 requires every new view to be checked against the design standard before committing.
- `DOCUMENTATION-STANDARD.md` creates a consistent documentation format that is tool-agnostic — the same template whether the code was written by Claude, ChatGPT, or by hand.
- The company Claude account with session memory is now the primary tool, which provides continuity across sessions that was not available at the start.

The inconsistencies already in the codebase will be cleaned up as features are completed, not in a separate refactor pass (which would risk introducing bugs).

---

## What this means for the gap analysis score

The documentation gap analysis issued on 2026-09-28 scored documentation at 3/10. That score reflects the state of the documentation as it existed at the time — informal, inconsistently named, missing structure, and missing several days entirely.

The causes of that score are explained above. None of the causes reflect a decision to skip documentation. They reflect a development environment that was not fully set up from Day 1: no company AI account, no project standards document, and no single tool with persistent session memory.

The corrective work completed on September 29 addresses all three gaps:

| Gap | Corrective action | Status |
|---|---|---|
| Standards not followed | 7 logs rewritten to DOCUMENTATION-STANDARD.md format | ✅ Complete |
| Missing logs for some days | Gaps acknowledged here; cannot be accurately fabricated | ✅ Disclosed |
| Multi-tool code inconsistency | DESIGN-STANDARD.md + gate 12 in RUNBOOK enforce consistency going forward | ✅ Controls in place |

---

## Declaration

The above explanation is accurate to the best of my knowledge. The gaps described are real, the causes described are the actual causes, and the corrective actions described have been completed. I have not fabricated or back-dated any documentation.

**Adnan Arshad**
Android Developer Intern, Markalytics
2026-09-29
