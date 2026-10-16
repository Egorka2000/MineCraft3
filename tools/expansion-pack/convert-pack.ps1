# Собирает ресурс-пак Minecraft для мода tf2-mc из расширительного пака —
# папки с файлами вашей установленной Team Fortress 2 (её делает build-pack.ps1).
# Файлы Valve остаются на вашем диске; скрипт ничего не скачивает.
#
#   .\convert-pack.ps1                       собрать архив tf2mc-expansion.zip рядом с паком
#   .\convert-pack.ps1 -Install              и положить его в папку resourcepacks игры
#   .\convert-pack.ps1 -PackDir D:\pack      расширительный пак лежит в другом месте
#   .\convert-pack.ps1 -Install -GameDir D:\Games\profile   игра стоит не в %appdata%\.minecraft
#
# Пак собирается один раз на все версии мода. В него попадает всё, что достал build-pack.ps1:
#   assets/tf2mc/sounds/...  звуки TF2 как есть, в WAV и MP3 (мод сам читает WAV);
#   assets/tf2mc/sounds.json все звуки из таблиц TF2 (scripts/game_sounds*.txt) под их родными
#                            именами строчными буквами: Weapon_RPG.Single -> tf2mc:weapon_rpg.single;
#   assets/tf2mc/tf2/...     модели, текстуры и таблицы TF2 как есть, для моделей персонажей.
# Мод берёт из пака то, что ему нужно, по этим именам, поэтому новой версии мода новый пак не нужен.
#
# Готовый архив можно класть в resourcepacks как есть, распаковывать не нужно.

