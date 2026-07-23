package com.pitchforge.app.audio

import android.util.Log
import com.pitchforge.app.data.PitchForgeRepository
import com.pitchforge.app.data.SettingsRepository
import com.pitchforge.app.domain.TimbreCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Warm-loads every selectable instrument in the background at app start so lessons
 * and skill challenges rarely hit a cold “Loading instruments…” wait.
 *
 * Active (settings) timbres are loaded first; the rest of [TimbreCatalog.SELECTABLE]
 * follows. Safe to call [start] more than once.
 */
@Singleton
class InstrumentPreloader @Inject constructor(
    private val notePlayer: NotePlayer,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val octaves = PitchForgeRepository.AVAILABLE_OCTAVES
            val active = settingsRepository.current().activeTimbres
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val rest = (TimbreCatalog.SELECTABLE + notePlayer.availableTimbres())
                .distinct()
                .filterNot { it in active }
            val order = (active + rest).distinct()
            Log.i(TAG, "Background preload starting (${order.size} timbres)")
            for (timbre in order) {
                runCatching { notePlayer.ensureLoaded(timbre, octaves) }
                    .onFailure { e -> Log.w(TAG, "Preload failed for '$timbre'", e) }
            }
            Log.i(TAG, "Background preload finished")
        }
    }

    companion object {
        private const val TAG = "InstrumentPreloader"
    }
}
