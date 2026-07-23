package com.pitchforge.app.audio

import com.pitchforge.app.domain.NoteName

/**
 * Abstraction over note playback so the lesson engine can be driven by a fake in tests
 * (e.g. to inject an artificial audio-start delay for the onset-timing test, §6 #12).
 *
 * [play] returns the audio-onset timestamp in millis — the epoch the response-time timer
 * must use (§3), not the time the play() call was issued.
 */
interface NotePlayer {
    fun play(timbre: String, octave: Int, note: NoteName): Long
    suspend fun ensureLoaded(timbre: String, octaves: List<Int>)
    fun availableTimbres(): List<String>

    /**
     * Stops any ringing trial tone and plays [durationMs] of colored noise so the previous
     * pitch is harder to hold as a relative-pitch reference.
     *
     * [octave] should be the octave of the tone just heard (or about to be masked); the
     * spectrum is chosen via [NoiseColor.forOctave] (brown→low, blue→high).
     * Suspends until the washout finishes.
     */
    suspend fun playNoiseWashout(
        octave: Int,
        durationMs: Int = DEFAULT_WASHOUT_MS
    )

    /**
     * Dissonant multi-tone cluster washout (alternative to noise) centered near [octave].
     * Suspends until playback finishes.
     */
    suspend fun playClusterWashout(
        octave: Int,
        durationMs: Int = DEFAULT_WASHOUT_MS
    )

    /** Immediately silence any playing trial tone or washout (e.g. when leaving a screen). */
    fun stopAll()

    companion object {
        const val DEFAULT_WASHOUT_MS = 2000
    }
}
