package com.pitchforge.app.domain

import kotlin.random.Random

/**
 * Friendly, rotating notification copy — each type has 10–12 variants so reminders
 * don't feel robotic. Workers pick one at random per send.
 */
object NotificationCopy {

    data class Message(val title: String, val body: String)

    private fun pick(pool: List<Message>, random: Random = Random.Default): Message =
        pool[random.nextInt(pool.size)]

    // ---- Habit (daily practice nudge) ----

    private val HABIT_WITH_HISTORY = listOf(
        Message("Your ear is waiting", "This is usually when you train. A short lesson keeps absolute pitch growing."),
        Message("Ready when you are", "Hop in for one session — consistency beats cramming every time."),
        Message("Pitch practice time", "A few focused minutes now will thank you in a few weeks."),
        Message("Let's name some notes", "No reference tone, just you and the sound. You've got this."),
        Message("Keep the streak warm", "Even one solid lesson protects the progress you've already made."),
        Message("Your notes miss you", "Drop in for today's session — short, focused, and worth it."),
        Message("Absolute pitch, daily", "Small sessions add up. Open PitchForge and take the next step."),
        Message("Ear gym is open", "Warm up those chromas — one lesson is a perfect daily dose."),
        Message("Nice window to train", "You're usually here around now. Make it count with a quick session."),
        Message("Progress loves routine", "Show up today. Future-you will hear the difference."),
        Message("One lesson, big payoff", "Ten to fifteen minutes of naming notes — that's the whole ask."),
        Message("Come sharpen your ear", "Friendly reminder: today's practice is waiting whenever you're ready.")
    )

    private val HABIT_NO_HISTORY = listOf(
        Message("Good morning, ear", "Morning is a great time to start. One short lesson is all you need."),
        Message("Start fresh today", "Absolute pitch grows with gentle daily practice — begin with one session."),
        Message("Your first notes await", "No pressure, just curiosity. Open PitchForge and listen."),
        Message("Morning pitch check-in", "A calm morning lesson is a lovely way to train your ear."),
        Message("Let's begin", "One 30-note lesson (~10–15 min) is a solid daily dose. You've got this."),
        Message("Hear something new", "Perfect pitch isn't magic — it's practice. Start with today's session."),
        Message("Sunrise for your ear", "A few minutes this morning can kick off a lasting habit."),
        Message("Welcome back to sound", "Whenever you're ready, PitchForge is here for a friendly warm-up."),
        Message("Small start, big ear", "You don't need a long session — just show up and name a few notes."),
        Message("Today's a good day", "Build the habit gently. One lesson is enough to start."),
        Message("Curious ears welcome", "No judgment, no rush — just listen, name, and learn."),
        Message("Open and listen", "Your absolute-pitch journey starts with a single short session.")
    )

    fun habit(streak: Int = 0, hasPracticeHistory: Boolean = true, random: Random = Random.Default): Message {
        val base = pick(if (hasPracticeHistory) HABIT_WITH_HISTORY else HABIT_NO_HISTORY, random)
        if (streak <= 0) return base
        return base.copy(body = "${base.body} Keep your $streak-day streak going!")
    }

    // ---- Second session ----

    private val SECOND_SESSION = listOf(
        Message("Ready for a second dose?", "One lesson is good — a short follow-up later helps lock notes in."),
        Message("Bonus round?", "A second mini-session today can deepen what you just learned."),
        Message("Still have a little fuel?", "Optional second lesson — research loves spaced practice."),
        Message("Two beats better than one", "If you're up for it, another short block seals the day."),
        Message("Lock it in", "A second pass helps notes stick. Totally optional, totally worth it."),
        Message("Afternoon ear snack", "Quick second session? Your chromas will thank you."),
        Message("Don't cram — split it", "You already practiced once. A gentle encore beats one long grind."),
        Message("Second wind for your ear", "Whenever you're free, a short follow-up keeps momentum kind."),
        Message("One more, lightly", "No marathon — just another friendly dose if it feels good."),
        Message("Distributed practice FTW", "Science (and your future ear) prefer two short sessions."),
        Message("Encore available", "PitchForge is ready for a second lesson whenever you are."),
        Message("Seal today's learning", "A quick second session helps turn hearing into remembering.")
    )

    fun secondSession(random: Random = Random.Default): Message = pick(SECOND_SESSION, random)

    // ---- Spaced review ----

    private val REVIEW = listOf(
        Message("Review time", "A couple of notes are due — two minutes now beats relearning later."),
        Message("Protect what you learned", "Spaced review keeps mastered notes from fading. Quick check-in?"),
        Message("Your notes want a hello", "A short review pass will keep them sharp and friendly."),
        Message("Don't let them slip", "Mastered pitches need a peek now and then. You've got this."),
        Message("Memory boost available", "Open PitchForge for a quick spaced-review session."),
        Message("Keep the greens green", "A tiny review today protects weeks of hard-earned progress."),
        Message("Ear check, soft edition", "Just a gentle revisit of notes you've already owned."),
        Message("Spaced repetition calling", "Your schedule says it's time — short, kind, effective."),
        Message("Revisit and smile", "Hearing an old friend-note again is oddly satisfying. Try it."),
        Message("Maintenance for mastery", "Champions review. A few minutes keeps absolute pitch honest."),
        Message("Quick polish", "Buff those mastered notes so they stay bright."),
        Message("Stay fluent in pitch", "A brief review keeps naming effortless.")
    )

