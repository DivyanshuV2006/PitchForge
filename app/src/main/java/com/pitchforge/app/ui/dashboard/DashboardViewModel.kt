package com.pitchforge.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitchforge.app.data.DailyMissionEntity
import com.pitchforge.app.data.PitchForgeRepository
import com.pitchforge.app.domain.ApCheckupPolicy
import com.pitchforge.app.domain.GeneralizationPolicy
import com.pitchforge.app.domain.NoteName
import com.pitchforge.app.domain.RetentionPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

data class AccuracyPoint(val dayEpochDay: Long, val accuracy: Float)

/** A note's place in the 12-note collection. */
enum class NoteCollectionState { MASTERED, LEARNING, LOCKED }

data class NoteSlot(val note: NoteName, val state: NoteCollectionState)

data class DashboardUiState(
    val loading: Boolean = true,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalLessons: Int = 0,
    val totalPracticeMinutes: Long = 0,
    val overallAccuracy: Float = 0f,
    val activePitchSetSize: Int = 3,
    val masteredCount: Int = 0,
    val collection: List<NoteSlot> = emptyList(),
    val level: Int = 1,
    val totalXp: Int = 0,
    val xpIntoLevel: Int = 0,
    val xpForNextLevel: Int = 100,
    val levelProgress: Float = 0f,
    val accuracyOverTime: List<AccuracyPoint> = emptyList(),
    val noteAccuracy: Map<NoteName, Float> = emptyMap(),
    val weakNote: NoteName? = null,
    val generalizationScore: Float? = null,
    val generalizationDue: Boolean = false,
    val retentionDueNotes: List<String> = emptyList(),
    val missions: List<DailyMissionEntity> = emptyList(),
    val baselineAccuracy: Float? = null,
    val latestCheckupAccuracy: Float? = null,
    val checkupDue: Boolean = false,
    /** Completed adaptive lessons since local midnight. */
    val lessonsCompletedToday: Int = 0,
    /** Epoch ms of the most recent completed lesson today (drives 20-min cooldown). */
    val lastLessonCompletedAtMs: Long? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: PitchForgeRepository
) : ViewModel() {

    private val missionsFlow = MutableStateFlow<List<DailyMissionEntity>>(emptyList())
    private val trainedTimbresFlow = MutableStateFlow<List<String>>(emptyList())

    private data class Quint(
        val user: com.pitchforge.app.data.UserEntity?,
        val sessions: List<com.pitchforge.app.data.LessonSessionEntity>,
        val progress: List<com.pitchforge.app.data.PitchProgressEntity>,
        val probes: List<com.pitchforge.app.data.GeneralizationProbeEntity>,
        val missions: List<DailyMissionEntity>
    )

    private data class CoreDashboard(
        val user: com.pitchforge.app.data.UserEntity?,
        val sessions: List<com.pitchforge.app.data.LessonSessionEntity>,
        val progress: List<com.pitchforge.app.data.PitchProgressEntity>,
        val probes: List<com.pitchforge.app.data.GeneralizationProbeEntity>,
        val retention: List<com.pitchforge.app.data.RetentionCheckEntity>,
        val missions: List<DailyMissionEntity>
    )

    val state: StateFlow<DashboardUiState> =
        combine(
            combine(
                combine(
                    repository.observeUser(),
                    repository.observeSessions(),
                    repository.observePitchProgress(),
                    repository.observeProbes(),
                    missionsFlow
                ) { user, sessions, progress, probes, missions ->
                    Quint(user, sessions, progress, probes, missions)
                },
                repository.observeRetentionChecks()
            ) { quint, retention ->
                CoreDashboard(
                    quint.user, quint.sessions, quint.progress, quint.probes, retention, quint.missions
                )
            },
            repository.observeCheckups(),
            trainedTimbresFlow
        ) { core, checkups, trainedTimbres ->
            val user = core.user
            val sessions = core.sessions
            val progress = core.progress
            val probes = core.probes
            val retention = core.retention
            val missions = core.missions
            val now = System.currentTimeMillis()
            val completed = sessions.filter { it.completedAt != null }
            val zone = ZoneId.systemDefault()
            val points = completed
                .filter { it.questionCount > 0 }
                .map {
                    val day = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().toEpochDay()
                    AccuracyPoint(day, it.correctCount.toFloat() / it.questionCount)
                }
                .takeLast(30)
            val overall = if (completed.isNotEmpty()) {
                completed.sumOf { it.correctCount }.toFloat() /
                    completed.sumOf { it.questionCount }.coerceAtLeast(1)
            } else 0f
            val lessonsTodayRows = completed.filter { s ->
                val completedAt = s.completedAt ?: return@filter false
                val day = Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate()
                day == java.time.LocalDate.now(zone)
            }
            val lessonsToday = lessonsTodayRows.size
            val lastLessonAt = lessonsTodayRows.maxOfOrNull { it.completedAt!! }
            val levelState = com.pitchforge.app.domain.LevelSystem.levelForXp(
                completed.sumOf { it.xpEarned } + (user?.missionXp ?: 0)
            )
            val noteAcc = progress.associate { NoteName.fromLabel(it.noteName) to it.emaAccuracyAll }
            val progressByNote = progress.associateBy { NoteName.fromLabel(it.noteName) }
            val collection = NoteName.entries.sortedBy { it.semitone }.map { n ->
                val p = progressByNote[n]
                val slotState = when {
                    p?.masteredAt != null -> NoteCollectionState.MASTERED
                    p?.isActive == true -> NoteCollectionState.LEARNING
                    else -> NoteCollectionState.LOCKED
                }
                NoteSlot(n, slotState)
            }
            val weak = progress
                .filter { it.isActive && it.attemptCount >= 5 }
                .minByOrNull { it.emaAccuracyWithinDeadline }
                ?.let { NoteName.fromLabel(it.noteName) }
            val latestCheckup = checkups.maxByOrNull { it.completedAt }
            val checkupDue = user != null && ApCheckupPolicy.isDue(
                lastCheckupAt = latestCheckup?.completedAt,
                now = now,
                onboardedAt = user.createdAt
            )
            val latestProbe = probes.maxByOrNull { it.date }
            val generalizationDue = user != null &&
                GeneralizationPolicy.isProbeDue(latestProbe?.date, now, user.createdAt) &&
                GeneralizationPolicy.pickUntrainedTimbre(trainedTimbres) != null
            val retentionDueNotes = retention
                .filter { RetentionPolicy.isDue(it.checkDueAt, it.checkCompletedAt, now) }
                .map { it.pitchName }
                .distinct()

            DashboardUiState(
                loading = false,
                currentStreak = user?.currentStreak ?: 0,
                longestStreak = user?.longestStreak ?: 0,
                totalLessons = completed.size,
                totalPracticeMinutes = (user?.totalTrainingSeconds ?: 0) / 60,
                overallAccuracy = overall,
                activePitchSetSize = user?.activePitchSetSize ?: 3,
                masteredCount = progress.count { it.masteredAt != null },
                collection = collection,
                level = levelState.level,
                totalXp = levelState.totalXp,
                xpIntoLevel = levelState.xpIntoLevel,
                xpForNextLevel = levelState.xpForNextLevel,
                levelProgress = levelState.progress,
                accuracyOverTime = points,
                noteAccuracy = noteAcc,
                weakNote = weak,
                generalizationScore = latestProbe?.let {
                    if (it.questionCount > 0) it.correctCount.toFloat() / it.questionCount else null
                },
                generalizationDue = generalizationDue,
                retentionDueNotes = retentionDueNotes,
                missions = missions,
                baselineAccuracy = user?.baselineAccuracy,
                latestCheckupAccuracy = latestCheckup?.accuracy,
                checkupDue = checkupDue,
                lessonsCompletedToday = lessonsToday,
                lastLessonCompletedAtMs = lastLessonAt
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    init {
        refreshMissions()
        refreshTrainedTimbres()
    }

    fun refreshMissions() {
        viewModelScope.launch { missionsFlow.value = repository.todaysMissions() }
    }

    fun refreshTrainedTimbres() {
        viewModelScope.launch { trainedTimbresFlow.value = repository.trainedTimbres() }
    }
}
