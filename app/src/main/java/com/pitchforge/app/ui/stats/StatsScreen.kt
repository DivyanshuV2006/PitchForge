package com.pitchforge.app.ui.stats

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Intent
import com.pitchforge.app.domain.NoteName
import com.pitchforge.app.ui.components.AccuracyAreaChart
import com.pitchforge.app.ui.components.FloatingNavBar
import com.pitchforge.app.ui.components.FloatingNavDestination
import com.pitchforge.app.ui.components.NoteMasteryRadarChart
import com.pitchforge.app.ui.components.SectionHeader
import com.pitchforge.app.ui.components.SettingsIconButton
import com.pitchforge.app.ui.components.StatChip
import com.pitchforge.app.ui.components.rememberSmoothFlingBehavior
import com.pitchforge.app.ui.dashboard.DashboardUiState
import com.pitchforge.app.ui.dashboard.DashboardViewModel
import com.pitchforge.app.ui.dashboard.NoteCollectionState
import com.pitchforge.app.ui.dashboard.WeeklyShareUi

private enum class AccuracyChartMode { Trend, Radar }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChallenges: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val flingBehavior = rememberSmoothFlingBehavior()
    var chartMode by remember { mutableStateOf(AccuracyChartMode.Trend) }

    val notes = remember { NoteName.entries.sortedBy { it.semitone } }
    val masteryByNote = remember(state.collection, state.noteAccuracy) {
        notes.map { note ->
            val slot = state.collection.find { it.note == note }
            when (slot?.state) {
                NoteCollectionState.MASTERED -> 1f
                NoteCollectionState.LEARNING -> state.noteAccuracy[note]?.coerceIn(0f, 1f) ?: 0f
                NoteCollectionState.LOCKED, null -> state.noteAccuracy[note]?.coerceIn(0f, 1f) ?: 0f
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats", fontWeight = FontWeight.Bold) },
                actions = {
                    SettingsIconButton(onClick = onOpenSettings)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        },
        bottomBar = {
            FloatingNavBar(
                selected = FloatingNavDestination.Stats,
                onSelect = { dest ->
                    when (dest) {
                        FloatingNavDestination.Home -> onOpenHome()
                        FloatingNavDestination.Stats -> Unit
                        FloatingNavDestination.Challenges -> onOpenChallenges()
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatChip("${state.currentStreak}", "day streak", Modifier.weight(1f))
                    StatChip("${state.longestStreak}", "best streak", Modifier.weight(1f))
                    StatChip("${state.totalLessons}", "lessons", Modifier.weight(1f))
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatChip(
                        if (state.totalLessons > 0) "${(state.overallAccuracy * 100).toInt()}%" else "—",
                        "accuracy",
                        Modifier.weight(1f)
                    )
                    StatChip("${state.totalPracticeMinutes}m", "practiced", Modifier.weight(1f))
                    StatChip("${state.masteredCount}/12", "mastered", Modifier.weight(1f))
                }
            }
            item { LevelCard(state) }
            state.weeklyShare?.let { week ->
                item { WeeklyShareCard(week) }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.padding(20.dp)) {
                        SectionHeader(
                            title = if (chartMode == AccuracyChartMode.Trend) {
                                "Accuracy trend"
                            } else {
                                "Note mastery"
                            },
                            subtitle = if (chartMode == AccuracyChartMode.Trend) {
                                "Last ${state.accuracyOverTime.size} sessions"
                            } else {
                                "Accuracy across all 12 notes"
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = chartMode == AccuracyChartMode.Trend,
                                onClick = { chartMode = AccuracyChartMode.Trend },
                                label = { Text("Trend") }
                            )
                            FilterChip(
                                selected = chartMode == AccuracyChartMode.Radar,
                                onClick = { chartMode = AccuracyChartMode.Radar },
                                label = { Text("Radar") }
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        when (chartMode) {
                            AccuracyChartMode.Trend -> {
                                if (state.accuracyOverTime.size < 2) {
                                    Text(
                                        "Complete a few lessons to see your trend.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    AccuracyAreaChart(state.accuracyOverTime.map { it.accuracy })
                                }
                            }
                            AccuracyChartMode.Radar -> {
                                if (masteryByNote.all { it <= 0f }) {
                                    Text(
                                        "Practice a few notes to fill in this radar.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    NoteMasteryRadarChart(
                                        labels = notes.map { it.label },
                                        values = masteryByNote
                                    )
                                }
                            }
                        }
                    }
                }
            }
            state.baselineAccuracy?.let { baseline ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            SectionHeader("Checkups", "Baseline & monthly")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Baseline ${(baseline * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            state.latestCheckupAccuracy?.let { latest ->
                                Text(
                                    "Latest checkup ${(latest * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            state.generalizationScore?.let { g ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            SectionHeader("Generalization", "Untrained instrument")
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${(g * 100).toInt()}% on an instrument you've never practiced",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyShareCard(week: WeeklyShareUi) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("This week", "Shareable progress card")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChip("${week.streak}", "streak", Modifier.weight(1f))
                StatChip("${week.lessonsThisWeek}", "lessons", Modifier.weight(1f))
                StatChip(
                    week.accuracyPercent?.let { "$it%" } ?: "—",
                    "accuracy",
                    Modifier.weight(1f)
                )
            }
            Text(
                "${week.mastered}/12 notes mastered",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "ChromaP this week")
                        putExtra(Intent.EXTRA_TEXT, week.shareText)
                    }
                    context.startActivity(Intent.createChooser(send, "Share weekly progress"))
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Share week", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LevelCard(state: DashboardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${state.level}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Level ${state.level}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${state.xpIntoLevel} / ${state.xpForNextLevel} XP",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(state.levelProgress)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${state.totalXp} XP total \u00B7 ${state.xpForNextLevel - state.xpIntoLevel} XP to level ${state.level + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val nextTheme = com.pitchforge.app.domain.CosmeticTheme.nextUnlock(state.level)
                if (nextTheme != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Theme unlock: ${nextTheme.displayName} at level ${nextTheme.unlockLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
