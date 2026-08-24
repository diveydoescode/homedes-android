# HomeDesign Android — Resume State

**Last updated:** 2026-08-24 (agent auto-launch + `homedes-adb` MCP; product near-complete)  
**Repo:** https://github.com/diveydoescode/homedes-android (`main`)  
**Local path:** `C:\webapp_android\homedes-android`  
**Companion sources:**
- Web (Stage 1 oracle): `C:\webapp_android\homedes-webapp` → https://github.com/diveydoescode/homedes-webapp
- iOS (UI / full-product reference): `C:\webapp_android\homedesign-ios` → https://github.com/diveydoescode/homedesign

When the user says **“read resume.md”**, treat this file as the source of truth for progress, scope, and next work. Do not re-analyze from scratch unless something here conflicts with the tree.

---

## 0. SESSION START — DO THIS FIRST (mandatory)

**Before any feature work, verify Grok can control the Android app for debugging.**

1. Confirm MCP **`homedes-adb`** is available (`search_tool` / `/mcps` / `grok mcp list`).  
   - Config: `.grok/config.toml` + `~/.grok/config.toml`  
   - Server: `tools/homedes-adb-mcp/` (`node src/index.mjs`)  
   - If missing: `cd tools/homedes-adb-mcp ; npm.cmd install ; node src/doctor.mjs`
2. Call **`hd_status`** then **`hd_devices`**.  
   - If no device: **`hd_ensure_emulator`** (or `.\scripts\ensure-emulator.ps1`).
3. Bring the app up: **`hd_dev_up`** with `skipTests: true` (or `.\scripts\dev-up.cmd -StartEmulator -SkipTests`).  
   - Package: `com.homedesign.android.debug`  
   - Activity: `com.homedesign.android.MainActivity`
4. Prove control: **`hd_screenshot`** and/or **`hd_ui_dump`**. Optionally **`hd_logcat`**.  
   - If that fails, fix adb/emulator/MCP before coding.
5. Only then open **§12 Remaining** and continue product work. Prefer MCP (`hd_tap`, `hd_swipe`, `hd_input_text`, `hd_screenshot`, `hd_logcat`) over asking the user to drive the device.

Also see `AGENTS.md` and `README.md` → “Agent auto-launch”.

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
- [x] Dashboard: search/sort, blank project, open, rename/delete sheet, relative time, hero Continue card; Room stores archives + thumbs
- [x] Editor: plan canvas (grid, rooms, mitered walls, furniture boxes/SVGs, openings, dims, trace underlay), pan/pinch, select, draw wall, draw room, place/replace catalog furniture, clipboard copy/paste/duplicate, undo/redo, ~3s autosave, thin property sheet, export DXF/PDF/`.homedesign` via FileProvider
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
- Sketch: **local mock E2E works** — `.\scripts\run-sketch-proxy.ps1` (mock±proxy on :8787); debug default `SKETCH_BASE_URL=http://10.0.2.2:8787`. Live Vercel `/api/sketch` still **404** (external deploy).
- Units: DataStore `unit_system` (`mm`/`cm`/`ft`) with legacy `use_metric` migration. Editor top bar has **mm | cm | ft** chips; dashboard area, dim pills, room labels, wall sheets, Add sheet thickness, catalog sizes, and exports all follow `UnitSystem`. Default is **millimetre** (web parity).

---

## 3. Honest completion estimate

| Target | Approx status |
|--------|----------------|
| App runs, basic edit/save | **Done** |
| = web Stage 1 (`homedes-webapp`) | **~100%** (app parity; live Vercel `/api/sketch` deploy is external — see §12) |
| = full iOS (`homedesign`) | **~88–92%** (in-app surfaces largely done; left: catalog meshes without SH3D archive, live Vercel sketch deploy, MCP device QA) |

---

## 4. Tier 1 (web Stage 1 parity) — **complete in-app**

