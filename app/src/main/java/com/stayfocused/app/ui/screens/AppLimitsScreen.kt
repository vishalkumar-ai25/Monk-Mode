package com.stayfocused.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import com.stayfocused.app.ui.onboarding.PrivateDnsNoticeHelper
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

enum class LimitSegment(val label: String) {
    APPS("Apps"),
    WEBSITES("Websites")
}

data class InstalledAppItem(
    val packageName: String,
    val appName: String
)

data class AppLimitConfigTarget(
    val packageName: String,
    val appName: String,
    val initialMinutes: Int = 15,
    val initialLaunches: Int = 0
)

private val iconMemoryCache = LruCache<String, ImageBitmap>(120)

@Composable
fun AppLimitsScreen(
    database: StayFocusedDatabase,
    isVpnRunning: Boolean = false,
    onToggleVpn: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedSegment by remember { mutableStateOf(LimitSegment.APPS) }
    val appLimits by database.appLimitDao().getAllAppLimits().collectAsState(initial = emptyList())
    val blockedDomains by database.blockedDomainDao().getAllBlockedDomains().collectAsState(initial = emptyList())
    val activeStrictSession by database.strictSessionDao().getActiveStrictSession().collectAsState(initial = null)

    var searchQuery by remember { mutableStateOf("") }
    var customDomainInput by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var editingTarget by remember { mutableStateOf<AppLimitConfigTarget?>(null) }

    // Load installed apps asynchronously
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val items = withContext(Dispatchers.IO) {
            val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            resolveInfos.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = resolveInfo.loadLabel(pm).toString()
                InstalledAppItem(packageName = pkg, appName = label)
            }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
        }
        installedApps = items
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Segmented Switcher
        item {
            Column {
                Text(
                    text = if (selectedSegment == LimitSegment.APPS) "App Limits" else "Website Blocker",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MonkText
                    )
                )
                Text(
                    text = if (selectedSegment == LimitSegment.APPS)
                        "Daily usage limits (15m, 30m, 1h) • sub-10ms interception"
                    else
                        "Local DNS proxy • 10.0.0.2/32 • RFC 1035 NXDOMAIN",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Segmented Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MonkCard, RoundedCornerShape(12.dp))
                        .border(1.dp, MonkLine, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    LimitSegment.entries.forEach { segment ->
                        val isSelected = selectedSegment == segment
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) MonkEmber else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedSegment = segment }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = segment.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MonkInk else MonkMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        if (selectedSegment == LimitSegment.APPS) {
            // Quick Presets Card: YouTube, Instagram, WhatsApp, Chrome, Reddit
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkCard),
                    modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Popular Distractions — Tap to Set Limit",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MonkMuted
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val popularPresets = listOf(
                                Triple("YouTube", "com.google.android.youtube", 15),
                                Triple("Instagram", "com.instagram.android", 15),
                                Triple("WhatsApp", "com.whatsapp", 30),
                                Triple("Chrome", "com.android.chrome", 30),
                                Triple("Reddit", "com.reddit.frontpage", 15)
                            )
                            popularPresets.forEach { (name, pkg, defaultMins) ->
                                val existing = appLimits.find { it.packageName == pkg }
                                PresetButton(
                                    text = name,
                                    onClick = {
                                        if (activeStrictSession != null && existing != null) {
                                            Toast.makeText(context, "Modifying limits is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                            return@PresetButton
                                        }
                                        editingTarget = AppLimitConfigTarget(
                                            packageName = pkg,
                                            appName = existing?.appName ?: name,
                                            initialMinutes = existing?.dailyTimeLimitMinutes ?: defaultMins,
                                            initialLaunches = existing?.dailyLaunchLimit ?: 0
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar & Real-time Installed App Filtering
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkCard),
                    modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Find & Configure App",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MonkText
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search installed apps (e.g. YouTube, Instagram)", color = MonkMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        val filteredApps = remember(installedApps, searchQuery) {
                            if (searchQuery.isNotBlank()) {
                                installedApps.filter {
                                    it.appName.contains(searchQuery, ignoreCase = true) ||
                                        it.packageName.contains(searchQuery, ignoreCase = true)
                                }.take(8)
                            } else emptyList()
                        }

                        if (filteredApps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                filteredApps.forEach { appItem ->
                                    val existing = appLimits.find { it.packageName.equals(appItem.packageName, ignoreCase = true) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MonkCardAlt, RoundedCornerShape(10.dp))
                                            .clickable {
                                                if (activeStrictSession != null && existing != null) {
                                                    Toast.makeText(context, "Modifying limits is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                                    return@clickable
                                                }
                                                editingTarget = AppLimitConfigTarget(
                                                    packageName = appItem.packageName,
                                                    appName = appItem.appName,
                                                    initialMinutes = existing?.dailyTimeLimitMinutes ?: 15,
                                                    initialLaunches = existing?.dailyLaunchLimit ?: 0
                                                )
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            AppIconImage(packageName = appItem.packageName, size = 36)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = appItem.appName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MonkText
                                                )
                                                Text(
                                                    text = if (existing != null) {
                                                        if (existing.dailyTimeLimitMinutes > 0) "${existing.dailyTimeLimitMinutes}m daily limit"
                                                        else if (existing.isBlocked) "Shielded"
                                                        else "Unrestricted"
                                                    } else {
                                                        appItem.packageName
                                                    },
                                                    fontSize = 11.sp,
                                                    color = if (existing != null) MonkEmber else MonkMuted
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (activeStrictSession != null && existing != null) {
                                                    Toast.makeText(context, "Modifying limits is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                                    return@Button
                                                }
                                                editingTarget = AppLimitConfigTarget(
                                                    packageName = appItem.packageName,
                                                    appName = appItem.appName,
                                                    initialMinutes = existing?.dailyTimeLimitMinutes ?: 15,
                                                    initialLaunches = existing?.dailyLaunchLimit ?: 0
                                                )
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MonkEmber,
                                                contentColor = MonkInk
                                            )
                                        ) {
                                            Text(if (existing != null) "Edit Limit" else "Set Limit", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section: Configured Limits
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configured Limits (${appLimits.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MonkText
                        )
                    )
                }
            }

            if (appLimits.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MonkCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No apps configured yet.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MonkMuted)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap any preset above (YouTube, Instagram) or search below to set 15m, 30m, or 1h daily limits.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted.copy(alpha = 0.6f), fontSize = 12.sp)
                            )
                        }
                    }
                }
            } else {
                items(appLimits, key = { it.packageName }) { appLimit ->
                    AppLimitItemCard(
                        entity = appLimit,
                        onToggleBlocked = { isBlocked ->
                            if (!isBlocked && activeStrictSession != null) {
                                Toast.makeText(context, "Unshielding apps is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                return@AppLimitItemCard
                            }
                            scope.launch(Dispatchers.IO) {
                                database.appLimitDao().upsertAppLimit(appLimit.copy(isBlocked = isBlocked))
                            }
                        },
                        onEditLimits = {
                            if (activeStrictSession != null) {
                                Toast.makeText(context, "Modifying limits is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                return@AppLimitItemCard
                            }
                            editingTarget = AppLimitConfigTarget(
                                packageName = appLimit.packageName,
                                appName = appLimit.appName,
                                initialMinutes = appLimit.dailyTimeLimitMinutes,
                                initialLaunches = appLimit.dailyLaunchLimit
                            )
                        },
                        onTestLaunch = {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(appLimit.packageName)
                            if (launchIntent != null) {
                                try {
                                    context.startActivity(launchIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not launch: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "App is not launchable directly", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            if (activeStrictSession != null) {
                                Toast.makeText(context, "Removing app limits is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                return@AppLimitItemCard
                            }
                            scope.launch(Dispatchers.IO) {
                                database.appLimitDao().deleteAppLimit(appLimit.packageName)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Removed ${appLimit.appName}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            // Section: All Installed Apps (for easy discovery when not searching)
            if (searchQuery.isBlank() && installedApps.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Installed Apps (${installedApps.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MonkText
                        )
                    )
                }

                items(installedApps, key = { "installed_${it.packageName}" }) { appItem ->
                    val existing = appLimits.find { it.packageName.equals(appItem.packageName, ignoreCase = true) }
                    InstalledAppRow(
                        appItem = appItem,
                        existingLimit = existing,
                        isStrictModeActive = activeStrictSession != null,
                        onConfigure = {
                            editingTarget = AppLimitConfigTarget(
                                packageName = appItem.packageName,
                                appName = appItem.appName,
                                initialMinutes = existing?.dailyTimeLimitMinutes ?: 15,
                                initialLaunches = existing?.dailyLaunchLimit ?: 0
                            )
                        }
                    )
                }
            }
        } else {
            // WEBSITES SEGMENT
            // DNS Shield Status Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isVpnRunning) MonkSage.copy(alpha = 0.08f) else MonkCard
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, if (isVpnRunning) MonkSage.copy(alpha = 0.4f) else MonkLine, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (isVpnRunning) MonkSage else MonkMuted, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isVpnRunning) "DNS Shield Active" else "DNS Shield Stopped",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVpnRunning) MonkSage else MonkMuted,
                                    fontSize = 14.sp
                                )
                            }

                            Button(
                                onClick = onToggleVpn,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isVpnRunning) MonkDanger.copy(alpha = 0.8f) else MonkEmber,
                                    contentColor = MonkInk
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(if (isVpnRunning) "Stop Shield" else "Start Shield", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Intercepts DNS packets on local 10.0.0.2/32 proxy. Blocked domains return RFC 1035 NXDOMAIN (0.0.0.0). Zero external network traffic or cloud telemetry.",
                            fontSize = 11.sp,
                            color = MonkMuted
                        )
                    }
                }
            }

            // Quick Website Presets
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkCard),
                    modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Popular Distracting Websites",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MonkMuted
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val websitePresets = listOf(
                            "youtube.com", "instagram.com", "reddit.com",
                            "twitter.com", "tiktok.com", "facebook.com", "netflix.com"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            websitePresets.take(4).forEach { domain ->
                                PresetDomainButton(
                                    domain = domain,
                                    modifier = Modifier.weight(1f),
                                    onClick = { addDomain(database, scope, context, domain) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            websitePresets.drop(4).forEach { domain ->
                                PresetDomainButton(
                                    domain = domain,
                                    modifier = Modifier.weight(1f),
                                    onClick = { addDomain(database, scope, context, domain) }
                                )
                            }
                        }
                    }
                }
            }

            // Custom Domain Blocker Input
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkCard),
                    modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Block Custom Website",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MonkText
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customDomainInput,
                                onValueChange = { customDomainInput = it },
                                label = { Text("e.g. youtube.com, x.com", color = MonkMuted) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            Button(
                                onClick = {
                                    val sanitized = sanitizeDomain(customDomainInput)
                                    if (sanitized.isNotEmpty()) {
                                        addDomain(database, scope, context, sanitized)
                                        customDomainInput = ""
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                            ) {
                                Text("Block")
                            }
                        }
                    }
                }
            }

            // Private DNS Advisory
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkEmberDim.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MonkEmber.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Private DNS Advisory",
                            fontWeight = FontWeight.SemiBold,
                            color = MonkEmber,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "If Android Private DNS is enabled, encrypted DoT (port 853) may bypass the local VPN proxy. For 100% blocking, set Private DNS to 'Off'.",
                            fontSize = 11.sp,
                            color = MonkText.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(PrivateDnsNoticeHelper.createPrivateDnsSettingsIntent())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MonkEmber.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkEmber)
                        ) {
                            Text("Open Android Private DNS Settings", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Blocked Domains List
            item {
                Text(
                    text = "Blocked Websites (${blockedDomains.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText
                    )
                )
            }

            if (blockedDomains.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MonkCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No websites blocked yet.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = MonkMuted)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap any preset above (youtube.com, instagram.com) to block distracting websites.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted.copy(alpha = 0.6f), fontSize = 12.sp)
                            )
                        }
                    }
                }
            } else {
                items(blockedDomains, key = { it.domain }) { domainEntity ->
                    BlockedDomainItemCard(
                        entity = domainEntity,
                        onToggleBlocked = { isBlocked ->
                            if (!isBlocked && activeStrictSession != null) {
                                Toast.makeText(context, "Unblocking websites is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                return@BlockedDomainItemCard
                            }
                            scope.launch(Dispatchers.IO) {
                                database.blockedDomainDao().upsertBlockedDomain(domainEntity.copy(isBlocked = isBlocked))
                            }
                        },
                        onDelete = {
                            if (activeStrictSession != null) {
                                Toast.makeText(context, "Removing blocked domains is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                return@BlockedDomainItemCard
                            }
                            scope.launch(Dispatchers.IO) {
                                database.blockedDomainDao().deleteBlockedDomain(domainEntity.domain)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Removed ${domainEntity.domain}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // App Limit Configuration Dialog (Presets: 15m, 30m, 1h, 2h, Block Only)
    if (editingTarget != null) {
        val target = editingTarget!!
        var minutesLimit by remember(target.packageName) { mutableFloatStateOf(target.initialMinutes.toFloat()) }
        var launchLimit by remember(target.packageName) { mutableFloatStateOf(target.initialLaunches.toFloat()) }

        AlertDialog(
            onDismissRequest = { editingTarget = null },
            containerColor = MonkCard,
            title = {
                Text(
                    "Set Limit for ${target.appName}",
                    color = MonkText,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Daily Screen Time Limit:",
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText
                    )

                    // Quick Presets: 15m, 30m, 1 hr, 2 hr, Block Only
                    val minutePresets = listOf(
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
                        minutePresets.forEach { (mins, label) ->
                            val isSelected = minutesLimit.toInt() == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(if (isSelected) MonkEmber else MonkCardAlt, RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) MonkEmber else MonkLine, RoundedCornerShape(8.dp))
                                    .clickable { minutesLimit = mins.toFloat() }
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
                        text = if (minutesLimit.toInt() == 0) "Immediate Block (0 min)" else "Daily Limit: ${minutesLimit.toInt()} minutes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonkEmber
                    )
                    Text(
                        text = if (minutesLimit.toInt() == 0)
                            "App will be shielded immediately whenever opened"
                        else
                            "App is locked immediately once ${minutesLimit.toInt()}m daily screen time is reached",
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

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Daily Launches: ${launchLimit.toInt()} times",
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText
                    )
                    Text(
                        text = if (launchLimit.toInt() == 0) "No launch limit" else "Shield triggers on ${launchLimit.toInt() + 1}th launch",
                        fontSize = 11.sp,
                        color = MonkMuted
                    )
                    Slider(
                        value = launchLimit,
                        onValueChange = { launchLimit = it },
                        valueRange = 0f..30f,
                        steps = 5,
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
                            val existing = appLimits.find { it.packageName == target.packageName }
                            val mins = minutesLimit.toInt()
                            val launches = launchLimit.toInt()
                            val isZeroBlock = mins == 0

                            // Strict Mode Invariant: If Strict Mode is active, existing limits cannot be modified or loosened
                            if (activeStrictSession != null && existing != null) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Modifying limits is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                }
                                return@launch
                            }

                            // Query today's actual usage for newly tracked apps so prior usage is accounted for immediately
                            val initialUsageMs = existing?.currentDayUsageMs
                                ?: com.stayfocused.app.tracker.UsageStatsTracker(context).queryPackageUsageToday(target.packageName)

                            val entityToSave = AppLimitEntity(
                                packageName = target.packageName,
                                appName = target.appName,
                                dailyTimeLimitMinutes = mins,
                                dailyLaunchLimit = launches,
                                isBlocked = isZeroBlock,
                                currentDayUsageMs = initialUsageMs,
                                currentDayLaunches = existing?.currentDayLaunches ?: 0,
                                lastResetTimestamp = existing?.lastResetTimestamp ?: System.currentTimeMillis()
                            )
                            database.appLimitDao().upsertAppLimit(entityToSave)
                            withContext(Dispatchers.Main) {
                                editingTarget = null
                                val msg = if (mins > 0)
                                    "Saved ${mins}m daily limit for ${target.appName}"
                                else
                                    "Shielded ${target.appName} (Immediate Block)"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                ) {
                    Text("Save Limit")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MonkMuted)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AppLimitItemCard(
    entity: AppLimitEntity,
    onToggleBlocked: (Boolean) -> Unit,
    onEditLimits: () -> Unit,
    onTestLaunch: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MonkCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MonkLine, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(packageName = entity.packageName, size = 42)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entity.appName.ifBlank { entity.packageName },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MonkText
                        )
                    )
                    val usedMin = entity.currentDayUsageMs / (60 * 1000L)
                    val limitText = if (entity.dailyTimeLimitMinutes > 0) {
                        "${usedMin}m used / ${entity.dailyTimeLimitMinutes}m daily limit"
                    } else if (entity.isBlocked) {
                        "Permanently Shielded (0m)"
                    } else {
                        "Unrestricted"
                    }
                    Text(
                        text = limitText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (entity.isBlocked) MonkDanger else MonkEmber,
                            fontSize = 11.sp
                        )
                    )
                }

                // Test Launch
                OutlinedButton(
                    onClick = onTestLaunch,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkMuted)
                ) {
                    Text("Test", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = entity.isBlocked,
                    onCheckedChange = onToggleBlocked,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MonkText,
                        checkedTrackColor = MonkEmberDim,
                        checkedBorderColor = MonkEmber,
                        uncheckedThumbColor = MonkMuted,
                        uncheckedTrackColor = MonkCardAlt,
                        uncheckedBorderColor = MonkLine
                    )
                )
            }

            // Visual Progress Bar for time limits
            if (entity.dailyTimeLimitMinutes > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val limitMs = entity.dailyTimeLimitMinutes * 60 * 1000L
                val fraction = (entity.currentDayUsageMs.toFloat() / limitMs.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(MonkCardAlt, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = fraction)
                            .height(5.dp)
                            .background(
                                if (entity.currentDayUsageMs >= limitMs) MonkDanger else MonkEmber,
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Launches: ${entity.currentDayLaunches}${if (entity.dailyLaunchLimit > 0) "/${entity.dailyLaunchLimit}" else ""}",
                    fontSize = 11.sp,
                    color = MonkMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onEditLimits,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MonkEmber)
                    ) {
                        Text("Edit Limit", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MonkMuted)
                    ) {
                        Text("✕", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun InstalledAppRow(
    appItem: InstalledAppItem,
    existingLimit: AppLimitEntity?,
    isStrictModeActive: Boolean,
    onConfigure: () -> Unit
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MonkCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MonkLine, RoundedCornerShape(12.dp))
            .clickable {
                if (isStrictModeActive && existingLimit != null) {
                    Toast.makeText(context, "Modifying limits is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                    return@clickable
                }
                onConfigure()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AppIconImage(packageName = appItem.packageName, size = 36)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = appItem.appName,
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText,
                        fontSize = 13.sp
                    )
                    Text(
                        text = if (existingLimit != null) {
                            if (existingLimit.dailyTimeLimitMinutes > 0) "${existingLimit.dailyTimeLimitMinutes}m limit configured"
                            else if (existingLimit.isBlocked) "Shielded"
                            else "Unrestricted"
                        } else {
                            "No limit set"
                        },
                        fontSize = 11.sp,
                        color = if (existingLimit != null) MonkEmber else MonkMuted
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    if (isStrictModeActive && existingLimit != null) {
                        Toast.makeText(context, "Modifying limits is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                        return@OutlinedButton
                    }
                    onConfigure()
                },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (existingLimit != null) MonkEmber else MonkLine),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (existingLimit != null) MonkEmber else MonkText)
            ) {
                Text(if (existingLimit != null) "Edit" else "Set Limit", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun AppIconImage(
    packageName: String,
    size: Int = 36
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) { mutableStateOf(iconMemoryCache.get(packageName)) }

    if (iconBitmap == null) {
        LaunchedEffect(packageName) {
            withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val drawable = pm.getApplicationIcon(packageName)
                    val bitmap = drawableToBitmap(drawable)
                    val imageBitmap = bitmap.asImageBitmap()
                    iconMemoryCache.put(packageName, imageBitmap)
                    withContext(Dispatchers.Main) {
                        iconBitmap = imageBitmap
                    }
                } catch (_: Exception) {
                    // Fallback to placeholder if package not found
                }
            }
        }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = packageName,
            modifier = Modifier.size(size.dp)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(MonkCardAlt, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = packageName.take(1).uppercase(),
                color = MonkMuted,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 2.5).sp
            )
        }
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
private fun PresetButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkEmber)
    ) {
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PresetDomainButton(
    domain: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkEmber)
    ) {
        Text(text = domain, fontSize = 12.sp, maxLines = 1, softWrap = false)
    }
}

private fun sanitizeDomain(raw: String): String {
    return raw.trim()
        .lowercase()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringBefore("/")
}

private fun addDomain(
    database: StayFocusedDatabase,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    domain: String
) {
    scope.launch(Dispatchers.IO) {
        database.blockedDomainDao().upsertBlockedDomain(
            BlockedDomainEntity(domain = domain, isBlocked = true)
        )
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Shielded $domain", Toast.LENGTH_SHORT).show()
        }
    }
}
