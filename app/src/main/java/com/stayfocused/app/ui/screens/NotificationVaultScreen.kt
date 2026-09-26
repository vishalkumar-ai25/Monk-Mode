package com.stayfocused.app.ui.screens

import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.SuppressedNotificationEntity
import com.stayfocused.app.ui.TimeFormatter
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkCardAlt
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkInk
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NotificationVaultScreen(
    database: StayFocusedDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notifications by database.suppressedNotificationDao().getAll().collectAsState(initial = emptyList())
    val unviewedCount by database.suppressedNotificationDao().getUnviewedCount().collectAsState(initial = 0)
    var searchQuery by remember { mutableStateOf("") }
    var isNotificationAccessGranted by remember { mutableStateOf(checkNotificationAccess(context)) }

    LaunchedEffect(Unit) {
        isNotificationAccessGranted = checkNotificationAccess(context)
    }

    val filteredNotifications = remember(notifications, searchQuery) {
        if (searchQuery.isBlank()) {
            notifications
        } else {
            val q = searchQuery.trim().lowercase()
            notifications.filter {
                it.packageName.lowercase().contains(q) ||
                    it.appName.lowercase().contains(q) ||
                    (it.title?.lowercase()?.contains(q) == true) ||
                    (it.contentSnippet?.lowercase()?.contains(q) == true)
            }
        }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Vault",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = MonkText
                            )
                        )
                        Text(
                            text = "Silenced distractions saved safely for later review",
                            style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                        )
                    }

                    if (unviewedCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(MonkEmber, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$unviewedCount new",
                                color = MonkInk,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Permission Warning — flat card with ember-tinted background
        if (!isNotificationAccessGranted) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MonkEmber.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MonkEmber.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Notification Access Required",
                            fontWeight = FontWeight.SemiBold,
                            color = MonkEmber,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To silence distracting notifications and log them to this vault, grant Notification Listener permission.",
                            fontSize = 11.sp,
                            color = MonkText.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                })
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MonkEmber.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkEmber)
                        ) {
                            Text("Enable Notification Listener", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Action Buttons Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            database.suppressedNotificationDao().markAllAsViewed()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Marked all as read", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    enabled = unviewedCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MonkEmber,
                        contentColor = MonkInk,
                        disabledContainerColor = MonkCardAlt,
                        disabledContentColor = MonkMuted
                    )
                ) {
                    Text("Mark All Read", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            database.suppressedNotificationDao().clearAll()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Vault cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MonkDanger.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkDanger),
                    enabled = notifications.isNotEmpty()
                ) {
                    Text("Clear All", fontSize = 13.sp)
                }
            }
        }

        // Search Filter
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Filter by title or app", color = MonkMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
        }

        // Notification Feed or Empty State
        if (filteredNotifications.isEmpty()) {
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
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(MonkCardAlt, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔔", fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching notifications" else "Vault is Clean",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = MonkText
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "Try searching for a different keyword or app name."
                            } else {
                                "Distracting notifications intercepted during active focus sessions will be safely preserved here."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MonkMuted,
                                fontSize = 12.sp
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredNotifications, key = { it.id }) { notification ->
                SuppressedNotificationItemCard(notification = notification)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SuppressedNotificationItemCard(
    notification: SuppressedNotificationEntity
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isViewed) MonkCard else MonkCardAlt.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (!notification.isViewed) MonkLine else MonkLine.copy(alpha = 0.4f),
                RoundedCornerShape(18.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (!notification.isViewed) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(MonkEmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = notification.appName.ifBlank { notification.packageName.substringAfterLast(".") },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MonkEmber
                        )
                    )
                }

                Text(
                    text = TimeFormatter.formatTimeAgo(notification.postTimestamp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MonkMuted,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (!notification.title.isNullOrBlank()) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (!notification.contentSnippet.isNullOrBlank()) {
                Text(
                    text = notification.contentSnippet,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MonkMuted,
                        fontSize = 12.sp
                    ),
                    maxLines = 2
                )
            }
        }
    }
}

private fun checkNotificationAccess(context: android.content.Context): Boolean {
    val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(context.packageName)
}
