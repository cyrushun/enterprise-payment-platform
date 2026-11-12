[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("performance-diagnostics", "reconciliation-precheck", "verify-transfer-integrity")]
    [string]$ScriptName
)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envFile = Join-Path $projectRoot ".env"
if (-not (Test-Path $envFile)) { throw ".env not found in project root." }

$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) { continue }
    if ($trimmed -match '^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $settings[$Matches[1]] = $value
    }
}
$dbName = if ($settings["POSTGRES_DB"]) { $settings["POSTGRES_DB"] } else { "payment_platform" }
$dbUser = if ($settings["POSTGRES_USER"]) { $settings["POSTGRES_USER"] } else { "payment_app" }
$container = "payment-platform-postgres"
$sqlFile = Join-Path $projectRoot "load-tests\sql\$ScriptName.sql"
if (-not (Test-Path $sqlFile)) { throw "SQL file not found: $sqlFile" }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker CLI is required for this SQL runner." }

Write-Host "Copying $ScriptName.sql to PostgreSQL container..."
docker cp $sqlFile "${container}:/tmp/phase52-$ScriptName.sql"
if ($LASTEXITCODE -ne 0) { throw "docker cp failed. Is the PostgreSQL container running?" }

Write-Host "Executing read-only diagnostic SQL against database '$dbName'..."
docker exec -i $container psql -v ON_ERROR_STOP=1 -U $dbUser -d $dbName -f "/tmp/phase52-$ScriptName.sql"
if ($LASTEXITCODE -ne 0) { throw "psql execution failed with exit code $LASTEXITCODE." }
