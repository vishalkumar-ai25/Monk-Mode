package com.stayfocused.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.stayfocused.app.domain.FocusSummaryShareManager
import com.stayfocused.app.domain.model.DailyFocusSummaryData
import com.stayfocused.app.tracker.UsageStatsTracker
import com.stayfocused.app.ui.TimeFormatter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
import androidx.compose.ui.graphics.Color
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
import com.stayfocused.app.data.local.entities.BreakSessionEntity
import com.stayfocused.app.domain.ProtectionHealthChecker
import com.stayfocused.app.domain.model.ProtectionCheckType
import com.stayfocused.app.oem.OemSurvivalHelper
import com.stayfocused.app.service.FocusAccessibilityService
import com.stayfocused.app.ui.components.DailyUsageDial
import com.stayfocused.app.ui.components.ProtectionStatusCard
import com.stayfocused.app.ui.components.TakeABreakCard
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkCardAlt
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkEmberDim
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkInk
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.stayfocused.app.domain.SettingsBackupManager
import com.stayfocused.app.domain.model.SettingsImportResult
import com.stayfocused.app.ui.components.ExportPasswordDialog
import com.stayfocused.app.ui.components.ImportPasswordDialog
import com.stayfocused.app.ui.components.SettingsBackupCard
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.domain.ProfileSwitchDecisionEngine
import com.stayfocused.app.domain.model.ProfileSwitchDecision
import com.stayfocused.app.ui.components.QuickProfileChipRow
import com.stayfocused.app.ui.components.StrictProfileSwitchBlockedDialog
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
    val scope = rememberCoroutineScope()

    val appLimits by database.appLimitDao().getAllAppLimits().collectAsState(initial = emptyList())
    val activeBreak by database.breakSessionDao().getActiveBreak().collectAsState(initial = null)
    val activeProfiles by database.focusProfileDao().getActiveProfiles().collectAsState(initial = emptyList())
    val allProfiles by database.focusProfileDao().getAllProfiles().collectAsState(initial = emptyList())
    val activeStrictSession by database.strictSessionDao().getActiveStrictSession().collectAsState(initial = null)
    val profileDecisionEngine = remember { ProfileSwitchDecisionEngine() }
    var blockedStrictProfile by remember { mutableStateOf<FocusProfileEntity?>(null) }
    var isCryptoProcessing by remember { mutableStateOf(false) }

    val handleProfileClick: (FocusProfileEntity) -> Unit = { targetProfile ->
        val currentActive = allProfiles.firstOrNull { it.isActive }
        val isStrictActive = (activeStrictSession != null && activeStrictSession!!.isActive) || (currentActive?.isStrictMode == true)
        val decision = profileDecisionEngine.evaluateSwitch(
            currentActiveProfile = currentActive,
            targetProfile = targetProfile,
            isStrictModeActive = isStrictActive
        )
        when (decision) {
            is ProfileSwitchDecision.AlreadyActive -> {}
            is ProfileSwitchDecision.ImmediateSwitch -> {
                scope.launch(Dispatchers.IO) {
                    database.focusProfileDao().switchToProfile(targetProfile.id)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Activated profile: ${targetProfile.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            is ProfileSwitchDecision.BlockedByStrictMode -> {
                blockedStrictProfile = decision.outgoingProfile ?: currentActive
            }
        }
    }

    val tracker = remember { UsageStatsTracker(context) }
    var hasUsagePermission by remember { mutableStateOf(tracker.hasUsageStatsPermission()) }
    var totalDeviceUsageMs by remember { mutableStateOf(0L) }
    var topUsedApps by remember { mutableStateOf<List<UsageStatsTracker.TopAppUsage>>(emptyList()) }

    // Calculate total daily tracked usage
    val monitoredUsedMinutes = (appLimits.sumOf { it.currentDayUsageMs } / (60 * 1000L)).toInt()
    val deviceUsedMinutes = (totalDeviceUsageMs / (60 * 1000L)).toInt()
    val totalUsedMinutes = if (deviceUsedMinutes > 0) deviceUsedMinutes else monitoredUsedMinutes
    val dailyTargetMinutes = 120 // 2 hours default daily budget

    val healthChecker = remember(context, database) { ProtectionHealthChecker(context, database) }
    var protectionSnapshot by remember { mutableStateOf(healthChecker.checkAll()) }
    val autostartIntent = remember { OemSurvivalHelper.findResolvableAutostartIntent(context) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val pwd = pendingExportPassword
        pendingExportPassword = null
        if (uri != null && pwd != null) {
            scope.launch {
                isCryptoProcessing = true
                try {
                    val encryptedJson = withContext(Dispatchers.Default) {
                        val backupManager = SettingsBackupManager(database)
                        backupManager.exportEncryptedBackup(pwd)
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(encryptedJson.toByteArray(Charsets.UTF_8))
                        }
                    }
                    Toast.makeText(context, "Settings exported securely", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isCryptoProcessing = false
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedImportUri = uri
            showImportDialog = true
        }
    }

    fun refreshUsageData() {
        val perm = tracker.hasUsageStatsPermission()
        hasUsagePermission = perm
        if (perm) {
            scope.launch(Dispatchers.IO) {
                try {
                    tracker.syncUsageWithDatabase(database.appLimitDao())
                    val deviceMs = tracker.getTotalDeviceUsageMs()
                    val topApps = tracker.getTopUsedApps(limit = 4)
                    withContext(Dispatchers.Main) {
                        totalDeviceUsageMs = deviceMs
                        topUsedApps = topApps
                    }
                } catch (e: Exception) {
                    // Ignored in test environment
                }
            }
        }
    }

    fun refreshHealth() {
        scope.launch(Dispatchers.IO) {
            val updated = healthChecker.checkAll()
            withContext(Dispatchers.Main) {
                protectionSnapshot = updated
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshHealth()
        refreshUsageData()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshHealth()
                refreshUsageData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
                    context.startActivity(prepareIntent.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } else {
                    val serviceIntent = Intent(context, DnsVpnService::class.java)
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
        // App Title
        item {
            Column {
                Text(
                    text = "Monk Mode",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MonkText
                    )
                )
                Text(
                    text = "Focus Engine  •  Anti-Tamper: ${BuildConfig.ANTI_TAMPER_ENABLED}",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )
            }
        }

        // Usage Access Permission Banner if not yet granted
        if (!hasUsagePermission) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkDanger.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MonkDanger.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Usage Access Required",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MonkDanger
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Monk Mode needs Usage Access permission in Android Settings to track screen time and enforce app limits.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MonkDanger, contentColor = MonkInk)
                        ) {
                            Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Active Strict Mode Banner
        val nowMs = System.currentTimeMillis()
        if (activeStrictSession != null && activeStrictSession!!.isActive && activeStrictSession!!.targetEndTime > nowMs) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkDanger.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MonkDanger.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
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
                                .background(MonkDanger, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Strict Mode: ACTIVE",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MonkDanger
                                )
                            )
                            val remainingMs = (activeStrictSession!!.targetEndTime - nowMs).coerceAtLeast(0L)
                            val hours = remainingMs / (3600 * 1000L)
                            val minutes = (remainingMs % (3600 * 1000L)) / (60 * 1000L)
                            Text(
                                text = "Shields locked: ${hours}h ${minutes}m remaining • Anti-tamper armed",
                                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Focus Profile Switcher Chip Row
        item {
            QuickProfileChipRow(
                profiles = allProfiles,
                onProfileClick = handleProfileClick
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
                        text = "Today's Screen Time",
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
                        text = if (deviceUsedMinutes > 0) {
                            "${TimeFormatter.formatMinutes(totalUsedMinutes)} total phone usage • ${appLimits.size} limits active"
                        } else {
                            "${appLimits.size} active app limit${if (appLimits.size == 1) "" else "s"} monitored"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val shareManager = FocusSummaryShareManager(context)
                                val startOfToday = UsageStatsTracker(context).getStartOfToday()
                                val blockedDistractions = database.suppressedNotificationDao().getSuppressedCountSince(startOfToday)
                                val activeProfile = activeProfiles.firstOrNull()?.name
                                val dateFormatted = LocalDate.now().format(
                                    DateTimeFormatter.ofPattern("MMM d, yyyy")
                                )
                                val summaryData = DailyFocusSummaryData(
                                    dateText = dateFormatted,
                                    usedMinutes = totalUsedMinutes,
                                    targetMinutes = dailyTargetMinutes,
                                    activeLimitsCount = appLimits.size,
                                    blockedDistractionsCount = blockedDistractions,
                                    activeProfileName = activeProfile
                                )
                                withContext(Dispatchers.Main) {
                                    shareManager.shareDailySummary(summaryData)
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkText)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MonkEmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Today", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Top Used Apps Today Card
        if (topUsedApps.isNotEmpty()) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Most Used Today",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    color = MonkText
                                )
                            )
                            Text(
                                text = "Live Screen Time",
                                style = MaterialTheme.typography.labelSmall.copy(color = MonkMuted)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        topUsedApps.forEachIndexed { index, appUsage ->
                            val mins = (appUsage.usageMs / (60 * 1000L)).toInt()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MonkEmber
                                        ),
                                        modifier = Modifier.width(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    AppIconImage(packageName = appUsage.packageName, size = 32)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = appUsage.appName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                color = MonkText
                                            )
                                        )
                                        Text(
                                            text = appUsage.packageName,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MonkMuted,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }

                                Text(
                                    text = TimeFormatter.formatMinutes(mins),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MonkEmber
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Focus Profile Banner (if any)
        if (activeProfiles.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkEmberDim.copy(alpha = 0.25f)),
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
                                .size(8.dp)
                                .background(MonkSage, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Active Profile: ${activeProfiles.first().name}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
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
                onStartBreak = { durationMinutes, reason ->
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
                        if (!breakEngine.canStartBreak(reason, isStrictModeActive = false)) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "A non-empty reason is required to start a break.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@launch
                        }

                        val session = breakEngine.createBreakSession(
                            currentTimeMs = System.currentTimeMillis(),
                            durationMinutes = durationMinutes,
                            reason = reason
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

        // Unified Protection Status Card
        item {
            ProtectionStatusCard(
                snapshot = protectionSnapshot,
                onFixCheck = handleFixCheck
            )
        }

        // Encrypted Backup & Restore Card
        item {
            SettingsBackupCard(
                onExportClick = { showExportDialog = true },
                onImportClick = {
                    openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }
            )
        }

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

    if (showExportDialog) {
        ExportPasswordDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { password ->
                pendingExportPassword = password
                showExportDialog = false
                createDocumentLauncher.launch("monk_mode_backup.json")
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
                    scope.launch {
                        isCryptoProcessing = true
                        try {
                            val content = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    inputStream.bufferedReader().use { it.readText() }
                                } ?: ""
                            }
                            val result = withContext(Dispatchers.Default) {
                                val backupManager = SettingsBackupManager(database)
                                backupManager.importEncryptedBackup(content, password)
                            }
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
                        } catch (e: Exception) {
                            Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isCryptoProcessing = false
                        }
                    }
                }
            }
        )
    }

    blockedStrictProfile?.let { profile ->
        StrictProfileSwitchBlockedDialog(
            outgoingProfile = profile,
            onDismiss = {
                blockedStrictProfile = null
            }
        )
    }

    if (isCryptoProcessing) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            containerColor = MonkCard,
            title = {
                Text(
                    text = "Cryptographic Processing",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MonkText
                    )
                )
            },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MonkEmber,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Executing PBKDF2 key derivation (210,000 iterations)... Please wait.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MonkMuted)
                    )
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot — sage for granted, danger for missing
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = if (isGranted) MonkSage.copy(alpha = 0.18f) else MonkDanger.copy(alpha = 0.18f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isGranted) "✓" else "✕",
                color = if (isGranted) MonkSage else MonkDanger,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MonkText)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
            )
        }

        if (!isGranted) {
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MonkEmber,
                    contentColor = MonkInk
                )
            ) {
                Text("Grant", fontSize = 12.sp)
            }
        }
    }
}

private fun checkAccessibilityService(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: ""
    return enabledServices.contains(context.packageName, ignoreCase = true)
}

private fun checkUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return false
    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

private fun checkNotificationAccess(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}


