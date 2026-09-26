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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.data.local.StayFocusedDatabase
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
                    text = "Web Blocker",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MonkText
                    )
                )
                Text(
                    text = "Local DNS proxy  •  10.0.0.2/32  •  RFC 1035 NXDOMAIN",
                    style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                )
            }
        }

        // VPN Engine Status Card — flat
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
                                    .background(
                                        color = if (isVpnRunning) MonkSage else MonkMuted,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isVpnRunning) "DNS Filtering Active" else "DNS Blocker Paused",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MonkText
                                    )
                                )
                                Text(
                                    text = if (isVpnRunning) "Narrow route: 10.0.0.2/32 active" else "Tap button to start local proxy",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isVpnRunning) MonkSage else MonkMuted,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = onToggleVpn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVpnRunning) MonkDanger else MonkEmber,
                                contentColor = if (isVpnRunning) MonkText else MonkInk
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isVpnRunning) "Stop" else "Start")
                        }
                    }
                }
            }
        }

        // Quick Category Presets — flat card
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

        // Add Custom Domain — flat card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MonkCard),
                modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Block a Website",
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
                            label = { Text("Domain (e.g. facebook.com)", color = MonkMuted) },
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

        // Private DNS Advisory — warm amber tone, flat
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
                        text = "If Android Private DNS is enabled, encrypted DoT (port 853) may bypass local VPN proxy. For 100% blocking, set Private DNS to 'Off'.",
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

        // Section title
        item {
            Text(
                text = "Blocked Domains (${blockedDomains.size})",
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
                            text = "Add domains or use quick presets above to block distracting websites.",
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MonkCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MonkLine, RoundedCornerShape(18.dp))
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
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText
                    )
                )
                Text(
                    text = if (entity.isBlocked) "Blocked (NXDOMAIN / 0.0.0.0)" else "Inactive",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (entity.isBlocked) MonkDanger else MonkMuted,
                        fontSize = 11.sp
                    )
                )
            }

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

            Spacer(modifier = Modifier.width(4.dp))

            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 4.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MonkMuted)
            ) {
                Text("✕", fontWeight = FontWeight.Bold)
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
        contentPadding = PaddingValues(vertical = 8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkEmber)
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
