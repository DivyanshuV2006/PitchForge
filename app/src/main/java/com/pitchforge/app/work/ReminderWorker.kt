package com.pitchforge.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pitchforge.app.data.PitchForgeRepository
import com.pitchforge.app.data.SettingsRepository
import com.pitchforge.app.domain.PracticeTimingPolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Smart practice notifications — runs about hourly.
 *
 * 1) Habit reminder: once/day in the user's observed practice hour (fallback: Settings time),
 *    only if they haven't practiced yet and it's outside quiet hours.
 * 2) Spaced-review due: once/day when mastered notes are due for review.
 * 3) Second-session dose: once/day if they already did one lesson and it's afternoon/evening.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PitchForgeRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (!settings.notificationsEnabled) return Result.success()

        val zone = ZoneId.systemDefault()
        val now = LocalTime.now(zone)
        val today = LocalDate.now(zone).toString()
        val hour = now.hour

        maybeHabitReminder(settings, today, hour, zone)
        maybeReviewReminder(settings.lastReviewReminderDate, today, hour)
        maybeSecondSessionReminder(settings.lastSecondSessionReminderDate, today, hour)

        return Result.success()
    }

    private suspend fun maybeHabitReminder(
        settings: com.pitchforge.app.data.AppSettings,
        today: String,
        hour: Int,
        zone: ZoneId
    ) {
        val fallback = PracticeTimingPolicy.parseReminderHour(settings.reminderTime)
        val history = repository.recentCompletedStartTimes()
        val hasHistory = history.isNotEmpty()
        val preferred = PracticeTimingPolicy.preferredHabitHour(
            sessionStartEpochMs = history,
            fallbackHour = fallback,
            zone = zone
        )
        val user = repository.getUser()
        val practicedToday = user?.lastPracticeDate == today
        val nowMs = System.currentTimeMillis()

        if (!PracticeTimingPolicy.shouldSendHabitReminder(
                currentHour = hour,
                preferredHour = preferred,
                practicedToday = practicedToday,
                hasPracticeHistory = hasHistory,
                reminderDate = settings.lastHabitReminderDate,
                reminderCountOnDate = settings.habitReminderCountOnDate,
                lastReminderAtMs = settings.lastHabitReminderAtMs,
                today = today,
                nowMs = nowMs
            )
        ) {
            return
        }

        val (title, text) = PracticeTimingPolicy.habitNotificationCopy(
            preferredHour = preferred,
            streak = user?.currentStreak ?: 0,
            hasPracticeHistory = hasHistory,
            missedYesterday = PracticeTimingPolicy.missedPracticeYesterday(
                lastPracticeIsoDate = user?.lastPracticeDate,
                today = LocalDate.parse(today)
            )
        )
        Notifications.post(applicationContext, Notifications.ID_HABIT, title, text)
        settingsRepository.recordHabitReminder(today, nowMs)
    }

    private suspend fun maybeReviewReminder(
        lastReminderDate: String?,
        today: String,
        hour: Int
    ) {
        val due = repository.pitchesDueForReview()
        val already = lastReminderDate == today
        if (!PracticeTimingPolicy.shouldSendReviewReminder(hour, due.size, already)) return

        val (title, text) = PracticeTimingPolicy.reviewNotificationCopy(due)
        Notifications.post(applicationContext, Notifications.ID_REVIEW, title, text)
        settingsRepository.setLastReviewReminderDate(today)
    }

    private suspend fun maybeSecondSessionReminder(
        lastReminderDate: String?,
        today: String,
        hour: Int
    ) {
        val sessions = repository.completedSessionsToday()
        val already = lastReminderDate == today
        if (!PracticeTimingPolicy.shouldSendSecondSessionReminder(hour, sessions, already)) return

        val (title, text) = PracticeTimingPolicy.secondSessionNotificationCopy()
        Notifications.post(applicationContext, Notifications.ID_SECOND_SESSION, title, text)
        settingsRepository.setLastSecondSessionReminderDate(today)
    }
}
