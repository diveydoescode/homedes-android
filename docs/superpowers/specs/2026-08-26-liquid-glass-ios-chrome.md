# Liquid Glass + iOS chrome parity (Android)

**Date:** 2026-08-26  
**Status:** Approved direction (user): upgrade toolchain for Kyant Backdrop 2.0.1; layout/chrome structure match iOS; glass on Layer-2 chrome only.

## Goals

1. Integrate [Kyant Backdrop / AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) (`io.github.kyant0:backdrop:2.0.1`).
2. Apply liquid glass to **navigation chrome only** (iOS ui-spec Layer 2): editor top strip, bottom dock, dashboard FAB — never on the plan canvas.
3. Align information architecture with iOS where chrome differs most:
   - New design sheet → Blank / From sketch / From template (imports via overflow).
   - Editor chrome → single strip + two-tier dock (iterative).

## Toolchain (landed)

| Piece | Target |
|-------|--------|
| Gradle | 9.3.1 (AGP 9.1 requires ≥9.3.1) |
| AGP | 9.1.0 (built-in Kotlin; no `kotlin-android` plugin) |
| Kotlin / Compose plugin | 2.4.10 |
| KSP | 2.3.11 |
| Compose BOM | 2026.08.00 |
| Hilt | 2.60.1 |
| compileSdk | 37 (platform `android-37` / `android-37.0`) |
| Backdrop | `io.github.kyant0:backdrop:2.0.1` |

**Note:** `services.gradle.org` downloads can time out on this machine; wrapper may use a local `file://` zip of Gradle 9.3.1. Prefer HTTPS when network allows.

## Glass pattern

```kotlin
val backdrop = rememberLayerBackdrop {
    drawRect(paperColor)
    drawContent()
}
CanvasContent(Modifier.layerBackdrop(backdrop))
ChromePill(Modifier.drawBackdrop(backdrop, shape = { RoundedCornerShape(14.dp) }, effects = {
    vibrancy(); blur(4.dp.toPx()); lens(16.dp.toPx(), 32.dp.toPx())
}, onDrawSurface = { drawRect(Color.White.copy(alpha = 0.45f)) }))
```

Fallback below API 33 (if RuntimeShader unavailable): translucent Material surface (same shape), no lens.

## Out of scope (this pass)

- Pixel-identical iOS 26 Liquid Glass morph animations
- Catalog mesh packs / Vercel sketch deploy
- Full split-canvas default (follow-up phase)
