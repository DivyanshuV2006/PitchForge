package com.pitchforge.app.domain

import org.junit.Assert.*
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
        assertEquals(200, s.xpForNextLevel) // level 2 -> 3 costs 200
    }

    @Test
    fun `partial progress within a level is reported`() {
        // Level 1 costs 100. 40 xp -> level 1, 40/100.
        val s = LevelSystem.levelForXp(40)
        assertEquals(1, s.level)
        assertEquals(40, s.xpIntoLevel)
        assertEquals(0.4f, s.progress, 0.0001f)
    }

    @Test
    fun `cumulative thresholds accumulate across levels`() {
        // L1->2 = 100, L2->3 = 200. So level 3 begins at 300 total xp.
        assertEquals(3, LevelSystem.levelForXp(300).level)
        assertEquals(2, LevelSystem.levelForXp(299).level)
        // 350 total -> level 3 with 50 into the 300-wide level 3.
        val s = LevelSystem.levelForXp(350)
        assertEquals(3, s.level)
        assertEquals(50, s.xpIntoLevel)
        assertEquals(300, s.xpForNextLevel)
    }

    @Test
    fun `negative xp is clamped to level 1`() {
        val s = LevelSystem.levelForXp(-50)
        assertEquals(1, s.level)
        assertEquals(0, s.totalXp)
    }
}
