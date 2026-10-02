package com.stayfocused.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictScheduleEntity
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkCardAlt
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkInk
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText

/**
 * Card displaying and managing recurring focus schedules with anti-tamper lock enforcement.
 */
@Composable
fun FocusSchedulesCard(
    schedules: List<StrictScheduleEntity>,
    profiles: List<FocusProfileEntity>,
    isStrictModeActive: Boolean,
    onToggleSchedule: (scheduleId: Long, isEnabled: Boolean) -> Unit,
    onAddScheduleClick: () -> Unit,
    onEditScheduleClick: (StrictScheduleEntity) -> Unit,
    onDeleteScheduleClick: (scheduleId: Long) -> Unit,
    onLockedActionAttempt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MonkCard),
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MonkLine, shape = RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recurring Focus Schedules",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MonkText
                        )
                    )
                    Text(
                        text = "Automatically arms Strict Mode during routine deep work hours.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                    )
                }

                if (isStrictModeActive) {
                    Box(
                        modifier = Modifier
                            .background(MonkDanger.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .border(1.dp, MonkDanger.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "LOCKED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonkDanger
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (schedules.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MonkCardAlt, RoundedCornerShape(12.dp))
                        .border(1.dp, MonkLine, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No focus schedules configured yet. Add your daily routine to stay consistent.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted),
                        lineHeight = 18.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    schedules.forEach { schedule ->
                        val profileName = profiles.find { it.id == schedule.profileId }?.name ?: "Profile"
                        val startH = schedule.startMinuteOfDay / 60
                        val startM = schedule.startMinuteOfDay % 60
                        val endH = schedule.endMinuteOfDay / 60
                        val endM = schedule.endMinuteOfDay % 60
                        val timeStr = String.format("%02d:%02d – %02d:%02d", startH, startM, endH, endM)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MonkCardAlt, RoundedCornerShape(12.dp))
                                .border(1.dp, MonkLine, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = schedule.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MonkText
                                        )
                                        Text(
                                            text = "$timeStr • $profileName",
                                            fontSize = 12.sp,
                                            color = MonkMuted
                                        )
                                    }

                                    Switch(
                                        checked = schedule.isEnabled,
                                        onCheckedChange = { isChecked ->
                                            if (isStrictModeActive) {
                                                onLockedActionAttempt()
                                            } else {
                                                onToggleSchedule(schedule.id, isChecked)
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MonkSage,
                                            checkedTrackColor = MonkCard,
                                            uncheckedThumbColor = MonkMuted,
                                            uncheckedTrackColor = MonkCardAlt
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Days of week row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    dayLetters.forEachIndexed { idx, letter ->
                                        val isActive = (schedule.daysOfWeekMask and (1 shl idx)) != 0
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(
                                                    if (isActive) MonkSage.copy(alpha = 0.25f) else Color.Transparent,
                                                    CircleShape
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isActive) MonkSage else MonkLine.copy(alpha = 0.5f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = letter,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isActive) MonkSage else MonkMuted
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // Challenge tag
                                    val challengeTag = when (schedule.deactivationChallenge) {
                                        "RANDOM_TEXT" -> "Quote Challenge"
                                        "COOL_DOWN" -> "24h Delay"
                                        else -> "Timer Only"
                                    }
                                    Text(
                                        text = challengeTag,
                                        fontSize = 11.sp,
                                        color = MonkEmber,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }

                                if (!isStrictModeActive) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { onEditScheduleClick(schedule) }) {
                                            Text("Edit", fontSize = 12.sp, color = MonkEmber)
                                        }
                                        TextButton(onClick = { onDeleteScheduleClick(schedule.id) }) {
                                            Text("Delete", fontSize = 12.sp, color = MonkDanger)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    if (isStrictModeActive) {
                        onLockedActionAttempt()
                    } else {
                        onAddScheduleClick()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStrictModeActive) MonkCardAlt else MonkSage,
                    contentColor = if (isStrictModeActive) MonkMuted else MonkInk
                )
            ) {
                Text(if (isStrictModeActive) "Schedules Locked in Strict Mode" else "+ Add Focus Schedule")
            }
        }
    }
}
