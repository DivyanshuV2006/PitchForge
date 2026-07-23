package com.pitchforge.app.ui.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pitchforge.app.domain.InterTrialPolicy
import com.pitchforge.app.domain.MissionEngine
import com.pitchforge.app.domain.NoteName
import com.pitchforge.app.ui.components.FloatingNavBar
import com.pitchforge.app.ui.components.FloatingNavDestination
import com.pitchforge.app.ui.components.GoalRing
import com.pitchforge.app.ui.components.SectionHeader
import com.pitchforge.app.ui.components.SettingsIconButton
import com.pitchforge.app.ui.components.rememberSmoothFlingBehavior
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartLesson: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenChallenge: () -> Unit,
    onOpenCheckup: () -> Unit,
    onOpenGeneralization: () -> Unit,
    onOpenRetention: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val flingBehavior = rememberSmoothFlingBehavior()
    var showHardDoseDialog by remember { mutableStateOf(false) }
    var showCooldownDialog by remember { mutableStateOf(false) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val gate = InterTrialPolicy.sessionGate(
        lessonsCompletedToday = state.lessonsCompletedToday,
        lastCompletedAtMs = state.lastLessonCompletedAtMs,
        nowMs = nowMs
    )
    val cooldownLeft = InterTrialPolicy.cooldownRemainingMs(state.lastLessonCompletedAtMs, nowMs)

    // Refresh missions whenever the dashboard is shown (e.g. after finishing a lesson).
    LaunchedEffect(Unit) {
        viewModel.refreshMissions()
        viewModel.refreshTrainedTimbres()
    }

    // Tick the cooldown clock once per second while waiting.
    LaunchedEffect(gate, state.lastLessonCompletedAtMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            val left = InterTrialPolicy.cooldownRemainingMs(state.lastLessonCompletedAtMs, nowMs)
            val capped = state.lessonsCompletedToday >= InterTrialPolicy.DAILY_LESSON_HARD_CAP
            if (capped || left <= 0L) break
            delay(1_000)
        }
        nowMs = System.currentTimeMillis()
    }

    // Ask for notification permission post-onboarding, once value has been shown (§2.1).
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (showCooldownDialog) {
        AlertDialog(
            onDismissRequest = { showCooldownDialog = false },
            title = { Text("Session cooldown") },
            text = {
                Text(
                    "Wait ${InterTrialPolicy.formatCooldown(cooldownLeft)} before the next lesson. " +
                        "Spacing sessions (~20 min) beats back-to-back cramming. " +
                        "${state.lessonsCompletedToday}/${InterTrialPolicy.DAILY_LESSON_HARD_CAP} used today."
                )
            },
            confirmButton = {
                TextButton(onClick = { showCooldownDialog = false }) { Text("OK") }
            }
        )
    }
    if (showHardDoseDialog) {
        AlertDialog(
            onDismissRequest = { showHardDoseDialog = false },
            title = { Text("Daily dose complete") },
            text = {
                Text(
                    "Cap is ${InterTrialPolicy.DAILY_LESSON_HARD_CAP} adaptive lessons per day. " +
                        "Come back tomorrow — challenges and probes are still available."
                )
            },
            confirmButton = {
                TextButton(onClick = { showHardDoseDialog = false }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("PitchForge", fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
                },
                actions = {
                    SettingsIconButton(onClick = onOpenSettings)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            FloatingNavBar(
                selected = FloatingNavDestination.Home,
                onSelect = { dest ->
                    when (dest) {
                        FloatingNavDestination.Home -> Unit
                        FloatingNavDestination.Stats -> onOpenStats()
                        FloatingNavDestination.Challenges -> onOpenChallenge()
                    }
                }
            )
        },
        floatingActionButton = {
            val blocked = gate != InterTrialPolicy.SessionGate.AVAILABLE
            ExtendedFloatingActionButton(
                onClick = {
                    when (gate) {
                        InterTrialPolicy.SessionGate.DOSE_COMPLETE -> showHardDoseDialog = true
                        InterTrialPolicy.SessionGate.COOLDOWN -> showCooldownDialog = true
                        InterTrialPolicy.SessionGate.AVAILABLE -> onStartLesson()
                    }
                },
                containerColor = if (blocked) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (blocked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            ) {
                Text(
                    when (gate) {
                        InterTrialPolicy.SessionGate.DOSE_COMPLETE -> "Dose complete"
                        InterTrialPolicy.SessionGate.COOLDOWN ->
                            "Wait ${InterTrialPolicy.formatCooldown(cooldownLeft)}"
                        InterTrialPolicy.SessionGate.AVAILABLE -> {
                            val focus = state.nextNoteFocus?.note?.label
                            when {
                                focus != null -> "Continue · $focus"
                                state.lessonsCompletedToday > 0 ->
                                    "Start Lesson · ${state.lessonsCompletedToday}/${InterTrialPolicy.DAILY_LESSON_HARD_CAP}"
                                else -> "Start Lesson"
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 96.dp)
        ) {
            // Goal hero
            item { GoalHero(state) }

            state.plateauMessage?.let { msg ->
                item { PlateauCallout(msg) }
            }

            state.weakNote?.let { weak ->
                item { WeakNoteCallout(weak) }
            }

            // 12-note collection + accuracy in one grid
            if (state.collection.isNotEmpty()) {
                item {
                    DashboardCard {
                        SectionHeader(
                            "Your 12 notes",
                            "Percent = how often you name it correctly"
                        )
                        Spacer(Modifier.height(12.dp))
                        NoteCollectionGrid(
                            collection = state.collection,
                            noteAccuracy = state.noteAccuracy
                        )
                    }
                }
            }

            if (state.retentionDueNotes.isNotEmpty()) {
                item {
                    Surface(
                        onClick = onOpenRetention,
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Retention check due",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Confirm you still have: ${state.retentionDueNotes.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("\u2192", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (state.generalizationDue) {
                item {
                    Surface(
                        onClick = onOpenGeneralization,
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Generalization probe due",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Name notes on an instrument you haven't practiced — doesn't affect training.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("\u2192", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                DashboardCard {
                    SectionHeader("Daily missions")
                    Spacer(Modifier.height(10.dp))
                    if (state.missions.isEmpty()) {
                        EmptyHint("Loading missions…")
                    } else {
                        state.missions.forEach { m ->
                            val label = runCatching {
                                MissionEngine.MissionType.valueOf(m.missionType).description
                            }.getOrDefault(
                                m.missionType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                            )
                            MissionRow(
                                label = label,
                                progress = m.progress,
                                target = m.target,
                                completed = m.completed,
                                xpReward = MissionEngine.XP_REWARD
                            )
                        }
                    }
                }
            }

            if (state.checkupDue) {
                item {
                    Surface(
                        onClick = onOpenCheckup,
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Monthly checkup due",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "End-of-month measure — doesn't affect training stats.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("\u2192", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalHero(state: DashboardUiState) {
    val heroBrush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceContainer,
            MaterialTheme.colorScheme.surface
        )
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .background(heroBrush)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GoalRing(value = state.masteredCount, total = 12, caption = "NOTES MASTERED")
            Spacer(Modifier.height(16.dp))
            Text("Absolute pitch goal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                when {
                    state.masteredCount >= 12 -> "All 12 — you built a full chromatic set."
                    state.masteredCount == 0 -> "Master a note at ≥95% accuracy over 3 days to unlock it here."
                    else -> "${12 - state.masteredCount} notes to go — hold ≥95% over the last 3 days to master the next one."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlateauCallout(message: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Keep going",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun DashboardCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
private fun WeakNoteCallout(note: NoteName) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("!", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.size(12.dp))
            Column {
                Text("Focus note: ${note.label}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                Text("You'll see this one more often until it improves.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun MissionRow(label: String, progress: Int, target: Int, completed: Boolean, xpReward: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(22.dp).background(
                if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            if (completed) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "+$xpReward XP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            if (completed) "Done" else "$progress/$target",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NoteCollectionGrid(
    collection: List<NoteSlot>,
    noteAccuracy: Map<NoteName, Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        collection.chunked(4).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { slot ->
                    NoteCollectionCell(
                        slot = slot,
                        accuracy = noteAccuracy[slot.note],
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCollectionCell(
    slot: NoteSlot,
    accuracy: Float?,
    modifier: Modifier = Modifier
) {
    val heat = accuracyHeatColor(accuracy)
    val bg = when (slot.state) {
        NoteCollectionState.MASTERED -> heat ?: MaterialTheme.colorScheme.primary
        NoteCollectionState.LEARNING -> heat ?: MaterialTheme.colorScheme.secondaryContainer
        NoteCollectionState.LOCKED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val fg = when {
        slot.state == NoteCollectionState.LOCKED ->
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        heat != null || slot.state == NoteCollectionState.MASTERED -> Color.White
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val status = when (slot.state) {
        NoteCollectionState.MASTERED -> "Mastered"
        NoteCollectionState.LEARNING -> "Learning"
        NoteCollectionState.LOCKED -> "Locked"
    }
    val percentLabel = accuracy?.let { "${(it * 100).toInt()}%" } ?: "—"
    val accBit = accuracy?.let { " · ${(it * 100).toInt()}%" }.orEmpty()

    Surface(
        color = bg,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .height(56.dp)
            .semantics { contentDescription = "${slot.note.label}: $status$accBit" }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    slot.note.label,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    percentLabel,
                    color = fg.copy(alpha = if (accuracy == null) 0.7f else 1f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Accuracy tint for active/mastered notes; null when there's no signal yet. */
private fun accuracyHeatColor(accuracy: Float?): Color? = when {
    accuracy == null -> null
    accuracy >= 0.9f -> Color(0xFF2E7D32)
    accuracy >= 0.7f -> Color(0xFF9E9D24)
    accuracy >= 0.4f -> Color(0xFFEF6C00)
    else -> Color(0xFFC62828)
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
