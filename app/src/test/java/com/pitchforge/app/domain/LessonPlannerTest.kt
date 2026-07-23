package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class LessonPlannerTest {

    private val planner = LessonPlanner()

    private fun info(
        note: NoteName,
        deadline: Int = 4000,
        acc: Float = 0.5f,
        attempts: Int = 20,
        mastered: Boolean = false,
        timbreSkills: Map<String, TimbreSkill> = emptyMap()
    ) = ActivePitchInfo(note, deadline, acc, attempts, mastered, timbreSkills = timbreSkills)

    private fun skill(attempts: Int, acc: Float) = TimbreSkill(attempts, acc)

    @Test
    fun `builds 8 to 30 questions with no duplicate consecutive notes (test 1)`() {
        val active = listOf(info(NoteName.C), info(NoteName.F), info(NoteName.A))
        val lesson = planner.buildLesson(
            activePitches = active,
            masteredPitches = emptyList(),
            primaryTimbre = "piano",
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
        val lesson = planner.buildLesson(active, emptyList(), "piano", 12, Random(7))
        val byNote = active.associate { it.note to it.currentDeadlineMs }
        lesson.forEach { q -> assertEquals(byNote[q.note], q.deadlineMs) }
    }

    @Test
    fun `learning notes stay on anchor octave until that note is solid`() {
        val active = listOf(
            info(NoteName.C, acc = 0.5f, attempts = 20),
            info(NoteName.F, acc = 0.6f, attempts = 20),
            info(NoteName.A, acc = 0.4f, attempts = 20)
        )
        repeat(10) { seed ->
            val lesson = planner.buildLesson(active, emptyList(), "piano", 12, Random(seed.toLong()))
            lesson.forEach { q ->
                assertEquals("weak notes must stay on octave 4", 4, q.octave)
            }
        }
    }

    @Test
    fun `solid per-note accuracy unlocks wide octaves for that chroma only`() {
        assertEquals(
            LessonPlanner.ANCHOR_OCTAVES,
            LessonPlanner.octavePoolFor(info(NoteName.E, acc = 0.94f, attempts = 20), false)
        )
        assertEquals(
            LessonPlanner.WIDE_OCTAVES,
            LessonPlanner.octavePoolFor(info(NoteName.E, acc = 0.95f, attempts = 15), false)
        )
        assertEquals(
            LessonPlanner.ANCHOR_OCTAVES,
            LessonPlanner.octavePoolFor(info(NoteName.E, acc = 0.99f, attempts = 5), false)
        )

        val active = listOf(
            info(NoteName.C, acc = 0.5f, attempts = 20),
            info(NoteName.E, acc = 0.96f, attempts = 20),
            info(NoteName.G, acc = 0.5f, attempts = 20)
        )
        val distinctE = mutableSetOf<Int>()
        repeat(25) { seed ->
            val lesson = planner.buildLesson(active, emptyList(), "piano", 16, Random(seed.toLong()))
            lesson.forEach { q ->
                when (q.note) {
                    NoteName.E -> {
                        assertTrue(q.octave in LessonPlanner.WIDE_OCTAVES)
                        distinctE.add(q.octave)
                    }
                    else -> assertEquals(4, q.octave)
                }
            }
        }
        assertTrue("solid E should vary octaves, got $distinctE", distinctE.size >= 2)
    }

    @Test
    fun `timbre expands only after octave seasoning then one instrument at a time`() {
        val selected = listOf("piano", "flute", "guitar")

        // Solid for octaves but not yet seasoned → primary only.
        assertEquals(
            listOf("piano"),
            LessonPlanner.timbrePoolFor(
                info(NoteName.E, acc = 0.96f, attempts = 20),
                masteredChroma = false,
                selected = selected
            )
        )

        // Seasoned, but primary not instrument-mastered → still primary only.
        assertEquals(
            listOf("piano"),
            LessonPlanner.timbrePoolFor(
                info(NoteName.E, acc = 0.96f, attempts = 30, timbreSkills = mapOf("piano" to skill(10, 0.9f))),
                masteredChroma = false,
                selected = selected
            )
        )

        // Seasoned + primary mastered → unlock flute only.
        assertEquals(
            listOf("piano", "flute"),
            LessonPlanner.timbrePoolFor(
                info(
                    NoteName.E, acc = 0.96f, attempts = 30,
                    timbreSkills = mapOf("piano" to skill(15, 0.95f))
                ),
                masteredChroma = false,
                selected = selected
            )
        )

        // Flute also mastered → unlock guitar.
        assertEquals(
            selected,
            LessonPlanner.timbrePoolFor(
                info(
                    NoteName.E, acc = 0.96f, attempts = 40,
                    timbreSkills = mapOf(
                        "piano" to skill(20, 0.96f),
                        "flute" to skill(15, 0.95f)
                    )
                ),
                masteredChroma = false,
                selected = selected
            )
        )
    }

    @Test
    fun `lesson keeps weak notes on primary while seasoned solid notes may mix`() {
        val selected = listOf("piano", "flute", "guitar")
        val active = listOf(
            info(NoteName.C, acc = 0.5f, attempts = 20),
            info(
                NoteName.E, acc = 0.96f, attempts = 35,
                timbreSkills = mapOf("piano" to skill(20, 0.96f))
            ),
            info(NoteName.G, acc = 0.5f, attempts = 20)
        )
        val usedByNote = mutableMapOf<NoteName, MutableSet<String>>()
        repeat(30) { seed ->
            val lesson = planner.buildLesson(
                activePitches = active,
                masteredPitches = emptyList(),
                timbres = selected,
                questionCount = 18,
                random = Random(seed.toLong())
            )
            lesson.forEach { q ->
                usedByNote.getOrPut(q.note) { mutableSetOf() }.add(q.timbre)
            }
        }
        assertEquals(setOf("piano"), usedByNote[NoteName.C])
        assertEquals(setOf("piano"), usedByNote[NoteName.G])
        assertTrue(
            "seasoned E with mastered piano should include flute, got ${usedByNote[NoteName.E]}",
            usedByNote[NoteName.E]?.contains("flute") == true
        )
        assertTrue(
            "guitar must wait until flute is mastered, got ${usedByNote[NoteName.E]}",
            usedByNote[NoteName.E]?.contains("guitar") != true
        )
    }

    @Test
    fun `multi-timbre lessons rotate instruments and avoid consecutive repeats`() {
        // All notes fully unlocked across the selected set.
        val skills = mapOf(
            "piano" to skill(20, 0.96f),
            "violin" to skill(15, 0.95f),
            "flute" to skill(15, 0.95f)
        )
        val active = listOf(
            info(NoteName.C, acc = 0.96f, attempts = 40, timbreSkills = skills),
            info(NoteName.F, acc = 0.96f, attempts = 40, timbreSkills = skills),
            info(NoteName.A, acc = 0.96f, attempts = 40, timbreSkills = skills)
        )
        val timbres = listOf("piano", "violin", "flute")
        val used = mutableSetOf<String>()
        repeat(15) { seed ->
            val lesson = planner.buildLesson(
                activePitches = active,
                masteredPitches = emptyList(),
                timbres = timbres,
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
    fun `new pitches see more verification, established pitches see mostly naming (test 15 support)`() {
        val active = listOf(
            info(NoteName.C, attempts = 2),
            info(NoteName.F, attempts = 40),
            info(NoteName.A, attempts = 40)
        )
        var newVerification = 0
        var newTotal = 0
        var estNaming = 0
        var estTotal = 0
        repeat(40) { seed ->
            val lesson = planner.buildLesson(active, emptyList(), "piano", 12, Random(seed.toLong()))
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
        val lesson = planner.buildLesson(active, emptyList(), "piano", 12, Random(3))
        lesson.forEach { q ->
            if (q.taskType == TaskType.VERIFICATION) assertNotNull(q.candidate)
            else assertNull(q.candidate)
        }
    }

    @Test
    fun `mastered chromas prefer max octave jumps`() {
        assertEquals(
            5,
            LessonPlanner.pickOctave(listOf(3, 4, 5), lastOctave = 3, preferMaxJump = true, random = Random(0))
        )
        assertEquals(
            3,
            LessonPlanner.pickOctave(listOf(3, 4, 5), lastOctave = 5, preferMaxJump = true, random = Random(0))
        )
        val mid = LessonPlanner.pickOctave(listOf(3, 4, 5), lastOctave = 4, preferMaxJump = true, random = Random(1))
        assertTrue(mid == 3 || mid == 5)
    }
}
