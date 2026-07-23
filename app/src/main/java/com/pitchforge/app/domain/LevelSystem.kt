package com.pitchforge.app.domain

import kotlin.math.roundToInt

/**
 * Maps cumulative XP to a player level and progress toward the next level.
 *
 * Pure Kotlin so it is unit-testable without Android. XP is earned per correct-within-
 * deadline answer (see [PitchForgeRepository.XP_PER_CORRECT]); this turns that running
 * total into a visible progression track.
 *
 * Curve: advancing FROM level L to L+1 costs `100·L + 12·L·(L−1)/2` XP. Early levels stay
 * snappy; mid/late levels gently stretch without freezing progress.
 *
 * Award scaling: [xpMultiplier] softens raw XP as level rises (~100% early → ~60% by 50)
 * so earning itself slows over time without changing the "correct = XP" feel.
 */
object LevelSystem {

    /**
     * XP required to advance from [level] to [level] + 1.
     * L=1 → 100, L=5 → 620, L=10 → 1540, L=20 → 4280 (was 100/500/1000/2000).
     */
    fun xpForLevel(level: Int): Int {
        val L = level.coerceAtLeast(1)
        return 100 * L + (12 * L * (L - 1)) / 2
    }

    /**
     * Soft diminishing returns on XP awards.
     * L1 ≈ 1.00, L10 ≈ 0.89, L25 ≈ 0.75, L50 ≈ 0.59 — floor 0.55.
     */
    fun xpMultiplier(level: Int): Float {
        val L = level.coerceAtLeast(1)
        return (1f / (1f + 0.014f * (L - 1))).coerceIn(0.55f, 1f)
    }

    /** Apply [xpMultiplier] to a base award; at least 1 XP if [baseXp] > 0. */
    fun scaleXp(baseXp: Int, level: Int): Int {
        if (baseXp <= 0) return 0
        return (baseXp * xpMultiplier(level)).roundToInt().coerceAtLeast(1)
    }

    data class LevelState(
        val level: Int,
        val totalXp: Int,
        /** XP accumulated inside the current level. */
        val xpIntoLevel: Int,
        /** XP needed to fill the current level. */
        val xpForNextLevel: Int,
        /** Fraction (0..1) of the current level completed. */
        val progress: Float
    )

    fun levelForXp(totalXp: Int): LevelState {
        var level = 1
        var remaining = totalXp.coerceAtLeast(0)
        while (remaining >= xpForLevel(level)) {
            remaining -= xpForLevel(level)
            level++
        }
        val need = xpForLevel(level)
        return LevelState(
            level = level,
            totalXp = totalXp.coerceAtLeast(0),
            xpIntoLevel = remaining,
            xpForNextLevel = need,
            progress = (remaining.toFloat() / need).coerceIn(0f, 1f)
        )
    }
}
