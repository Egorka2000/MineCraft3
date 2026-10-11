# 06. Референсы

Моды, статьи и документация, на которые опирается проект, с выводами по каждому. Чужой код читать для понимания подхода, не копировать: у части модов лицензия запрещает производные работы.

## Моды с движением в стиле Source/Quake

Список собран поиском 2026-10-11, сами проекты ещё не изучены. Версии и лицензии взяты из описаний на площадках, перед использованием перепроверить.

| Мод | Где | Версии Minecraft | Заметка |
|---|---|---|---|
| goldsqource | [GitHub](https://github.com/GameChaos/goldsqource) | 26.3 | **Основной референс.** Движение в стиле GoldSrc, Kotlin, лицензия Unlicense, обновлён 2026-10-05. Собран под ту же версию и с именами Mojang. Миксины прочитаны 2026-10-11, см. ниже. |
| Squake++ | [GitHub](https://github.com/polina4096/SquakePlusPlus) | 1.19.4 | Клиентский Fabric-мод: распрыжка, стрейф, воздушный контроль. Лицензия Unlicense, последнее обновление в 2023 году, имена Yarn. Миксины прочитаны 2026-10-11: перехват начала `travel` у игрока с отменой ванильного расчёта, плюс перехваты прыжка и задержки между прыжками. Сам расчёт движения ещё не читался. |
| MomentumReforged | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/momentumreforged) | 26.1.2, 26.2 | Распрыжка в стиле Source на миксинах, автор ninjabowtie, лицензия Apache 2.0. Исходники не опубликованы: ссылки на репозиторий на странице нет, поиск по GitHub и в интернете ничего не дал. Как референс не используется. |
| re:squake | [GitHub](https://github.com/polina4096/resquake) | 1.21.4 | Переписанный на Kotlin Squake, лицензия Unlicense, имена Yarn. Предок goldsqource. |
| source_movement | [GitHub](https://github.com/Jorbon/source_movement) | 1.21.10 | Полная замена движения игрока, порты движения Portal. Лицензия GPL-3.0: код не переносить, проект под Apache 2.0. |
| SourceHop | [GitHub](https://github.com/ZipeStudio/SourceHop) | 1.21.2–1.21.5 | Клиентская распрыжка Source. Лицензия CC-BY-ND: производные работы запрещены, код не использовать. |
| Bunnyhop | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/bunnyhop) | 1.21.11 | Заменяет движение на физику Source. На ванильном сервере игрока отдёргивает назад. |
| mcsow | [Modrinth](https://modrinth.com/project/mcsow) | 1.21.11 | Порт движения Warsow. |
| BunnyHop+ | [Modrinth](https://modrinth.com/mod/bunnyhop+) | 1.16.5–1.21.8 | Лицензия CC-BY-NC-ND: производные работы запрещены, код не использовать. |

Не читать: репозиторий `momentum-mod/game` (Momentum Mod). Это игра на движке Source, её код движения основан на Source SDK, а правило проекта запрещает копировать код SDK.

### Точки встраивания в goldsqource (Minecraft 26.3)

Прочитаны только три файла миксинов, сам расчёт движения (`MvPlayer.kt`) не читался.

| Класс игры | Метод | Что делает мод |
|---|---|---|
| `Player` | `travel(Vec3)` | Перехват в начале; если свой расчёт отработал, ванильный отменяется |
| `Player` | `tick()` | Подготовка перед тиком |
| `LivingEntity` | `aiStep()` | Обнуляет поле `noJumpDelay`, то есть убирает ванильную задержку между прыжками |
| `LivingEntity` | `jumpFromGround()` | Перехват до и после прыжка: буфер прыжка и поправка скорости |
| `Entity` | `moveRelative(float, Vec3)` | Подмена ванильного ускорения |

Расчёт движения (`MvPlayer.kt`) прочитан 2026-10-11. Ответы на вопросы шага 3:

- **Предсказание и сервер.** Мод работает только на клиенте: на серверной стороне все его перехваты сразу выходят. Сервер о моде не знает и принимает позицию клиента как обычно.
- **Подшаги.** Не используются, один расчёт за тик. Ускорение в воздухе считается так, будто шаг равен 1/100 с.
- **Что остаётся ванильным.** Гравитация, столкновения (штатный `move`), вода, лава, элитры, творческий полёт, езда верхом. Лестницы мод считает сам.
- **Масштаб.** 40 единиц на блок (72 / 1,8 — по росту игрока Half-Life), у нас 45.

## Код Minecraft

Исходники игры для чтения получаются командой `gradlew genSources`: она декомпилирует Minecraft в кеш Gradle, в репозиторий ничего не попадает. Выводы из чтения кода движения 26.3 записаны в `08-architecture.md`, раздел 5.

## Документация

- [Fabric: документация для разработчиков](https://docs.fabricmc.net/develop/)
- [Fabric: Data Attachments](https://docs.fabricmc.net/develop/serialization/data-attachments) — хранение данных на игроке с сохранением и синхронизацией.
- [Fabric: сеть](https://docs.fabricmc.net/develop/networking), [автоматические тесты](https://docs.fabricmc.net/develop/automatic-testing).
- [Fabric API 26.3: пакет attachment.v1](https://maven.fabricmc.net/docs/fabric-api-0.160.4+26.3/net/fabricmc/fabric/api/attachment/v1/package-summary.html)
- [Fabric: миграция маппингов](https://docs.fabricmc.net/develop/porting/mappings) — с версии 26.1 Minecraft не обфусцирован, Yarn больше не поддерживается, используются имена Mojang.
- [TF2 Wiki](https://wiki.teamfortress.com/) — числа классов и оружия. Страницы классов с описанием, оружием и механиками: [Soldier](https://wiki.teamfortress.com/wiki/Soldier); ссылки на остальные классы даёт владелец проекта по запросу.
- [Valve Developer Community](https://developer.valvesoftware.com/wiki/Team_Fortress_2/Mapper%27s_Reference) — размеры игрока.

## Описания механики движения

- [Quakeworld Air Physics](https://mattias.niklewski.com/2013/01/qw_air_physics.html) — откуда берётся ограничение 30 u/s и почему поворот при стрейфе даёт прирост скорости.
- [Replicating Source Engine Air Strafing in Unity](https://www.theseus.fi/bitstream/handle/10024/507593/Peltola_Mikko.pdf) — дипломная работа: разбор формулы ускорения в воздухе по шагам, пересчёт единиц.
- [TF2 Wiki, Strafing](https://wiki.teamfortress.com/wiki/Strafing) — описание воздушного стрейфа словами.
- [Mathematical description of air strafing](https://backup.jump.tf/forums/forums.tf2jump.com/index.php/topic%2C2369.0.html) — форум jump.tf, замеры для TF2.

Не открывать: репозитории с утёкшими исходниками TF2 (встречаются в поисковой выдаче) и файлы движения из Source SDK ради алгоритмов. Числа из открытого SDK использовать можно, см. `05-sources.md`.
