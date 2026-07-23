package com.pitchforge.app.audio

/**
 * Spectral shape for inter-trial washout noise. Heavier bass for low octaves, brighter
 * noise for high octaves — matched to the trial tone we're trying to erase from memory.
 */
enum class NoiseColor {
    /** 1/f² — low-frequency weighted; best mask for octave ≤3. */
    BROWN,
    /** 1/f — balanced musical midrange; octave 4. */
    PINK,
    /** Flat spectrum; octave 5 default. */
    WHITE,
    /** High-frequency weighted; octave ≥6 (and bright octave-5 variant). */
    BLUE;

    companion object {
        /** Pick a washout spectrum from the octave of the tone just heard. */
        fun forOctave(octave: Int): NoiseColor = when {
            octave <= 3 -> BROWN
            octave == 4 -> PINK
            octave == 5 -> WHITE
            else -> BLUE
        }
    }
}
