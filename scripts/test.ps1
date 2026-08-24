# Run unit tests (optionally a single class).
# Usage: .\scripts\test.ps1
#        .\scripts\test.ps1 -Class com.homedesign.android.domain.io.Sh3dReaderTest

[CmdletBinding()]
param([string]$Class = "")

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools
Set-Location $HdRoot

$argsList = @(":app:testDebugUnitTest", "--no-daemon")
if ($Class) { $argsList += @("--tests", $Class) }

Write-Host ">> gradlew $($argsList -join ' ')" -ForegroundColor Cyan
& $Gradle @argsList
exit $LASTEXITCODE
