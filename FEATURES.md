# PitchForge — Feature Document

**Scope:** Verified against application source under `pitchforge/app/src/main` as of the learner-UX build on `main` (post–settings-outline restore).  
**Rule:** Only features with concrete code paths are listed. Items that exist only as unused constants or dead helpers are called out explicitly.

---

## 1. Product identity

| Claim | Evidence |
| --- | --- |
| Free / no ads / no IAP | No Play Billing, AdMob, or purchase APIs in the app module. Manifest permissions are only `POST_NOTIFICATIONS`. |
| Offline-first training | Audio from bundled assets + in-app synthesis; Room + DataStore local persistence; no network permission for lessons. |
| Package | `com.pitchforge.app` (`app/build.gradle.kts`) |
| Min / target SDK | 26 / 35 |

---

## 2. App shell & navigation

**Primary routes** (`ui/Navigation.kt` → `Routes`):

| Route | Screen | How reached |
| --- | --- | --- |
| `onboarding` | `OnboardingScreen` | Cold start if no onboarded user |
| `dashboard` | `DashboardScreen` (Home) | Default after onboarding; nav tab Home |
| `stats` | `StatsScreen` | Floating nav Stats |
| `challenge` | `ChallengeScreen` | Floating nav Challenges |
| `lesson` | `LessonScreen` | Home FAB / dose-gated start |
| `settings` | `SettingsScreen` | Top-right settings button |
| `checkup` | `CheckupScreen` | Home card when monthly checkup is due |
| `generalization` | `GeneralizationScreen` | Home card when probe is due |
| `retention` | `RetentionScreen` | Home card when retention checks are due |

**Floating nav** (`ui/components/FloatingNavBar.kt`): iOS-style translucent bar with **Home · Stats · Challenges**. Settings is **not** in the bar; it lives in the top app bar.

**Lifecycle audio** (`PitchForgeApplication`): observes process lifecycle and releases `AudioManager` when the app goes to background. Lesson/challenge screens also call `stopAudio()` on dispose when backing out.

**Typography** (`ui/theme/Type.kt`): bundled fonts — Instrument Serif (brand), Fraunces (headlines), Red Hat Text (body/UI).

**Smooth scrolling** (`ui/components/SmoothScroll.kt`): custom fling behavior on main LazyColumns (Home, Stats, Challenges, Settings).

**Overscroll**: disabled app-wide via `LocalOverscrollConfiguration provides null` in `PitchForgeTheme`.

---

## 3. Onboarding

**Files:** `ui/onboarding/OnboardingScreen.kt`, `OnboardingViewModel.kt`, `domain/DiagnosticTrialFactory.kt`

1. **Intro pager** — 4 pages covering welcome, how training works, practice expectations, and the diagnostic. Honest framing: most adults improve; full 12-note AP is rare.
2. **Skip control** — Skip jumps to the last intro page (“Diagnostic Test”) only. It does **not** skip the diagnostic. `OnboardingViewModel.skipOnboarding()` (seed without diagnostic) exists but is **unwired** in the UI.
3. **AP diagnostic** — 14 isolated naming trials (`DiagnosticTrialFactory.TRIAL_COUNT`), piano, octaves 4 and 5, **no feedback until the end**.
4. **Baseline seeding** — Accuracy + mean error seed starting active-set size via `ActivePitchSetManager.startingSizeFromBaseline`:
   - ≥50% → 6 notes  
   - ≥30% → 4 notes  
   - else → 3 notes  
5. **Completion** — Creates user + pitch-progress rows; navigates to Home.

**Deep links:** none — manifest is `MAIN`/`LAUNCHER` only; notification taps open `MainActivity` without a route.

---

## 4. Home (Dashboard)

**Files:** `ui/dashboard/DashboardScreen.kt`, `DashboardViewModel.kt`

### 4.1 Start adaptive lesson

- FAB / primary CTA starts a lesson when dose/cooldown allow.
- **Continue · {note}** labeling when a focus note exists (`NextNoteClarity`).
- **Cooldown dialog** when &lt;20 minutes since last completed adaptive lesson (`InterTrialPolicy.SESSION_COOLDOWN_MS`).
- **Dose complete dialog** when 4 adaptive lessons already finished today (`DAILY_LESSON_HARD_CAP`).

### 4.2 Focus note

- Surfaces the next chroma to care about (`domain/Motivation.kt` → `NextNoteClarity`).
- Prefer weak note still in the learning set; else highest-accuracy learning note with reason copy.

### 4.3 Soft plateau message

- Optional card from `PlateauMessaging` when history suggests a flat stretch (never changes scoring).

### 4.4 Your 12 notes

