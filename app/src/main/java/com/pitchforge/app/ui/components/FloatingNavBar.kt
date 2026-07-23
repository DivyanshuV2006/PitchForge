package com.pitchforge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class FloatingNavDestination {
    Home,
    Stats,
    Challenges;

    val label: String
        get() = name

    val icon: ImageVector
        get() = when (this) {
            Home -> Icons.Rounded.Home
            Stats -> Icons.Rounded.BarChart
            Challenges -> Icons.Rounded.EmojiEvents
        }
}

@Composable
fun SettingsIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = "Settings",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * iOS-style tab bar: full-width frosted dock with icons and tiny labels,
 * tinted when selected. Sits under the daily-practice FAB.
 */
@Composable
fun FloatingNavBar(
    selected: FloatingNavDestination,
    onSelect: (FloatingNavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val frosted = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    val hairline = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(frosted)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(hairline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            FloatingNavDestination.entries.forEach { dest ->
                IosTabItem(
                    destination = dest,
                    selected = dest == selected,
                    onClick = { onSelect(dest) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun IosTabItem(
    destination: FloatingNavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }

    Column(
        modifier = modifier
            .semantics {
                this.selected = selected
                contentDescription = destination.label
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = destination.label,
            color = tint,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = (-0.1).sp
        )
    }
}
