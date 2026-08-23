# HomeDesign Android — Resume State

**Last updated:** 2026-08-24  
**Repo:** https://github.com/diveydoescode/homedes-android (`main`)  
**Local path:** `C:\webapp_android\homedes-android`  
**Companion sources:**
- Web (Stage 1 oracle): `C:\webapp_android\homedes-webapp` → https://github.com/diveydoescode/homedes-webapp
- iOS (UI / full-product reference): `C:\webapp_android\homedesign-ios` → https://github.com/diveydoescode/homedesign

When the user says **“read resume.md”**, treat this file as the source of truth for progress, scope, and next work. Do not re-analyze from scratch unless something here conflicts with the tree.

---

## 1. Goal (as agreed)

1. Native Android port of **HomeDesign** (not a WebView wrapper).
2. **Feature ceiling for Stage 1 = webapp** (`homedes-webapp`).
3. **iOS** used for UI/chrome analogy and for understanding later full-product scope — not required for Stage 1 finish.
4. Sketch convert talks to Vercel proxy (no API key in the app): default `https://homedes-webapp.vercel.app`.
5. Stack (mandatory): Kotlin, Compose + Material 3, Clean Architecture + MVVM, Navigation Compose, Retrofit + OkHttp + Kotlin Serialization, Hilt, Room + DataStore, Coil, Coroutines/Flow, minSdk 26, targetSdk 35.

**User clarification:** they want full conversion / working features; “same level as iOS” is a later horizon. Close **web Stage 1** first, then iOS 2D chrome, then 3D/AR.

---

## 2. What’s done (verified)

### Build / delivery
- [x] Android project scaffolded and pushed to GitHub
- [x] `.\gradlew.bat :app:assembleDebug` — **BUILD SUCCESSFUL**
- [x] `.\gradlew.bat :app:testDebugUnitTest` — **BUILD SUCCESSFUL**
- [x] App **runs on device** (user confirmed: “at least the app is running and needs work”)
- [x] JDK 17 (Microsoft) + Android SDK 35 cmdline tools installed on this machine
- [x] Docs: `README.md`, `docs/WEB_TO_ANDROID_MAPPING.md`, `docs/superpowers/specs/2026-03-24-homedes-android-design.md`

### Product surface shipped
- [x] Journey: Splash → Landing → Onboarding → Setup → Auth → Dashboard
- [x] Auth: Google **stub** (same as web) + email/skip → `hasOnboarded` in DataStore
- [x] Dashboard: search/sort, blank project, open, delete; Room stores archives + thumbs
- [x] Editor: plan canvas (grid, rooms, mitered walls, furniture boxes, openings, dims), pan/pinch, select, draw wall, draw room, place catalog furniture, undo/redo, ~3s autosave, thin property sheet, export DXF/PDF/`.homedesign` via FileProvider
- [x] Domain: models, catalog (~30), geom kernel subset, undo stack, `.homedesign` zip codec
- [x] Sketch flow UI + Retrofit `SketchApi` wired to `BuildConfig.SKETCH_BASE_URL`
- [x] Theme: HD editorial + architect tokens (terracotta / selection blue, light/dark)
- [x] Assets copied: `app/src/main/assets/svg/`, `assets/textures/`

### Package layout
```
app/src/main/java/com/homedesign/android/
  presentation/   screens, editor canvas, navigation
  domain/         model, geom, editor, catalog, io, export, project, settings
  data/           Room, DataStore, Retrofit, repository impls
  di/             Hilt modules
  core/ui/        theme + shared Compose chrome
```

### Known runtime notes
- `installDebug` can fail / kill Gradle daemon if **no device/emulator** is connected. Prefer:
  ```powershell
  adb devices
  adb install -r .\app\build\outputs\apk\debug\app-debug.apk
  adb shell am start -n com.homedesign.android.debug/com.homedesign.android.MainActivity
  ```
- Debug applicationId is `com.homedesign.android.debug` (suffix).
- Sketch: live site serves SPA; `/api/sketch` was **404 / flaky** when probed. Override with `sketch.base.url` in `local.properties` when proxy is healthy.

---

## 3. Honest completion estimate

| Target | Approx status |
|--------|----------------|
| App runs, basic edit/save | **Done** |
| = web Stage 1 (`homedes-webapp`) | **~40–50%** |
| = full iOS (`homedesign`) | **~15–20%** |

---

## 4. Remaining work — Tier 1 (web Stage 1 parity) — **DO THIS NEXT**

Highest priority. Ordered roughly by impact:

### 4.1 Sketch convert (end-to-end)
- [ ] Ensure Vercel `/api/sketch*` works (or document working proxy URL)
- [ ] Real crop UX (parity with web `SketchCrop`)
- [ ] Poll / cancel / download / decode import into editor
- [ ] Resume pending job on cold start (`PendingSketchResume` parity)
- [ ] Error copy for 413 / timeout / network (web strings)

### 4.2 Canvas fidelity
- [ ] Door/window symbols (swing arcs, glass lines, jambs) — web `openingArt` / `OpeningSymbol`
- [ ] Furniture SVG symbols on canvas (not only rectangles)
- [ ] Dimension pills along lines; room name + area labels
- [ ] Snap cues + alignment guides
- [ ] Selection emphasis matching architect blue / caution orange where web does

### 4.3 Wall / room tools
- [ ] Draw **chain** (continue from last endpoint; tap to end)
- [ ] Interior 10 / Exterior 20 thickness chips (full dock/Add sheet parity)
- [ ] Port/finish: `WallJoinInference`, stronger T-junction heal
- [ ] Room detection reconcile after wall edits (web quality)

