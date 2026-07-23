package com.pitchforge.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pitchforge.app.audio.InstrumentPreloader
import com.pitchforge.app.work.Notifications
import com.pitchforge.app.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PitchForgeApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var instrumentPreloader: InstrumentPreloader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        WorkScheduler.scheduleAll(this)
        instrumentPreloader.start()
    }
}
