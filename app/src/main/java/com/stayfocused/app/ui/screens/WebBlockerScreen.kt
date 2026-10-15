package com.stayfocused.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import com.stayfocused.app.data.local.entities.BlockedKeywordEntity
import com.stayfocused.app.data.local.entities.BlockedUrlPathEntity
import com.stayfocused.app.data.local.entities.BrowsingHistoryEntity
import com.stayfocused.app.domain.BrowserUrlBarRegistry
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
import com.stayfocused.app.util.ProtectionPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class WebBlockerTab(val label: String) {
    DOMAINS("Domains"),
    PATHS("URL Paths"),
    KEYWORDS("Keywords"),
    ADULT("Adult Filter"),
    HISTORY("History")
}

@Composable
fun WebBlockerScreen(
    database: StayFocusedDatabase,
    isVpnRunning: Boolean,
    onToggleVpn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ProtectionPreferences(context) }

    val blockedDomains by database.blockedDomainDao().getAllBlockedDomains().collectAsState(initial = emptyList())
    val blockedPaths by database.blockedUrlPathDao().getAllUrlPaths().collectAsState(initial = emptyList())
    val blockedKeywords by database.blockedKeywordDao().getAllKeywords().collectAsState(initial = emptyList())
    val browsingHistory by database.browsingHistoryDao().getRecentHistory(100).collectAsState(initial = emptyList())
    val activeStrictSession by database.strictSessionDao().getActiveStrictSession().collectAsState(initial = null)

    var selectedTab by remember { mutableStateOf(WebBlockerTab.DOMAINS) }
    var customDomainInput by remember { mutableStateOf("") }
    var customPathInput by remember { mutableStateOf("") }
    var customKeywordInput by remember { mutableStateOf("") }
    var isAdultBlockerEnabled by remember { mutableStateOf(prefs.isAdultBlockerEnabled) }

    val isStrictModeActive = activeStrictSession != null && activeStrictSession?.isActive == true

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
                    text = "DNS NXDOMAIN Proxy  •  DOM URL Inspector  •  Aho-Corasick Trie",
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
                                contentColor = MonkInk
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isVpnRunning) "Stop" else "Start")
                        }
                    }
                }
            }
        }

        // Segmented Tab Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WebBlockerTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val badgeCount = when (tab) {
                        WebBlockerTab.DOMAINS -> blockedDomains.size
                        WebBlockerTab.PATHS -> blockedPaths.size
                        WebBlockerTab.KEYWORDS -> blockedKeywords.size
                        WebBlockerTab.ADULT -> if (isAdultBlockerEnabled) 1 else 0
                        WebBlockerTab.HISTORY -> browsingHistory.size
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) MonkEmber else MonkCard,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MonkEmber else MonkLine,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MonkInk else MonkText
                                )
                            )
                            if (badgeCount > 0 && tab != WebBlockerTab.ADULT) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isSelected) MonkInk.copy(alpha = 0.2f) else MonkCardAlt,
                                            shape = CircleShape
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = badgeCount.toString(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MonkInk else MonkMuted
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tab Content
        when (selectedTab) {
            WebBlockerTab.DOMAINS -> {
                // Add Domain Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MonkCard),
                        modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Block a Website Domain",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MonkText
                                )
                            )
                            Text(
                                text = "Blocks domain resolution across all apps via local DNS proxy.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
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
                                    label = { Text("Domain (e.g. reddit.com)", color = MonkMuted) },
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

                // Private DNS Advisory Card
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
                                text = "If Android Private DNS is enabled, encrypted DoT (port 853) may bypass the local VPN. Set Private DNS to 'Off' for 100% domain blocking.",
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
                        EmptyStateCard("No websites blocked yet.", "Add a domain above to block distracting websites.")
                    }
                } else {
                    items(blockedDomains, key = { it.domain }) { domainEntity ->
                        BlockedDomainItemCard(
                            entity = domainEntity,
                            onToggleBlocked = { isBlocked ->
                                if (!isBlocked && isStrictModeActive) {
                                    Toast.makeText(context, "Unblocking websites is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                    return@BlockedDomainItemCard
                                }
                                scope.launch(Dispatchers.IO) {
                                    database.blockedDomainDao().upsertBlockedDomain(domainEntity.copy(isBlocked = isBlocked))
                                }
                            },
                            onDelete = {
                                if (isStrictModeActive) {
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

            WebBlockerTab.PATHS -> {
                // Add URL Path Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MonkCard),
                        modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Surgical URL Path Blocker",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MonkText
                                )
                            )
                            Text(
                                text = "Blocks specific URL paths (e.g. reddit.com/r/all) while keeping useful sections of the site accessible. Zero page content scraping.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customPathInput,
                                    onValueChange = { customPathInput = it },
                                    label = { Text("Path (e.g. /r/all or site.com/feed)", color = MonkMuted) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Button(
                                    onClick = {
                                        val trimmed = customPathInput.trim()
                                        if (trimmed.isNotEmpty()) {
                                            scope.launch(Dispatchers.IO) {
                                                database.blockedUrlPathDao().insertUrlPath(
                                                    BlockedUrlPathEntity(pathPattern = trimmed, isBlocked = true)
                                                )
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Added path: $trimmed", Toast.LENGTH_SHORT).show()
                                                    customPathInput = ""
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Blocked URL Paths (${blockedPaths.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MonkText
                        )
                    )
                }

                if (blockedPaths.isEmpty()) {
                    item {
                        EmptyStateCard("No URL paths blocked yet.", "Add a subpath or specific page to block addicting sections.")
                    }
                } else {
                    items(blockedPaths, key = { it.pathPattern }) { pathEntity ->
                        BlockedRuleItemCard(
                            title = pathEntity.pathPattern,
                            subtitle = if (pathEntity.isBlocked) "Blocked on 12+ browsers" else "Inactive",
                            isBlocked = pathEntity.isBlocked,
                            onToggleBlocked = { isBlocked ->
                                if (!isBlocked && isStrictModeActive) {
                                    Toast.makeText(context, "Unblocking URL paths is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                    return@BlockedRuleItemCard
                                }
                                scope.launch(Dispatchers.IO) {
                                    database.blockedUrlPathDao().insertUrlPath(pathEntity.copy(isBlocked = isBlocked))
                                }
                            },
                            onDelete = {
                                if (isStrictModeActive) {
                                    Toast.makeText(context, "Removing blocked URL paths is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                    return@BlockedRuleItemCard
                                }
                                scope.launch(Dispatchers.IO) {
                                    database.blockedUrlPathDao().deleteUrlPath(pathEntity.pathPattern)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Removed ${pathEntity.pathPattern}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            WebBlockerTab.KEYWORDS -> {
                // Add Keyword Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MonkCard),
                        modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Aho-Corasick Keyword Blocker",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MonkText
                                )
                            )
                            Text(
                                text = "Scans search engine queries and browser omnibox in O(N+M) time with delimiter boundary enforcement to eliminate Scunthorpe false positives.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customKeywordInput,
                                    onValueChange = { customKeywordInput = it },
                                    label = { Text("Keyword (e.g. vape, casino)", color = MonkMuted) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Button(
                                    onClick = {
                                        val trimmed = customKeywordInput.trim().lowercase()
                                        if (trimmed.isNotEmpty()) {
                                            scope.launch(Dispatchers.IO) {
                                                database.blockedKeywordDao().insertKeyword(
                                                    BlockedKeywordEntity(
                                                        keyword = trimmed,
                                                        category = "CUSTOM",
                                                        isBlocked = true
                                                    )
                                                )
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Added keyword: $trimmed", Toast.LENGTH_SHORT).show()
                                                    customKeywordInput = ""
                                                }
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk)
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Custom Blocked Keywords (${blockedKeywords.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MonkText
                        )
                    )
                }

                if (blockedKeywords.isEmpty()) {
                    item {
                        EmptyStateCard("No custom keywords blocked yet.", "Add keywords like 'casino' or 'vape' to block matching searches and URLs.")
                    }
                } else {
                    items(blockedKeywords, key = { it.keyword }) { kwEntity ->
                        BlockedRuleItemCard(
                            title = kwEntity.keyword,
                            subtitle = if (kwEntity.isBlocked) "Active • Word-boundary filtered" else "Inactive",
                            isBlocked = kwEntity.isBlocked,
                            onToggleBlocked = { isBlocked ->
                                if (!isBlocked && isStrictModeActive) {
                                    Toast.makeText(context, "Unblocking keywords is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                    return@BlockedRuleItemCard
                                }
                                scope.launch(Dispatchers.IO) {
                                    database.blockedKeywordDao().insertKeyword(kwEntity.copy(isBlocked = isBlocked))
                                }
                            },
                            onDelete = {
                                if (isStrictModeActive) {
                                    Toast.makeText(context, "Removing blocked keywords is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                    return@BlockedRuleItemCard
                                }
                                scope.launch(Dispatchers.IO) {
                                    database.blockedKeywordDao().deleteKeyword(kwEntity.keyword)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Removed ${kwEntity.keyword}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            WebBlockerTab.ADULT -> {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MonkCard),
                        modifier = Modifier.border(1.dp, MonkLine, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Adult Content Blocker",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MonkText
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isAdultBlockerEnabled) "Active • 100+ Adult Terms & Top Domains Blocked" else "Disabled",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (isAdultBlockerEnabled) MonkSage else MonkMuted,
                                            fontSize = 12.sp
                                        )
                                    )
                                }

                                Switch(
                                    checked = isAdultBlockerEnabled,
                                    onCheckedChange = { enabled ->
                                        if (!enabled && isStrictModeActive) {
                                            Toast.makeText(context, "Disabling Adult Content Blocker is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                            return@Switch
                                        }
                                        isAdultBlockerEnabled = enabled
                                        prefs.isAdultBlockerEnabled = enabled
                                        Toast.makeText(context, if (enabled) "Adult Blocker Enabled" else "Adult Blocker Disabled", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = MonkEmber,
                                        checkedBorderColor = MonkEmber,
                                        uncheckedThumbColor = MonkMuted,
                                        uncheckedTrackColor = MonkCardAlt,
                                        uncheckedBorderColor = MonkLine
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "What this blocks:",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MonkText
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            listOf(
                                "Major pornography websites & explicit domains",
                                "Explicit adult search queries across Google, Bing, Yahoo, DuckDuckGo",
                                "Subreddits and forum paths dedicated to adult media",
                                "Word-boundary collision protection (e.g. 'classic' and 'pass' are never blocked)"
                            ).forEach { itemText ->
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("✓ ", color = MonkSage, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = itemText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MonkMuted,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            WebBlockerTab.HISTORY -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Browsing Audit Log",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MonkText
                                )
                            )
                            Text(
                                text = "Dwell times & visited domains (Sanitized, 0 body data)",
                                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 11.sp)
                            )
                        }

                        if (browsingHistory.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    if (isStrictModeActive) {
                                        Toast.makeText(context, "Clearing history is prohibited while Strict Mode is active.", Toast.LENGTH_LONG).show()
                                        return@TextButton
                                    }
                                    scope.launch(Dispatchers.IO) {
                                        database.browsingHistoryDao().clearAll()
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Clear", color = MonkMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (browsingHistory.isEmpty()) {
                    item {
                        EmptyStateCard("No browsing history recorded yet.", "Visit websites in Chrome, Brave, or Firefox with Monk Mode active to record dwell metrics.")
                    }
                } else {
                    items(browsingHistory, key = { it.id }) { historyEntry ->
                        BrowsingHistoryCard(historyEntry)
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
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MonkEmber,
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
fun BlockedRuleItemCard(
    title: String,
    subtitle: String,
    isBlocked: Boolean,
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
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isBlocked) MonkDanger else MonkMuted,
                        fontSize = 11.sp
                    )
                )
            }

            Switch(
                checked = isBlocked,
                onCheckedChange = onToggleBlocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MonkEmber,
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
fun BrowsingHistoryCard(entry: BrowsingHistoryEntity) {
    val browserName = BrowserUrlBarRegistry.getBrowserConfig(entry.browserPackage)?.browserName
        ?: entry.browserPackage.substringAfterLast('.')
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = timeFormatter.format(Date(entry.timestampMs))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MonkCard),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MonkLine, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.domain,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText
                    )
                )

                Box(
                    modifier = Modifier
                        .background(
                            color = if (entry.wasBlocked) MonkDanger.copy(alpha = 0.15f) else MonkSage.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (entry.wasBlocked) "BLOCKED" else "VISITED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.wasBlocked) MonkDanger else MonkSage
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.fullUrl,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MonkMuted,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$browserName  •  $timeString",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MonkMuted.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                )

                Text(
                    text = if (entry.wasBlocked) {
                        "Rule: ${entry.blockReason ?: "matched"}"
                    } else {
                        "Dwell: ${formatDwellTime(entry.dwellTimeMs)}"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (entry.wasBlocked) MonkDanger.copy(alpha = 0.8f) else MonkText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(title: String, subtitle: String) {
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
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(color = MonkMuted)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted.copy(alpha = 0.6f), fontSize = 12.sp)
            )
        }
    }
}

private fun formatDwellTime(dwellMs: Long): String {
    if (dwellMs < 1000L) return "<1s"
    val seconds = dwellMs / 1000
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val remSec = seconds % 60
    return "${minutes}m ${remSec}s"
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
