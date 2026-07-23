package com.pitchforge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchforge.app.data.PitchForgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Decides the start destination: onboarding for new users, dashboard for returning ones. */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val repository: PitchForgeRepository
) : ViewModel() {
    private val _onboarded = MutableStateFlow<Boolean?>(null)
    val onboarded: StateFlow<Boolean?> = _onboarded.asStateFlow()

    init {
        refreshOnboarded()
    }

    fun refreshOnboarded() {
        viewModelScope.launch { _onboarded.value = repository.isOnboarded() }
    }
}
