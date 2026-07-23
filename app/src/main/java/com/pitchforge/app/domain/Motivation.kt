package com.pitchforge.app.domain

/**
 * Picks the note the learner should care about next — closest to mastery among
 * learning notes, falling back to the weak-note focus.
 */
object NextNoteClarity {
    data class Focus(
        val note: NoteName,
        val accuracy: Float?,
        val reason: String
    )

    fun pick(
        learning: List<NoteName>,
        accuracyByNote: Map<NoteName, Float>,
        weakNote: NoteName?
    ): Focus? {
        if (weakNote != null && weakNote in learning) {
            return Focus(
                note = weakNote,
                accuracy = accuracyByNote[weakNote],
                reason = "Showing up more until it sticks"
            )
        }
        val ranked = learning
            .map { it to accuracyByNote[it] }
            .sortedWith(
                compareByDescending<Pair<NoteName, Float?>> { it.second ?: -1f }
                    .thenBy { it.first.semitone }
            )
        val best = ranked.firstOrNull() ?: return null
        val acc = best.second
        val reason = when {
            acc == null -> "Start naming this one in lessons"
            acc >= 0.9f -> "One strong streak from mastery"
            acc >= 0.7f -> "Close — keep the pressure on"
            else -> "Your clearest next focus"
        }
        return Focus(note = best.first, accuracy = acc, reason = reason)
    }
}

/**
 * Per-note snapshot for a single lesson — explanation only, no scoring changes.
 */
data class NoteLessonDelta(
    val note: NoteName,
    val correct: Int,
    val total: Int,
    val tag: String
) {
    val percent: Int get() = if (total > 0) (correct * 100) / total else 0
}

object SessionFeedback {
    fun noteBreakdown(
        namingAttempts: List<Pair<NoteName, Boolean /* correctWithinDeadline */>>
    ): List<NoteLessonDelta> {
        if (namingAttempts.isEmpty()) return emptyList()
        return namingAttempts
            .groupBy { it.first }
            .map { (note, rows) ->
                val correct = rows.count { it.second }
                val total = rows.size
                val rate = correct.toFloat() / total
                val tag = when {
                    rate >= 0.8f -> "Strong"
                    rate >= 0.5f -> "Steady"
                    else -> "Slipped"
                }
                NoteLessonDelta(note, correct, total, tag)
            }
            .sortedWith(
                compareByDescending<NoteLessonDelta> { it.total }
                    .thenBy { it.note.semitone }
            )
    }
}

/**
 * Soft plateau / expectation copy for mid-training. Never changes the engine —
 * only surfaces when the learner has enough history for the message to be honest.
 */
object PlateauMessaging {
    fun message(
        totalLessons: Int,
        masteredCount: Int,
        recentSessionAccuracies: List<Float>,
        lessonsSinceLastMastery: Int?
    ): String? {
        if (totalLessons < 4 || masteredCount >= 12) return null

        if (totalLessons >= 6 && masteredCount == 0) {
            return "Mastery is slow by design — most adults improve a lot before the first note “locks.” Keep the daily dose."
        }

        if ((lessonsSinceLastMastery ?: 0) >= 5) {
            return "A flat stretch between masteries is normal in absolute-pitch training. Consistency beats intensity."
        }

        if (recentSessionAccuracies.size >= 4) {
            val early = recentSessionAccuracies.take(2).average()
            val late = recentSessionAccuracies.takeLast(2).average()
            if (kotlin.math.abs(late - early) < 0.06 && late in 0.40..0.88) {
                return "Plateaus are part of auditory learning. Progress often shows up after the flat stretch — don’t rush the cooldown."
            }
        }

        return null
    }
}
