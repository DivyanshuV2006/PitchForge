package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test

class TimbreCatalogTest {

    @Test
    fun `study A set has seven timbres including square`() {
        assertEquals(7, TimbreCatalog.STUDY_A.size)
        assertTrue(TimbreCatalog.STUDY_A.containsAll(
            listOf("piano", "flute", "guitar", "cello", "clarinet", "harpsichord", "square")
        ))
    }

    @Test
    fun `selectable includes study A plus extras`() {
        assertTrue(TimbreCatalog.SELECTABLE.containsAll(TimbreCatalog.STUDY_A))
        assertTrue(TimbreCatalog.SELECTABLE.containsAll(listOf("sine", "violin", "trumpet")))
    }
}
