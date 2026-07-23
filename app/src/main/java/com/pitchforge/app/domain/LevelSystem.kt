package com.pitchforge.app.domain

/**
 * Maps cumulative XP to a player level and progress toward the next level.
 *
 * Pure Kotlin so it is unit-testable without Android. XP is earned per correct-within-
 * deadline answer (see [PitchForgeRepository.XP_PER_CORRECT]); this turns that running
 * total into a visible progression track.
 *
 * Curve: advancing FROM level L to L+1 costs `100 * L` XP. This gives fast early levels
 * (~1 lesson for level 2) that gradually slow, so the number keeps moving without ever
 * trivializing higher levels.
 */
object LevelSystem {

    /** XP required to advance from [level] to [level] + 1. */
    fun xpForLevel(level: Int): Int = 100 * level.coerceAtLeast(1)

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
