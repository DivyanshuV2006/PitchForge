package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test

class DeadlineManagerTest {

    private val deadlineManager = DeadlineManager()

    @Test
    fun `new pitches get 4000ms deadline`() {
        val deadline = deadlineManager.computeDeadline(4000, 0.5f, 5)
        assertEquals(4000, deadline)
    }

    @Test
    fun `deadline shrinks to 3000ms at 70 percent accuracy`() {
        val deadline = deadlineManager.computeDeadline(4000, 0.75f, 20)
        assertEquals(3000, deadline)
    }

    @Test
    fun `deadline shrinks to 2200ms at 80 percent accuracy`() {
        val deadline = deadlineManager.computeDeadline(3000, 0.85f, 20)
        assertEquals(2200, deadline)
    }

    @Test
    fun `deadline shrinks to 1600ms at 90 percent accuracy`() {
        val deadline = deadlineManager.computeDeadline(2200, 0.92f, 20)
        assertEquals(1600, deadline)
    }

    @Test
    fun `deadline never goes below floor`() {
        val deadline = deadlineManager.computeDeadline(1600, 0.99f, 30)
        assertTrue("Deadline $deadline should be >= ${deadlineManager.floorMs}",
            deadline >= deadlineManager.floorMs)
    }

    @Test
    fun `deadline loosens when accuracy drops`() {
        val deadline = deadlineManager.computeDeadline(1600, 0.65f, 20)
        assertTrue("Deadline $deadline should be > 1600 when accuracy drops",
            deadline > 1600)
    }

    @Test
    fun `deadline stays same when accuracy maintains current tier`() {
        val deadline = deadlineManager.computeDeadline(3000, 0.72f, 20)
        assertEquals(3000, deadline)
    }

    @Test
    fun `boundary just under 70 percent stays at 4000`() {
        val deadline = deadlineManager.computeDeadline(4000, 0.69f, 20)
        assertEquals(4000, deadline)
    }

    @Test
    fun `boundary just over 70 percent drops to 3000`() {
        val deadline = deadlineManager.computeDeadline(4000, 0.71f, 20)
        assertEquals(3000, deadline)
    }
}
