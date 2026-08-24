# Swipe from (X1,Y1) to (X2,Y2) over DurationMs.
# Usage: .\scripts\swipe.ps1 -X1 100 -Y1 800 -X2 100 -Y2 200 -DurationMs 300

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][int]$X1,
    [Parameter(Mandatory = $true)][int]$Y1,
    [Parameter(Mandatory = $true)][int]$X2,
    [Parameter(Mandatory = $true)][int]$Y2,
    [int]$DurationMs = 300
)

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools
$serial = Wait-HdDevice -TimeoutSec 30
& $Adb -s $serial shell input swipe $X1 $Y1 $X2 $Y2 $DurationMs
Write-Host ">> swipe ($X1,$Y1)->($X2,$Y2) ${DurationMs}ms" -ForegroundColor Green
