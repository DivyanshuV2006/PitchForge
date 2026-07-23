package com.pitchforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelSystemTest {

    @Test
    fun `zero xp is level 1 with empty progress`() {
        val s = LevelSystem.levelForXp(0)
        assertEquals(1, s.level)
        assertEquals(0, s.xpIntoLevel)
        assertEquals(100, s.xpForNextLevel)
        assertEquals(0f, s.progress, 0.0001f)
    }

    @Test
    fun `100 xp reaches level 2 exactly`() {
        val s = LevelSystem.levelForXp(100)
        assertEquals(2, s.level)
        assertEquals(0, s.xpIntoLevel)
        // L2→3 costs 100*2 + 12*2*1/2 = 212
        assertEquals(212, s.xpForNextLevel)
    }

    @Test
    fun `partial progress within a level is reported`() {
        val s = LevelSystem.levelForXp(40)
        assertEquals(1, s.level)
        assertEquals(40, s.xpIntoLevel)
        assertEquals(0.4f, s.progress, 0.0001f)
    }

    @Test
    fun `cumulative thresholds accumulate across levels`() {
        // L1→2 = 100, L2→3 = 212. Level 3 begins at 312 total xp.
        assertEquals(3, LevelSystem.levelForXp(312).level)
        assertEquals(2, LevelSystem.levelForXp(311).level)
        val s = LevelSystem.levelForXp(312 + 50)
        assertEquals(3, s.level)
        assertEquals(50, s.xpIntoLevel)
        // L3→4 = 100*3 + 12*3*2/2 = 300 + 36 = 336
        assertEquals(336, s.xpForNextLevel)
    }

    @Test
    fun `higher levels cost more than the old linear curve`() {
        // Old curve was 100*L. Soft quadratic should exceed that for L >= 2.
        assertTrue(LevelSystem.xpForLevel(10) > 1000)
        assertTrue(LevelSystem.xpForLevel(20) > 2000)
    }

    @Test
    fun `negative xp is clamped to level 1`() {
        val s = LevelSystem.levelForXp(-50)
        assertEquals(1, s.level)
        assertEquals(0, s.totalXp)
    }

    @Test
    fun `xp multiplier softens awards over levels but stays above floor`() {
        assertEquals(1f, LevelSystem.xpMultiplier(1), 0.001f)
        assertTrue(LevelSystem.xpMultiplier(10) < 1f)
        assertTrue(LevelSystem.xpMultiplier(10) > 0.85f)
        assertTrue(LevelSystem.xpMultiplier(50) >= 0.55f)
        assertTrue(LevelSystem.xpMultiplier(50) < 0.65f)
    }

    @Test
    fun `scaleXp never zeroes a positive award`() {
        assertEquals(0, LevelSystem.scaleXp(0, 50))
        assertTrue(LevelSystem.scaleXp(10, 50) >= 1)
        assertEquals(10, LevelSystem.scaleXp(10, 1))
    }
}
