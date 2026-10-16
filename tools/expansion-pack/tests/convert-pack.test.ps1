# Тест конвертера расширительного пака (convert-pack.ps1) на самодельном образце: таблица звуков
# в формате TF2 и пустые файлы с нужными именами. Файлы Valve не нужны.
#
#   powershell -ExecutionPolicy Bypass -File tools\expansion-pack\tests\convert-pack.test.ps1
#
# Скрипт завершается с ошибкой, если хоть одна проверка не прошла. В CI запускается задачей build.

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$converter = Join-Path $PSScriptRoot "..\convert-pack.ps1"
$work = Join-Path ([System.IO.Path]::GetTempPath()) ("tf2mc-pack-test-" + [guid]::NewGuid())
$pack = Join-Path $work "pack"
$zipPath = Join-Path $work "tf2mc-expansion.zip"
$failures = @()

function Check([bool]$condition, [string]$message) {
    if (-not $condition) { $script:failures += $message }
}

function New-File([string]$relative, [string]$text = "x") {
    $path = Join-Path $pack $relative
    New-Item -ItemType Directory -Force (Split-Path $path -Parent) | Out-Null
    [System.IO.File]::WriteAllText($path, $text)
}

try {
    # Таблица звуков: всё, что встречается в настоящих таблицах TF2.
    New-File "scripts/game_sounds_weapons.txt" @'
// Комментарий
"Weapon_RPG.Single"
{
	"channel"	"CHAN_WEAPON"
	"volume"	"1.0"
	"soundlevel"	"SNDLVL_94dB"
	"wave"		")weapons/rocket_shoot.wav"
}

"BaseExplosionEffect.Sound"
{
	"volume"	"VOL_NORM"
	"pitch"		"PITCH_NORM"
	"rndwave"
	{
		"wave"	")weapons/explode1.wav"
		"wave"	"^weapons/explode2.wav"
	}
}

"Weapon_Quiet.Single"
{
	"volume"	".5"
	"pitch"		"95,105"
	"wave"		"weapons/quiet.wav"
}

"Weapon_Silent.Single"
{
	"volume"	"0"
	"wave"		"weapons/silent.wav"
}

"Weapon_Missing.Single"
{
	"wave"		"weapons/not_in_pack.wav"
}

"Soldier.Yes01"
{
	"volume"	"0.62"
	"wave"		"vo/soldier_yes01.mp3"
}

"Bad Name"
{
	"wave"		"weapons/quiet.wav"
}
'@
    # Не звуки, а движения губ: конвертер должен его пропустить.
    New-File "scripts/game_sounds_vo_phonemes.txt" "`"vo\burp02.mp3`" { `"wave`" `"vo/soldier_yes01.mp3`" }"
    foreach ($file in "rocket_shoot", "explode1", "explode2", "quiet", "silent") { New-File "sound/weapons/$file.wav" }
    New-File "sound/vo/soldier_yes01.mp3"
    New-File "models/player/soldier.mdl"
    New-File "materials/models/player/soldier/soldier_red.vtf"

    & powershell -NoProfile -ExecutionPolicy Bypass -File $converter -PackDir $pack -ZipPath $zipPath | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "конвертер завершился с кодом $LASTEXITCODE" }

    $zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        $names = @($zip.Entries | ForEach-Object { $_.FullName })
        Check (-not ($names | Where-Object { $_.Contains('\') })) "в архиве пути через \"
        foreach ($name in "pack.mcmeta", "assets/tf2mc/sounds.json", "assets/tf2mc/sounds/weapons/rocket_shoot.wav",
                "assets/tf2mc/sounds/vo/soldier_yes01.mp3", "assets/tf2mc/tf2/models/player/soldier.mdl",
                "assets/tf2mc/tf2/materials/models/player/soldier/soldier_red.vtf",
                "assets/tf2mc/tf2/scripts/game_sounds_weapons.txt") {
            Check ($names -contains $name) "в архиве нет $name"
        }

        $reader = New-Object System.IO.StreamReader($zip.GetEntry("assets/tf2mc/sounds.json").Open())
        $raw = $reader.ReadToEnd()
        $reader.Dispose()
    } finally {
        $zip.Dispose()
    }

    Check ($raw[0] -eq '{') "sounds.json начинается не с { (метка порядка байтов?)"
    $sounds = $raw | ConvertFrom-Json
    $events = @($sounds.PSObject.Properties | ForEach-Object { $_.Name })

    # Правила Minecraft: имена строчные, громкость и высота больше нуля.
    foreach ($event in $sounds.PSObject.Properties) {
        Check ($event.Name -cmatch '^[a-z0-9/._-]+$') "недопустимое имя звука: $($event.Name)"
        foreach ($sound in $event.Value.sounds) {
            Check ($sound.name -cmatch '^tf2mc:[a-z0-9/._-]+$') "недопустимое имя файла: $($sound.name)"
            if ($null -ne $sound.volume) { Check ($sound.volume -gt 0) "громкость не больше 0: $($event.Name)" }
            if ($null -ne $sound.pitch) { Check ($sound.pitch -gt 0) "высота не больше 0: $($event.Name)" }
        }
    }

    $single = $sounds.'weapon_rpg.single'
    Check ($single.replace -eq $true) "weapon_rpg.single без replace"
    Check ($single.subtitle -eq "subtitles.tf2mc.weapon_rpg.single") "weapon_rpg.single: подпись $($single.subtitle)"
    Check ((@($single.sounds) | ForEach-Object { $_.name }) -join "," -eq "tf2mc:weapons/rocket_shoot") "weapon_rpg.single: файлы"

    $explode = @($sounds.'baseexplosioneffect.sound'.sounds | ForEach-Object { $_.name }) -join ","
    Check ($explode -eq "tf2mc:weapons/explode1,tf2mc:weapons/explode2") "взрыв: файлы из rndwave $explode"

    $quiet = @($sounds.'weapon_quiet.single'.sounds)[0]
    Check ($quiet.volume -eq 0.5) "громкость .5 прочитана как $($quiet.volume)"
    Check ($null -eq $quiet.pitch) "высота 95,105 (в среднем 100) должна остаться по умолчанию, а не $($quiet.pitch)"

    Check ($events -notcontains "weapon_silent.single") "звук с громкостью 0 попал в sounds.json"
    Check ($events -notcontains "weapon_missing.single") "звук без файла попал в sounds.json"
    Check (-not ($events | Where-Object { $_ -like "*burp*" })) "таблица движений губ попала в sounds.json"
    Check ($events -contains "soldier.yes01") "реплика в MP3 не попала в sounds.json"
    Check (@($sounds.'soldier.yes01'.sounds)[0].volume -eq 0.62) "громкость реплики"
} finally {
    Remove-Item -Recurse -Force -Confirm:$false $work -ErrorAction SilentlyContinue
}

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Host "ОШИБКА: $_" }
    throw "тест конвертера пака: ошибок $($failures.Count)"
}

Write-Host "Тест конвертера пака пройден."
