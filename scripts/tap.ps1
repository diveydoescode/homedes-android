# Tap screen coordinates (pixels).
# Usage: .\scripts\tap.ps1 -X 200 -Y 400

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][int]$X,
    [Parameter(Mandatory = $true)][int]$Y
)

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools
$serial = Wait-HdDevice -TimeoutSec 30
& $Adb -s $serial shell input tap $X $Y
Write-Host ">> tapped ($X,$Y)" -ForegroundColor Green
