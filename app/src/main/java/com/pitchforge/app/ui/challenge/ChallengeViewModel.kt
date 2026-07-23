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

enum class ChallengeType { GAUNTLET, TIMED, CHAOS, PROOF }
enum class ChallengePhase { IDLE, LOADING, READY, BUFFERING, ANSWERING, DONE, UNAVAILABLE }

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
    val clusterWashout: Boolean = false,
    /** True while a cold-start silence window is running between trials. */
    val coldStart: Boolean = false,
    /** Seconds remaining in the cold-start silence (UI countdown). */
    val coldStartSecondsLeft: Int? = null,
    /** Mastered notes used for PROOF challenges (for copy + gating). */
    val proofNotes: List<NoteName> = emptyList(),
    val unavailableMessage: String? = null
)

@HiltViewModel
class ChallengeViewModel @Inject constructor(
    private val notePlayer: NotePlayer,
    private val settingsRepository: SettingsRepository,
    private val repository: com.pitchforge.app.data.PitchForgeRepository
) : ViewModel() {

    private data class Q(val note: NoteName, val octave: Int, val timbre: String)

    private var questions: List<Q> = emptyList()
    private var coldStartIndices: Set<Int> = emptySet()
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
        val count = when (type) {
            ChallengeType.GAUNTLET -> 20
            ChallengeType.PROOF -> 12
            else -> 12
        }
        viewModelScope.launch {
            _state.value = ChallengeUiState(phase = ChallengePhase.LOADING, type = type)

            val mastered = repository.masteredNotes()
            if (type == ChallengeType.PROOF && mastered.isEmpty()) {
                _state.value = ChallengeUiState(
                    phase = ChallengePhase.UNAVAILABLE,
                    type = type,
                    unavailableMessage = "Master at least one note in lessons, then come prove you still own it."
                )
                return@launch
            }

            val active = settingsRepository.current().activeTimbres
                .filter { it.isNotBlank() }
                .distinct()
                .ifEmpty { listOf("piano") }
            val primary = active.first()

            active.forEach { notePlayer.ensureLoaded(it, OCTAVES) }

            questions = buildQuestions(type, count, active, primary, mastered, rng)
            coldStartIndices = InterTrialPolicy.pickColdStartIndices(count, random = rng)
            clusterWashoutIndices = InterTrialPolicy.pickClusterWashoutIndices(
                questionCount = count,
                avoid = coldStartIndices,
                random = rng
            )
            val first = questions.firstOrNull()
            _state.value = ChallengeUiState(
                phase = ChallengePhase.READY,
                type = type,
                total = count,
                deadlineMs = if (type == ChallengeType.TIMED) TIMED_DEADLINE_MS else null,
                currentTimbre = first?.timbre,
                proofNotes = if (type == ChallengeType.PROOF) mastered else emptyList()
            )
        }
    }

    private fun buildQuestions(
        type: ChallengeType,
        count: Int,
        active: List<String>,
        primary: String,
        mastered: List<NoteName>,
        rng: Random
    ): List<Q> {
        var lastTimbre: String? = null
        var lastOctave: Int? = null
        val notePool = if (type == ChallengeType.PROOF) mastered else NoteName.entries
        return (0 until count).map {
            val timbre = when {
                type != ChallengeType.CHAOS -> primary
                active.size == 1 -> active.first()
                else -> {
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
                note = notePool.random(rng),
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
            clusterWashout = false,
            coldStart = false,
            coldStartSecondsLeft = null
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
            val useCold = next in coldStartIndices
            val useCluster = !useCold && next in clusterWashoutIndices
            _state.value = _state.value.copy(
                phase = ChallengePhase.BUFFERING,
                index = next,
                correct = nowCorrect,
                timedOut = nowTimedOut,
                currentTimbre = nextQ.timbre,
                clusterWashout = useCluster,
                coldStart = useCold,
                coldStartSecondsLeft = null
            )
            autoPlayJob?.cancel()
            autoPlayJob = viewModelScope.launch {
                val prevOctave = questions.getOrNull(i)?.octave ?: 4
                when {
                    useCold -> runColdStartBuffer(prevOctave, next)
                    useCluster -> {
                        val isi = InterTrialPolicy.randomIsiMs(random)
                        notePlayer.playClusterWashout(
                            octave = prevOctave,
                            durationMs = isi.coerceAtLeast(InterTrialPolicy.CLUSTER_MS)
                        )
                    }
                    else -> {
                        val isi = InterTrialPolicy.randomIsiMs(random)
                        notePlayer.playNoiseWashout(octave = prevOctave, durationMs = isi)
                    }
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
                        clusterWashout = false,
                        coldStart = false,
                        coldStartSecondsLeft = null
                    )
                }
            }
        } else {
            _state.value = _state.value.copy(
                phase = ChallengePhase.DONE,
                index = next,
                correct = nowCorrect,
                timedOut = nowTimedOut,
                clusterWashout = false,
                coldStart = false,
                coldStartSecondsLeft = null
            )
        }
    }

    private suspend fun runColdStartBuffer(prevOctave: Int, nextIndex: Int) {
        notePlayer.playNoiseWashout(prevOctave, InterTrialPolicy.COLD_CLEAR_MS)
        if (_state.value.phase != ChallengePhase.BUFFERING || _state.value.index != nextIndex) return

        val silenceMs = InterTrialPolicy.randomColdSilenceMs(random)
        var left = (silenceMs + 999) / 1000
        while (left > 0) {
            if (_state.value.phase != ChallengePhase.BUFFERING || _state.value.index != nextIndex) return
            _state.value = _state.value.copy(coldStartSecondsLeft = left)
            delay(1_000)
            left--
        }
        _state.value = _state.value.copy(coldStartSecondsLeft = null)
    }

    fun reset() {
        stopAudio()
        _state.value = ChallengeUiState()
    }

    /** Cancel timers and silence any ringing tone / washout (Quit, tab switch, leave screen). */
    fun stopAudio() {
        timerJob?.cancel()
        autoPlayJob?.cancel()
        notePlayer.stopAll()
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
    }
}
