# PitchForge

**PitchForge** is a free, offline-first Android app that trains absolute pitch (“perfect pitch”) through short, adaptive daily lessons. It is built around mechanics drawn from peer-reviewed adult absolute-pitch acquisition research—not a generic “play a note, guess it” ear trainer.

There are **no ads, no subscriptions, and no in-app purchases**. Every feature is available to every user from first install.

---

## What it does

PitchForge teaches you to name musical notes **without a reference tone**. Lessons feel a bit like a language app (streaks, daily missions, short sessions) and a bit like a lab ear-training protocol (response deadlines, pitch-set expansion, generalization checks).

A typical session:

1. Hear an isolated note
2. Answer with a naming task (pick the note) or a verification task (“is this a C?”)
3. Get immediate feedback—including “right note, but too slow” when you beat the pitch but miss the deadline
4. Repeat for ~8–12 trials (~3–5 minutes)

Progress is stored locally with Room and works fully offline after install.

---

## The science behind the training loop

The core loop follows published adult AP-training protocols (Wong and colleagues), especially:

| Study | Contribution mirrored in the app |
| --- | --- |
| Wong et al. (2019) | Weak-note reintroduction; multi-timbre training; retention follow-ups |
| Wong et al. (2020) | Active pitch set starts small (~3) and grows toward 12; naming + verification tasks |
| Wong et al. (2025) | Shrinking response-time window; error size in semitones; untrained-timbre generalization |

Honest expectations matter: meaningful improvement typically takes **weeks of regular practice** (studies used on the order of 12–40+ hours over ~8 weeks). Only a minority of trained adults reached full 12-pitch AP in those studies. PitchForge does **not** claim to guarantee perfect pitch.

### Intentional product deviation from the lab protocol

In the lab, researchers could pace difficulty carefully. For at-home use, if accuracy **drops after a deadline tightens**, PitchForge will **loosen the deadline one step** instead of leaving the learner stuck on an impossible timer. That is a deliberate product adaptation (see `DeadlineManager`) and should be treated as a disclosed departure from the strict Study C protocol—not as identical replication of the experiment.

---

## Features

- **Onboarding + AP diagnostic** — short intro plus a no-feedback pre-test that seeds baseline accuracy / error and starting pitch-set size
- **Adaptive lessons** — dynamically generated from weak-note stats and current active pitch set
- **Active pitch-set expansion** — start with maximally spread pitches (not adjacent semitones); expand 3 → 12 only after mastery criteria are met
- **Shrinking RT deadlines** — per-pitch windows that tighten with accuracy (floor ~1300 ms), measured from audio onset
- **Multiple timbres** — piano, flute, guitar, cello, clarinet, harpsichord, violin, trumpet, plus synthesized sine/square
- **Generalization probes** — occasional untrained-timbre checks scored separately from mastery
- **Retention checks** — later follow-ups on previously mastered pitches
- **Daily missions & streaks** — lightweight habit loop with optional practice reminders (WorkManager)
- **Skill challenges** — harder “test” modes that do not feed the practice model the same way as lessons
- **Dashboard** — accuracy over time, per-note breakdown, streak / practice stats from local data
- **Settings** — timbres, volume, reminders, theme preferences
- **Accessibility-minded UI** — content descriptions, system font scale, dark mode support

---

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM, unidirectional flow (`StateFlow`) |
| DI | Hilt |
| Persistence | Room + DataStore |
| Audio | `SoundPool` / synthesized tones; WAV sample packs in assets |
| Background work | WorkManager (reminders, maintenance) |
| Min / target SDK | 26 / 35 |
| Tests | JUnit, Turbine, Compose UI tests |

---

## Project layout

```
pitchforge/
├── app/
│   ├── src/main/java/com/pitchforge/app/
│   │   ├── audio/          # playback
│   │   ├── data/           # Room entities, DAOs, repositories
│   │   ├── di/             # Hilt modules
│   │   ├── domain/         # adaptive engine (pure Kotlin, heavily unit-tested)
│   │   ├── ui/             # Compose screens (onboarding, lesson, dashboard, …)
│   │   └── work/           # reminder / maintenance workers
│   ├── src/main/assets/samples/   # per-timbre WAV packs
│   ├── src/test/           # domain unit tests
│   └── src/androidTest/    # Compose / instrumentation tests
├── tools/gen_samples.py    # optional sample-pack generator
├── gradlew
└── README.md
```

The **domain** package is the heart of the app: active pitch-set management, deadlines, note selection, lesson planning, streaks/missions, generalization/retention policies. Prefer changing those rules with unit tests rather than only through UI tweaks.

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

Or copy the APK to a phone and enable install from unknown sources.

---

## Generating / refreshing audio samples

Bundled acoustic samples live under `app/src/main/assets/samples/<timbre>/`. Piano and other packs may already be present; synthetic **sine** / **square** tones are generated in-app.

To regenerate royalty-free packs with FluidSynth (GeneralUser GS):

```bash
cd tools
# Download a SoundFont first, then:
python3 gen_samples.py \
  --sf2 GeneralUser-GS.sf2 \
  --assets ../app/src/main/assets/samples
```

Do **not** scrape audio from other commercial ear-training apps. Use original or clearly licensed samples only.

---

## Branding & originality

PitchForge recreates a **research-inspired training mechanic and product structure**. The name, copy, UI, and audio are original (or royalty-free). It is not affiliated with any commercial perfect-pitch app and does not reuse trademarked branding or proprietary sample libraries from other products.

---

## AI disclosure

Substantial portions of this project—including application code, domain logic, UI, tests, tooling, and this README—were **produced with assistance from AI coding tools** (large language models), guided by a detailed human-authored product and research specification.

AI was used to accelerate implementation of an already-specified design. Humans remain responsible for reviewing the result, validating scientific faithfulness of the training loop, checking licensing of assets, and deciding whether the app is fit for real users. Treat generated code and documentation as starting points that still need scrutiny—not as guaranteed correctness or clinical claims.

---

## References

1. Wong, A. C.-N., et al. (2019). Absolute pitch can be learned by some adults. *PLoS ONE*. https://pmc.ncbi.nlm.nih.gov/articles/PMC6759182/
2. Wong, A. C.-N., et al. (2020). Is it impossible to acquire absolute pitch in adulthood? *Attention, Perception, & Psychophysics*. https://pubmed.ncbi.nlm.nih.gov/31686378/
3. Wong, A. C.-N., et al. (2025). Learning fast and accurate absolute pitch judgment in adulthood. *Psychonomic Bulletin & Review*. https://pmc.ncbi.nlm.nih.gov/articles/PMC12325523/

---

## License

This project is licensed under the [MIT License](LICENSE).
