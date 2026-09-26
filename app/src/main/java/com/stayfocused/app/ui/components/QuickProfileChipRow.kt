package com.stayfocused.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkInk
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText

/**
 * Horizontal chip row displaying all available Focus Profiles with live activation states.
 */
@Composable
fun QuickProfileChipRow(
    profiles: List<FocusProfileEntity>,
    onProfileClick: (FocusProfileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Focus Profiles",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MonkText
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "• Tap to activate",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MonkMuted,
                    fontSize = 11.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (profiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MonkLine, RoundedCornerShape(12.dp))
                    .background(MonkCard)
                    .padding(14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "No focus profiles configured yet",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileChipItem(
                        profile = profile,
                        onClick = { onProfileClick(profile) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileChipItem(
    profile: FocusProfileEntity,
    onClick: () -> Unit
) {
    val isActive = profile.isActive
    val shape = RoundedCornerShape(12.dp)

    val borderColor = if (isActive) MonkEmber else MonkLine
    val containerColor = if (isActive) MonkEmber.copy(alpha = 0.16f) else MonkCard

    Row(
        modifier = Modifier
            .clip(shape)
            .border(width = if (isActive) 1.5.dp else 1.dp, color = borderColor, shape = shape)
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MonkSage, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) MonkText else MonkMuted
            )
        )

        if (profile.isStrictMode) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Strict Mode",
                tint = if (isActive) MonkEmber else MonkMuted,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

/**
 * Confirmation dialog displayed when attempting to switch away from a profile currently in Strict Mode.
 */
@Composable
fun StrictProfileSwitchDialog(
    outgoingProfile: FocusProfileEntity,
    targetProfile: FocusProfileEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonkCard,
        title = {
            Text(
                text = "Switch from Strict Mode?",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MonkText
                )
            )
        },
        text = {
            Text(
                text = "Profile \"${outgoingProfile.name}\" is actively enforcing Strict Mode. Switching to \"${targetProfile.name}\" will end strict enforcement on the previous profile rules. Are you sure you want to proceed?",
                style = MaterialTheme.typography.bodyMedium.copy(color = MonkMuted)
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MonkDanger,
                    contentColor = MonkInk
                )
            ) {
                Text("Confirm Switch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Stay Focused", color = MonkMuted)
            }
        }
    )
}
