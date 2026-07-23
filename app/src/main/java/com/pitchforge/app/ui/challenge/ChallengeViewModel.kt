package com.pitchforge.app.ui.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchforge.app.audio.NotePlayer
import com.pitchforge.app.data.SettingsRepository
import com.pitchforge.app.domain.InterTrialPolicy
import com.pitchforge.app.domain.NoteName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

enum class ChallengeType { GAUNTLET, TIMED, CHAOS }
enum class ChallengePhase { IDLE, LOADING, READY, BUFFERING, ANSWERING, DONE }

/**
 * Skill challenges (§2.6): harder, non-adaptive TEST modes. Deliberately isolated from the
 * training model — nothing here calls the repository's accuracy/mastery updates, so
 * challenge results never bias the adaptive lesson engine.
 */
data class ChallengeUiState(
    val phase: ChallengePhase = ChallengePhase.IDLE,
    val type: ChallengeType? = null,
    val index: Int = 0,
    val total: Int = 0,
    val correct: Int = 0,
    val timedOut: Int = 0,
    /** Per-question answer deadline in ms for TIMED mode; null for untimed modes. */
    val deadlineMs: Int? = null,
    /** Instrument for the current question (shown in Chaos so the mix is visible). */
    val currentTimbre: String? = null,
    val choices: List<NoteName> = NoteName.entries.sortedBy { it.semitone },
    /** True while a cluster distractor is playing between trials. */
    val clusterWashout: Boolean = false
)

@HiltViewModel
class ChallengeViewModel @Inject constructor(
    private val notePlayer: NotePlayer,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private data class Q(val note: NoteName, val octave: Int, val timbre: String)

    private var questions: List<Q> = emptyList()
    private var clusterWashoutIndices: Set<Int> = emptySet()
    private var timerJob: Job? = null
    private var autoPlayJob: Job? = null
    private val _state = MutableStateFlow(ChallengeUiState())
    val state: StateFlow<ChallengeUiState> = _state.asStateFlow()
    private val random = Random.Default

    companion object {
        private const val TIMED_DEADLINE_MS = 3000
        private val OCTAVES = listOf(3, 4, 5)
    }

    fun start(type: ChallengeType) {
        val rng = Random(System.currentTimeMillis())
        val count = if (type == ChallengeType.GAUNTLET) 20 else 12
        viewModelScope.launch {
            _state.value = ChallengeUiState(phase = ChallengePhase.LOADING, type = type)

            val active = settingsRepository.current().activeTimbres
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf("piano") }
            val primary = active.first()

            // Wait until every selected instrument is fully decoded — otherwise Chaos
            // silently falls back to sine / silence and looks like it "doesn't use" them.
            active.forEach { notePlayer.ensureLoaded(it, OCTAVES) }

            questions = buildQuestions(type, count, active, primary, rng)
            clusterWashoutIndices = InterTrialPolicy.pickClusterWashoutIndices(count, random = rng)
            val first = questions.firstOrNull()
            // Always land on READY with a Play button — including Timed (no auto-jump).
            _state.value = ChallengeUiState(
                phase = ChallengePhase.READY,
                type = type,
                total = count,
                deadlineMs = if (type == ChallengeType.TIMED) TIMED_DEADLINE_MS else null,
                currentTimbre = first?.timbre
            )
        }
    }

    private fun buildQuestions(
        type: ChallengeType,
        count: Int,
        active: List<String>,
        primary: String,
        rng: Random
    ): List<Q> {
        var lastTimbre: String? = null
        var lastOctave: Int? = null
        return (0 until count).map {
            val timbre = when {
                type != ChallengeType.CHAOS -> primary
                active.size == 1 -> active.first()
                else -> {
                    // Force instrument changes between consecutive questions so Chaos is audible.
                    val pool = active.filter { it != lastTimbre }.ifEmpty { active }
                    pool.random(rng).also { lastTimbre = it }
                }
            }
            val octave = if (OCTAVES.size > 1) {
                OCTAVES.filter { it != lastOctave }.random(rng)
            } else {
                OCTAVES.first()
            }
            lastOctave = octave
            Q(
                note = NoteName.entries.random(rng),
                octave = octave,
                timbre = timbre
            )
        }
    }

    fun playCurrent() {
        autoPlayJob?.cancel()
        val q = questions.getOrNull(_state.value.index) ?: return
        notePlayer.play(q.timbre, q.octave, q.note)
        _state.value = _state.value.copy(
            phase = ChallengePhase.ANSWERING,
            currentTimbre = q.timbre,
            clusterWashout = false
        )

        // TIMED mode: enforce the deadline. If it elapses while still answering the same
        // question, it counts as a miss and auto-advances.
        val deadline = _state.value.deadlineMs
        timerJob?.cancel()
        if (deadline != null) {
            val questionIndex = _state.value.index
            timerJob = viewModelScope.launch {
                delay(deadline.toLong())
                val s = _state.value
                if (s.phase == ChallengePhase.ANSWERING && s.index == questionIndex) {
                    advance(correctInc = 0, timedOutInc = 1)
                }
            }
        }
    }

    fun answer(note: NoteName) {
        timerJob?.cancel()
        val q = questions.getOrNull(_state.value.index) ?: return
        advance(correctInc = if (note == q.note) 1 else 0, timedOutInc = 0)
    }

    private fun advance(correctInc: Int, timedOutInc: Int) {
        val i = _state.value.index
        val nowCorrect = _state.value.correct + correctInc
        val nowTimedOut = _state.value.timedOut + timedOutInc
        val next = i + 1
        val type = _state.value.type
        if (next < questions.size) {
            val nextQ = questions[next]
            val useCluster = next in clusterWashoutIndices
            _state.value = _state.value.copy(
                phase = ChallengePhase.BUFFERING,
                index = next,
                correct = nowCorrect,
                timedOut = nowTimedOut,
                currentTimbre = nextQ.timbre,
                clusterWashout = useCluster
            )
            autoPlayJob?.cancel()
            autoPlayJob = viewModelScope.launch {
                val prevOctave = questions.getOrNull(i)?.octave ?: 4
                val isi = InterTrialPolicy.randomIsiMs(random)
                if (useCluster) {
                    notePlayer.playClusterWashout(
                        octave = prevOctave,
                        durationMs = isi.coerceAtLeast(InterTrialPolicy.CLUSTER_MS)
                    )
                } else {
                    notePlayer.playNoiseWashout(octave = prevOctave, durationMs = isi)
                }
                if (_state.value.phase != ChallengePhase.BUFFERING || _state.value.index != next) {
                    return@launch
                }
                // Timed keeps pressure: auto-play after washout. Gauntlet/Chaos wait for Play.
                if (type == ChallengeType.TIMED) {
                    playCurrent()
                } else {
                    _state.value = _state.value.copy(
                        phase = ChallengePhase.READY,
                        clusterWashout = false
                    )
                }
            }
        } else {
            _state.value = _state.value.copy(
                phase = ChallengePhase.DONE,
                index = next,
                correct = nowCorrect,
                timedOut = nowTimedOut,
                clusterWashout = false
            )
        }
    }

    fun reset() {
        timerJob?.cancel()
        autoPlayJob?.cancel()
        _state.value = ChallengeUiState()
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        autoPlayJob?.cancel()
    }
}
