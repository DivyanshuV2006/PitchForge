# ChromaP

**ChromaP** is a free, offline-first Android app that trains **absolute pitch** (“perfect pitch”) through short, adaptive daily lessons. It is built around mechanics drawn from peer-reviewed adult absolute-pitch acquisition research—not a generic “play a note, guess it” ear trainer.

There are **no ads, no subscriptions, and no in-app purchases**. Every feature is available from first install.

> Package ID remains `com.pitchforge.app` (internal code still uses PitchForge names). The product name and launcher branding are **ChromaP**.

For a full feature + algorithm + philosophy write-up, see **[CHROMAP_REPORT.md](CHROMAP_REPORT.md)**.

---

## Philosophy in one page

Absolute pitch means naming a tone’s chroma (C, C♯, …) **without** a reference. Relative pitch—answering “one higher than the last note”—is easier and is exactly what ChromaP tries **not** to teach by accident.

| Principle | How the app enforces it |
| --- | --- |
| Absolute naming | Minimum chroma distance between consecutive lesson trials; colored-noise washouts; dissonant cluster chords; cold-start silences |
| Mastery = fast & reliable | Only **on-time** correct naming advances mastery (≥95% over 3 days, ≥15 attempts) |
| Distributed practice | ~30-trial lessons; 20‑minute cooldown; hard cap of 4 adaptive lessons/day; optional bedtime second-session nudge |
| Measurement stays separate | Challenges, checkup, probes, and boundary drills do **not** rewrite mastery |
| Honest expectations | AP is learnable-in-part by most adults; full 12-pitch AP is uncommon (~14% in cited studies) |

---

## What a session feels like

1. Hear an isolated note (piano and other instruments; octave/timbre unlock as you stabilize each chroma)
2. Name it, or answer a yes/no verification prompt on newer pitches
3. Get immediate feedback—including **right note, but too slow** when you miss the deadline
4. Sit through a washout (noise, cluster chord, or cold-start silence) so the next trial isn’t “relative to the last one”
5. Repeat for ~**30** trials (~10–15 minutes)

Progress is stored locally (Room + DataStore) and works fully offline after install.

---

## Features

### Core training
- **Onboarding + AP diagnostic** — 14 no-feedback trials seed baseline accuracy and starting pitch-set size (3 / 4 / 6)
- **Adaptive lessons** — weak-note reintroduction, active pitch-set expansion toward 12, naming + verification
- **Shrinking RT deadlines** — per-pitch windows from 4000 ms down toward ~1300 ms (from audio onset)
- **Octave & timbre staging** — learn chroma on octave 4 / primary instrument first; widen later
- **Anti–relative-pitch washouts** — variable ISI noise, longer loud cluster chords, cold starts; mild EMA bump on the first trial of each lesson

### Habit & progress
- **Home / Stats / Challenges** floating nav; Settings in the top bar
- **Daily missions & streaks** — lightweight habit loop
- **XP, levels, cosmetic themes** — unlock new looks every 10 levels
- **Smart reminders** — habit hour from practice history; optional **bedtime** encore (~10 minutes before sleep)
- **12-note collection** — Mastered / Learning / Locked with per-note accuracy

### Measurement & remediation (do not feed mastery)
- **Skill challenges** — Mastery Proof, 20-Note Gauntlet, Timed Mode, Mixed Timbre Chaos
- **Monthly AP checkup** — diagnostic-style snapshot near month’s end
- **Generalization probes** — untrained instrument every ~14 days
- **Retention checks** — 30- and 90-day follow-ups after mastery
- **Boundary drills** — optional short sessions for any stable confusion pair (longer washouts)

### Audio & polish
- Multi-timbre sample packs + sine/square synthesis
- Bundled brand fonts; six unlockable color themes; light/dark/system

---

## The science behind the training loop

| Study | Contribution mirrored in the app |
| --- | --- |
| Wong et al. (2019) | Weak-note reintroduction; multi-timbre training; retention follow-ups |
| Wong et al. (2020) | Active pitch set starts small and grows toward 12; naming + verification |
| Wong et al. (2025) | Shrinking response-time window; error in semitones; untrained-timbre generalization |

Meaningful improvement typically takes **weeks of regular practice** (studies used on the order of 12–40+ hours over ~8 weeks). ChromaP does **not** claim to guarantee perfect pitch.

