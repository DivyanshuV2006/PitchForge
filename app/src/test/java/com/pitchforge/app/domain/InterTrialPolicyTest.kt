package com.pitchforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InterTrialPolicyTest {
    @Test
    fun randomIsi_staysInRange() {
        repeat(40) {
            val ms = InterTrialPolicy.randomIsiMs(Random(it))
            assertTrue(ms in InterTrialPolicy.ISI_MIN_MS..InterTrialPolicy.ISI_MAX_MS)
        }
    }

    @Test
    fun coldStarts_spacedAndBounded() {
        val indices = InterTrialPolicy.pickColdStartIndices(30, Random(42))
        assertTrue(indices.size in 2..4)
        assertTrue(indices.all { it in 5 until 30 })
        val sorted = indices.sorted()
        for (i in 1 until sorted.size) {
            assertTrue(sorted[i] - sorted[i - 1] >= 5)
        }
    }

    @Test
    fun clusterWashouts_everySixToEight_avoidCold() {
        val cold = InterTrialPolicy.pickColdStartIndices(30, Random(1))
        val clusters = InterTrialPolicy.pickClusterWashoutIndices(30, avoid = cold, random = Random(2))
        assertTrue(clusters.isNotEmpty())
        assertTrue(clusters.none { it in cold })
        assertTrue(clusters.all { it in 6 until 30 })
    }

    @Test
    fun sessionGate_cooldownAndDose() {
        val now = 1_000_000L
        assertEquals(
            InterTrialPolicy.SessionGate.AVAILABLE,
            InterTrialPolicy.sessionGate(0, null, now)
        )
        assertEquals(
            InterTrialPolicy.SessionGate.COOLDOWN,
            InterTrialPolicy.sessionGate(1, now - 5 * 60_000L, now)
        )
        assertEquals(
            InterTrialPolicy.SessionGate.AVAILABLE,
            InterTrialPolicy.sessionGate(1, now - 21 * 60_000L, now)
        )
        assertEquals(
            InterTrialPolicy.SessionGate.DOSE_COMPLETE,
            InterTrialPolicy.sessionGate(4, now - 5 * 60_000L, now)
        )
    }

    @Test
    fun formatCooldown_mmss() {
        assertEquals("20:00", InterTrialPolicy.formatCooldown(20 * 60_000L))
        assertEquals("1:05", InterTrialPolicy.formatCooldown(65_000L))
        assertEquals("0:00", InterTrialPolicy.formatCooldown(0))
    }
}
