package com.pitchforge.app.domain

import java.time.LocalDate

/**
 * Manages streak logic.
 * A streak is consecutive days with ≥1 completed lesson.
 * Streak-freeze grace period: until local midnight + N hours.
 */
class StreakManager(
    private val graceHours: Int = 4
) {
    /**
     * Computes new streak given last practice date and whether user practiced today.
     * @return new streak count
     */
    fun computeStreak(
        currentStreak: Int,
        lastPracticeDate: LocalDate?,
        practiceCompletedToday: Boolean
    ): Int {
        if (!practiceCompletedToday) return currentStreak

        val today = LocalDate.now()
        return when {
            lastPracticeDate == null -> 1
            lastPracticeDate == today -> currentStreak // already counted today
            lastPracticeDate == today.minusDays(1) -> currentStreak + 1
            else -> 1 // gap of 2+ days, reset
        }
    }

    /**
     * Checks if a practice timestamp falls within the grace window
     * of the previous day (e.g., practicing at 1am counts as "yesterday").
     */
    fun isWithinGraceWindow(practiceHour: Int): Boolean {
        return practiceHour < graceHours
    }
}

/**
 * Fixed daily missions — the same three every day, each worth [XP_REWARD] XP on completion.
 */
class MissionEngine {
    enum class MissionType(val description: String) {
        COMPLETE_LESSON("Complete 1 daily lesson"),
        SCORE_EIGHT("Score at least 24/30 on daily lesson"),
        PRACTICE_TIME("Practice for 10 minutes")
    }

    data class DailyMission(
        val type: MissionType,
        val target: Int,
        val progress: Int = 0,
        val completed: Boolean = false
    )

    companion object {
        const val XP_REWARD = 20
        /** Correct-within-deadline answers needed for the score mission (~80% of 30). */
        const val SCORE_TARGET = 24
    }

    fun generateDailyMissions(): List<DailyMission> = listOf(
        DailyMission(MissionType.COMPLETE_LESSON, target = 1),
        DailyMission(MissionType.SCORE_EIGHT, target = 1),
        DailyMission(MissionType.PRACTICE_TIME, target = 10)
    )
}
