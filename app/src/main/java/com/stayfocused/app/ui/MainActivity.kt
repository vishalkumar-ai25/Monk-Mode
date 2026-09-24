package com.stayfocused.app.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.strict.FailsafeManager
import com.stayfocused.app.ui.screens.AppLimitsScreen
import com.stayfocused.app.ui.screens.DashboardScreen
import com.stayfocused.app.ui.screens.NotificationVaultScreen
import com.stayfocused.app.ui.screens.StrictLockScreen
import com.stayfocused.app.ui.screens.WebBlockerScreen
import com.stayfocused.app.vpn.DnsVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NavTab(val title: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Default.Home),
    APP_LIMITS("Apps", Icons.Default.Settings),
    WEB_BLOCKER("Web", Icons.Default.Share),
    VAULT("Vault", Icons.Default.Notifications),
    STRICT("Strict", Icons.Default.Lock)
}

private val StayFocusedColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), // Indigo 500
    onPrimary = Color.White,
    background = Color(0xFF0F172A), // Slate 900
    surface = Color(0xFF1E293B), // Slate 800
    surfaceVariant = Color(0xFF334155), // Slate 700
    onSurface = Color(0xFFF8FAFC),
    error = Color(0xFFEF4444)
)

class MainActivity : ComponentActivity() {

    private lateinit var database: StayFocusedDatabase
    private lateinit var failsafeManager: FailsafeManager

    private var vpnStatusState = mutableStateOf(false)

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startDnsVpnService()
        } else {
            Toast.makeText(this, "VPN permission required for website filtering", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = StayFocusedDatabase.getInstance(applicationContext)
        failsafeManager = FailsafeManager(database.recoveryCodeDao(), database.strictSessionDao())

        setContent {
            MaterialTheme(colorScheme = StayFocusedColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigationHost(
                        database = database,
                        failsafeManager = failsafeManager,
                        isVpnRunning = vpnStatusState.value,
                        onToggleVpn = { toggleVpn() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vpnStatusState.value = DnsVpnService.isVpnRunning
    }

    private fun toggleVpn() {
        if (DnsVpnService.isVpnRunning) {
            lifecycleScope.launch(Dispatchers.IO) {
                val activeStrict = database.strictSessionDao().getActiveStrictSessionSync()
                if (activeStrict != null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Website Blocker cannot be stopped while Strict Mode is active.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    val stopIntent = Intent(this@MainActivity, DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_STOP
                    }
                    startService(stopIntent)
                    vpnStatusState.value = false
                    Toast.makeText(this@MainActivity, "Website Blocker Stopped", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                vpnPrepareLauncher.launch(vpnIntent)
            } else {
                startDnsVpnService()
            }
        }
    }

    private fun startDnsVpnService() {
        val startIntent = Intent(this, DnsVpnService::class.java).apply {
            action = DnsVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(this, startIntent)
        vpnStatusState.value = true
        Toast.makeText(this, "Website Blocker Active (Narrow 10.0.0.2/32)", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainNavigationHost(
    database: StayFocusedDatabase,
    failsafeManager: FailsafeManager,
    isVpnRunning: Boolean,
    onToggleVpn: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(NavTab.DASHBOARD) }
    val unviewedCount by database.suppressedNotificationDao().getUnviewedCount().collectAsState(initial = 0)

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ) {
                NavTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            if (tab == NavTab.VAULT && unviewedCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = Color(0xFF38BDF8),
                                            contentColor = Color(0xFF0F172A)
                                        ) {
                                            Text(
                                                text = if (unviewedCount > 99) "99+" else "$unviewedCount",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF38BDF8),
                            selectedTextColor = Color(0xFF38BDF8),
                            indicatorColor = Color(0xFF1E293B),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavTab.DASHBOARD -> DashboardScreen(database = database)
                NavTab.APP_LIMITS -> AppLimitsScreen(database = database)
                NavTab.WEB_BLOCKER -> WebBlockerScreen(
                    database = database,
                    isVpnRunning = isVpnRunning,
                    onToggleVpn = onToggleVpn
                )
                NavTab.VAULT -> NotificationVaultScreen(database = database)
                NavTab.STRICT -> StrictLockScreen(
                    database = database,
                    failsafeManager = failsafeManager
                )
            }
        }
    }
}
