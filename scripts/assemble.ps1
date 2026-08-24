# Assemble debug APK (+ optional unit tests)
# Usage: .\scripts\assemble.ps1 [-Test]

[CmdletBinding()]
param([switch]$Test)

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools
Set-Location $HdRoot

$tasks = @(":app:assembleDebug")
if ($Test) { $tasks += ":app:testDebugUnitTest" }

Write-Host ">> gradlew $($tasks -join ' ')" -ForegroundColor Cyan
& $Gradle @tasks --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host ">> OK $DebugApk" -ForegroundColor Green
