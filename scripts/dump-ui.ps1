# Dump UI Automator hierarchy (for agent debugging).
# Usage: .\scripts\dump-ui.ps1 [-Out path.xml]

[CmdletBinding()]
param([string]$Out = "")

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools

$serial = Wait-HdDevice -TimeoutSec 30
$art = Join-Path $HdRoot "artifacts"
New-Item -ItemType Directory -Force -Path $art | Out-Null
if (-not $Out) {
    $Out = Join-Path $art ("ui-{0:yyyyMMdd-HHmmss}.xml" -f (Get-Date))
}

$remote = "/sdcard/hd-window_dump.xml"
& $Adb -s $serial shell uiautomator dump $remote 2>$null | Out-Null
& $Adb -s $serial pull $remote $Out | Out-Null
& $Adb -s $serial shell rm $remote 2>$null | Out-Null
Write-Host ">> UI dump: $Out" -ForegroundColor Green
Get-Content $Out -Raw
