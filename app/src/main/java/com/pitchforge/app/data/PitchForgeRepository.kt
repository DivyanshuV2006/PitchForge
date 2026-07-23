package com.pitchforge.app.data

import com.pitchforge.app.domain.ActivePitchInfo
import com.pitchforge.app.domain.ActivePitchSetManager
import com.pitchforge.app.domain.AnswerOutcome
import com.pitchforge.app.domain.DeadlineManager
import com.pitchforge.app.domain.GeneralizationPolicy
import com.pitchforge.app.domain.LessonQuestion
import com.pitchforge.app.domain.LevelSystem
import com.pitchforge.app.domain.MissionEngine
import com.pitchforge.app.domain.NoteLessonDelta
import com.pitchforge.app.domain.NoteName
import com.pitchforge.app.domain.NoteSelector
import com.pitchforge.app.domain.SessionFeedback
import com.pitchforge.app.domain.StreakManager
import com.pitchforge.app.domain.TaskType
import com.pitchforge.app.domain.TimbreSkill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central repository wiring Room persistence to the pure-Kotlin domain engine.
 * All mastery/deadline/streak/mission math lives in `domain`; this class only reads and
 * writes state and delegates the decisions.
 */
@Singleton
class PitchForgeRepository @Inject constructor(
    private val userDao: UserDao,
    private val pitchProgressDao: PitchProgressDao,
    private val noteStatDao: NoteStatDao,
    private val lessonSessionDao: LessonSessionDao,
    private val questionAttemptDao: QuestionAttemptDao,
    private val generalizationProbeDao: GeneralizationProbeDao,
    private val retentionCheckDao: RetentionCheckDao,
    private val dailyMissionDao: DailyMissionDao,
    private val apCheckupDao: ApCheckupDao,
    private val settingsRepository: SettingsRepository,
    private val pitchSetManager: ActivePitchSetManager,
    private val deadlineManager: DeadlineManager,
    private val noteSelector: NoteSelector,
    private val streakManager: StreakManager,
    private val missionEngine: MissionEngine
) {
    private val missionsMutex = Mutex()

    companion object {
        val AVAILABLE_OCTAVES = listOf(3, 4, 5)
        const val PRIMARY_OCTAVE = 4
        const val MASTERY_MIN_ATTEMPTS = 15
        /** Mastery is judged over naming attempts in this trailing window. */
        const val MASTERY_WINDOW_DAYS = 3L
        const val XP_PER_CORRECT = 10

        /** Expanding spaced-repetition review ladder (days) for mastered notes. */
        val REVIEW_INTERVALS_DAYS = listOf(1, 3, 7, 21, 60)
    }

    // ---- User / onboarding ----

    fun observeUser(): Flow<UserEntity?> = userDao.getUser()

    suspend fun getUser(): UserEntity? = userDao.getUserSync()

    suspend fun isOnboarded(): Boolean = userDao.getUserSync() != null

    /**
     * Called once after the onboarding diagnostic. Seeds the User row, derives the starting
     * pitch-set size from the baseline, and creates PitchProgress rows: the spread starting
     * set active, the remainder inactive.
     */
    suspend fun initializeUser(baselineAccuracy: Float, baselineErrorSemitones: Float) {
        val startSize = pitchSetManager.startingSizeFromBaseline(baselineAccuracy)
        userDao.upsertUser(
            UserEntity(
                id = 1,
                activePitchSetSize = startSize,
                baselineAccuracy = baselineAccuracy,
                baselineErrorSemitones = baselineErrorSemitones
            )
        )
        val startingPitches = pitchSetManager.selectSpreadPitches(startSize).toSet()
        NoteName.entries.forEach { note ->
            pitchProgressDao.upsertProgress(
                PitchProgressEntity(
                    noteName = note.label,
                    isActive = note in startingPitches,
                    currentDeadlineMs = 4000
                )
            )
        }
    }

    // ---- Lesson lifecycle ----

    suspend fun startSession(): Long =
        lessonSessionDao.insertSession(
            LessonSessionEntity(startedAt = System.currentTimeMillis())
        )

    suspend fun activePitchInfos(): List<ActivePitchInfo> {
        val now = System.currentTimeMillis()
        val skillsByNote = noteStatDao.getAllStatsSync()
            .groupBy { it.noteName }
            .mapValues { (_, rows) ->
                rows.associate { row ->
                    row.timbre to TimbreSkill(
                        attemptCount = row.attemptCount,
                        emaAccuracy = row.emaAccuracy
                    )
                }
            }
        return pitchProgressDao.getActiveProgress().map { entity ->
            entity.toActivePitchInfo(now, skillsByNote[entity.noteName].orEmpty())
        }
    }

    suspend fun masteredReviewPitches(): List<NoteName> =
        pitchProgressDao.getAllProgressSync()
            .filter { it.masteredAt != null && !it.isActive }
            .map { NoteName.fromLabel(it.noteName) }

    /** Notes whose mastery timestamp falls at or after [sinceMs] (e.g. this lesson). */
    suspend fun notesMasteredSince(sinceMs: Long): List<NoteName> =
        pitchProgressDao.getAllProgressSync()
            .filter { it.masteredAt != null && it.masteredAt!! >= sinceMs }
            .map { NoteName.fromLabel(it.noteName) }
            .sortedBy { it.semitone }

    /** All currently mastered chromas (active or review). */
    suspend fun masteredNotes(): List<NoteName> =
        pitchProgressDao.getAllProgressSync()
            .filter { it.masteredAt != null }
            .map { NoteName.fromLabel(it.noteName) }
            .sortedBy { it.semitone }

    /**
     * Settings-selected instruments for lessons (and preload). Per-note staging — which
     * subset actually plays on a given trial — lives in [LessonPlanner.timbrePoolFor].
     */
    suspend fun selectedTimbres(): List<String> =
        settingsRepository.current().activeTimbres
            .filter { it.isNotBlank() }
            .distinct()
            .ifEmpty { listOf("piano") }

    /** @deprecated Prefer [selectedTimbres]; kept for any older call sites. */
    suspend fun currentTimbres(): List<String> = selectedTimbres()

    suspend fun primaryTimbre(): String =
        selectedTimbres().first()

    /**
     * Records one answered/timed-out trial: writes the QuestionAttempt row and updates the
     * pitch's rolling accuracy, response deadline, mastery state, and the per-note/octave/
     * timbre NoteStat. Returns the (possibly unchanged) pitch progress after the update.
     */
    suspend fun recordAttempt(
        sessionId: Long,
        question: LessonQuestion,
        outcome: AnswerOutcome,
        userAnswerLabel: String?,
        responseTimeMs: Long,
        audioOnsetTimestamp: Long
    ) {
        questionAttemptDao.insertAttempt(
            QuestionAttemptEntity(
                sessionId = sessionId,
                taskType = question.taskType.name,
                noteName = question.note.label,
                octave = question.octave,
                timbre = question.timbre,
                userAnswer = userAnswerLabel,
                correct = outcome.correct,
                correctWithinDeadline = outcome.correctWithinDeadline,
                errorSemitones = outcome.errorSemitones,
                deadlineMsAtTrial = question.deadlineMs,
                responseTimeMs = responseTimeMs,
                audioOnsetTimestamp = audioOnsetTimestamp
            )
        )
        updatePitchProgress(question, outcome)
        updateNoteStat(question, outcome)
    }

    private suspend fun updatePitchProgress(question: LessonQuestion, outcome: AnswerOutcome) {
        val existing = pitchProgressDao.getProgress(question.note.label) ?: return
        val now = System.currentTimeMillis()

        // Overall accuracy (all task types) drives selection weighting.
        val emaAll = noteSelector.computeEma(existing.emaAccuracyAll, outcome.correct)

        // Mastery accuracy only tracks NAMING attempts within the deadline (§2.4b, test #15).
        val isNaming = question.taskType == TaskType.NAMING
        val emaMastery = if (isNaming) {
            noteSelector.computeEma(existing.emaAccuracyWithinDeadline, outcome.correctWithinDeadline)
        } else {
            existing.emaAccuracyWithinDeadline
        }
        val masteryAttempts = if (isNaming) existing.attemptCount + 1 else existing.attemptCount
        val correctCount = if (isNaming && outcome.correctWithinDeadline) {
            existing.correctWithinDeadlineCount + 1
        } else {
            existing.correctWithinDeadlineCount
        }

        val newDeadline = deadlineManager.computeDeadline(
            currentDeadline = existing.currentDeadlineMs,
            accuracyOverLast15 = emaMastery,
            attemptCount = masteryAttempts
        )

        // Mastery = ≥95% naming accuracy over the trailing window (default 3 days). The just-
        // recorded attempt is already persisted, so the window query includes it.
        val since = now - TimeUnit.DAYS.toMillis(MASTERY_WINDOW_DAYS)
        val windowAttempts = questionAttemptDao.countNamingSince(question.note.label, since)
        val windowCorrect = questionAttemptDao.countCorrectNamingSince(question.note.label, since)
        val meetsMastery = windowAttempts >= MASTERY_MIN_ATTEMPTS &&
            windowCorrect.toFloat() / windowAttempts >= ActivePitchSetManager.MASTERY_ACCURACY

        val wasMastered = existing.masteredAt != null
        // Once earned, a note stays in the collection (sticky). The expansion gate still uses
        // live window performance, and weak-note reintroduction still reviews it if it slips.
        val masteredAt = if (meetsMastery) (existing.masteredAt ?: now) else existing.masteredAt
        val justMastered = !wasMastered && meetsMastery

        // Spaced-repetition scheduling for mastered notes (expanding intervals; reset on a miss).
        var reviewIntervalDays = existing.reviewIntervalDays
        var nextReviewAt = existing.nextReviewAt
        if (masteredAt != null && isNaming) {
            reviewIntervalDays = when {
                !wasMastered -> REVIEW_INTERVALS_DAYS.first()
                outcome.correctWithinDeadline ->
                    REVIEW_INTERVALS_DAYS.firstOrNull { it > existing.reviewIntervalDays }
                        ?: REVIEW_INTERVALS_DAYS.last()
                else -> REVIEW_INTERVALS_DAYS.first()
            }
            nextReviewAt = now + TimeUnit.DAYS.toMillis(reviewIntervalDays.toLong())
        }

        pitchProgressDao.upsertProgress(
            existing.copy(
                emaAccuracyAll = emaAll,
                emaAccuracyWithinDeadline = emaMastery,
                attemptCount = masteryAttempts,
                correctWithinDeadlineCount = correctCount,
                currentDeadlineMs = newDeadline,
                masteredAt = masteredAt,
                reviewIntervalDays = reviewIntervalDays,
                nextReviewAt = nextReviewAt,
                lastSeenAt = now
            )
        )
        if (justMastered) scheduleRetentionChecks(question.note.label, now)
    }

    private suspend fun updateNoteStat(question: LessonQuestion, outcome: AnswerOutcome) {
        val existing = noteStatDao.getStat(question.note.label, question.timbre)
        // Within-deadline accuracy so instrument mastery matches the rest of the AP engine.
        val emaAcc = noteSelector.computeEma(
            existing?.emaAccuracy ?: 0.5f,
            outcome.correctWithinDeadline
        )
        val emaErr = 0.3f * outcome.errorSemitones + 0.7f * (existing?.emaErrorSemitones ?: 0f)
        noteStatDao.upsertStat(
            (existing ?: NoteStatEntity(noteName = question.note.label, octave = question.octave, timbre = question.timbre))
                .copy(
                    octave = question.octave,
                    emaAccuracy = emaAcc,
                    emaErrorSemitones = emaErr,
                    attemptCount = (existing?.attemptCount ?: 0) + 1,
                    lastSeenAt = System.currentTimeMillis()
                )
        )
    }

    private suspend fun scheduleRetentionChecks(pitchName: String, masteredAt: Long) {
        com.pitchforge.app.domain.RetentionPolicy.dueDatesFor(masteredAt).forEach { dueAt ->
            retentionCheckDao.insertCheck(
                RetentionCheckEntity(
                    pitchName = pitchName,
                    originalMasteredAt = masteredAt,
                    checkDueAt = dueAt
                )
            )
        }
    }

    /**
     * Finalizes a session: writes aggregate stats, advances the streak, and updates missions.
     */
    suspend fun completeSession(sessionId: Long, durationSeconds: Long) {
        val attempts = questionAttemptDao.getAttemptsForSession(sessionId)
        if (attempts.isEmpty()) return
        val correct = attempts.count { it.correctWithinDeadline }
        val avgRt = attempts.map { it.responseTimeMs }.average().toFloat()
        val avgErr = attempts.map { it.errorSemitones }.average().toFloat()
        val level = LevelSystem.levelForXp(totalXp()).level
        val xp = LevelSystem.scaleXp(correct * XP_PER_CORRECT, level)

        val session = lessonSessionDao.getSession(sessionId)
        if (session != null) {
            lessonSessionDao.updateSession(
                session.copy(
                    completedAt = System.currentTimeMillis(),
                    questionCount = attempts.size,
                    correctCount = correct,
                    xpEarned = xp,
                    avgResponseTimeMs = avgRt,
                    avgErrorSemitones = avgErr
                )
            )
        }
        advanceStreak(durationSeconds)
        val sessionCombo = MissionEngine.maxCombo(attempts.map { it.correctWithinDeadline })
        updateMissionsAfterLesson(correct, sessionCombo)
    }

    data class SessionSummary(
        val accuracy: Float,
        val avgError: Float,
        val avgRt: Float,
        val total: Int,
        val correctWithinDeadline: Int,
        val xp: Int
    )

    /** Total XP across completed lessons + daily-mission rewards (drives the player level). */
    suspend fun totalXp(): Int {
        val lessonXp = lessonSessionDao.totalXp()
        val missionXp = userDao.getUserSync()?.missionXp ?: 0
        return lessonXp + missionXp
    }

    /** Reads back a completed session's attempts and computes the summary shown to the user. */
    suspend fun sessionSummary(sessionId: Long): SessionSummary {
        val attempts = questionAttemptDao.getAttemptsForSession(sessionId)
        if (attempts.isEmpty()) return SessionSummary(0f, 0f, 0f, 0, 0, 0)
        val correctWithin = attempts.count { it.correctWithinDeadline }
        val session = lessonSessionDao.getSession(sessionId)
        val xp = session?.xpEarned?.takeIf { session.completedAt != null }
            ?: LevelSystem.scaleXp(
                correctWithin * XP_PER_CORRECT,
                LevelSystem.levelForXp(totalXp()).level
            )
        return SessionSummary(
            accuracy = correctWithin.toFloat() / attempts.size,
            avgError = attempts.map { it.errorSemitones }.average().toFloat(),
            avgRt = attempts.map { it.responseTimeMs }.average().toFloat(),
            total = attempts.size,
            correctWithinDeadline = correctWithin,
            xp = xp
        )
    }

    /** Per-note naming results for the lesson-complete “what changed” breakdown. */
    suspend fun sessionNoteBreakdown(sessionId: Long): List<NoteLessonDelta> {
        val attempts = questionAttemptDao.getAttemptsForSession(sessionId)
        val naming = attempts
            .filter { it.taskType == TaskType.NAMING.name }
            .map { NoteName.fromLabel(it.noteName) to it.correctWithinDeadline }
        return SessionFeedback.noteBreakdown(naming)
    }

    private suspend fun advanceStreak(durationSeconds: Long) {
        val user = userDao.getUserSync() ?: return
        val today = LocalDate.now()
        val lastDate = user.lastPracticeDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val newStreak = streakManager.computeStreak(user.currentStreak, lastDate, true)
        userDao.upsertUser(
            user.copy(
                currentStreak = newStreak,
                longestStreak = maxOf(user.longestStreak, newStreak),
                lastPracticeDate = today.toString(),
                totalTrainingSeconds = user.totalTrainingSeconds + durationSeconds
            )
        )
    }

    /** Adds one pitch to the active set if mastery criteria are met. Returns the new pitch. */
    suspend fun maybeExpandActiveSet(): NoteName? {
        val all = pitchProgressDao.getAllProgressSync()
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MASTERY_WINDOW_DAYS)
        val snapshots = all.map {
            it.toSnapshot(
                windowAttemptCount = questionAttemptDao.countNamingSince(it.noteName, since),
                windowCorrectCount = questionAttemptDao.countCorrectNamingSince(it.noteName, since)
            )
        }
        val user = userDao.getUserSync() ?: return null
        if (!pitchSetManager.shouldExpand(snapshots, user.activePitchSetSize)) return null

        val activeNotes = all.filter { it.isActive }.map { NoteName.fromLabel(it.noteName) }
        val newPitch = pitchSetManager.expandSet(activeNotes)
        val row = pitchProgressDao.getProgress(newPitch.label)
        pitchProgressDao.upsertProgress(
            (row ?: PitchProgressEntity(noteName = newPitch.label)).copy(
                isActive = true,
                currentDeadlineMs = 4000, // brand-new pitch gets the generous deadline again
                emaAccuracyWithinDeadline = 0f,
                attemptCount = 0,
                masteredAt = null
            )
        )
        userDao.upsertUser(user.copy(activePitchSetSize = user.activePitchSetSize + 1))
        return newPitch
    }

    // ---- Missions ----

    suspend fun todaysMissions(): List<DailyMissionEntity> = missionsMutex.withLock {
        val today = LocalDate.now().toString()
        val expected = MissionEngine.MissionType.entries.map { it.name }
        val expectedSet = expected.toSet()
        val existing = dailyMissionDao.getMissionsForDate(today)
        val types = existing.map { it.missionType }
        // Rebuild if wrong set, wrong count, or duplicates (e.g. concurrent refresh race).
        val needsReset = existing.size != expected.size ||
            types.toSet() != expectedSet ||
            types.size != types.distinct().size

        if (needsReset) {
            // Preserve the best progress/XP when collapsing duplicate rows.
            val bestByType = existing.groupBy { it.missionType }.mapValues { (_, rows) ->
                rows.maxWith(compareBy({ it.completed }, { it.progress }, { it.xpAwarded }))
            }
            dailyMissionDao.deleteMissionsForDate(today)
            missionEngine.generateDailyMissions().forEach { generated ->
                val prior = bestByType[generated.type.name]
                val progress = (prior?.progress ?: 0).coerceAtMost(generated.target)
                val completed = prior?.completed == true || progress >= generated.target
                dailyMissionDao.upsertMission(
                    DailyMissionEntity(
                        date = today,
                        missionType = generated.type.name,
                        target = generated.target,
                        progress = progress,
                        completed = completed,
                        xpAwarded = if (completed) {
                            prior?.xpAwarded?.takeIf { it > 0 } ?: MissionEngine.XP_REWARD
                        } else 0
                    )
                )
            }
        }
        dailyMissionDao.getMissionsForDate(today)
    }

    private suspend fun updateMissionsAfterLesson(correct: Int, sessionCombo: Int) {
        val today = LocalDate.now().toString()
        // Ensure today's fixed missions exist before updating progress.
        val missions = todaysMissions()
        var missionXpGained = 0
        val level = LevelSystem.levelForXp(totalXp()).level
        missions.forEach { m ->
            val type = runCatching { MissionEngine.MissionType.valueOf(m.missionType) }.getOrNull()
                ?: return@forEach
            val progress = when (type) {
                MissionEngine.MissionType.COMPLETE_LESSON -> (m.progress + 1).coerceAtMost(m.target)
                MissionEngine.MissionType.SCORE_EIGHT ->
                    if (correct >= MissionEngine.SCORE_TARGET) m.target else m.progress
                MissionEngine.MissionType.COMBO_STREAK ->
                    maxOf(m.progress, sessionCombo).coerceAtMost(m.target)
            }
            val nowComplete = progress >= m.target
            val freshReward = LevelSystem.scaleXp(MissionEngine.XP_REWARD, level)
            val xpAwarded = when {
                nowComplete && m.xpAwarded > 0 -> m.xpAwarded
                nowComplete && !m.completed -> freshReward
                nowComplete -> freshReward
                else -> 0
            }
            if (nowComplete && !m.completed) missionXpGained += freshReward
            dailyMissionDao.updateMission(today, m.missionType, progress, nowComplete, xpAwarded)
        }
        if (missionXpGained > 0) {
            val user = userDao.getUserSync() ?: return
            userDao.upsertUser(user.copy(missionXp = user.missionXp + missionXpGained))
        }
    }

    // ---- Dashboard streams ----

    fun observeSessions(): Flow<List<LessonSessionEntity>> = lessonSessionDao.getAllSessions()
    fun observeNoteStats(): Flow<List<NoteStatEntity>> = noteStatDao.getAllStats()
    fun observeProbes(): Flow<List<GeneralizationProbeEntity>> = generalizationProbeDao.getAllProbes()
    fun observePitchProgress(): Flow<List<PitchProgressEntity>> = pitchProgressDao.getAllProgress()
    fun observeRetentionChecks(): Flow<List<RetentionCheckEntity>> = retentionCheckDao.getAllChecks()

    // ---- Generalization / retention (measurement only — never mutates mastery) ----

    suspend fun trainedTimbres(): List<String> = noteStatDao.getTimbres()

    suspend fun latestProbeDate(): Long? = generalizationProbeDao.getLatestProbe()?.date

    suspend fun dueRetentionChecks(now: Long = System.currentTimeMillis()): List<RetentionCheckEntity> =
        retentionCheckDao.getDueChecks(now)

    suspend fun isGeneralizationProbeDue(now: Long = System.currentTimeMillis()): Boolean {
        val user = getUser() ?: return false
        if (!GeneralizationPolicy.isProbeDue(latestProbeDate(), now, user.createdAt)) return false
        return pickGeneralizationTimbre() != null
    }

    /** Timbre for the next generalization probe, or null if every sampled timbre was trained. */
    suspend fun pickGeneralizationTimbre(): String? =
        GeneralizationPolicy.pickUntrainedTimbre(trainedTimbres())

    /** Pitch classes the learner has practiced — preferred pool for generalization trials. */
    suspend fun knownPitchClasses(): List<NoteName> {
        val progress = pitchProgressDao.getAllProgressSync()
        val known = progress
            .filter { it.isActive || it.masteredAt != null || it.attemptCount > 0 }
            .map { NoteName.fromLabel(it.noteName) }
            .distinct()
        return known.ifEmpty { NoteName.entries }
    }

    /**
     * Persist a completed generalization probe. Does not touch pitch progress, XP, streaks,
     * or missions.
     */
    suspend fun saveGeneralizationProbe(
        untrainedTimbre: String,
        questionCount: Int,
        correctCount: Int,
        avgErrorSemitones: Float,
        date: Long = System.currentTimeMillis()
    ) = generalizationProbeDao.insertProbe(
        GeneralizationProbeEntity(
            date = date,
            untrainedTimbre = untrainedTimbre,
            questionCount = questionCount,
            correctCount = correctCount,
            avgErrorSemitones = avgErrorSemitones
        )
    )

    /**
     * Mark due retention rows complete for pitches tested in this probe.
     * [accuracyByPitchLabel] is per-pitch accuracy from the probe only.
     */
    suspend fun completeDueRetentionChecks(
        accuracyByPitchLabel: Map<String, Float>,
        now: Long = System.currentTimeMillis()
    ) {
        val due = retentionCheckDao.getDueChecks(now)
        due.filter { it.pitchName in accuracyByPitchLabel }.forEach { check ->
            retentionCheckDao.updateCheck(
                check.copy(
                    checkCompletedAt = now,
                    accuracyAtCheck = accuracyByPitchLabel[check.pitchName]
                )
            )
        }
    }

    /** Start timestamps of recent completed lessons — used to infer the user's habit hour. */
    suspend fun recentCompletedStartTimes(): List<Long> = lessonSessionDao.recentCompletedStartTimes()

    /** Mastered pitch labels whose spaced-repetition review is due right now. */
    suspend fun pitchesDueForReview(now: Long = System.currentTimeMillis()): List<String> =
        pitchProgressDao.getDueReviews(now).map { it.noteName }

    suspend fun practicedToday(today: String = LocalDate.now().toString()): Boolean =
        getUser()?.lastPracticeDate == today

    /** How many lessons were completed since local midnight today. */
    suspend fun completedSessionsToday(
        now: Long = System.currentTimeMillis(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): Int {
        val day = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return lessonSessionDao.countCompletedBetween(start, end)
    }

    /** Most recent completed lesson timestamp today, or null if none yet. */
    suspend fun lastCompletedSessionAtToday(
        now: Long = System.currentTimeMillis(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): Long? {
        val day = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return lessonSessionDao.latestCompletedBetween(start, end)
    }

    // ---- Monthly AP checkup (measurement only — never mutates mastery / pitch set) ----

    fun observeCheckups(): Flow<List<ApCheckupEntity>> = apCheckupDao.getAllCheckups()

    suspend fun latestCheckup(): ApCheckupEntity? = apCheckupDao.getLatestCheckup()

    /**
     * Persist a completed monthly checkup. Does not touch pitch progress, active set size,
     * or baseline fields on the user — those stay owned by onboarding / training.
     */
    suspend fun saveApCheckup(
        questionCount: Int,
        correctCount: Int,
        accuracy: Float,
        avgErrorSemitones: Float,
        completedAt: Long = System.currentTimeMillis()
    ): Long = apCheckupDao.insertCheckup(
        ApCheckupEntity(
            completedAt = completedAt,
            questionCount = questionCount,
            correctCount = correctCount,
            accuracy = accuracy,
            avgErrorSemitones = avgErrorSemitones
        )
    )

    suspend fun isApCheckupDue(now: Long = System.currentTimeMillis()): Boolean {
        val user = getUser() ?: return false
        val last = latestCheckup()?.completedAt
        return com.pitchforge.app.domain.ApCheckupPolicy.isDue(last, now, user.createdAt)
    }
}

private fun PitchProgressEntity.toActivePitchInfo(
    now: Long,
    timbreSkills: Map<String, TimbreSkill> = emptyMap()
) = ActivePitchInfo(
    note = NoteName.fromLabel(noteName),
    currentDeadlineMs = currentDeadlineMs,
    // Within-deadline EMA drives weak-note weighting and per-note octave unlock (≥95%).
    emaAccuracy = emaAccuracyWithinDeadline,
    attemptCount = attemptCount,
    mastered = masteredAt != null,
    dueForReview = masteredAt != null && (nextReviewAt == null || nextReviewAt <= now),
    timbreSkills = timbreSkills
)

private fun PitchProgressEntity.toSnapshot(
    windowAttemptCount: Int,
    windowCorrectCount: Int
) = com.pitchforge.app.domain.PitchProgressSnapshot(
    noteName = NoteName.fromLabel(noteName),
    isActive = isActive,
    currentDeadlineMs = currentDeadlineMs,
    emaAccuracyWithinDeadline = emaAccuracyWithinDeadline,
    attemptCount = attemptCount,
    masteredAt = masteredAt,
    correctWithinDeadlineCount = correctWithinDeadlineCount,
    windowAttemptCount = windowAttemptCount,
    windowCorrectCount = windowCorrectCount
)
