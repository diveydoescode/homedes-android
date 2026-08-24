# Type text into the focused field (%s = space).
# Usage: .\scripts\input-text.ps1 -Text "hello world"

[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$Text)

. "$PSScriptRoot\lib\env.ps1"
Assert-HdTools
$serial = Wait-HdDevice -TimeoutSec 30
$escaped = ($Text -replace " ", "%s") -replace "'", "\'"
& $Adb -s $serial shell input text $escaped
Write-Host ">> typed text" -ForegroundColor Green
