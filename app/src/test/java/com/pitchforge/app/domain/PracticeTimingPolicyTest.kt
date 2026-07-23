package com.pitchforge.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PracticeTimingPolicyTest {

    private val zone = ZoneId.of("America/Chicago")

    private fun epochAt(hour: Int): Long =
        LocalDateTime.of(2026, 7, 21, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `preferredHabitHour uses the modal practice hour`() {
        val starts = listOf(
            epochAt(19), epochAt(19), epochAt(19),
            epochAt(8), epochAt(20)
        )
        assertEquals(19, PracticeTimingPolicy.preferredHabitHour(starts, fallbackHour = 18, zone = zone))
    }

    @Test
    fun `preferredHabitHour falls back when history is empty`() {
        assertEquals(18, PracticeTimingPolicy.preferredHabitHour(emptyList(), fallbackHour = 18, zone = zone))
        assertEquals(7, PracticeTimingPolicy.preferredHabitHour(emptyList(), fallbackHour = 7, zone = zone))
    }

    @Test
    fun `quiet hours cover late night and early morning`() {
        assertTrue(PracticeTimingPolicy.isQuietHour(22))
        assertTrue(PracticeTimingPolicy.isQuietHour(23))
        assertTrue(PracticeTimingPolicy.isQuietHour(0))
        assertTrue(PracticeTimingPolicy.isQuietHour(7))
        assertFalse(PracticeTimingPolicy.isQuietHour(8))
        assertFalse(PracticeTimingPolicy.isQuietHour(18))
    }

    @Test
    fun `habit reminder first of day in preferred hour when not practiced`() {
        val today = "2026-07-22"
        assertTrue(
            PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = 19, preferredHour = 19,
                practicedToday = false, hasPracticeHistory = true,
                reminderDate = null, reminderCountOnDate = 0, lastReminderAtMs = null,
                today = today, nowMs = 0L
            )
        )
        assertFalse(
            PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = 18, preferredHour = 19,
                practicedToday = false, hasPracticeHistory = true,
                reminderDate = null, reminderCountOnDate = 0, lastReminderAtMs = null,
                today = today, nowMs = 0L
            )
        )
        assertFalse(
            PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = 19, preferredHour = 19,
                practicedToday = true, hasPracticeHistory = true,
                reminderDate = null, reminderCountOnDate = 0, lastReminderAtMs = null,
                today = today, nowMs = 0L
            )
        )
        assertFalse(
            PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = 23, preferredHour = 23,
                practicedToday = false, hasPracticeHistory = true,
                reminderDate = null, reminderCountOnDate = 0, lastReminderAtMs = null,
                today = today, nowMs = 0L
            )
        )
    }

    @Test
    fun `no history habit reminder only between 10am and noon for first send`() {
        val today = "2026-07-22"
        assertTrue(
            PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = 10, preferredHour = 18,
                practicedToday = false, hasPracticeHistory = false,
                reminderDate = null, reminderCountOnDate = 0, lastReminderAtMs = null,
                today = today, nowMs = 0L
            )
        )
        assertTrue(
            PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = 12, preferredHour = 18,
                practicedToday = false, hasPracticeHistory = false,
                reminderDate = null, reminderCountOnDate = 0, lastReminderAtMs = null,
                today = today, nowMs = 0L
            )
        )
        assertFalse(
            PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = 18, preferredHour = 18,
                practicedToday = false, hasPracticeHistory = false,
                reminderDate = null, reminderCountOnDate = 0, lastReminderAtMs = null,
                today = today, nowMs = 0L
            )
        )
    }

    @Test
    fun `second habit reminder three hours after first max two`() {
        val today = "2026-07-22"
        val firstAt = 1_000_000L
        assertFalse(
            "too soon for second",
            PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = 14, preferredHour = 10,
                practicedToday = false, hasPracticeHistory = true,
                reminderDate = today, reminderCountOnDate = 1, lastReminderAtMs = firstAt,
                today = today, nowMs = firstAt + 2 * 60 * 60 * 1000L
            )
        )
        assertTrue(
            "3h later allows second",
            PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = 14, preferredHour = 10,
                practicedToday = false, hasPracticeHistory = true,
                reminderDate = today, reminderCountOnDate = 1, lastReminderAtMs = firstAt,
                today = today, nowMs = firstAt + 3 * 60 * 60 * 1000L
            )
        )
        assertFalse(
            "cap at 2",
            PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = 17, preferredHour = 10,
                practicedToday = false, hasPracticeHistory = true,
                reminderDate = today, reminderCountOnDate = 2, lastReminderAtMs = firstAt,
                today = today, nowMs = firstAt + 6 * 60 * 60 * 1000L
            )
        )
    }

    @Test
    fun `review reminder requires due notes and skips quiet hours`() {
        assertTrue(
            PracticeTimingPolicy.shouldSendReviewReminder(
                currentHour = 12, dueNoteCount = 2, alreadyRemindedToday = false
            )
        )
        assertFalse(
            PracticeTimingPolicy.shouldSendReviewReminder(
                currentHour = 12, dueNoteCount = 0, alreadyRemindedToday = false
            )
        )
        assertFalse(
            PracticeTimingPolicy.shouldSendReviewReminder(
                currentHour = 23, dueNoteCount = 2, alreadyRemindedToday = false
            )
        )
    }

    @Test
    fun `parseReminderHour reads HH colon mm`() {
        assertEquals(18, PracticeTimingPolicy.parseReminderHour("18:00"))
        assertEquals(9, PracticeTimingPolicy.parseReminderHour("09:30"))
        assertEquals(18, PracticeTimingPolicy.parseReminderHour("nope"))
    }

    @Test
    fun `missedPracticeYesterday detects calendar gap`() {
        val today = java.time.LocalDate.of(2026, 7, 22)
        assertFalse(
            PracticeTimingPolicy.missedPracticeYesterday("2026-07-21", today)
        )
        assertFalse(
            PracticeTimingPolicy.missedPracticeYesterday("2026-07-22", today)
        )
        assertTrue(
            PracticeTimingPolicy.missedPracticeYesterday("2026-07-20", today)
        )
        assertFalse(
            PracticeTimingPolicy.missedPracticeYesterday(null, today)
        )
    }

    @Test
    fun `second session nudge after exactly one lesson in afternoon`() {
        assertTrue(
            PracticeTimingPolicy.shouldSendSecondSessionReminder(
                currentHour = 17, sessionsCompletedToday = 1, alreadyRemindedToday = false
            )
        )
        assertFalse(
            PracticeTimingPolicy.shouldSendSecondSessionReminder(
                currentHour = 17, sessionsCompletedToday = 0, alreadyRemindedToday = false
            )
        )
        assertFalse(
            PracticeTimingPolicy.shouldSendSecondSessionReminder(
                currentHour = 17, sessionsCompletedToday = 2, alreadyRemindedToday = false
            )
        )
        assertFalse(
            PracticeTimingPolicy.shouldSendSecondSessionReminder(
                currentHour = 12, sessionsCompletedToday = 1, alreadyRemindedToday = false
            )
        )
        assertFalse(
            PracticeTimingPolicy.shouldSendSecondSessionReminder(
                currentHour = 17, sessionsCompletedToday = 1, alreadyRemindedToday = true
            )
        )
    }
}
