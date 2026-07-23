package com.pitchforge.app.ui.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchforge.app.audio.NotePlayer
import com.pitchforge.app.data.PitchForgeRepository
import com.pitchforge.app.domain.InterTrialPolicy
import com.pitchforge.app.domain.LessonPlanner
import com.pitchforge.app.domain.LessonQuestion
import com.pitchforge.app.domain.NoteName
import com.pitchforge.app.domain.ResponseEvaluator
import com.pitchforge.app.domain.TaskType
import com.pitchforge.app.domain.TrialTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

enum class LessonPhase { LOADING, EMPTY, DOSE_CAPPED, COOLDOWN, READY, BUFFERING, ANSWERING, FEEDBACK, SUMMARY }

enum class BufferMode { WASHOUT, COLD_START, CLUSTER }

data class FeedbackState(
    val correct: Boolean,
    val late: Boolean,
    val correctNote: NoteName
)

data class LessonSummary(
    val accuracy: Float,
    val avgErrorSemitones: Float,
    val avgResponseTimeMs: Float,
    val totalQuestions: Int,
    val correctWithinDeadline: Int,
    val xpEarned: Int,
    val newlyUnlockedNote: NoteName?,
    /** Notes that crossed the mastery threshold during this lesson. */
    val newlyMasteredNotes: List<NoteName> = emptyList(),
    /** One-line skill win for motivation (e.g. focus note %). */
    val todayWin: String? = null,
    val level: Int = 1,
    val xpIntoLevel: Int = 0,
    val xpForNextLevel: Int = 100,
    val levelProgress: Float = 0f,
    val leveledUp: Boolean = false,
    /** True when this was the learner's first completed lesson today — nudge a second dose. */
    val suggestSecondSession: Boolean = false,
    /** Per-note naming snapshot for the post-lesson “what changed” breakdown. */
    val noteBreakdown: List<com.pitchforge.app.domain.NoteLessonDelta> = emptyList()
)

data class LessonUiState(
    val phase: LessonPhase = LessonPhase.LOADING,
    val questionIndex: Int = 0,
    val totalQuestions: Int = 0,
    val current: LessonQuestion? = null,
    val answerChoices: List<NoteName> = emptyList(),
    val feedback: FeedbackState? = null,
    val summary: LessonSummary? = null,
    /** Consecutive correct-within-deadline answers, for the streak/combo reward. */
    val combo: Int = 0,
    /** Next is disabled until the correct-note feedback replay has settled. */
    val feedbackAdvanceEnabled: Boolean = false,
    val bufferMode: BufferMode = BufferMode.WASHOUT,
    /** Countdown seconds during cold-start silence; null otherwise. */
    val coldStartSecondsLeft: Int? = null,
    /** True while answering a cold-start probe trial. */
    val isColdStartTrial: Boolean = false,
    /** Remaining cooldown ms when [phase] is [LessonPhase.COOLDOWN]. */
    val cooldownRemainingMs: Long = 0L
)

