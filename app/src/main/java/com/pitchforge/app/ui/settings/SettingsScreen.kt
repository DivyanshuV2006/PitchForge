package com.pitchforge.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pitchforge.app.domain.CosmeticTheme
import com.pitchforge.app.domain.PracticeTimingPolicy
import com.pitchforge.app.domain.TimbreCatalog
import com.pitchforge.app.ui.components.rememberSmoothFlingBehavior
import com.pitchforge.app.ui.theme.swatchPrimary
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val ui by viewModel.state.collectAsState()
    val settings = ui.settings
    val playerLevel = ui.playerLevel
    val listState = rememberLazyListState()
    val flingBehavior = rememberSmoothFlingBehavior()
    val selectedTheme = CosmeticTheme.fromId(settings.themeId)
    val nextTheme = CosmeticTheme.nextUnlock(playerLevel)
    var showBedtimePicker by remember { mutableStateOf(false) }
    val bedtime = remember(settings.bedtime) {
        PracticeTimingPolicy.parseLocalTime(settings.bedtime)
    }
    val bedtimeLabel = remember(bedtime) {
        bedtime.format(DateTimeFormatter.ofPattern("h:mm a"))
    }
    val nudgeLabel = remember(bedtime) {
        PracticeTimingPolicy.secondSessionTargetFromBedtime(settings.bedtime)
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    if (showBedtimePicker) {
        BedtimePickerDialog(
            initial = bedtime,
            onDismiss = { showBedtimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.setBedtime(hour, minute)
                showBedtimePicker = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
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
                SettingsCard(
                    "Color themes",
                    "Earn XP to unlock a new look every ${CosmeticTheme.LEVELS_PER_THEME} levels. " +
                        "You're level $playerLevel." +
                        (nextTheme?.let { " Next: ${it.displayName} at level ${it.unlockLevel}." } ?: " All themes unlocked.")
                ) {
                    CosmeticTheme.entries.forEach { theme ->
                        ThemeRow(
                            theme = theme,
                            selected = theme == selectedTheme,
                            unlocked = theme.isUnlocked(playerLevel),
                            onSelect = { viewModel.setTheme(theme) }
                        )
                    }
                }
            }
            item {
                SettingsCard(
                    "Active timbres",
                    "Octaves unlock first when a note is solid. Extra instruments unlock later, one at a time — each only after you’ve mastered the previous one on that note. Also powers Mixed Timbre Chaos."
                ) {
                    TimbreCatalog.SELECTABLE.forEach { timbre ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(timbre.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = timbre in settings.activeTimbres,
                                onCheckedChange = { viewModel.toggleTimbre(timbre, it) }
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard("Volume", null) {
                    Slider(
                        value = settings.volume,
                        onValueChange = { viewModel.setVolume(it) },
                        valueRange = 0f..1f
                    )
                }
            }
            item {
                SettingsCard(
                    "Notifications",
                    "Smart reminders at your usual practice time, plus alerts when notes are due for spaced review. Quiet hours 10 PM–8 AM (bedtime encore is exempt)."
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Practice reminders", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = settings.notificationsEnabled,
                            onCheckedChange = { viewModel.setNotifications(it) }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Bedtime second lesson", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Optional. After one lesson, nudge ~10 min before you sleep.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.bedtimeEnabled,
                            onCheckedChange = { viewModel.setBedtimeEnabled(it) },
                            enabled = settings.notificationsEnabled
                        )
                    }
                    if (settings.bedtimeEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(
                                    enabled = settings.notificationsEnabled,
                                    role = Role.Button,
                                    onClick = { showBedtimePicker = true }
                                )
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("I usually sleep at", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Encore around $nudgeLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                bedtimeLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard("Light / dark", null) {
                    listOf("system", "light", "dark").forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                            RadioButton(
                                selected = settings.darkMode == mode,
                                onClick = { viewModel.setDarkMode(mode) }
                            )
                        }
                    }
                }
            }
            item {
                SettingsCard("About the science", null) {
                    Text(
                        "ChromaP's method is drawn from published research on adult absolute-pitch acquisition (Wong et al., 2019, 2020, 2025). Absolute pitch is learnable-in-part by most adults with sustained training, and fully learnable by some — only about 14% of trained adults in the studies reached all 12 pitches. No result is guaranteed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedtimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bedtime") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ThemeRow(
    theme: CosmeticTheme,
    selected: Boolean,
    unlocked: Boolean,
    onSelect: () -> Unit
) {
    val border = when {
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, border, MaterialTheme.shapes.medium)
            .clickable(enabled = unlocked, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(theme.swatchPrimary().copy(alpha = if (unlocked) 1f else 0.35f))
        )
        Column(Modifier.weight(1f)) {
            Text(
                theme.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (unlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                }
            )
            Text(
                if (unlocked) theme.blurb else "Unlocks at level ${theme.unlockLevel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (unlocked) 1f else 0.7f)
            )
        }
        if (unlocked) {
            RadioButton(selected = selected, onClick = onSelect)
        } else {
            Text(
                "Lv ${theme.unlockLevel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
