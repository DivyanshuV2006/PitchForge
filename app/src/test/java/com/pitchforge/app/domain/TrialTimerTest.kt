package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * §6 #12: response time must be measured from the actual audio-onset timestamp, not from
 * the moment play() was requested. We simulate a decode/playback delay by reporting an
 * onset LATER than the play-call time and confirm the recorded RT excludes that delay.
 */
class TrialTimerTest {

    @Test
    fun `response time is measured from audio onset not from play call`() {
        var virtualNow = 0L
        val timer = TrialTimer(now = { virtualNow })

        val playCallTime = 1_000L
        val audioStartDelay = 250L        // audio actually started 250ms after play() was called
        val onset = playCallTime + audioStartDelay
        timer.markAudioOnset(onset)

        // User answers 600ms after the note actually started sounding.
        virtualNow = onset + 600
        assertEquals(600L, timer.responseTimeMs())

        // If we (incorrectly) measured from the play-call time it would have been 850ms.
        assertNotEquals(850L, timer.responseTimeMs())
    }

    @Test
    fun `response time never negative`() {
        var virtualNow = 500L
        val timer = TrialTimer(now = { virtualNow })
        timer.markAudioOnset(1_000L) // onset after 'now' (clock skew guard)
        assertEquals(0L, timer.responseTimeMs())
    }
}
