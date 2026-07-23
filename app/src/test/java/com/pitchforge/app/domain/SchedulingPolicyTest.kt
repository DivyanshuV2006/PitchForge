package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SchedulingPolicyTest {

    // ---- Generalization probes (§6 #16) ----

    @Test
    fun `first probe waits 14 days after onboarding`() {
        val onboarded = 1_000_000L
        assertFalse(
            GeneralizationPolicy.isProbeDue(
                lastProbeAt = null,
                now = onboarded + TimeUnit.DAYS.toMillis(3),
                onboardedAt = onboarded
            )
        )
        assertTrue(
            GeneralizationPolicy.isProbeDue(
                lastProbeAt = null,
                now = onboarded + TimeUnit.DAYS.toMillis(14),
                onboardedAt = onboarded
            )
        )
    }

    @Test
    fun `probe not due within the 2 week interval`() {
        val now = 100_000_000_000L
        val recent = now - TimeUnit.DAYS.toMillis(3)
        val onboarded = now - TimeUnit.DAYS.toMillis(60)
        assertFalse(GeneralizationPolicy.isProbeDue(recent, now, onboarded))
    }

    @Test
    fun `probe due after 2 weeks`() {
        val now = 100_000_000_000L
        val old = now - TimeUnit.DAYS.toMillis(15)
        val onboarded = now - TimeUnit.DAYS.toMillis(60)
        assertTrue(GeneralizationPolicy.isProbeDue(old, now, onboarded))
    }

    @Test
    fun `probe timbre is one the user has never trained on`() {
        val trained = setOf("piano", "sine")
        val all = listOf("piano", "sine", "violin", "flute")
        val picked = GeneralizationPolicy.pickUntrainedTimbre(trained, all)
        assertNotNull(picked)
        assertFalse(picked in trained)
        assertEquals("violin", picked)
    }

    @Test
    fun `probe timbre is null when everything has been trained`() {
        val all = listOf("piano", "sine")
        assertNull(GeneralizationPolicy.pickUntrainedTimbre(all.toSet(), all))
    }

    // ---- Retention checks (§6 #17) ----

    @Test
    fun `retention checks scheduled at 30 and 90 days after mastery`() {
        val masteredAt = 1_700_000_000_000L
        val dues = RetentionPolicy.dueDatesFor(masteredAt)
        assertEquals(2, dues.size)
        assertEquals(masteredAt + TimeUnit.DAYS.toMillis(30), dues[0])
        assertEquals(masteredAt + TimeUnit.DAYS.toMillis(90), dues[1])
    }

    @Test
    fun `backdated 30-day check is due now but future 90-day check is not`() {
        val now = 1_700_000_000_000L
        val masteredAt = now - TimeUnit.DAYS.toMillis(31) // mastered 31 days ago
        val (thirty, ninety) = RetentionPolicy.dueDatesFor(masteredAt)
        assertTrue(RetentionPolicy.isDue(thirty, checkCompletedAt = null, now = now))
        assertFalse(RetentionPolicy.isDue(ninety, checkCompletedAt = null, now = now))
    }

    @Test
    fun `completed check is never due again`() {
        val now = 1_700_000_000_000L
        val past = now - 1000
        assertFalse(RetentionPolicy.isDue(past, checkCompletedAt = now, now = now))
    }
}
