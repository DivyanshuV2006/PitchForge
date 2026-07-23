package com.pitchforge.app.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Pure timing rules for smart practice notifications.
 *
 * "Optimal" here means: remind during the user's observed practice habit window, never during
 * quiet hours, and surface spaced-repetition reviews when they are actually due — not a
 * claim about a universal best hour for the brain.
 */
object PracticeTimingPolicy {

    /** Local quiet hours: no habit pings from 22:00 through 07:59. */
    const val QUIET_START_HOUR = 22
    const val QUIET_END_HOUR = 8

    /** Fallback hour label when Settings parse fails (not used for no-history sends). */
    const val DEFAULT_HABIT_HOUR = 18

    /**
     * When the user has no completed-lesson history yet, the *first* habit reminder may fire
     * in this inclusive local-hour window (10 AM through noon).
     */
    const val NO_HISTORY_HABIT_START_HOUR = 10
    const val NO_HISTORY_HABIT_END_HOUR = 12

    /** Max habit practice nudges per calendar day. */
    const val HABIT_MAX_PER_DAY = 2

    /** Gap before a second habit nudge may fire (after the first today). */
    const val HABIT_SECOND_GAP_MS = 3 * 60 * 60 * 1000L

    fun isNoHistoryHabitHour(hour: Int): Boolean =
        hour.coerceIn(0, 23) in NO_HISTORY_HABIT_START_HOUR..NO_HISTORY_HABIT_END_HOUR

    /** How many habit reminders already fired on [today] given stored date/count. */
    fun habitRemindersSentToday(
        reminderDate: String?,
        reminderCountOnDate: Int,
        today: String
    ): Int = if (reminderDate == today) reminderCountOnDate.coerceAtLeast(0) else 0

    /**
     * Most common local hour-of-day among [sessionStartEpochMs], falling back to
     * [fallbackHour] (from Settings) when history is empty.
     */
    fun preferredHabitHour(
        sessionStartEpochMs: List<Long>,
        fallbackHour: Int = DEFAULT_HABIT_HOUR,
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        if (sessionStartEpochMs.isEmpty()) return fallbackHour.coerceIn(0, 23)
        val hours = sessionStartEpochMs.map { epoch ->
            Instant.ofEpochMilli(epoch).atZone(zone).hour
        }
        return hours.groupingBy { it }.eachCount().maxWithOrNull(
            compareBy<Map.Entry<Int, Int>>({ it.value }, { -kotlin.math.abs(it.key - fallbackHour) })
        )?.key ?: fallbackHour.coerceIn(0, 23)
    }

    fun parseReminderHour(reminderTime: String): Int {
        val hour = reminderTime.substringBefore(':').toIntOrNull() ?: DEFAULT_HABIT_HOUR
        return hour.coerceIn(0, 23)
    }

    fun isQuietHour(hour: Int): Boolean {
        val h = hour.coerceIn(0, 23)
        return h >= QUIET_START_HOUR || h < QUIET_END_HOUR
    }

    /**
     * Habit reminder (max [HABIT_MAX_PER_DAY]/day), not practiced yet, outside quiet hours.
     * - 1st today: with history → [preferredHour]; no history → 10 AM–12 PM
     * - 2nd today: at least [HABIT_SECOND_GAP_MS] after the first send
     */
    fun shouldSendHabitReminder(
        currentHour: Int,
        preferredHour: Int,
        practicedToday: Boolean,
        hasPracticeHistory: Boolean = true,
        reminderDate: String? = null,
        reminderCountOnDate: Int = 0,
        lastReminderAtMs: Long? = null,
        today: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (practicedToday) return false
        if (isQuietHour(currentHour)) return false

        val sentToday = habitRemindersSentToday(reminderDate, reminderCountOnDate, today)
        if (sentToday >= HABIT_MAX_PER_DAY) return false

        if (sentToday == 0) {
            return if (hasPracticeHistory) {
                currentHour == preferredHour.coerceIn(0, 23)
            } else {
                isNoHistoryHabitHour(currentHour)
            }
        }

        // Second nudge: wait 3 hours after the first, regardless of preferred hour.
        val lastAt = lastReminderAtMs ?: return false
        return nowMs - lastAt >= HABIT_SECOND_GAP_MS
    }

    /** Review-due ping at most once per calendar day, skipping quiet hours. */
    fun shouldSendReviewReminder(
        currentHour: Int,
        dueNoteCount: Int,
        alreadyRemindedToday: Boolean
    ): Boolean {
        if (dueNoteCount <= 0 || alreadyRemindedToday) return false
        if (isQuietHour(currentHour)) return false
        return true
    }

    fun habitNotificationCopy(
        preferredHour: Int,
        streak: Int,
        hasPracticeHistory: Boolean = true
    ): Pair<String, String> {
        // preferredHour kept for call-site compatibility; copy is now randomized.
        @Suppress("UNUSED_VARIABLE")
        val ignored = preferredHour
        val msg = NotificationCopy.habit(streak = streak, hasPracticeHistory = hasPracticeHistory)
        return msg.title to msg.body
    }

    fun reviewNotificationCopy(noteLabels: List<String>): Pair<String, String> {
        val msg = NotificationCopy.review(noteLabels)
        return msg.title to msg.body
    }

    /**
     * Optional second-session nudge: after one lesson today, remind later (afternoon/evening)
     * that a second short block helps retention — once per day, outside quiet hours.
     */
    fun shouldSendSecondSessionReminder(
        currentHour: Int,
        sessionsCompletedToday: Int,
        alreadyRemindedToday: Boolean
    ): Boolean {
        if (sessionsCompletedToday != 1 || alreadyRemindedToday) return false
        if (isQuietHour(currentHour)) return false
        // Prefer late afternoon / early evening so it isn't stacked on the habit hour.
        return currentHour in 15..20
    }

    fun secondSessionNotificationCopy(): Pair<String, String> {
        val msg = NotificationCopy.secondSession()
        return msg.title to msg.body
    }
}
