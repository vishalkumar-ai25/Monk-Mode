package com.stayfocused.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.ClipboardManager
import android.view.WindowManager
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
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

import com.stayfocused.app.ui.TimeFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Dialog for interactive on-demand arming of Strict Mode with multi-day presets
 * and an exact expiration date & time picker matching the original Stay Focused UX.
 */
@Composable
fun ArmStrictSessionDialog(
    profiles: List<FocusProfileEntity>,
    onDismiss: () -> Unit,
    onArm: (targetEndTimeMs: Long, profileId: Long, challenge: String) -> Unit
) {
    val context = LocalContext.current
    val zoneId = remember { ZoneId.systemDefault() }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Presets, 1: Expiration Time

    // Preset options (hours & multi-day commitments)
    val hourOptions = listOf(30L to "30m", 60L to "1h", 120L to "2h", 240L to "4h", 480L to "8h", 720L to "12h")
    val dayOptions = listOf(
        1L to "1 day",
        2L to "2 days",
        7L to "7 days",
        15L to "15 days",
        30L to "30 days",
        45L to "45 days",
        60L to "60 days",
        75L to "75 days",
        90L to "90 days"
    )

    var selectedPresetMinutes by remember { mutableLongStateOf(120L) } // default 2h

    // Expiration date/time picker state
    val currentZdt = remember { ZonedDateTime.now(zoneId) }
    var selectedDayOffset by remember { mutableIntStateOf(1) } // 0=Today, 1=Tomorrow, 7=In a week
    var expirationHour by remember { mutableIntStateOf(currentZdt.hour) }
    var expirationMinute by remember { mutableIntStateOf((currentZdt.minute / 5) * 5) }

    val calculatedTargetEndTimeMs = remember(selectedTab, selectedPresetMinutes, selectedDayOffset, expirationHour, expirationMinute) {
        if (selectedTab == 0) {
            System.currentTimeMillis() + (selectedPresetMinutes * 60 * 1000L)
        } else {
            val targetDate = LocalDate.now(zoneId).plusDays(selectedDayOffset.toLong())
            val targetTime = LocalTime.of(
                expirationHour.coerceIn(0, 23),
                expirationMinute.coerceIn(0, 59)
            )
            val zdt = ZonedDateTime.of(targetDate, targetTime, zoneId)
            val computedMs = zdt.toInstant().toEpochMilli()
            if (computedMs <= System.currentTimeMillis()) {
                // If computed time is in the past (e.g. today earlier hour), push to at least 15 mins from now
                System.currentTimeMillis() + 15 * 60 * 1000L
            } else {
                computedMs
            }
        }
    }

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Mode Toggle Tabs (Presets vs Expiration Time)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MonkCardAlt, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selectedTab == 0) MonkEmber else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Duration Presets",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) MonkInk else MonkMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selectedTab == 1) MonkEmber else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Expiration Time",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) MonkInk else MonkMuted
                        )
                    }
                }

                if (selectedTab == 0) {
                    // TAB 0: PRESET CHIPS
                    Text(
                        text = "Hours:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MonkMuted)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        hourOptions.forEach { (minutes, label) ->
                            val isSelected = selectedPresetMinutes == minutes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelected) MonkEmber else MonkCardAlt, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) MonkEmber else MonkLine, RoundedCornerShape(8.dp))
                                    .clickable { selectedPresetMinutes = minutes }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MonkInk else MonkText
                                )
                            }
                        }
                    }

                    Text(
                        text = "Multi-Day Commitments:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MonkMuted)
                    )
                    // Row 1: 1d, 2d, 7d, 15d
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dayOptions.take(4).forEach { (days, label) ->
                            val minutes = days * 1440L
                            val isSelected = selectedPresetMinutes == minutes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelected) MonkSage else MonkCardAlt, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) MonkSage else MonkLine, RoundedCornerShape(8.dp))
                                    .clickable { selectedPresetMinutes = minutes }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MonkInk else MonkText
                                )
                            }
                        }
                    }
                    // Row 2: 30d, 45d, 60d, 75d, 90d
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dayOptions.drop(4).forEach { (days, label) ->
                            val minutes = days * 1440L
                            val isSelected = selectedPresetMinutes == minutes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelected) MonkSage else MonkCardAlt, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) MonkSage else MonkLine, RoundedCornerShape(8.dp))
                                    .clickable { selectedPresetMinutes = minutes }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${days}d",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MonkInk else MonkText
                                )
                            }
                        }
                    }
                } else {
                    // TAB 1: EXACT EXPIRATION TIME PICKER
                    Text(
                        text = "Target Date:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MonkMuted)
                    )
                    val quickDates = listOf(
                        0 to "Today",
                        1 to "Tomorrow",
                        2 to "+2 days",
                        7 to "+7 days",
                        15 to "+15 days",
                        30 to "+30 days",
                        90 to "+90 days"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        quickDates.take(4).forEach { (offset, label) ->
                            val isSelected = selectedDayOffset == offset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelected) MonkEmber else MonkCardAlt, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) MonkEmber else MonkLine, RoundedCornerShape(8.dp))
                                    .clickable { selectedDayOffset = offset }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MonkInk else MonkText
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        quickDates.drop(4).forEach { (offset, label) ->
                            val isSelected = selectedDayOffset == offset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelected) MonkEmber else MonkCardAlt, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) MonkEmber else MonkLine, RoundedCornerShape(8.dp))
                                    .clickable { selectedDayOffset = offset }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MonkInk else MonkText
                                )
                            }
                        }
                    }

                    Text(
                        text = "Target Time (24h):",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MonkMuted)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hour Selector
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(MonkCardAlt, RoundedCornerShape(8.dp))
                                .border(1.dp, MonkLine, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "-",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MonkEmber,
                                modifier = Modifier.clickable { expirationHour = (expirationHour - 1 + 24) % 24 }
                            )
                            Text(
                                text = String.format("%02d hr", expirationHour),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MonkText
                            )
                            Text(
                                text = "+",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MonkEmber,
                                modifier = Modifier.clickable { expirationHour = (expirationHour + 1) % 24 }
                            )
                        }

                        // Minute Selector
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .background(MonkCardAlt, RoundedCornerShape(8.dp))
                                .border(1.dp, MonkLine, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "-",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MonkEmber,
                                modifier = Modifier.clickable { expirationMinute = (expirationMinute - 5 + 60) % 60 }
                            )
                            Text(
                                text = String.format("%02d min", expirationMinute),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MonkText
                            )
                            Text(
                                text = "+",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MonkEmber,
                                modifier = Modifier.clickable { expirationMinute = (expirationMinute + 5) % 60 }
                            )
                        }
                    }
                }

                // Dynamic Expiration Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MonkCardAlt, RoundedCornerShape(10.dp))
                        .border(1.dp, MonkEmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "Strict Mode will automatically deactivate at:",
                            fontSize = 11.sp,
                            color = MonkMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = TimeFormatter.formatExactDateTime(calculatedTargetEndTimeMs, zoneId),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MonkEmber
                        )
                        val remainingMs = maxOf(0L, calculatedTargetEndTimeMs - System.currentTimeMillis())
                        Text(
                            text = "${TimeFormatter.formatRemainingTime(remainingMs)} remaining",
                            fontSize = 11.sp,
                            color = MonkSage
                        )
                    }
                }

                if (profiles.isNotEmpty()) {
                    Text(
                        text = "Focus Profile to lock:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                Text(text = profile.name, color = MonkText, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Text(
                    text = "Deactivation Challenge:",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                )

                val challenges = listOf(
                    "EXPIRATION_ONLY" to "Timer Only (Locked until time elapses)",
                    "COOL_DOWN" to "24h Delay Safety Valve",
                    "RANDOM_TEXT" to "Random Stoic Quote"
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                            Text(text = label, color = MonkText, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onArm(calculatedTargetEndTimeMs, selectedProfileId, selectedChallenge) },
                colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
            ) {
                Text("Arm Strict Mode")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MonkMuted)
            }
        }
    )
}

