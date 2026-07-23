package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StreakManagerTest {

    private val streakManager = StreakManager()

    @Test
    fun `first practice sets streak to 1`() {
        val streak = streakManager.computeStreak(0, null, true)
        assertEquals(1, streak)
    }

    @Test
    fun `consecutive day increments streak`() {
        val yesterday = LocalDate.now().minusDays(1)
        val streak = streakManager.computeStreak(5, yesterday, true)
        assertEquals(6, streak)
    }

    @Test
    fun `gap of 2 days resets streak`() {
        val twoDaysAgo = LocalDate.now().minusDays(3)
        val streak = streakManager.computeStreak(10, twoDaysAgo, true)
        assertEquals(1, streak)
    }

    @Test
    fun `same day does not change streak`() {
        val today = LocalDate.now()
        val streak = streakManager.computeStreak(5, today, true)
        assertEquals(5, streak)
    }
}

class MissionEngineTest {

    private val engine = MissionEngine()

    @Test
    fun `generateDailyMissions returns the fixed three missions`() {
        val missions = engine.generateDailyMissions()
        assertEquals(3, missions.size)
        assertEquals(
            listOf(
                MissionEngine.MissionType.COMPLETE_LESSON,
                MissionEngine.MissionType.SCORE_EIGHT,
                MissionEngine.MissionType.PRACTICE_TIME
            ),
            missions.map { it.type }
        )
    }

    @Test
    fun `missions have the expected targets and xp reward`() {
        val missions = engine.generateDailyMissions()
        assertEquals(1, missions[0].target)
        assertEquals(1, missions[1].target)
        assertEquals(10, missions[2].target)
        assertEquals(20, MissionEngine.XP_REWARD)
        assertEquals(24, MissionEngine.SCORE_TARGET)
    }
}
