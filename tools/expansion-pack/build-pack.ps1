# Собирает расширительный пак tf2-mc из установленной на этом компьютере Team Fortress 2.
# Файлы Valve берутся из вашей копии игры и остаются на вашем диске; скрипт ничего не скачивает.
#
#   .\build-pack.ps1            собрать пак в Документы\tf2mc-expansion-pack\pack
#   .\build-pack.ps1 -List      только показать, сколько файлов попадёт в пак
#   .\build-pack.ps1 -Tf2Dir "D:\Steam\steamapps\common\Team Fortress 2"

param(
    [string]$Tf2Dir = "C:\Program Files (x86)\Steam\steamapps\common\Team Fortress 2",
    [string]$OutDir = (Join-Path $env:USERPROFILE "Documents\tf2mc-expansion-pack\pack"),
    [string]$Only = "",
    [switch]$List
)

$ErrorActionPreference = "Stop"

$classes = "scout|soldier|pyro|demo|heavy|engineer|medic|sniper|spy"
$voices = "scout|soldier|pyro|demoman|heavy|engineer|medic|sniper|spy"
# Стоковое оружие девяти классов.
$weapons = "rocketlauncher|shotgun|shovel|scattergun|pistol|bat|flamethrower|fireaxe_pyro|grenadelauncher|" +
    "stickybomb_launcher|bottle|minigun|wrench|pda_engineer|builder|toolbox|medigun|syringegun|bonesaw|" +
    "sniperrifle|smg|machete|revolver|knife|sapper"
$projectiles = "rocket|grenade_grenadelauncher|stickybomb|syringe_proj"
$buildings = "sentry1|sentry2|sentry3|sentry_shield|dispenser|teleporter|toolbox"
$pickups = "(ammopack|medkit)_(small|medium|large)"

# Архив TF2 -> какие файлы из него нужны.
$sources = [ordered]@{
    "tf2_misc_dir.vpk" = @(
        # Персонажи и их анимации, руки от первого лица.
        "^models/player/($classes)(_animations)?\.",
        "^models/weapons/c_models/c_($classes)_(arms|animations)\.",
        # Оружие, снаряды, постройки инженера, аптечки и патроны, флаг и контрольная точка.
        "^models/weapons/c_models/c_($weapons)(/|\.)",
        "^models/weapons/w_models/w_($projectiles)\.",
        "^models/buildables/($buildings)[a-z0-9_]*\.",
        "^models/items/$pickups\.",
        "^models/flag/briefcase\.",
        "^models/props_gameplay/cap_point_base\.",
        # Описания материалов для всего перечисленного.
        "^materials/models/player/($classes|hvyweapon|shared)/",
        "^materials/models/weapons/c_items/c_($weapons)[a-z0-9_]*\.",
        "^materials/models/weapons/c_models/c_($weapons)/",
        "^materials/models/weapons/w_($projectiles)",
        "^materials/models/buildables/",
        "^materials/models/items/(ammopack|medkit)",
        "^materials/models/flag/",
        # Таблицы: какое событие какой звук играет, меню голосовых команд.
        "^scripts/voicecommands\.txt$",
        "^scripts/game_sounds[a-z0-9_]*\.txt$"
    )
    "tf2_textures_dir.vpk" = @(
        "^materials/models/player/($classes|hvyweapon|shared)/",
        "^materials/models/weapons/c_items/c_($weapons)[a-z0-9_]*\.",
        "^materials/models/weapons/c_models/c_($weapons)/",
        "^materials/models/weapons/w_($projectiles)",
        "^materials/models/buildables/",
        "^materials/models/items/(ammopack|medkit)",
        "^materials/models/flag/"
    )
    "tf2_sound_misc_dir.vpk" = @(
        "^sound/weapons/",
        "^sound/player/",
        "^sound/items/",
        "^sound/misc/",
        "^sound/ui/"
    )
    "tf2_sound_vo_english_dir.vpk" = @(
        # Реплики и голосовые команды классов, диктор.
        "^sound/vo/($voices)_",
        "^sound/vo/announcer_"
    )
}

$vpk = Join-Path $Tf2Dir "bin\vpk.exe"
if (-not (Test-Path $vpk)) {
    throw "Не найдена TF2: нет файла $vpk. Укажите папку игры параметром -Tf2Dir."
}

$total = 0
$plan = [ordered]@{}

foreach ($archive in $sources.Keys) {
    $archivePath = Join-Path $Tf2Dir "tf\$archive"
    if (-not (Test-Path $archivePath)) {
        throw "Не найден архив $archivePath."
    }

    $pattern = ($sources[$archive] -join "|")
    $files = @(& $vpk l $archivePath | Where-Object { $_ -match $pattern -and $_ -match $Only })
    $plan[$archivePath] = $files
    $total += $files.Count
    Write-Host ("{0,-32} {1,6} файлов" -f $archive, $files.Count)
}

Write-Host ("{0,-32} {1,6} файлов" -f "Всего", $total)

if ($List) {
    return
}

if (Test-Path $OutDir) {
    Remove-Item -Recurse -Force -Confirm:$false $OutDir
}
New-Item -ItemType Directory -Force $OutDir | Out-Null

Push-Location $OutDir
try {
    foreach ($archivePath in $plan.Keys) {
        $files = $plan[$archivePath]

        # vpk.exe сам папки не создаёт.
        $files | ForEach-Object { Split-Path $_ -Parent } | Sort-Object -Unique | ForEach-Object {
            New-Item -ItemType Directory -Force $_ | Out-Null
        }

        # Пачками, чтобы не упереться в предел длины командной строки.
        for ($i = 0; $i -lt $files.Count; $i += 100) {
            $last = [Math]::Min($i + 99, $files.Count - 1)
            & $vpk x $archivePath $files[$i..$last] | Out-Null
        }
    }
} finally {
    Pop-Location
}

$done = @(Get-ChildItem -Recurse -File $OutDir)
$sizeMb = [Math]::Round(($done | Measure-Object -Sum Length).Sum / 1MB)
Write-Host "Готово: $($done.Count) файлов, $sizeMb МБ в $OutDir"

if ($done.Count -ne $total) {
    Write-Warning "Распаковано не всё: ожидалось $total файлов."
}
