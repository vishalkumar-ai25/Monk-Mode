package com.stayfocused.app.ui.screens

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Surfing
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.PomodoroSettingsEntity
import com.stayfocused.app.data.local.entities.SoundStemEntity
import com.stayfocused.app.domain.model.PomodoroConfig
import com.stayfocused.app.domain.model.PomodoroPhase
import com.stayfocused.app.domain.model.PomodoroState
import com.stayfocused.app.service.FocusSoundService
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

@Composable
fun PomodoroSoundscapeScreen(
    database: StayFocusedDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val pomodoroState by FocusSoundService.pomodoroStateFlow.collectAsState()
    val isSoundscapeActive by FocusSoundService.isSoundscapeActiveFlow.collectAsState()
    val soundStems by database.soundStemDao().getAllSoundStemsFlow().collectAsState(initial = emptyList())
    val settingsEntity by database.pomodoroDao().getSettingsFlow().collectAsState(initial = null)
    val profiles by database.focusProfileDao().getAllProfiles().collectAsState(initial = emptyList())

    val config = remember(settingsEntity) {
        settingsEntity?.let {
            PomodoroConfig(
                workMinutes = it.workMinutes,
                shortBreakMinutes = it.shortBreakMinutes,
                longBreakMinutes = it.longBreakMinutes,
                longBreakInterval = it.longBreakInterval,
                autoStartBreaks = it.autoStartBreaks,
                autoStartWork = it.autoStartWork,
                isDndEnabled = it.isDndEnabled,
                linkedProfileId = it.linkedProfileId
            )
        } ?: PomodoroConfig()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Focus Sanctuary",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MonkText
                    )
                    Text(
                        text = "Pomodoro intervals & offline ambient mixer",
                        fontSize = 12.sp,
                        color = MonkMuted
                    )
                }

                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MonkCardAlt)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Timer Settings",
                        tint = MonkText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Segmented Tab Row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MonkCardAlt,
                contentColor = MonkEmber,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MonkEmber,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Study Timer",
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    },
                    selectedContentColor = MonkEmber,
                    unselectedContentColor = MonkMuted
                )

                val audibleCount = soundStems.count { it.isEnabled && it.volume > 0f }
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (audibleCount > 0) "Soundscape ($audibleCount)" else "Soundscape",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    },
                    selectedContentColor = MonkEmber,
                    unselectedContentColor = MonkMuted
                )
            }
        }

        // Tab Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTabIndex) {
                0 -> PomodoroTimerTab(
                    state = pomodoroState,
                    config = config,
                    onStart = { FocusSoundService.startWork(context, config) },
                    onPause = { FocusSoundService.pause(context) },
                    onResume = { FocusSoundService.resume(context) },
                    onSkip = { FocusSoundService.skip(context) },
                    onReset = { FocusSoundService.reset(context) },
                    onSelectPreset = { work, sBreak, lBreak ->
                        scope.launch(Dispatchers.IO) {
                            val updated = (settingsEntity ?: PomodoroSettingsEntity()).copy(
                                workMinutes = work,
                                shortBreakMinutes = sBreak,
                                longBreakMinutes = lBreak
                            )
                            database.pomodoroDao().saveSettings(updated)
                        }
                    }
                )

                1 -> AmbientSoundscapeTab(
                    soundStems = soundStems,
                    onUpdateStem = { stemId, vol, enabled ->
                        FocusSoundService.updateStem(context, stemId, vol, enabled)
                    },
                    onUpdateMasterVol = { masterVol ->
                        FocusSoundService.updateMasterVolume(context, masterVol)
                    },
                    onApplyMixPreset = { preset ->
                        scope.launch(Dispatchers.IO) {
                            soundStems.forEach { stem ->
                                val targetVol = preset[stem.stemId] ?: 0f
                                val isEnabled = targetVol > 0f
                                FocusSoundService.updateStem(context, stem.stemId, targetVol, isEnabled)
                            }
                        }
                    }
                )
            }
        }
    }

    if (showSettingsDialog) {
        PomodoroSettingsDialog(
            currentConfig = config,
            profiles = profiles,
            onDismiss = { showSettingsDialog = false },
            onSave = { updatedConfig ->
                scope.launch(Dispatchers.IO) {
                    val entity = PomodoroSettingsEntity(
                        id = 1,
                        workMinutes = updatedConfig.workMinutes,
                        shortBreakMinutes = updatedConfig.shortBreakMinutes,
                        longBreakMinutes = updatedConfig.longBreakMinutes,
                        longBreakInterval = updatedConfig.longBreakInterval,
                        autoStartBreaks = updatedConfig.autoStartBreaks,
                        autoStartWork = updatedConfig.autoStartWork,
                        isDndEnabled = updatedConfig.isDndEnabled,
                        linkedProfileId = updatedConfig.linkedProfileId
                    )
                    database.pomodoroDao().saveSettings(entity)
                }
                showSettingsDialog = false
            }
        )
    }
}

