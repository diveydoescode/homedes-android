# Dump recent logcat filtered to the app (or custom filter).
# Usage: .\scripts\logcat.ps1 [-Lines 200] [-Filter "Homedesign|AndroidRuntime"]

[CmdletBinding()]
param(
    [int]$Lines = 200,
    [string]$Filter = "homedesign|AndroidRuntime|FATAL|AndroidRuntime"
)

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools
$serial = Wait-HdDevice -TimeoutSec 30
$raw = & $Adb -s $serial logcat -d -t $Lines 2>$null | Out-String
$raw -split "`r?`n" | Where-Object { $_ -match $Filter }
