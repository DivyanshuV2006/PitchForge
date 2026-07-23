package com.pitchforge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long = 1, // singleton user
    val createdAt: Long = System.currentTimeMillis(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastPracticeDate: String? = null, // ISO date
    val activePitchSetSize: Int = 3,
    val baselineAccuracy: Float = 0f,
    val baselineErrorSemitones: Float = 0f,
    val totalTrainingSeconds: Long = 0,
    /** Cumulative XP earned from completed daily missions. */
    val missionXp: Int = 0
)

@Entity(tableName = "pitch_progress")
data class PitchProgressEntity(
    @PrimaryKey val noteName: String, // "C", "C#", etc.
    val userId: Long = 1,
    val isActive: Boolean = false,
    val currentDeadlineMs: Int = 4000,
    val emaAccuracyWithinDeadline: Float = 0f,
    val emaAccuracyAll: Float = 0f,
    val attemptCount: Int = 0,
    val correctWithinDeadlineCount: Int = 0,
    val masteredAt: Long? = null,
    val lastSeenAt: Long? = null,
    // Spaced-repetition review scheduling for mastered notes (expanding intervals).
    val reviewIntervalDays: Int = 0,
    val nextReviewAt: Long? = null
)

@Entity(tableName = "note_stats")
data class NoteStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 1,
    val noteName: String,
    val octave: Int = 4,
    val timbre: String = "piano",
    val emaAccuracy: Float = 0f,
    val emaErrorSemitones: Float = 0f,
    val attemptCount: Int = 0,
    val lastSeenAt: Long? = null
)

@Entity(tableName = "lesson_sessions")
data class LessonSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 1,
    val startedAt: Long,
    val completedAt: Long? = null,
    val questionCount: Int = 0,
    val correctCount: Int = 0,
    val xpEarned: Int = 0,
    val avgResponseTimeMs: Float = 0f,
    val avgErrorSemitones: Float = 0f
)

@Entity(tableName = "question_attempts")
data class QuestionAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val taskType: String, // "NAMING" or "VERIFICATION"
    val noteName: String,
    val octave: Int = 4,
    val timbre: String = "piano",
    val userAnswer: String?,
    val correct: Boolean,
    val correctWithinDeadline: Boolean,
    val errorSemitones: Int = 0,
    val deadlineMsAtTrial: Int = 4000,
    val responseTimeMs: Long = 0,
    val audioOnsetTimestamp: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "generalization_probes")
data class GeneralizationProbeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 1,
    val date: Long,
    val untrainedTimbre: String,
    val questionCount: Int = 0,
    val correctCount: Int = 0,
    val avgErrorSemitones: Float = 0f
)

@Entity(tableName = "retention_checks")
data class RetentionCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 1,
    val pitchName: String,
    val originalMasteredAt: Long,
    val checkDueAt: Long,
    val checkCompletedAt: Long? = null,
    val accuracyAtCheck: Float? = null
)

@Entity(tableName = "daily_missions")
data class DailyMissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 1,
    val date: String, // ISO date
    val missionType: String,
    val target: Int,
    val progress: Int = 0,
    val completed: Boolean = false,
    /** XP granted when this mission was completed (0 until then). */
    val xpAwarded: Int = 0
)

/** Monthly AP checkup results — measurement only; never feeds mastery. */
@Entity(tableName = "ap_checkups")
data class ApCheckupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long = 1,
    val completedAt: Long,
    val questionCount: Int,
    val correctCount: Int,
    val accuracy: Float,
    val avgErrorSemitones: Float
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val userId: Long = 1,
    val activeTimbres: String = "piano,sine", // comma-separated
    val notificationsEnabled: Boolean = true,
    val reminderTime: String = "18:00",
    val textScale: Float = 1.0f,
    val darkMode: String = "system" // "light", "dark", "system"
)
