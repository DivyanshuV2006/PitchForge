package com.pitchforge.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/**
 * Shared trial list for the onboarding diagnostic and the monthly AP checkup:
 * 14 isolated notes across octaves 4–5, no feedback until the end.
 */
object DiagnosticTrialFactory {
    data class Trial(val note: NoteName, val octave: Int)

    const val TRIAL_COUNT = 14

    fun build(random: Random = Random.Default): List<Trial> {
        val notes = NoteName.entries.shuffled(random)
        return buildList {
            (0 until 7).forEach { add(Trial(notes[it % 12], 4)) }
            (0 until 7).forEach { add(Trial(notes[(it + 5) % 12], 5)) }
        }.shuffled(random)
    }
}

/**
 * Monthly AP checkup — measurement only; does not affect mastery or the active pitch set.
 * Offered only at the end of each calendar month (last [END_OF_MONTH_DAYS] days), and only
 * if the learner has not already completed a checkup in that month.
 */
object ApCheckupPolicy {
    /** Inclusive window: due when this many calendar days remain in the month (0 = last day). */
    const val END_OF_MONTH_DAYS = 2

    fun isDue(
        lastCheckupAt: Long?,
        now: Long,
        onboardedAt: Long?,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (onboardedAt == null) return false
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val onboarded = Instant.ofEpochMilli(onboardedAt).atZone(zone).toLocalDate()
        if (today.isBefore(onboarded)) return false

        if (completedInMonth(lastCheckupAt, today, zone)) return false

        val lastDay = today.with(TemporalAdjusters.lastDayOfMonth())
        val daysRemaining = today.until(lastDay).days
        return daysRemaining in 0..END_OF_MONTH_DAYS
    }

    fun completedInMonth(
        lastCheckupAt: Long?,
        monthOf: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (lastCheckupAt == null) return false
        val done = Instant.ofEpochMilli(lastCheckupAt).atZone(zone).toLocalDate()
        return done.year == monthOf.year && done.month == monthOf.month
    }
}
