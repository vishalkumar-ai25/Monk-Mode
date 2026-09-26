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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import com.stayfocused.app.domain.FailsafeLogFormatter
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkCardAlt
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText

/**
 * Read-only Compose card rendering the append-only audit trail of all
 * anti-tamper failsafe interactions.
 * Intentionally contains zero edit or delete actions.
 */
@Composable
fun FailsafeLogCard(
    logs: List<FailsafeLogEntity>,
    modifier: Modifier = Modifier
) {
    val formatter = remember { FailsafeLogFormatter() }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MonkCard),
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MonkLine, shape = RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Failsafe Integrity Audit Log",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MonkText
                        )
                    )
                    Text(
                        text = "Immutable audit trail of failsafe layer interactions",
                        style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(MonkCardAlt, RoundedCornerShape(8.dp))
                        .border(1.dp, MonkLine, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${logs.size} event${if (logs.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MonkMuted,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MonkCardAlt.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, MonkLine, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No failsafe events recorded. All anti-tamper protections remain untouched.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted),
                        lineHeight = 18.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    logs.forEachIndexed { index, log ->
                        FailsafeLogRow(log = log, formatter = formatter)
                        if (index < logs.size - 1) {
                            HorizontalDivider(
                                color = MonkLine.copy(alpha = 0.5f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailsafeLogRow(
    log: FailsafeLogEntity,
    formatter: FailsafeLogFormatter
) {
    val statusColor = if (log.success) MonkSage else MonkDanger
    val statusLabel = formatter.getStatusLabel(log)
    val displayName = formatter.getEventDisplayName(log.eventType)
    val dateText = formatter.formatTimestamp(log.timestamp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Status indicator dot
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .background(statusColor, CircleShape)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MonkText
                    )
                )

                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MonkMuted,
                    fontSize = 11.sp
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = log.details,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MonkText.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            )
        }
    }
}
