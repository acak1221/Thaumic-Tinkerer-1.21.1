# Building and testing

Paths in this document are relative to the root of the repository. Русская версия: [BUILDING.ru.md](BUILDING.ru.md).

## What you need

* **JDK 21** (any vendor; the build uses a Java 21 toolchain) and git. The Gradle wrapper (8.12.1) is in the repository.
* **Thaumcraft 4 Reload, built from the commit the add-on needs.** The add-on uses extensions of `thaumcraft.api` that are on the branch `main` of
  <https://github.com/acak1221/Thaumcraft-1.21.1> since commit `4c38e3f` (2026-10-31); a Thaumcraft without them (an older commit or release) does not
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
./gradlew runTinkererGameTest -PwithoutTeamRebornEnergy   # without the optional Team Reborn Energy (the energy checks then check that the add-on runs without it)
./gradlew remapThaumicTinkererJar            # the release jar: build/libs/ThaumicTinkerer-fabric-<version>.jar
./gradlew check                              # compiles the main, GameTest and client-smoke source sets and runs tinkererTest
                                             #   (from the root directory also :neoforge:check, which compiles the NeoForge sources and GameTests)
./gradlew runTinkererClient                  # a development client with Thaumcraft and the add-on
tools/client-smoke.sh                        # the client smoke under Xvfb, about 2 minutes after the first compile (docs/CLIENT_VERIFICATION.md)
tools/client-smoke.sh --neoforge             # the same on NeoForge (results in neoforge/build/tinkerer-client-smoke/)
tools/multiplayer-smoke.sh [--neoforge]      # dedicated servers and real clients over the network, with Open Parties and Claims on the server, 6 to 8 minutes (docs/MULTIPLAYER_VERIFICATION.md)
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
  | `./gradlew runTinkererGameTest` | the log line `All 357 required tests passed :)` and `BUILD SUCCESSFUL`; `build/tinkerer-gametest-results.xml` has 357 `testcase` elements and no `failure` or `error` |
  | `./gradlew remapThaumicTinkererJar` | `BUILD SUCCESSFUL` and `build/libs/ThaumicTinkerer-fabric-0.1.0-alpha.7.jar` (about 2.3 MB) |
  | `./gradlew check` | `BUILD SUCCESSFUL` (the three source sets compile, the Mixin refmap check passes, `tinkererTest` runs) |
  | `./gradlew :neoforge:assemble` | `BUILD SUCCESSFUL` and `neoforge/build/libs/ThaumicTinkerer-neoforge-0.1.0-alpha.7.jar` |
  | `./gradlew :neoforge:runGameTest` | like `runTinkererGameTest`, with the eight NeoForge-only tests on top: `All 365 required tests passed :)`, and `neoforge/build/tinkerer-gametest-results.xml` with 365 `testcase` elements and no `failure` or `error`; the last run (2026-11-01) reached it. The earlier runs and their failures are in `docs/NEOFORGE.md`, "GameTests on NeoForge" |
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
* **Releases** (`.github/workflows/release.yml`): a tag `v<mod_version>` on a commit of `main` builds Thaumcraft 4 Reload at `thaumcraft_ref`,
  runs JUnit and the GameTests of both loaders, and publishes a GitHub release with the Fabric and the NeoForge jar and their SHA-256 sums (a version
  with `-alpha`, `-beta` or `-rc` is a pre-release). Started by hand (`workflow_dispatch`) without a tag it is a dry run: the same build, the jars as an
  artifact, nothing published. A release is only usable with a Thaumcraft 4 Reload build that has the API of `thaumcraft_ref`.
  To publish the next version, commit and push its `mod_version`, then run `git tag v<mod_version>` and `git push origin v<mod_version>`.
  **Actions → Release → Run workflow** without a tag checks the complete build without publishing. With a tag, the manual run checks out that
  exact tag; publishing also requires the `publish` checkbox and a commit on `main`. Test reports must contain passing cases with no failures,
  errors or skips. Releases include both production jars, `SHA256SUMS.txt` and `BUILD_INFO.json` with actual source commits and test results.
  An interrupted upload can resume from its draft; published releases are not overwritten. The workflow uses `GITHUB_TOKEN` and caches Gradle.
  Release automation maintained with OpenAI Codex (GPT-6).

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
