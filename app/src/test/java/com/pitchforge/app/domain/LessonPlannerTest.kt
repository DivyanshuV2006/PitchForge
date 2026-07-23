package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class LessonPlannerTest {

    private val planner = LessonPlanner()

    private fun info(note: NoteName, deadline: Int = 4000, acc: Float = 0.5f, attempts: Int = 20, mastered: Boolean = false) =
        ActivePitchInfo(note, deadline, acc, attempts, mastered)

    @Test
    fun `builds 8 to 30 questions with no duplicate consecutive notes (test 1)`() {
        val active = listOf(info(NoteName.C), info(NoteName.F), info(NoteName.A))
        val lesson = planner.buildLesson(
            activePitches = active,
            masteredPitches = emptyList(),
            primaryTimbre = "piano",
            octaves = listOf(3, 4, 5),
            questionCount = 10,
            random = Random(42)
        )
        assertTrue("count in 8..30", lesson.size in 8..30)
        for (i in 1 until lesson.size) {
            assertNotEquals("consecutive duplicate at $i", lesson[i - 1].note, lesson[i].note)
        }
    }

    @Test
    fun `each question carries its pitch current deadline`() {
        val active = listOf(
            info(NoteName.C, deadline = 1600),
            info(NoteName.F, deadline = 2200),
            info(NoteName.A, deadline = 3000)
        )
        val lesson = planner.buildLesson(active, emptyList(), "piano", listOf(3, 4, 5), 12, Random(7))
        val byNote = active.associate { it.note to it.currentDeadlineMs }
        lesson.forEach { q -> assertEquals(byNote[q.note], q.deadlineMs) }
    }

    @Test
    fun `octaves are randomized across the provided range and never repeat consecutively`() {
        val active = listOf(info(NoteName.C), info(NoteName.F), info(NoteName.A))
        val octaves = listOf(3, 4, 5)
        val distinct = mutableSetOf<Int>()
        repeat(20) { seed ->
            val lesson = planner.buildLesson(active, emptyList(), "piano", octaves, 12, Random(seed.toLong()))
            lesson.forEach { q -> assertTrue("octave ${q.octave} out of range", q.octave in octaves) }
            distinct.addAll(lesson.map { it.octave })
            for (i in 1 until lesson.size) {
                assertNotEquals("octave repeated consecutively at $i", lesson[i - 1].octave, lesson[i].octave)
            }
        }
        assertTrue("octave should vary across lessons (relative-height crutch removed)", distinct.size >= 2)
    }

    @Test
    fun `new pitches see more verification, established pitches see mostly naming (test 15 support)`() {
        // One brand-new pitch (few attempts) and two established naming-heavy pitches.
        val active = listOf(
            info(NoteName.C, attempts = 2),          // new -> verification-heavy
            info(NoteName.F, attempts = 40),
            info(NoteName.A, attempts = 40)
        )
        var newVerification = 0
        var newTotal = 0
        var estNaming = 0
        var estTotal = 0
        repeat(40) { seed ->
            val lesson = planner.buildLesson(active, emptyList(), "piano", listOf(3, 4, 5), 12, Random(seed.toLong()))
            lesson.forEach { q ->
                if (q.note == NoteName.C) {
                    newTotal++
                    if (q.taskType == TaskType.VERIFICATION) newVerification++
                } else {
                    estTotal++
                    if (q.taskType == TaskType.NAMING) estNaming++
                }
            }
        }
        val newVerRate = newVerification.toFloat() / newTotal
        val estNamingRate = estNaming.toFloat() / estTotal
        assertTrue("new pitch should be verification-heavy ($newVerRate)", newVerRate > 0.4f)
        assertTrue("established pitches should be naming-dominant ($estNamingRate)", estNamingRate > 0.6f)
    }

    @Test
    fun `verification questions include a candidate, naming questions do not`() {
        val active = listOf(info(NoteName.C, attempts = 2), info(NoteName.F), info(NoteName.A))
        val lesson = planner.buildLesson(active, emptyList(), "piano", listOf(3, 4, 5), 12, Random(3))
        lesson.forEach { q ->
            if (q.taskType == TaskType.VERIFICATION) assertNotNull(q.candidate)
            else assertNull(q.candidate)
        }
    }

    @Test
    fun `multi-timbre lessons rotate instruments and avoid consecutive repeats`() {
        val active = listOf(info(NoteName.C), info(NoteName.F), info(NoteName.A))
        val timbres = listOf("piano", "violin", "flute")
        val used = mutableSetOf<String>()
        repeat(15) { seed ->
            val lesson = planner.buildLesson(
                activePitches = active,
                masteredPitches = emptyList(),
                timbres = timbres,
                octaves = listOf(4),
                questionCount = 12,
                random = Random(seed.toLong())
            )
            used.addAll(lesson.map { it.timbre })
            for (i in 1 until lesson.size) {
                assertNotEquals("timbre repeated consecutively at $i", lesson[i - 1].timbre, lesson[i].timbre)
            }
        }
        assertTrue("expected multiple timbres across lessons, got $used", used.size >= 2)
    }

    @Test
    fun `mastered chromas prefer max octave jumps`() {
        // After lastOctave=3, farthest in {3,4,5} is 5.
        assertEquals(
            5,
            LessonPlanner.pickOctave(listOf(3, 4, 5), lastOctave = 3, preferMaxJump = true, random = Random(0))
        )
        assertEquals(
            3,
            LessonPlanner.pickOctave(listOf(3, 4, 5), lastOctave = 5, preferMaxJump = true, random = Random(0))
        )
        // Mid last → extremes 3 or 5 both dist 1.
        val mid = LessonPlanner.pickOctave(listOf(3, 4, 5), lastOctave = 4, preferMaxJump = true, random = Random(1))
        assertTrue(mid == 3 || mid == 5)
    }
}
