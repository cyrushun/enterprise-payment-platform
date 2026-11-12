param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [Parameter(Mandatory = $true)][string]$TargetDatabase,
    [switch]$ConfirmRestore
)
$ErrorActionPreference = "Stop"
if ($TargetDatabase -notmatch '^[A-Za-z_][A-Za-z0-9_]{0,62}$') {
    throw "TargetDatabase must be a simple PostgreSQL identifier (letters, numbers, underscore; max 63 characters)."
}
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $projectRoot
$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
if (-not $ConfirmRestore) {
    throw "Restore not executed. Review the target database, then rerun with -ConfirmRestore. Existing objects in the target may be replaced."
}

$settings = @{}
foreach ($line in Get-Content -LiteralPath ".env") {
    $t = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($t) -or $t.StartsWith("#")) { continue }
    if ($t -match '^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { $settings[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'") }
}
$user = [string]$settings["POSTGRES_USER"]
if ([string]::IsNullOrWhiteSpace($user)) { $user = "payment_app" }

$exists = docker compose exec -T postgres psql -U $user -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$TargetDatabase'"
if ($LASTEXITCODE -ne 0) { throw "Could not query PostgreSQL databases." }
if (-not ([string]$exists).Trim()) {
    docker compose exec -T postgres createdb -U $user $TargetDatabase
    if ($LASTEXITCODE -ne 0) { throw "Could not create target database '$TargetDatabase'." }
}

$containerFile = "/tmp/restore-$([Guid]::NewGuid().ToString('N')).dump"
docker compose cp $resolvedBackup "postgres:$containerFile"
if ($LASTEXITCODE -ne 0) { throw "Could not copy backup into PostgreSQL container." }
docker compose exec -T postgres pg_restore -U $user -d $TargetDatabase --clean --if-exists --no-owner --no-privileges $containerFile
$restoreExit = $LASTEXITCODE
docker compose exec -T postgres rm -f $containerFile | Out-Null
if ($restoreExit -ne 0) { throw "PostgreSQL restore failed. Inspect container logs and the target database." }
Write-Host "Restore completed to database '$TargetDatabase'. Verify application data and Flyway state before use."
