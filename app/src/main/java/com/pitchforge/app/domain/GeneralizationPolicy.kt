package com.pitchforge.app.domain

import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Pure scheduling rules for generalization probes (§2.4e) and retention checks (§2.4f),
 * kept free of Android so they are unit-testable (§6 #16, #17).
 */
object GeneralizationPolicy {

    /** A generalization probe runs at most once every ~2 weeks of active use. */
    const val PROBE_INTERVAL_DAYS = 14L

    const val TRIAL_COUNT = 10

    /**
     * Instruments we can actually play in a probe. Matches Study A + extras that ship
     * with samples (or are synthesized).
     */
    val PROBE_TIMBRES: List<String> = TimbreCatalog.PROBE_POOL

    /**
     * Due when ~2 weeks have passed since the last probe — or, for the first probe,
     * since onboarding (never day-0).
     */
    fun isProbeDue(lastProbeAt: Long?, now: Long, onboardedAt: Long?): Boolean {
        if (onboardedAt == null) return false
        val anchor = lastProbeAt ?: onboardedAt
        return now - anchor >= TimeUnit.DAYS.toMillis(PROBE_INTERVAL_DAYS)
    }

    /**
     * Picks a timbre the user has never trained on, so the probe measures genuine
     * chroma-based generalization rather than memorized timbre-specific cues.
     */
    fun pickUntrainedTimbre(trainedTimbres: Collection<String>, allTimbres: List<String> = PROBE_TIMBRES): String? =
        allTimbres.firstOrNull { it !in trainedTimbres }

    data class Trial(val note: NoteName, val octave: Int)

    /** Short naming block on [timbre] using the learner's known pitch classes when possible. */
    fun buildTrials(
        knownNotes: List<NoteName>,
        count: Int = TRIAL_COUNT,
        random: Random = Random.Default
    ): List<Trial> {
        val pool = knownNotes.ifEmpty { NoteName.entries }.distinct()
        val octaves = listOf(4, 5)
        return List(count) {
            Trial(pool.random(random), octaves.random(random))
        }
    }
}

/** Retention-check scheduling: fires at ~30 and ~90 days after a pitch was first mastered. */
object RetentionPolicy {
    val CHECK_OFFSETS_DAYS = listOf(30L, 90L)

    /** Trials per due pitch in a retention probe session. */
    const val TRIALS_PER_PITCH = 2

    fun dueDatesFor(masteredAt: Long): List<Long> =
        CHECK_OFFSETS_DAYS.map { masteredAt + TimeUnit.DAYS.toMillis(it) }

    fun isDue(checkDueAt: Long, checkCompletedAt: Long?, now: Long): Boolean =
        checkCompletedAt == null && checkDueAt <= now

    data class Trial(val note: NoteName, val octave: Int)

    fun buildTrials(
        duePitchLabels: List<String>,
        trialsPerPitch: Int = TRIALS_PER_PITCH,
        random: Random = Random.Default
    ): List<Trial> {
        val notes = duePitchLabels.map { NoteName.fromLabel(it) }.distinct()
        if (notes.isEmpty()) return emptyList()
        val octaves = listOf(4, 5)
        return buildList {
            notes.forEach { note ->
                repeat(trialsPerPitch) {
                    add(Trial(note, octaves.random(random)))
                }
            }
        }.shuffled(random)
    }
}
