# Web → Android Mapping (HomeDesign Stage 1)

Source of truth: `homedes-webapp` (Stage 1). iOS `homedesign` used only for UI/chrome analogy.

## Screens

| Web route | Web component | Android destination | Notes |
|-----------|---------------|---------------------|-------|
| `/` | `Splash` | `SplashScreen` | Timed redirect; tap to skip |
| `/welcome` | `Landing` | `LandingScreen` | Get started / Sign in |
| `/onboarding` | `Onboarding` | `OnboardingScreen` | Pager of copy cards |
| `/setup` | `Welcome` | `SetupScreen` | Name + metric/imperial |
| `/signin` | `Auth` | `AuthScreen` | Email stand-in + skip; Google stub |
| `/designs` | `Dashboard` | `DashboardScreen` | Grid, search, sort, new/delete/rename |
| `/designs/:id` | `Editor` | `EditorScreen` | Plan canvas + dock + sheets |
| `/sketch` | `SketchRoute` | `SketchFlow` (nested nav) | Picker → crop → reel → poll → editor |

## Navigation

```
Splash
  ├─ hasOnboarded → Dashboard
  └─ else → Landing → Onboarding → Setup → Auth → Dashboard
Dashboard
  ├─ open project → Editor(projectId)
  ├─ blank → Editor(newId)
  └─ from sketch → SketchFlow → Editor(imported)
Editor → back → Dashboard
```

Nav: Jetpack Navigation Compose. Auth is soft (same as web): `hasOnboarded` in DataStore.

## Features → Android

| Feature | Web | Android |
|---------|-----|---------|
| Project list / meta / thumbs | IndexedDB `ProjectStore` | Room (`ProjectMetaEntity` + archive blob + thumbnail) |
| Home document | Zod camelCase JSON in zip | Kotlin models + kotlinx.serialization; `.homedesign` zip via Okio/Zip |
| Autosave | 3s debounce + visibility flush | ViewModel debounce + `Lifecycle` ON_STOP flush |
| Undo | depth 30, 0.8s coalesce | `UndoStack` in domain |
| Plan canvas | SVG/DOM `PlanCanvas` | `AndroidView` + `Canvas` (or Compose `Canvas`) with same cm / +Y-down |
| Walls / rooms / openings / furniture | `geom/*` + `state/mutations` | Ported Kotlin packages under `domain/geom` + `domain/editor` |
| Furniture catalog | `catalog/entries.ts` + SVG | Same entries + vector drawables / Coil SVG |
| Sheets (wall/room/opening/furniture) | bottom sheets / right rail | Material3 ModalBottomSheet (+ side panel on large width) |
| Editor dock / top bar | `EditorDock`, `EditorTopBar` | Compose bars; iOS deck chrome as menu analogy |
| Theme light/dark | `tokens.css` + toggle | Material3 `ColorScheme` from HD tokens + DataStore theme pref |
| Units | metric/imperial | DataStore + `UnitFormat` |
| Sketch convert | `POST/GET /api/sketch*` via Vercel proxy | Retrofit → `https://homedes-webapp.vercel.app/api/sketch` (override in `local.properties`) |
| Auth Google | Unconfigured stub | Same stub + snackbar copy |
| Export DXF/PDF | `export/*` | Ported writers + Share / Storage Access Framework |
| Textures | floor/wall jpgs | `assets/textures/**` |
| Offline | local projects; sketch needs network | Room offline; sketch errors surfaced |

## Data models (core)

`Home`, `Level`, `Wall`, `Room`, `HomePieceOfFurniture`, `HomeDoorOrWindow`, `DimensionLine`, `PlanLabel`, `Outdoor`, `WallCurveProfile`, `ProjectMeta`, `UnitSystem`, catalog `CatalogEntry`.

Plan units: **cm**, origin top-left, **+Y down** (same invariants as web).

## API

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/sketch` | multipart `image` + `drawing_type` → 202 `JobStatus` |
| GET | `/api/sketch/{id}` | poll status |
| GET | `/api/sketch/{id}/file` | download bytes |
| POST | `/api/sketch/{id}/cancel` | cancel |

Base URL default: `https://homedes-webapp.vercel.app`  
Override: `SKETCH_BASE_URL` in `local.properties` / BuildConfig.

## Design tokens

Editorial + Architect registers from `tokens.css` / iOS `HDTheme`:

- Terracotta `#B85C3C` / dark `#CE6E4B`
- Selection `#378ADD`, deep `#185FA5`
- Paper/ink adaptive light/dark warm neutrals
- Fonts: system sans + serif headlines (Editorial), mono for dims

## Package map

| Web | Android |
|-----|---------|
| `src/model` | `domain.model` |
| `src/geom` | `domain.geom` |
| `src/state` | `domain.editor` |
| `src/io` | `data.local` + `domain.io` |
| `src/net` | `data.remote` |
| `src/catalog` | `domain.catalog` |
| `src/canvas` | `presentation.editor.canvas` |
| `src/chrome` | `presentation.*` screens/components |
| `src/sketch` | `presentation.sketch` + `domain.sketch` |
| `src/export` | `domain.export` |
| `src/textures` | `domain.textures` + assets |
| — | `di` Hilt modules |
| — | `core.ui` theme/components |
