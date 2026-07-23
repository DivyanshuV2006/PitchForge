package com.pitchforge.app.ui.checkup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchforge.app.audio.NotePlayer
import com.pitchforge.app.data.PitchForgeRepository
import com.pitchforge.app.domain.DiagnosticTrialFactory
import com.pitchforge.app.domain.NoteName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

enum class CheckupPhase { INTRO, READY, ANSWERING, DONE }

data class CheckupUiState(
    val phase: CheckupPhase = CheckupPhase.INTRO,
    val index: Int = 0,
    val total: Int = DiagnosticTrialFactory.TRIAL_COUNT,
    val choices: List<NoteName> = NoteName.entries.sortedBy { it.semitone },
    val accuracy: Float = 0f,
    val avgErrorSemitones: Float = 0f,
    val baselineAccuracy: Float? = null,
    val baselineErrorSemitones: Float? = null,
    val previousAccuracy: Float? = null
)

/**
 * Monthly AP checkup — same isolated-note protocol as onboarding, but measurement-only:
 * results are stored and never rewrite mastery or the active pitch set.
 */
@HiltViewModel
class CheckupViewModel @Inject constructor(
    private val repository: PitchForgeRepository,
    private val notePlayer: NotePlayer
) : ViewModel() {

    private val trials: List<DiagnosticTrialFactory.Trial> =
        DiagnosticTrialFactory.build(Random(System.currentTimeMillis()))
    private val timbre = "piano"
    private var correct = 0
    private var totalError = 0

    private val _state = MutableStateFlow(CheckupUiState(total = trials.size))
    val state: StateFlow<CheckupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val user = repository.getUser()
            val previous = repository.latestCheckup()
            _state.value = _state.value.copy(
                baselineAccuracy = user?.baselineAccuracy,
                baselineErrorSemitones = user?.baselineErrorSemitones,
                previousAccuracy = previous?.accuracy
            )
        }
    }

    fun begin() {
        viewModelScope.launch {
            notePlayer.ensureLoaded(timbre, listOf(4, 5))
            correct = 0
            totalError = 0
            _state.value = _state.value.copy(phase = CheckupPhase.READY, index = 0)
        }
    }

    fun playCurrent() {
        val t = trials.getOrNull(_state.value.index) ?: return
        notePlayer.play(timbre, t.octave, t.note)
        _state.value = _state.value.copy(phase = CheckupPhase.ANSWERING)
    }

    fun answer(note: NoteName) {
        val i = _state.value.index
        val t = trials.getOrNull(i) ?: return
        if (note == t.note) correct++
        totalError += NoteName.intervalSemitones(t.note, note)

        val next = i + 1
        if (next < trials.size) {
            _state.value = _state.value.copy(phase = CheckupPhase.READY, index = next)
        } else {
            finish()
        }
    }

    private fun finish() {
        val accuracy = correct.toFloat() / trials.size
        val avgError = totalError.toFloat() / trials.size
        viewModelScope.launch {
            repository.saveApCheckup(
                questionCount = trials.size,
                correctCount = correct,
                accuracy = accuracy,
                avgErrorSemitones = avgError
            )
            _state.value = _state.value.copy(
                phase = CheckupPhase.DONE,
                accuracy = accuracy,
                avgErrorSemitones = avgError
            )
        }
    }
}