### 4.4 Openings
- [ ] Tap wall to insert door/window/french
- [ ] Slide along wall, resize handles
- [ ] Hinge / swing flips
- [ ] Port: `OpeningSwing`, `OpeningSymbol`, `WallSegmentation` cutouts

### 4.5 Furniture
- [ ] Drag move + rotate with snap-to-wall / wall-butt
- [ ] Replace-with…, stamp mode
- [ ] Multi-select, align/distribute (`FurnitureArrange`, `ObjectAlignment`)
- [ ] Clipboard copy/paste/duplicate; groups

### 4.6 Dimensions
- [ ] Two-tap measure tool
- [ ] Exterior auto-chain
- [ ] Drag edit + typed length
- [ ] Port: `DimensionMutation`, `dimensionFace`

### 4.7 Sheets / chrome
- [ ] Full WallSheet / RoomSheet / OpeningSheet / FurnitureSheet (web mobile detents)
- [ ] Add sheet + Furniture picker parity (categories, recents)
- [ ] Right-rail / large-width side panel (≥672dp) like web

### 4.8 Other Stage 1 gaps
- [ ] Trace underlay (photo under plan ~35% opacity)
- [ ] Curve walls: bow handle + live curve (`WallCurveMutation`)
- [ ] Export quality: fuller DXF layers / PDF contractor sheet (web `export/*`)
- [ ] Dashboard: live thumbnails from canvas, rename UX, relative time, hero parity
- [ ] Remaining geom ports listed in §6

---

## 5. Remaining work — Tier 2 (iOS-quality 2D, still no 3D)

Do after Tier 1:

- [ ] File menu: Open `.homedesign` / `.sh3d`, Save copy, Trace, Showcase, Units
- [ ] Multi-level floors + ghost inactive levels + add floor
- [ ] Full wall style sheet (per-side textures, paint, glass, height/length, curve, wainscoting)
- [ ] Full room style sheet (floor/ceiling, borders, stage-this-room, lighting sets)
- [ ] Large catalog (~iOS scale), search/sort/recents
- [ ] Haptics, restore-session alert, first-run editor tips
- [ ] SH3D import

Reference: `homedesign-ios/docs/feature-inventory.md` §§1–11, 14–15 (2D parts).

---

## 6. Remaining work — Tier 3 (true iOS parity)

Do only when user explicitly wants full iOS level:

- [ ] 3D renderer (Filament or equivalent on Android)
- [ ] Split / 3D / AR mode picker
- [ ] Walkthrough (pegman, joystick, FOV, minimap)
- [ ] Lighting & scene (sun/time, outdoor grass/fence, roofs)
- [ ] Full-home AR + solo-piece AR
- [ ] Real 3D catalog meshes / materials
- [ ] Door swing animation in 3D

Reference: `homedesign-ios/docs/feature-inventory.md` §§12–13.

---

## 7. Geom modules — ported vs deferred

### Ported (Android `domain/geom/`)
`AngleSnap`, `ArcWallGeometry`, `Constants`, `FurnitureGeometry`, `FurnitureSnap`, `HitTest`, `OpeningBinding`, `OpeningMutation`, `RectangleRoom`, `RoomDetection`, `RoomGeometry`, `RoomMutation`, `SnapEngine`, `Vec`, `WallCurveProfileUtil`, `WallGeometry`, `WallMutation`, `WallTJunction`

### Still missing vs web `src/geom/` (Tier 1)
- [ ] `WallJoinInference`
- [ ] `WallSegmentation`
- [ ] `WallCurveMutation` / fuller curve profile UX
- [ ] `WallStyleMutation`, `WallClearance`
- [ ] `OpeningSwing`, `OpeningSymbol`
- [ ] `FurnitureFacing`, `FurnitureReplace`, `FurnitureArrange`, `FurnitureGroupMove`, `FurnitureSymbolClassifier`, `FurnitureSymbols`
- [ ] `ObjectAlignment`, `PlacementDefaults`
- [ ] `DimensionMutation`, `dimensionFace`
- [ ] `RoomSizeMutation`, `RoomStyleMutation`, `RoomContainment`
- [ ] `PlanTransform`

---

## 8. How to run (Windows PowerShell)

```powershell
cd C:\webapp_android\homedes-android

$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

.\gradlew.bat :app:assembleDebug

# Device must show in `adb devices` first:
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.homedesign.android.debug/com.homedesign.android.MainActivity
```

Optional sketch URL override in `local.properties` (gitignored):
```properties
sdk.dir=C:\\Users\\itsne\\AppData\\Local\\Android\\Sdk
sketch.base.url=https://homedes-webapp.vercel.app
```

No Python venv — this is Gradle/Android only.

---

## 9. Invariants (do not break)

From web `AGENTS.md` — keep identical on Android:

- Plan units **cm**, origin top-left, **+Y down**
- Snap to wall **faces**, not centrelines; join epsilon **1 cm**
- Furniture `(x,y)` = centre; be careful with facing formulas
- Unknown JSON keys should survive decode→encode where possible
- Never mention “Sweet Home 3D” in user-facing strings
- No convert API key in the APK (proxy only)

---

## 10. Suggested resume prompt for agents

> Read `C:\webapp_android\homedes-android\resume.md` and continue from Tier 1. Prefer the highest unchecked items in §4. Keep webapp as Stage 1 oracle; use iOS only for UI analogy unless I ask for Tier 2/3. Verify with `assembleDebug` after changes.

---

## 11. Update rule

Whenever a Tier 1/2/3 checkbox is completed (or scope changes), **update this file in the same PR/commit** so `read resume.md` stays accurate.
