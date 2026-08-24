# Starts local sketch E2E backends for the Android debug app.
#
# Default: mock-upstream :8790 + key-guarding proxy :8787
# Emulator URL (debug BuildConfig default): http://10.0.2.2:8787
#
# Standalone: mock alone on :8787 serving /api/sketch* (no proxy).
#
# Usage:
#   .\scripts\run-sketch-proxy.ps1
#   .\scripts\run-sketch-proxy.ps1 -Standalone
#   .\scripts\run-sketch-proxy.ps1 -CompleteAfterMs 500
#
# Stop: Ctrl+C

[CmdletBinding()]
param(
    [switch]$Standalone,
    [int]$ProxyPort = 8787,
    [int]$MockPort = 8790,
    [int]$CompleteAfterMs = 800,
    [string]$Bind = "127.0.0.1"
)

$ErrorActionPreference = "Stop"

$webappServer = (Resolve-Path (Join-Path $PSScriptRoot "..\..\homedes-webapp\server")).Path
$mockPath = Join-Path $webappServer "mock-upstream.mjs"
$proxyPath = Join-Path $webappServer "proxy.mjs"

if (-not (Test-Path $mockPath)) {
    throw "mock-upstream.mjs not found at $mockPath"
}

$nodeCmd = Get-Command node -ErrorAction SilentlyContinue
if (-not $nodeCmd) {
    throw "node.exe not on PATH. Install Node 20+ (needs zlib.crc32)."
}

$procs = New-Object System.Collections.Generic.List[System.Diagnostics.Process]

function Start-NodeScript([string]$Label, [string]$ScriptPath, [hashtable]$EnvMap) {
    foreach ($kv in $EnvMap.GetEnumerator()) {
        Set-Item -Path "Env:$($kv.Key)" -Value ([string]$kv.Value)
    }
    Write-Host "Starting $Label..."
    $p = Start-Process -FilePath $nodeCmd.Source `
        -ArgumentList @($ScriptPath) `
        -WorkingDirectory $webappServer `
        -PassThru -NoNewWindow
    $script:procs.Add($p) | Out-Null
    return $p
}

try {
    if ($Standalone) {
        Write-Host @"

[run-sketch-proxy] STANDALONE mock on ${Bind}:${ProxyPort}
  Android emulator: http://10.0.2.2:${ProxyPort}
  Health:           http://127.0.0.1:${ProxyPort}/healthz
  Ctrl+C to stop.

"@
        Start-NodeScript "mock" $mockPath @{
            MOCK_UPSTREAM_PORT     = "$ProxyPort"
            MOCK_BIND              = $Bind
            MOCK_COMPLETE_AFTER_MS = "$CompleteAfterMs"
        } | Out-Null
    }
    else {
        if (-not (Test-Path $proxyPath)) {
            throw "proxy.mjs not found at $proxyPath"
        }
        Write-Host @"

[run-sketch-proxy] mock :${MockPort} + proxy :${ProxyPort}
  Android emulator: http://10.0.2.2:${ProxyPort}
  Health:           http://127.0.0.1:${ProxyPort}/healthz
  Ctrl+C to stop.

"@
        Start-NodeScript "mock" $mockPath @{
            MOCK_UPSTREAM_PORT     = "$MockPort"
            MOCK_BIND              = $Bind
            MOCK_COMPLETE_AFTER_MS = "$CompleteAfterMs"
        } | Out-Null

        Start-Sleep -Milliseconds 500

        Start-NodeScript "proxy" $proxyPath @{
            PROXY_PORT      = "$ProxyPort"
            PROXY_BIND      = $Bind
            CONVERT_HOST    = "http://127.0.0.1:${MockPort}"
            CONVERT_API_KEY = "local-mock"
            UPSTREAM_MODE   = "convert"
        } | Out-Null
    }

    $healthUrl = "http://127.0.0.1:${ProxyPort}/healthz"
    $ready = $false
    for ($i = 0; $i -lt 25; $i++) {
        Start-Sleep -Milliseconds 200
        try {
            $r = Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -eq 200) {
                $ready = $true
                break
            }
        }
        catch { }
    }
    if ($ready) {
        Write-Host "[run-sketch-proxy] ready → $healthUrl"
    }
    else {
        Write-Warning "Health check did not succeed yet; probe $healthUrl"
    }

    Write-Host "Running (PIDs: $(($procs | ForEach-Object { $_.Id }) -join ', ')). Press Ctrl+C to stop."
    while ($true) {
        Start-Sleep -Seconds 1
        foreach ($p in $procs) {
            if ($p.HasExited) {
                throw "Child process exited early (PID $($p.Id), code $($p.ExitCode))."
            }
        }
    }
}
finally {
    foreach ($p in $procs) {
        if ($null -ne $p -and -not $p.HasExited) {
            Write-Host "Stopping PID $($p.Id)..."
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        }
    }
}
