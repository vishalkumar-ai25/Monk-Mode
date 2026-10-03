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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import com.stayfocused.app.domain.FocusSummaryShareManager
import com.stayfocused.app.domain.model.DailyFocusSummaryData
import com.stayfocused.app.tracker.AppUsageInfo
import com.stayfocused.app.tracker.UsageStatsTracker
import com.stayfocused.app.ui.TimeFormatter
import com.stayfocused.app.widget.FocusGlanceWidgetReceiver
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableLongStateOf
import com.stayfocused.app.data.local.entities.AppLimitEntity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.stayfocused.app.ui.theme.MonkCardAlt
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkEmberDim
import com.stayfocused.app.ui.theme.MonkInk
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText
import com.stayfocused.app.receiver.StayFocusedDeviceAdminReceiver
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
    var isSharing by remember { mutableStateOf(false) }

    val usageTracker = remember(context) { UsageStatsTracker(context) }
    var hasUsagePermission by remember { mutableStateOf(usageTracker.hasUsageStatsPermission()) }
    var totalDeviceScreenTimeMs by remember { mutableLongStateOf(0L) }
    var topUsedApps by remember { mutableStateOf<List<AppUsageInfo>>(emptyList()) }
    var appToConfigureLimit by remember { mutableStateOf<AppUsageInfo?>(null) }

    val healthChecker = remember(context, database) { ProtectionHealthChecker(context, database) }
    var protectionSnapshot by remember { mutableStateOf(healthChecker.checkAll(hasBlockedDomains)) }
    val autostartIntent = remember { OemSurvivalHelper.findResolvableAutostartIntent(context) }

    fun refreshUsageAndHealth() {
        hasUsagePermission = usageTracker.hasUsageStatsPermission()
        protectionSnapshot = healthChecker.checkAll(hasBlockedDomains)
        if (hasUsagePermission) {
            scope.launch(Dispatchers.IO) {
                try {
                    usageTracker.syncUsageWithDatabase(database.appLimitDao())
                    val totalMs = usageTracker.queryTotalDeviceScreenTimeMs()
                    val top = usageTracker.queryTopUsedApps(limit = 6)
                    withContext(Dispatchers.Main) {
                        totalDeviceScreenTimeMs = totalMs
                        topUsedApps = top
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    LaunchedEffect(hasBlockedDomains) {
        refreshUsageAndHealth()
    }

    DisposableEffect(lifecycleOwner, hasBlockedDomains) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshUsageAndHealth()
                FocusGlanceWidgetReceiver.triggerUpdateAsync(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Calculate total daily tracked usage (actual device screen time if permission granted, else app limits sum)
    val totalUsedMinutes = if (hasUsagePermission && totalDeviceScreenTimeMs > 0L) {
        (totalDeviceScreenTimeMs / (60 * 1000L)).toInt()
    } else {
        (appLimits.sumOf { it.currentDayUsageMs } / (60 * 1000L)).toInt()
    }
    val dailyTargetMinutes = 120 // 2 hours default daily budget

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
        refreshUsageAndHealth()
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
                    refreshUsageAndHealth()
                }
            }
            ProtectionCheckType.BATTERY_OPTIMIZATION -> {
                context.startActivity(OemSurvivalHelper.createBatteryOptimizationIntent(context))
            }
            ProtectionCheckType.WATCHDOG -> {
                val request = OneTimeWorkRequestBuilder<WatchdogWorker>().build()
                WorkManager.getInstance(context).enqueue(request)
                Toast.makeText(context, "System watchdog triggered", Toast.LENGTH_SHORT).show()
                refreshUsageAndHealth()
            }
            ProtectionCheckType.USAGE_ACCESS -> {
                try {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (e: Exception) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
            ProtectionCheckType.DEVICE_ADMIN -> {
                try {
                    context.startActivity(StayFocusedDeviceAdminReceiver.createAddDeviceAdminIntent(context).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                } catch (e: Exception) {
                    Toast.makeText(context, "Unable to open Device Admin settings", Toast.LENGTH_SHORT).show()
                }
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

        // Usage Access Permission Warning Banner (if not granted)
        if (!hasUsagePermission) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = MonkEmber, shape = RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(MonkEmber, CircleShape))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Usage Access Permission Required",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MonkEmber)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Android requires Usage Access to track your real screen time, calculate app usage, and enforce daily boundaries.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 12.sp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { handleFixCheck(ProtectionCheckType.USAGE_ACCESS) },
                            colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Grant Usage Access", fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
                        text = if (hasUsagePermission) {
                            "Total Device Usage: ${TimeFormatter.formatUsageDuration(totalDeviceScreenTimeMs)}"
                        } else {
                            "${appLimits.size} active app limit${if (appLimits.size == 1) "" else "s"} monitored"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            if (isSharing) return@OutlinedButton
                            isSharing = true
                            scope.launch {
                                try {
                                    val shareManager = FocusSummaryShareManager(context)
                                    val startOfToday = UsageStatsTracker(context).getStartOfToday()
                                    val blockedDistractions = database.suppressedNotificationDao().getSuppressedCountSince(startOfToday)
                                    val activeProfileName = activeProfile?.name
                                    val dateFormatted = LocalDate.now().format(
                                        DateTimeFormatter.ofPattern("MMM d, yyyy")
                                    )
                                    val summaryData = DailyFocusSummaryData(
                                        dateText = dateFormatted,
                                        usedMinutes = totalUsedMinutes,
                                        targetMinutes = dailyTargetMinutes,
                                        activeLimitsCount = appLimits.size,
                                        blockedDistractionsCount = blockedDistractions,
                                        activeProfileName = activeProfileName
                                    )
                                    shareManager.shareDailySummary(summaryData)
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Could not share summary: ${e.message ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
                                    }
                                } finally {
                                    isSharing = false
                                }
                            }
                        },
                        enabled = !isSharing,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkText)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = if (isSharing) MonkMuted else MonkEmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSharing) "Preparing..." else "Share Today",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Usage Overview: Top Apps by Usage Today
        if (hasUsagePermission && topUsedApps.isNotEmpty()) {
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
                                text = "Usage Overview",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    color = MonkText
                                )
                            )
                            Text(
                                text = "Today",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MonkMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val maxUsage = topUsedApps.maxOfOrNull { it.foregroundTimeMs }?.coerceAtLeast(1L) ?: 1L

                        topUsedApps.forEachIndexed { index, app ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            val matchingLimit = appLimits.find { it.packageName == app.packageName }
                            val isLimitActive = matchingLimit != null && matchingLimit.dailyTimeLimitMinutes > 0
                            val isShielded = matchingLimit != null && matchingLimit.isBlocked

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (activeStrictSession != null && activeStrictSession!!.isActive) {
                                            Toast.makeText(
                                                context,
                                                "Modifying limits is prohibited while Strict Mode is active.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            appToConfigureLimit = app
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconImage(packageName = app.packageName, size = 42)

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = app.appName.ifBlank { app.packageName },
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MonkText
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isLimitActive) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(MonkEmberDim, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "${matchingLimit!!.dailyTimeLimitMinutes}m",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MonkEmber,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            } else if (isShielded) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .background(MonkDanger.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "Blocked",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MonkDanger,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = TimeFormatter.formatUsageDuration(app.foregroundTimeMs),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MonkMuted,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    val fraction = (app.foregroundTimeMs.toFloat() / maxUsage.toFloat()).coerceIn(0.02f, 1f)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .background(MonkCardAlt, RoundedCornerShape(3.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction)
                                                .height(6.dp)
                                                .background(
                                                    if (isShielded) MonkDanger else MonkEmber,
                                                    RoundedCornerShape(3.dp)
                                                )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = "⋮",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MonkMuted,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
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
            val isStrictActive = activeStrictSession != null && activeStrictSession!!.isActive
            TakeABreakCard(
                activeBreak = activeBreak,
                isStrictModeActive = isStrictActive,
                onStartBreak = { durationMinutes, reason ->
                    scope.launch(Dispatchers.IO) {
                        val activeStrict = database.strictSessionDao().getActiveStrictSessionSync()
                        val isStrictNow = activeStrict != null && activeStrict.isActive
                        val breakEngine = BreakDecisionEngine()

                        if (!breakEngine.canStartBreak(reason, isStrictModeActive = isStrictNow)) {
                            withContext(Dispatchers.Main) {
                                val msg = if (isStrictNow) {
                                    "Breaks are not permitted while Strict Mode is active."
                                } else {
                                    "A valid non-empty reason is required to take a break."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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

    appToConfigureLimit?.let { app ->
        val existingLimit = appLimits.find { it.packageName == app.packageName }
        var minutesLimit by remember(app.packageName) {
            mutableFloatStateOf(
                existingLimit?.dailyTimeLimitMinutes?.toFloat() ?: 60f
            )
        }

        AlertDialog(
            onDismissRequest = { appToConfigureLimit = null },
            containerColor = MonkCard,
            title = {
                Text(
                    text = "Set Limit: ${app.appName}",
                    color = MonkText,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Daily Screen Time Boundary:",
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText,
                        fontSize = 13.sp
                    )

                    // Quick Presets: 15 min, 30 min, 1 hr, 2 hr, Block Only
                    val presets = listOf(
                        15 to "15 min",
                        30 to "30 min",
                        60 to "1 hr",
                        120 to "2 hr",
                        0 to "Block Only"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { (mins, label) ->
                            val isSelected = minutesLimit.toInt() == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) MonkEmber else MonkCardAlt,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MonkEmber else MonkLine,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { minutesLimit = mins.toFloat() }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MonkInk else MonkText
                                )
                            }
                        }
                    }

                    Text(
                        text = if (minutesLimit.toInt() == 0) "Immediate Block (0 min)" else "Daily Limit: ${minutesLimit.toInt()} minutes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonkEmber
                    )
                    Text(
                        text = if (minutesLimit.toInt() == 0) "App will be blocked whenever opened" else "App will lock immediately once ${minutesLimit.toInt()}m is reached",
                        fontSize = 11.sp,
                        color = MonkMuted
                    )

                    Slider(
                        value = minutesLimit,
                        onValueChange = { minutesLimit = it },
                        valueRange = 0f..180f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = MonkEmber,
                            activeTrackColor = MonkEmber,
                            inactiveTrackColor = MonkCardAlt
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val isZeroBlock = (minutesLimit.toInt() == 0)
                            val updated = existingLimit?.copy(
                                dailyTimeLimitMinutes = minutesLimit.toInt(),
                                isBlocked = isZeroBlock || existingLimit.isBlocked
                            ) ?: AppLimitEntity(
                                packageName = app.packageName,
                                appName = app.appName,
                                dailyTimeLimitMinutes = minutesLimit.toInt(),
                                isBlocked = isZeroBlock
                            )
                            database.appLimitDao().upsertAppLimit(updated)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Limit saved for ${app.appName}", Toast.LENGTH_SHORT).show()
                                appToConfigureLimit = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Limit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { appToConfigureLimit = null },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkMuted),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
