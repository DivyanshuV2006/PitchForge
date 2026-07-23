package com.pitchforge.app.domain

import kotlin.random.Random

/**
 * A single generated question in a lesson.
 *
 * [candidate] is only set for VERIFICATION tasks (the note name shown to the user
 * for a yes/no judgement). For NAMING tasks it is null.
 */
data class LessonQuestion(
    val note: NoteName,
    val octave: Int,
    val timbre: String,
    val taskType: TaskType,
    val candidate: NoteName?,
    val deadlineMs: Int
)

/** Per-instrument skill for one chroma (from note_stats). */
data class TimbreSkill(
    val attemptCount: Int,
    val emaAccuracy: Float
)

/** Per-pitch state the planner needs, decoupled from Room entities. */
data class ActivePitchInfo(
    val note: NoteName,
    val currentDeadlineMs: Int,
    val emaAccuracy: Float,
    val attemptCount: Int,
    val mastered: Boolean,
    /** A mastered pitch whose spaced-repetition review is due. */
    val dueForReview: Boolean = false,
    /** On-time EMA / attempts keyed by timbre for this chroma. */
    val timbreSkills: Map<String, TimbreSkill> = emptyMap()
)

/**
 * Builds a dynamic lesson (§2.2) from the user's active pitch set and weak-note profile.
 *
 * - Uses [NoteSelector] for weighted, interval-avoiding note selection (Study A / C).
 * - Mixes NAMING and VERIFICATION (Study B).
 * - Per-note octave staging first: anchor octave 4 until the chroma is solid, then 3–5.
 * - Per-note timbre staging after that: stay on the primary instrument through octave
 *   seasoning, then unlock Settings instruments one at a time — each new instrument only
 *   after the previous one is mastered for that chroma.
 */
