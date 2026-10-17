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
# Стоковое оружие девяти классов, включая часы Spy.
$weapons = "rocketlauncher|shotgun|shovel|scattergun|pistol|bat|flamethrower|fireaxe_pyro|grenadelauncher|" +
    "stickybomb_launcher|bottle|minigun|wrench|pda_engineer|builder|toolbox|medigun|syringegun|bonesaw|fireaxe|" +
    "sniperrifle|smg|machete|revolver|knife|sapper|spy_watch"

# Пак собирается «на вырост»: всё, что может понадобиться моду для девяти классов со стоковым
# оружием, построек, режимов и HUD, чтобы не пересобирать его с каждой новой механикой.
# Не берутся предметы мастерской, косметика и карты: это гигабайты, которые мод не использует.

# Материалы (VMT лежат в tf2_misc, текстуры VTF — в tf2_textures).
$materials = @(
    # Персонажи, их обломки, руки; без косметики (items).
    "^materials/models/player/(?!items/)",
    "^materials/models/gibs/",
    # Освещение моделей TF2: мягкие тени персонажей и оружия.
    "^materials/models/lightwarps/",
    # Стоковое оружие; старые модели v_ и w_ целиком — они небольшие.
    "^materials/models/weapons/c_items/c_($weapons)[a-z0-9_]*\.",
    "^materials/models/weapons/c_models/c_($weapons)/",
    "^materials/models/weapons/[vw]_",
    # Постройки, аптечки и патроны, флаг, реквизит режимов (шкаф, точка захвата, вагонетка), эффекты моделей.
    "^materials/models/buildables/",
    "^materials/models/items/",
    "^materials/models/flag/",
    "^materials/models/props_gameplay/",
    "^materials/models/props_trainyard/",
    "^materials/models/effects/",
    # Частицы, спрайты, следы на стенах; эффекты без предметов мастерской.
    "^materials/effects/(?!workshop/)",
    "^materials/particle/",
    "^materials/particles/",
    "^materials/sprites/",
    "^materials/decals/",
    # HUD: значки, убийства, прицелы, корень интерфейса.
    "^materials/hud/",
    "^materials/vgui/[^/]+$",
    "^materials/vgui/crosshairs/"
)

# Архив TF2 -> какие файлы из него нужны.
$sources = [ordered]@{
    "tf2_misc_dir.vpk" = @(
        # Персонажи, их анимации и обломки; руки от первого лица и их анимации.
        "^models/player/($classes)(_animations)?\.",
        "^models/player/gibs/",
        "^models/weapons/c_models/c_($classes)_(arms|animations)\.",
        # Стоковое оружие; все снаряды и старые модели оружия.
        "^models/weapons/c_models/c_($weapons)(/|\.)",
        "^models/weapons/[vw]_models/",
        "^models/weapons/shells/",
        # Постройки инженера, аптечки и патроны, флаг, реквизит режимов, эффекты.
        "^models/buildables/",
        "^models/items/",
        "^models/flag/",
        "^models/props_gameplay/",
        "^models/props_trainyard/bomb_cart",
        "^models/effects/",
        # Системы частиц: взрывы, огонь, лучи лечения, следы ракет.
        "^particles/",
        # Таблицы TF2: звуки, голосовые команды, оружие, раскладки HUD.
        "^scripts/[^/]+\.txt$",
        "^resource/(?!ui/quests/)"
    ) + $materials
    "tf2_textures_dir.vpk" = $materials
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

# Шрифты TF2 для HUD лежат в папке игры отдельными файлами, не в архивах.
$fonts = @(Get-ChildItem (Join-Path $Tf2Dir "tf\resource") -Filter "*.ttf" -File -ErrorAction SilentlyContinue)
if ($fonts.Count -gt 0) {
    New-Item -ItemType Directory -Force (Join-Path $OutDir "resource") | Out-Null
    $fonts | Copy-Item -Destination (Join-Path $OutDir "resource")
    $total += $fonts.Count
}

$done = @(Get-ChildItem -Recurse -File $OutDir)
$sizeMb = [Math]::Round(($done | Measure-Object -Sum Length).Sum / 1MB)
Write-Host "Готово: $($done.Count) файлов, $sizeMb МБ в $OutDir"

if ($done.Count -ne $total) {
    Write-Warning "Распаковано не всё: ожидалось $total файлов."
}
