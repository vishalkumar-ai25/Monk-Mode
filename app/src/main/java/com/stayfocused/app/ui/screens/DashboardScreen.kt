package com.stayfocused.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.BuildConfig
import com.stayfocused.app.breaks.BreakDecisionEngine
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.BreakSessionEntity
import com.stayfocused.app.oem.OemSurvivalHelper
import com.stayfocused.app.service.FocusAccessibilityService
import com.stayfocused.app.ui.components.DailyUsageDial
import com.stayfocused.app.ui.components.TakeABreakCard
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

    // Calculate total daily tracked usage
    val totalUsedMinutes = (appLimits.sumOf { it.currentDayUsageMs } / (60 * 1000L)).toInt()
    val dailyTargetMinutes = 120 // 2 hours default daily budget

    val isAccessibilityGranted = remember { mutableStateOf(checkAccessibilityService(context)) }
    val isOverlayGranted = remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val isUsageGranted = remember { mutableStateOf(checkUsageAccess(context)) }
    val isBatteryExempt = remember { mutableStateOf(OemSurvivalHelper.isIgnoringBatteryOptimizations(context)) }
    val isNotificationGranted = remember { mutableStateOf(checkNotificationAccess(context)) }

    LaunchedEffect(Unit) {
        isAccessibilityGranted.value = checkAccessibilityService(context)
        isOverlayGranted.value = Settings.canDrawOverlays(context)
        isUsageGranted.value = checkUsageAccess(context)
        isBatteryExempt.value = OemSurvivalHelper.isIgnoringBatteryOptimizations(context)
        isNotificationGranted.value = checkNotificationAccess(context)
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
                    text = "Stay Focused",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Commercial-Grade Focus Engine • Anti-Tamper: ${BuildConfig.ANTI_TAMPER_ENABLED}",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                )
            }
        }

        // Circular Usage Dial Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
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
                            fontWeight = FontWeight.Bold,
                            color = Color.White
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
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                }
            }
        }

        // Active Focus Profile Banner (if any)
        if (activeProfiles.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
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
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Active Profile: ${activeProfiles.first().name}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Shield enforcement active across assigned apps and domains",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.sp)
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

        // System Health & Permissions
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "System Health & OS Permissions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionRow(
                        title = "Accessibility Shield",
                        subtitle = "Required for sub-10ms foreground app interception",
                        isGranted = isAccessibilityGranted.value,
                        onRequest = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    )

                    PermissionRow(
                        title = "Display Over Other Apps",
                        subtitle = "Required for Compose shield overlay window",
                        isGranted = isOverlayGranted.value,
                        onRequest = {
                            context.startActivity(Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                        }
                    )

                    PermissionRow(
                        title = "Usage Access",
                        subtitle = "Required for daily foreground time tracking",
                        isGranted = isUsageGranted.value,
                        onRequest = {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    )

                    PermissionRow(
                        title = "Battery Optimization",
                        subtitle = "Prevents OEM aggressive killing (Realme / ColorOS)",
                        isGranted = isBatteryExempt.value,
                        onRequest = {
                            context.startActivity(OemSurvivalHelper.createBatteryOptimizationIntent(context))
                        }
                    )

                    PermissionRow(
                        title = "Notification Silencer",
                        subtitle = "Required to intercept and vault distracting notifications",
                        isGranted = isNotificationGranted.value,
                        onRequest = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    )

                    val autostartIntent = remember { OemSurvivalHelper.findResolvableAutostartIntent(context) }
                    if (autostartIntent != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { context.startActivity(autostartIntent) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(text = "Open Realme / OEM Autostart Settings", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
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
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isGranted) "✓" else "✕",
                color = if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = Color.White)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.sp)
            )
        }

        if (!isGranted) {
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
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
