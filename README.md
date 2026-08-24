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

## Agent auto-launch (no manual steps)

Scripts under `scripts/` set `JAVA_HOME` / `ANDROID_HOME` and wrap Gradle + adb:

| Script | Purpose |
|--------|---------|
| `.\scripts\dev-up.ps1` | Assemble (+tests) → install → launch (starts emulator if none) |
| `.\scripts\assemble.ps1 [-Test]` | Debug APK (+ unit tests) |
| `.\scripts\install-launch.ps1` | `adb install -r` + start `com.homedesign.android.debug` |
| `.\scripts\ensure-emulator.ps1` | Boot first AVD if no device |
| `.\scripts\screenshot.ps1` / `dump-ui.ps1` / `tap.ps1` / `logcat.ps1` | Device control |
| `.\scripts\run-sketch-proxy.ps1` | Local `/api/sketch` mock for debug |

```powershell
cd C:\webapp_android\homedes-android
.\scripts\dev-up.ps1 -StartEmulator -SkipTests
# optional sketch mock:
.\scripts\run-sketch-proxy.ps1
.\scripts\dev-up.ps1 -SketchProxy
```

### MCP connector (`homedes-adb`)

Project MCP server at `tools/homedes-adb-mcp/` is registered in `.grok/config.toml`. After opening this repo in Grok, tools like `hd_dev_up`, `hd_screenshot`, `hd_ui_dump`, `hd_tap`, `hd_logcat`, `hd_assemble` are available for autonomous install/launch/debug.

```powershell
cd tools\homedes-adb-mcp
npm.cmd install
node src\doctor.mjs
```

Debug package: `com.homedesign.android.debug`  
Activity: `com.homedesign.android.MainActivity`

## SKETCH_BASE_URL / `sketch.base.url`

Sketch convert calls go to a **proxy host** (no API key in the APK). Retrofit uses `BuildConfig.SKETCH_BASE_URL` as the base URL; relative paths are `api/sketch`, `api/sketch/{id}`, `api/sketch/{id}/file`, `api/sketch/{id}/cancel`.

| Variant | `SKETCH_BASE_URL` |
|---------|-------------------|
| **debug** (default) | `http://10.0.2.2:8787` — emulator → host local mock |
| **debug** override | `sketch.base.url` in **`local.properties`** (gitignored) |
| **release** | always `https://homedes-webapp.vercel.app` (ignores `local.properties`) |

### Local mock E2E (debug)

```powershell
# From homedes-android/
.\scripts\run-sketch-proxy.ps1
# or standalone mock only (same /api/sketch contract on :8787):
.\scripts\run-sketch-proxy.ps1 -Standalone
```

That starts `homedes-webapp/server/mock-upstream.mjs` (+ `proxy.mjs` in default mode). Debug builds already default to `http://10.0.2.2:8787` and allow cleartext HTTP. Physical device: set `sketch.base.url=http://<LAN-IP>:8787` and rebuild.

```properties
# local.properties — optional debug override (release ignores this)
sdk.dir=C:\\Users\\you\\AppData\\Local\\Android\\Sdk
sketch.base.url=http://10.0.2.2:8787
```

**Vercel status:** the public site still serves SPA-only; **`https://homedes-webapp.vercel.app/api/sketch` returns 404** until the webapp API routes are redeployed. Local mock covers Android sketch E2E in the meantime.

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

## Status (verified)

`./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` succeed on this tree.

**Shipped**
- Journey: Splash, Landing, Onboarding, Setup, Auth (Google stub + skip/email, same as web)
- Dashboard: search/sort, blank project, open, delete; Room stores `.homedesign` archives + thumbs
- Editor: plan canvas (grid, rooms, mitered walls, furniture, openings, dims), pan/pinch, select, draw wall, draw room, place catalog furniture, undo/redo, 3s autosave, property sheet, export DXF/PDF/`.homedesign` via FileProvider
- Domain ports: models, catalog, geom kernel (walls/rooms/openings/snap/hit-test), undo, zip codec
- Sketch flow UI + Retrofit client; debug → local mock (`scripts/run-sketch-proxy.ps1`), release → Vercel origin

**Notes**
- Sketch: local mock E2E works via **SKETCH_BASE_URL** section above; live Vercel `/api/sketch` still needs deploy.
- Remaining deferred geom vs web: multi-span curve breakpoint UX, FurnitureFacing / symbol classifier. See `resume.md` §6–7.
- Dark theme follows system / DataStore preference using HD editorial + architect tokens.
