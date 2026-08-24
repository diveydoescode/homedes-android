# Agent notes — HomeDesign Android

## SESSION START (do this first)

When `resume.md` is read or a new session begins on this repo:

1. Confirm MCP **`homedes-adb`** is loaded (`search_tool` / `grok mcp list`).
2. `hd_status` → `hd_devices` → if empty, `hd_ensure_emulator`.
3. `hd_dev_up` (`skipTests: true`) or `.\scripts\dev-up.cmd -StartEmulator -SkipTests`.
4. Prove control with `hd_screenshot` and/or `hd_ui_dump`.
5. Only then continue product work from `resume.md` **§12**.

Full checklist: **`resume.md` §0**.

## Auto-launch

```powershell
cd C:\webapp_android\homedes-android
.\scripts\dev-up.cmd -StartEmulator -SkipTests
```

With sketch mock:

```powershell
.\scripts\run-sketch-proxy.ps1   # leave running
.\scripts\dev-up.cmd -SkipTests
```

Individual: `assemble.cmd`, `install-launch.cmd`, `test.ps1`, `screenshot.ps1`, `dump-ui.ps1`, `tap.ps1`, `logcat.ps1`, `stop-app.ps1`.

## MCP: `homedes-adb`

Registered in `.grok/config.toml` and `~/.grok/config.toml`. Prefer these over asking the user to run adb:

| Tool | Purpose |
|------|---------|
| `hd_status` / `hd_devices` | Paths + connected devices |
| `hd_ensure_emulator` | Boot AVD if none |
| `hd_assemble` / `hd_test` / `hd_gradle` | Build & unit tests |
| `hd_install_launch` / `hd_dev_up` / `hd_stop_app` | Install & start |
| `hd_screenshot` / `hd_ui_dump` | Observe UI |
| `hd_tap` / `hd_swipe` / `hd_input_text` / `hd_press_key` | Drive UI |
| `hd_logcat` / `hd_shell` | Debug |

Debug app id: `com.homedesign.android.debug`  
Activity: `com.homedesign.android.MainActivity`

Source of product progress + remaining work: **`resume.md`**.
