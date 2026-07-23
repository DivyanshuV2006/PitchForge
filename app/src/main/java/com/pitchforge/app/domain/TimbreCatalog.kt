package com.pitchforge.app.domain

/**
 * Canonical timbre inventory.
 *
 * Study A / Van Hedger, Heald & Nusbaum (2019) trained on **seven** instrumental timbres
 * (160 notes total): piano, flute, guitar, cello, clarinet, harpsichord, and square wave.
 * ChromaP ships that full set (square synthesized; the rest as A440 WAV packs across
 * octaves 3–5) plus violin/trumpet/sine extras already used in training and challenges.
 */
object TimbreCatalog {

    /** The seven Study A training timbres. */
    val STUDY_A: List<String> = listOf(
        "piano",
        "flute",
        "guitar",
        "cello",
        "clarinet",
        "harpsichord",
        "square"
    )

    /** Synthesized at runtime (no asset files). */
    val SYNTH: Set<String> = setOf("sine", "square")

    /**
     * Settings / challenge selectable list. Study A set first, then extras.
     * Order is stable for UI.
     */
    val SELECTABLE: List<String> = listOf(
        "piano",
        "flute",
        "guitar",
        "cello",
        "clarinet",
        "harpsichord",
        "square",
        "sine",
        "violin",
        "trumpet"
    )

    /** Prefer these when picking an untrained generalization probe. */
    val PROBE_POOL: List<String> = STUDY_A + listOf("violin", "trumpet", "sine")
}
