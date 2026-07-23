package com.pitchforge.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pitchforge.app.R
import com.pitchforge.app.domain.NoteName

data class OnboardingPage(val title: String, val body: String)

val pages = listOf(
    OnboardingPage(
        "Welcome to ChromaP",
        "Most adults improve a lot. Full 12-note absolute pitch is rare. Progress is real either way — this app is built on peer-reviewed research into adult AP learning."
    ),
    OnboardingPage(
        "How It Works",
        "You'll hear a note, name it, and get instant feedback. Training starts with 3 notes and expands as you master them, while the answer deadline tightens to build real recognition speed. A right-but-slow answer won't count as mastery — that's intentional."
    ),
    OnboardingPage(
        "Practice Matters",
        "Everyone progresses at their own pace. Aim for one 30-note lesson (~10–15 min) daily; an optional second short session later helps more than cramming."
    ),
    OnboardingPage(
        "Diagnostic Test",
        "First, a short diagnostic: about 14 notes with no feedback until the end. This sets your baseline and starting level. A monthly checkup reuses the same measure at month-end — it never changes your training stats."
    )
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val diag by viewModel.state.collectAsState()

    if (diag.phase == DiagnosticPhase.INTRO) {
        IntroPager(
            skipping = diag.skipping,
            onStartDiagnostic = { viewModel.beginDiagnostic() }
        )
    } else {
        DiagnosticFlow(diag = diag, viewModel = viewModel, onComplete = onComplete)
    }
}

@Composable
private fun IntroPager(
    skipping: Boolean,
    onStartDiagnostic: () -> Unit
) {
    var currentPage by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 28.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        // Step indicator: start-aligned dots, clear of the centered front-camera punch-out.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pages.forEachIndexed { index, _ ->
                Box(
                    Modifier
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .then(if (index == currentPage) Modifier.fillMaxWidth(0f) else Modifier)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        AnimatedContent(targetState = currentPage, label = "page") { page ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${page + 1}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.height(24.dp))
                Text(pages[page].title, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(
                    pages[page].body,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (currentPage < pages.lastIndex) currentPage++ else onStartDiagnostic() },
            enabled = !skipping,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            Text(
                if (currentPage == pages.lastIndex) stringResource(R.string.start_diagnostic) else stringResource(R.string.next),
                fontWeight = FontWeight.SemiBold
            )
        }
        // Skip only the intro copy — land on the Diagnostic Test page.
        if (currentPage < pages.lastIndex) {
            TextButton(
                onClick = { currentPage = pages.lastIndex },
                enabled = !skipping
            ) {
                Text(stringResource(R.string.skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DiagnosticFlow(
    diag: DiagnosticUiState,
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 28.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (diag.phase) {
            DiagnosticPhase.DONE -> {
                Spacer(Modifier.weight(1f))
                Text("Diagnostic complete", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Baseline accuracy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${(diag.baselineAccuracy * 100).toInt()}%", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Avg error: ${String.format("%.1f", diag.baselineErrorSemitones)} semitones", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Text("Start training", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
            }
            DiagnosticPhase.READY -> {
                Spacer(Modifier.weight(1f))
                Text("Diagnostic", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("Note ${diag.index + 1} of ${diag.total}", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = viewModel::playCurrent,
                    modifier = Modifier.size(132.dp).semantics { contentDescription = "Play the note" },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("\u25B6", fontSize = 40.sp) }
                Spacer(Modifier.height(20.dp))
                Text("Listen, then name the note.\nNo feedback until the end.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
            }
            DiagnosticPhase.INTRO -> Unit
            else -> {
                Spacer(Modifier.height(8.dp))
                Text("Which note was that?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    diag.choices.chunked(3).forEach { row ->
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
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}
