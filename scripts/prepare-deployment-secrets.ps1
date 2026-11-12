$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$secretDir = Join-Path $projectRoot ".secrets"
$keyDir = Join-Path $projectRoot "src\main\resources\keys"

$privateSource = Join-Path $keyDir "jwt-private.pem"
$publicSource = Join-Path $keyDir "jwt-public.pem"
$privateDestination = Join-Path $secretDir "jwt-private.pem"
$publicDestination = Join-Path $secretDir "jwt-public.pem"

foreach ($file in @($privateSource, $publicSource)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Required local development JWT key file not found: $file"
    }
}

New-Item -ItemType Directory -Force -Path $secretDir | Out-Null

# Do not remove existing files or rewrite Windows ACLs here. Those operations
# caused Access Denied errors on some Windows installations. Keep the existing
# .secrets directory permissions and let the operator manage them explicitly.
Copy-Item -LiteralPath $privateSource -Destination $privateDestination -Force
Copy-Item -LiteralPath $publicSource -Destination $publicDestination -Force

foreach ($file in @($privateDestination, $publicDestination)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        throw "Deployment key preparation failed: $file"
    }
    if ((Get-Item -LiteralPath $file).Length -eq 0) {
        throw "Deployment key file is empty: $file"
    }
}

Write-Host "Local Docker JWT keys prepared successfully."
Write-Host "Private key contents were not displayed."
Write-Warning "These are development keys only. Never use them for staging or production."
