# Собирает ресурс-пак Minecraft для мода tf2-mc из расширительного пака —
# папки с файлами вашей установленной Team Fortress 2 (её делает build-pack.ps1).
# Файлы Valve остаются на вашем диске; скрипт ничего не скачивает.
#
#   .\convert-pack.ps1                       собрать ресурс-пак рядом с паком
#   .\convert-pack.ps1 -Install              и положить его в папку resourcepacks игры
#   .\convert-pack.ps1 -PackDir D:\pack      расширительный пак лежит в другом месте
#
# Звук перекодирует Blender: в него встроена нужная библиотека, ставить ничего не надо.

param(
    [string]$PackDir = (Join-Path $env:USERPROFILE "Documents\tf2mc-expansion-pack\pack"),
    [string]$OutDir = "",
    [string]$Blender = "",
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

if ($Blender -eq "") {
    $found = Get-ChildItem "C:\Program Files\Blender Foundation" -Filter blender.exe -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($null -ne $found) {
        $Blender = $found.FullName
    }
}

if ($Blender -eq "" -or -not (Test-Path $Blender)) {
    throw "Не найден Blender. Установите его с blender.org или укажите путь к blender.exe параметром -Blender."
}

$map = Get-Content (Join-Path $PSScriptRoot "sounds-map.json") -Raw -Encoding UTF8 | ConvertFrom-Json

if (Test-Path $OutDir) {
    Remove-Item -Recurse -Force -Confirm:$false $OutDir
}
New-Item -ItemType Directory -Force $OutDir | Out-Null

$soundsDir = Join-Path $OutDir "assets\tf2mc\sounds"
$jobs = @()
$sounds = [ordered]@{}
$missing = @()

foreach ($event in $map.PSObject.Properties) {
    $entries = @()

    foreach ($file in $event.Value.files) {
        $source = Join-Path $PackDir ($file -replace "/", "\")

        if (-not (Test-Path $source)) {
            $missing += $file
            continue
        }

        # sound/weapons/rocket_shoot.wav -> weapons/rocket_shoot
        $name = ($file -replace "^sound/", "") -replace "\.[A-Za-z0-9]+$", ""
        $jobs += @{ source = $source; target = (Join-Path $soundsDir (($name -replace "/", "\") + ".ogg")) }
        $entries += "tf2mc:$name"
    }

    if ($entries.Count -gt 0) {
        # replace: звуки пака встают вместо заглушек мода, а не добавляются к ним.
        $sounds[$event.Name] = [ordered]@{ replace = $true; subtitle = $event.Value.subtitle; sounds = $entries }
    }
}

if ($jobs.Count -eq 0) {
    throw "В расширительном паке не нашлось ни одного нужного звука."
}

# Файлы пишутся без метки порядка байтов: с ней игра не разберёт JSON.
$utf8 = New-Object System.Text.UTF8Encoding($false)
$jobsFile = Join-Path $env:TEMP "tf2mc-convert-jobs.json"
[System.IO.File]::WriteAllText($jobsFile, (ConvertTo-Json @($jobs) -Depth 4), $utf8)

Write-Host "Перекодирую звуков: $($jobs.Count)"
$log = & $Blender --background --factory-startup --python (Join-Path $PSScriptRoot "convert_sounds.py") -- $jobsFile
$blenderExit = $LASTEXITCODE
Remove-Item -Force -Confirm:$false $jobsFile
$log | Where-Object { $_ -match "^TF2CONV fail" } | ForEach-Object { Write-Warning $_ }

$mcmeta = [ordered]@{ pack = [ordered]@{ description = "tf2-mc: звуки из установленной Team Fortress 2"; min_format = $packFormat; max_format = $packFormat } }
[System.IO.File]::WriteAllText((Join-Path $OutDir "pack.mcmeta"), (ConvertTo-Json $mcmeta -Depth 4), $utf8)
[System.IO.File]::WriteAllText((Join-Path $OutDir "assets\tf2mc\sounds.json"), (ConvertTo-Json $sounds -Depth 6), $utf8)

$done = @(Get-ChildItem -Recurse -File $soundsDir -Filter *.ogg)
Write-Host "Готово: $($done.Count) звуков из $($jobs.Count) в $OutDir"

foreach ($file in $missing) {
    Write-Warning "Нет в расширительном паке: $file"
}

if ($blenderExit -ne 0 -or $done.Count -ne $jobs.Count) {
    throw "Перекодировано не всё: ожидалось $($jobs.Count), получилось $($done.Count)."
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
