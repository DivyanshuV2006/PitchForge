package com.pitchforge.app.ui.probe

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pitchforge.app.domain.NoteName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralizationScreen(
    onBack: () -> Unit,
    viewModel: GeneralizationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    ProbeScaffold(
        title = "Generalization",
        phase = state.phase,
        onBack = onBack,
        intro = {
            IntroPane(
                contentPadding = it,
                title = "Untrained instrument",
                body = "A short naming block on ${state.timbre?.replaceFirstChar { c -> c.uppercase() } ?: "a new"} — an instrument you haven't practiced. No feedback until the end. This does not affect mastery, XP, or streaks.",
                cta = "Start probe",
                onStart = viewModel::begin
            )
        },
        unavailable = {
            MessagePane(it, "No untrained instrument left", "You've practiced every instrument we can probe with. Keep training — more instruments may arrive later.") {
                onBack()
            }
        },
        done = {
            ResultPane(
                contentPadding = it,
                title = "Generalization score",
                accuracy = state.accuracy,
                avgError = state.avgErrorSemitones,
                detail = "On ${state.timbre?.replaceFirstChar { c -> c.uppercase() } ?: "new timbre"} — training stats unchanged.",
                onDone = onBack
            )
        },
        trial = {
            TrialPane(
                label = "Generalization",
                index = state.index,
                total = state.total,
                phase = state.phase,
                choices = state.choices,
                onPlay = viewModel::playCurrent,
                onAnswer = viewModel::answer
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetentionScreen(
    onBack: () -> Unit,
    viewModel: RetentionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    ProbeScaffold(
        title = "Retention",
        phase = state.phase,
        onBack = onBack,
        intro = {
            IntroPane(
                contentPadding = it,
                title = "Retention check",
                body = "Quick check on notes you mastered weeks ago: ${state.pitchLabels.joinToString(", ")}. No feedback until the end — and it won't change your training stats.",
                cta = "Start check",
                onStart = viewModel::begin
            )
        },
        unavailable = {
            MessagePane(it, "Nothing due yet", "Retention checks appear about 30 and 90 days after you master a note.") {
                onBack()
            }
        },
        done = {
            ResultPane(
                contentPadding = it,
                title = "Retention complete",
                accuracy = state.accuracy,
                avgError = state.avgErrorSemitones,
                detail = state.perPitchAccuracy.entries.joinToString(" · ") {
                    "${it.key} ${(it.value * 100).toInt()}%"
                }.ifBlank { "Training stats unchanged." },
                onDone = onBack
            )
        },
        trial = {
            TrialPane(
                label = "Retention",
                index = state.index,
                total = state.total,
                phase = state.phase,
                choices = state.choices,
                onPlay = viewModel::playCurrent,
                onAnswer = viewModel::answer
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProbeScaffold(
    title: String,
    phase: ProbePhase,
    onBack: () -> Unit,
    intro: @Composable (PaddingValues) -> Unit,
    unavailable: @Composable (PaddingValues) -> Unit,
    done: @Composable (PaddingValues) -> Unit,
    trial: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            if (phase == ProbePhase.INTRO || phase == ProbePhase.DONE || phase == ProbePhase.UNAVAILABLE) {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    ) { padding ->
        when (phase) {
            ProbePhase.LOADING -> BoxLoading(padding)
            ProbePhase.UNAVAILABLE -> unavailable(padding)
            ProbePhase.INTRO -> intro(padding)
            ProbePhase.DONE -> done(padding)
            else -> trial()
        }
    }
}

@Composable
private fun BoxLoading(padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { CircularProgressIndicator() }
}

@Composable
private fun IntroPane(
    contentPadding: PaddingValues,
    title: String,
    body: String,
    cta: String,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 28.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            body,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text(cta, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MessagePane(contentPadding: PaddingValues, title: String, body: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 28.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Back", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TrialPane(
    label: String,
    index: Int,
    total: Int,
    phase: ProbePhase,
    choices: List<NoteName>,
    onPlay: () -> Unit,
    onAnswer: (NoteName) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 28.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (phase) {
            ProbePhase.READY -> {
                Spacer(Modifier.weight(1f))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Note ${index + 1} of $total", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onPlay,
                    modifier = Modifier.size(132.dp).semantics { contentDescription = "Play the note" },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("\u25B6", fontSize = 40.sp) }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Listen, then name the note.\nNo feedback until the end.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.weight(1f))
            }
            else -> {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Which note was that?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    choices.chunked(3).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { note ->
                                Button(
                                    onClick = { onAnswer(note) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(60.dp)
                                        .semantics { contentDescription = "Answer ${note.label}" },
                                    shape = MaterialTheme.shapes.medium,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) { Text(note.label, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun ResultPane(
    contentPadding: PaddingValues,
    title: String,
    accuracy: Float,
    avgError: Float,
    detail: String,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 28.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Accuracy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${(accuracy * 100).toInt()}%",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Avg error: ${String.format("%.1f", avgError)} semitones",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    detail,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { Text("Done", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(8.dp))
    }
}