### Intentional product deviation from the lab protocol

If accuracy **drops after a deadline tightens**, ChromaP will **loosen the deadline one step** instead of leaving the learner stuck on an impossible timer (`DeadlineManager`). That is a deliberate at-home adaptation—not identical replication of the strict lab protocol.

---

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM, unidirectional flow (`StateFlow`) |
| DI | Hilt |
| Persistence | Room (v7) + DataStore |
| Audio | `SoundPool` / `AudioTrack`; WAV packs in assets |
| Background work | WorkManager (reminders, maintenance) |
| Min / target SDK | 26 / 35 |
| Tests | JUnit (domain), Compose / instrumentation tests |

---

## Project layout

```
pitchforge/
├── app/
│   ├── src/main/java/com/pitchforge/app/
│   │   ├── audio/          # playback, washouts, clusters
│   │   ├── data/           # Room entities, DAOs, repositories
│   │   ├── di/             # Hilt modules
│   │   ├── domain/         # adaptive engine (pure Kotlin, unit-tested)
│   │   ├── ui/             # Compose screens (home, lesson, drill, …)
│   │   └── work/           # reminder / maintenance workers
│   ├── src/main/assets/samples/   # per-timbre WAV packs
│   ├── src/test/           # domain unit tests
│   └── src/androidTest/    # Compose / instrumentation tests
├── CHROMAP_REPORT.md       # full product & algorithm report
├── FEATURES.md             # earlier feature inventory (see report for current detail)
├── tools/gen_samples.py    # optional sample-pack generator
├── gradlew
└── README.md
```

The **domain** package is the heart of the app: pitch-set management, deadlines, note selection, lesson planning, streaks/missions, probes, boundary drills. Prefer changing those rules with unit tests rather than only through UI tweaks.

---

## Requirements

- Android Studio (or SDK + JDK 17)
- Android SDK with compile SDK 35
- A device or emulator running Android 8.0+ (API 26+)
- Network access on first Gradle sync (Google Maven / Maven Central)

Optional (only if regenerating instrument samples):

- Python 3
- [FluidSynth](https://www.fluidsynth.org/) and [ffmpeg](https://ffmpeg.org/)
- A royalty-free SoundFont (e.g. GeneralUser GS)

---

## Build & run

From the `pitchforge/` directory:

```bash
# Unit tests (domain / adaptive engine)
./gradlew test

# Debug APK
./gradlew assembleDebug

# Install on a connected device / emulator
./gradlew installDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Sideload with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Generating / refreshing audio samples

Bundled acoustic samples live under `app/src/main/assets/samples/<timbre>/`. Synthetic **sine** / **square** tones are generated in-app.

```bash
cd tools
python3 gen_samples.py \
  --sf2 GeneralUser-GS.sf2 \
  --assets ../app/src/main/assets/samples
```

Do **not** scrape audio from other commercial ear-training apps. Use original or clearly licensed samples only.

---

## Branding & originality

ChromaP recreates a **research-inspired training mechanic and product structure**. The name, copy, UI, and audio are original (or royalty-free). It is not affiliated with any commercial perfect-pitch app and does not reuse trademarked branding or proprietary sample libraries from other products.

---

## AI disclosure

Substantial portions of this project—including application code, domain logic, UI, tests, tooling, and documentation—were **produced with assistance from AI coding tools** (large language models), guided by a detailed human-authored product and research specification.

AI was used to accelerate implementation of an already-specified design. Humans remain responsible for reviewing the result, validating scientific faithfulness of the training loop, checking licensing of assets, and deciding whether the app is fit for real users.

---

## References

1. Wong, A. C.-N., et al. (2019). Absolute pitch can be learned by some adults. *PLoS ONE*. https://pmc.ncbi.nlm.nih.gov/articles/PMC6759182/
2. Wong, A. C.-N., et al. (2020). Is it impossible to acquire absolute pitch in adulthood? *Attention, Perception, & Psychophysics*. https://pubmed.ncbi.nlm.nih.gov/31686378/
3. Wong, A. C.-N., et al. (2025). Learning fast and accurate absolute pitch judgment in adulthood. *Psychonomic Bulletin & Review*. https://pmc.ncbi.nlm.nih.gov/articles/PMC12325523/

---

## License

This project is licensed under the [MIT License](LICENSE).
