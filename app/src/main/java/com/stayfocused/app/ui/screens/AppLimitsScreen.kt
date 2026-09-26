package com.stayfocused.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import android.util.LruCache

data class InstalledAppItem(
    val packageName: String,
    val appName: String
)

private val iconMemoryCache = LruCache<String, ImageBitmap>(100)

@Composable
fun AppLimitsScreen(
    database: StayFocusedDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val appLimits by database.appLimitDao().getAllAppLimits().collectAsState(initial = emptyList())
    val activeStrictSession by database.strictSessionDao().getActiveStrictSession().collectAsState(initial = null)
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var editingAppLimit by remember { mutableStateOf<AppLimitEntity?>(null) }

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
        // Header
        item {
            Column {
                Text(
                    text = "App Limits",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MonkText
                    )
                )
                Text(
                    text = "Sub-10ms interception  •  daily screen time boundaries",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )
            }
        }

        // Quick Presets — flat card, no nested disclosure
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MonkCard),
                modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MonkMuted
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetButton(
                            text = "Flipkart",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    database.appLimitDao().upsertAppLimit(
                                        AppLimitEntity(
                                            packageName = "com.flipkart.android",
                                            appName = "Flipkart",
                                            isBlocked = true
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Flipkart shielded", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        PresetButton(
                            text = "Blinkit",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    database.appLimitDao().upsertAppLimit(
                                        AppLimitEntity(
                                            packageName = "com.grofers.customerapp",
                                            appName = "Blinkit",
                                            isBlocked = true
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Blinkit shielded", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        PresetButton(
                            text = "Notes",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    database.appLimitDao().upsertAppLimit(
                                        AppLimitEntity(
                                            packageName = "com.coloros.note",
                                            appName = "Notes",
                                            isBlocked = true
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Notes shielded", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Search Bar & Installed App Picker — flat card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MonkCard),
                modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add Application",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MonkText
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search installed apps (e.g. YouTube, Chrome)", color = MonkMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    val filteredApps = remember(installedApps, searchQuery) {
                        if (searchQuery.isNotBlank()) {
                            installedApps.filter {
                                it.appName.contains(searchQuery, ignoreCase = true) ||
                                    it.packageName.contains(searchQuery, ignoreCase = true)
                            }.take(5)
                        } else emptyList()
                    }

                    if (filteredApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            filteredApps.forEach { appItem ->
                                val isAlreadyAdded = appLimits.any { it.packageName.equals(appItem.packageName, ignoreCase = true) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MonkCardAlt, RoundedCornerShape(10.dp))
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
                                                text = appItem.packageName,
                                                fontSize = 11.sp,
                                                color = MonkMuted
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                database.appLimitDao().upsertAppLimit(
                                                    AppLimitEntity(
                                                        packageName = appItem.packageName,
                                                        appName = appItem.appName,
                                                        isBlocked = true
                                                    )
                                                )
                                                withContext(Dispatchers.Main) {
                                                    searchQuery = ""
                                                    Toast.makeText(context, "Added ${appItem.appName}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        enabled = !isAlreadyAdded,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MonkEmber,
                                            contentColor = MonkInk,
                                            disabledContainerColor = MonkCardAlt,
                                            disabledContentColor = MonkMuted
                                        )
                                    ) {
                                        Text(if (isAlreadyAdded) "Configured" else "Shield", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configured (${appLimits.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText
                    )
                )
            }
        }

        // Configured App Limit Cards — flat, one per app
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
                            text = "Search and add apps above to set time boundaries or shields.",
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
                        editingAppLimit = appLimit
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
                            Toast.makeText(context, "App is not launchable", Toast.LENGTH_SHORT).show()
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

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Limit Configuration Dialog
    if (editingAppLimit != null) {
        val target = editingAppLimit!!
        var minutesLimit by remember(target.packageName) { mutableFloatStateOf(target.dailyTimeLimitMinutes.toFloat()) }
        var launchLimit by remember(target.packageName) { mutableFloatStateOf(target.dailyLaunchLimit.toFloat()) }

        AlertDialog(
            onDismissRequest = { editingAppLimit = null },
            containerColor = MonkCard,
            title = {
                Text(
                    "Configure  ${target.appName}",
                    color = MonkText,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Daily Screen Time: ${minutesLimit.toInt()} minutes",
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText
                    )
                    Text(
                        text = if (minutesLimit.toInt() == 0) "No time limit (Block only)" else "Shield triggers once exceeded",
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

                    Spacer(modifier = Modifier.height(12.dp))

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
                            database.appLimitDao().upsertAppLimit(
                                target.copy(
                                    dailyTimeLimitMinutes = minutesLimit.toInt(),
                                    dailyLaunchLimit = launchLimit.toInt()
                                )
                            )
                            withContext(Dispatchers.Main) {
                                editingAppLimit = null
                                Toast.makeText(context, "Saved limits for ${target.appName}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingAppLimit = null },
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
    // Single flat card — no nested accordions
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
                        "${usedMin}m / ${entity.dailyTimeLimitMinutes}m limit"
                    } else if (entity.isBlocked) {
                        "Permanently Shielded"
                    } else {
                        "Unrestricted"
                    }
                    Text(
                        text = limitText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (entity.isBlocked) MonkDanger else MonkMuted,
                            fontSize = 11.sp
                        )
                    )
                }

                // Test Shield
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

            Spacer(modifier = Modifier.height(8.dp))

            // Flat detail row — launches + action links
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Launches today: ${entity.currentDayLaunches}${if (entity.dailyLaunchLimit > 0) "/${entity.dailyLaunchLimit}" else ""}",
                    fontSize = 11.sp,
                    color = MonkMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onEditLimits,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MonkEmber)
                    ) {
                        Text("Set Limits", fontSize = 11.sp)
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
        contentPadding = PaddingValues(vertical = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkEmber)
    ) {
        Text(text = text, fontSize = 12.sp)
    }
}