### 4.1 Sketch convert (end-to-end)
- [x] Document working proxy URL / override — **local mock works** (`scripts/run-sketch-proxy.ps1` + debug default `http://10.0.2.2:8787`); release stays on Vercel origin. **Live Vercel `/api/sketch` still 404** — external deploy gap (§12).
- [x] Real crop UX (parity with web `SketchCrop`) — camera/gallery pick, fitted frame, corner/move crop, EXIF upright, JPEG 2048@0.85
- [x] Poll / cancel / download / decode import into editor — `SketchConvertClient` + `pollUntilTerminal` + `importDownloadedArchive`
- [x] Resume pending job on cold start (`PendingSketchResume` parity) — DataStore JSON + dashboard → sketch resume
- [x] Error copy for 413 / timeout / network (web strings) — `SketchCopy` / `flowErrorFromApi`

### 4.2 Canvas fidelity
- [x] Door/window symbols (swing arcs, glass lines) — `OpeningSymbol` + PlanCanvas (jamb math ported; not stroked on canvas, same as web)
- [x] Furniture SVG symbols on canvas — asset path parse + stroke draw; box fallback
- [x] Dimension pills along lines; room name + area labels
- [x] Snap cues + alignment guides — dashed terracotta guides while drawing walls / moving furniture (`SnapEngine` + angle/face cues)
- [x] Selection emphasis matching architect blue / caution orange where web does — walls/furniture/openings selection blue fill+stroke; endpoint handles caution orange; selected dims caution orange offset line + endpoint handles + extension ticks

### 4.3 Wall / room tools
- [x] Draw **chain** (continue from last endpoint; tap to end)
- [x] Interior 10 / Exterior 20 thickness chips (Add sheet + dock while DrawWall)
- [x] Port/finish: `WallJoinInference` + T-junction heal (RoomDetection.reconcileRooms on wall edits)
- [x] Room detection reconcile after wall edits (web quality)

### 4.4 Openings
- [x] Tap wall to insert door/window/french
- [x] Slide along wall, resize handles
- [x] Hinge / swing flips
- [x] Port: `OpeningSymbol`, `OpeningSwing`, `WallSegmentation` cutouts (even-odd wall fills)

### 4.5 Furniture
- [x] Drag move + rotate with snap-to-wall / wall-butt
- [x] Replace-with… — catalog swap via sheet; `FurnitureReplace` / `applyReplaceFurniture` (preserves centre; adopts size)
- [x] Stamp mode — `PlaceFurniture.stamp` default on; banner Stamp chip + Done; stays in place tool until cancelled
- [x] Multi-select, align/distribute (`FurnitureArrange`, `ObjectAlignment`) — long-press / Shift-Ctrl-Meta tap → `Selection.MultiFurniture`; sheet Align L/C/R/T/Middle/B + Distribute H/V; rings on all selected
- [x] Clipboard copy/paste/duplicate (furniture; +40 cm offset; sheet actions; multi-aware) — groups still open
- [x] Groups — sheet Group/Ungroup (`applyGroup` / `applyUngroup`); `FurnitureGroupMove` fan on drag commit + live preview

### 4.6 Dimensions
- [x] Two-tap measure tool (pills + live preview length)
- [x] Exterior auto-chain — `DimensionMutation.exteriorChain` + Add sheet / Measure-dock **Ext dims**
- [x] Typed length — NumberRow + `applyDimensionLength` (scale end along dim dir, start fixed)
- [x] Drag edit (endpoint / offset) — Select tool: `HitTest`/classify dim end vs body → `DrawPreview.DimensionEdit` + commit mutate `DimensionLine`; offset via `signedDimensionOffset`
- [x] Port: `DimensionMutation`, `dimensionFace` (face snap on measure taps + outer envelope corners)

### 4.7 Sheets / chrome
- [x] Wall / Room / Opening / Furniture property sheets (NumberRows, flips, length/width) — not full web detents yet
- [x] Furniture picker categories + recents + SVG thumbs
- [x] Right-rail / large-width side panel (≥672dp) like web — `BoxWithConstraints`; property sheet docks top-end at `SidePanelMinWidth`

