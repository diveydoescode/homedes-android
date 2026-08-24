# Capture a device screenshot to artifacts/
# Usage: .\scripts\screenshot.ps1 [-Out path.png]

[CmdletBinding()]
param([string]$Out = "")

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools

$serial = Wait-HdDevice -TimeoutSec 30
$art = Join-Path $HdRoot "artifacts"
New-Item -ItemType Directory -Force -Path $art | Out-Null
if (-not $Out) {
    $Out = Join-Path $art ("screen-{0:yyyyMMdd-HHmmss}.png" -f (Get-Date))
}

$remote = "/sdcard/hd-screenshot.png"
& $Adb -s $serial shell screencap -p $remote
& $Adb -s $serial pull $remote $Out | Out-Null
& $Adb -s $serial shell rm $remote | Out-Null
Write-Host ">> Screenshot: $Out" -ForegroundColor Green
$Out
