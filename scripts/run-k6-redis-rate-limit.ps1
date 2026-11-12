[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [ValidateRange(1, 500)]
    [int]$VUs = 5,
    [string]$Duration = "2m"
)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envFile = Join-Path $projectRoot ".env"
if (-not (Test-Path $envFile)) { throw ".env not found in project root." }

foreach ($line in Get-Content -LiteralPath $envFile) {
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) { continue }
    if ($trimmed -match '^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $name = $Matches[1]; $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}
$required = @("LOAD_TEST_EMAIL", "LOAD_TEST_PASSWORD")
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, "Process"))) {
        throw "Required .env setting '$name' is empty. Set it in .env; do not put credentials in the script."
    }
}
if (-not (Get-Command k6 -ErrorAction SilentlyContinue)) {
    throw "Native k6 is required. Install with: winget install --id GrafanaLabs.k6 -e"
}
$env:BASE_URL = $BaseUrl
$env:VUS = "$VUs"
$env:DURATION = $Duration
$results = Join-Path $projectRoot "load-tests\results"
New-Item -ItemType Directory -Force -Path $results | Out-Null
Push-Location $projectRoot
try {
    # Pass only the explicitly required local test settings into k6; values are not written to files or printed.
    & k6 run --env "BASE_URL=$BaseUrl" --env "VUS=$VUs" --env "DURATION=$Duration" --env "LOAD_TEST_EMAIL=$env:LOAD_TEST_EMAIL" --env "LOAD_TEST_PASSWORD=$env:LOAD_TEST_PASSWORD" "load-tests/k6/redis-rate-limit.js"
    if ($LASTEXITCODE -ne 0) { throw "k6 exited with code $LASTEXITCODE." }
}
finally { Pop-Location }
