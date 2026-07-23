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

/** Per-pitch state the planner needs, decoupled from Room entities. */
data class ActivePitchInfo(
    val note: NoteName,
    val currentDeadlineMs: Int,
    val emaAccuracy: Float,
    val attemptCount: Int,
    val mastered: Boolean,
    /** A mastered pitch whose spaced-repetition review is due. */
    val dueForReview: Boolean = false
)

/**
 * Builds a dynamic lesson (§2.2) from the user's active pitch set and weak-note profile.
 *
 * - Uses [NoteSelector] for weighted, interval-avoiding note selection (Study A / C).
 * - Mixes NAMING and VERIFICATION (Study B): verification is used more heavily for
 *   brand-new pitches (<10 exposures); naming dominates once a pitch is established.
 * - Each question carries the pitch's own current response deadline (Study C).
 * - Octave randomization: every trial picks a random octave from [octaves] so the learner
 *   must identify the pitch CLASS (chroma) regardless of how high or low it sounds. Playing
 *   a fixed octave lets a learner pass by relative "low/mid/high" ordering — a relative-pitch
 *   crutch — which defeats absolute-pitch training. Consecutive-trial interval avoidance
 *   (in [NoteSelector]) is chroma-based, so it still holds across octaves.
 * - Timbre rotation: when [timbres] has more than one entry, instruments rotate across
 *   trials (no consecutive repeat). The repository stages this progressively so early
 *   lessons stay on one instrument while chroma anchors form (§2.4d).
 */
class LessonPlanner(
    private val noteSelector: NoteSelector = NoteSelector()
) {
    fun buildLesson(
        activePitches: List<ActivePitchInfo>,
        masteredPitches: List<NoteName>,
        timbres: List<String>,
        octaves: List<Int>,
        questionCount: Int,
        random: Random = Random.Default
    ): List<LessonQuestion> {
        if (activePitches.isEmpty()) return emptyList()

        val accuracyMap = activePitches.associate { it.note to it.emaAccuracy }
        val infoByNote = activePitches.associateBy { it.note }

        // Notes still being acquired form the core pool; mastered notes only re-enter when
        // their spaced-repetition review is due (expanding intervals — Study A retention).
        val learning = activePitches.filter { !it.mastered }
        val activeNotes = (if (learning.isNotEmpty()) learning else activePitches).map { it.note }
        val dueReview = activePitches.filter { it.mastered && it.dueForReview }.map { it.note }
        val reviewPool = (dueReview + masteredPitches).distinct().filter { it !in activeNotes }

        val count = questionCount.coerceIn(8, 30)
        val octaveChoices = octaves.ifEmpty { listOf(4) }
        val timbreChoices = timbres.filter { it.isNotBlank() }.ifEmpty { listOf("piano") }

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

            // Verification is common for new pitches, rare once a pitch is established.
            val verificationChance = if (isNew) 0.6f else 0.2f
            val taskType = if (random.nextFloat() < verificationChance) {
                TaskType.VERIFICATION
            } else {
                TaskType.NAMING
            }

            val candidate = if (taskType == TaskType.VERIFICATION) {
                // Half the time show the true note (expected "yes"), half a distractor.
                if (random.nextFloat() < 0.5f) note
                else activeNotes.filter { it != note }.randomOrNull(random) ?: note
            } else null

            // Randomize octave; mastered ("green") chromas take the widest jump available
            // so relative high/low from the previous trial is a weaker crutch.
            val masteredChroma = info?.mastered == true || note in reviewPool
            val octave = pickOctave(
                pool = if (masteredChroma) MASTERED_OCTAVES else octaveChoices,
                lastOctave = lastOctave,
                preferMaxJump = masteredChroma,
                random = random
            )
            lastOctave = octave

            val timbre = if (timbreChoices.size > 1) {
                timbreChoices.filter { it != lastTimbre }.random(random)
            } else {
                timbreChoices.first()
            }
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
        /** Full sample range — used for mastered chromas even early in training. */
        val MASTERED_OCTAVES = listOf(3, 4, 5)

        /**
         * @param preferMaxJump when true (mastered chroma), pick an octave farthest from
         *   [lastOctave] — typically a 2-octave leap across 3↔5.
         */
        fun pickOctave(
            pool: List<Int>,
            lastOctave: Int?,
            preferMaxJump: Boolean,
            random: Random
        ): Int {
            val choices = pool.ifEmpty { listOf(4) }
            if (choices.size == 1) return choices.first()
            if (preferMaxJump) {
                if (lastOctave == null) {
                    // Bias to register extremes so the first mastered trial isn't mid-only.
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

    /** Back-compat overload used by older call sites / tests. */
    fun buildLesson(
        activePitches: List<ActivePitchInfo>,
        masteredPitches: List<NoteName>,
        primaryTimbre: String,
        octaves: List<Int>,
        questionCount: Int,
        random: Random = Random.Default
    ): List<LessonQuestion> = buildLesson(
        activePitches = activePitches,
        masteredPitches = masteredPitches,
        timbres = listOf(primaryTimbre),
        octaves = octaves,
        questionCount = questionCount,
        random = random
    )
}