- Chromatic collection grid: **Locked / Learning / Mastered**.
- Learning/mastered cells show **percentage** heat (on-time naming accuracy), not only checkmarks.
- Goal ring elsewhere on Home: mastered count / 12 (`GoalRing`).

### 4.5 Daily missions

Fixed trio every day (`MissionEngine`), **+20 XP each** when completed:

1. Complete 1 daily lesson  
2. Score at least 24/30 on daily lesson (`SCORE_TARGET`)  
3. Hit a 5-in-a-row on-time combo (`COMBO_STREAK` / `COMBO_TARGET`)

### 4.6 Due-work cards

Shown when applicable (do not silently auto-run):

- **Retention** — mastered notes due for 30/90-day checks  
- **Generalization** — untrained-timbre probe due (~14 days)  
- **Monthly checkup** — last 0–2 days of the calendar month, if not already done this month  

### 4.7 Notification permission

- On Home, requests `POST_NOTIFICATIONS` on Android 13+ when appropriate.

---

## 5. Stats

**File:** `ui/stats/StatsScreen.kt` (shares `DashboardViewModel`)

| Block | What it shows |
| --- | --- |
| Stat chips | Current streak, best streak, lessons, overall accuracy, practice minutes, mastered/12 |
| Level card | Level, XP into level / XP for next level, progress bar (`LevelSystem`) |
| Weekly share card | Streak, lessons this week, accuracy, approx minutes; **Share week** via Android share sheet |
| Accuracy trend ↔ Radar | Toggle: session accuracy area chart **or** 12-note mastery radar (`NoteMasteryRadarChart`) |
| Checkups | Baseline % and latest monthly checkup % |
| Generalization | Latest untrained-instrument probe score (if any) |

**Note:** The old “8-week AP Foundation” progress bar is **not** present in this build.

---

## 6. Adaptive daily lesson (core trainer)

**Files:** `ui/lesson/*`, `domain/LessonPlanner.kt`, `NoteSelector.kt`, `DeadlineManager.kt`, `InterTrialPolicy.kt`, `data/PitchForgeRepository.kt`

### 6.1 Session shape

- **~30 trials** (`LessonViewModel` `questionCount = 30`; planner clamps 8–30).
- Task mix: **NAMING** and **VERIFICATION** (yes/no with a candidate label).
- New pitches (&lt;10 attempts) get higher verification rate (~60%); later ~20%.
- First tone requires Play; later trials auto-advance after washout buffering.
- Leave mid-lesson prompts a confirm dialog; audio stops on exit.

### 6.2 Note selection (`NoteSelector`)

- Weighted toward **weaker** EMA accuracy.
- Avoids small chroma intervals from the previous trial; floor rises with set size (2 / 3 / 4 semitones for sets ≥3 / ≥6 / ≥9).
- Occasionally injects mastered notes due for spaced review (~20% chance pool mix).

### 6.3 Per-note octave staging (`LessonPlanner`)

- Until chroma is solid (≥15 attempts & ≥95% EMA) or mastered: **octave 4 only**.
- After that: pool **3–5**; mastered / review notes prefer **large octave jumps** so height is a weaker cue.

### 6.4 Per-note timbre staging (`LessonPlanner`)

- Uses Settings **active timbres** (primary = first selected).
- Stay on primary until chroma is solid **and** has ≥30 attempts (`TIMBRE_EXPAND_MIN_ATTEMPTS`).
- Then unlock instruments **one at a time** for that chroma; next instrument requires mastery of the previous on that note (≥15 attempts & ≥95% EMA on that timbre skill).
- Avoids repeating the same timbre back-to-back when multiple are unlocked.

### 6.5 Deadlines (`DeadlineManager`)

Per-pitch tiers (ms): **4000 → 3000 → 2200 → 1600** as on-time EMA rises (first 10 exposures stay at 4000). Floor constant 1300 ms exists; practical bottom tier is 1600. If accuracy falls after a tighten, deadline **loosens one step**. Timing is from **audio onset**. Right-but-late ≠ mastery credit.

### 6.6 Anti–relative-pitch timing (`InterTrialPolicy` + audio)

| Mechanic | Behavior |
| --- | --- |
| Variable ISI | 1.5–4 s washout between trials |
| Octave-colored noise | Brown / pink / white / blue by previous octave (`NoiseColor.forOctave`) |
| Cluster washouts | Periodic dissonant cluster (~every 6–8 trials) |
| Cold starts | 2–4 per 30-q lesson: brief clear + 5–8 s silence |
| Feedback | Correct-note replay; **post-answer feedback noise mask removed** in this build |

### 6.7 Dose gating

- Recommend ~2 lessons/day; hard cap **4**/day; **≥20 min** between adaptive lessons.
- Challenges / probes remain available when capped.

### 6.8 Feedback UX

