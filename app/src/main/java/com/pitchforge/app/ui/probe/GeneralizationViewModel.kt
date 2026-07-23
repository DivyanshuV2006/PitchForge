package com.pitchforge.app.ui.probe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchforge.app.audio.NotePlayer
import com.pitchforge.app.data.PitchForgeRepository
import com.pitchforge.app.domain.GeneralizationPolicy
import com.pitchforge.app.domain.NoteName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

enum class ProbePhase { LOADING, UNAVAILABLE, INTRO, READY, ANSWERING, DONE }

data class GeneralizationUiState(
    val phase: ProbePhase = ProbePhase.LOADING,
    val timbre: String? = null,
    val index: Int = 0,
    val total: Int = GeneralizationPolicy.TRIAL_COUNT,
    val choices: List<NoteName> = NoteName.entries.sortedBy { it.semitone },
    val accuracy: Float = 0f,
    val avgErrorSemitones: Float = 0f
)

/**
 * Untrained-timbre generalization probe (§2.4e) — measurement only.
 */
@HiltViewModel
class GeneralizationViewModel @Inject constructor(
    private val repository: PitchForgeRepository,
    private val notePlayer: NotePlayer
) : ViewModel() {

    private var trials: List<GeneralizationPolicy.Trial> = emptyList()
    private var correct = 0
    private var totalError = 0

    private val _state = MutableStateFlow(GeneralizationUiState())
    val state: StateFlow<GeneralizationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val timbre = repository.pickGeneralizationTimbre()
            if (timbre == null) {
                _state.value = GeneralizationUiState(phase = ProbePhase.UNAVAILABLE)
            } else {
                _state.value = GeneralizationUiState(phase = ProbePhase.INTRO, timbre = timbre)
            }
        }
    }

    fun begin() {
        val timbre = _state.value.timbre ?: return
        viewModelScope.launch {
            val known = repository.knownPitchClasses()
            trials = GeneralizationPolicy.buildTrials(known, random = Random(System.currentTimeMillis()))
            notePlayer.ensureLoaded(timbre, listOf(4, 5))
            correct = 0
            totalError = 0
            _state.value = _state.value.copy(
                phase = ProbePhase.READY,
                index = 0,
                total = trials.size,
                choices = NoteName.entries.sortedBy { it.semitone }
            )
        }
    }

    fun playCurrent() {
        val timbre = _state.value.timbre ?: return
        val t = trials.getOrNull(_state.value.index) ?: return
        notePlayer.play(timbre, t.octave, t.note)
        _state.value = _state.value.copy(phase = ProbePhase.ANSWERING)
    }

    fun answer(note: NoteName) {
        val i = _state.value.index
        val t = trials.getOrNull(i) ?: return
        if (note == t.note) correct++
        totalError += NoteName.intervalSemitones(t.note, note)
        val next = i + 1
        if (next < trials.size) {
            _state.value = _state.value.copy(phase = ProbePhase.READY, index = next)
        } else {
            finish()
        }
    }

    private fun finish() {
        val timbre = _state.value.timbre ?: return
        val accuracy = correct.toFloat() / trials.size
        val avgError = totalError.toFloat() / trials.size
        viewModelScope.launch {
            repository.saveGeneralizationProbe(
                untrainedTimbre = timbre,
                questionCount = trials.size,
                correctCount = correct,
                avgErrorSemitones = avgError
            )
            _state.value = _state.value.copy(
                phase = ProbePhase.DONE,
                accuracy = accuracy,
                avgErrorSemitones = avgError
            )
        }
    }
}
