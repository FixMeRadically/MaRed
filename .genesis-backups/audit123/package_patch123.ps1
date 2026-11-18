$ErrorActionPreference='Stop'
$projectRoot=(Get-Location).Path
$packageRoot=Join-Path $projectRoot 'artifacts/Genesis-system-bindings-123'
$beforeRoot=Join-Path $projectRoot '.genesis-backups/audit123/before'
function Hash-Bytes([byte[]]$bytes) {
    $hash=[Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($hash.ComputeHash($bytes))).Replace('-','').ToLowerInvariant() }
    finally { $hash.Dispose() }
}
function Canonical-Hash([byte[]]$bytes) {
    # Preserve bytes and BOM, normalize only CRLF exactly as the Python installer does.
    $latin=[Text.Encoding]::Latin1
    return Hash-Bytes ($latin.GetBytes($latin.GetString($bytes).Replace("`r`n","`n")))
}
$files=[Collections.Generic.List[object]]::new()
$paths=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($original in Get-ChildItem -LiteralPath $beforeRoot -Recurse -File) {
    $relative=[IO.Path]::GetRelativePath($beforeRoot,$original.FullName).Replace('\','/')
    $current=[IO.File]::ReadAllBytes((Join-Path $projectRoot $relative))
    $prior=[IO.File]::ReadAllBytes($original.FullName)
    if((Hash-Bytes $current) -eq (Hash-Bytes $prior)){continue}
    $files.Add([ordered]@{path=$relative;sha256=(Hash-Bytes $current);versions=@{workspace122=(Canonical-Hash $prior)};allow_missing=$false})
    $null=$paths.Add($relative)
}
foreach($relative in @(
    'technologies/core/src/main/java/com/fixmer/genesis/technology/workspace/live122/WorkspaceRequest123.java',
    'src/test/java/com/fixmer/genesis/technology/workspace/live122/WorkspaceRegressionChecks123.java',
    'src/test/java/com/fixmer/genesis/supervisor/SupervisorControlChecks123.java',
    'docs/GENESIS_SYSTEM_BINDINGS_123_RU.md'
)) {
    $current=[IO.File]::ReadAllBytes((Join-Path $projectRoot $relative))
    $files.Add([ordered]@{path=$relative;sha256=(Hash-Bytes $current);versions=@{};allow_missing=$true})
    $null=$paths.Add($relative)
}
foreach($entry in $files) {
    $dest=Join-Path $packageRoot ('patch/files/'+$entry.path)
    $null=New-Item -ItemType Directory -Path (Split-Path $dest) -Force
    Copy-Item -LiteralPath (Join-Path $projectRoot $entry.path) -Destination $dest
}
$required=[Collections.Generic.List[object]]::new()
foreach($relative in (& rg --files src/main src/test technologies/core/src/main)) {
    $relative=$relative.Replace('\','/')
    if(!$paths.Contains($relative)) {
        $required.Add([ordered]@{path=$relative;sha256=(Canonical-Hash ([IO.File]::ReadAllBytes((Join-Path $projectRoot $relative))))})
    }
}
$manifest=[ordered]@{schema=123;base='current-workspace122';files=$files.ToArray();required=$required.ToArray()}
[IO.File]::WriteAllText((Join-Path $packageRoot 'patch/manifest.json'),($manifest | ConvertTo-Json -Depth 12),[Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath (Join-Path $projectRoot 'docs/GENESIS_SYSTEM_BINDINGS_123_RU.md') -Destination (Join-Path $packageRoot 'README_RU.md')
Write-Output "Patch123 payloads: $($files.Count); required files: $($required.Count)"
