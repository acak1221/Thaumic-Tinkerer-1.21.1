# Thaumic Tinkerer for Thaumcraft 4 Reload (Minecraft 1.21.1)

A port of **Thaumic Tinkerer** (1.7.10, by Vazkii, pixlepix, nekosune and the GTNewHorizons contributors) to Minecraft 1.21.1 as an
add-on of **Thaumcraft 4 Reload**, for **Fabric** and **NeoForge**. Mod id `thaumictinkerer`; a separate jar per loader that depends on `thaumcraft`,
`architectury` and (on Fabric) `fabric-api`. It talks to Thaumcraft only through the public `thaumcraft.api`; a JUnit test (`TinkererApiOnlyTest`) fails the build if
a source file imports anything else of Thaumcraft.

Русская версия: [README.ru.md](README.ru.md).

> **Licence: CC BY-NC-SA 3.0** (see `LICENSE` and `NOTICE.md`). Non-commercial, share-alike. The add-on sources are under it. The build
> files of this repository (`settings.gradle`, `build.gradle`, `gradle.properties`, `fabric.gradle`, `neoforge/build.gradle`,
> `neoforge/gradle.properties`, `tools/`, the CI workflow, `scripts/porting/`) are MIT (`LICENSE-MIT`); the Gradle wrapper is Gradle's own (Apache-2.0); `NOTICE.md` lists them.

