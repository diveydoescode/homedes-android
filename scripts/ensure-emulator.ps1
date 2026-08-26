# Boot the first available AVD if no device is online.
# Usage: .\scripts\ensure-emulator.ps1 [-Avd <name>] [-TimeoutSec 180]

[CmdletBinding()]
param(
    [string]$Avd = "",
    [int]$TimeoutSec = 180
)

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools

$existing = @(Get-HdDevices)
if ($existing.Count -gt 0) {
    Write-Host ">> Device already online: $($existing[0])" -ForegroundColor Green
    return $existing[0]
}

if (-not (Test-Path $Emulator)) {
    throw "emulator.exe not found at $Emulator"
}

if (-not $Avd) {
    $avds = & $Emulator -list-avds 2>$null
    if (-not $avds) { throw "No AVDs found. Create one in Android Studio first." }
    $Avd = ($avds | Select-Object -First 1).ToString().Trim()
}

Write-Host ">> Starting AVD '$Avd'..." -ForegroundColor Cyan
$p = Start-Process -FilePath $Emulator -ArgumentList @("-avd", $Avd, "-netdelay", "none", "-netspeed", "full") -PassThru -WindowStyle Minimized
Write-Host ">> emulator pid $($p.Id)"

$serial = Wait-HdDevice -TimeoutSec $TimeoutSec
& $Adb -s $serial wait-for-device
# Wait until boot completed
$deadline = (Get-Date).AddSeconds($TimeoutSec)
do {
    $boot = & $Adb -s $serial shell getprop sys.boot_completed 2>$null
    if (($boot | Out-String).Trim() -eq "1") { break }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)

Write-Host ">> Emulator ready: $serial" -ForegroundColor Green
return $serial
