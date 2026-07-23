package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test

class ResponseEvaluatorTest {

    @Test
    fun `correct and on time counts within deadline`() {
        val o = ResponseEvaluator.evaluateNaming(NoteName.C, NoteName.C, responseTimeMs = 1000, deadlineMs = 2200)
        assertTrue(o.correct)
        assertTrue(o.correctWithinDeadline)
        assertFalse(o.late)
        assertEquals(0, o.errorSemitones)
    }

    @Test
    fun `correct but late is right note too slow and does not count toward mastery (test 13)`() {
        val o = ResponseEvaluator.evaluateNaming(NoteName.C, NoteName.C, responseTimeMs = 3000, deadlineMs = 2200)
        assertTrue("still the correct note", o.correct)
        assertFalse("but not within deadline -> excluded from mastery", o.correctWithinDeadline)
        assertTrue("surfaced as late, not plain wrong", o.late)
    }

    @Test
    fun `wrong answer records error size in semitones`() {
        val o = ResponseEvaluator.evaluateNaming(NoteName.C, NoteName.E, responseTimeMs = 500, deadlineMs = 2200)
        assertFalse(o.correct)
        assertFalse(o.correctWithinDeadline)
        assertEquals(4, o.errorSemitones)
    }

    @Test
    fun `no answer timeout is incorrect with max error`() {
        val o = ResponseEvaluator.evaluateNaming(NoteName.C, null, responseTimeMs = 5000, deadlineMs = 2200)
        assertFalse(o.correct)
        assertEquals(ResponseEvaluator.MAX_ERROR, o.errorSemitones)
    }

    @Test
    fun `verification yes when candidate matches is correct`() {
        val o = ResponseEvaluator.evaluateVerification(NoteName.C, NoteName.C, answerYes = true, responseTimeMs = 800, deadlineMs = 2200)
        assertTrue(o.correct)
        assertTrue(o.correctWithinDeadline)
    }

    @Test
    fun `verification no when candidate differs is correct`() {
        val o = ResponseEvaluator.evaluateVerification(NoteName.C, NoteName.F_SHARP, answerYes = false, responseTimeMs = 800, deadlineMs = 2200)
        assertTrue(o.correct)
    }

    @Test
    fun `verification wrong yes records semitone error between candidate and played note`() {
        // Played C, asked "is this F#?", user said yes -> wrong, error = 6 semitones.
        val o = ResponseEvaluator.evaluateVerification(NoteName.C, NoteName.F_SHARP, answerYes = true, responseTimeMs = 800, deadlineMs = 2200)
        assertFalse(o.correct)
        assertEquals(6, o.errorSemitones)
    }
}
