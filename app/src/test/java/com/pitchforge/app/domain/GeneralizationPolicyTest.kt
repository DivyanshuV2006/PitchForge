package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test

class GeneralizationPolicyTest {

    private val dayMs = 24L * 60L * 60L * 1000L

    @Test
    fun `first probe waits 14 days after onboarding`() {
        val onboarded = 1_000_000L
        assertFalse(GeneralizationPolicy.isProbeDue(null, onboarded + 7 * dayMs, onboarded))
        assertTrue(GeneralizationPolicy.isProbeDue(null, onboarded + 14 * dayMs, onboarded))
    }

    @Test
    fun `subsequent probes wait 14 days after last probe`() {
        val onboarded = 1_000_000L
        val last = onboarded + 20 * dayMs
        assertFalse(GeneralizationPolicy.isProbeDue(last, last + 10 * dayMs, onboarded))
        assertTrue(GeneralizationPolicy.isProbeDue(last, last + 14 * dayMs, onboarded))
    }

    @Test
    fun `not due without onboarding`() {
        assertFalse(GeneralizationPolicy.isProbeDue(null, System.currentTimeMillis(), null))
    }

    @Test
    fun `picks first untrained sampled timbre`() {
        assertEquals(
            "flute",
            GeneralizationPolicy.pickUntrainedTimbre(listOf("piano"))
        )
        assertNull(
            GeneralizationPolicy.pickUntrainedTimbre(GeneralizationPolicy.PROBE_TIMBRES)
        )
    }

    @Test
    fun `builds fixed trial count`() {
        val trials = GeneralizationPolicy.buildTrials(listOf(NoteName.C, NoteName.G), count = 10)
        assertEquals(10, trials.size)
    }
}

class RetentionPolicyTest {

    @Test
    fun `due dates are 30 and 90 days after mastery`() {
        val mastered = 1_000_000L
        val dates = RetentionPolicy.dueDatesFor(mastered)
        assertEquals(2, dates.size)
        assertEquals(mastered + 30L * 24 * 60 * 60 * 1000, dates[0])
        assertEquals(mastered + 90L * 24 * 60 * 60 * 1000, dates[1])
    }

    @Test
    fun `builds trials for due pitches`() {
        val trials = RetentionPolicy.buildTrials(listOf("C", "F#", "A"), trialsPerPitch = 2)
        assertEquals(6, trials.size)
        assertTrue(trials.any { it.note == NoteName.C })
        assertTrue(trials.any { it.note == NoteName.F_SHARP })
    }

    @Test
    fun `empty due pitches yields empty trials`() {
        assertTrue(RetentionPolicy.buildTrials(emptyList()).isEmpty())
    }
}
