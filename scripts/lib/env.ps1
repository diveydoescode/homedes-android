# Shared environment for HomeDesign Android automation scripts.
# Dot-source:  . "$PSScriptRoot\lib\env.ps1"

$ErrorActionPreference = "Stop"

$script:HdRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
if (-not (Test-Path (Join-Path $HdRoot "settings.gradle.kts"))) {
    # scripts/lib -> scripts -> repo root
    $script:HdRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

$env:JAVA_HOME = if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
    $env:JAVA_HOME
} else {
    "C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
}

$env:ANDROID_HOME = if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $env:ANDROID_HOME
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

$platformTools = Join-Path $env:ANDROID_HOME "platform-tools"
$emulatorDir = Join-Path $env:ANDROID_HOME "emulator"
$env:PATH = "$env:JAVA_HOME\bin;$platformTools;$emulatorDir;$env:PATH"

$script:Adb = Join-Path $platformTools "adb.exe"
$script:Emulator = Join-Path $emulatorDir "emulator.exe"
$script:Gradle = Join-Path $HdRoot "gradlew.bat"
$script:DebugApk = Join-Path $HdRoot "app\build\outputs\apk\debug\app-debug.apk"
$script:DebugPackage = "com.homedesign.android.debug"
$script:MainActivity = "com.homedesign.android.MainActivity"
$script:Component = "$DebugPackage/$MainActivity"

function Assert-HdTools {
    if (-not (Test-Path $Adb)) { throw "adb not found at $Adb" }
    if (-not (Test-Path $Gradle)) { throw "gradlew.bat not found at $Gradle" }
    if (-not (Test-Path $env:JAVA_HOME)) { throw "JAVA_HOME missing: $env:JAVA_HOME" }
}

function Get-HdDevices {
    Assert-HdTools
    & $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" } | ForEach-Object {
        ($_ -split "\t")[0]
    }
}

function Wait-HdDevice {
    param([int]$TimeoutSec = 120)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    do {
        $devs = @(Get-HdDevices)
        if ($devs.Count -gt 0) { return $devs[0] }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "No adb device online within ${TimeoutSec}s. Start an emulator or plug in a phone."
}
