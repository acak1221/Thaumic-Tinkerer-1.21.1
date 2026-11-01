# Thaumic Tinkerer для Thaumcraft 4 Reload (Minecraft 1.21.1)

Порт **Thaumic Tinkerer** (1.7.10; авторы Vazkii, pixlepix, nekosune и участники GTNewHorizons) на Minecraft 1.21.1 как аддон к
**Thaumcraft 4 Reload**, для **Fabric** и **NeoForge**. Идентификатор мода `thaumictinkerer`; для каждого загрузчика отдельный jar, зависящий
от `thaumcraft`, `architectury` и (на Fabric) `fabric-api`. С Thaumcraft аддон разговаривает только через публичный `thaumcraft.api`: JUnit-тест
`TinkererApiOnlyTest` роняет сборку, если какой-нибудь исходник импортирует что-то ещё из Thaumcraft.

English version: [README.md](README.md).

> **Лицензия: CC BY-NC-SA 3.0** (см. `LICENSE` и `NOTICE.md`). Некоммерческая, с условием распространения на тех же условиях. Ей подчинены
> исходники аддона. Файлы сборки этого репозитория (`settings.gradle`, `build.gradle`, `gradle.properties`, `fabric.gradle`, `neoforge/build.gradle`,
> `neoforge/gradle.properties`, `tools/`, workflow CI и релизов, `scripts/porting/`) под MIT (`LICENSE-MIT`); Gradle wrapper — собственная работа Gradle (Apache-2.0); их перечень в `NOTICE.md`.

