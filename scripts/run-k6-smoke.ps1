[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [ValidateRange(1, 10000)]
    [int]$Iterations = 5
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$resultsDirectory = Join-Path $projectRoot "load-tests\results"
$k6Script = Join-Path $projectRoot "load-tests\k6\smoke.js"

New-Item -ItemType Directory -Force -Path $resultsDirectory | Out-Null

if ($BaseUrl -notmatch '^https?://') {
    throw "BaseUrl must start with http:// or https://"
}
if (-not (Test-Path -LiteralPath $k6Script -PathType Leaf)) {
    throw "k6 smoke script not found: $k6Script"
}

$k6Command = Get-Command k6 -ErrorAction SilentlyContinue
if (-not $k6Command) {
    throw @"
k6 was not found on PATH.
Install k6 directly on Windows:
  winget install --id GrafanaLabs.k6 -e
Then close and reopen PowerShell and verify:
  k6 version
"@
}

Write-Host "Using native Windows k6:"
& k6 version
if ($LASTEXITCODE -ne 0) {
    throw "Unable to execute the installed k6 binary."
}

Write-Host "Running Phase 5.2.1 smoke test with locally installed k6..."
Push-Location $projectRoot
try {
    & k6 run --env "BASE_URL=$BaseUrl" --env "ITERATIONS=$Iterations" "load-tests/k6/smoke.js"
    $k6ExitCode = $LASTEXITCODE
    if ($k6ExitCode -ne 0) {
        throw "k6 returned exit code $k6ExitCode. Review the thresholds and output above."
    }
}
finally {
    Pop-Location
}

$summaryFile = Join-Path $resultsDirectory "phase-5.2.1-smoke-summary.json"
if (-not (Test-Path -LiteralPath $summaryFile -PathType Leaf)) {
    throw "k6 completed but the expected summary file was not created: $summaryFile"
}

Write-Host ""
Write-Host "Phase 5.2.1 smoke test completed successfully."
Write-Host "Summary: load-tests/results/phase-5.2.1-smoke-summary.json"
