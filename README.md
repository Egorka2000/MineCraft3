# tf2-mc

Fabric-мод для Minecraft Java Edition: классы Team Fortress 2 со стоковым оружием. Главный приоритет — движение в стиле Source: ракетный прыжок и распрыжка.

Проект в самом начале: есть пустой мод, который собирается и запускается. Игровой логики пока нет.

## Сборка и запуск

Нужен JDK 25. Gradle ставить не надо, он скачивается сам при первом запуске. На Windows команды запускаются как `gradlew.bat`, на Linux и macOS как `./gradlew`.

| Команда | Что делает |
|---|---|
| `gradlew build` | Собирает мод и запускает все тесты, jar появляется в `build/libs/` |
| `gradlew test` | Только юнит-тесты |
| `gradlew runGameTest` | Только серверные игровые тесты |
| `gradlew runClient` | Запускает игру с модом |
| `gradlew runServer` | Запускает выделенный сервер с модом |

Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3.

## Документы

| Файл | Что внутри |
|---|---|
| [docs/README.md](docs/README.md) | Индекс и правила работы с документами |
| [docs/01-brief.md](docs/01-brief.md) | Цели, scope, классы, этапы |
| [docs/02-physics.md](docs/02-physics.md) | Спецификация физики движения |
| [docs/03-tasks.md](docs/03-tasks.md) | Задачи по этапам |
| [docs/04-decisions.md](docs/04-decisions.md) | Открытые вопросы и журнал решений |
| [docs/05-sources.md](docs/05-sources.md) | Числа TF2 и страницы, по которым они сверены |
| [docs/06-references.md](docs/06-references.md) | Похожие моды и документация |
| [docs/07-plan.md](docs/07-plan.md) | План действий до первой вехи |
| [docs/08-architecture.md](docs/08-architecture.md) | Архитектура: модули, границы, сеть, тесты |

## Ограничения

- Ассеты Valve (модели, звуки, текстуры) не используются.
- Код Source SDK не копируется, алгоритмы переписываются своими словами.

## Лицензия

Apache License 2.0, см. [LICENSE](LICENSE).
