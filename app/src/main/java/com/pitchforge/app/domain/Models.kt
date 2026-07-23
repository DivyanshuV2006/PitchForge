package com.pitchforge.app.domain

/** All 12 chromatic note names. Index = semitone offset from C. */
enum class NoteName(val semitone: Int, val label: String) {
    C(0, "C"),
    C_SHARP(1, "C#"),
    D(2, "D"),
    D_SHARP(3, "D#"),
    E(4, "E"),
    F(5, "F"),
    F_SHARP(6, "F#"),
    G(7, "G"),
    G_SHARP(8, "G#"),
    A(9, "A"),
    A_SHARP(10, "A#"),
    B(11, "B");

    companion object {
        fun fromSemitone(s: Int): NoteName = entries.first { it.semitone == ((s % 12) + 12) % 12 }

        /** Parse a display label ("C", "C#", ...) back into a NoteName. */
        fun fromLabel(label: String): NoteName = entries.first { it.label == label }

        /** Returns the smallest semitone interval between two notes (0–6). */
        fun intervalSemitones(a: NoteName, b: NoteName): Int {
            val raw = kotlin.math.abs(a.semitone - b.semitone)
            return minOf(raw, 12 - raw)
        }
    }
}

/** Exercise types from Study B. */
enum class TaskType { NAMING, VERIFICATION }

/** Trained vs untrained timbre for generalization. */
enum class TimbreCategory { TRAINED, UNTRAINED }
