package com.pitchforge.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchforge.app.audio.AudioManager
import com.pitchforge.app.data.AppSettings
import com.pitchforge.app.data.PitchForgeRepository
import com.pitchforge.app.data.SettingsRepository
import com.pitchforge.app.domain.CosmeticTheme
import com.pitchforge.app.domain.LevelSystem
import com.pitchforge.app.work.WorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val playerLevel: Int = 1
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val repository: PitchForgeRepository,
    private val audioManager: AudioManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val playerLevel: StateFlow<Int> = combine(
        repository.observeSessions(),
        repository.observeUser()
    ) { sessions, user ->
        val lessonXp = sessions.filter { it.completedAt != null }.sumOf { it.xpEarned }
        val missionXp = user?.missionXp ?: 0
        LevelSystem.levelForXp(lessonXp + missionXp).level
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings.onEach { audioManager.masterVolume = it.volume },
        playerLevel
    ) { settings, level ->
        SettingsUiState(settings = settings, playerLevel = level)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    /** Back-compat for any callers still reading settings alone. */
    val settings: StateFlow<AppSettings> = state
        .map { it.settings }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun toggleTimbre(timbre: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsRepository.current().activeTimbres.toMutableSet()
            if (enabled) current.add(timbre) else current.remove(timbre)
            if (current.isEmpty()) current.add("sine")
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

    fun setTheme(theme: CosmeticTheme) {
        viewModelScope.launch {
            val level = LevelSystem.levelForXp(repository.totalXp()).level
            if (!theme.isUnlocked(level)) return@launch
            settingsRepository.setThemeId(theme.id)
        }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setTextScale(scale) }
    }
}
