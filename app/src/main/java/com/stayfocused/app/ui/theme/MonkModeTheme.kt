package com.stayfocused.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// --- Monk Mode Color Tokens ---------------------------------------------------
/** Deepest background: warm near-black ink. */
val MonkInk = Color(0xFF14120F)

/** Navigation bar / chrome background. */
val MonkPanel = Color(0xFF1D1B16)

/** Card surface fill. */
val MonkCard = Color(0xFF24221C)

/** Nested card / track background. */
val MonkCardAlt = Color(0xFF2C2A22)

/** Hairline border / divider. No elevation shadows in Monk Mode. */
val MonkLine = Color(0xFF39362C)

/** Primary readable text. */
val MonkText = Color(0xFFECE7DC)

/** Secondary / placeholder text. */
val MonkMuted = Color(0xFF9C978A)

/** Single warm-amber accent: primary actions, active nav tab, progress arc. */
val MonkEmber = Color(0xFFD98E3F)

/** Accent container / Switch "on" track background. */
val MonkEmberDim = Color(0xFF5E4826)

/** Strict Mode armed state / high-usage ring segment. */
val MonkDanger = Color(0xFFC1544B)

/** Low-usage ring / allowed / unblocked states. */
val MonkSage = Color(0xFF8AA980)

// --- Material3 dark color scheme ---------------------------------------------
val MonkModeColorScheme = darkColorScheme(
    primary = MonkEmber,
    onPrimary = MonkInk,
    primaryContainer = MonkEmberDim,
    onPrimaryContainer = MonkText,
    secondary = MonkSage,
    onSecondary = MonkInk,
    secondaryContainer = Color(0xFF2A3828),
    onSecondaryContainer = MonkText,
    error = MonkDanger,
    onError = MonkText,
    errorContainer = Color(0xFF4A1A17),
    onErrorContainer = MonkText,
    background = MonkInk,
    onBackground = MonkText,
    surface = MonkCard,
    onSurface = MonkText,
    surfaceVariant = MonkCardAlt,
    onSurfaceVariant = MonkMuted,
    outline = MonkLine,
    outlineVariant = MonkLine,
    inverseSurface = MonkText,
    inverseOnSurface = MonkInk,
    inversePrimary = MonkEmberDim,
    scrim = MonkInk
)