- Correct / incorrect / too-slow states.
- Separate **Replay note** under the result.
- Green check + **confetti** on correct; combo flame when streak ≥2.
- Haptic near deadline expiry and on answer.
- Summary: score, XP/level progress, optional second-dose nudge, **newly mastered notes**, per-note lesson breakdown (`SessionFeedback.noteBreakdown`: Strong / Steady / Slipped).

### 6.9 Persistence on each answer

Writes `question_attempts`, updates pitch EMA/deadline/mastery window, per-note-timbre `note_stats`, missions, streak, XP.

---

## 7. Active pitch set & mastery

**Files:** `domain/ActivePitchSetManager.kt`, `PitchForgeRepository`

| Rule | Constant / behavior |
| --- | --- |
| Mastery window | Last **3 days** (`MASTERY_WINDOW_DAYS`) |
| Min naming attempts in window | **15** |
| Accuracy | ≥ **95%** correct **within deadline** (naming only) |
| Sticky collection | Once mastered, stays mastered in the grid; expansion still uses live window |
| Expansion | +1 chroma when **every** active note meets mastery; new note maximizes min-interval from existing |
| Spread start | Even chromatic spacing (`selectSpreadPitches`) |
| Spaced review ladder | After mastery: **1 / 3 / 7 / 21 / 60** day intervals; miss resets toward front |

**Note:** `StreakManager.isWithinGraceWindow` (4-hour post-midnight grace) exists but is **not referenced** elsewhere — streak math today is calendar-day based only.

---

## 8. XP & levels

**Files:** `PitchForgeRepository.XP_PER_CORRECT`, `domain/LevelSystem.kt`, `domain/CosmeticTheme.kt`

- **+10 base XP** per correct-within-deadline answer, scaled by `LevelSystem.xpMultiplier(level)` (~100% early → floor 0.55).
- Level curve: cost to leave level L = `100·L + 12·L·(L−1)/2`.
- Missions award **+20 XP** each on completion.
- **Cosmetic themes unlock every 10 levels** (see Settings).

---

## 9. Skill challenges

**Files:** `ui/challenge/*`  
**Important:** Results **do not** update mastery / adaptive model (no repository mastery writes from challenges).

| Mode | Size | Behavior |
| --- | --- | --- |
| 20-note Gauntlet | 20 | Untimed naming; primary timbre |
| Timed | 12 | **3000 ms** deadline; vibration/haptic path like lessons |
| Mixed Timbre Chaos | 12 | Cycles Settings-selected instruments; shows current timbre |
| Proof | 12 | Mastered notes only; gated until ≥1 note mastered |

Shared: Play-ready screen (including Timed — no auto-jump), octaves 3–5, cluster washouts between trials, stop audio on back-out.

---

## 10. Monthly AP checkup

**Files:** `ui/checkup/*`, `ApCheckupPolicy`, `DiagnosticTrialFactory`

- Same 14-trial no-feedback measure as diagnostic.
- Due only in the **last 3 calendar days of the month** (`END_OF_MONTH_DAYS = 2` → days remaining 0..2).
- At most one completed checkup per month.
- **Measurement only** — does not rewrite mastery or active set.
- Home card + optional notification (`MaintenanceWorker`).

---

## 11. Generalization probe

**Files:** `ui/probe/*`, `GeneralizationPolicy`

- Due ~every **14 days** after onboarding / last probe.
- **10 trials** on an **untrained** timbre from `TimbreCatalog.PROBE_POOL`.
- Uses known pitch classes when possible; octaves 4–5.
- Scored separately; shown on Stats when available.
- Notification when due (if notifications on).

---

## 12. Retention checks

**Files:** `RetentionPolicy` in `GeneralizationPolicy.kt`, `ui/probe/Retention*`

- Scheduled at **~30 and ~90 days** after a note’s first mastery.
- Short naming probes per due pitch (`TRIALS_PER_PITCH = 2`).
- Home card + notification when due.

---

## 13. Settings

**Files:** `ui/settings/SettingsScreen.kt`, `SettingsViewModel`, `SettingsRepository` (DataStore)

| Setting | Behavior |
| --- | --- |
| Color themes | Studio / Ocean / Forest / Volcanic / Aurora / Midnight — unlock at levels 1/10/20/30/40/50 |
| Active timbres | Toggle list from `TimbreCatalog.SELECTABLE` (piano, flute, guitar, cello, clarinet, harpsichord, square, sine, violin, trumpet) |
| Volume | 0–1 slider → playback gain |
| Practice reminders | Master switch for smart notifications |
| Light / dark | system / light / dark |
| About the science | Static Wong et al. disclosure copy |

**Stored but not exposed in Settings UI:** `textScale`, `reminderTime` (still used as fallback hour for habit inference when history exists).

---

## 14. Instruments & audio engine

