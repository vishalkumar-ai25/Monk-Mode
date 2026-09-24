package com.stayfocused.app.ui.screens

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import com.stayfocused.app.ui.onboarding.PrivateDnsNoticeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WebBlockerScreen(
    database: StayFocusedDatabase,
    isVpnRunning: Boolean,
    onToggleVpn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val blockedDomains by database.blockedDomainDao().getAllBlockedDomains().collectAsState(initial = emptyList())
    val activeStrictSession by database.strictSessionDao().getActiveStrictSession().collectAsState(initial = null)
    var customDomainInput by remember { mutableStateOf("") }

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
                    text = "Website Blocker",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Local loopback DNS proxy (10.0.0.2/32) • Zero battery drain • RFC 1035 NXDOMAIN",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                )
            }
        }

        // VPN Engine Status Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
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
                                    .size(12.dp)
                                    .background(
                                        color = if (isVpnRunning) Color(0xFF10B981) else Color(0xFF94A3B8),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isVpnRunning) "DNS Filtering Active" else "DNS Blocker Paused",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = if (isVpnRunning) "Narrow route: 10.0.0.2/32 active" else "Tap button to start local proxy",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isVpnRunning) Color(0xFF10B981) else Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = onToggleVpn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVpnRunning) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isVpnRunning) "Stop Blocker" else "Start Blocker")
                        }
                    }
                }
            }
        }

        // Quick Category Presets
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Presets:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetDomainButton(
                            domain = "reddit.com",
                            modifier = Modifier.weight(1f),
                            onClick = { addDomain(database, scope, context, "reddit.com") }
                        )
                        PresetDomainButton(
                            domain = "instagram.com",
                            modifier = Modifier.weight(1f),
                            onClick = { addDomain(database, scope, context, "instagram.com") }
                        )
                        PresetDomainButton(
                            domain = "twitter.com",
                            modifier = Modifier.weight(1f),
                            onClick = { addDomain(database, scope, context, "twitter.com") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetDomainButton(
                            domain = "youtube.com",
                            modifier = Modifier.weight(1f),
                            onClick = { addDomain(database, scope, context, "youtube.com") }
                        )
                        PresetDomainButton(
                            domain = "tiktok.com",
                            modifier = Modifier.weight(1f),
                            onClick = { addDomain(database, scope, context, "tiktok.com") }
                        )
                    }
                }
            }
        }

        // Add Custom Domain
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add Blocked Website",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customDomainInput,
                            onValueChange = { customDomainInput = it },
                            label = { Text("Domain (e.g. facebook.com)") },
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
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Block")
                        }
                    }
                }
            }
        }

        // Private DNS Settings Advisory
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF78350F).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Android Private DNS Advisory",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "If Android Private DNS is enabled, encrypted DoT (port 853) may bypass local VPN proxy. For 100% blocking, set Private DNS to 'Off'.",
                        fontSize = 11.sp,
                        color = Color(0xFFFDE68A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(PrivateDnsNoticeHelper.createPrivateDnsSettingsIntent())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Open Android Private DNS Settings", color = Color(0xFFFBBF24), fontSize = 12.sp)
                    }
                }
            }
        }

        // Section Title for Blocked Domains
        item {
            Text(
                text = "Blocked Domains (${blockedDomains.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }

        if (blockedDomains.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No websites blocked yet.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF94A3B8))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add domains or use quick presets above to block distracting websites.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B), fontSize = 12.sp)
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

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun BlockedDomainItemCard(
    entity: BlockedDomainEntity,
    onToggleBlocked: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.domain,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = if (entity.isBlocked) "Blocked (Forged NXDOMAIN / 0.0.0.0)" else "Inactive",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (entity.isBlocked) Color(0xFFEF4444) else Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                )
            }

            Switch(
                checked = entity.isBlocked,
                onCheckedChange = onToggleBlocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFEF4444)
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            TextButton(
                onClick = onDelete,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
            ) {
                Text("✕", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
            }
        }
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        Text(text = domain, fontSize = 12.sp)
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
    context: android.content.Context,
    domain: String
) {
    scope.launch(Dispatchers.IO) {
        database.blockedDomainDao().upsertBlockedDomain(
            BlockedDomainEntity(domain = domain, isBlocked = true)
        )
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Added $domain", Toast.LENGTH_SHORT).show()
        }
    }
}
