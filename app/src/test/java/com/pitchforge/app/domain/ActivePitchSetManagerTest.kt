package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test

class ActivePitchSetManagerTest {

    private val manager = ActivePitchSetManager()

    @Test
    fun `initial set size starts at 3`() {
        assertEquals(3, manager.activePitchSetSize)
    }

    @Test
    fun `default 3-pitch set has all pairs more than 2 semitones apart`() {
        // Spec test #9: default start of 3, no two within 2 semitones (e.g. not C/C#/D).
        val pitches = manager.selectSpreadPitches(3)
        assertEquals(3, pitches.size)
        for (i in pitches.indices) {
            for (j in i + 1 until pitches.size) {
                val interval = NoteName.intervalSemitones(pitches[i], pitches[j])
                assertTrue("Pitches ${pitches[i]} and ${pitches[j]} are $interval semitones apart (≤2)",
                    interval > 2)
            }
        }
    }

    @Test
    fun `spread sizes 3-6 achieve the maximal even minimum interval`() {
        // With N pitches over 12 semitones the best possible min interval is floor(12/N).
        // 3 -> 4, 4 -> 3, 5 -> 2, 6 -> 2. Assert we hit that ceiling (no clustering bug).
        for (n in 3..6) {
            val pitches = manager.selectSpreadPitches(n)
            assertEquals("Wrong count for $n", n, pitches.size)
            assertEquals("Duplicate pitches for size $n", n, pitches.toSet().size)
            val minInterval = pitches.indices.flatMap { i ->
                (i + 1 until pitches.size).map { j -> NoteName.intervalSemitones(pitches[i], pitches[j]) }
            }.min()
            assertEquals("Size $n not maximally spread", 12 / n, minInterval)
        }
    }

    // Helper: a snapshot with a given trailing-window naming record.
    private fun snap(note: NoteName, windowAttempts: Int, windowCorrect: Int) =
        PitchProgressSnapshot(
            noteName = note, isActive = true, currentDeadlineMs = 1600,
            emaAccuracyWithinDeadline = 1.0f, attemptCount = windowAttempts,
            windowAttemptCount = windowAttempts, windowCorrectCount = windowCorrect
        )

    @Test
    fun `shouldExpand returns false when a note is below 95 percent over the window`() {
        val pitches = listOf(
            snap(NoteName.C, 20, 20),
            snap(NoteName.F_SHARP, 20, 20),
            snap(NoteName.A, 20, 18) // 18/20 = 90% < 95%
        )
        assertFalse(manager.shouldExpand(pitches))
    }

    @Test
    fun `shouldExpand returns true when every note is at least 95 percent over the window`() {
        val pitches = listOf(
            snap(NoteName.C, 20, 19),   // 95%
            snap(NoteName.F_SHARP, 20, 20),
            snap(NoteName.A, 15, 15)
        )
        assertTrue(manager.shouldExpand(pitches))
    }

    @Test
    fun `meetsMastery requires 95 percent not 100`() {
        assertFalse(snap(NoteName.C, 20, 18).meetsMastery()) // 90%
        assertTrue(snap(NoteName.C, 20, 19).meetsMastery())  // 95%
        assertTrue(snap(NoteName.C, 20, 20).meetsMastery())  // 100%
    }

    @Test
    fun `shouldExpand returns false when window attempt count below 15`() {
        val pitches = listOf(
            snap(NoteName.C, 14, 14),
            snap(NoteName.F_SHARP, 14, 14),
            snap(NoteName.A, 14, 14)
        )
        assertFalse(manager.shouldExpand(pitches))
    }

    @Test
    fun `expandSet adds maximally spread pitch`() {
        val active = listOf(NoteName.C, NoteName.F_SHARP, NoteName.A)
        val newPitch = manager.expandSet(active)
        assertFalse("New pitch $newPitch should not already be active", newPitch in active)
        // Should be spread — maximize min interval
        val minInterval = active.minOf { NoteName.intervalSemitones(newPitch, it) }
        assertTrue("New pitch $newPitch is only $minInterval semitones from closest active pitch",
            minInterval >= 1)
    }

    @Test
    fun `startingSizeFromBaseline returns correct sizes`() {
        assertEquals(3, manager.startingSizeFromBaseline(0.1f))
        assertEquals(4, manager.startingSizeFromBaseline(0.3f))
        assertEquals(6, manager.startingSizeFromBaseline(0.5f))
        assertEquals(6, manager.startingSizeFromBaseline(0.8f))
    }

    @Test
    fun `set does not expand below 95 then expands by exactly one when third crosses 95`() {
        // 2 of 3 pitches ≥95% over the window, one at 18/20 (90%) -> must NOT expand.
        val below = listOf(
            snap(NoteName.C, 20, 19),
            snap(NoteName.F_SHARP, 20, 20),
            snap(NoteName.A, 20, 18)
        )
        assertFalse("Set must not expand while one pitch is under 95%", manager.shouldExpand(below))

        // Flip the third to 19/20 (95%) -> set expands by exactly one pitch.
        val crossed = below.map {
            if (it.noteName == NoteName.A) it.copy(windowCorrectCount = 19) else it
        }
        assertTrue("Set must expand once all three are ≥95%", manager.shouldExpand(crossed))

        val active = crossed.map { it.noteName }
        val newPitch = manager.expandSet(active)
        assertFalse("Expansion pitch must be new", newPitch in active)
        assertEquals("Expansion adds exactly one pitch", 4, (active + newPitch).toSet().size)
    }
}
