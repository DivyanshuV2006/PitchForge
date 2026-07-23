package com.pitchforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotivationTest {

    @Test
    fun `next note prefers weak note when learning`() {
        val focus = NextNoteClarity.pick(
            learning = listOf(NoteName.C, NoteName.G),
            accuracyByNote = mapOf(NoteName.C to 0.9f, NoteName.G to 0.4f),
            weakNote = NoteName.G
        )
        assertEquals(NoteName.G, focus?.note)
    }

    @Test
    fun `next note picks highest accuracy learner when no weak note`() {
        val focus = NextNoteClarity.pick(
            learning = listOf(NoteName.C, NoteName.D),
            accuracyByNote = mapOf(NoteName.C to 0.5f, NoteName.D to 0.8f),
            weakNote = null
        )
        assertEquals(NoteName.D, focus?.note)
        assertNull(NextNoteClarity.pick(emptyList(), emptyMap(), null))
    }

    @Test
    fun `session note breakdown tags strong and slipped`() {
        val rows = listOf(
            NoteName.C to true,
            NoteName.C to true,
            NoteName.C to true,
            NoteName.G to false,
            NoteName.G to false
        )
        val breakdown = SessionFeedback.noteBreakdown(rows)
        assertEquals("Strong", breakdown.first { it.note == NoteName.C }.tag)
        assertEquals("Slipped", breakdown.first { it.note == NoteName.G }.tag)
    }

    @Test
    fun `plateau message waits for enough history`() {
        assertNull(PlateauMessaging.message(2, 0, emptyList(), null))
        assertTrue(
            PlateauMessaging.message(6, 0, emptyList(), null)?.contains("Mastery is slow") == true
        )
        assertTrue(
            PlateauMessaging.message(8, 1, emptyList(), lessonsSinceLastMastery = 6)
                ?.contains("flat stretch") == true
        )
    }
}