param(
    [string]$PackDir = (Join-Path $env:USERPROFILE "Documents\tf2mc-expansion-pack\pack"),
    [string]$ZipPath = "",
    [string]$GameDir = (Join-Path $env:APPDATA ".minecraft"),
    [switch]$Install
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression

# Версия формата ресурс-пака для Minecraft 26.3.
$packFormat = 97
$packName = "tf2mc-expansion"

if (-not (Test-Path $PackDir)) {
    throw "Не найден расширительный пак: $PackDir. Сначала соберите его скриптом build-pack.ps1 или укажите папку параметром -PackDir."
}

$PackDir = (Resolve-Path $PackDir).Path

if ($ZipPath -eq "") {
    $ZipPath = Join-Path (Split-Path $PackDir -Parent) "$packName.zip"
}

# Имена в Minecraft: только строчные латинские буквы, цифры и знаки / . _ -
$validName = '^[a-z0-9/._-]+$'

# --- Таблицы звуков TF2 -> sounds.json --------------------------------------------------------

# Разбирает файл формата KeyValues: "ключ" "значение" и "ключ" { ... }.
function Read-KeyValue([string]$path) {
    $text = [System.IO.File]::ReadAllText($path)
    $tokens = [regex]::Matches($text, '//[^\n]*|"([^"]*)"|[{}]|[^\s{}"]+')
    $script:kvTokens = New-Object System.Collections.Generic.List[object]
    foreach ($t in $tokens) {
        if ($t.Value.StartsWith("//")) { continue }
        if ($t.Groups[1].Success) { $script:kvTokens.Add(@("s", $t.Groups[1].Value)) }
        elseif ($t.Value -eq "{" -or $t.Value -eq "}") { $script:kvTokens.Add(@($t.Value, $t.Value)) }
        else { $script:kvTokens.Add(@("s", $t.Value)) }
    }
    $script:kvPos = 0
    return , (Read-Block $false)
}

# Список пар (ключ, значение); значение — строка или вложенный список.
function Read-Block([bool]$inner) {
    $pairs = New-Object System.Collections.ArrayList
    while ($script:kvPos -lt $script:kvTokens.Count) {
        $tok = $script:kvTokens[$script:kvPos]
        if ($tok[0] -eq "}") {
            $script:kvPos++
            if ($inner) { break } else { continue }
        }
        if ($tok[0] -eq "{") {
            # Блок без ключа: пропускаем.
            $script:kvPos++
            [void](Read-Block $true)
            continue
        }
        $key = $tok[1]
        $script:kvPos++
        if ($script:kvPos -ge $script:kvTokens.Count) { break }
        $next = $script:kvTokens[$script:kvPos]
        if ($next[0] -eq "{") {
            $script:kvPos++
            [void]$pairs.Add(@($key, (Read-Block $true)))
        } elseif ($next[0] -eq "s") {
            $script:kvPos++
            # Условия вида [$WIN32] после значения пропускаем.
            if ($script:kvPos -lt $script:kvTokens.Count -and $script:kvTokens[$script:kvPos][1] -match '^\[.*\]$') { $script:kvPos++ }
            [void]$pairs.Add(@($key, $next[1]))
        }
    }
    return , $pairs
}

# Все значения "wave" в записи, в том числе внутри "rndwave".
function Get-Wave($pairs) {
    $result = @()
    foreach ($p in $pairs) {
        if ($p[1] -is [System.Collections.ArrayList]) { $result += Get-Wave $p[1] }
        elseif ($p[0] -ieq "wave") { $result += $p[1] }
    }
    return $result
}

function Get-Value($pairs, [string]$key) {
    foreach ($p in $pairs) {
        if ($p[0] -ieq $key -and $p[1] -is [string]) { return $p[1] }
    }
    return $null
}

# "1", ".5", "VOL_NORM", "0.7, 1.0" -> число; диапазон -> середина.
function Convert-Number([string]$value, [hashtable]$names, [double]$default) {
    if ($null -eq $value) { return $default }
    $parts = @($value -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ -ne "" })
    $numbers = @()
    foreach ($part in $parts) {
        if ($names.ContainsKey($part.ToUpper())) { $numbers += $names[$part.ToUpper()]; continue }
        $n = 0.0
        if ([double]::TryParse($part, [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$n)) { $numbers += $n }
    }
    if ($numbers.Count -eq 0) { return $default }
    return ($numbers | Measure-Object -Average).Average
}

$volumeNames = @{ "VOL_NORM" = 1.0 }
$pitchNames = @{ "PITCH_NORM" = 100.0; "PITCH_LOW" = 95.0; "PITCH_HIGH" = 120.0 }

$soundRoot = Join-Path $PackDir "sound"
$sounds = [ordered]@{}
$skippedEvents = 0
$skippedSilent = 0

# game_sounds_vo_phonemes.txt — не звуки, а движения губ для реплик; он лежит в паке как есть.
$scripts = @(Get-ChildItem (Join-Path $PackDir "scripts") -Filter "game_sounds*.txt" | Where-Object { $_.Name -ne "game_sounds_vo_phonemes.txt" } | Sort-Object Name)
foreach ($scriptFile in $scripts) {
    foreach ($entry in (Read-KeyValue $scriptFile.FullName)) {
        if (-not ($entry[1] -is [System.Collections.ArrayList])) { continue }

        $eventName = $entry[0].ToLowerInvariant()
        if ($sounds.Contains($eventName)) { continue }
        if ($eventName -notmatch $validName) { $skippedEvents++; continue }

        $volume = [Math]::Round([Math]::Min(1.0, (Convert-Number (Get-Value $entry[1] "volume") $volumeNames 1.0)), 3)
        $pitch = [Math]::Round([Math]::Min(2.0, [Math]::Max(0.5, (Convert-Number (Get-Value $entry[1] "pitch") $pitchNames 100.0) / 100.0)), 3)

        # Звуки с нулевой громкостью в TF2 молчат, а Minecraft отвергает из-за одного такого
        # весь sounds.json пака. Их просто нет в паке.
        if ($volume -le 0) { $skippedSilent++; continue }

        $entries = @()
        foreach ($wave in (Get-Wave $entry[1])) {
            # Перед путём TF2 ставит служебные знаки: ")weapons/x.wav", "^vo/x.mp3".
            $file = ($wave -replace '^[^A-Za-z0-9_]+', '').Replace('\', '/').ToLowerInvariant()
            if (-not (Test-Path -LiteralPath (Join-Path $soundRoot ($file -replace '/', '\')))) { continue }

            $name = $file -replace '\.[a-z0-9]+$', ''
            if ($name -notmatch $validName) { continue }

            $sound = [ordered]@{ name = "tf2mc:$name" }
            if ($volume -ne 1.0) { $sound.volume = $volume }
            if ($pitch -ne 1.0) { $sound.pitch = $pitch }
            $entries += $sound
        }

        if ($entries.Count -gt 0) {
            # replace: звуки пака встают вместо заглушек мода, а не добавляются к ним.
            $sounds[$eventName] = [ordered]@{ replace = $true; subtitle = "subtitles.tf2mc.$eventName"; sounds = $entries }
        }
    }
}

if ($sounds.Count -eq 0) {
    throw "В расширительном паке не нашлось ни одного звука из таблиц TF2."
}

# --- Архив ---------------------------------------------------------------------------------

# Путь в паке TF2 -> путь в ресурс-паке; $null — файл не нужен.
function Get-EntryName([string]$relative) {
    $relative = $relative.Replace('\', '/').ToLowerInvariant()
    if ($relative.StartsWith("sound/")) { $target = "assets/tf2mc/sounds/" + $relative.Substring(6) }
    else { $target = "assets/tf2mc/tf2/" + $relative }
    if ($target -notmatch $validName) { return $null }
    return $target
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$mcmeta = [ordered]@{ pack = [ordered]@{ description = "tf2-mc: звуки и модели из Team Fortress 2"; min_format = $packFormat; max_format = $packFormat } }

if (Test-Path $ZipPath) {
    Remove-Item -Force -Confirm:$false $ZipPath
}

$files = @(Get-ChildItem -Recurse -File $PackDir)
$added = 0
$skippedFiles = @()
$stream = [System.IO.File]::Open($ZipPath, [System.IO.FileMode]::CreateNew)
$zip = New-Object System.IO.Compression.ZipArchive($stream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    # Имена внутри архива — через /, как требует Minecraft (Windows сама поставила бы \).
    function Add-Text([string]$name, [string]$text) {
        $writer = New-Object System.IO.StreamWriter($zip.CreateEntry($name).Open(), $utf8)
        try { $writer.Write($text) } finally { $writer.Dispose() }
    }

    # Файлы пишутся без метки порядка байтов: с ней игра не разберёт JSON.
    Add-Text "pack.mcmeta" (ConvertTo-Json $mcmeta -Depth 4)
    Add-Text "assets/tf2mc/sounds.json" (ConvertTo-Json $sounds -Depth 6)

    foreach ($file in $files) {
        $name = Get-EntryName $file.FullName.Substring($PackDir.Length + 1)
        if ($null -eq $name) { $skippedFiles += $file.FullName; continue }

        # MP3 уже сжат, жать его ещё раз — только терять время.
        $level = if ($file.Extension -eq ".mp3") { [System.IO.Compression.CompressionLevel]::NoCompression } else { [System.IO.Compression.CompressionLevel]::Optimal }
        $output = $zip.CreateEntry($name, $level).Open()
        $source = [System.IO.File]::OpenRead($file.FullName)
        try { $source.CopyTo($output) } finally { $source.Dispose(); $output.Dispose() }
        $added++
    }
} finally {
    $zip.Dispose()
    $stream.Dispose()
}

$sizeMb = [Math]::Round((Get-Item $ZipPath).Length / 1MB)
Write-Host "Готово: $ZipPath, $sizeMb МБ. Файлов $added, звуков в sounds.json $($sounds.Count)."

if ($skippedEvents -gt 0) {
    Write-Warning "Пропущено звуков с недопустимыми для Minecraft именами: $skippedEvents"
}
if ($skippedSilent -gt 0) {
    Write-Host "Пропущено беззвучных звуков (громкость 0): $skippedSilent"
}
foreach ($file in $skippedFiles) {
    Write-Warning "Пропущен файл с недопустимым для Minecraft именем: $file"
}

if ($Install) {
    $packs = Join-Path $GameDir "resourcepacks"
    New-Item -ItemType Directory -Force $packs | Out-Null

    # Пак старого вида — папка tf2mc-expansion; новый его заменяет.
    $oldFolder = Join-Path $packs $packName
    if (Test-Path $oldFolder) {
        Remove-Item -Recurse -Force -Confirm:$false $oldFolder
    }

    Copy-Item -Force $ZipPath (Join-Path $packs "$packName.zip")
    Write-Host "Установлено: $packs\$packName.zip. Включите пак в игре: Настройки -> Наборы ресурсов."
}
