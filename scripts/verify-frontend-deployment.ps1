[CmdletBinding()]
param(
    [string]$FrontendBaseUrl = "http://localhost:8088",
    [string]$ApiBaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function Assert-HttpOk {
    param([string]$Url, [string]$Description)
    try {
        $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 10
        if ($response.StatusCode -ne 200) {
            throw "$Description returned HTTP $($response.StatusCode)."
        }
        Write-Host "PASS: $Description (HTTP $($response.StatusCode))" -ForegroundColor Green
        return $response
    }
    catch {
        Write-Error "FAIL: $Description at $Url. $($_.Exception.Message)"
        throw
    }
}

$api = Assert-HttpOk -Url "$ApiBaseUrl/actuator/health/readiness" -Description "Spring Boot readiness"
$apiBody = $api.Content | ConvertFrom-Json
if ($apiBody.status -ne "UP") { throw "Spring Boot readiness status is '$($apiBody.status)', expected UP." }

$edge = Assert-HttpOk -Url "$FrontendBaseUrl/healthz" -Description "Nginx frontend edge health"
$frontendHealth = Assert-HttpOk -Url "$FrontendBaseUrl/api/health" -Description "Next.js frontend health"
$healthBody = $frontendHealth.Content | ConvertFrom-Json
if ($healthBody.status -ne "UP" -or $healthBody.service -ne "payment-platform-frontend") {
    throw "Unexpected frontend health response: $($frontendHealth.Content)"
}

$login = Assert-HttpOk -Url "$FrontendBaseUrl/login" -Description "Customer/operations login page"
Write-Host "Frontend deployment smoke checks passed." -ForegroundColor Green
