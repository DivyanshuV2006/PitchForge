package com.pitchforge.app.ui.onboarding

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

enum class DiagnosticPhase { INTRO, READY, ANSWERING, DONE }

data class DiagnosticUiState(
    val phase: DiagnosticPhase = DiagnosticPhase.INTRO,
    val index: Int = 0,
    val total: Int = 0,
    val choices: List<NoteName> = NoteName.entries.sortedBy { it.semitone },
    val baselineAccuracy: Float = 0f,
    val baselineErrorSemitones: Float = 0f,
    /** True while Skip is seeding the user row / pitch set. */
    val skipping: Boolean = false
)

/**
 * Drives the AP diagnostic pre-test (§2.1, §6 #18): 14 isolated notes across 2 octaves,
 * NO feedback until the end. Seeds baselineAccuracy / baselineErrorSemitones and the
 * starting active-pitch-set size.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: PitchForgeRepository,
    private val notePlayer: NotePlayer
) : ViewModel() {

    private val trials: List<DiagnosticTrialFactory.Trial> =
        DiagnosticTrialFactory.build(Random(System.currentTimeMillis()))
    private val timbre = "piano"
    private var correct = 0
    private var totalError = 0

    private val _state = MutableStateFlow(DiagnosticUiState(total = trials.size))
    val state: StateFlow<DiagnosticUiState> = _state.asStateFlow()

    fun beginDiagnostic() {
        viewModelScope.launch {
            notePlayer.ensureLoaded(timbre, listOf(4, 5))
            _state.value = _state.value.copy(phase = DiagnosticPhase.READY, index = 0)
        }
    }

    fun playCurrent() {
        val t = trials.getOrNull(_state.value.index) ?: return
        notePlayer.play(timbre, t.octave, t.note)
        _state.value = _state.value.copy(phase = DiagnosticPhase.ANSWERING)
    }

    fun answer(note: NoteName) {
        val i = _state.value.index
        val t = trials.getOrNull(i) ?: return
        if (note == t.note) correct++
        totalError += NoteName.intervalSemitones(t.note, note)

        val next = i + 1
        if (next < trials.size) {
            _state.value = _state.value.copy(phase = DiagnosticPhase.READY, index = next)
        } else {
            finishDiagnostic()
        }
    }

    private fun finishDiagnostic() {
        val accuracy = correct.toFloat() / trials.size
        val avgError = totalError.toFloat() / trials.size
        viewModelScope.launch {
            repository.initializeUser(accuracy, avgError)
            _state.value = _state.value.copy(
                phase = DiagnosticPhase.DONE,
                baselineAccuracy = accuracy,
                baselineErrorSemitones = avgError
            )
        }
    }

    /**
     * Skip the intro/diagnostic but still seed a usable training profile: default 3-note
     * active set, chance-level baseline (so we don't pretend they took the test).
     */
    fun skipOnboarding(onDone: () -> Unit) {
        if (_state.value.skipping) return
        viewModelScope.launch {
            _state.value = _state.value.copy(skipping = true)
            // Chance-level accuracy → startingSizeFromBaseline → 3 pitches.
            repository.initializeUser(
                baselineAccuracy = 0f,
                baselineErrorSemitones = 6f
            )
            onDone()
        }
    }
}