The add-on is developed in this repository. Until 2026-10-31 it was written inside the
[Thaumcraft 4 Reload repository](https://github.com/acak1221/Thaumcraft-1.21.1) (`NOTICE.md`, "History"); that repository keeps the
extensions of `thaumcraft.api` the add-on needs and no longer contains the add-on.

## Status

Both loaders are built from the same shared code: **Fabric** (the root project) and, since 2026-10-31, **NeoForge** (the subproject `:neoforge`,
`docs/NEOFORGE.md`). Fabric came first; both are checked the same way now: the GameTests on a server and the client smoke in a dev client, in a world
(`docs/CLIENT_VERIFICATION.md`).

| Part | State |
|---|---|
| Eight feature groups (simple blocks, tile blocks, machines, basic items, foci, enchantments, Kami gear, Kami world) | ported, reviewed; GameTests exist for every group. What is **recorded as run** is uneven: the documents of Kami world, Foci and (first round only) Kami gear record runs with numbers; the Machines and Basic items documents record no result of a run; the Enchantments document says that its tests ran, without a result. The one record that covers all eight is a GameTest results file, `build/tinkerer-gametest-results.xml`. The file of 2026-10-30 10:29 had 298 cases and no failure, error or skipped entry and was not tied to a console log. Later runs of the tree (2026-10-30, Gradle logs with the line "All 299 required tests passed"; the logs are not committed) wrote 299 cases, again without a failure, error or skipped entry (Tile blocks: 34 then, 35 now) (`docs/HOOKS.md` section 12, "What ran") |
| Loader hooks (`LIVING_DAMAGE`, `LIVING_ARMOR_ABSORB`, `BREAK_SPEED`, jump, fall, item-use tick, drops, floating ticks, item renderers) | written for Fabric (Mixin) and for NeoForge (handlers of NeoForge's events in `NeoForgeEventPosters` and three mixins, `docs/HOOKS.md` section 9); checks exist (JUnit and GameTests, `docs/HOOKS.md` section 12). The results file above lists the 14 hook GameTests, the two of `LIVING_ARMOR_ABSORB` among them, without a failure; no document records a run of the hook JUnit tests. On NeoForge the same 14 GameTests passed on a NeoForge GameTest server (server side only; `docs/HOOKS.md` section 9) |
| The groups that needed the hooks (Enchantments, Kami world, Kami gear, Machines, Foci) | **rewired** to them on Fabric, their workarounds deleted (`docs/HOOKS.md` section 10). Runs recorded in the group documents: Kami world 20 GameTests (together with the hook GameTests of its review round, 33 of 33) and 38 JUnit checks; Foci 36 GameTests; Kami gear a pass for its first round only (30 JUnit tests of the group plus `TinkererApiOnlyTest`, and 30 GameTests; the group has 34 JUnit tests and 36 GameTests now); Machines no result; Enchantments "ran" without a result. No totals are added up here. On NeoForge the GameTests of the five groups passed with the NeoForge posters (`docs/HOOKS.md` section 9) |
| Research tree and recipes | registered through the Thaumcraft API; compared with the 1.7.10 source by script and by a GameTest (next row) |
| Parity with the 1.7.10 source | `docs/PARITY_REPORT.md` (runs of 2026-10-30): 74 research entries and 127 recipes compared field by field with the original, and 17 object tags; **11 documented differences, 0 undocumented**; the runs recorded there are Fabric's (a GameTest on a running server: all 7 required tests passed). On NeoForge the same 7 GameTests passed in the full NeoForge run of 2026-10-31; the document does not compare that run's report |
| NeoForge | **built and run on a GameTest server** (2026-10-31; `docs/NEOFORGE.md`, `docs/HOOKS.md` section 9): the subproject `:neoforge` (`neoforge/build.gradle`) compiles the shared code (`common/src/main`, `compat/src/main`) with the NeoForge glue of `neoforge/src/main`: the `@Mod` entry, the Thaumcraft add-on hook (`@IThaumcraftAddon.Addon`), the posters of every loader event (`NeoForgeEventPosters`), four mixins (armour step, `lastHurt` access, floating ticks, Infused Farmland soil), the item renderer backend (Mob Display), the Kami gear's wings and ghost blocks, and the Transvector Interface's fluid and item capabilities. `./gradlew :neoforge:runGameTest`: "All 362 required tests passed" (2026-11-01: the 354 generated from the Fabric wrappers and 8 NeoForge-only checks). Decided differences from Fabric (`docs/HOOKS.md`, `docs/NEOFORGE.md`): the armour step is a mixin, not NeoForge's damage-container modifier; "cannot dig" is a speed of 0; the armour float check expects NeoForge's own arithmetic; NeoForge also posts the jump event for slimes, magma cubes and ridden horses and camels; the Infused Farmland needs its NeoForge soil mixin; the Interface also offers an item capability. Seen in a NeoForge dev client in a world: the NeoForge client smoke (2026-10-31: 730 checks passed, 0 failed; 739 with EMI and Jade, 735 with JEI; `docs/CLIENT_VERIFICATION.md`). A dedicated NeoForge server with real clients: the multiplayer check (`docs/MULTIPLAYER_VERIFICATION.md`). **Not seen:** the Kami wings and ghost blocks (the harness does not visit them on either loader). The CI workflow (`.github/workflows/ci.yml`) builds and tests both loaders; it has not run on GitHub yet |
| Client (models, screens, HUD) | **seen in a running dev client on Fabric and on NeoForge** under Xvfb with software rendering (Mesa llvmpipe), `docs/CLIENT_VERIFICATION.md`: the last runs of the harness recorded 728 checks passed on Fabric and 730 on NeoForge, 0 failed, 1 not run (the tool-mode tooltip needs a key press), 80 pictures, English and Russian; the defects the first run found were fixed and the run repeated. **Not seen:** a GPU, sound, input from a real mouse or keyboard, other mods than EMI, JEI and Jade (seen in their own runs), the Kami wings and ghost blocks, the original 1.7.10 client for comparison (`docs/CLIENT_VERIFICATION.md`, "What it cannot show"). **Multiplayer**: real clients on a real dedicated server, on Fabric and on NeoForge, with the config sync, spawn protection, two menu packets, a second player seeing a block-entity change and the registration-option mismatch (`docs/MULTIPLAYER_VERIFICATION.md`; final runs of 2026-11-01: 67 checks passed, 0 failed on each loader) |
| Recipe-viewer pages (EMI, JEI) and Jade tooltips | **ported** (optional: the add-on runs without these mods): the necromancy and infused-seed-drop pages of NEI as EMI and JEI categories, the Waila lines of five blocks as Jade tooltips (`docs/deviations/RecipeViewers.md`, `docs/deviations/Tooltips.md`). Tested on a server (GameTests) and **seen in the dev client** with EMI + Jade and, in a second run, with JEI: 30 + 45 pages, all 82 arcane/crucible/infusion recipes of the add-on in Thaumcraft's own categories, the tooltip text of six blocks read from Jade (`docs/CLIENT_VERIFICATION.md`). Not seen: hover tooltips, EMI and JEI together, NeoForge (the GameTests of the pages and tooltips passed there; EMI, JEI and Jade were not started on NeoForge) |
| ComputerCraft / OpenComputers peripherals, golem API | **ComputerCraft and the golem API: ported for CC: Tweaked** (optional, both loaders, `docs/deviations/ComputerCraft.md`): the peripherals of the Dynamism Tablet, the attractors, the Aspectalyzer, the Golem Connector (its golem through `thaumcraft.api.golems`, A11), Thaumcraft's Deconstruction Table, Brain in a Jar, Arcane Ear and Arcane Bore (`thaumcraft.api.machines`, A12) and of every Thaumcraft aspect container and essentia transport, the Fume Dissipator's turtle tool, the method pages of the research `PERIPHERALS`; checked on the GameTest servers of both loaders with CC: Tweaked by real Lua on real computers and a real turtle (RUNS_EN). **OpenComputers:** not ported (no 1.21.1 release) |

Nothing in this table is claimed beyond what a document or a results file records as run. The group documents differ in how much they record (first rows); where a document records no run, this table says so.

## What you need

* **JDK 21** (any vendor; the build uses a Java 21 toolchain) and git. The Gradle wrapper (8.12.1) is in the repository.
* **Thaumcraft 4 Reload, built from the commit the add-on needs.** The add-on uses extensions of `thaumcraft.api` that are on the branch `main` of
  <https://github.com/acak1221/Thaumcraft-1.21.1> since commit `4c38e3f` (2026-10-31), and the golem and machine API (`thaumcraft.api.golems`,
  `thaumcraft.api.machines`) since `316897d` (2026-11-01); a Thaumcraft without them (an older commit or release) does not
  compile the add-on. `thaumcraft_ref` in `gradle.properties` is the commit that the add-on is built and tested against (the CI uses it too). The build reads Thaumcraft from your local Maven repository (`~/.m2`, `mavenLocal()`), for **both loaders**:
  `thaumcraft:Thaumcraft-fabric:<thaumcraft_version>` for the Fabric build and `thaumcraft:Thaumcraft-neoforge:<thaumcraft_version>` for `:neoforge`
  (`gradle.properties`, now `0.1.0-alpha.7`). Install both first (`tools/publish-thaumcraft.sh` does, below); a build without the Fabric jar stops
  with a message that says so (that check looks for the Fabric jar only).
* Internet access for Maven Central, `maven.fabricmc.net`, `maven.neoforged.net`, `maven.architectury.dev` and Mojang (Minecraft, assets) on the first build.
* Memory: the Gradle daemon is set to 3 GB (`org.gradle.jvmargs` in `gradle.properties`), and the GameTest servers and the client smoke each start a JVM with
  `-Xmx3G` (`fabric.gradle`, `neoforge/build.gradle`), so plan for about 6 GB of free memory during `runTinkererGameTest`, `:neoforge:runGameTest` and `tools/client-smoke.sh`.
* Only for the client smoke: Linux with `xvfb-run`, Mesa software rendering (no GPU needed) and `python3` (the script prints a summary of `report.json`
  with it). Only for `scripts/porting`: Python 3.

## Build and test

All commands run in the root of this repository.

```bash
# 1. Thaumcraft 4 Reload into ~/.m2 (once; again when thaumcraft_ref in gradle.properties changes)
tools/publish-thaumcraft.sh                  # clones ../Thaumcraft-1.21.1 at thaumcraft_ref and runs ./gradlew :fabric:publishToMavenLocal :neoforge:publishToMavenLocal there
#    by hand:  git clone https://github.com/acak1221/Thaumcraft-1.21.1 ../Thaumcraft-1.21.1
#              cd ../Thaumcraft-1.21.1 && git checkout <thaumcraft_ref> && ./gradlew :fabric:publishToMavenLocal :neoforge:publishToMavenLocal

# 2. The add-on
./gradlew tinkererTest                       # JUnit tests of the add-on
./gradlew runTinkererGameTest                # all GameTests on a Fabric GameTest server
./gradlew runTinkererGameTest -PtinkererGameTestFilter='simpleblocksgametests.*'   # one group or test (comma list, trailing * allowed)
./gradlew remapThaumicTinkererJar            # the release jar: build/libs/ThaumicTinkerer-fabric-<version>.jar
./gradlew check                              # compiles the main, GameTest and client-smoke source sets and runs tinkererTest
                                             #   (from the root directory also :neoforge:check, which compiles the NeoForge sources and GameTests)
./gradlew runTinkererClient                  # a development client with Thaumcraft and the add-on
tools/client-smoke.sh                        # the client smoke under Xvfb, about 2 minutes after the first compile (docs/CLIENT_VERIFICATION.md)
tools/client-smoke.sh --neoforge             # the same on NeoForge (results in neoforge/build/tinkerer-client-smoke/)
tools/multiplayer-smoke.sh [--neoforge]      # dedicated servers and real clients over the network, 4 to 6 minutes (docs/MULTIPLAYER_VERIFICATION.md)
./gradlew exportTinkererClasspath && tools/compile.sh [-g]   # javac outside Gradle, for a quick edit-compile loop (-g: with the GameTest sources)

# 3. NeoForge (the subproject :neoforge; docs/NEOFORGE.md)
./gradlew :neoforge:assemble                 # the NeoForge release jar: neoforge/build/libs/ThaumicTinkerer-neoforge-<version>.jar
./gradlew :neoforge:runGameTest              # the same GameTests on a NeoForge GameTest server
./gradlew :neoforge:runGameTest -PtinkererGameTestFilter='kamiworldgametests.*'    # the same filter as the Fabric run
python3 tools/neoforge_gametests.py          # are the NeoForge GameTest wrappers in step with the Fabric ones? (--write regenerates them)
```

* **What a good run looks like** (0.1.0-alpha.7, 2026-11-01; the counts move when tests are added, a failure is what matters):

  | Command | Result |
  |---|---|
  | `./gradlew tinkererTest` | `BUILD SUCCESSFUL`; the files in `build/test-results/tinkererTest/` add up to 424 tests, 0 failures, 0 errors, 0 skipped |
  | `./gradlew runTinkererGameTest` | the log line `All 356 required tests passed :)` and `BUILD SUCCESSFUL`; `build/tinkerer-gametest-results.xml` has 356 `testcase` elements and no `failure` or `error` |
  | `./gradlew remapThaumicTinkererJar` | `BUILD SUCCESSFUL` and `build/libs/ThaumicTinkerer-fabric-0.1.0-alpha.7.jar` (about 2.3 MB) |
  | `./gradlew check` | `BUILD SUCCESSFUL` (the three source sets compile, the Mixin refmap check passes, `tinkererTest` runs) |
  | `./gradlew :neoforge:assemble` | `BUILD SUCCESSFUL` and `neoforge/build/libs/ThaumicTinkerer-neoforge-0.1.0-alpha.7.jar` |
  | `./gradlew :neoforge:runGameTest` | like `runTinkererGameTest`, with the eight NeoForge-only tests on top: `All 364 required tests passed :)`, and `neoforge/build/tinkerer-gametest-results.xml` with 364 `testcase` elements and no `failure` or `error`; the last run (2026-11-01) reached it. The earlier runs and their failures are in `docs/NEOFORGE.md`, "GameTests on NeoForge" |
  | `tools/multiplayer-smoke.sh` | `multiplayer: passed on fabric` and `BUILD SUCCESSFUL`; `build/tinkerer-multiplayer/summary.txt` lists the seven reports (two servers, five clients), each `harness COMPLETED`, and ends with `no problems`; the same with `--neoforge` (results in `neoforge/build/tinkerer-multiplayer/`). The exit code is non-zero for any FAIL, HARNESS_ERROR or NOT_RUN |
  | `tools/client-smoke.sh` | `client-smoke: harness COMPLETED; checks {PASS: ..., FAIL: 0, HARNESS_ERROR: 0, NOT_RUN: 1, ...}` and 80 screenshots in `build/tinkerer-client-smoke/`; on NeoForge (`--neoforge`, results in `neoforge/build/tinkerer-client-smoke/`) 730. On Fabric PASS was 725 to 728 in six runs (three fire-block checks depend on random pixels), FAIL 0 and NOT_RUN 1 did not move. `tools/client-smoke.sh -- -PwithEmi -PwithJade` adds the EMI and Jade checks (737 PASS, 88 screenshots), `-- -PwithJei` the JEI ones (730 PASS, 82 screenshots) |
* The release jars (`mod_version` in `gradle.properties`): for Fabric `build/libs/ThaumicTinkerer-fabric-<version>.jar` (`remapThaumicTinkererJar`, which
  the root project's `assemble` builds); it is remapped for production, carries `LICENSE` under `META-INF/` and holds the Mixin refmap. To play with it, put
  it into `mods/` next to the Thaumcraft 4 Reload jar for Fabric, Architectury API 13.0.11+ and Fabric API. For NeoForge
  `neoforge/build/libs/ThaumicTinkerer-neoforge-<version>.jar` (`./gradlew :neoforge:assemble`); it carries `LICENSE` under `META-INF/` too and needs no
  refmap (NeoForge runs on Mojang names). It goes into `mods/` of NeoForge 21.1.259+ next to the Thaumcraft 4 Reload jar for NeoForge and Architectury
  API 13.0.11+ (`neoforge.mods.toml`). On NeoForge the add-on has run on a GameTest server and in a dev client in a world (the client smoke), both from the
  build's classes, not from this jar.
* `runTinkererGameTest` writes `build/tinkerer-gametest-results.xml`; its world lives in `run-tinkerer-gametest/` (deleted at the start of every run). The
  JUnit reports are in `build/reports/tests/tinkererTest/`, the client smoke results in `build/tinkerer-client-smoke/`. All of that is git-ignored.
  `:neoforge:runGameTest` writes `neoforge/build/tinkerer-gametest-results.xml`; its world lives in `neoforge/run-gametest/` (deleted at the start of
  every run, git-ignored too).
* `tools/client-smoke.sh` runs `./gradlew` under `xvfb-run`; the environment variable `GRADLE_CMD` replaces the Gradle command (see the header of the script).
* The Fabric compile step fails if the Mixin annotation processor wrote no refmap or one without an entry for a mixin that has an injector (`fabric.gradle`).
  The NeoForge build makes no refmap.
* Files that the documents cite and that belong to the **Thaumcraft 4 Reload repository**, not to this one: `AGENTS.md`, `docs/api/` (`PORTING.md`,
  `LEGACY_IDS.md`, the guides), `docs/MIGRATION_STATUS.md`, `common/src/main/java/thaumcraft/` and `common/src/main/resources/*/thaumcraft/` (the sources and resources of Thaumcraft; `docs/API_GAPS.md` abbreviates them as `API/` and `INT/`) and `fabric/build.gradle`
  (this repository has `fabric.gradle` instead). `neoforge/build.gradle` is this repository's own: the NeoForge build of the add-on. Results that the documents quote from runs of Thaumcraft's own suites (JUnit 2761, 515 GameTests, 296 Python tests,
  `docs/DEVIATIONS.md` section 5) were runs in the Thaumcraft repository, not in this one; section 5 says which rows those are.
* `scripts/porting/` holds the Python tools that converted the 1.7.10 resources and extracted the parity oracle (MIT; see its `README.md`). They need a
  checkout of the original 1.7.10 source (and `tt_oracle.py` one of Thaumcraft); `tools/porting.sh` runs them.

## Layout

```
common/src/main      loader-neutral code, data (assets/, data/), language files
common/src/test      JUnit tests (no world)
common/src/gameTest  runtime checks used by the GameTests
fabric/src/main      Fabric entry points, Mixins, Fabric item-renderer backend
fabric/src/gameTest  GameTest wrappers and the Fabric GameTest mod
fabric/src/clientSmoke  the client smoke harness (a development tool, never in the jar)
compat/src/main      the optional EMI, JEI and Jade integrations (both loaders)
neoforge/src/main    NeoForge entry (@Mod), Thaumcraft add-on hook, event posters, mixins, client glue, block capabilities
neoforge/src/gameTest  NeoForge GameTest wrappers (generated from the Fabric ones) and the NeoForge GameTest mod
build.gradle         the root (Fabric) project: Loom, Minecraft, Fabric, Architectury and the Thaumcraft jar
fabric.gradle        source sets, runs and tasks of the Fabric add-on
neoforge/build.gradle  the subproject :neoforge: the NeoForge build, its runs and its GameTests
tools/               compile.sh, client-smoke.sh, publish-thaumcraft.sh, porting.sh, neoforge_gametests.py
scripts/porting/     Python tools of the resource and oracle pipeline (MIT)
docs/                porting rules, deviations, API requests and gaps, hooks, parity report, client verification
lang-extra/          language fragments merged into the language files
```

The registry layer is in `thaumic.tinkerer.common.registry` (`TTRegistry`, `TTRecipes`), the content in
`thaumic.tinkerer.common.content` (one registrar per group, listed in `ContentGroups`).

## Where to read next

- `docs/PORTING_RULES.md` – the rules every file follows (naming, data components, licence headers, API-only imports).
- `docs/DEVIATIONS.md` – every difference from the 1.7.10 mod, grouped, with pointers to the per-group documents in `docs/deviations/`.
- `docs/API_GAPS.md`, `docs/api-requests/` – what the add-on needed from the Thaumcraft API and how each request was answered.
- `docs/HOOKS.md` – the loader events and how they are posted (Fabric and NeoForge).
- `docs/NEOFORGE.md` – the NeoForge build: its GameTests, its glue and what was verified there.
- `docs/PARITY_REPORT.md` – the research/recipe comparison with the original source.
- `docs/CLIENT_VERIFICATION.md` – the client smoke harness and what it cannot show.
- `docs/AUDIT.md` – the defect audit of 2026-11-01: what was found, fixed and verified.
