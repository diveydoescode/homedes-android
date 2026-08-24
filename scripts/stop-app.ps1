# Force-stop the debug app.
# Usage: .\scripts\stop-app.ps1

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools
$serial = Wait-HdDevice -TimeoutSec 30
& $Adb -s $serial shell am force-stop $DebugPackage
Write-Host ">> stopped $DebugPackage" -ForegroundColor Green
