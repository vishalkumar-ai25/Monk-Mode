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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.breaks.BreakDecisionEngine
import com.stayfocused.app.data.local.entities.BreakSessionEntity
import com.stayfocused.app.ui.TimeFormatter
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkCardAlt
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun TakeABreakCard(
    activeBreak: BreakSessionEntity?,
    onStartBreak: (Int) -> Unit,
    onEndBreak: () -> Unit,
    modifier: Modifier = Modifier
) {
    val breakEngine = remember { BreakDecisionEngine() }
    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Live countdown update ticker (only runs while break session is active)
    LaunchedEffect(activeBreak) {
        if (activeBreak != null && activeBreak.isActive && System.currentTimeMillis() < activeBreak.endTime) {
            while (isActive && System.currentTimeMillis() < activeBreak.endTime) {
                currentTimeMs = System.currentTimeMillis()
                delay(1000L)
            }
            currentTimeMs = System.currentTimeMillis()
        }
    }

    val isBreakActive = breakEngine.isBreakActive(
        currentTimeMs = currentTimeMs,
        breakSession = activeBreak,
        isStrictModeActive = false
    )
    val remainingSeconds = breakEngine.calculateRemainingSeconds(
        currentTimeMs = currentTimeMs,
        breakSession = activeBreak
    )
    val remainingMs = remainingSeconds * 1000L

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBreakActive) MonkCardAlt else MonkCard
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = MonkLine, shape = RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
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
                                color = if (isBreakActive) MonkEmber else MonkMuted,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isBreakActive) "Break In Progress" else "Take a Quick Break",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = MonkText
                        )
                    )
                }

                if (isBreakActive) {
                    Text(
                        text = TimeFormatter.formatRemainingTime(remainingMs),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MonkEmber
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isBreakActive) {
                    "Shields and website blocks are temporarily relaxed so you can take a breather."
                } else {
                    "Need a breather? Temporarily bypass app shields for a limited time (disabled in Strict Mode)."
                },
                style = MaterialTheme.typography.bodySmall.copy(color = MonkMuted)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (isBreakActive) {
                Button(
                    onClick = onEndBreak,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MonkDanger,
                        contentColor = MonkText
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("End Break Early & Re-Arm Shields", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickBreakButton(
                        label = "+5 min",
                        modifier = Modifier.weight(1f),
                        onClick = { onStartBreak(5) }
                    )
                    QuickBreakButton(
                        label = "+10 min",
                        modifier = Modifier.weight(1f),
                        onClick = { onStartBreak(10) }
                    )
                    QuickBreakButton(
                        label = "+15 min",
                        modifier = Modifier.weight(1f),
                        onClick = { onStartBreak(15) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickBreakButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MonkEmber
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine)
    ) {
        Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
