# Install debug APK and launch MainActivity.
# Usage: .\scripts\install-launch.ps1 [-NoBuild] [-Serial <id>]

[CmdletBinding()]
param(
    [switch]$NoBuild,
    [string]$Serial = ""
)

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools
Set-Location $HdRoot

if (-not $NoBuild) {
    & "$PSScriptRoot\assemble.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

if (-not (Test-Path $DebugApk)) {
    throw "APK missing: $DebugApk — run assemble first"
}

$serial = $Serial
if (-not $serial) {
    $serial = Wait-HdDevice -TimeoutSec 30
}

$adbArgs = @()
if ($serial) { $adbArgs += @("-s", $serial) }

Write-Host ">> adb install -r $DebugApk" -ForegroundColor Cyan
& $Adb @adbArgs install -r $DebugApk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ">> adb shell am start -n $Component" -ForegroundColor Cyan
& $Adb @adbArgs shell am start -n $Component
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ">> Launched $Component on $serial" -ForegroundColor Green
