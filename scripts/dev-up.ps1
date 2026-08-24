# Full agent-friendly pipeline: env → (optional emulator) → assemble+test → install → launch.
# Usage:
#   .\scripts\dev-up.ps1
#   .\scripts\dev-up.ps1 -SkipTests
#   .\scripts\dev-up.ps1 -StartEmulator
#   .\scripts\dev-up.ps1 -SketchProxy

[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$StartEmulator,
    [switch]$SketchProxy,
    [string]$Avd = ""
)

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools
Set-Location $HdRoot

Write-Host "=== HomeDesign Android dev-up ===" -ForegroundColor Cyan
Write-Host "Root: $HdRoot"
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"

$proxyProc = $null
if ($SketchProxy) {
    Write-Host ">> Starting sketch proxy in background…" -ForegroundColor Cyan
    $proxyProc = Start-Process -FilePath "powershell.exe" -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $PSScriptRoot "run-sketch-proxy.ps1")
    ) -PassThru -WindowStyle Minimized
    Start-Sleep -Seconds 2
}

if ($StartEmulator) {
    & "$PSScriptRoot\ensure-emulator.ps1" -Avd $Avd | Out-Null
} else {
    $devs = @(Get-HdDevices)
    if ($devs.Count -eq 0) {
        Write-Host ">> No device; attempting emulator…" -ForegroundColor Yellow
        & "$PSScriptRoot\ensure-emulator.ps1" -Avd $Avd | Out-Null
    }
}

$assembleArgs = @()
if (-not $SkipTests) { $assembleArgs += "-Test" }
& "$PSScriptRoot\assemble.ps1" @assembleArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& "$PSScriptRoot\install-launch.ps1" -NoBuild
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "=== Ready. Package=$DebugPackage ===" -ForegroundColor Green
if ($proxyProc) {
    Write-Host "Sketch proxy pid $($proxyProc.Id) (stop with Stop-Process -Id $($proxyProc.Id))" -ForegroundColor DarkGray
}
