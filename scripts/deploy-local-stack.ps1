    $ErrorActionPreference = "Stop"
    $projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
    Set-Location $projectRoot
    if (-not (Test-Path ".env")) { throw ".env is required. Copy .env.example to .env and configure it." }
    & "$PSScriptRoot\setup-observability.ps1"
    & "$PSScriptRoot\prepare-deployment-secrets.ps1"
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose configuration validation failed." }
    docker compose --profile deployment up -d --build
    if ($LASTEXITCODE -ne 0) { throw "Deployment stack startup failed." }
    docker compose ps