    fun review(noteLabels: List<String>, random: Random = Random.Default): Message {
        val base = pick(REVIEW, random)
        if (noteLabels.isEmpty()) return base
        val shown = noteLabels.take(4).joinToString(", ")
        val more = if (noteLabels.size > 4) " +${noteLabels.size - 4} more" else ""
        return base.copy(body = "$shown$more — ${base.body}")
    }

    // ---- Retention ----

    private val RETENTION = listOf(
        Message("Retention check ready", "A short check on notes you mastered weeks ago — curious how they feel?"),
        Message("Still got it?", "Time for a friendly retention probe. No pressure, just honesty."),
        Message("Long-term ear test", "See if those notes stuck. Open PitchForge for a quick check."),
        Message("Memory lane (musical)", "A few old friends are due for a listen. You've got this."),
        Message("Retention moment", "Confirm the pitches you owned still answer when called."),
        Message("Check the foundations", "A brief retention session keeps long-term gains visible."),
        Message("Weeks later…", "How's that note holding up? A short probe will tell you."),
        Message("Durable pitch check", "This one's about lasting memory — short and insightful."),
        Message("Prove it to yourself", "Retention check ready. Celebrate what stuck."),
        Message("Time capsule tones", "Open up and see which notes still feel like home."),
        Message("Stay honest with progress", "A retention probe won't change training stats — just informs you."),
        Message("Long game check-in", "Absolute pitch is a marathon. This tiny test helps.")
    )

    fun retention(noteLabels: List<String>, random: Random = Random.Default): Message {
        val base = pick(RETENTION, random)
        if (noteLabels.isEmpty()) return base
        val notes = noteLabels.distinct().joinToString(", ")
        return base.copy(body = "Notes: $notes. ${base.body}")
    }

    // ---- Generalization ----

    private val GENERALIZATION = listOf(
        Message("New instrument, same ear?", "See if your pitch sense transfers — fun, and it won't touch training stats."),
        Message("Generalization check", "Try an untrained timbre and discover how far your ear travels."),
        Message("Can you hear it elsewhere?", "A short probe on a fresh instrument. Curious minds welcome."),
        Message("Timbre adventure", "Same notes, new color of sound. Give the generalization check a go."),
        Message("Transfer test ready", "Open PitchForge and see if absolute pitch follows you across instruments."),
        Message("Stretch your listening", "A friendly untrained-timbre probe is waiting."),
        Message("Beyond the piano", "Your ear might surprise you on a new sound. Take the quick check."),
        Message("Cross-instrument curiosity", "Generalization probe due — low stakes, high insight."),
        Message("Does chroma travel?", "Find out with a short session on an instrument you haven't trained."),
        Message("Ear passport stamp", "New timbre checkpoint. Won't change your mastery scores."),
        Message("Color the pitch", "Same note names, different voice. Ready when you are."),
        Message("Generalize with kindness", "No grades that stick — just a peek at how flexible your ear is.")
    )

    fun generalization(timbre: String, random: Random = Random.Default): Message {
        val base = pick(GENERALIZATION, random)
        val pretty = timbre.replaceFirstChar { it.uppercase() }
        return base.copy(body = "${base.body} Today's guest: $pretty.")
    }

    // ---- Monthly checkup ----

    private val CHECKUP = listOf(
        Message("Monthly checkup due", "A short diagnostic — no feedback until the end. It won't change training stats."),
        Message("Month-end ear snapshot", "Take the AP checkup and see where you stand. Friendly and measurement-only."),
        Message("How's your absolute pitch?", "Monthly measure ready. Honest listening, zero training side effects."),
        Message("Checkup time", "A calm diagnostic to track progress over months — you've earned this look."),
        Message("Baseline, refreshed", "Revisit the checkup protocol. No pressure, just data for you."),
        Message("End-of-month listen", "PitchForge's monthly AP checkup is open. Short and insightful."),
        Message("Measure, don't stress", "This checkup never rewrites mastery — it only informs."),
        Message("Progress portrait", "Sit for a quick monthly diagnostic and celebrate the arc."),
        Message("AP temperature check", "How warm is your pitch sense this month? Find out gently."),
        Message("Calendar says checkup", "Whenever you're ready — same fair test, clearer picture."),
        Message("Stay curious about growth", "Monthly checkup due. Treat it like a friendly lab visit."),
        Message("Snapshot for your journey", "One short diagnostic keeps the long story honest.")
    )

    fun checkup(random: Random = Random.Default): Message = pick(CHECKUP, random)
}
