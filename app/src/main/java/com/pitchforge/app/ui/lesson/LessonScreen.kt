package com.pitchforge.app.ui.lesson

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pitchforge.app.R
import com.pitchforge.app.domain.NoteName
import com.pitchforge.app.domain.TaskType
import com.pitchforge.app.ui.components.DeadlineRing
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
@Composable
fun LessonScreen(
    onNavigateToDashboard: () -> Unit,
    viewModel: LessonViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(viewModel) {
        onDispose { viewModel.stopAudio() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        if (state.totalQuestions > 0) {
            Text(
                "Question ${state.questionIndex + 1} of ${state.totalQuestions}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.questionIndex.toFloat() / state.totalQuestions },
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Spacer(Modifier.height(32.dp))

        when (state.phase) {
            LessonPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            LessonPhase.EMPTY -> EmptyState(onNavigateToDashboard)
            LessonPhase.DOSE_CAPPED -> DoseCappedState(onNavigateToDashboard)
            LessonPhase.COOLDOWN -> CooldownState(state, onNavigateToDashboard)
            LessonPhase.READY -> PlayPrompt(onPlay = viewModel::playCurrentNote)
            LessonPhase.BUFFERING -> InterTrialBuffer(state)
            LessonPhase.ANSWERING -> AnsweringArea(state, viewModel)
            LessonPhase.FEEDBACK -> FeedbackArea(state, onNext = viewModel::next, onReplay = viewModel::replayCurrentNote)
            LessonPhase.SUMMARY -> SummaryArea(state, onNavigateToDashboard)
        }
    }
}

@Composable
private fun EmptyState(onNavigateToDashboard: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("No active notes yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Finish onboarding to start training.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNavigateToDashboard) { Text(stringResource(R.string.dashboard)) }
    }
}

@Composable
private fun DoseCappedState(onNavigateToDashboard: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Daily dose complete", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "You've hit today's training cap (${com.pitchforge.app.domain.InterTrialPolicy.DAILY_LESSON_HARD_CAP} lessons). " +
                "Distributed practice beats cramming — come back tomorrow. Challenges & probes are still open.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNavigateToDashboard) { Text(stringResource(R.string.dashboard)) }
    }
}

@Composable
private fun CooldownState(state: LessonUiState, onNavigateToDashboard: () -> Unit) {
    var remaining by remember(state.cooldownRemainingMs) { mutableStateOf(state.cooldownRemainingMs) }
    LaunchedEffect(state.cooldownRemainingMs) {
        remaining = state.cooldownRemainingMs
        while (remaining > 0L) {
            delay(1_000)
            remaining = (remaining - 1_000).coerceAtLeast(0L)
        }
    }
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Session cooldown", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            com.pitchforge.app.domain.InterTrialPolicy.formatCooldown(remaining),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "20 minutes between lessons helps lock in what you just practiced.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNavigateToDashboard) { Text(stringResource(R.string.dashboard)) }
    }
}

