package com.pitchforge.app.domain

import kotlin.random.Random

/**
 * Anti–relative-pitch timing: variable washout ISI, post-feedback masks, and occasional
 * cold-start probes (long silence so no recent pitch sits in echoic memory).
 */
object InterTrialPolicy {
    const val ISI_MIN_MS = 1_500
    const val ISI_MAX_MS = 4_000
    /** Short mask after the correct-note feedback replay. */
    const val FEEDBACK_MASK_MS = 1_000
    /** Let the reinforcement sample ring before masking it. */
    const val FEEDBACK_REPLAY_SETTLE_MS = 800
    /** Brief noise before a cold-start silence window. */
    const val COLD_CLEAR_MS = 1_500
    const val COLD_SILENCE_MIN_MS = 5_000
    const val COLD_SILENCE_MAX_MS = 8_000
    /** Cluster distractor length (dissonant chord instead of noise). */
    const val CLUSTER_MS = 2_000

    /**
     * Daily adaptive-lesson dose. Research-style distributed practice favors ~1–2 solid
     * sessions; a hard stop at 4 plus a 20-minute gap between sessions keeps cramming down.
     */
    const val DAILY_LESSON_RECOMMENDED = 2
    const val DAILY_LESSON_HARD_CAP = 4
    const val SESSION_COOLDOWN_MS = 20 * 60 * 1000L

    fun randomIsiMs(random: Random = Random.Default): Int =
        random.nextInt(ISI_MIN_MS, ISI_MAX_MS + 1)

    fun randomColdSilenceMs(random: Random = Random.Default): Int =
        random.nextInt(COLD_SILENCE_MIN_MS, COLD_SILENCE_MAX_MS + 1)

    /** Milliseconds left before the next adaptive lesson may start (0 = ready). */
    fun cooldownRemainingMs(lastCompletedAtMs: Long?, nowMs: Long = System.currentTimeMillis()): Long {
        if (lastCompletedAtMs == null) return 0L
        return (SESSION_COOLDOWN_MS - (nowMs - lastCompletedAtMs)).coerceAtLeast(0L)
    }

    fun formatCooldown(remainingMs: Long): String {
        val totalSec = ((remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    enum class SessionGate { AVAILABLE, COOLDOWN, DOSE_COMPLETE }

    fun sessionGate(
        lessonsCompletedToday: Int,
        lastCompletedAtMs: Long?,
        nowMs: Long = System.currentTimeMillis()
    ): SessionGate = when {
        lessonsCompletedToday >= DAILY_LESSON_HARD_CAP -> SessionGate.DOSE_COMPLETE
        cooldownRemainingMs(lastCompletedAtMs, nowMs) > 0L -> SessionGate.COOLDOWN
        else -> SessionGate.AVAILABLE
    }

    /**
     * ~1 cold-start every 7 trials (2–4 per 30-question lesson), spaced ≥5 apart,
     * never on the first few questions.
     */
    fun pickColdStartIndices(
        questionCount: Int,
        random: Random = Random.Default
    ): Set<Int> {
        if (questionCount < 8) return emptySet()
        val targets = (questionCount / 7).coerceIn(2, 4)
        val picked = mutableSetOf<Int>()
        var attempts = 0
        while (picked.size < targets && attempts++ < 60) {
            val i = random.nextInt(5, questionCount)
            if (picked.none { kotlin.math.abs(it - i) < 5 }) {
                picked += i
            }
        }
        return picked
    }

    /**
     * Indices that use a dissonant chord/cluster washout instead of colored noise
     * (~every 6–8 trials after the first few).
     */
    fun pickClusterWashoutIndices(
        questionCount: Int,
        avoid: Set<Int> = emptySet(),
        random: Random = Random.Default
    ): Set<Int> {
        if (questionCount < 7) return emptySet()
        val picked = mutableSetOf<Int>()
        var i = random.nextInt(6, 9)
        while (i < questionCount) {
            if (i !in avoid) picked += i
            i += random.nextInt(6, 9)
        }
        return picked
    }
}
