# Collect a review snapshot and refresh the report.
#
# ASCII ONLY, ON PURPOSE: Windows PowerShell 5.1 reads a .ps1 without a UTF-8 BOM
# as ANSI, and non-ASCII text then breaks the parser (this bit us once already).
# Keep every message in this file ASCII; put Chinese in the Markdown docs instead.
#
# Usage:
#   & .\run_daily.ps1 -World "C:\srv\world"
#   & .\run_daily.ps1 -World "C:\srv\world" -Log "C:\srv\logs\latest.log" -Out "D:\mc-review"
#
# Automate it (run every 30 minutes). Registering the task is NOT verified on this
# machine -- adjust the paths and run it yourself:
#
#   schtasks /Create /SC MINUTE /MO 30 /TN "MC Review Snapshot" ^
#     /TR "powershell -NoProfile -ExecutionPolicy Bypass -File \"<full path>\run_daily.ps1\" -World \"C:\srv\world\""
#
# Why 30 minutes: the collector stores a *snapshot* (cumulative counters), and the
# analyser diffs consecutive snapshots. Shorter interval = finer timeline, more files.

param(
    [Parameter(Mandatory = $true)][string]$World,
    [string]$Log = "",
    [string]$Out = (Join-Path $PSScriptRoot "data")
)

$ErrorActionPreference = 'Stop'
$toolDir = $PSScriptRoot

if (-not (Test-Path -LiteralPath $World)) {
    Write-Host "world path not found: $World" -ForegroundColor Red
    exit 1
}

$collectArgs = @((Join-Path $toolDir 'collect_snapshot.py'), '--world', $World, '--out', $Out)
if ($Log -ne "") { $collectArgs += @('--log', $Log) }

Write-Host "== collecting snapshot =="
& python @collectArgs
if ($LASTEXITCODE -ne 0) { Write-Host "collect failed" -ForegroundColor Red; exit $LASTEXITCODE }

Write-Host "== refreshing report =="
& python (Join-Path $toolDir 'analyze_snapshots.py') --data $Out --milestones (Join-Path $Out 'milestones.csv')
if ($LASTEXITCODE -ne 0) {
    # Fewer than 2 snapshots is expected on the very first run -- not an error.
    Write-Host "analyze skipped (need >=2 snapshots; this is normal on the first run)" -ForegroundColor Yellow
}

Write-Host "done. data dir: $Out"
Write-Host "report: $(Join-Path $Out 'report.md')"
