package com.stayfocused.app.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.domain.model.BlockReason
import com.stayfocused.app.domain.model.LimitType

private val OverlayDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6366F1), // Indigo 500
    onPrimary = Color.White,
    background = Color(0xFF0F172A), // Slate 900
    surface = Color(0xFF1E293B), // Slate 800
    onSurface = Color(0xFFF8FAFC),
    error = Color(0xFFEF4444)
)

@Composable
fun BlockOverlayContent(
    reason: BlockReason,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier,
    quote: String = remember { BlockOverlayQuotes.getRandomQuote() }
) {
    MaterialTheme(colorScheme = OverlayDarkColorScheme) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Shield Icon Badge
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🛡️",
                            fontSize = 38.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Title
                    Text(
                        text = getBlockTitle(reason),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subtitle / Reason Details
                    Text(
                        text = getBlockDetail(reason),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Quote Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "“$quote”",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // Return to Home Action Button
                    Button(
                        onClick = onReturnHome,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            text = "Return to Home",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

private fun getBlockTitle(reason: BlockReason): String {
    return when (reason) {
        is BlockReason.LimitReached -> {
            when (reason.limitType) {
                LimitType.TIME_LIMIT -> "Time Limit Reached"
                LimitType.LAUNCH_LIMIT -> "Launch Limit Reached"
            }
        }
        is BlockReason.ProfileActive -> "Focus Session Active"
        is BlockReason.SettingsTamper -> "Settings Protected"
        is BlockReason.ManuallyBlocked -> "App Blocked"
    }
}

private fun getBlockDetail(reason: BlockReason): String {
    return when (reason) {
        is BlockReason.LimitReached -> {
            when (reason.limitType) {
                LimitType.TIME_LIMIT -> {
                    val minutes = reason.limit / 60000L
                    "${reason.appName} has reached your daily allowance of ${minutes}m."
                }
                LimitType.LAUNCH_LIMIT -> {
                    "${reason.appName} has reached your daily launch limit of ${reason.limit} times."
                }
            }
        }
        is BlockReason.ProfileActive -> {
            "\"${reason.profileName}\" is currently active. Stay focused on your goals!"
        }
        is BlockReason.SettingsTamper -> {
            reason.message
        }
        is BlockReason.ManuallyBlocked -> {
            "${reason.appName} is manually blocked to protect your attention."
        }
    }
}