Аддон разрабатывается в этом репозитории. До 2026-10-31 он писался внутри
[репозитория Thaumcraft 4 Reload](https://github.com/acak1221/Thaumcraft-1.21.1) (`NOTICE.md`, раздел «History»); там остаются расширения
`thaumcraft.api`, которые нужны аддону, а самого аддона там больше нет.

## Состояние


Оба загрузчика собираются из одного общего кода: **Fabric** (корневой проект) и, с 2026-10-31, **NeoForge** (подпроект `:neoforge`,
`docs/NEOFORGE.md`). Fabric был первым; теперь оба проверяются одинаково: GameTests на сервере и клиентский осмотр в dev-клиенте, в мире
(`docs/CLIENT_VERIFICATION.md`).

| Часть | Состояние |
|---|---|
| Восемь групп содержимого (простые блоки, блоки с TileEntity, машины, базовые предметы, фокусы, зачарования, Kami-снаряжение, Kami-мир) | перенесены, проверены ревью; GameTests есть у каждой группы. То, что **записано как запущенное**, неравномерно: документы Kami-мира, фокусов и (только первый раунд) Kami-снаряжения фиксируют запуски с числами; документы машин и базовых предметов не фиксируют результата запуска; документ зачарований говорит, что его тесты запускались, но без результата. Единственная запись, охватывающая все восемь групп, — файл результатов GameTest `build/tinkerer-gametest-results.xml`. В файле от 2026-10-30 10:29 было 298 случаев и ни одного элемента failure, error или skipped, и он не был связан с консольным логом. Более поздние запуски дерева (2026-10-30, логи Gradle со строкой «All 299 required tests passed»; логи не закоммичены) записали 299 случаев, снова без failure, error и skipped (блоки с TileEntity: тогда 34, теперь 35) (`docs/HOOKS.md`, раздел 12, «What ran») |
| Слой хуков (`LIVING_DAMAGE`, `LIVING_ARMOR_ABSORB`, `BREAK_SPEED`, прыжок, падение, тик использования предмета, дроп, floating-тики, рендереры предметов) | написан для Fabric (Mixin) и для NeoForge (обработчики событий NeoForge в `NeoForgeEventPosters` и три миксина, `docs/HOOKS.md`, раздел 9); проверки существуют (JUnit и GameTests, `docs/HOOKS.md`, раздел 12). Файл результатов выше перечисляет 14 GameTests хуков, среди них два для `LIVING_ARMOR_ABSORB`, без сбоев; ни один документ не фиксирует запуск JUnit-тестов хуков. На NeoForge те же 14 GameTests прошли на тестовом сервере NeoForge (только серверная сторона; `docs/HOOKS.md`, раздел 9) |
| Группы, которым нужны хуки (зачарования, Kami-мир, Kami-снаряжение, машины, фокусы) | **переведены** на хуки на Fabric, обходные пути удалены (`docs/HOOKS.md`, раздел 10). Запуски, записанные в документах групп: Kami-мир — 20 GameTests (вместе с GameTests хуков его раунда ревью, 33 из 33) и 38 JUnit-проверок; фокусы — 36 GameTests; Kami-снаряжение — прохождение зафиксировано только для первого раунда (30 JUnit-тестов группы плюс `TinkererApiOnlyTest` и 30 GameTests; сейчас у группы 34 JUnit-теста и 36 GameTests); машины — без результата; зачарования — «запускались» без результата. Общих итогов здесь не складываем. На NeoForge GameTests этих пяти групп прошли с отправкой событий NeoForge (`docs/HOOKS.md`, раздел 9) |
| Дерево исследований и рецепты | регистрируются через API Thaumcraft; сверены с исходником 1.7.10 скриптом и GameTest (следующая строка) |
| Паритет с исходником 1.7.10 | `docs/PARITY_REPORT.md` (прогоны 2026-10-30): сверены по полям 74 записи исследований и 127 рецептов с оригиналом, а также 17 тегов объектов; **11 задокументированных отличий, 0 незадокументированных** (12 с 2026-11-01: исследование левитационного локомотива показывает схему мультиблока, которую оригинал строит, но не показывает; GameTests паритета в полных прогонах того дня прошли на обоих загрузчиках); записанные там прогоны сделаны на Fabric (GameTest на запущенном сервере: пройдены все 7 обязательных тестов). На NeoForge те же 7 GameTests прошли в полном прогоне NeoForge от 2026-10-31; отчёт этого прогона документ не сверяет |
| NeoForge | **собран и запущен на тестовом сервере** (2026-10-31; `docs/NEOFORGE.md`, `docs/HOOKS.md`, раздел 9): подпроект `:neoforge` (`neoforge/build.gradle`) компилирует общий код (`common/src/main`, `compat/src/main`) вместе со связующим кодом NeoForge из `neoforge/src/main`: точка входа `@Mod`, хук аддона Thaumcraft (`@IThaumcraftAddon.Addon`), отправка всех событий загрузчика (`NeoForgeEventPosters`), четыре миксина (шаг брони, доступ к `lastHurt`, floating-тики, почва Infused Farmland), реализация рендереров предметов (Mob Display), крылья и призрачные блоки Kami-снаряжения, capability жидкостей, предметов и энергии у Transvector Interface. `./gradlew :neoforge:runGameTest`: «All 362 required tests passed» (2026-11-01: 354, сгенерированных из обёрток Fabric, и 8 проверок только для NeoForge). Решённые отличия от Fabric (`docs/HOOKS.md`, `docs/NEOFORGE.md`): шаг брони — миксин, а не модификатор damage container NeoForge; «копать нельзя» — скорость 0; проверка float брони ожидает собственную арифметику NeoForge; NeoForge отправляет событие прыжка ещё и для слизней, лавовых кубов и прыжков верхом на лошадях и верблюдах; Infused Farmland нужен свой миксин почвы для NeoForge; Interface на NeoForge отдаёт ещё и capability предметов. Просмотрено в dev-клиенте NeoForge в мире: клиентский осмотр на NeoForge (2026-10-31: 730 проверок пройдено, 0 провалов; 739 с EMI и Jade, 735 с JEI; `docs/CLIENT_VERIFICATION.md`). **Не просмотрено:** выделенный сервер NeoForge с настоящими клиентами, крылья и призрачные блоки Kami (стенд их не посещает ни на одном загрузчике). Workflow CI (`.github/workflows/ci.yml`) собирает и проверяет оба загрузчика; на GitHub он ещё не запускался |
| Клиент (модели, экраны, HUD) | **просмотрен в запущенном dev-клиенте Fabric** под Xvfb с программным рендерингом (Mesa llvmpipe), `docs/CLIENT_VERIFICATION.md`: последний прогон стенда записал 726 пройденных проверок, 0 провалов, 1 не запущенную (подсказка режима инструмента требует нажатия клавиши), 80 снимков, английский и русский; дефекты, найденные первым прогоном, исправлены, прогон повторён. **Не просмотрено:** видеокарта, звук, ввод (клики, клавиши, перетаскивание), мультиплеер, другие моды, кроме EMI, JEI и Jade (их смотрели отдельными запусками), крылья и призрачные блоки Kami, оригинальный клиент 1.7.10 для сравнения (`docs/CLIENT_VERIFICATION.md`, «What it cannot show») |
| Страницы в просмотрщиках рецептов (EMI, JEI) и подсказки Jade | **перенесены** (необязательные: аддон работает и без этих модов): страницы NEI «некромантия» и «выпадение из заражённых семян» стали категориями EMI и JEI, строки Waila пяти блоков — подсказками Jade (`docs/deviations/RecipeViewers.md`, `docs/deviations/Tooltips.md`). Проверены на сервере (GameTests) и **просмотрены в dev-клиенте** с EMI + Jade и, отдельным запуском, с JEI: 30 + 45 страниц, все 82 рецепта аддона для верстака, тигля и инфузии в собственных категориях Thaumcraft, текст подсказок шести блоков прочитан из Jade (`docs/CLIENT_VERIFICATION.md`). Не просмотрено: всплывающие подсказки при наведении, EMI и JEI вместе, NeoForge (GameTests страниц и подсказок там прошли; EMI, JEI и Jade на NeoForge не запускались) |
| Энергия через Transvector Interface (RF в 1.7.10) | **перенесена** (2026-11-01, `docs/deviations/TileBlocks.md`, 2.5): на NeoForge — `Capabilities.EnergyStorage.BLOCK`; на Fabric — через **Team Reborn Energy**, необязательный мод (аддон компилируется с ним, но не включает его в jar; без него аддон работает, а Interface на Fabric энергию не передаёт). Количества передаются как есть, без пересчёта. GameTests на обоих загрузчиках с тестовой энергоячейкой; `runTinkererGameTest -PwithoutTeamRebornEnergy` запускает сервер Fabric без этого мода. EU из IC2 не перенесена (IC2 для 1.21.1 нет) |
| Периферия ComputerCraft / OpenComputers, API големов | не перенесены (см. `docs/DEVIATIONS.md`, «Не перенесено») |

Ничего из этой таблицы не заявлено сверх того, что документ или файл результатов фиксирует как запущенное. Документы групп
записывают запуски в разном объёме (первые строки); где документ не фиксирует запуска, таблица так и говорит.

## Что нужно

* **JDK 21** (любого поставщика; сборка использует toolchain Java 21) и git. Gradle wrapper (8.12.1) лежит в репозитории.
* **Thaumcraft 4 Reload, собранный из нужного аддону коммита.** Аддон использует расширения `thaumcraft.api`, которые есть в ветке `main`
  репозитория <https://github.com/acak1221/Thaumcraft-1.21.1> начиная с коммита `4c38e3f` (2026-10-31); Thaumcraft без них (более старый коммит или релиз)
  аддон не скомпилирует. `thaumcraft_ref` в `gradle.properties` — коммит, с которым аддон собирается и проверяется (CI берёт его же).
  Сборка берёт Thaumcraft из локального Maven-репозитория (`~/.m2`, `mavenLocal()`) для **обоих загрузчиков**:
  `thaumcraft:Thaumcraft-fabric:<thaumcraft_version>` для сборки Fabric и `thaumcraft:Thaumcraft-neoforge:<thaumcraft_version>` для `:neoforge`
  (`gradle.properties`, сейчас `0.1.0-alpha.7`). Сначала установите оба (это делает `tools/publish-thaumcraft.sh`, см. ниже); сборка без jar для Fabric
  останавливается сообщением об этом (эта проверка ищет только jar для Fabric).
* Доступ в интернет к Maven Central, `maven.fabricmc.net`, `maven.neoforged.net`, `maven.architectury.dev` и Mojang (Minecraft, ассеты) при первой сборке.
* Память: демон Gradle настроен на 3 ГБ (`org.gradle.jvmargs` в `gradle.properties`), а серверы GameTest и клиентский осмотр запускают каждый свою JVM с
  `-Xmx3G` (`fabric.gradle`, `neoforge/build.gradle`), поэтому на время `runTinkererGameTest`, `:neoforge:runGameTest` и `tools/client-smoke.sh`
  рассчитывайте примерно на 6 ГБ свободной памяти.
* Только для клиентского осмотра: Linux с `xvfb-run`, программным рендерингом Mesa (видеокарта не нужна) и `python3` (скрипт печатает с его помощью
  сводку `report.json`). Только для `scripts/porting`: Python 3.

## Сборка и тесты

Все команды выполняются в корне этого репозитория.

```bash
# 1. Thaumcraft 4 Reload в ~/.m2 (один раз; снова — когда изменился thaumcraft_ref в gradle.properties)
tools/publish-thaumcraft.sh                  # клонирует ../Thaumcraft-1.21.1 на thaumcraft_ref и запускает там ./gradlew :fabric:publishToMavenLocal :neoforge:publishToMavenLocal
#    вручную:  git clone https://github.com/acak1221/Thaumcraft-1.21.1 ../Thaumcraft-1.21.1
#              cd ../Thaumcraft-1.21.1 && git checkout <thaumcraft_ref> && ./gradlew :fabric:publishToMavenLocal :neoforge:publishToMavenLocal

# 2. Аддон
./gradlew tinkererTest                       # JUnit-тесты аддона
./gradlew runTinkererGameTest                # все GameTests на тестовом сервере Fabric
./gradlew runTinkererGameTest -PtinkererGameTestFilter='simpleblocksgametests.*'   # группа или тест (список через запятую, * в конце допустима)
./gradlew runTinkererGameTest -PwithoutTeamRebornEnergy   # без необязательного Team Reborn Energy (проверки энергии тогда проверяют, что аддон работает без него)
./gradlew remapThaumicTinkererJar            # релизный jar: build/libs/ThaumicTinkerer-fabric-<версия>.jar
./gradlew check                              # компилирует наборы main, GameTest и client-smoke и запускает tinkererTest
                                             #   (из корня ещё и :neoforge:check, который компилирует исходники и GameTests NeoForge)
./gradlew runTinkererClient                  # клиент для разработки с Thaumcraft и аддоном
tools/client-smoke.sh                        # клиентский осмотр под Xvfb, около 2 минут после первой компиляции (docs/CLIENT_VERIFICATION.md)
tools/client-smoke.sh --neoforge             # то же на NeoForge (результаты в neoforge/build/tinkerer-client-smoke/)
./gradlew exportTinkererClasspath && tools/compile.sh [-g]   # javac вне Gradle для быстрого цикла правка-компиляция (-g: вместе с исходниками GameTest)

# 3. NeoForge (подпроект :neoforge; docs/NEOFORGE.md)
./gradlew :neoforge:assemble                 # релизный jar для NeoForge: neoforge/build/libs/ThaumicTinkerer-neoforge-<версия>.jar
./gradlew :neoforge:runGameTest              # те же GameTests на тестовом сервере NeoForge
./gradlew :neoforge:runGameTest -PtinkererGameTestFilter='kamiworldgametests.*'    # тот же фильтр, что у запуска Fabric
python3 tools/neoforge_gametests.py          # совпадают ли обёртки GameTest для NeoForge с обёртками Fabric? (--write генерирует их заново)
```

* **Как выглядит хороший запуск** (0.1.0-alpha.7, 2026-11-01; числа меняются, когда добавляются тесты, важно отсутствие сбоев):

  | Команда | Результат |
  |---|---|
  | `./gradlew tinkererTest` | `BUILD SUCCESSFUL`; файлы в `build/test-results/tinkererTest/` дают в сумме 424 теста, 0 сбоев, 0 ошибок, 0 пропущенных |
  | `./gradlew runTinkererGameTest` | строка лога `All 357 required tests passed :)` и `BUILD SUCCESSFUL`; в `build/tinkerer-gametest-results.xml` 357 элементов `testcase` и ни одного `failure` или `error` |
  | `./gradlew remapThaumicTinkererJar` | `BUILD SUCCESSFUL` и `build/libs/ThaumicTinkerer-fabric-0.1.0-alpha.7.jar` (около 2,3 МБ) |
  | `./gradlew check` | `BUILD SUCCESSFUL` (три набора исходников компилируются, проверка refmap Mixin проходит, `tinkererTest` запускается) |
  | `./gradlew :neoforge:assemble` | `BUILD SUCCESSFUL` и `neoforge/build/libs/ThaumicTinkerer-neoforge-0.1.0-alpha.7.jar` |
  | `./gradlew :neoforge:runGameTest` | как у `runTinkererGameTest`, плюс восемь тестов только для NeoForge: `All 365 required tests passed :)` и `neoforge/build/tinkerer-gametest-results.xml` с 365 элементами `testcase` без `failure` и `error`; последний запуск (2026-11-01) это показал. Более ранние запуски и их сбои — в `docs/NEOFORGE.md`, «GameTests on NeoForge» |
  | `tools/client-smoke.sh` | `client-smoke: harness COMPLETED; checks {PASS: ..., FAIL: 0, HARNESS_ERROR: 0, NOT_RUN: 1, ...}` и 80 снимков в `build/tinkerer-client-smoke/`; на NeoForge (`--neoforge`, результаты в `neoforge/build/tinkerer-client-smoke/`) 730. На Fabric PASS был 725–728 в шести запусках (три проверки блоков огня зависят от случайных пикселей), FAIL 0 и NOT_RUN 1 не менялись. `tools/client-smoke.sh -- -PwithEmi -PwithJade` добавляет проверки EMI и Jade (737 PASS, 88 снимков), `-- -PwithJei` — проверки JEI (730 PASS, 82 снимка) |
* Релизные jar (`mod_version` в `gradle.properties`): для Fabric — `build/libs/ThaumicTinkerer-fabric-<версия>.jar` (`remapThaumicTinkererJar`, его
  собирает `assemble` корневого проекта); он переотображён для продакшена, несёт `LICENSE` в `META-INF/` и содержит refmap Mixin. Чтобы играть с ним,
  положите его в `mods/` рядом с jar Thaumcraft 4 Reload для Fabric, Architectury API 13.0.11+ и Fabric API. Для NeoForge —
  `neoforge/build/libs/ThaumicTinkerer-neoforge-<версия>.jar` (`./gradlew :neoforge:assemble`); он тоже несёт `LICENSE` в `META-INF/` и refmap не требует
  (NeoForge работает на именах Mojang). Он кладётся в `mods/` NeoForge 21.1.259+ рядом с jar Thaumcraft 4 Reload для NeoForge и Architectury API
  13.0.11+ (`neoforge.mods.toml`). На NeoForge аддон запускался на тестовом сервере и в dev-клиенте в мире (клиентский осмотр), оба раза из классов
  сборки, а не из этого jar.
* `runTinkererGameTest` пишет `build/tinkerer-gametest-results.xml`; его мир лежит в `run-tinkerer-gametest/` (удаляется в начале каждого запуска).
  Отчёты JUnit — в `build/reports/tests/tinkererTest/`, результаты клиентского осмотра — в `build/tinkerer-client-smoke/`. Всё это игнорируется git.
  `:neoforge:runGameTest` пишет `neoforge/build/tinkerer-gametest-results.xml`; его мир лежит в `neoforge/run-gametest/` (удаляется в начале каждого
  запуска, тоже игнорируется git).
* `tools/client-smoke.sh` запускает `./gradlew` под `xvfb-run`; переменная окружения `GRADLE_CMD` заменяет команду Gradle (см. заголовок скрипта).
* Компиляция для Fabric падает, если процессор аннотаций Mixin не записал refmap или записал refmap без записи для миксина с инжектором (`fabric.gradle`).
  Сборка NeoForge refmap не создаёт.
* Файлы, на которые ссылаются документы и которые принадлежат **репозиторию Thaumcraft 4 Reload**, а не этому: `AGENTS.md`, `docs/api/` (`PORTING.md`,
  `LEGACY_IDS.md`, руководства), `docs/MIGRATION_STATUS.md`, `common/src/main/java/thaumcraft/` и `common/src/main/resources/*/thaumcraft/` (исходники и ресурсы Thaumcraft; `docs/API_GAPS.md` сокращает их до `API/` и `INT/`) и `fabric/build.gradle`
  (в этом репозитории вместо него `fabric.gradle`). `neoforge/build.gradle` принадлежит этому репозиторию: это сборка аддона под NeoForge. Результаты, которые документы приводят по запускам собственных наборов тестов Thaumcraft (JUnit 2761, 515 GameTest,
  296 Python-тестов, `docs/DEVIATIONS.md` раздел 5), — запуски в репозитории Thaumcraft, а не в этом; раздел 5 говорит, какие строки это.
* В `scripts/porting/` лежат Python-инструменты, которыми конвертировались ресурсы 1.7.10 и извлекался оракул паритета (MIT; см. их `README.md`).
  Им нужен checkout исходников оригинала 1.7.10 (а `tt_oracle.py` ещё и checkout Thaumcraft); `tools/porting.sh` запускает их.
* **Релизы** (`.github/workflows/release.yml`): тег `v<mod_version>` на коммите `main` собирает Thaumcraft 4 Reload на `thaumcraft_ref`,
  прогоняет JUnit и GameTest обоих загрузчиков и публикует релиз на GitHub с jar для Fabric и для NeoForge и их суммами SHA-256 (версия с
  `-alpha`, `-beta` или `-rc` публикуется как пре-релиз). Запуск вручную (`workflow_dispatch`) без тега — пробный: та же сборка, jar как артефакт,
  ничего не публикуется. Релиз работает только со сборкой Thaumcraft 4 Reload, в которой есть API коммита `thaumcraft_ref`.

## Структура

```
common/src/main      код и данные (assets/, data/), не зависящие от загрузчика; языковые файлы
common/src/test      JUnit-тесты (без мира)
common/src/gameTest  проверки времени выполнения для GameTests
fabric/src/main      точки входа Fabric, Mixin, реализация рендереров предметов для Fabric
fabric/src/gameTest  обёртки GameTest и тестовый мод Fabric
fabric/src/clientSmoke  стенд клиентского осмотра (инструмент разработки, в jar не попадает)
compat/src/main      необязательные интеграции с EMI, JEI и Jade (оба загрузчика)
neoforge/src/main    точка входа NeoForge (@Mod), хук аддона Thaumcraft, отправка событий, миксины, клиентский связующий код, capability блоков
neoforge/src/gameTest  обёртки GameTest для NeoForge (сгенерированы из обёрток Fabric) и тестовый мод NeoForge
build.gradle         корневой проект (Fabric): Loom, Minecraft, Fabric, Architectury и jar Thaumcraft
fabric.gradle        наборы исходников, запуски и задачи аддона для Fabric
neoforge/build.gradle  подпроект :neoforge: сборка под NeoForge, её запуски и GameTests
tools/               compile.sh, client-smoke.sh, publish-thaumcraft.sh, porting.sh, neoforge_gametests.py
scripts/porting/     Python-инструменты конвейера ресурсов и оракула (MIT)
docs/                правила переноса, отступления, запросы к API и пробелы, хуки, отчёт о паритете, клиентская проверка
lang-extra/          фрагменты локализации, сливаемые в языковые файлы
```

Слой реестров — `thaumic.tinkerer.common.registry` (`TTRegistry`, `TTRecipes`), содержимое —
`thaumic.tinkerer.common.content` (по регистратору на группу, список в `ContentGroups`).

## Что читать дальше

- `docs/PORTING_RULES.md` — правила, которым следует каждый файл (имена, компоненты данных, заголовки лицензий, импорты только из API).
- `docs/DEVIATIONS.md` — все отличия от мода 1.7.10 по группам, со ссылками на документы групп в `docs/deviations/`.
- `docs/API_GAPS.md`, `docs/api-requests/` — что аддону понадобилось от API Thaumcraft и как закрыт каждый запрос.
- `docs/HOOKS.md` — события загрузчика и как они отправляются (Fabric и NeoForge).
- `docs/NEOFORGE.md` — сборка под NeoForge: её GameTests, связующий код и что там проверено.
- `docs/PARITY_REPORT.md` — сверка исследований и рецептов с исходником оригинала.
- `docs/CLIENT_VERIFICATION.md` — стенд клиентского осмотра и то, чего он показать не может.
- `docs/AUDIT.md` — аудит дефектов 2026-11-01: что найдено, исправлено и проверено.
