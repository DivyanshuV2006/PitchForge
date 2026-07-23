package com.pitchforge.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchforge.app.audio.AudioManager
import com.pitchforge.app.data.AppSettings
import com.pitchforge.app.data.SettingsRepository
import com.pitchforge.app.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val audioManager: AudioManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .onEach { audioManager.masterVolume = it.volume }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun toggleTimbre(timbre: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.current().activeTimbres.toMutableSet()
            if (enabled) current.add(timbre) else current.remove(timbre)
            if (current.isEmpty()) current.add("sine") // never leave zero timbres
            settingsRepository.setActiveTimbres(current.toList())
        }
    }

    fun setVolume(v: Float) {
        audioManager.masterVolume = v
        viewModelScope.launch { settingsRepository.setVolume(v) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(enabled)
            if (enabled) WorkScheduler.scheduleAll(appContext)
        }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch { settingsRepository.setDarkMode(mode) }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setTextScale(scale) }
    }
}
