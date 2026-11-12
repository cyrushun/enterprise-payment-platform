$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envFile = Join-Path $projectRoot ".env"
$templateFile = Join-Path $projectRoot "observability\prometheus\prometheus.yml.template"
$prometheusConfig = Join-Path $projectRoot "observability\prometheus\prometheus.yml"
$secretDirectory = Join-Path $projectRoot ".secrets"
$secretFile = Join-Path $secretDirectory "metrics-password"

if (-not (Test-Path $envFile -PathType Leaf)) {
    throw ".env is missing from the project root. Create .env and configure the required values before continuing."
}
if (-not (Test-Path $templateFile -PathType Leaf)) {
    throw "Prometheus template is missing: $templateFile"
}

# Read simple KEY=VALUE entries from .env. Supports values wrapped in single/double quotes.
$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) { continue }
    if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { continue }

    $name = $Matches[1]
    $value = $Matches[2].Trim()
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    $settings[$name] = $value
}

$username = [string]$settings["METRICS_USERNAME"]
$password = [string]$settings["METRICS_PASSWORD"]
$prometheusTarget = [string]$settings["PROMETHEUS_TARGET"]
if ([string]::IsNullOrWhiteSpace($prometheusTarget)) { $prometheusTarget = "host.docker.internal:8080" }
if ($prometheusTarget -notmatch '^[A-Za-z0-9_.:-]+$') { throw "PROMETHEUS_TARGET contains unsupported characters." }
if ([string]::IsNullOrWhiteSpace($username)) { throw "METRICS_USERNAME is missing or empty in .env." }
if ($username -notmatch '^[A-Za-z0-9_.-]+$') {
    throw "METRICS_USERNAME may contain only letters, numbers, underscore, dot, and hyphen for safe YAML generation."
}
if ([string]::IsNullOrWhiteSpace($password)) { throw "METRICS_PASSWORD is missing or empty in .env." }

$template = Get-Content -LiteralPath $templateFile -Raw
if (-not $template.Contains("__METRICS_USERNAME__")) {
    throw "Prometheus template does not contain the __METRICS_USERNAME__ token."
}
$renderedConfig = $template.Replace("__METRICS_USERNAME__", $username).Replace("__PROMETHEUS_TARGET__", $prometheusTarget)
[System.IO.File]::WriteAllText($prometheusConfig, $renderedConfig, [System.Text.UTF8Encoding]::new($false))

New-Item -ItemType Directory -Force -Path $secretDirectory | Out-Null
[System.IO.File]::WriteAllText($secretFile, $password, [System.Text.UTF8Encoding]::new($false))

Write-Host "Generated observability/prometheus/prometheus.yml using METRICS_USERNAME from .env."
Write-Host "Generated .secrets/metrics-password from METRICS_PASSWORD in .env (value not displayed)."
Write-Host "Next run: docker compose config --quiet"
Write-Host "Then run: docker compose up -d"
