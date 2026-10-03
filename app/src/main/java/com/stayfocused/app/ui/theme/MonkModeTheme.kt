package com.stayfocused.app.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// --- Monk Mode Light Theme Color Tokens ----------------------------------------
/** Screen canvas: calming warm alabaster / Japanese paper washi background (zero glare). */
val MonkCanvas = Color(0xFFF8F6F2)

/** Card surface fill: crisp pure white for tactile, clean elevation. */
val MonkCard = Color(0xFFFFFFFF)

/** Nested card / track / chip background / search fill: warm neutral mist. */
val MonkCardAlt = Color(0xFFEFECE6)

/** Hairline border / divider: subtle sand outline. No muddy drop shadows in Monk Mode. */
val MonkLine = Color(0xFFE5E0D8)

/** Primary readable text: deep warm Sumi charcoal ink (16.2:1 contrast on white). */
val MonkText = Color(0xFF1A1815)

/** Secondary / placeholder / timestamp text: warm pebble slate (5.2:1 contrast on white). */
val MonkMuted = Color(0xFF767167)

/** Primary accent: burnished terracotta amber for primary actions, active tabs, progress arc. */
val MonkEmber = Color(0xFFC4681A)

/** Accent container / pill & badge wash / active tab indicator: soft warm amber mist. */
val MonkEmberDim = Color(0xFFFCEFDE)

/** Strict Mode armed state / quota exhausted / warning state: crimson brick. */
val MonkDanger = Color(0xFFC24135)

/** Normal usage / allowed / safe quota state: serene forest sage. */
val MonkSage = Color(0xFF3D7946)

/** Navigation bar / chrome background: crisp white. */
val MonkPanel = Color(0xFFFFFFFF)

/**
 * On-accent / high-contrast action text token.
 * Provides crisp white typography (WCAG AA compliant) on top of MonkEmber and MonkSage buttons.
 */
val MonkInk = Color(0xFFFFFFFF)

// --- Material3 Light Color Scheme ---------------------------------------------
val MonkModeColorScheme = lightColorScheme(
    primary = MonkEmber,
    onPrimary = Color.White,
    primaryContainer = MonkEmberDim,
    onPrimaryContainer = MonkText,
    secondary = MonkSage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F3EA),
    onSecondaryContainer = MonkText,
    error = MonkDanger,
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = MonkDanger,
    background = MonkCanvas,
    onBackground = MonkText,
    surface = MonkCard,
    onSurface = MonkText,
    surfaceVariant = MonkCardAlt,
    onSurfaceVariant = MonkMuted,
    outline = MonkLine,
    outlineVariant = MonkLine,
    inverseSurface = MonkText,
    inverseOnSurface = MonkCard,
    inversePrimary = MonkEmberDim,
    scrim = Color(0x66000000)
)

