# ADR 010: Monk Mode Rebranding & Laser-Focused Zen Iconography

## Status
Accepted

## Context
1. The app was previously labeled "Stay Focused", causing user confusion alongside commercial apps installed from the Play Store with identical titles.
2. The user requested rebranding the app name strictly to **Monk Mode** and introducing an iconography depicting a monk working in a silent environment with laser-like focus and 100% attention.
3. In Android 8.0+ (API 26+), app icons must comply with the Adaptive Icon standard (`108dp x 108dp` canvas with an inner `72dp x 72dp` masked safe viewport), while maintaining fallback squircle and circular raster assets for legacy launchers.

## Decisions
1. **Application Display Name:**
   - Change `app_name` resource in `strings.xml` to `"Monk Mode"`.
   - Ensure `AndroidManifest.xml` references `@string/app_name` for `<application>`.
   - Maintain anti-tamper domain protection in `SettingsTamperInspector` which already guards `"monk mode"`, `"stay focused"`, and `"stayfocused"`.

2. **Adaptive Icon Assets:**
   - Use high-resolution digital art generated with generative imaging depicting a Buddhist monk in a serene, silent monastery sanctuary with intense laser-like focus into his workstation.
   - Separate and export into:
     - `ic_launcher_background.png`: Deep obsidian/navy ambient monastery slate background matching canvas perimeter.
     - `ic_launcher_foreground.png`: Centered monk with golden halo, twin laser beam focus, and workstation scaled appropriately to fit securely inside the 72dp safe zone across circular, squircle, and rounded-rectangle OEM masks.
     - `ic_launcher.png`: Legacy pre-API 26 rounded squircle launcher icon.
     - `ic_launcher_round.png`: Legacy pre-API 26 circular launcher icon.
   - Generate all raster densities across `mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, and `xxxhdpi`.

3. **Backward Compatibility & System Integration:**
   - Retain `applicationId` as `com.stayfocused.app` to prevent database wiped or broken Android OS permissions/device admin bindings.
   - Preserve `FocusGlanceWidgetReceiver` and QS Tiles referencing `@string/app_name`.

## Consequences
- The launcher title cleanly reads "Monk Mode" across the home screen, recent apps list, and OS settings.
- The app icon visually communicates deep work, digital silence, and laser focus.
- Parallax effects and OEM mask cutouts function flawlessly on all Android launcher implementations (Pixel, ColorOS/Realme, OneUI, MIUI).
