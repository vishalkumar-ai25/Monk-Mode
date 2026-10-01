package com.stayfocused.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.stayfocused.app.BuildConfig
import com.stayfocused.app.breaks.BreakDecisionEngine
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.domain.ProfileSwitchDecisionEngine
import com.stayfocused.app.domain.ProfileSwitchManager
import com.stayfocused.app.domain.ProtectionHealthChecker
import com.stayfocused.app.domain.SettingsBackupManager
import com.stayfocused.app.domain.model.ProfileSwitchDecision
import com.stayfocused.app.domain.model.ProtectionCheckType
import com.stayfocused.app.domain.model.SettingsImportResult
import com.stayfocused.app.oem.OemSurvivalHelper
import com.stayfocused.app.ui.components.DailyUsageDial
import com.stayfocused.app.ui.components.ExportPasswordDialog
import com.stayfocused.app.ui.components.ImportPasswordDialog
import com.stayfocused.app.ui.components.ProtectionStatusCard
import com.stayfocused.app.ui.components.QuickProfileChipRow
import com.stayfocused.app.ui.components.SettingsBackupCard
import com.stayfocused.app.ui.components.StrictLockBlockedDialog
import com.stayfocused.app.ui.components.TakeABreakCard
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText
import com.stayfocused.app.vpn.DnsVpnService
import com.stayfocused.app.worker.WatchdogWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    database: StayFocusedDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val appLimits by database.appLimitDao().getAllAppLimits().collectAsState(initial = emptyList())
    val activeBreak by database.breakSessionDao().getActiveBreak().collectAsState(initial = null)
    val profiles by database.focusProfileDao().getAllProfiles().collectAsState(initial = emptyList())
    val activeProfile = remember(profiles) { profiles.firstOrNull { it.isActive } }
    val activeStrictSession by database.strictSessionDao().getActiveStrictSession().collectAsState(initial = null)
    val blockedDomains by database.blockedDomainDao().getAllBlockedDomains().collectAsState(initial = emptyList())
    val hasBlockedDomains = remember(blockedDomains) { blockedDomains.any { it.isBlocked } }

    val switchManager = remember(database) { ProfileSwitchManager(database) }
    var blockedStrictProfile by remember { mutableStateOf<FocusProfileEntity?>(null) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var selectedExportUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }

    // Calculate total daily tracked usage
    val totalUsedMinutes = (appLimits.sumOf { it.currentDayUsageMs } / (60 * 1000L)).toInt()
    val dailyTargetMinutes = 120 // 2 hours default daily budget

    val healthChecker = remember(context, database) { ProtectionHealthChecker(context, database) }
    var protectionSnapshot by remember { mutableStateOf(healthChecker.checkAll(hasBlockedDomains)) }
    val autostartIntent = remember { OemSurvivalHelper.findResolvableAutostartIntent(context) }

    fun refreshHealth() {
        protectionSnapshot = healthChecker.checkAll(hasBlockedDomains)
    }

    LaunchedEffect(hasBlockedDomains) {
        refreshHealth()
    }

    DisposableEffect(lifecycleOwner, hasBlockedDomains) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshHealth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            selectedExportUri = uri
            showExportDialog = true
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedImportUri = uri
            showImportDialog = true
        }
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val serviceIntent = Intent(context, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
        refreshHealth()
    }

    val handleFixCheck: (ProtectionCheckType) -> Unit = { checkType ->
        when (checkType) {
            ProtectionCheckType.ACCESSIBILITY -> {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            ProtectionCheckType.VPN -> {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    vpnLauncher.launch(prepareIntent)
                } else {
                    val serviceIntent = Intent(context, DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_START
                    }
                    ContextCompat.startForegroundService(context, serviceIntent)
                    refreshHealth()
                }
            }
            ProtectionCheckType.BATTERY_OPTIMIZATION -> {
                context.startActivity(OemSurvivalHelper.createBatteryOptimizationIntent(context))
            }
            ProtectionCheckType.WATCHDOG -> {
                val request = OneTimeWorkRequestBuilder<WatchdogWorker>().build()
                WorkManager.getInstance(context).enqueue(request)
                Toast.makeText(context, "System watchdog triggered", Toast.LENGTH_SHORT).show()
                refreshHealth()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title & Anti-Tamper Status
        item {
            Column {
                Text(
                    text = "Monk Mode",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MonkText
                    )
                )
                Text(
                    text = "Distraction Defense Engine • Anti-Tamper: ${if (BuildConfig.ANTI_TAMPER_ENABLED) "ARMED" else "DEV"}",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )
            }
        }

        // Quick Focus Profile Switcher
        item {
            QuickProfileChipRow(
                profiles = profiles,
                onProfileClick = { targetProfile ->
                    scope.launch(Dispatchers.IO) {
                        val decision = switchManager.switchProfileAtomically(targetProfile)
                        withContext(Dispatchers.Main) {
                            when (decision) {
                                is ProfileSwitchDecision.AlreadyActive -> {
                                    // Already active, no-op
                                }
                                is ProfileSwitchDecision.BlockedByStrictMode -> {
                                    blockedStrictProfile = decision.targetProfile
                                }
                                is ProfileSwitchDecision.ImmediateSwitch -> {
                                    Toast.makeText(context, "Activated profile: ${targetProfile.name}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            )
        }

        // Circular Usage Dial Card
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
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Today's Tracked Screen Time",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MonkText
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    DailyUsageDial(
                        usedMinutes = totalUsedMinutes,
                        targetMinutes = dailyTargetMinutes,
                        size = 190.dp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "${appLimits.size} active app limit${if (appLimits.size == 1) "" else "s"} monitored",
                        style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                    )
                }
            }
        }

        // Active Focus Profile Banner (if any)
        if (activeProfile != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = MonkLine, shape = RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MonkSage, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Active Profile: ${activeProfile.name}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MonkText
                                )
                            )
                            Text(
                                text = "Shield enforcement active across assigned apps and domains",
                                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Breaks Card
        item {
            TakeABreakCard(
                activeBreak = activeBreak,
                onStartBreak = { durationMinutes ->
                    scope.launch(Dispatchers.IO) {
                        val activeStrictSession = database.strictSessionDao().getActiveStrictSessionSync()
                        if (activeStrictSession != null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Breaks are not permitted while Strict Mode is active.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@launch
                        }

                        val breakEngine = BreakDecisionEngine()
                        val session = breakEngine.createBreakSession(
                            currentTimeMs = System.currentTimeMillis(),
                            durationMinutes = durationMinutes
                        )
                        database.breakSessionDao().upsertBreak(session)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "$durationMinutes minute break started", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onEndBreak = {
                    scope.launch(Dispatchers.IO) {
                        database.breakSessionDao().deactivateBreak()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Break ended. Shields re-armed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // Unified Protection Status Health Card
        item {
            ProtectionStatusCard(
                snapshot = protectionSnapshot,
                onFixCheck = handleFixCheck
            )
        }

        // Encrypted Settings Backup Card
        item {
            SettingsBackupCard(
                onExportClick = {
                    createDocumentLauncher.launch("monk_mode_backup.json")
                },
                onImportClick = {
                    openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }
            )
        }

        // OEM autostart shortcut (if resolvable on Realme/ColorOS/HyperOS)
        if (autostartIntent != null) {
            item {
                OutlinedButton(
                    onClick = { context.startActivity(autostartIntent) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkEmber)
                ) {
                    Text(text = "Open Realme / OEM Autostart Settings")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showExportDialog && selectedExportUri != null) {
        ExportPasswordDialog(
            onDismiss = {
                showExportDialog = false
                selectedExportUri = null
            },
            onConfirm = { password ->
                val uri = selectedExportUri
                showExportDialog = false
                selectedExportUri = null
                if (uri != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val backupManager = SettingsBackupManager(database)
                            val encryptedJson = backupManager.exportEncryptedBackup(password)
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                outputStream.bufferedWriter().use { it.write(encryptedJson) }
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Encrypted settings exported successfully", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
    }

    if (showImportDialog && selectedImportUri != null) {
        ImportPasswordDialog(
            onDismiss = {
                showImportDialog = false
                selectedImportUri = null
            },
            onConfirm = { password ->
                val uri = selectedImportUri
                showImportDialog = false
                selectedImportUri = null
                if (uri != null) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val maxSizeBytes = 2 * 1024 * 1024 // 2 MB budget
                            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                val buffer = ByteArray(8192)
                                val outputStream = java.io.ByteArrayOutputStream()
                                var bytesRead: Int
                                var totalBytes = 0
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    totalBytes += bytesRead
                                    if (totalBytes > maxSizeBytes) {
                                        throw IllegalArgumentException("Backup file exceeds maximum allowed size (2 MB)")
                                    }
                                    outputStream.write(buffer, 0, bytesRead)
                                }
                                outputStream.toString(Charsets.UTF_8.name())
                            } ?: ""
                            val backupManager = SettingsBackupManager(database)
                            val result = backupManager.importEncryptedBackup(content, password)
                            withContext(Dispatchers.Main) {
                                when (result) {
                                    is SettingsImportResult.Success -> {
                                        Toast.makeText(
                                            context,
                                            "Restored ${result.limitsImported} limits, ${result.domainsImported} domains, ${result.profilesImported} profiles",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    is SettingsImportResult.BlockedByStrictMode -> {
                                        Toast.makeText(
                                            context,
                                            "Import rejected: Cannot modify settings while Strict Mode is active",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    is SettingsImportResult.DecryptionFailed -> {
                                        Toast.makeText(
                                            context,
                                            "Decryption failed: Incorrect passphrase or corrupted backup",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    is SettingsImportResult.InvalidPayload -> {
                                        Toast.makeText(
                                            context,
                                            "Invalid backup file: ${result.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
    }

    blockedStrictProfile?.let { target ->
        StrictLockBlockedDialog(
            targetProfile = target,
            onDismiss = { blockedStrictProfile = null }
        )
    }
}
