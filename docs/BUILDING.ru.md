# Сборка и тесты

Пути в этом документе даны от корня репозитория. English version: [BUILDING.md](BUILDING.md).

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
tools/multiplayer-smoke.sh [--neoforge]      # выделенные серверы и настоящие клиенты по сети, с Open Parties and Claims на сервере, 6–8 минут (docs/MULTIPLAYER_VERIFICATION.md)
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
  Для следующего выпуска измените и отправьте `mod_version`, затем выполните `git tag v<mod_version>` и `git push origin v<mod_version>`.
  **Actions → Release → Run workflow** без тега проверяет весь цикл без публикации. С тегом ручной запуск собирает именно отмеченный им
  коммит; публикация требует флажка `publish` и наличия коммита в `main`. Отчёты должны содержать успешные тесты без ошибок и пропусков.
  К обоим JAR прикрепляются `SHA256SUMS.txt` и `BUILD_INFO.json` с коммитами исходников и фактическими результатами тестов. Незавершённую
  загрузку можно повторить в черновик; опубликованные релизы не перезаписываются. Используются штатный `GITHUB_TOKEN` и кэш Gradle.
  Настройка автоматизации релизов выполнена с помощью OpenAI Codex (GPT-6).

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
