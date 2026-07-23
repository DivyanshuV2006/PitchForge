package com.pitchforge.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NotificationCopyTest {
    @Test
    fun pools_returnNonBlankMessages() {
        repeat(20) {
            val r = Random(it)
            assertTrue(NotificationCopy.habit(hasPracticeHistory = true, random = r).title.isNotBlank())
            assertTrue(NotificationCopy.habit(hasPracticeHistory = false, random = r).body.isNotBlank())
            assertTrue(
                NotificationCopy.habit(hasPracticeHistory = true, missedYesterday = true, random = r)
                    .body.isNotBlank()
            )
            assertTrue(NotificationCopy.secondSession(r).body.isNotBlank())
            assertTrue(NotificationCopy.review(listOf("C", "G"), r).body.contains("C"))
            assertTrue(NotificationCopy.retention(listOf("F#"), r).body.contains("F#"))
            assertTrue(NotificationCopy.generalization("flute", r).body.contains("Flute"))
            assertTrue(NotificationCopy.checkup(r).title.isNotBlank())
        }
    }

    @Test
    fun habit_appendsStreakWhenPresent() {
        val msg = NotificationCopy.habit(streak = 5, hasPracticeHistory = true, random = Random(0))
        assertTrue(msg.body.contains("5-day streak"))
    }

    @Test
    fun habit_afterMiss_acknowledgesGapWithoutStreakClaim() {
        val msg = NotificationCopy.habit(
            streak = 5,
            hasPracticeHistory = true,
            missedYesterday = true,
            random = Random(0)
        )
        assertTrue(msg.body.contains("Consistency", ignoreCase = true) || msg.body.contains("return", ignoreCase = true) || msg.body.contains("miss", ignoreCase = true) || msg.body.contains("yesterday", ignoreCase = true) || msg.title.contains("miss", ignoreCase = true) || msg.title.contains("okay", ignoreCase = true) || msg.title.contains("comeback", ignoreCase = true) || msg.title.contains("Consistency", ignoreCase = true))
        assertTrue(!msg.body.contains("5-day streak"))
    }

    @Test
    fun random_canVaryAcrossDraws() {
        val titles = (0 until 40).map {
            NotificationCopy.habit(hasPracticeHistory = true, random = Random(it)).title
        }.toSet()
        assertTrue("expected variety, got ${titles.size}: $titles", titles.size >= 5)
    }
}
