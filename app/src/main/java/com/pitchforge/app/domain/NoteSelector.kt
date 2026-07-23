package com.pitchforge.app.domain

import kotlin.random.Random

/**
 * Weighted-random note selector implementing weak-note reintroduction (Study A)
 * and interval-avoidance (Study C).
 *
 * Favors:
 * - Pitches with lower recent accuracy in the active set
 * - Previously-mastered pitches not seen in >X days (spaced repetition)
 * - Avoids small-interval consecutive trials; the floor rises as the active set grows
 *   so larger ("hard") sets stay farther from relative-pitch comparison crutches.
 */
class NoteSelector {

    companion object {
        /**
         * Minimum chroma distance from the previous trial. Larger active sets get a
         * stricter floor so consecutive notes can't be named by relative height alone.
         */
        fun minIntervalSemitones(activeSetSize: Int): Int = when {
            activeSetSize >= 9 -> 4
            activeSetSize >= 6 -> 3
            else -> 2
        }

        /** Default EMA blend for ordinary trials. */
        const val DEFAULT_EMA_ALPHA = 0.3f

        /**
         * Stronger EMA blend for the first trial of an adaptive lesson — that tone is a
         * true cold start (no within-session pitch reference), so the outcome is a better
         * absolute-pitch signal than mid-session answers.
         */
        const val SESSION_OPENING_EMA_ALPHA = 0.55f

        /**
         * Mastery-window weight for that same opening trial (counts as this many naming
         * attempts toward the 3-day ≥95% gate). Session score / XP still count it once.
         */
        const val SESSION_OPENING_MASTERY_WEIGHT = 2
    }

    /**
     * Selects the next trial note, avoiding small-interval repetition.
     *
     * @param activePitches pitches in the current active set
     * @param recentAccuracy map of note → rolling EMA accuracy
     * @param lastNote the note from the immediate prior trial (null for first)
     * @param masteredPitches pitches that are mastered but in spaced review
     * @return the selected NoteName
     */
    fun selectNext(
        activePitches: List<NoteName>,
        recentAccuracy: Map<NoteName, Float>,
        lastNote: NoteName?,
        masteredPitches: List<NoteName> = emptyList()
    ): NoteName {
        // Build candidate pool: active pitches + some mastered for review
        val candidates = mutableListOf<NoteName>()
        candidates.addAll(activePitches)

        // Add mastered pitches at ~20% rate
        if (masteredPitches.isNotEmpty() && Random.nextFloat() < 0.20f) {
            candidates.addAll(masteredPitches.shuffled().take(2))
        }

        val minInterval = minIntervalSemitones(activePitches.size)

        // Prefer strict filter; if that empties the pool, loosen one step at a time.
        val pool = if (lastNote != null && candidates.size > 2) {
            var threshold = minInterval
            var filtered = candidates.filter {
                NoteName.intervalSemitones(it, lastNote) > threshold
            }
            while (filtered.isEmpty() && threshold > 1) {
                threshold--
                filtered = candidates.filter {
                    NoteName.intervalSemitones(it, lastNote) > threshold
                }
            }
            if (filtered.isEmpty()) candidates else filtered
        } else {
            candidates
        }

        // Weighted random: lower accuracy → higher weight
        val weights = pool.map { note ->
            val acc = recentAccuracy[note] ?: 0.5f
            // Invert: low accuracy = high weight, but never zero
            maxOf(1.0f - acc, 0.05f)
        }
        val totalWeight = weights.sum()
        val roll = Random.nextFloat() * totalWeight
        var cumulative = 0.0f
        for (i in pool.indices) {
            cumulative += weights[i]
            if (roll <= cumulative) return pool[i]
        }
        return pool.last()
    }

    /**
     * Computes EMA accuracy. Alpha controls how much weight recent attempts get.
     */
    fun computeEma(
        currentEma: Float,
        correct: Boolean,
        alpha: Float = DEFAULT_EMA_ALPHA
    ): Float {
        return (alpha * (if (correct) 1.0f else 0.0f)) + ((1.0f - alpha) * currentEma)
    }

    /**
     * Computes error in semitones between the correct note and the user's answer.
     * 0 = correct, 1 = one semitone off, etc.
     */
    fun errorSemitones(correct: NoteName, userAnswer: NoteName): Int {
        return NoteName.intervalSemitones(correct, userAnswer)
    }
}