@Composable
private fun PlayPrompt(onPlay: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Button(
            onClick = onPlay,
            modifier = Modifier.size(132.dp).semantics { contentDescription = "Play the note" },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("\u25B6", fontSize = 40.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.tap_to_hear),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Later notes play automatically after a short pause.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InterTrialBuffer(state: LessonUiState) {
    val cold = state.bufferMode == BufferMode.COLD_START
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(36.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (cold) "Cold-start probe"
            else if (state.bufferMode == BufferMode.CLUSTER) "Scrambling last note…"
            else "Clearing last note…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                cold && state.coldStartSecondsLeft != null ->
                    "Silence ${state.coldStartSecondsLeft}s — no pitch reference"
                cold -> "Clearing, then silence…"
                state.bufferMode == BufferMode.CLUSTER -> "Distractor cluster washout"
                else -> "Variable noise washout (1.5–4s)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AnsweringArea(state: LessonUiState, viewModel: LessonViewModel) {
    val q = state.current ?: return
    val haptic = LocalHapticFeedback.current

    // Deadline ring shrinks over the pitch's current deadline; the ViewModel enforces the
    // hard timeout. A haptic pulse near expiry is the non-visual equivalent for TalkBack (#24).
    var started by remember(state.questionIndex) { mutableStateOf(false) }
    LaunchedEffect(state.questionIndex) { started = true }
    val progress by animateFloatAsState(
        targetValue = if (started) 0f else 1f,
        animationSpec = tween(durationMillis = q.deadlineMs),
        label = "deadline"
    )
    LaunchedEffect(progress) {
        if (progress in 0.01f..0.25f) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    DeadlineRing(progress = progress, deadlineMs = q.deadlineMs, modifier = Modifier.size(132.dp)) {
        Box(
            Modifier.size(108.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("\u266A", fontSize = 34.sp, color = MaterialTheme.colorScheme.primary)
        }
    }

    Spacer(Modifier.height(28.dp))

    if (state.isColdStartTrial) {
        Text(
            "Cold-start — name it without a recent reference",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
    }

    if (q.taskType == TaskType.NAMING) {
        Text(stringResource(R.string.which_note), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.answerChoices.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { note ->
                        Button(
                            onClick = { viewModel.submitNaming(note) },
                            modifier = Modifier.weight(1f).height(64.dp).semantics { contentDescription = "Answer ${note.label}" },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text(note.label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    } else {
        Text(
            "Is this a ${q.candidate?.label ?: "?"}?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { viewModel.submitVerification(true) },
                modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = "Yes" },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
            ) { Text("Yes", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = { viewModel.submitVerification(false) },
                modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = "No" },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            ) { Text("No", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
    Spacer(Modifier.navigationBarsPadding())
}

@Composable
private fun FeedbackArea(state: LessonUiState, onNext: () -> Unit, onReplay: () -> Unit) {
    val fb = state.feedback ?: return
    val celebrate = fb.correct && !fb.late
    val correctGreen = Color(0xFF22C55E)
    val color = when {
        fb.late -> MaterialTheme.colorScheme.tertiary
        fb.correct -> correctGreen
        else -> MaterialTheme.colorScheme.error
    }
    val message = when {
        fb.late -> stringResource(R.string.too_slow)
        fb.correct -> stringResource(R.string.correct)
        else -> stringResource(R.string.incorrect)
    }

    val haptic = LocalHapticFeedback.current
    val badgeScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (celebrate) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            badgeScale.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        } else {
            badgeScale.snapTo(1f)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (celebrate) {
            ConfettiBurst(modifier = Modifier.fillMaxSize())
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
                color = color.copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        fb.correctNote.label,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics {
                            contentDescription = "That note was ${fb.correctNote.label}"
                            liveRegion = LiveRegionMode.Assertive
                        }
                    )
                    if (celebrate) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "+10 XP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                        if (state.combo >= 2) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "\uD83D\uDD25 ${state.combo} in a row",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onReplay,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Replay note" }
            ) {
                Text("Replay note", fontWeight = FontWeight.SemiBold)
            }

            // Big green check under Replay — takes the leftover space on correct answers.
            if (celebrate) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(160.dp)
                            .scale(badgeScale.value)
                            .clip(CircleShape)
                            .background(correctGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Correct",
                            tint = Color.White,
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Button(
                onClick = onNext,
                enabled = state.feedbackAdvanceEnabled,
                modifier = Modifier.navigationBarsPadding().fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    when {
                        !state.feedbackAdvanceEnabled -> "Playing answer…"
                        state.questionIndex + 1 < state.totalQuestions ->
                            stringResource(R.string.next_question)
                        else -> stringResource(R.string.see_results)
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private data class ConfettiPiece(
    val startX: Float,
    val color: Color,
    val width: Float,
    val height: Float,
    val drift: Float,
    val spin: Float,
    val delay: Float
)

@Composable
private fun ConfettiBurst(modifier: Modifier = Modifier) {
    val colors = listOf(
        Color(0xFF22C55E),
        Color(0xFFFFB300),
        Color(0xFFFF7043),
        Color(0xFFE6C06A),
        Color(0xFF60A5FA),
        Color(0xFFF472B6),
        Color(0xFFF3EBE0)
    )
    val pieces = remember {
        val rng = Random(System.currentTimeMillis())
        List(48) {
            ConfettiPiece(
                startX = rng.nextFloat(),
                color = colors[rng.nextInt(colors.size)],
                width = 8f + rng.nextFloat() * 10f,
                height = 6f + rng.nextFloat() * 8f,
                drift = (rng.nextFloat() - 0.5f) * 220f,
                spin = (rng.nextFloat() - 0.5f) * 720f,
                delay = rng.nextFloat() * 0.18f
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1600))
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        pieces.forEach { p ->
            val localT = ((progress.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (localT <= 0f) return@forEach
            val x = p.startX * w + p.drift * localT
            val y = -40f + (h + 80f) * localT * localT
            val alpha = (1f - localT).coerceIn(0f, 1f)
            rotate(degrees = p.spin * localT, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x - p.width / 2f, y - p.height / 2f),
                    size = Size(p.width, p.height)
                )
            }
        }
        // A few circular bits for variety
        pieces.take(12).forEachIndexed { i, p ->
            val localT = ((progress.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (localT <= 0f) return@forEachIndexed
            val angle = (i / 12f) * 360f * (Math.PI / 180.0)
            val radius = 40f + 180f * localT
            val cx = w / 2f + (cos(angle) * radius).toFloat()
            val cy = h * 0.35f + (sin(angle) * radius * 0.55f).toFloat() + 120f * localT
            drawCircle(
                color = p.color.copy(alpha = (1f - localT) * 0.9f),
                radius = 4f + p.width * 0.15f,
                center = Offset(cx, cy)
            )
        }
    }
}

@Composable
private fun SummaryArea(state: LessonUiState, onNavigateToDashboard: () -> Unit) {
    val s = state.summary ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.lesson_complete), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            s.todayWin ?: "Nice work — here's how you did.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        if (s.newlyMasteredNotes.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (s.newlyMasteredNotes.size == 1) "Note mastered!" else "Notes mastered!",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        s.newlyMasteredNotes.joinToString(" · ") { it.label },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Held ≥95% naming accuracy over the last 3 days. That's real absolute-pitch progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        s.newlyUnlockedNote?.let {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text("+", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("New note unlocked", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(it.label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (s.leveledUp) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                        Text("${s.level}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Level up!", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("You reached Level ${s.level}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatRow("Accuracy (on time)", "${(s.accuracy * 100).toInt()}%")
                StatRow("Avg error", "${String.format("%.1f", s.avgErrorSemitones)} semitones")
                StatRow("Avg response", "${s.avgResponseTimeMs.toInt()} ms")
                StatRow("Score", "${s.correctWithinDeadline}/${s.totalQuestions}")
                StatRow("XP earned", "+${s.xpEarned}")
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Level ${s.level}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Text("${s.xpIntoLevel}/${s.xpForNextLevel} XP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(s.levelProgress).height(8.dp)
                                .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        if (s.noteBreakdown.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "What changed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "How each note went in this lesson",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    s.noteBreakdown.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${row.note.label}  ${row.correct}/${row.total} · ${row.percent}%",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                row.tag,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = when (row.tag) {
                                    "Strong" -> MaterialTheme.colorScheme.primary
                                    "Slipped" -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        if (s.suggestSecondSession) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Optional second dose",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "One lesson is a solid start. Coming back later today for another short block helps lock notes in better than a single longer session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onNavigateToDashboard,
            modifier = Modifier.navigationBarsPadding().fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) { Text(stringResource(R.string.dashboard), fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}