**Files:** `audio/AudioManager.kt`, `NotePlayer.kt`, `InstrumentPreloader.kt`, `domain/TimbreCatalog.kt`, `assets/samples/`

| Capability | Detail |
| --- | --- |
| Bundled WAV packs | piano, flute, guitar, cello, clarinet, harpsichord, violin, trumpet — chromatic, octaves 3–5 |
| Synthesized | `sine`, `square` (no asset files) |
| Loudness | Per-sample gain / RMS normalization across acoustic samples |
| Background preload | `InstrumentPreloader` warm-loads active then remaining selectable timbres at app start |
| Washout audio | Octave-dependent colored noise + cluster chords |

---

## 15. Notifications (WorkManager)

**Files:** `work/WorkScheduler.kt`, `ReminderWorker.kt`, `MaintenanceWorker.kt`, `Notifications.kt`, `PracticeTimingPolicy`, `NotificationCopy`

**Schedules:** ReminderWorker ~hourly; MaintenanceWorker daily.

| Kind | When (high level) |
| --- | --- |
| Habit practice | Not practiced today; outside quiet hours 22:00–08:00; with history at preferred hour; no history 10:00–12:00; max **2**/day with **3 h** gap; miss-day copy when yesterday was skipped |
| Spaced review due | Mastered notes due; once/day; not quiet hours |
| Second-session dose | Already 1 lesson today; afternoon/evening window; once/day |
| Retention due | Daily maintenance |
| Generalization due | Daily maintenance |
| Monthly checkup due | Daily maintenance |

Copy pools randomized in `NotificationCopy` (habit / review / second session / retention / generalization / checkup).

---

## 16. Streaks

**File:** `domain/StreakManager.kt` + repository updates on lesson complete

- Streak = consecutive calendar days with ≥1 completed adaptive lesson.
- Longest streak tracked on the user row and shown on Stats.
- Grace-window helper exists (**unused** by callers today).

---

## 17. Accessibility & UX polish (implemented)

- Content descriptions on key controls (e.g. note cells, onboarding).
- System dark/light via Settings + Material 3 schemes per cosmetic theme.
- Haptics on deadline pressure and answers (lessons; timed challenges).
- TalkBack-oriented deadline haptic near expiry in lesson answering UI.
- Confetti / combo / mastery celebration surfaces on lesson summary.

---

## 18. Data & architecture (supporting features)

| Layer | Role |
| --- | --- |
| Room DB | `pitchforge.db` **version 5** — users, pitch progress, note stats, sessions, attempts, probes, retention, missions, checkups; explicit migrations 1→5 (no destructive fallback) |
| DataStore | Preferences (timbres, theme, notifications, reminder metadata, …) — this is the live settings store |
| Domain package | Pure Kotlin training rules, unit-tested under `app/src/test` |
| Hilt | DI across UI / data / audio / workers |

---

## 19. Explicitly not present or unwired (checked)

These are **absent** from current navigation/UI, or exist in code but are unused:

- In-app purchases, ads, accounts, cloud sync  
- Deep links / notification deep routing to a specific screen  
- Assessments / History screens (not in `Routes`)  
- 8-week AP Foundation progress bar  
- Settings UI for text scale or reminder clock time (DataStore fields only; habit hour inferred from history with `"18:00"` fallback)  
- `skipOnboarding()` full skip (VM method unwired; UI Skip only skips intro copy)  
- Streak freeze grace actually applied to streak computation (`isWithinGraceWindow` unused)  
- Network-based content download for training audio  

---

## 20. Feature map (quick index)

1. Onboarding + diagnostic seeding  
2. Adaptive 30-trial lessons with naming/verification  
3. Active pitch-set expand-to-12  
4. 3-day ≥95% mastery gate  
5. Per-note octave then timbre staging  
6. Shrinking RT deadlines with loosen-on-struggle  
7. Washouts, colored noise, clusters, cold starts  
8. Daily dose cap + 20-min cooldown  
9. Daily missions (lesson / score / combo)  
10. XP + levels + cosmetic themes  
11. Home focus note + plateau copy + 12-note grid  
12. Stats (chips, level, share week, trend/radar, checkup/gen scores)  
13. Floating Home/Stats/Challenges nav  
14. Skill challenges (Gauntlet / Timed / Chaos / Proof)  
15. Monthly checkup  
16. Generalization probes  
17. Retention checks  
18. Smart notifications (habit, review, second dose, probes, checkup)  
19. Multi-timbre audio + background preload  
20. Settings (themes, timbres, volume, notifs, dark mode)  
21. Offline local persistence  
22. Custom music typography  

---

*Generated from source inspection of the PitchForge Android module. If a UI label and a domain constant disagree, the domain/repository constants above are authoritative for training behavior.*
