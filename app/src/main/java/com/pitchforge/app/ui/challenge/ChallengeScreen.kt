package com.pitchforge.app.ui.challenge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pitchforge.app.R
import com.pitchforge.app.ui.components.DeadlineRing
import com.pitchforge.app.ui.components.rememberSmoothFlingBehavior

data class ChallengeInfo(val name: String, val description: String, val type: ChallengeType, val badge: String)

val challenges = listOf(
    ChallengeInfo("20-Note Gauntlet", "Name 20 notes in a row. No adaptive difficulty, no mercy.", ChallengeType.GAUNTLET, "20"),
    ChallengeInfo("Timed Mode", "3 seconds per note. Miss the clock and it counts against you.", ChallengeType.TIMED, "\u23F1"),
    ChallengeInfo("Mixed Timbre Chaos", "The instrument changes every question.", ChallengeType.CHAOS, "\u266A")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeScreen(
    onBack: () -> Unit,
    viewModel: ChallengeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skill Challenges", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = { if (state.phase == ChallengePhase.IDLE) onBack() else viewModel.reset() }) {
                        Text(if (state.phase == ChallengePhase.IDLE) "Back" else "Quit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            when (state.phase) {
                ChallengePhase.IDLE -> ChallengeList(onStart = viewModel::start)
                ChallengePhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading instruments…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ChallengePhase.DONE -> ChallengeResult(state, onDone = viewModel::reset)
                ChallengePhase.BUFFERING -> ChallengeBuffer(state)
                else -> ChallengeRun(state, viewModel)
            }
        }
    }
}

@Composable
private fun ChallengeBuffer(state: ChallengeUiState) {
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
            if (state.clusterWashout) "Scrambling last note…" else "Clearing last note…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.clusterWashout) "Distractor cluster washout"
            else "Variable noise washout (1.5–4s)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChallengeList(onStart: (ChallengeType) -> Unit) {
    val listState = rememberLazyListState()
    val flingBehavior = rememberSmoothFlingBehavior()
    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Text(
                "Harder, non-adaptive TEST modes. These don't affect your training accuracy model — they're tests, not practice.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        items(challenges) { c ->
            Card(
                onClick = { onStart(c.type) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(c.badge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(c.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("\u2192", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChallengeRun(state: ChallengeUiState, viewModel: ChallengeViewModel) {
    val timed = state.deadlineMs != null
    val haptic = LocalHapticFeedback.current
    var started by remember(state.index, state.phase) { mutableStateOf(false) }
    LaunchedEffect(state.index, state.phase) {
        started = state.phase == ChallengePhase.ANSWERING
    }
    val ringProgress by animateFloatAsState(
        targetValue = if (started && timed) 0f else 1f,
        animationSpec = tween(durationMillis = if (started && timed) state.deadlineMs!! else 0),
        label = "challengeDeadline"
    )
    // Match daily-lesson deadline haptic: pulse near expiry as a non-visual cue.
    LaunchedEffect(ringProgress, timed) {
        if (timed && ringProgress in 0.01f..0.25f) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Question ${state.index + 1} of ${state.total}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { state.index.toFloat() / state.total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (state.type == ChallengeType.CHAOS && state.currentTimbre != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                state.currentTimbre.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (timed) {
            Spacer(Modifier.height(8.dp))
            Text(
                "3-second limit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.weight(1f))
        if (state.phase == ChallengePhase.READY) {
            Button(
                onClick = viewModel::playCurrent,
                modifier = Modifier.size(132.dp).semantics { contentDescription = "Play the note" },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(0.dp)
            ) { Text("\u25B6", fontSize = 40.sp) }
            Spacer(Modifier.height(16.dp))
            Text(
                if (timed) "Tap play, then name it before the clock runs out"
                else "Tap to hear the note",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else {
            if (timed) {
                DeadlineRing(progress = ringProgress, deadlineMs = state.deadlineMs!!, modifier = Modifier.size(120.dp)) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\u266A", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Text("Which note is this?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.choices.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { note ->
                            Button(
                                onClick = { viewModel.answer(note) },
                                modifier = Modifier.weight(1f).height(60.dp).semantics { contentDescription = "Answer ${note.label}" },
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)
                            ) { Text(note.label, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ChallengeResult(state: ChallengeUiState, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Challenge complete", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("${state.correct} / ${state.total} correct", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (state.type == ChallengeType.TIMED && state.timedOut > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "${state.timedOut} timed out",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("This result does not affect your training stats.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.navigationBarsPadding().fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) { Text("Done", fontWeight = FontWeight.SemiBold) }
    }
}
