package com.pitchforge.app.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class NoiseColorTest {
    @Test
    fun forOctave_mapsLowToMidToHigh() {
        assertEquals(NoiseColor.BROWN, NoiseColor.forOctave(2))
        assertEquals(NoiseColor.BROWN, NoiseColor.forOctave(3))
        assertEquals(NoiseColor.PINK, NoiseColor.forOctave(4))
        assertEquals(NoiseColor.WHITE, NoiseColor.forOctave(5))
        assertEquals(NoiseColor.BLUE, NoiseColor.forOctave(6))
    }
}
