[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidatePattern('^\d+$')]
    [string]$JvmPid,
    [ValidateRange(15, 600)]
    [int]$JfrDurationSeconds = 60
)
$ErrorActionPreference = "Stop"
$jcmd = Get-Command jcmd -ErrorAction SilentlyContinue
if (-not $jcmd) { throw "jcmd was not found. Install/use JDK 21 and ensure its bin directory is on PATH." }
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$outDir = Join-Path $root "load-tests\results\jvm"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$threadFile = Join-Path $outDir "threads-$stamp.txt"
$heapFile = Join-Path $outDir "heap-$stamp.txt"
$jfrFile = Join-Path $outDir "phase52-profile-$stamp.jfr"

Write-Host "Capturing thread dump..."
& jcmd $JvmPid Thread.print | Out-File -FilePath $threadFile -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw "Thread.print failed for PID $JvmPid." }

Write-Host "Capturing heap summary..."
& jcmd $JvmPid GC.heap_info | Out-File -FilePath $heapFile -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw "GC.heap_info failed for PID $JvmPid." }

Write-Host "Starting Java Flight Recorder for $JfrDurationSeconds seconds..."
& jcmd $JvmPid JFR.start "name=phase52" "settings=profile" "duration=${JfrDurationSeconds}s" "filename=$jfrFile"
if ($LASTEXITCODE -ne 0) { throw "JFR.start failed. Check that this JDK supports Flight Recorder." }

Write-Host "JFR recording started. Wait for its configured duration to complete."
Write-Host "Thread dump: $threadFile"
Write-Host "Heap summary: $heapFile"
Write-Host "JFR recording: $jfrFile"
