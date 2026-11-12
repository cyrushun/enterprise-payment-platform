    param([string]$BaseUrl = "http://localhost:8080")
    $ErrorActionPreference = "Stop"
    $checks = @(
        @{ Name = "liveness"; Url = "$BaseUrl/actuator/health/liveness" },
        @{ Name = "readiness"; Url = "$BaseUrl/actuator/health/readiness" }
    )
    foreach ($check in $checks) {
        try {
            $response = Invoke-RestMethod -Uri $check.Url -TimeoutSec 10
            if ($response.status -ne "UP") { throw "$($check.Name) status is $($response.status)" }
            Write-Host "PASS $($check.Name): UP"
        } catch {
            Write-Error "FAIL $($check.Name): $($_.Exception.Message)"
            exit 1
        }
    }
    Write-Host "Deployment health probes passed. This does not replace functional/regression testing."
