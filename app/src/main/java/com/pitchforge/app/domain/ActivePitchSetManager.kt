package com.pitchforge.app.domain

import kotlin.math.min
import kotlin.math.max

/**
 * Manages the active pitch set — the core mechanic from Study B.
 *
 * A user starts with 3 pitches (maximally spread) and expands one at a time
 * up to 12 as mastery is demonstrated on the current set.
 */
class ActivePitchSetManager(
    initialSize: Int = 3
) {
    var activePitchSetSize: Int = initialSize.coerceIn(3, 12)
        private set

    /** The maximum number of concurrently-trained pitches. */
    val maxPitchSetSize = 12

    companion object {
        /** Minimum qualifying naming attempts inside the mastery window. */
        const val MASTERY_MIN_ATTEMPTS = 15
        /** Trailing-window naming accuracy required to master a note / expand the set. */
        const val MASTERY_ACCURACY = 0.95f
    }

    /**
     * Selects [count] pitches maximally (evenly) spread across the chromatic circle.
     *
     * Uses even float spacing rounded to the nearest semitone rather than an integer
     * step, so a 5-pitch set spreads across the full octave (C, D, F, G, A#) instead of
     * clustering into the lower 8 semitones (the old `12 / count` integer-step bug).
     *
     * Note on spacing: with N pitches over 12 semitones the best achievable minimum
     * interval is `floor(12 / N)`. For the default 3-pitch start this yields 4 semitones
     * (all pairs > 2, per §2.4a and test #9). For 5-6 pitches the theoretical minimum is
     * 2 semitones — it is mathematically impossible to keep every pair > 2 apart once the
     * set has more than 4 concurrent pitches, so we simply guarantee maximal even spread.
     */
    fun selectSpreadPitches(count: Int): List<NoteName> {
        val effective = count.coerceIn(3, 12)
        val semitones = LinkedHashSet<Int>()
        var i = 0
        // Even spacing; round each slot and skip rare rounding collisions by nudging.
        while (semitones.size < effective && i < 12) {
            val target = Math.round(i * 12.0 / effective).toInt() % 12
            var s = target
            while (s in semitones) s = (s + 1) % 12
            semitones.add(s)
            i++
        }
        // Fill any remainder (defensive; not hit for counts 3-12).
        var fill = 0
        while (semitones.size < effective) {
            if (fill !in semitones) semitones.add(fill)
            fill++
        }
        return semitones.map { NoteName.fromSemitone(it) }
    }

    /**
     * Returns true if the set should expand by 1.
     * Conditions: every pitch in the current set has ≥90% accuracy over ≥15 attempts
     * within its current deadline.
     */
    fun shouldExpand(
        pitchProgresses: List<PitchProgressSnapshot>,
        currentSize: Int = activePitchSetSize
    ): Boolean {
        if (currentSize >= maxPitchSetSize) return false
        val active = pitchProgresses.filter { it.isActive }
        if (active.size < currentSize) return false
        return active.all { it.meetsMastery() }
    }

    /** Expands the set by exactly one pitch — the one least-represented
     *  in terms of interval distance from existing active pitches. */
    fun expandSet(existingActive: List<NoteName>): NoteName {
        val activeSemitones = existingActive.map { it.semitone }.toSet()
        val candidates = NoteName.entries.filterNot { it.semitone in activeSemitones }

        // Pick the candidate with the largest minimum interval to any active pitch
        val chosen = candidates.maxByOrNull { candidate ->
            existingActive.minOf { NoteName.intervalSemitones(candidate, it) }
        }
        return chosen ?: candidates.first()
    }

    /** Determines starting set size from baseline accuracy. */
    fun startingSizeFromBaseline(baselineAccuracy: Float): Int {
        return when {
            baselineAccuracy >= 0.50f -> 6
            baselineAccuracy >= 0.30f -> 4
            else -> 3
        }
    }
}

data class PitchProgressSnapshot(
    val noteName: NoteName,
    val isActive: Boolean,
    val currentDeadlineMs: Int,
    val emaAccuracyWithinDeadline: Float,
    val attemptCount: Int,
    val masteredAt: Long? = null,
    val correctWithinDeadlineCount: Int = 0,
    /** Naming attempts within the trailing mastery window (e.g. last 3 days). */
    val windowAttemptCount: Int = 0,
    /** Of those, how many were correct within their deadline. */
    val windowCorrectCount: Int = 0
) {
    /**
     * Mastery: ≥95% naming accuracy over the trailing window — at least
     * [ActivePitchSetManager.MASTERY_MIN_ATTEMPTS] qualifying naming attempts in the
     * window, of which ≥ [ActivePitchSetManager.MASTERY_ACCURACY] were correct within
     * their deadline.
     */
    fun meetsMastery(): Boolean {
        if (windowAttemptCount < ActivePitchSetManager.MASTERY_MIN_ATTEMPTS) return false
        return windowCorrectCount.toFloat() / windowAttemptCount >= ActivePitchSetManager.MASTERY_ACCURACY
    }

    /** Whether a pitch is still in the learning phase (<10 exposures). */
    fun isNewPitch(): Boolean = attemptCount < 10
}