### 4.8 Other Stage 1 gaps
- [x] Trace underlay (photo under plan ~35% opacity; width capsule; pan/zoom with plan; session + `files/traces/{id}.jpg`)
- [x] Curve walls: bow handle + live curve (`WallCurveMutation` setSpanBow / applyWallBow; handle on selected straight wall)
- [x] Multi-span curve breakpoints — sheet **Add curve point**; caution-orange dots on plan; drag to move / drag to end merges+deletes (`insertBreakpoint` / `moveBreakpoint` / `shouldMergeBreakpoint`)
- [x] Export quality: DXF web layer names + furniture/openings/dims/title; PDF title block, openings (`OpeningSymbol`), furniture footprints, dims (still short of full web hatch/BLOCK art)
- [x] Dashboard: rename UX (long-press / ⋯), relative time, hero “Continue” + area/units
- [x] Dashboard: live thumbnails from canvas — `PlanThumbnail` JPEG on save/`createFromHome` → Room `thumbnailBlob` → `ProjectMeta.thumbnailJpeg` → Coil `AsyncImage` on HeroCard/ProjectCard
- [x] Geom ports listed in §7 (`RoomSizeMutation`, `WallClearance`, etc.)

---

## 5. Tier 2 (iOS-quality 2D) — **complete in-app**


- [x] File menu: Open `.homedesign`, Save copy, Trace, Units (editor + dashboard). Open `.sh3d` wired to real reader. Showcase + sample `.sh3d` on New sheet
- [x] Multi-level floors + ghost inactive levels + add floor — `LevelMutation`, floor selector (elevator G/1/…), PlanCanvas ghost draw, selectedLevelID in undo snapshot
- [x] Fuller wall style sheet (height/length, curve readout + straighten + add curve point, per-side paint chips + wall texture presets, Solid/Hatch/Glass)
- [x] Fuller room style sheet (floor/ceiling paint + texture presets from `assets/textures`, border chips, ceiling visible/style)
- [x] Large catalog — loads `assets/catalog/generic.json` (~1535 entries) via `CatalogLoader`; multi-token search (name/category/id/creator); sort/recents; expanded categories
- [x] First-run editor tip banner (DataStore `editor_tip_dismissed`). Haptics on wall commit + selection (`Haptics` / Vibrator). Restore-session alert via DataStore `last_project_id` + `editor_session_dirty`
- [x] SH3D import — `SH3DReader` (ZIP + Home.xml): walls, rooms, furniture, doors/windows, levels; dashboard + editor File menu; bundled `assets/samples/test_small.sh3d`
- [x] SH3D embedded meshes — `SH3DMeshExtraction` extracts ZIP entries (flat numbered + subdir); content-sniff `.obj`/`.mtl`/`.png`/`.jpg`; wires `modelRef` → `HomePieceOfFurniture.modelURL` + `extractedAssetURLs`; `ObjLoader` → `MeshTri` in `HomeExtrusion` (procedural fallback). Fixtures: `test_small` flat OBJs; iOS `AlpsHotel.sh3d` has 78×`.obj`+`.mtl` (no DAE/glTF). Legacy Java `Home` blob skipped (not a mesh).
- [x] OBJ MTL `map_Kd` textures — `ObjLoader` parses `vt` / `mtllib` / `usemtl` + MTL `map_Kd` (incl. option tokens); sets `MeshTri.uvs` + `textureAssetPath=file:…`; Filament `Plan3DView` uses textured material when UV+texture present (multi-material → multiple meshes).

