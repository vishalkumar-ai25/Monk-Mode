package com.stayfocused.app.ui.screens

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.BuildConfig
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.receiver.StayFocusedDeviceAdminReceiver
import com.stayfocused.app.strict.FailsafeManager
import com.stayfocused.app.strict.StrictModeManager
import com.stayfocused.app.strict.StrictPreferences
import com.stayfocused.app.strict.StrictUnlockMethod
import com.stayfocused.app.ui.TimeFormatter
import com.stayfocused.app.ui.components.FailsafeLogCard
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkCardAlt
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkEmberDim
import com.stayfocused.app.ui.theme.MonkInk
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun StrictLockScreen(
    database: StayFocusedDatabase,
    failsafeManager: FailsafeManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val strictPreferences = remember { StrictPreferences(context) }
    val strictManager = remember {
        StrictModeManager(
            database = database,
            preferences = strictPreferences,
            failsafeManager = failsafeManager
        )
    }

    val activeStrictSession by database.strictSessionDao().getActiveStrictSession().collectAsState(initial = null)
    val failsafeLogs by database.failsafeLogDao().getAllLogs().collectAsState(initial = emptyList())

    // 1-second live ticker
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val sessionState = remember(activeStrictSession, currentTimeMs) {
        strictManager.evaluateState(activeStrictSession)
    }

    val isDeviceAdminActive = remember(currentTimeMs) {
        StayFocusedDeviceAdminReceiver.isDeviceAdminActive(context)
    }

    // Inactive setup states
    var selectedDurationMinutes by remember { mutableIntStateOf(strictPreferences.lastDurationMinutes) }
    var selectedUnlockMethod by remember { mutableStateOf(strictPreferences.unlockMethod) }
    var configuredPin by remember { mutableStateOf("") }
    var blockSettingsOption by remember { mutableStateOf(strictPreferences.blockSettings) }
    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var showArmConfirmDialog by remember { mutableStateOf(false) }

    // Active deactivation dialog states
    var showHardcoreRefusalDialog by remember { mutableStateOf(false) }
    var showPinUnlockDialog by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    var showTypingChallengeDialog by remember { mutableStateOf(false) }
    var typedChallengeText by remember { mutableStateOf("") }

    // Failsafe states
    var generatedRecoveryCode by remember { mutableStateOf<String?>(null) }
    var enteredRecoveryCode by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Strict Mode",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = if (sessionState.isActive) MonkDanger else MonkText
                    )
                )
                Text(
                    text = "Uncompromising self-control engine  •  anti-tamper lockout",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )
            }
        }

        // ==========================================
        // 1. ACTIVE STRICT MODE STATUS CARD
        // ==========================================
        if (sessionState.isActive && sessionState.session != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkDanger.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, MonkDanger.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(MonkDanger, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STRICT LOCK ACTIVE",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp,
                                    color = MonkDanger
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Large Digital Countdown Timer (HH:MM:SS)
                        val totalSecs = sessionState.remainingMillis / 1000L
                        val hours = totalSecs / 3600
                        val mins = (totalSecs % 3600) / 60
                        val secs = totalSecs % 60
                        val timerText = String.format("%02d:%02d:%02d", hours, mins, secs)

                        Text(
                            text = timerText,
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MonkText
                            )
                        )
                        Text(
                            text = "Remaining until ${TimeFormatter.formatMinutes(((sessionState.session.targetEndTime - sessionState.session.startTime) / 60000L).toInt())} session concludes",
                            style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Unlock Method Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MonkCardAlt)
                                .border(1.dp, MonkLine, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Lock Mode: ${sessionState.unlockMethod.displayName}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MonkEmber,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Deactivate / Unlock Button
                        Button(
                            onClick = {
                                when (sessionState.unlockMethod) {
                                    StrictUnlockMethod.HARDCORE -> {
                                        showHardcoreRefusalDialog = true
                                    }
                                    StrictUnlockMethod.PIN -> {
                                        enteredPin = ""
                                        pinError = false
                                        showPinUnlockDialog = true
                                    }
                                    StrictUnlockMethod.TYPING_PHRASE -> {
                                        typedChallengeText = ""
                                        showTypingChallengeDialog = true
                                    }
                                    StrictUnlockMethod.TIME_DELAYED -> {
                                        if (!sessionState.isDelayedUnlockPending) {
                                            scope.launch(Dispatchers.IO) {
                                                strictManager.requestDelayedUnlock(delayMinutes = 15)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "15-minute cooldown initiated.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else if (sessionState.delayedUnlockRemainingMillis == 0L) {
                                            scope.launch(Dispatchers.IO) {
                                                strictManager.tryFinalizeDelayedUnlock()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Delayed unlock finalized.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, "Cooldown in progress. Please wait.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    StrictUnlockMethod.EMERGENCY_CODE -> {
                                        Toast.makeText(context, "Use Emergency Recovery Code section below.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sessionState.unlockMethod == StrictUnlockMethod.HARDCORE) MonkCardAlt else MonkDanger,
                                contentColor = if (sessionState.unlockMethod == StrictUnlockMethod.HARDCORE) MonkMuted else MonkInk
                            )
                        ) {
                            val buttonLabel = when (sessionState.unlockMethod) {
                                StrictUnlockMethod.HARDCORE -> "🔒 Locked (No Early Exit)"
                                StrictUnlockMethod.PIN -> "Enter PIN to Deactivate"
                                StrictUnlockMethod.TYPING_PHRASE -> "Typing Friction Challenge"
                                StrictUnlockMethod.TIME_DELAYED -> {
                                    if (!sessionState.isDelayedUnlockPending) {
                                        "Request 15m Delayed Unlock"
                                    } else if (sessionState.delayedUnlockRemainingMillis == 0L) {
                                        "Finalize Delayed Unlock"
                                    } else {
                                        val dMins = sessionState.delayedUnlockRemainingMillis / 60000L
                                        val dSecs = (sessionState.delayedUnlockRemainingMillis % 60000L) / 1000L
                                        "Cooldown: ${String.format("%02d:%02d", dMins, dSecs)}"
                                    }
                                }
                                StrictUnlockMethod.EMERGENCY_CODE -> "Use Recovery Code Below"
                            }
                            Text(buttonLabel, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (sessionState.isDelayedUnlockPending) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        strictManager.cancelDelayedUnlock()
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Delayed unlock request cancelled.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Cancel Delayed Unlock & Re-arm", color = MonkMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. INACTIVE: ARM STRICT MODE SETUP CARD
        // ==========================================
        if (!sessionState.isActive) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Arm Strict Session",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = MonkText
                            )
                        )
                        Text(
                            text = "Configure session duration and anti-cheat deactivation mode.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Duration Presets
                        Text(
                            text = "Duration: ${TimeFormatter.formatMinutes(selectedDurationMinutes)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val presetDurations = listOf(15, 30, 60, 120, 240, 480, 1440)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presetDurations) { duration ->
                                val isSelected = selectedDurationMinutes == duration
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) MonkEmber else MonkCardAlt)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MonkEmber else MonkLine,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedDurationMinutes = duration }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    val label = when (duration) {
                                        15 -> "15m"
                                        30 -> "30m"
                                        60 -> "1h"
                                        120 -> "2h"
                                        240 -> "4h"
                                        480 -> "8h"
                                        1440 -> "24h"
                                        else -> "${duration}m"
                                    }
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MonkInk else MonkText
                                    )
                                }
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MonkCardAlt)
                                        .border(1.dp, MonkLine, RoundedCornerShape(10.dp))
                                        .clickable { showCustomDurationDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text("Custom…", fontSize = 12.sp, color = MonkMuted)
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MonkLine)

                        // Unlock Method Selection (Stay Focused signature modes)
                        Text(
                            text = "Deactivation Friction Mode",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val unlockMethods = listOf(
                            StrictUnlockMethod.HARDCORE,
                            StrictUnlockMethod.PIN,
                            StrictUnlockMethod.TYPING_PHRASE,
                            StrictUnlockMethod.TIME_DELAYED
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            unlockMethods.forEach { method ->
                                val isSelected = selectedUnlockMethod == method
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) MonkEmberDim.copy(alpha = 0.25f) else MonkCardAlt)
                                        .border(
                                            1.dp,
                                            if (isSelected) MonkEmber else MonkLine,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedUnlockMethod = method }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isSelected) MonkEmber else MonkMuted, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = method.displayName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MonkText
                                        )
                                        Text(
                                            text = method.description,
                                            fontSize = 11.sp,
                                            color = MonkMuted
                                        )
                                    }
                                }
                            }
                        }

                        // PIN Input field if PIN mode chosen
                        if (selectedUnlockMethod == StrictUnlockMethod.PIN) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = configuredPin,
                                onValueChange = { if (it.length <= 6) configuredPin = it },
                                label = { Text("Set 4 to 6 digit unlock PIN", color = MonkMuted) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = MonkLine)

                        // Shields & Tamper Options
                        Text(
                            text = "Anti-Tamper Barriers",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Block Phone Settings toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Block Android Settings", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MonkText)
                                Text("Prevents accessing Settings to disable Accessibility", fontSize = 11.sp, color = MonkMuted)
                            }
                            Switch(
                                checked = blockSettingsOption,
                                onCheckedChange = { blockSettingsOption = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MonkText,
                                    checkedTrackColor = MonkEmberDim,
                                    checkedBorderColor = MonkEmber
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Device Admin Anti-Uninstall row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Prevent Uninstallation", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MonkText)
                                Text(
                                    if (isDeviceAdminActive) "Device Administrator is ACTIVE" else "Requires Device Administrator permission",
                                    fontSize = 11.sp,
                                    color = if (isDeviceAdminActive) MonkSage else MonkMuted
                                )
                            }
                            if (!isDeviceAdminActive) {
                                Button(
                                    onClick = {
                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(
                                                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                                StayFocusedDeviceAdminReceiver.getComponentName(context)
                                            )
                                            putExtra(
                                                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                                "Monk Mode uses Device Administrator to prevent uninstallation during active focus sessions."
                                            )
                                        }
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                                ) {
                                    Text("Activate", fontSize = 11.sp)
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MonkSage.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✓", color = MonkSage, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        // ARM STRICT MODE CTA BUTTON
                        Button(
                            onClick = {
                                if (selectedUnlockMethod == StrictUnlockMethod.PIN && configuredPin.length < 4) {
                                    Toast.makeText(context, "Please set a PIN of at least 4 digits.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                showArmConfirmDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MonkEmber,
                                contentColor = MonkInk
                            )
                        ) {
                            Text("ARM STRICT MODE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. EMERGENCY FAILSAFES & AUDIT LOG
        // ==========================================
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MonkCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MonkLine, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(
                        text = "Emergency Failsafe Layers",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MonkText
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Emergency Recovery Code
                    FailsafeLayerRow(
                        numeral = "1",
                        title = "Emergency Recovery Code",
                        description = "Single-use 16-character cryptographic key (PBKDF2/SHA-256). Immediately overrides all active strict locks in real emergencies."
                    ) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val code = strictManager.generateRecoveryCode()
                                    withContext(Dispatchers.Main) {
                                        generatedRecoveryCode = code
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MonkCardAlt, contentColor = MonkText)
                        ) {
                            Text("Generate 16-Char Recovery Code")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = enteredRecoveryCode,
                                onValueChange = { enteredRecoveryCode = it },
                                label = { Text("Redeem code", color = MonkMuted) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val success = strictManager.verifyAndConsumeRecoveryCode(enteredRecoveryCode.trim())
                                        withContext(Dispatchers.Main) {
                                            if (success) {
                                                enteredRecoveryCode = ""
                                                Toast.makeText(context, "Recovery code accepted! Strict Mode deactivated.", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Invalid or already consumed code.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MonkSage, contentColor = MonkInk)
                            ) {
                                Text("Redeem")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MonkLine)

                    // Scoped Boot Grace Period
                    FailsafeLayerRow(
                        numeral = "2",
                        title = "Scoped Boot Grace Window",
                        description = "For 3–5 minutes after reboot, Settings and Device Admin restrictions are lifted to allow recovering from system errors. App blocks remain armed."
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MonkLine)

                    // ADB Escape Hatch
                    FailsafeLayerRow(
                        numeral = "3",
                        title = "Developer ADB Escape Hatch",
                        description = "Connect phone to PC via USB for developer recovery."
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MonkCardAlt, RoundedCornerShape(10.dp))
                                .border(1.dp, MonkLine, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "adb shell pm disable-user --user 0 com.stayfocused.app",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MonkMuted
                            )
                        }
                    }
                }
            }
        }

        // Failsafe Integrity Audit Log Card
        item {
            FailsafeLogCard(logs = failsafeLogs)
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ==========================================
    // DIALOGS
    // ==========================================

    // Confirmation dialog before arming
    if (showArmConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showArmConfirmDialog = false },
            containerColor = MonkCard,
            title = {
                Text(
                    text = "Arm Strict Mode?",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MonkDanger
                )
            },
            text = {
                Text(
                    text = "You are activating Strict Mode for ${TimeFormatter.formatMinutes(selectedDurationMinutes)} under ${selectedUnlockMethod.displayName}. During this period, bypass attempts will be blocked and restrictions enforced. Do you want to proceed?",
                    color = MonkMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showArmConfirmDialog = false
                        scope.launch(Dispatchers.IO) {
                            strictManager.startStrictSession(
                                durationMinutes = selectedDurationMinutes,
                                unlockMethod = selectedUnlockMethod,
                                pin = configuredPin.ifBlank { null },
                                blockSettings = blockSettingsOption
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Strict Mode Armed! Focus maintained.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonkDanger, contentColor = MonkInk)
                ) {
                    Text("Arm Strict Mode")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArmConfirmDialog = false }) {
                    Text("Cancel", color = MonkMuted)
                }
            }
        )
    }

    // Hardcore mode refusal dialog
    if (showHardcoreRefusalDialog) {
        AlertDialog(
            onDismissRequest = { showHardcoreRefusalDialog = false },
            containerColor = MonkCard,
            title = {
                Text(
                    text = "🔒 Hardcore Mode Active",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MonkDanger
                )
            },
            text = {
                Text(
                    text = "Hardcore Strict Mode does not allow early exit. You pledged to stay focused until the session reaches zero. Stay disciplined and finish strong!",
                    color = MonkText
                )
            },
            confirmButton = {
                Button(
                    onClick = { showHardcoreRefusalDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                ) {
                    Text("Stay Focused")
                }
            }
        )
    }

    // PIN Deactivation Dialog
    if (showPinUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showPinUnlockDialog = false },
            containerColor = MonkCard,
            title = {
                Text("Enter PIN to Deactivate", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = MonkText)
            },
            text = {
                Column {
                    Text("Enter the 4-6 digit PIN you configured when arming Strict Mode:", color = MonkMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = {
                            enteredPin = it
                            pinError = false
                        },
                        isError = pinError,
                        label = { Text("4 to 6 digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (pinError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Incorrect PIN. Please try again.", color = MonkDanger, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val success = strictManager.verifyAndUnlockWithPin(enteredPin)
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    showPinUnlockDialog = false
                                    Toast.makeText(context, "Strict Mode Deactivated.", Toast.LENGTH_SHORT).show()
                                } else {
                                    pinError = true
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinUnlockDialog = false }) {
                    Text("Cancel", color = MonkMuted)
                }
            }
        )
    }

    // Typing Challenge Dialog
    if (showTypingChallengeDialog) {
        val targetPhrase = strictPreferences.typingPhrase.trim()
        val isMatched = typedChallengeText.trim() == targetPhrase

        AlertDialog(
            onDismissRequest = { showTypingChallengeDialog = false },
            containerColor = MonkCard,
            title = {
                Text(
                    text = "Typing Friction Challenge",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MonkText
                )
            },
            text = {
                Column {
                    Text(
                        text = "To deactivate, type the pledge below exactly as shown. Copy-pasting is discouraged:",
                        color = MonkMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MonkCardAlt, RoundedCornerShape(8.dp))
                            .border(1.dp, MonkEmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = targetPhrase,
                            fontFamily = FontFamily.Serif,
                            color = MonkText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = typedChallengeText,
                        onValueChange = { typedChallengeText = it },
                        placeholder = { Text("Type here…", color = MonkMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${typedChallengeText.length} / ${targetPhrase.length} characters matched",
                        fontSize = 11.sp,
                        color = if (isMatched) MonkSage else MonkMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val success = strictManager.verifyAndUnlockWithPhrase(typedChallengeText)
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    showTypingChallengeDialog = false
                                    Toast.makeText(context, "Challenge completed. Strict Mode unlocked.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Text does not match target phrase.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = isMatched,
                    colors = ButtonDefaults.buttonColors(containerColor = MonkSage, contentColor = MonkInk)
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTypingChallengeDialog = false }) {
                    Text("Cancel", color = MonkMuted)
                }
            }
        )
    }

    // Custom Duration Picker Dialog
    if (showCustomDurationDialog) {
        var hoursSlider by remember { mutableFloatStateOf((selectedDurationMinutes / 60).toFloat()) }
        var minsSlider by remember { mutableFloatStateOf((selectedDurationMinutes % 60).toFloat()) }

        AlertDialog(
            onDismissRequest = { showCustomDurationDialog = false },
            containerColor = MonkCard,
            title = {
                Text("Custom Strict Duration", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = MonkText)
            },
            text = {
                Column {
                    val calcTotal = (hoursSlider.toInt() * 60) + minsSlider.toInt()
                    Text(
                        text = "Duration: ${hoursSlider.toInt()}h ${minsSlider.toInt()}m (${calcTotal} mins)",
                        fontWeight = FontWeight.Bold,
                        color = MonkEmber
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Hours: ${hoursSlider.toInt()}", fontSize = 12.sp, color = MonkMuted)
                    Slider(
                        value = hoursSlider,
                        onValueChange = { hoursSlider = it },
                        valueRange = 0f..24f,
                        steps = 23,
                        colors = SliderDefaults.colors(thumbColor = MonkEmber, activeTrackColor = MonkEmber)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Minutes: ${minsSlider.toInt()}", fontSize = 12.sp, color = MonkMuted)
                    Slider(
                        value = minsSlider,
                        onValueChange = { minsSlider = it },
                        valueRange = 5f..55f,
                        steps = 9,
                        colors = SliderDefaults.colors(thumbColor = MonkEmber, activeTrackColor = MonkEmber)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val total = (hoursSlider.toInt() * 60) + minsSlider.toInt()
                        selectedDurationMinutes = total.coerceAtLeast(5)
                        showCustomDurationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                ) {
                    Text("Set Duration")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDurationDialog = false }) {
                    Text("Cancel", color = MonkMuted)
                }
            }
        )
    }

    // Emergency Recovery Code Display Dialog
    if (generatedRecoveryCode != null) {
        AlertDialog(
            onDismissRequest = { generatedRecoveryCode = null },
            containerColor = MonkCard,
            title = {
                Text(
                    "Emergency Recovery Code",
                    color = MonkText,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Save this 16-character code in a secure physical location. It will never be displayed again:",
                        color = MonkMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MonkCardAlt, RoundedCornerShape(10.dp))
                            .border(1.dp, MonkEmber.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val formatted = generatedRecoveryCode!!.chunked(4).joinToString("-")
                        Text(
                            text = formatted,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MonkEmber
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Recovery Code", generatedRecoveryCode)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        generatedRecoveryCode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                ) {
                    Text("Copy & Close")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { generatedRecoveryCode = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MonkMuted)
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun FailsafeLayerRow(
    numeral: String,
    title: String,
    description: String,
    content: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = numeral,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MonkEmber,
            modifier = Modifier
                .width(32.dp)
                .padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MonkText
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MonkMuted,
                    lineHeight = 18.sp
                )
            )
            if (content != null) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}
