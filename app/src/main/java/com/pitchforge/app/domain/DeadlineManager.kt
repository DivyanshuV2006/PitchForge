package com.pitchforge.app.domain

/**
 * Manages the shrinking response-time (RT) window — the core mechanic from Study C.
 *
 * Deadline shrinks as accuracy improves, converging toward the 1305-2028ms range.
 * If accuracy drops after a tightening, deadline loosens back one step.
 */
class DeadlineManager {

    /** Definition of a deadline tier. */
    data class DeadlineTier(
        val thresholdMs: Int,
        val accuracyRequired: Float,
        val name: String
    )

    val tiers = listOf(
        DeadlineTier(4000, 0.00f, "Initial"),
        DeadlineTier(3000, 0.70f, "Tightened"),
        DeadlineTier(2200, 0.80f, "Stricter"),
        DeadlineTier(1600, 0.90f, "Advanced")
    )

    /** Floor from Study C — never tighten below ~1300ms. */
    val floorMs = 1300

    /**
     * Returns the appropriate deadline for a pitch given its current accuracy.
     * If accuracy drops, loosens back one step.
     */
    fun computeDeadline(
        currentDeadline: Int,
        accuracyOverLast15: Float,
        attemptCount: Int
    ): Int {
        // New pitches get the most generous tier for first 10 exposures
        if (attemptCount < 10) return 4000

        // Find the strictest tier this accuracy qualifies for (lowest ms threshold)
        val eligible = tiers.filter { accuracyOverLast15 >= it.accuracyRequired }
        val targetDeadline = if (eligible.isEmpty()) {
            tiers.first().thresholdMs
        } else {
            eligible.minBy { it.thresholdMs }.thresholdMs
        }

        // If current is already more generous than target, tighten (improving)
        // If current is stricter than target, loosen (struggling — deviation from lab protocol)
        return when {
            currentDeadline > targetDeadline -> maxOf(targetDeadline, floorMs)
            currentDeadline < targetDeadline -> {
                // Loosen one step up — intentional deviation from strict lab protocol
                val nextLooser = tiers.filter { it.thresholdMs > currentDeadline }
                    .minByOrNull { it.thresholdMs }
                nextLooser?.thresholdMs ?: currentDeadline
            }
            else -> currentDeadline
        }
    }

    /** Returns the deadline tier label for display. */
    fun getTierLabel(deadlineMs: Int): String {
        return tiers.lastOrNull { deadlineMs <= it.thresholdMs }?.name ?: "Expert"
    }
}