class LessonPlanner(
    private val noteSelector: NoteSelector = NoteSelector()
) {
    fun buildLesson(
        activePitches: List<ActivePitchInfo>,
        masteredPitches: List<NoteName>,
        timbres: List<String>,
        questionCount: Int,
        random: Random = Random.Default
    ): List<LessonQuestion> {
        if (activePitches.isEmpty()) return emptyList()

        val accuracyMap = activePitches.associate { it.note to it.emaAccuracy }
        val infoByNote = activePitches.associateBy { it.note }

        val learning = activePitches.filter { !it.mastered }
        val activeNotes = (if (learning.isNotEmpty()) learning else activePitches).map { it.note }
        val dueReview = activePitches.filter { it.mastered && it.dueForReview }.map { it.note }
        val reviewPool = (dueReview + masteredPitches).distinct().filter { it !in activeNotes }

        val count = questionCount.coerceIn(8, 30)
        val selectedTimbres = timbres.filter { it.isNotBlank() }.ifEmpty { listOf("piano") }

        val questions = mutableListOf<LessonQuestion>()
        var lastNote: NoteName? = null
        var lastOctave: Int? = null
        var lastTimbre: String? = null

        repeat(count) {
            val note = noteSelector.selectNext(
                activePitches = activeNotes,
                recentAccuracy = accuracyMap,
                lastNote = lastNote,
                masteredPitches = reviewPool
            )
            val info = infoByNote[note]
            val isNew = info?.let { it.attemptCount < 10 } ?: false

            val verificationChance = if (isNew) 0.6f else 0.2f
            val taskType = if (random.nextFloat() < verificationChance) {
                TaskType.VERIFICATION
            } else {
                TaskType.NAMING
            }

            val candidate = if (taskType == TaskType.VERIFICATION) {
                if (random.nextFloat() < 0.5f) note
                else activeNotes.filter { it != note }.randomOrNull(random) ?: note
            } else null

            val masteredChroma = info?.mastered == true || note in reviewPool
            val octavePool = octavePoolFor(info, masteredChroma)
            val octave = pickOctave(
                pool = octavePool,
                lastOctave = lastOctave,
                preferMaxJump = masteredChroma,
                random = random
            )
            lastOctave = octave

            val timbrePool = timbrePoolFor(
                info = info,
                masteredChroma = masteredChroma,
                selected = selectedTimbres
            )
            val timbre = pickTimbre(timbrePool, lastTimbre, random)
            lastTimbre = timbre

            questions.add(
                LessonQuestion(
                    note = note,
                    octave = octave,
                    timbre = timbre,
                    taskType = taskType,
                    candidate = candidate,
                    deadlineMs = info?.currentDeadlineMs ?: 4000
                )
            )
            lastNote = note
        }
        return questions
    }

    companion object {
        val ANCHOR_OCTAVES = listOf(4)
        val WIDE_OCTAVES = listOf(3, 4, 5)
        val MASTERED_OCTAVES = WIDE_OCTAVES

        /**
         * Overall naming attempts on a chroma before the first extra instrument may unlock.
         * Octave widening happens at [ActivePitchSetManager.MASTERY_MIN_ATTEMPTS]; this gap
         * is the “season on wide octaves” period (~a few more lessons).
         */
        const val TIMBRE_EXPAND_MIN_ATTEMPTS = 30

        fun noteIsSolid(info: ActivePitchInfo?): Boolean {
            val attempts = info?.attemptCount ?: 0
            val acc = info?.emaAccuracy ?: 0f
            return attempts >= ActivePitchSetManager.MASTERY_MIN_ATTEMPTS &&
                acc >= ActivePitchSetManager.MASTERY_ACCURACY
        }

        /** Ready for wide octaves (3–5). */
        fun octavePoolFor(info: ActivePitchInfo?, masteredChroma: Boolean): List<Int> {
            if (masteredChroma || noteIsSolid(info)) return WIDE_OCTAVES
            return ANCHOR_OCTAVES
        }

        /** True when this note+timbre pair is mastered enough to unlock the next instrument. */
        fun instrumentMastered(skill: TimbreSkill?): Boolean {
            if (skill == null) return false
            return skill.attemptCount >= ActivePitchSetManager.MASTERY_MIN_ATTEMPTS &&
                skill.emaAccuracy >= ActivePitchSetManager.MASTERY_ACCURACY
        }

        /**
         * Octaves first: extra instruments only after the chroma is solid *and* has been
         * practiced further ([TIMBRE_EXPAND_MIN_ATTEMPTS]). Then unlock Settings instruments
         * one at a time — each new one requires mastery of the previous on this chroma.
         */
        fun noteIsTimbreExpandReady(info: ActivePitchInfo?, masteredChroma: Boolean): Boolean {
            if (info == null) return false
            if (!(masteredChroma || noteIsSolid(info))) return false
            if (info.attemptCount < TIMBRE_EXPAND_MIN_ATTEMPTS) return false
            return masteredChroma || info.emaAccuracy >= ActivePitchSetManager.MASTERY_ACCURACY
        }

        fun timbrePoolFor(
            info: ActivePitchInfo?,
            masteredChroma: Boolean,
            selected: List<String>
        ): List<String> {
            val choices = selected.filter { it.isNotBlank() }.ifEmpty { listOf("piano") }
            val primary = choices.first()
            if (choices.size == 1) return choices
            if (!noteIsTimbreExpandReady(info, masteredChroma)) return listOf(primary)

            val skills = info?.timbreSkills.orEmpty()
            val unlocked = mutableListOf(primary)
            for (i in 1 until choices.size) {
                val previous = choices[i - 1]
                if (instrumentMastered(skills[previous])) {
                    unlocked.add(choices[i])
                } else {
                    break
                }
            }
            return unlocked
        }

        fun pickTimbre(pool: List<String>, lastTimbre: String?, random: Random): String {
            val choices = pool.filter { it.isNotBlank() }.ifEmpty { listOf("piano") }
            if (choices.size == 1) return choices.first()
            return choices.filter { it != lastTimbre }.randomOrNull(random) ?: choices.random(random)
        }

        fun pickOctave(
            pool: List<Int>,
            lastOctave: Int?,
            preferMaxJump: Boolean,
            random: Random
        ): Int {
            val choices = pool.ifEmpty { ANCHOR_OCTAVES }
            if (choices.size == 1) return choices.first()
            if (preferMaxJump) {
                if (lastOctave == null) {
                    val extremes = choices.filter { it == choices.minOrNull() || it == choices.maxOrNull() }
                    return extremes.random(random)
                }
                val maxDist = choices.maxOf { kotlin.math.abs(it - lastOctave) }
                val farthest = choices.filter { kotlin.math.abs(it - lastOctave) == maxDist }
                return farthest.random(random)
            }
            return choices.filter { it != lastOctave }.randomOrNull(random) ?: choices.random(random)
        }
    }

    fun buildLesson(
        activePitches: List<ActivePitchInfo>,
        masteredPitches: List<NoteName>,
        primaryTimbre: String,
        questionCount: Int,
        random: Random = Random.Default
    ): List<LessonQuestion> = buildLesson(
        activePitches = activePitches,
        masteredPitches = masteredPitches,
        timbres = listOf(primaryTimbre),
        questionCount = questionCount,
        random = random
    )
}
