package com.pitchforge.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = 1")
    fun getUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = 1")
    suspend fun getUserSync(): UserEntity?

    @Upsert
    suspend fun upsertUser(user: UserEntity)
}

@Dao
interface PitchProgressDao {
    @Query("SELECT * FROM pitch_progress WHERE userId = 1")
    fun getAllProgress(): Flow<List<PitchProgressEntity>>

    @Query("SELECT * FROM pitch_progress WHERE userId = 1")
    suspend fun getAllProgressSync(): List<PitchProgressEntity>

    @Query("SELECT * FROM pitch_progress WHERE noteName = :note AND userId = 1")
    suspend fun getProgress(note: String): PitchProgressEntity?

    @Upsert
    suspend fun upsertProgress(progress: PitchProgressEntity)

    @Query("SELECT * FROM pitch_progress WHERE isActive = 1 AND userId = 1")
    suspend fun getActiveProgress(): List<PitchProgressEntity>

    /** Mastered notes whose spaced-repetition review is due (or overdue). */
    @Query(
        """
        SELECT * FROM pitch_progress
        WHERE userId = 1 AND masteredAt IS NOT NULL
          AND nextReviewAt IS NOT NULL AND nextReviewAt <= :now
        """
    )
    suspend fun getDueReviews(now: Long): List<PitchProgressEntity>
}

@Dao
interface NoteStatDao {
    @Query("SELECT * FROM note_stats WHERE userId = 1")
    fun getAllStats(): Flow<List<NoteStatEntity>>

    @Query("SELECT * FROM note_stats WHERE userId = 1")
    suspend fun getAllStatsSync(): List<NoteStatEntity>

    @Query("SELECT * FROM note_stats WHERE noteName = :note AND timbre = :timbre AND userId = 1")
    suspend fun getStat(note: String, timbre: String): NoteStatEntity?

    @Upsert
    suspend fun upsertStat(stat: NoteStatEntity)

    @Query("SELECT DISTINCT timbre FROM note_stats WHERE userId = 1")
    suspend fun getTimbres(): List<String>
}

@Dao
interface LessonSessionDao {
    @Query("SELECT * FROM lesson_sessions WHERE userId = 1 ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<LessonSessionEntity>>

    @Query("SELECT * FROM lesson_sessions WHERE userId = 1 ORDER BY startedAt DESC")
    suspend fun getAllSessionsSync(): List<LessonSessionEntity>

    @Insert
    suspend fun insertSession(session: LessonSessionEntity): Long

    @Update
    suspend fun updateSession(session: LessonSessionEntity)

    @Query("SELECT * FROM lesson_sessions WHERE id = :id")
    suspend fun getSession(id: Long): LessonSessionEntity?

    /** Sum of XP across all completed lessons — the total that drives the level. */
    @Query("SELECT COALESCE(SUM(xpEarned), 0) FROM lesson_sessions WHERE userId = 1 AND completedAt IS NOT NULL")
    suspend fun totalXp(): Int

    /** Start times of completed lessons (most recent first) for habit-hour inference. */
    @Query("SELECT startedAt FROM lesson_sessions WHERE userId = 1 AND completedAt IS NOT NULL ORDER BY startedAt DESC LIMIT 30")
    suspend fun recentCompletedStartTimes(): List<Long>

    /** Completed lessons whose start falls in [dayStartMs, dayEndMs). */
    @Query(
        """
        SELECT COUNT(*) FROM lesson_sessions
        WHERE userId = 1 AND completedAt IS NOT NULL
          AND startedAt >= :dayStartMs AND startedAt < :dayEndMs
        """
    )
    suspend fun countCompletedBetween(dayStartMs: Long, dayEndMs: Long): Int

    /** Most recent completion timestamp among lessons completed in the window. */
    @Query(
        """
        SELECT MAX(completedAt) FROM lesson_sessions
        WHERE userId = 1 AND completedAt IS NOT NULL
          AND completedAt >= :dayStartMs AND completedAt < :dayEndMs
        """
    )
    suspend fun latestCompletedBetween(dayStartMs: Long, dayEndMs: Long): Long?
}

@Dao
interface QuestionAttemptDao {
    @Insert
    suspend fun insertAttempt(attempt: QuestionAttemptEntity)

    @Query("SELECT * FROM question_attempts WHERE sessionId = :sessionId ORDER BY id")
    suspend fun getAttemptsForSession(sessionId: Long): List<QuestionAttemptEntity>

    @Query("SELECT COUNT(*) FROM question_attempts WHERE sessionId = :sessionId AND correct = 1")
    suspend fun countCorrect(sessionId: Long): Int

    @Query("SELECT COUNT(*) FROM question_attempts WHERE sessionId = :sessionId")
    suspend fun countTotal(sessionId: Long): Int

    /** Naming attempts for a note since [since] (mastery window). */
    @Query("SELECT COUNT(*) FROM question_attempts WHERE noteName = :note AND taskType = 'NAMING' AND timestamp >= :since")
    suspend fun countNamingSince(note: String, since: Long): Int

    /** Of those, correct-within-deadline naming attempts since [since]. */
    @Query("SELECT COUNT(*) FROM question_attempts WHERE noteName = :note AND taskType = 'NAMING' AND correctWithinDeadline = 1 AND timestamp >= :since")
    suspend fun countCorrectNamingSince(note: String, since: Long): Int
}

@Dao
interface GeneralizationProbeDao {
    @Insert
    suspend fun insertProbe(probe: GeneralizationProbeEntity)

    @Query("SELECT * FROM generalization_probes WHERE userId = 1 ORDER BY date DESC")
    fun getAllProbes(): Flow<List<GeneralizationProbeEntity>>

    @Query("SELECT * FROM generalization_probes WHERE userId = 1 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestProbe(): GeneralizationProbeEntity?
}

@Dao
interface RetentionCheckDao {
    @Insert
    suspend fun insertCheck(check: RetentionCheckEntity)

    @Query("SELECT * FROM retention_checks WHERE userId = 1 AND checkCompletedAt IS NULL AND checkDueAt <= :now")
    suspend fun getDueChecks(now: Long): List<RetentionCheckEntity>

    @Query("SELECT * FROM retention_checks WHERE userId = 1 ORDER BY checkDueAt ASC")
    fun getAllChecks(): Flow<List<RetentionCheckEntity>>

    @Update
    suspend fun updateCheck(check: RetentionCheckEntity)
}

@Dao
interface DailyMissionDao {
    @Query("SELECT * FROM daily_missions WHERE userId = 1 AND date = :date")
    suspend fun getMissionsForDate(date: String): List<DailyMissionEntity>

    @Upsert
    suspend fun upsertMission(mission: DailyMissionEntity)

    @Query("UPDATE daily_missions SET progress = :progress, completed = :completed, xpAwarded = :xpAwarded WHERE userId = 1 AND date = :date AND missionType = :type")
    suspend fun updateMission(date: String, type: String, progress: Int, completed: Boolean, xpAwarded: Int)

    @Query("DELETE FROM daily_missions WHERE userId = 1 AND date = :date")
    suspend fun deleteMissionsForDate(date: String)
}

@Dao
interface ApCheckupDao {
    @Insert
    suspend fun insertCheckup(checkup: ApCheckupEntity): Long

    @Query("SELECT * FROM ap_checkups WHERE userId = 1 ORDER BY completedAt DESC")
    fun getAllCheckups(): Flow<List<ApCheckupEntity>>

    @Query("SELECT * FROM ap_checkups WHERE userId = 1 ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLatestCheckup(): ApCheckupEntity?
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE userId = 1")
    suspend fun getSettings(): SettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: SettingsEntity)
}
