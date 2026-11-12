param(
    [string]$OutputDirectory = ".\backups",
    [string]$Database = ""
)
$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $projectRoot

$settings = @{}
foreach ($line in Get-Content -LiteralPath ".env") {
    $t = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($t) -or $t.StartsWith("#")) { continue }
    if ($t -match '^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { $settings[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'") }
}
if ([string]::IsNullOrWhiteSpace($Database)) { $Database = [string]$settings["POSTGRES_DB"] }
if ([string]::IsNullOrWhiteSpace($Database)) { $Database = "payment_platform" }
$user = [string]$settings["POSTGRES_USER"]
if ([string]::IsNullOrWhiteSpace($user)) { $user = "payment_app" }

$directory = Join-Path $projectRoot $OutputDirectory
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$fileName = "$Database-$timestamp.dump"
$file = Join-Path $directory $fileName
$containerFile = "/tmp/$fileName"

docker compose exec -T postgres pg_dump -U $user -d $Database -Fc --no-owner --no-privileges -f $containerFile
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed inside the PostgreSQL container." }
docker compose cp "postgres:$containerFile" $file
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $file) -or (Get-Item $file).Length -eq 0) {
    Remove-Item $file -ErrorAction SilentlyContinue
    throw "PostgreSQL backup copy failed or produced an empty file."
}
docker compose exec -T postgres rm -f $containerFile | Out-Null
Write-Host "Backup created: $file"
Write-Host "Protect this file as sensitive financial/application data."
