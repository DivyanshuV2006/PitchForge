package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test

class NoteSelectorTest {

    private val selector = NoteSelector()

    @Test
    fun `selectNext returns a note from active set`() {
        val active = listOf(NoteName.C, NoteName.F_SHARP, NoteName.A)
        val accuracies = mapOf(
            NoteName.C to 0.9f,
            NoteName.F_SHARP to 0.5f,
            NoteName.A to 0.8f
        )
        val result = selector.selectNext(active, accuracies, null)
        assertTrue("Result $result should be in active set", result in active)
    }

    @Test
    fun `selectNext avoids small intervals from last note`() {
        val active = NoteName.entries.take(6)
        val accuracies = active.associateWith { 0.5f }
        val lastNote = NoteName.C
        val minInterval = NoteSelector.minIntervalSemitones(active.size)

        var violations = 0
        repeat(100) {
            val result = selector.selectNext(active, accuracies, lastNote)
            val interval = NoteName.intervalSemitones(result, lastNote)
            if (interval <= minInterval) violations++
        }
        assertTrue(
            "Too many interval violations: $violations/100 (min>$minInterval)",
            violations < 10
        )
    }

    @Test
    fun `hard sets use a stricter interval floor`() {
        assertEquals(2, NoteSelector.minIntervalSemitones(3))
        assertEquals(3, NoteSelector.minIntervalSemitones(6))
        assertEquals(4, NoteSelector.minIntervalSemitones(9))
        assertEquals(4, NoteSelector.minIntervalSemitones(12))
    }

    @Test
    fun `low accuracy notes are favored`() {
        val active = listOf(NoteName.C, NoteName.F_SHARP)
        val accuracies = mapOf(
            NoteName.C to 0.9f,  // good
            NoteName.F_SHARP to 0.1f  // struggling
        )

        // F# should appear more often due to lower accuracy
        var fSharpCount = 0
        repeat(50) {
            val result = selector.selectNext(active, accuracies, null)
            if (result == NoteName.F_SHARP) fSharpCount++
        }
        assertTrue("F# should be chosen more than half the time (low accuracy), got $fSharpCount/50",
            fSharpCount > 25)
    }

    @Test
    fun `errorSemitones computes correctly`() {
        assertEquals(0, selector.errorSemitones(NoteName.C, NoteName.C))
        assertEquals(1, selector.errorSemitones(NoteName.C, NoteName.C_SHARP))
        assertEquals(5, selector.errorSemitones(NoteName.C, NoteName.F))
        assertEquals(1, selector.errorSemitones(NoteName.C, NoteName.B)) // wraps around
    }

    @Test
    fun `ema computation is correct`() {
        val result = selector.computeEma(0.5f, true, 0.3f)
        // 0.3 * 1.0 + 0.7 * 0.5 = 0.3 + 0.35 = 0.65
        assertEquals(0.65f, result, 0.001f)

        val result2 = selector.computeEma(0.5f, false, 0.3f)
        // 0.3 * 0.0 + 0.7 * 0.5 = 0.0 + 0.35 = 0.35
        assertEquals(0.35f, result2, 0.001f)
    }
}
