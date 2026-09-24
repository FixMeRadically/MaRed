# fix.ps1 — правит package и import в каждом .java
# Save as UTF-8 with BOM

$ErrorActionPreference = "Stop"
$ROOT = Join-Path $PSScriptRoot "src\main\java\com\fixmer\mared"
$pkgRoot = "com.fixmer.mared"

Write-Host "=== Fix packages and imports ===" -ForegroundColor Cyan

# ============================================================
#  Шаг 1. Индексируем все .java: имя класса -> FQCN
# ============================================================

$classMap = @{}
Get-ChildItem -Path $ROOT -Recurse -Filter *.java | ForEach-Object {
    $rel = $_.FullName.Substring($ROOT.Length).TrimStart('\','/')
    $dir = Split-Path $rel -Parent
    $name = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)

    if ([string]::IsNullOrEmpty($dir)) { $pkg = $pkgRoot }
    else { $pkg = $pkgRoot + "." + ($dir -replace '\\', '.') }

    $classMap[$name] = "$pkg.$name"
}

Write-Host "  Classes indexed: $($classMap.Count)" -ForegroundColor Gray

# ============================================================
#  Шаг 2. Патчим каждый файл
# ============================================================

$patched = 0

Get-ChildItem -Path $ROOT -Recurse -Filter *.java | ForEach-Object {
    $file = $_
    $raw = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $original = $raw

    # --- правим package по реальному пути ---
    $rel = $file.FullName.Substring($ROOT.Length).TrimStart('\','/')
    $dir = Split-Path $rel -Parent
    if ([string]::IsNullOrEmpty($dir)) { $expectedPkg = $pkgRoot }
    else { $expectedPkg = $pkgRoot + "." + ($dir -replace '\\', '.') }

    $raw = [regex]::Replace($raw, '(?m)^package\s+[a-zA-Z0-9_.]+\s*;', "package $expectedPkg;")

    # --- собираем уже существующие импорты ---
    $existingImports = @{}
    [regex]::Matches($raw, '(?m)^import\s+([a-zA-Z0-9_.]+)\s*;') | ForEach-Object {
        $existingImports[$_.Groups[1].Value] = $true
    }

    # --- сканируем тело (без import-строк) на имена классов ---
    $body = [regex]::Replace($raw, '(?m)^import\s+[a-zA-Z0-9_.]+\s*;\s*$', '')

    $need = @{}
    [regex]::Matches($body, '\b([A-Z][A-Za-z0-9_]+)\b') | ForEach-Object {
        $cls = $_.Groups[1].Value
        if (-not $classMap.ContainsKey($cls)) { return }
        $fqcn = $classMap[$cls]
        $pkgOfCls = $fqcn.Substring(0, $fqcn.LastIndexOf('.'))
        if ($pkgOfCls -eq $expectedPkg) { return }
        if ($existingImports.ContainsKey($fqcn)) { return }
        # Не трогаем сами классы из этого же файла
        if ($cls -eq [System.IO.Path]::GetFileNameWithoutExtension($file.Name)) { return }
        $need[$fqcn] = $true
    }

    # --- удаляем битые импорты старых путей ---
    # (script.MaredX, event.MaredX, storage.MaredX, gui.MaredX которые реально переместились)
    $raw = [regex]::Replace($raw, '(?m)^import\s+com\.fixmer\.mared\.script\.[a-zA-Z0-9_.]+\s*;\s*$', '')
    $raw = [regex]::Replace($raw, '(?m)^import\s+com\.fixmer\.mared\.script\.commands\.[a-zA-Z0-9_.]+\s*;\s*$', '')
    $raw = [regex]::Replace($raw, '(?m)^import\s+com\.fixmer\.mared\.event\.[a-zA-Z0-9_.]+\s*;\s*$', '')
    $raw = [regex]::Replace($raw, '(?m)^import\s+com\.fixmer\.mared\.storage\.[a-zA-Z0-9_.]+\s*;\s*$', '')
    $raw = [regex]::Replace($raw, '(?m)^import\s+com\.fixmer\.mared\.gui\.MaredCommandRegistry\s*;\s*$', '')

    # --- добавляем недостающие импорты ---
    if ($need.Count -gt 0) {
        $importLines = $need.Keys | Sort-Object | ForEach-Object { "import $_;" }

        $lines = $raw -split "`r?`n", -1
        $insertAt = -1
        for ($i = 0; $i -lt [Math]::Min($lines.Length, 60); $i++) {
            if ($lines[$i] -match '^\s*import\s+[a-zA-Z0-9_.]+\s*;\s*$') { $insertAt = $i }
        }
        if ($insertAt -lt 0) {
            for ($i = 0; $i -lt [Math]::Min($lines.Length, 20); $i++) {
                if ($lines[$i] -match '^\s*package\s+[a-zA-Z0-9_.]+\s*;\s*$') { $insertAt = $i; break }
            }
        }

        if ($insertAt -ge 0) {
            $newLines = @()
            for ($i = 0; $i -lt $lines.Length; $i++) {
                $newLines += $lines[$i]
                if ($i -eq $insertAt) { $newLines += $importLines }
            }
            $raw = $newLines -join "`r`n"
        }
    }

    # --- чистим пустые строки в блоке импортов ---
    # (не критично, но аккуратно)
    $raw = [regex]::Replace($raw, "(\r?\n){3,}", "`r`n`r`n")

    if ($raw -ne $original) {
        [System.IO.File]::WriteAllText($file.FullName, $raw, (New-Object System.Text.UTF8Encoding $false))
        Write-Host "  patched $rel" -ForegroundColor Green
        $patched++
    }
}

Write-Host ""
Write-Host "Patched: $patched files" -ForegroundColor Cyan
Write-Host "Next: ./gradlew compileJava" -ForegroundColor White