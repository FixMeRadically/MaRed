param(
    [Parameter(Mandatory=$true)][string]$Project,
    [switch]$Check,
    [string]$Restore
)
$ErrorActionPreference = 'Stop'
$installer = Join-Path $PSScriptRoot 'patch/apply_changes.py'
if ($Restore) {
    & py -3 $installer $Project --restore $Restore
} elseif ($Check) {
    & py -3 $installer $Project --check
} else {
    & py -3 $installer $Project --check
    if ($LASTEXITCODE -ne 0) { throw 'Unknown or incomplete Workspace122 base; no files changed. Run the installer with --diagnose for details.' }
    & py -3 $installer $Project
}
if ($LASTEXITCODE -ne 0) { throw 'Genesis123 installation/check failed. Keep the diagnostic output.' }
