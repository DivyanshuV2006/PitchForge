package com.pitchforge.app.domain

/**
 * Outcome of a single answered (or timed-out) trial.
 *
 * [correct] = right note/judgement regardless of timing.
 * [correctWithinDeadline] = right AND answered before the response deadline. Only this
 * counts toward mastery (§2.4b, test #13). A right-but-late answer is surfaced to the
 * user as "right note, too slow", not as a plain wrong answer.
 * [errorSemitones] = distance in semitones from the correct note (§1a, primary outcome
 * measure across all three studies).
 */
data class AnswerOutcome(
    val correct: Boolean,
    val correctWithinDeadline: Boolean,
    val errorSemitones: Int,
    val late: Boolean
)

/**
 * Pure scoring for both task types. Kept free of Android/Room so it is unit-testable.
 */
object ResponseEvaluator {

    /** Maximum semitone error (tritone) used for a no-answer timeout. */
    const val MAX_ERROR = 6

    fun evaluateNaming(
        correctNote: NoteName,
        answer: NoteName?,
        responseTimeMs: Long,
        deadlineMs: Int
    ): AnswerOutcome {
        if (answer == null) {
            return AnswerOutcome(correct = false, correctWithinDeadline = false, errorSemitones = MAX_ERROR, late = true)
        }
        val correct = answer == correctNote
        val onTime = responseTimeMs <= deadlineMs
        return AnswerOutcome(
            correct = correct,
            correctWithinDeadline = correct && onTime,
            errorSemitones = NoteName.intervalSemitones(correctNote, answer),
            late = correct && !onTime
        )
    }

    fun evaluateVerification(
        correctNote: NoteName,
        candidate: NoteName,
        answerYes: Boolean?,
        responseTimeMs: Long,
        deadlineMs: Int
    ): AnswerOutcome {
        val expectedYes = candidate == correctNote
        if (answerYes == null) {
            return AnswerOutcome(
                correct = false,
                correctWithinDeadline = false,
                errorSemitones = if (expectedYes) 0 else NoteName.intervalSemitones(correctNote, candidate),
                late = true
            )
        }
        val correct = answerYes == expectedYes
        val onTime = responseTimeMs <= deadlineMs
        // Error size is only meaningful when the played note was misidentified.
        val error = if (expectedYes) 0 else NoteName.intervalSemitones(correctNote, candidate)
        return AnswerOutcome(
            correct = correct,
            correctWithinDeadline = correct && onTime,
            errorSemitones = if (correct) 0 else error,
            late = correct && !onTime
        )
    }
}

/**
 * Measures response time from the actual audio-onset timestamp (§3, test #12), NOT from
 * the moment the play() call was issued or the answer UI became interactive.
 *
 * [now] is injectable so tests can advance a virtual clock and inject an artificial
 * audio-start delay independent of the play-call time.
 */
class TrialTimer(private val now: () -> Long = { System.currentTimeMillis() }) {
    var audioOnsetTimestamp: Long = 0L
        private set

    /** Record the true audio-start time reported by the audio layer. */
    fun markAudioOnset(onsetTimestamp: Long) {
        audioOnsetTimestamp = onsetTimestamp
    }

    /** Elapsed time since audio onset. */
    fun responseTimeMs(): Long = (now() - audioOnsetTimestamp).coerceAtLeast(0)
}
