package com.stayfocused.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.BuildConfig
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.strict.FailsafeManager
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StrictLockScreen(
    database: StayFocusedDatabase,
    failsafeManager: FailsafeManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val activeStrictSession by database.strictSessionDao().getActiveStrictSession().collectAsState(initial = null)
    val failsafeLogs by database.failsafeLogDao().getAllLogs().collectAsState(initial = emptyList())
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
                    text = "Strict Mode & Failsafes",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MonkText
                    )
                )
                Text(
                    text = "Irreversible anti-tamper controls with multi-tiered emergency safety valves",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )
            }
        }

        // Anti-Tamper Status Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MonkCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = MonkLine, shape = RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (BuildConfig.ANTI_TAMPER_ENABLED) MonkSage else MonkEmber,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (BuildConfig.ANTI_TAMPER_ENABLED) "Anti-Tamper: ACTIVE" else "Anti-Tamper: DEV RELAXED",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MonkText
                                    )
                                )
                                Text(
                                    text = if (BuildConfig.ANTI_TAMPER_ENABLED) {
                                        "Release Build: Settings and uninstallation lockout fully armed"
                                    } else {
                                        "Debug Build: Safe dev mode (Settings interception logs without lockout)"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Failsafe Architecture Card — All 4 layers cleanly organized inside 1 card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MonkCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = MonkLine, shape = RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Text(
                        text = "Defense-in-Depth Safety Valves",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MonkText
                        )
                    )
                    Text(
                        text = "Pre-configured override channels to guarantee you are never irreversibly trapped.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Layer 1: Time-Delayed Unlock
                    FailsafeLayerRow(
                        numeral = "1",
                        title = "Time-Delayed Unlock",
                        description = "Mandatory 24–48 hour cooldown. Prevents dopamine-driven impulsive disarming while ensuring deliberate escape."
                    ) {
                        val session = activeStrictSession
                        if (session != null && session.isActive) {
                            val isPending = failsafeManager.isUnlockPending(session)
                            if (isPending) {
                                val remainingMs = failsafeManager.getRemainingDelayMs(session)
                                val remainingHours = remainingMs / (1000 * 3600)
                                val remainingMinutes = (remainingMs % (1000 * 3600)) / (1000 * 60)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MonkCardAlt, RoundedCornerShape(12.dp))
                                        .border(1.dp, MonkLine, RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = "Unlock Pending",
                                        fontWeight = FontWeight.Bold,
                                        color = MonkEmber,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Cooldown completes in: ${remainingHours}h ${remainingMinutes}m",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    val success = failsafeManager.tryFinalizeDelayedUnlock(session.id)
                                                    withContext(Dispatchers.Main) {
                                                        if (success) {
                                                            Toast.makeText(context, "Delayed unlock completed!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "Cooldown time has not elapsed yet.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                                        ) {
                                            Text("Finalize Unlock")
                                        }

                                        Button(
                                            onClick = {
                                                scope.launch(Dispatchers.IO) {
                                                    failsafeManager.cancelDelayedUnlock(session.id)
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "Unlock request cancelled.", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MonkDanger, contentColor = MonkText)
                                        ) {
                                            Text("Cancel Request")
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            failsafeManager.requestDelayedUnlock(session.id)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "24-hour delayed unlock initiated.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmberDim, contentColor = MonkText)
                                ) {
                                    Text("Request 24h Delayed Unlock")
                                }
                            }
                        } else {
                            Text(
                                text = "Strict session inactive. Arm a session to activate.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 12.sp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MonkLine)

                    // Layer 2: Emergency Recovery Code
                    FailsafeLayerRow(
                        numeral = "2",
                        title = "Emergency Recovery Code",
                        description = "Single-use 16-character cryptographic recovery code. Generates once; store in a password manager or physical vault."
                    ) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val code = failsafeManager.generateAndStoreRecoveryCode()
                                    withContext(Dispatchers.Main) {
                                        generatedRecoveryCode = code
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MonkCardAlt, contentColor = MonkText)
                        ) {
                            Text("Generate New Code")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = enteredRecoveryCode,
                                onValueChange = { enteredRecoveryCode = it },
                                label = { Text("Enter 16-character code", color = MonkMuted) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val success = failsafeManager.verifyAndConsumeRecoveryCode(enteredRecoveryCode.trim())
                                        withContext(Dispatchers.Main) {
                                            if (success) {
                                                enteredRecoveryCode = ""
                                                Toast.makeText(context, "Recovery code accepted! Strict locks released.", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Invalid or already used recovery code.", Toast.LENGTH_SHORT).show()
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

                    // Layer 3: Boot Grace Period
                    FailsafeLayerRow(
                        numeral = "3",
                        title = "Scoped Boot Grace Period",
                        description = "For 3–5 minutes after device restart, Settings-blocking and Device Admin lockout are suspended ONLY. All app and website blocking rules remain armed during reboot."
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MonkLine)

                    // Layer 4: ADB Escape Hatch
                    FailsafeLayerRow(
                        numeral = "4",
                        title = "Developer ADB Escape Hatch",
                        description = "Last-resort developer path via ADB commands. Documented in README."
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MonkCardAlt, RoundedCornerShape(10.dp))
                                .border(1.dp, MonkLine, RoundedCornerShape(10.dp))
                                .padding(12.dp)
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

    // Recovery Code Dialog — styled with Monk tokens
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

/**
 * Single flat row for a failsafe layer.
 * Uses a small serif numeral on the left, title + description on the right,
 * with an optional content slot for action widgets.
 */
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
        // Serif numeral
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