### Tier 2 polish shipped
- [x] Wainscoting / baseboard on wall sheet — enable chip + height/thickness `NumberRow`s → `leftSideBaseboard` / `rightSideBaseboard`
- [x] Format painter — wall sheet button → `EditorTool.FormatPainter`; taps apply `WallStyleMutation.matchAttributes` / `applyMatchWallProperties`
- [x] Stage-this-room on room sheet — zooms plan to room bounds (`CameraFocusRequest`) + toast "Room staged" + `Home.properties["roomLighting:<id>"]=studio`
- [x] User-import wall/floor textures — gallery → `files/textures/*.jpg` via `UserTextureStore`; apply to selected wall side / room floor (3D file: path load)
- [x] Crown moulding — **closed as model gap**: SH3D / web / current iOS `Wall` only expose baseboard (floor trim). iOS removed `leftSideCrown` / `rightSideCrown` (#27); Android `DefaultHomeJson` already `ignoreUnknownKeys` so legacy crown keys decode safely. No schema to implement top moulding against.

Reference: `homedesign-ios/docs/feature-inventory.md` §§1–11, 14–15 (2D parts).

---

## 6. Tier 3 (iOS 3D/AR) — shipped in-app; mesh/occlusion external

- [x] 3D renderer (Filament) — walls + room floors extruded via `HomeExtrusion` → `Plan3DSurfaceView`; orbit; GLES fallback; opening cutouts; floor + wall-side UV textures
- [x] Mode picker — Plan2D / Split (≥900dp) / View3D / Walk / AR
- [x] Walkthrough (lite) — eye @ 1.70 m; drag look; joystick + WASD; FOV 70°; minimap; XZ collision + doorway pass
- [x] Lighting & scene — time-of-day + sky; Grass / Roofs / Fence; lit materials + roughness; textured floors/walls
- [x] Door swing animation in 3D — sash leaves via `TransformManager` + `OpeningSwing`
- [x] AR (in-app surface) — `ArHomeScreen` + `ArHomeGlView`: ARCore planes + tap-to-place; solo furniture box; scale 25–250%; plane grid; CameraX sim without ARCore
- [x] AR occlusion — Depth API (`Config.DepthMode.AUTOMATIC` + `acquireDepthImage16Bits` → RG8 sample + fragment discard) when device supports it; always-on below-plane Y clip fallback; status shows `depth occ` / `plane clip`
- [x] Procedural 3D furniture by kind (bed/sofa/table/chair/toilet/tub/wardrobe/plant) — used when `modelURL` absent
- [x] SH3D-imported OBJ furniture in Filament/AR — when piece has extracted `.obj` `modelURL`

Reference: `homedesign-ios/docs/feature-inventory.md` §§12–13.

---

## 7. Geom modules — ported

### Ported (Android `domain/geom/`)
`AngleSnap`, `ArcWallGeometry`, `Constants`, `DimensionFace`, `DimensionMutation`, `FurnitureArrange`, `FurnitureFacing`, `FurnitureGeometry`, `FurnitureGroupMove`, `FurnitureReplace`, `FurnitureSnap`, `FurnitureSymbolClassifier`, `FurnitureSymbols` (procedural path art for nightstand/dresser/fridge/tv/rug/lamp/stairs/sofaL/chandelier/mirror; SVG kinds via assets), `FurnitureSvgParse`, `HitTest`, `LevelMutation`, `ObjectAlignment`, `OpeningBinding`, `OpeningMutation`, `OpeningSwing`, `OpeningSymbol`, `PlacementDefaults`, `PlanTransform`, `RectangleRoom`, `RoomContainment`, `RoomDetection`, `RoomGeometry`, `RoomMutation`, `RoomSizeMutation`, `RoomStyleMutation`, `SnapEngine`, `Vec`, `WallClearance`, `WallCurveMutation` (incl. multi-span breakpoints + sheet/plan UX), `WallCurveProfileUtil`, `WallGeometry`, `WallJoinInference`, `WallMutation`, `WallSegmentation`, `WallStyleMutation`, `WallTJunction`

### Textures (Android `domain/textures/`)
`TexturePresets` — floor/wall presets, paint palette, border handles (web `textures/presets.ts`); `findPreset(preferWall=)` for shared slugs

### Sketch domain (new)
`domain/sketch/` — `SketchCopy`, `SketchErrors`, `SketchConstants`, `SketchPollLoop`, `PendingSketchJob`, `SketchImagePrep`, `SketchConvertClient`

### vs web `src/geom/` (Tier 1) — all ported
- [x] `WallCurveMutation` multi-span breakpoint UX (basics + bow handle + add/move/merge breakpoints done)
- [x] `WallStyleMutation` (colors/textures/height/thickness/pattern/glass/matchAttributes)
- [x] `WallClearance` (resolve on place/move commit + `healForExport`)
- [x] `FurnitureSymbolClassifier` + `FurnitureSymbols` procedural art when SVG absent; PlanCanvas SVG-first then procedural then kind-tint box
- [x] `FurnitureReplace` + `FurnitureArrange` + `FurnitureGroupMove`; basic `FurnitureFacing` helpers exist (fuller outcome API still thin — not blocking Stage 1)
- [x] `ObjectAlignment`, `FurnitureArrange` + UI multi-select / align / distribute
- [x] `PlacementDefaults` (wired into place furniture elevation)
- [x] `DimensionMutation`, `dimensionFace`
- [x] `RoomStyleMutation` (floor/ceiling colour+texture, border, ceiling visible/style)
- [x] `RoomSizeMutation` (sheet Width/Depth + `applyRoomSize` reconcile)
- [x] `RoomContainment` (clamp on place + move)
- [x] `PlanTransform` — mirror/rotate plan + selection mirror; File menu + furniture sheet Flip L↔R / T↔B

---

## 8. How to run (Windows PowerShell)

**Preferred (agent / one-shot):**

```powershell
cd C:\webapp_android\homedes-android
.\scripts\dev-up.ps1 -StartEmulator -SkipTests
```

MCP server **`homedes-adb`** (`.grok/config.toml` + `tools/homedes-adb-mcp/`) exposes `hd_dev_up`, `hd_screenshot`, `hd_ui_dump`, `hd_tap`, `hd_logcat`, etc. See `AGENTS.md`.

**Manual:**

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

> Read `C:\webapp_android\homedes-android\resume.md`. **First** complete **§0 SESSION START** (verify MCP `homedes-adb` can install/launch/screenshot the app). Then work only from **§12 Remaining**. Do not re-litigate finished Tier 1–3 checklists. After changes: `assembleDebug` + `testDebugUnitTest`, and re-check the app via MCP when UI is involved.

---

## 11. Update rule

Whenever a Tier 1/2/3 checkbox is completed (or scope changes), **update this file in the same PR/commit** so `read resume.md` stays accurate. Keep **§0** as the mandatory session opener.

---

## 12. Remaining — what’s left

Everything else in-repo is done or explicitly closed (incl. crown moulding — no Wall schema). Do **not** re-open polish checklists above unless a regression is found via MCP device debugging.

### Still open (do these next)

| Priority | Item | Notes |
|----------|------|--------|
| 1 | **Catalog-library 3D meshes without SH3D** | Pieces from `generic.json` / `.sh3f` with no embedded OBJ still use procedural meshes. Need a catalog mesh pack or OBJ mapping. |
| 2 | **Vercel `/api/sketch` deploy** | Android client + local mock are done. Live `https://homedes-webapp.vercel.app/api/sketch` is still **404** — deploy webapp API (outside this APK). |
| 3 | **Device QA via MCP** | Run full journey on emulator/phone with `hd_*` tools; fix regressions found that way. |

### Done recently (do not redo)

- [x] Agent auto-launch scripts — `scripts/dev-up.cmd`, `assemble.cmd`, `install-launch.cmd`, screenshot/ui/tap/logcat helpers  
- [x] MCP **`homedes-adb`** — build/install/launch/screenshot/ui dump/tap/swipe/text/keys/logcat/shell/gradle  
- [x] SH3D OBJ + MTL/`map_Kd` textures in Filament  
- [x] AR occlusion (Depth API + plane clip fallback)  
- [x] Local sketch mock proxy for debug E2E  
