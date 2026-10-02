package com.stayfocused.app.ui.components

import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictScheduleEntity
import com.stayfocused.app.domain.ChallengeQuote
import com.stayfocused.app.domain.RandomTextChallengeEngine
import com.stayfocused.app.domain.RandomTextVerificationResult
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
 * Dialog for interactive on-demand arming of Strict Mode.
 */
@Composable
fun ArmStrictSessionDialog(
    profiles: List<FocusProfileEntity>,
    onDismiss: () -> Unit,
    onArm: (durationMinutes: Int, profileId: Long, challenge: String) -> Unit
) {
    val durationOptions = listOf(30, 60, 120, 240, 480)
    var selectedDurationMinutes by remember { mutableIntStateOf(120) }
    var selectedProfileId by remember { mutableLongStateOf(profiles.firstOrNull()?.id ?: 1L) }
    var selectedChallenge by remember { mutableStateOf("EXPIRATION_ONLY") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonkCard,
        title = {
            Text(
                text = "Arm Strict Mode",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = MonkText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Select focus duration:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    durationOptions.forEach { minutes ->
                        val isSelected = selectedDurationMinutes == minutes
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isSelected) MonkEmber else MonkCardAlt, RoundedCornerShape(8.dp))
                                .border(1.dp, if (isSelected) MonkEmber else MonkLine, RoundedCornerShape(8.dp))
                                .clickable { selectedDurationMinutes = minutes }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val label = if (minutes >= 60) "${minutes / 60}h" else "${minutes}m"
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MonkInk else MonkText
                            )
                        }
                    }
                }

                if (profiles.isNotEmpty()) {
                    Text(
                        text = "Focus Profile to lock:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        profiles.forEach { profile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProfileId = profile.id },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProfileId == profile.id,
                                    onClick = { selectedProfileId = profile.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = MonkEmber, unselectedColor = MonkMuted)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = profile.name, color = MonkText, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Text(
                    text = "Deactivation Challenge:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                )

                val challenges = listOf(
                    "EXPIRATION_ONLY" to "Timer Only (Locked until time elapses)",
                    "COOL_DOWN" to "24h Delay Safety Valve",
                    "RANDOM_TEXT" to "Random Stoic Quote (Type phrase to unlock)"
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    challenges.forEach { (type, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedChallenge = type },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedChallenge == type,
                                onClick = { selectedChallenge = type },
                                colors = RadioButtonDefaults.colors(selectedColor = MonkEmber, unselectedColor = MonkMuted)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = label, color = MonkText, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onArm(selectedDurationMinutes, selectedProfileId, selectedChallenge) },
                colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
            ) {
                Text("Arm Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MonkMuted)
            }
        }
    )
}

/**
 * Dialog for typing a Stoic quote challenge to disarm Strict Mode.
 */
@Composable
fun RandomTextChallengeDialog(
    quote: ChallengeQuote,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val engine = remember { RandomTextChallengeEngine() }
    val startTimeMs = remember { System.currentTimeMillis() }
    var userInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonkCard,
        title = {
            Text(
                text = "Stoic Reflection Challenge",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = MonkText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Type the stoic phrase verbatim to break dopamine cravings and confirm intentional disarm:",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MonkCardAlt, RoundedCornerShape(10.dp))
                        .border(1.dp, MonkLine, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = "\"${quote.text}\"",
                            fontFamily = FontFamily.Serif,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MonkEmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "- ${quote.author}",
                            fontSize = 12.sp,
                            color = MonkMuted,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    label = { Text("Type quote here", color = MonkMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val duration = System.currentTimeMillis() - startTimeMs
                    val result = engine.verifyInput(quote.text, userInput, duration)
                    when (result) {
                        is RandomTextVerificationResult.Success -> {
                            onSuccess()
                        }
                        is RandomTextVerificationResult.PasteDetected -> {
                            Toast.makeText(context, "Pasting detected! Please type the words mindfully.", Toast.LENGTH_SHORT).show()
                        }
                        is RandomTextVerificationResult.Mismatch -> {
                            Toast.makeText(context, "Quote does not match. Please verify spelling.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MonkSage, contentColor = MonkInk)
            ) {
                Text("Verify & Disarm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Strict Mode", color = MonkMuted)
            }
        }
    )
}

/**
 * Dialog for adding or editing a recurring Focus Schedule.
 */
@Composable
fun ScheduleConfigDialog(
    initialSchedule: StrictScheduleEntity? = null,
    profiles: List<FocusProfileEntity>,
    onDismiss: () -> Unit,
    onSave: (StrictScheduleEntity) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialSchedule?.name ?: "Workday Focus") }
    var daysMask by remember { mutableIntStateOf(initialSchedule?.daysOfWeekMask ?: 31) } // Default Mon-Fri
    var startTimeText by remember {
        mutableStateOf(String.format("%02d:%02d", (initialSchedule?.startMinuteOfDay ?: 540) / 60, (initialSchedule?.startMinuteOfDay ?: 540) % 60))
    }
    var endTimeText by remember {
        mutableStateOf(String.format("%02d:%02d", (initialSchedule?.endMinuteOfDay ?: 1020) / 60, (initialSchedule?.endMinuteOfDay ?: 1020) % 60))
    }
    var profileId by remember { mutableLongStateOf(initialSchedule?.profileId ?: profiles.firstOrNull()?.id ?: 1L) }
    var deactivationChallenge by remember { mutableStateOf(initialSchedule?.deactivationChallenge ?: "EXPIRATION_ONLY") }

    val days = listOf("M", "T", "W", "T", "F", "S", "S")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MonkCard,
        title = {
            Text(
                text = if (initialSchedule == null) "Add Focus Schedule" else "Edit Focus Schedule",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = MonkText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Schedule Name", color = MonkMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Active Days:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    days.forEachIndexed { index, dayLetter ->
                        val bit = 1 shl index
                        val isSelected = (daysMask and bit) != 0

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(if (isSelected) MonkSage else MonkCardAlt, CircleShape)
                                .border(1.dp, if (isSelected) MonkSage else MonkLine, CircleShape)
                                .clickable {
                                    daysMask = if (isSelected) daysMask and bit.inv() else daysMask or bit
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayLetter,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isSelected) MonkInk else MonkMuted
                            )
                        }
                    }
                }

                Text(
                    text = "Time Window:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start (HH:MM)", fontSize = 11.sp, color = MonkMuted)
                        OutlinedTextField(
                            value = startTimeText,
                            onValueChange = { startTimeText = it },
                            singleLine = true
                        )
                    }

                    Text("to", color = MonkMuted, modifier = Modifier.padding(top = 16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("End (HH:MM)", fontSize = 11.sp, color = MonkMuted)
                        OutlinedTextField(
                            value = endTimeText,
                            onValueChange = { endTimeText = it },
                            singleLine = true
                        )
                    }
                }

                if (profiles.isNotEmpty()) {
                    Text(
                        text = "Focus Profile:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        profiles.forEach { profile ->
                            val isSelected = profileId == profile.id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelected) MonkEmber else MonkCardAlt, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) MonkEmber else MonkLine, RoundedCornerShape(8.dp))
                                    .clickable { profileId = profile.id }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = profile.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MonkInk else MonkText
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Deactivation Challenge:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                )

                val challenges = listOf(
                    "EXPIRATION_ONLY" to "Timer Only",
                    "COOL_DOWN" to "24h Delay",
                    "RANDOM_TEXT" to "Quote Challenge"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    challenges.forEach { (type, label) ->
                        val isSelected = deactivationChallenge == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isSelected) MonkEmber else MonkCardAlt, RoundedCornerShape(8.dp))
                                .border(1.dp, if (isSelected) MonkEmber else MonkLine, RoundedCornerShape(8.dp))
                                .clickable { deactivationChallenge = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) MonkInk else MonkText
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (daysMask == 0) {
                        Toast.makeText(context, "Please select at least one active day", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val startParts = startTimeText.trim().split(":")
                    val endParts = endTimeText.trim().split(":")
                    if (startParts.size != 2 || endParts.size != 2) {
                        Toast.makeText(context, "Please enter valid times in HH:MM format", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val sH = startParts[0].toIntOrNull()
                    val sM = startParts[1].toIntOrNull()
                    val eH = endParts[0].toIntOrNull()
                    val eM = endParts[1].toIntOrNull()

                    if (sH == null || sM == null || sH !in 0..23 || sM !in 0..59 ||
                        eH == null || eM == null || eH !in 0..23 || eM !in 0..59
                    ) {
                        Toast.makeText(context, "Invalid time range. Hours 00..23, Mins 00..59", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val startMinTotal = sH * 60 + sM
                    val endMinTotal = eH * 60 + eM
                    if (startMinTotal == endMinTotal) {
                        Toast.makeText(context, "Start and end times cannot be identical", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val entity = StrictScheduleEntity(
                        id = initialSchedule?.id ?: 0L,
                        name = name.trim().ifEmpty { "Focus Schedule" },
                        daysOfWeekMask = daysMask,
                        startMinuteOfDay = startMinTotal,
                        endMinuteOfDay = endMinTotal,
                        profileId = profileId,
                        deactivationChallenge = deactivationChallenge,
                        isEnabled = initialSchedule?.isEnabled ?: true
                    )
                    onSave(entity)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MonkSage, contentColor = MonkInk)
            ) {
                Text("Save Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MonkMuted)
            }
        }
    )
}
