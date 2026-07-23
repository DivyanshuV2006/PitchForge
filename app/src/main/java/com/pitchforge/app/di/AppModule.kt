package com.pitchforge.app.di

import android.content.Context
import com.pitchforge.app.audio.AudioManager
import com.pitchforge.app.audio.NotePlayer
import com.pitchforge.app.data.*
import com.pitchforge.app.domain.ActivePitchSetManager
import com.pitchforge.app.domain.DeadlineManager
import com.pitchforge.app.domain.LessonPlanner
import com.pitchforge.app.domain.MissionEngine
import com.pitchforge.app.domain.NoteSelector
import com.pitchforge.app.domain.StreakManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PitchForgeDatabase =
        PitchForgeDatabase.getInstance(context)

    @Provides fun provideUserDao(db: PitchForgeDatabase) = db.userDao()
    @Provides fun providePitchProgressDao(db: PitchForgeDatabase) = db.pitchProgressDao()
    @Provides fun provideNoteStatDao(db: PitchForgeDatabase) = db.noteStatDao()
    @Provides fun provideLessonSessionDao(db: PitchForgeDatabase) = db.lessonSessionDao()
    @Provides fun provideQuestionAttemptDao(db: PitchForgeDatabase) = db.questionAttemptDao()
    @Provides fun provideGeneralizationProbeDao(db: PitchForgeDatabase) = db.generalizationProbeDao()
    @Provides fun provideRetentionCheckDao(db: PitchForgeDatabase) = db.retentionCheckDao()
    @Provides fun provideDailyMissionDao(db: PitchForgeDatabase) = db.dailyMissionDao()
    @Provides fun provideApCheckupDao(db: PitchForgeDatabase) = db.apCheckupDao()
    @Provides fun provideSettingsDao(db: PitchForgeDatabase) = db.settingsDao()

    @Provides @Singleton
    fun provideActivePitchSetManager() = ActivePitchSetManager()

    @Provides @Singleton
    fun provideDeadlineManager() = DeadlineManager()

    @Provides @Singleton
    fun provideNoteSelector() = NoteSelector()

    @Provides @Singleton
    fun provideLessonPlanner(noteSelector: NoteSelector) = LessonPlanner(noteSelector)

    @Provides @Singleton
    fun provideStreakManager() = StreakManager()

    @Provides @Singleton
    fun provideMissionEngine() = MissionEngine()

    @Provides @Singleton
    fun provideNotePlayer(audioManager: AudioManager): NotePlayer = audioManager
}