// =================================================================================
// Tab 1: Pomodoro Study Timer
// =================================================================================

@Composable
fun PomodoroTimerTab(
    state: PomodoroState,
    config: PomodoroConfig,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onSelectPreset: (work: Int, sBreak: Int, lBreak: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))

            // Main Circular Countdown Dial
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(280.dp)
                    .padding(16.dp)
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = if (state.phase == PomodoroPhase.IDLE) 0f else state.progressFraction,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                    label = "PomodoroProgress"
                )

                val phaseColor = when (state.phase) {
                    PomodoroPhase.WORK -> MonkEmber
                    PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> MonkSage
                    PomodoroPhase.PAUSED -> MonkMuted
                    PomodoroPhase.IDLE -> MonkEmber
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)

                    // Track
                    drawArc(
                        color = MonkCardAlt,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Elapsed Arc
                    val sweepAngle = 360f * animatedProgress
                    if (sweepAngle > 0f) {
                        drawArc(
                            color = phaseColor,
                            startAngle = -90f,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Centered Information
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Phase Tag
                    val (tagText, tagBg, tagColor) = when (state.phase) {
                        PomodoroPhase.WORK -> Triple("FOCUS INTERVAL", MonkEmberDim, MonkEmber)
                        PomodoroPhase.SHORT_BREAK -> Triple("SHORT BREAK", Color(0xFFE8F3EA), MonkSage)
                        PomodoroPhase.LONG_BREAK -> Triple("LONG BREAK", Color(0xFFE8F3EA), MonkSage)
                        PomodoroPhase.PAUSED -> Triple("PAUSED", MonkCardAlt, MonkMuted)
                        PomodoroPhase.IDLE -> Triple("READY", MonkCardAlt, MonkText)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(tagBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = tagText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = tagColor,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Remaining Time
                    val displayTime = if (state.phase == PomodoroPhase.IDLE) {
                        String.format(java.util.Locale.getDefault(), "%02d:00", config.workMinutes)
                    } else {
                        state.formattedRemainingTime
                    }

                    Text(
                        text = displayTime,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MonkText
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cycle Indicator Dots
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val totalDots = config.longBreakInterval.coerceIn(2, 6)
                        val completedInCycle = (state.currentCycle - 1).coerceAtLeast(0)

                        for (i in 0 until totalDots) {
                            val isCompleted = i < completedInCycle
                            val isCurrent = i == completedInCycle && state.phase != PomodoroPhase.IDLE
                            val dotColor = when {
                                isCurrent -> phaseColor
                                isCompleted -> phaseColor.copy(alpha = 0.6f)
                                else -> MonkLine
                            }
                            Box(
                                modifier = Modifier
                                    .size(if (isCurrent) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cycle ${state.currentCycle} of ${config.longBreakInterval}",
                        fontSize = 12.sp,
                        color = MonkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Primary Action Button (Start / Pause / Resume)
            val isRunning = state.isRunning
            val isPaused = state.phase == PomodoroPhase.PAUSED

            Button(
                onClick = {
                    when {
                        isRunning -> onPause()
                        isPaused -> onResume()
                        else -> onStart()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MonkCardAlt else MonkEmber,
                    contentColor = if (isRunning) MonkText else MonkInk
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = when {
                        isRunning -> Icons.Default.Pause
                        isPaused -> Icons.Default.PlayArrow
                        else -> Icons.Default.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        isRunning -> "Pause Focus Interval"
                        isPaused -> "Resume Timer"
                        else -> "Start Focus Session"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Controls (Skip, Reset)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    enabled = state.phase != PomodoroPhase.IDLE,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkText),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Skip Phase", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onReset,
                    enabled = state.phase != PomodoroPhase.IDLE,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MonkDanger),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Quick Presets Row
            Text(
                text = "FOCUS INTERVAL PRESETS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MonkMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetChip(
                    title = "25 / 5",
                    subtitle = "Standard",
                    isSelected = config.workMinutes == 25 && config.shortBreakMinutes == 5,
                    onClick = { onSelectPreset(25, 5, 15) },
                    modifier = Modifier.weight(1f)
                )
                PresetChip(
                    title = "50 / 10",
                    subtitle = "Extended",
                    isSelected = config.workMinutes == 50 && config.shortBreakMinutes == 10,
                    onClick = { onSelectPreset(50, 10, 20) },
                    modifier = Modifier.weight(1f)
                )
                PresetChip(
                    title = "90 / 20",
                    subtitle = "Deep Work",
                    isSelected = config.workMinutes == 90 && config.shortBreakMinutes == 20,
                    onClick = { onSelectPreset(90, 20, 30) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Focus Profile Link Notice
            if (config.linkedProfileId != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MonkEmberDim),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MonkEmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Focus Profile Linked",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonkText
                            )
                            Text(
                                text = "Distracting apps & websites automatically block during work intervals and disarm on breaks.",
                                fontSize = 11.sp,
                                color = MonkMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun PresetChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MonkEmberDim else MonkCard
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) MonkEmber else MonkLine
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MonkEmber else MonkText
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MonkMuted
            )
        }
    }
}

// =================================================================================
// Tab 2: Ambient Soundscape Mixer
// =================================================================================

@Composable
fun AmbientSoundscapeTab(
    soundStems: List<SoundStemEntity>,
    onUpdateStem: (stemId: String, volume: Float, isEnabled: Boolean) -> Unit,
    onUpdateMasterVol: (volume: Float) -> Unit,
    onApplyMixPreset: (preset: Map<String, Float>) -> Unit,
    modifier: Modifier = Modifier
) {
    var masterVolume by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))

            // Master Volume Controller Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MonkCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MonkLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isMuted || masterVolume == 0f) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MonkEmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Master Sound Volume",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MonkText
                                )
                                Text(
                                    text = if (isMuted) "Muted" else "${(masterVolume * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = MonkMuted
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                val target = if (isMuted) 0f else masterVolume
                                onUpdateMasterVol(target)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) MonkCardAlt else MonkEmberDim)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Mute Toggle",
                                tint = if (isMuted) MonkMuted else MonkEmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = if (isMuted) 0f else masterVolume,
                        onValueChange = { newVal ->
                            isMuted = false
                            masterVolume = newVal
                            onUpdateMasterVol(newVal)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MonkEmber,
                            activeTrackColor = MonkEmber,
                            inactiveTrackColor = MonkCardAlt
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mix Presets Header
            Text(
                text = "CURATED ACOUSTIC PRESETS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MonkMuted,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Presets Scrollable Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    MixPresetChip(
                        name = "Deep Study",
                        desc = "Brown + Rain",
                        onClick = {
                            onApplyMixPreset(mapOf("brownnoise" to 0.6f, "rain" to 0.4f))
                        }
                    )
                }
                item {
                    MixPresetChip(
                        name = "Ocean Storm",
                        desc = "Waves + Rain",
                        onClick = {
                            onApplyMixPreset(mapOf("waves" to 0.75f, "rain" to 0.5f))
                        }
                    )
                }
                item {
                    MixPresetChip(
                        name = "Cabin Fire",
                        desc = "Campfire + Forest",
                        onClick = {
                            onApplyMixPreset(mapOf("campfire" to 0.8f, "forest" to 0.35f))
                        }
                    )
                }
                item {
                    MixPresetChip(
                        name = "Quiet Focus",
                        desc = "White + Forest",
                        onClick = {
                            onApplyMixPreset(mapOf("whitenoise" to 0.4f, "forest" to 0.25f))
                        }
                    )
                }
                item {
                    MixPresetChip(
                        name = "Mute All",
                        desc = "Reset Stems",
                        isDanger = true,
                        onClick = {
                            onApplyMixPreset(emptyMap())
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "INDIVIDUAL AMBIENT STEMS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MonkMuted,
                letterSpacing = 1.sp
            )
        }

        // 6 Sound Stem Cards
        items(soundStems, key = { it.stemId }) { stem ->
            SoundStemCard(
                stem = stem,
                onStateChanged = { newVolume, isEnabled ->
                    onUpdateStem(stem.stemId, newVolume, isEnabled)
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun MixPresetChip(
    name: String,
    desc: String,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isDanger) Color(0xFFFCE8E6) else MonkCard),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDanger) MonkDanger.copy(alpha = 0.4f) else MonkLine),
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDanger) MonkDanger else MonkText
            )
            Text(
                text = desc,
                fontSize = 10.sp,
                color = MonkMuted
            )
        }
    }
}

@Composable
fun SoundStemCard(
    stem: SoundStemEntity,
    onStateChanged: (volume: Float, isEnabled: Boolean) -> Unit
) {
    val isPlaying = stem.isEnabled && stem.volume > 0f

    val (stemIcon, stemDesc) = when (stem.stemId) {
        "rain" -> Pair(Icons.Default.WaterDrop, "Soothing rainfall with gentle distant thunder")
        "waves" -> Pair(Icons.Default.Surfing, "Rhythmic oceanic surf rolling against pebbles")
        "forest" -> Pair(Icons.Default.Forest, "Gentle mountain wind rustling pine needles")
        "whitenoise" -> Pair(Icons.Default.Air, "Balanced full-spectrum white acoustic mask")
        "brownnoise" -> Pair(Icons.Default.FilterDrama, "Deep low-frequency waterfall resonance")
        "campfire" -> Pair(Icons.Default.LocalFireDepartment, "Crackling logs and glowing embers")
        else -> Pair(Icons.Default.GraphicEq, "Ambient background texture")
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MonkEmberDim.copy(alpha = 0.4f) else MonkCard
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPlaying) MonkEmber.copy(alpha = 0.5f) else MonkLine
        ),
        modifier = Modifier.fillMaxWidth()
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
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) MonkEmber else MonkCardAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = stemIcon,
                            contentDescription = stem.title,
                            tint = if (isPlaying) MonkInk else MonkText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stem.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonkText
                            )
                            if (isPlaying) {
                                Spacer(modifier = Modifier.width(8.dp))
                                StemWaveformIndicator()
                            }
                        }
                        Text(
                            text = stemDesc,
                            fontSize = 11.sp,
                            color = MonkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = stem.isEnabled,
                    onCheckedChange = { checked ->
                        val newVol = if (checked && stem.volume == 0f) 0.5f else stem.volume
                        onStateChanged(newVol, checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MonkInk,
                        checkedTrackColor = MonkEmber,
                        uncheckedThumbColor = MonkMuted,
                        uncheckedTrackColor = MonkCardAlt
                    )
                )
            }

            if (stem.isEnabled) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = null,
                        tint = MonkMuted,
                        modifier = Modifier.size(16.dp)
                    )

                    Slider(
                        value = stem.volume,
                        onValueChange = { newVol ->
                            onStateChanged(newVol, true)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MonkEmber,
                            activeTrackColor = MonkEmber,
                            inactiveTrackColor = MonkCardAlt
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    Text(
                        text = "${(stem.volume * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MonkMuted,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
fun StemWaveformIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformAnim")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.height(14.dp)
    ) {
        Box(modifier = Modifier.width(2.5.dp).height(h1.dp).clip(CircleShape).background(MonkEmber))
        Box(modifier = Modifier.width(2.5.dp).height(h2.dp).clip(CircleShape).background(MonkEmber))
        Box(modifier = Modifier.width(2.5.dp).height(h3.dp).clip(CircleShape).background(MonkEmber))
    }
}

// =================================================================================
// Configuration Dialog: Custom Times, Auto-Start, DND, Profile Link
// =================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroSettingsDialog(
    currentConfig: PomodoroConfig,
    profiles: List<FocusProfileEntity>,
    onDismiss: () -> Unit,
    onSave: (PomodoroConfig) -> Unit
) {
    var workMins by remember { mutableIntStateOf(currentConfig.workMinutes) }
    var shortBreakMins by remember { mutableIntStateOf(currentConfig.shortBreakMinutes) }
    var longBreakMins by remember { mutableIntStateOf(currentConfig.longBreakMinutes) }
    var longBreakInterval by remember { mutableIntStateOf(currentConfig.longBreakInterval) }
    var autoStartBreaks by remember { mutableStateOf(currentConfig.autoStartBreaks) }
    var autoStartWork by remember { mutableStateOf(currentConfig.autoStartWork) }
    var isDndEnabled by remember { mutableStateOf(currentConfig.isDndEnabled) }
    var selectedProfileId by remember { mutableStateOf(currentConfig.linkedProfileId) }
    var profileDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Timer & Focus Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MonkText
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    // Work Duration Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Work Interval", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MonkText)
                            Text("$workMins min", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MonkEmber)
                        }
                        Slider(
                            value = workMins.toFloat(),
                            onValueChange = { workMins = it.toInt() },
                            valueRange = 5f..120f,
                            steps = 22,
                            colors = SliderDefaults.colors(thumbColor = MonkEmber, activeTrackColor = MonkEmber)
                        )
                    }

                    // Short Break Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Short Break", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MonkText)
                            Text("$shortBreakMins min", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MonkSage)
                        }
                        Slider(
                            value = shortBreakMins.toFloat(),
                            onValueChange = { shortBreakMins = it.toInt() },
                            valueRange = 1f..30f,
                            steps = 28,
                            colors = SliderDefaults.colors(thumbColor = MonkSage, activeTrackColor = MonkSage)
                        )
                    }

                    // Long Break Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Long Break", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MonkText)
                            Text("$longBreakMins min", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MonkSage)
                        }
                        Slider(
                            value = longBreakMins.toFloat(),
                            onValueChange = { longBreakMins = it.toInt() },
                            valueRange = 5f..60f,
                            steps = 10,
                            colors = SliderDefaults.colors(thumbColor = MonkSage, activeTrackColor = MonkSage)
                        )
                    }

                    // Long Break Interval
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Long Break Interval", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MonkText)
                            Text("Every $longBreakInterval cycles", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MonkText)
                        }
                        Slider(
                            value = longBreakInterval.toFloat(),
                            onValueChange = { longBreakInterval = it.toInt() },
                            valueRange = 2f..6f,
                            steps = 3,
                            colors = SliderDefaults.colors(thumbColor = MonkEmber, activeTrackColor = MonkEmber)
                        )
                    }

                    // Auto-start Switches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-start Breaks", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MonkText)
                            Text("Begin rest interval automatically", fontSize = 11.sp, color = MonkMuted)
                        }
                        Switch(
                            checked = autoStartBreaks,
                            onCheckedChange = { autoStartBreaks = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MonkInk, checkedTrackColor = MonkEmber)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-start Focus", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MonkText)
                            Text("Begin next cycle automatically after break", fontSize = 11.sp, color = MonkMuted)
                        }
                        Switch(
                            checked = autoStartWork,
                            onCheckedChange = { autoStartWork = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MonkInk, checkedTrackColor = MonkEmber)
                        )
                    }

                    // Do Not Disturb Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Do Not Disturb (DND)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MonkText)
                            Text("Silence ringers during focus intervals", fontSize = 11.sp, color = MonkMuted)
                        }
                        Switch(
                            checked = isDndEnabled,
                            onCheckedChange = { isDndEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MonkInk, checkedTrackColor = MonkEmber)
                        )
                    }

                    // Linked Focus Profile Picker
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Linked Focus Profile",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MonkText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }

                        ExposedDropdownMenuBox(
                            expanded = profileDropdownExpanded,
                            onExpandedChange = { profileDropdownExpanded = !profileDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedProfile?.name ?: "None (No auto-blocking)",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileDropdownExpanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MonkEmber,
                                    unfocusedBorderColor = MonkLine
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            )

                            ExposedDropdownMenu(
                                expanded = profileDropdownExpanded,
                                onDismissRequest = { profileDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None (Timer Only)", color = MonkMuted) },
                                    onClick = {
                                        selectedProfileId = null
                                        profileDropdownExpanded = false
                                    }
                                )
                                profiles.forEach { profile ->
                                    DropdownMenuItem(
                                        text = { Text(profile.name, fontWeight = FontWeight.Medium) },
                                        onClick = {
                                            selectedProfileId = profile.id
                                            profileDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = currentConfig.copy(
                        workMinutes = workMins,
                        shortBreakMinutes = shortBreakMins,
                        longBreakMinutes = longBreakMins,
                        longBreakInterval = longBreakInterval,
                        autoStartBreaks = autoStartBreaks,
                        autoStartWork = autoStartWork,
                        isDndEnabled = isDndEnabled,
                        linkedProfileId = selectedProfileId
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MonkEmber, contentColor = MonkInk),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cancel", color = MonkText)
            }
        },
        containerColor = MonkCard,
        shape = RoundedCornerShape(20.dp)
    )
}
