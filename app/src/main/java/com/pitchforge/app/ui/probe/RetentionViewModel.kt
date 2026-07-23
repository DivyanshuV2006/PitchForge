package com.pitchforge.app.ui.probe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchforge.app.audio.NotePlayer
import com.pitchforge.app.data.PitchForgeRepository
import com.pitchforge.app.domain.NoteName
import com.pitchforge.app.domain.RetentionPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import javax.inject.Inject

data class RetentionUiState(
    val phase: ProbePhase = ProbePhase.LOADING,
    val pitchLabels: List<String> = emptyList(),
    val index: Int = 0,
    val total: Int = 0,
    val choices: List<NoteName> = NoteName.entries.sortedBy { it.semitone },
    val accuracy: Float = 0f,
    val avgErrorSemitones: Float = 0f,
    val perPitchAccuracy: Map<String, Float> = emptyMap()
)

/**
 * Retention probe on mastered pitches due at ~30/90 days (§2.4f) — measurement only.
 */
@HiltViewModel
class RetentionViewModel @Inject constructor(
    private val repository: PitchForgeRepository,
    private val notePlayer: NotePlayer
) : ViewModel() {

    private var trials: List<RetentionPolicy.Trial> = emptyList()
    private val correctByPitch = mutableMapOf<String, Int>()
    private val attemptsByPitch = mutableMapOf<String, Int>()
    private var correct = 0
    private var totalError = 0
    private val timbre = "piano"

    private val _state = MutableStateFlow(RetentionUiState())
    val state: StateFlow<RetentionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val due = repository.dueRetentionChecks()
            val labels = due.map { it.pitchName }.distinct()
            if (labels.isEmpty()) {
                _state.value = RetentionUiState(phase = ProbePhase.UNAVAILABLE)
            } else {
                _state.value = RetentionUiState(
                    phase = ProbePhase.INTRO,
                    pitchLabels = labels
                )
            }
        }
    }

    fun begin() {
        val labels = _state.value.pitchLabels
        if (labels.isEmpty()) return
        viewModelScope.launch {
            trials = RetentionPolicy.buildTrials(labels, random = Random(System.currentTimeMillis()))
            notePlayer.ensureLoaded(timbre, listOf(4, 5))
            correct = 0
            totalError = 0
            correctByPitch.clear()
            attemptsByPitch.clear()
            _state.value = _state.value.copy(
                phase = ProbePhase.READY,
                index = 0,
                total = trials.size,
                choices = NoteName.entries.sortedBy { it.semitone }
            )
        }
    }

    fun playCurrent() {
        val t = trials.getOrNull(_state.value.index) ?: return
        notePlayer.play(timbre, t.octave, t.note)
        _state.value = _state.value.copy(phase = ProbePhase.ANSWERING)
    }

    fun answer(note: NoteName) {
        val i = _state.value.index
        val t = trials.getOrNull(i) ?: return
        val label = t.note.label
        attemptsByPitch[label] = (attemptsByPitch[label] ?: 0) + 1
        if (note == t.note) {
            correct++
            correctByPitch[label] = (correctByPitch[label] ?: 0) + 1
        }
        totalError += NoteName.intervalSemitones(t.note, note)
        val next = i + 1
        if (next < trials.size) {
            _state.value = _state.value.copy(phase = ProbePhase.READY, index = next)
        } else {
            finish()
        }
    }

    private fun finish() {
        val accuracy = if (trials.isEmpty()) 0f else correct.toFloat() / trials.size
        val avgError = if (trials.isEmpty()) 0f else totalError.toFloat() / trials.size
        val perPitch = attemptsByPitch.mapValues { (label, attempts) ->
            (correctByPitch[label] ?: 0).toFloat() / attempts.coerceAtLeast(1)
        }
        viewModelScope.launch {
            repository.completeDueRetentionChecks(perPitch)
            _state.value = _state.value.copy(
                phase = ProbePhase.DONE,
                accuracy = accuracy,
                avgErrorSemitones = avgError,
                perPitchAccuracy = perPitch
            )
        }
    }
}
