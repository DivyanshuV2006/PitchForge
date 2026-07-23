package com.pitchforge.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val REMINDER = "pitchforge_smart_reminder"
    private const val REMINDER_LEGACY = "pitchforge_daily_reminder"
    private const val MAINTENANCE = "pitchforge_maintenance"

    /**
     * Habit + spaced-review checks run about hourly (WorkManager may flex).
     * Retention / generalization stay on a daily cadence.
     *
     * Uses [ExistingPeriodicWorkPolicy.UPDATE] so upgrades replace prior schedules.
     */
    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        // Drop the old once-daily unique work if it still exists from earlier builds.
        wm.cancelUniqueWork(REMINDER_LEGACY)
        wm.enqueueUniquePeriodicWork(
            REMINDER,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.HOURS)
                .build()
        )
        wm.enqueueUniquePeriodicWork(
            MAINTENANCE,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build()
        )
    }
}
