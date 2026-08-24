# Send a keyevent (BACK=4, HOME=3, ENTER=66, TAB=61, DEL=67).
# Usage: .\scripts\press-key.ps1 -Code 4

[CmdletBinding()]
param([Parameter(Mandatory = $true)][int]$Code)

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools
$serial = Wait-HdDevice -TimeoutSec 30
& $Adb -s $serial shell input keyevent $Code
Write-Host ">> keyevent $Code" -ForegroundColor Green
