package com.pitchforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticThemeTest {

    @Test
    fun `studio is unlocked from level 1`() {
        assertTrue(CosmeticTheme.STUDIO.isUnlocked(1))
        assertEquals(listOf(CosmeticTheme.STUDIO), CosmeticTheme.unlocked(1))
    }

    @Test
    fun `ocean unlocks at level 10`() {
        assertFalse(CosmeticTheme.OCEAN.isUnlocked(9))
        assertTrue(CosmeticTheme.OCEAN.isUnlocked(10))
        assertEquals(
            listOf(CosmeticTheme.STUDIO, CosmeticTheme.OCEAN),
            CosmeticTheme.unlocked(10)
        )
    }

    @Test
    fun `themes unlock every 10 levels`() {
        assertEquals(1, CosmeticTheme.STUDIO.unlockLevel)
        assertEquals(10, CosmeticTheme.OCEAN.unlockLevel)
        assertEquals(20, CosmeticTheme.FOREST.unlockLevel)
        assertEquals(30, CosmeticTheme.VOLCANIC.unlockLevel)
        assertEquals(40, CosmeticTheme.AURORA.unlockLevel)
        assertEquals(50, CosmeticTheme.MIDNIGHT.unlockLevel)
    }

    @Test
    fun `nextUnlock points at the next locked pack`() {
        assertEquals(CosmeticTheme.OCEAN, CosmeticTheme.nextUnlock(1))
        assertEquals(CosmeticTheme.FOREST, CosmeticTheme.nextUnlock(10))
        assertNull(CosmeticTheme.nextUnlock(50))
    }

    @Test
    fun `fromId falls back to studio`() {
        assertEquals(CosmeticTheme.OCEAN, CosmeticTheme.fromId("ocean"))
        assertEquals(CosmeticTheme.STUDIO, CosmeticTheme.fromId("nope"))
        assertEquals(CosmeticTheme.STUDIO, CosmeticTheme.fromId(null))
    }
}