@HiltViewModel
class LessonViewModel @Inject constructor(
    private val repository: PitchForgeRepository,
    private val notePlayer: NotePlayer,
    private val planner: LessonPlanner
) : ViewModel() {

    private val _state = MutableStateFlow(LessonUiState())
    val state: StateFlow<LessonUiState> = _state.asStateFlow()

    private var sessionId: Long = 0
    private var questions: List<LessonQuestion> = emptyList()
    private var answerChoices: List<NoteName> = emptyList()
    private var coldStartIndices: Set<Int> = emptySet()
    private var clusterWashoutIndices: Set<Int> = emptySet()
    private val trialTimer = TrialTimer()
    private var deadlineJob: Job? = null
    private var autoPlayJob: Job? = null
    private var feedbackJob: Job? = null
    private var answered = false
    private val startWallClock = System.currentTimeMillis()
    private val random = Random.Default

    init {
        loadLesson()
    }

    private fun loadLesson() {
        viewModelScope.launch {
            val active = repository.activePitchInfos()
            if (active.isEmpty()) {
                _state.value = LessonUiState(phase = LessonPhase.EMPTY)
                return@launch
            }
            val timbres = repository.selectedTimbres()
            // Preload every instrument that might play this lesson (wait for SoundPool decode).
            timbres.forEach { notePlayer.ensureLoaded(it, PitchForgeRepository.AVAILABLE_OCTAVES) }
            val mastered = repository.masteredReviewPitches()
            questions = planner.buildLesson(
                activePitches = active,
                masteredPitches = mastered,
                timbres = timbres,
                questionCount = 30
            )
            coldStartIndices = InterTrialPolicy.pickColdStartIndices(questions.size, random)
            clusterWashoutIndices = InterTrialPolicy.pickClusterWashoutIndices(
                questionCount = questions.size,
                avoid = coldStartIndices,
                random = random
            )
            answerChoices = active.map { it.note }.sortedBy { it.semitone }
            val lessonsToday = repository.completedSessionsToday()
            val lastCompletedAt = repository.lastCompletedSessionAtToday()
            val gate = InterTrialPolicy.sessionGate(lessonsToday, lastCompletedAt)
            if (gate != InterTrialPolicy.SessionGate.AVAILABLE) {
                _state.value = LessonUiState(
                    phase = when (gate) {
                        InterTrialPolicy.SessionGate.DOSE_COMPLETE -> LessonPhase.DOSE_CAPPED
                        else -> LessonPhase.COOLDOWN
                    },
                    cooldownRemainingMs = InterTrialPolicy.cooldownRemainingMs(lastCompletedAt)
                )
                return@launch
            }
            sessionId = repository.startSession()
            _state.value = LessonUiState(
                phase = LessonPhase.READY,
                questionIndex = 0,
                totalQuestions = questions.size,
                current = questions.first(),
                answerChoices = answerChoices,
                isColdStartTrial = 0 in coldStartIndices
            )
        }
    }

    /** Called when the user taps play (first question only) or after the inter-trial buffer. */
    fun playCurrentNote() {
        autoPlayJob?.cancel()
        val index = _state.value.questionIndex
        val q = questions.getOrNull(index) ?: return
        answered = false
        val onset = notePlayer.play(q.timbre, q.octave, q.note)
        trialTimer.markAudioOnset(onset)
        _state.value = _state.value.copy(
            phase = LessonPhase.ANSWERING,
            feedback = null,
            feedbackAdvanceEnabled = false,
            coldStartSecondsLeft = null,
            isColdStartTrial = index in coldStartIndices
        )
        deadlineJob?.cancel()
        deadlineJob = viewModelScope.launch {
            delay(q.deadlineMs.toLong())
            if (!answered) submitAnswer(namingAnswer = null, verificationYes = null)
        }
    }

    fun replayCurrentNote() {
        val q = questions.getOrNull(_state.value.questionIndex) ?: return
        if (_state.value.phase == LessonPhase.FEEDBACK) {
            feedbackJob?.cancel()
            feedbackJob = viewModelScope.launch {
                _state.value = _state.value.copy(feedbackAdvanceEnabled = false)
                notePlayer.play(q.timbre, q.octave, q.note)
                delay(InterTrialPolicy.FEEDBACK_REPLAY_SETTLE_MS.toLong())
                if (_state.value.phase == LessonPhase.FEEDBACK) {
                    _state.value = _state.value.copy(feedbackAdvanceEnabled = true)
                }
            }
        } else {
            notePlayer.play(q.timbre, q.octave, q.note)
        }
    }

    fun submitNaming(answer: NoteName) = submitAnswer(namingAnswer = answer, verificationYes = null)

    fun submitVerification(yes: Boolean) = submitAnswer(namingAnswer = null, verificationYes = yes)

    private fun submitAnswer(namingAnswer: NoteName?, verificationYes: Boolean?) {
        if (answered) return
        answered = true
        deadlineJob?.cancel()
        autoPlayJob?.cancel()
        val q = questions.getOrNull(_state.value.questionIndex) ?: return
        val rt = trialTimer.responseTimeMs()

        val outcome = if (q.taskType == TaskType.NAMING) {
            ResponseEvaluator.evaluateNaming(q.note, namingAnswer, rt, q.deadlineMs)
        } else {
            ResponseEvaluator.evaluateVerification(q.note, q.candidate ?: q.note, verificationYes, rt, q.deadlineMs)
        }

        val answerLabel = when {
            q.taskType == TaskType.NAMING -> namingAnswer?.label
            else -> verificationYes?.let { if (it) "yes" else "no" }
        }

        viewModelScope.launch {
            repository.recordAttempt(
                sessionId = sessionId,
                question = q,
                outcome = outcome,
                userAnswerLabel = answerLabel,
                responseTimeMs = rt,
                audioOnsetTimestamp = trialTimer.audioOnsetTimestamp
            )
        }

        // A genuine on-time correct answer extends the combo; anything else breaks it.
        val newCombo = if (outcome.correctWithinDeadline) _state.value.combo + 1 else 0

        _state.value = _state.value.copy(
            phase = LessonPhase.FEEDBACK,
            feedback = FeedbackState(correct = outcome.correct, late = outcome.late, correctNote = q.note),
            combo = newCombo,
            feedbackAdvanceEnabled = false
        )

        // Encoding reinforcement (correct-note replay). Inter-trial washout runs on Next,
        // so we don't double up with a second noise burst here.
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            if (outcome.correct) delay(180)
            notePlayer.play(q.timbre, q.octave, q.note)
            delay(InterTrialPolicy.FEEDBACK_REPLAY_SETTLE_MS.toLong())
            if (_state.value.phase == LessonPhase.FEEDBACK) {
                _state.value = _state.value.copy(feedbackAdvanceEnabled = true)
            }
        }
    }

    fun next() {
        if (_state.value.phase == LessonPhase.FEEDBACK && !_state.value.feedbackAdvanceEnabled) return
        feedbackJob?.cancel()
        val nextIndex = _state.value.questionIndex + 1
        if (nextIndex < questions.size) {
            val cold = nextIndex in coldStartIndices
            val cluster = !cold && nextIndex in clusterWashoutIndices
            _state.value = _state.value.copy(
                phase = LessonPhase.BUFFERING,
                questionIndex = nextIndex,
                current = questions[nextIndex],
                feedback = null,
                feedbackAdvanceEnabled = false,
                bufferMode = when {
                    cold -> BufferMode.COLD_START
                    cluster -> BufferMode.CLUSTER
                    else -> BufferMode.WASHOUT
                },
                coldStartSecondsLeft = null,
                isColdStartTrial = cold
            )
            autoPlayJob?.cancel()
            autoPlayJob = viewModelScope.launch {
                val prevOctave = questions.getOrNull(nextIndex - 1)?.octave ?: 4
                when {
                    cold -> runColdStartBuffer(prevOctave, nextIndex)
                    cluster -> {
                        val isi = InterTrialPolicy.randomIsiMs(random)
                            .coerceAtLeast(InterTrialPolicy.CLUSTER_MS)
                        notePlayer.playClusterWashout(octave = prevOctave, durationMs = isi)
                    }
                    else -> {
                        val isi = InterTrialPolicy.randomIsiMs(random)
                        notePlayer.playNoiseWashout(octave = prevOctave, durationMs = isi)
                    }
                }
                if (_state.value.phase == LessonPhase.BUFFERING &&
                    _state.value.questionIndex == nextIndex
                ) {
                    playCurrentNote()
                }
            }
        } else {
            finish()
        }
    }

    private suspend fun runColdStartBuffer(prevOctave: Int, nextIndex: Int) {
        // Clear the last tone, then a long silent window — no pitch in the ear.
        notePlayer.playNoiseWashout(prevOctave, InterTrialPolicy.COLD_CLEAR_MS)
        if (_state.value.phase != LessonPhase.BUFFERING || _state.value.questionIndex != nextIndex) return

        val silenceMs = InterTrialPolicy.randomColdSilenceMs(random)
        var left = (silenceMs + 999) / 1000
        while (left > 0) {
            if (_state.value.phase != LessonPhase.BUFFERING || _state.value.questionIndex != nextIndex) return
            _state.value = _state.value.copy(coldStartSecondsLeft = left)
            delay(1_000)
            left--
        }
        _state.value = _state.value.copy(coldStartSecondsLeft = null)
    }

    private fun finish() {
        autoPlayJob?.cancel()
        feedbackJob?.cancel()
        viewModelScope.launch {
            val durationSec = (System.currentTimeMillis() - startWallClock) / 1000
            repository.completeSession(sessionId, durationSec)
            val unlocked = repository.maybeExpandActiveSet()
            val masteredThisSession = repository.notesMasteredSince(startWallClock)
            val stats = repository.sessionSummary(sessionId)
            val noteBreakdown = repository.sessionNoteBreakdown(sessionId)

            // Level: the just-finished session's XP is already in the total, so subtract it
            // to recover the pre-lesson total and detect whether this lesson leveled the user up.
            val totalAfter = repository.totalXp()
            val levelAfter = com.pitchforge.app.domain.LevelSystem.levelForXp(totalAfter)
            val levelBefore = com.pitchforge.app.domain.LevelSystem.levelForXp(totalAfter - stats.xp)

            val todayWin = when {
                masteredThisSession.isNotEmpty() ->
                    "You mastered ${masteredThisSession.joinToString(", ") { it.label }}."
                stats.accuracy >= 0.85f ->
                    "Strong session — ${(stats.accuracy * 100).toInt()}% on time."
                stats.correctWithinDeadline > 0 ->
                    "${stats.correctWithinDeadline} clean hits locked in."
                else -> null
            }

            val summary = LessonSummary(
                accuracy = stats.accuracy,
                avgErrorSemitones = stats.avgError,
                avgResponseTimeMs = stats.avgRt,
                totalQuestions = stats.total,
                correctWithinDeadline = stats.correctWithinDeadline,
                xpEarned = stats.xp,
                newlyUnlockedNote = unlocked,
                newlyMasteredNotes = masteredThisSession,
                todayWin = todayWin,
                level = levelAfter.level,
                xpIntoLevel = levelAfter.xpIntoLevel,
                xpForNextLevel = levelAfter.xpForNextLevel,
                levelProgress = levelAfter.progress,
                leveledUp = levelAfter.level > levelBefore.level,
                suggestSecondSession = repository.completedSessionsToday() <= 1,
                noteBreakdown = noteBreakdown
            )
            _state.value = _state.value.copy(phase = LessonPhase.SUMMARY, summary = summary)
        }
    }

    /** Cancel timers and silence any ringing tone / washout (system back, leave screen). */
    fun stopAudio() {
        deadlineJob?.cancel()
        autoPlayJob?.cancel()
        feedbackJob?.cancel()
        notePlayer.stopAll()
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