private class NoPasteTextToolbar(private val base: TextToolbar) : TextToolbar {
    override val status: TextToolbarStatus get() = base.status
    override fun hide() = base.hide()
    override fun showMenu(
        rect: androidx.compose.ui.geometry.Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        // Omit onPasteRequested so Paste is stripped from context menus
        base.showMenu(rect, onCopyRequested, null, onCutRequested, onSelectAllRequested)
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Hardened Dialog for typing a Stoic quote challenge to disarm Strict Mode.
 * Protected with FLAG_SECURE (display buffer blacking), Compose semantics clearing (anti-assist scraping),
 * NoPasteTextToolbar context menu stripping, shortcut key interception, and keystroke cadence telemetry.
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
    var keystrokeCount by remember { mutableIntStateOf(0) }

    // Host Window FLAG_SECURE enforcement (covers screenshots, 3-finger swipe, and screen recording)
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val window = activity?.window
        val originalFlags = window?.attributes?.flags ?: 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (originalFlags and WindowManager.LayoutParams.FLAG_SECURE == 0) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    val baseToolbar = LocalTextToolbar.current
    val noPasteToolbar = remember(baseToolbar) { NoPasteTextToolbar(baseToolbar) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
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
                        // Semantics Shield: Zero accessibility text exposure + DisableSelection
                        DisableSelection {
                            Text(
                                text = "\"${quote.text}\"",
                                fontFamily = FontFamily.Serif,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MonkEmber,
                                modifier = Modifier.clearAndSetSemantics { }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        DisableSelection {
                            Text(
                                text = "- ${quote.author}",
                                fontSize = 12.sp,
                                color = MonkMuted,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .clearAndSetSemantics { }
                            )
                        }
                    }
                }

                CompositionLocalProvider(LocalTextToolbar provides noPasteToolbar) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { newValue ->
                            keystrokeCount++
                            userInput = newValue
                        },
                        label = { Text("Type quote here", color = MonkMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                // Intercept Ctrl+V and Shift+Insert hardware shortcuts
                                if (event.type == KeyEventType.KeyDown) {
                                    val isCtrlV = event.key == Key.V && event.isCtrlPressed
                                    val isShiftInsert = event.key == Key.Insert && event.isShiftPressed
                                    if (isCtrlV || isShiftInsert) {
                                        Toast.makeText(context, "Pasting is disabled. Please type the words mindfully.", Toast.LENGTH_SHORT).show()
                                        return@onPreviewKeyEvent true
                                    }
                                }
                                false
                            },
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.None
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val duration = System.currentTimeMillis() - startTimeMs
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clipText = clipboard?.primaryClip?.let { clip ->
                        if (clip.itemCount > 0) clip.getItemAt(0)?.text?.toString() else null
                    }
                    val result = engine.verifyInput(
                        challengeText = quote.text,
                        userInput = userInput,
                        typingDurationMs = duration,
                        keystrokeCount = keystrokeCount,
                        clipboardText = clipText
                    )
                    when (result) {
                        is RandomTextVerificationResult.Success -> {
                            onSuccess()
                        }
                        is RandomTextVerificationResult.PasteDetected -> {
                            Toast.makeText(context, "Pasting or automated input detected! Please type the words mindfully.", Toast.LENGTH_SHORT).show()
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
