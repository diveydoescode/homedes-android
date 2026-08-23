# HomeDesign (Android)

Native Kotlin / Jetpack Compose client for [HomeDesign](https://homedes-webapp.vercel.app), targeting Stage 1 parity with `homedes-webapp`.

## Requirements

- Android Studio Ladybug+ (AGP 8.7+)
- JDK 17
- Android SDK 35 (`compileSdk` / `targetSdk` 35, `minSdk` 26)

## Open & run

1. Open `homedes-android/` in Android Studio.
2. Confirm `local.properties` has a valid `sdk.dir` (generated for this machine as `%LOCALAPPDATA%\Android\Sdk`).
3. Sync Gradle, then **Run** the `app` configuration on an emulator or device.

CLI (with JDK 17 on `PATH`):

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

## SKETCH_BASE_URL

Sketch convert calls go to the Vercel webapp proxy (no API key in the app).

- **Default:** `https://homedes-webapp.vercel.app`
- **Override:** set in `local.properties`:

```properties
sketch.base.url=https://homedes-webapp.vercel.app
```

The value is baked into `BuildConfig.SKETCH_BASE_URL` at compile time (see `app/build.gradle.kts`). Retrofit base URL is that host; endpoints are `api/sketch`, `api/sketch/{id}`, `api/sketch/{id}/file`, `api/sketch/{id}/cancel`.

## Architecture

Single `:app` module with package layers:

| Package | Role |
|---------|------|
| `presentation/` | Compose screens, ViewModels, Navigation |
| `domain/` | Repository interfaces, models |
| `data/` | DataStore settings, Room projects, Retrofit `SketchApi` |
| `di/` | Hilt `AppModule`, `DatabaseModule`, `NetworkModule` |
| `core/ui/` | HD Material 3 theme (editorial + architect tokens) |

### Navigation

```
Splash → (hasOnboarded ? Dashboard : Landing)
Landing → Onboarding → Setup → Auth → Dashboard
Dashboard → Editor/{id} | Sketch flow → Editor
```

`hasOnboarded` is stored in DataStore via `SettingsRepository`. Auth Google is an intentional stub: *"Sign-in isn't configured in this build yet"* (same as web).

### Stack

Kotlin, Compose + Material 3, Hilt, Navigation Compose, Retrofit + OkHttp, Kotlin Serialization, Room, DataStore, Coil, Coroutines. ViewBinding is off.

## Assets

Furniture SVG catalog and floor/wall textures are under:

- `app/src/main/assets/svg/`
- `app/src/main/assets/textures/`

Copied from `homedes-webapp/public/`.

## Docs

- `docs/WEB_TO_ANDROID_MAPPING.md` — web → Android screen/API map
- `docs/superpowers/specs/2026-03-24-homedes-android-design.md` — Stage 1 design

## Status

Scaffold + journey UI shell, dashboard/project Room store, editor chrome placeholders, and sketch flow UI (picker → crop → progress → error) with Retrofit `SketchApi` wired for the next integration pass.
