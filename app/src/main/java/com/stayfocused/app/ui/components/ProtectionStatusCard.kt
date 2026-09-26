package com.stayfocused.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.domain.model.ProtectionCheckResult
import com.stayfocused.app.domain.model.ProtectionCheckStatus
import com.stayfocused.app.domain.model.ProtectionCheckType
import com.stayfocused.app.domain.model.ProtectionOverallStatus
import com.stayfocused.app.domain.model.ProtectionStatusSnapshot
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkInk
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText

/**
 * Monk Mode dashboard card displaying the health of the 4 core enforcement subsystems
 * with aggregate status badge and one-tap fix actions for any degraded or failing checks.
 */
@Composable
fun ProtectionStatusCard(
    snapshot: ProtectionStatusSnapshot,
    onFixCheck: (ProtectionCheckType) -> Unit,
    modifier: Modifier = Modifier
) {
    val overallStatus = snapshot.overallStatus

    val (badgeText, badgeColor, descriptionText) = when (overallStatus) {
        ProtectionOverallStatus.GREEN -> Triple(
            "SHIELDS ARMED",
            MonkSage,
            "All 4 background enforcement systems are actively protecting your focus."
        )
        ProtectionOverallStatus.AMBER -> Triple(
            "ATTENTION NEEDED",
            MonkEmber,
            "Protection is active, but performance may be degraded by system settings."
        )
        ProtectionOverallStatus.RED -> Triple(
            "COMPROMISED",
            MonkDanger,
            "One or more core protection shields are down. Focus cannot be fully enforced."
        )
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MonkCard),
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MonkLine, shape = RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row: Title & Aggregate Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Protection Status",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MonkText
                    )
                )

                Box(
                    modifier = Modifier
                        .background(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = badgeColor.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(badgeColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                letterSpacing = 0.8.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted, fontSize = 12.sp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subsystem Items List
            snapshot.checks.forEachIndexed { index, check ->
                ProtectionCheckRow(
                    check = check,
                    onFix = { onFixCheck(check.type) }
                )
                if (index < snapshot.checks.lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ProtectionCheckRow(
    check: ProtectionCheckResult,
    onFix: () -> Unit
) {
    val (symbol, color) = when (check.status) {
        ProtectionCheckStatus.PASS -> "✓" to MonkSage
        ProtectionCheckStatus.WARN -> "!" to MonkEmber
        ProtectionCheckStatus.FAIL -> "✕" to MonkDanger
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status symbol indicator
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = color.copy(alpha = 0.18f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and description
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MonkText
                )
            )
            Text(
                text = check.summary,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MonkMuted,
                    fontSize = 11.sp
                )
            )
        }

        // Action button for degraded or failed checks
        if (check.status != ProtectionCheckStatus.PASS && check.actionLabel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            val (btnBg, btnFg) = if (check.status == ProtectionCheckStatus.FAIL) {
                MonkDanger to MonkText
            } else {
                MonkEmber to MonkInk
            }

            Button(
                onClick = onFix,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnBg,
                    contentColor = btnFg
                )
            ) {
                Text(
                    text = check.actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
