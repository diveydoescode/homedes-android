# HomeDesign Android — Design (Stage 1 parity)

**Date:** 2026-03-24  
**Goal:** Native Kotlin/Compose Android app matching `homedes-webapp` Stage 1.  
**Oracle:** Webapp for features/data; iOS Frontend for chrome/menu analogy only.

## 1. Product scope

Include everything the webapp ships for Stage 1:

- Journey: Splash, Landing, Onboarding, Setup, Auth (stub Google + email/skip)
- Dashboard: list/search/sort, create blank, rename, delete, open
- Editor: 2D plan (walls, rooms, openings, furniture, dimensions, undo, autosave)
- Sketch-to-plan flow against Vercel `/api/sketch`
- Export DXF + PDF
- Light/dark theme, metric/imperial
- Local-only projects (no account sync)

Out of scope (iOS-only / later stages): 3D/Filament, AR, multi-span curve advanced tooling beyond web, Sweet Home 3D naming.

## 2. Architecture

Clean Architecture + MVVM, Hilt, Coroutines/Flow.

```
:app
  presentation/  screens, viewmodels, navigation, canvas host
  di/            Hilt modules
:core-ui         theme, shared Compose components
:domain          models, geom, editor state, use cases, repository interfaces
:data            Room, DataStore, Retrofit, repository impls
```

Single `:app` Gradle module may host packages if faster to open; logical layers still enforced by package.

### Layer rules

- `domain` has no Android framework deps (pure Kotlin + kotlinx.serialization where needed).
- ViewModels expose `StateFlow<UiState>` + one-shot `SharedFlow` events.
- Repositories are the only bridge to Room / network / files.

## 3. Persistence

| Concern | Store |
|---------|--------|
| Settings / identity / theme / units / hasOnboarded | DataStore Preferences |
| Project metas, archive bytes, thumbnails | Room |
| Pending sketch job id | DataStore |
| User textures | app files dir + Room index |

Autosave: editor ViewModel debounces Home mutations 3s; flush on `ON_STOP`.

## 4. Networking

- Retrofit + OkHttp + Kotlin Serialization (JSON) / Multipart for sketch upload.
- Default `SKETCH_BASE_URL=https://homedes-webapp.vercel.app`
- No API key in the app (proxy adds key server-side).
- Override via `local.properties` → BuildConfig for local proxy.

## 5. Editor / canvas

- Plan drawn with Android `Canvas` inside Compose (`AndroidView` or Compose Canvas).
- Same coordinate system as web (cm, +Y down).
- Single pointer owner: classify hit once per gesture (wall / opening / furniture / room / dim).
- Tools: select, draw wall, room rect, dimension, furniture stamp — matching web dock.
- Bottom sheets for properties on phone; optional side panel ≥672dp width.
- Dock centered on working area; top bar with name, undo/redo, export, theme.

Geometry: port web `src/geom` and `src/state` modules to Kotlin under `domain.geom` / `domain.editor`, preserving invariants from web `AGENTS.md`.

## 6. Sketch flow

Screens: Picker → Crop → Preparing/Reel → Polling → Success handoff to Editor / Error.

Client mirrors `sketchClient.ts`. Resume pending job on cold start like web `PendingSketchResume`.

## 7. Auth

Match web: `UnconfiguredAuthProvider` behavior for Google; email field + Continue / Skip call `completeAuth` and set `hasOnboarded`.

## 8. Theming

Material 3 schemes built from HD editorial + architect tokens (`tokens.css` / `HDTheme.swift`).  
Two registers: editorial for journey/dashboard; architect for editor chrome.

## 9. Testing & build

- Unit tests for geom / undo / UnitFormat / sketch poll decoding.
- `minSdk 26`, `targetSdk 35`, Kotlin, Compose BOM.
- `./gradlew assembleDebug` must succeed.

## 10. Delivery

Push complete project to `https://github.com/diveydoescode/homedes-android` with README (run instructions, architecture, `SKETCH_BASE_URL` setup).
