package com.pitchforge.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pitchforge.app.data.PitchForgeRepository
import com.pitchforge.app.data.SettingsRepository
import com.pitchforge.app.domain.NotificationCopy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Daily maintenance job (§2.4e, §2.4f). Surfaces due retention checks (~30/90 days after a
 * pitch was mastered), generalization probes (~every 2 weeks), and the monthly AP checkup
 * as notifications — never silent background testing. Fires via WorkManager even if the
 * app hasn't been opened.
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PitchForgeRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (!settings.notificationsEnabled) return Result.success()

        val now = System.currentTimeMillis()
        val today = LocalDate.now(ZoneId.systemDefault()).toString()

        val dueRetention = repository.dueRetentionChecks(now)
        if (dueRetention.isNotEmpty() && settings.lastRetentionReminderDate != today) {
            val notes = dueRetention.map { it.pitchName }.distinct()
            val msg = NotificationCopy.retention(notes)
            Notifications.post(applicationContext, Notifications.ID_RETENTION, msg.title, msg.body)
            settingsRepository.setLastRetentionReminderDate(today)
        }

        if (repository.isGeneralizationProbeDue(now) && settings.lastGeneralizationReminderDate != today) {
            val untrained = repository.pickGeneralizationTimbre()
            if (untrained != null) {
                val msg = NotificationCopy.generalization(untrained)
                Notifications.post(
                    applicationContext,
                    Notifications.ID_GENERALIZATION,
                    msg.title,
                    msg.body
                )
                settingsRepository.setLastGeneralizationReminderDate(today)
            }
        }

        if (repository.isApCheckupDue(now) && settings.lastCheckupReminderDate != today) {
            val msg = NotificationCopy.checkup()
            Notifications.post(applicationContext, Notifications.ID_CHECKUP, msg.title, msg.body)
            settingsRepository.setLastCheckupReminderDate(today)
        }

        return Result.success()
    }
}
