# Собирает ресурс-пак Minecraft для мода tf2-mc из расширительного пака —
# папки с файлами вашей установленной Team Fortress 2 (её делает build-pack.ps1).
# Файлы Valve остаются на вашем диске; скрипт ничего не скачивает.
#
#   .\convert-pack.ps1                       собрать ресурс-пак рядом с паком
#   .\convert-pack.ps1 -Install              и положить его в папку resourcepacks игры
#   .\convert-pack.ps1 -PackDir D:\pack      расширительный пак лежит в другом месте
#   .\convert-pack.ps1 -Install -GameDir D:\Games\profile   игра стоит не в %appdata%\.minecraft
#
# Никакие программы не нужны: звуки копируются как есть, в формате WAV. Игра сама его
# не понимает, но мод tf2-mc умеет читать WAV из ресурс-паков.

param(
    [string]$PackDir = (Join-Path $env:USERPROFILE "Documents\tf2mc-expansion-pack\pack"),
    [string]$OutDir = "",
    [string]$GameDir = (Join-Path $env:APPDATA ".minecraft"),
    [switch]$Install
)

$ErrorActionPreference = "Stop"

# Версия формата ресурс-пака для Minecraft 26.3.
$packFormat = 97
$packName = "tf2mc-expansion"

if (-not (Test-Path $PackDir)) {
    throw "Не найден расширительный пак: $PackDir. Сначала соберите его скриптом build-pack.ps1 или укажите папку параметром -PackDir."
}

if ($OutDir -eq "") {
    $OutDir = Join-Path (Split-Path $PackDir -Parent) $packName
}

$map = Get-Content (Join-Path $PSScriptRoot "sounds-map.json") -Raw -Encoding UTF8 | ConvertFrom-Json

if (Test-Path $OutDir) {
    Remove-Item -Recurse -Force -Confirm:$false $OutDir
}
New-Item -ItemType Directory -Force $OutDir | Out-Null

$soundsDir = Join-Path $OutDir "assets\tf2mc\sounds"
$sounds = [ordered]@{}
$copied = 0
$missing = @()
$skipped = @()

foreach ($event in $map.PSObject.Properties) {
    $entries = @()

    foreach ($file in $event.Value.files) {
        $source = Join-Path $PackDir ($file -replace "/", "\")

        if (-not (Test-Path $source)) {
            $missing += $file
            continue
        }

        # Мод читает только WAV; сжатые звуки (MP3) пока не поддерживаются.
        if ([System.IO.Path]::GetExtension($source) -ne ".wav") {
            $skipped += $file
            continue
        }

        # sound/weapons/rocket_shoot.wav -> weapons/rocket_shoot
        $name = ($file -replace "^sound/", "") -replace "\.[A-Za-z0-9]+$", ""
        $target = Join-Path $soundsDir (($name -replace "/", "\") + ".wav")

        New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
        Copy-Item $source $target
        $entries += "tf2mc:$name"
        $copied++
    }

    if ($entries.Count -gt 0) {
        # replace: звуки пака встают вместо заглушек мода, а не добавляются к ним.
        $sounds[$event.Name] = [ordered]@{ replace = $true; subtitle = $event.Value.subtitle; sounds = $entries }
    }
}

if ($copied -eq 0) {
    throw "В расширительном паке не нашлось ни одного нужного звука."
}

# Файлы пишутся без метки порядка байтов: с ней игра не разберёт JSON.
$utf8 = New-Object System.Text.UTF8Encoding($false)
$mcmeta = [ordered]@{ pack = [ordered]@{ description = "tf2-mc: звуки из установленной Team Fortress 2"; min_format = $packFormat; max_format = $packFormat } }
[System.IO.File]::WriteAllText((Join-Path $OutDir "pack.mcmeta"), (ConvertTo-Json $mcmeta -Depth 4), $utf8)
[System.IO.File]::WriteAllText((Join-Path $OutDir "assets\tf2mc\sounds.json"), (ConvertTo-Json $sounds -Depth 6), $utf8)

Write-Host "Готово: звуков $copied в $OutDir"

foreach ($file in $missing) {
    Write-Warning "Нет в расширительном паке: $file"
}

foreach ($file in $skipped) {
    Write-Warning "Пропущен, формат пока не поддерживается: $file"
}

if ($Install) {
    $target = Join-Path $GameDir "resourcepacks\$packName"
    New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
    if (Test-Path $target) {
        Remove-Item -Recurse -Force -Confirm:$false $target
    }
    Copy-Item -Recurse $OutDir $target
    Write-Host "Установлено: $target. Включите пак в игре: Настройки -> Наборы ресурсов."
}
