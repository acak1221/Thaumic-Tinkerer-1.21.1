# Client verification of the add-on

The server side of the add-on is covered by JUnit tests and by GameTests on a Fabric and a NeoForge dedicated server. None of that loads the
client: block and item models, textures, translations, screens, block-entity renderers and the HUD are not touched by it. The
**client smoke harness** closes that gap as far as the development machine allows: it starts a real Minecraft 1.21.1 client with
Thaumcraft 4 Reload and the add-on, under Xvfb with Mesa software rendering, looks at the resources, builds a world, takes pictures
and writes a report. The same harness runs on **both loaders**, Fabric and NeoForge (see "Wiring" for how it is split, and
"Results on each loader"). It is a development tool: it is never part of a release jar (see "Wiring").

What it produces is **evidence of what a software renderer drew and what the game's own lookups answered**. It is not an
acceptance by a person looking at the game on a GPU; the list of what it cannot show is at the end and is part of the document.

The same harness also has a **multiplayer mode**: real clients that join a real dedicated server, play scripted scenarios through their
real connection and are checked against the server (`MULTIPLAYER_VERIFICATION.md`, `tools/multiplayer-smoke.sh`).

## Running it

```bash
# Gradle is started as ./gradlew in the project root; GRADLE_CMD replaces that command (below)
tools/client-smoke.sh                        # Fabric (the default), whole run, about 2 minutes after the first compile
tools/client-smoke.sh --neoforge             # the same harness on NeoForge, about 2 minutes as well
tools/client-smoke.sh -l /path/gradle.log    # where the Gradle output goes (default $TMPDIR/tt-client-smoke-gradle.log,
                                             #   with --neoforge $TMPDIR/tt-client-smoke-neoforge-gradle.log)
tools/client-smoke.sh -t 600                 # watchdog limit in seconds (default 1500)
tools/client-smoke.sh --neoforge -- -PwithEmi -PwithJade   # anything after -- goes to Gradle (the optional mods, below)
```

`--fabric` (the default, and what the script did before it had the switch) and `--neoforge` may stand anywhere before `--`:

| | Fabric (`--fabric`, default) | NeoForge (`--neoforge`) |
|---|---|---|
| Gradle task | `runTinkererClientSmoke` (root project, `fabric.gradle`) | `:neoforge:runClientSmoke` (`neoforge/build.gradle`) |
| reset step it depends on | `prepareTinkererClientSmoke` | `:neoforge:prepareClientSmoke` |
| results | `build/tinkerer-client-smoke/` | `neoforge/build/tinkerer-client-smoke/` |
| run directory | `run-tinkerer-clientsmoke/` | `neoforge/run-clientsmoke/` |

The script runs that task with `./gradlew`, inside `xvfb-run -a -s "-screen 0 1280x720x24"` with `LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe`. The environment variable
`GRADLE_CMD` replaces `./gradlew`: a command line that is given the Gradle arguments last; its output is tee'd to the log, unless it contains the word
`{LOG}`, which is replaced by the log path (for a wrapper that writes the log itself). In the machine the harness was written in, Gradle builds are serialized by a
wrapper script that takes a shared lock itself and retries failed downloads (it is not part of this repository; do not wrap the script in `flock`, and expect it
to hold the lock for the whole run). The runs recorded in this document were started that way, `GRADLE_CMD='<that wrapper> {LOG}' tools/client-smoke.sh`
(a wrapper that has to be told the project directory is given it in the same string). It needs the Minecraft assets in the
Loom cache (`downloadAssets`, run by the task), `xvfb-run` and the Mesa software drivers; there is no GPU, no window manager and no audio device
in the environment it was written for.

The run directory (git-ignored; its `options.txt` is rewritten every run, the same on both loaders: English, GUI scale 2,
view distance 8, no sound) is in the table above. The world is made by the harness itself and deleted first: nothing depends on a prepared save.
Both runs pass the harness the same system properties: `-Dthaumictinkerer.clientSmoke=true` (without it the harness mod does nothing),
`.outDir`, `.sourceRoot` (the repository root), `.timeoutSeconds`, and a 1280x720 window.

**Exit code.** The script exits 0 only when the harness itself reached its last phase (`harness.status` is `COMPLETED` in `report.json`; the Gradle task checks it
and fails otherwise). What the checks found is in `report.json`, never in the exit code: a run that found thirty defects exits 0. A hang ends with the watchdog
(`TIMED_OUT`, partial report, `Minecraft.stop()`, and a JVM halt with exit code 3 if the game is still running ten seconds later); a crash or a kill leaves `ABORTED`
(shutdown hook) and the crash report in `crash-reports/`. In all three cases the task fails and the script exits non-zero.

## Output

Everything is in `build/tinkerer-client-smoke/` (Fabric) or `neoforge/build/tinkerer-client-smoke/` (NeoForge), deleted at the start of every run of
that loader:

| File | What it is |
|---|---|
| `report.json` | the result: see below |
| `screenshots/*.png` | the pictures, 1280x720; `screenshots/INDEX.md` lists them with one sentence each |
| `client-log-excerpt.txt` | every WARN/ERROR/FATAL line of the client log that names the add-on, a missing model/texture/translation or an exception, with the stack lines that follow; classified `addon`, `resource`, `exception` or `environment` |
| `gradle.log`, `client-latest.log`, `crash-reports/` | copies made by the script, for the case that something went wrong |

### report.json

* `harness`: `status` (`RUNNING`, `COMPLETED`, `TIMED_OUT`, `ABORTED`), `loader` (`fabric` or `neoforge`), start and end time.
* `summary`: the number of checks per result and `failures_by_group` (content group, then defect kind, then count).
* `checks[]`: one entry per result: `phase`, `name`, `group`, `target`, `result`, `detail`.
  * `result` is `PASS`, `FAIL` (a defect of the port or of a resource it ships), `HARNESS_ERROR` (the check or the harness threw or timed out: suspect the
    harness first, the detail has the stack), `NOT_RUN` (skipped on purpose, the detail says why) or `INFO` (a number or a fact; never a failure).
  * `name` is the kind of defect for a `FAIL`: `missing_model`, `missing_blockstate`, `missing_texture`, `missing_translation` (en_us: the player
    sees the raw key), `missing_translation_ru_ru` (the game falls back to the English text), `screen_missing`, `screen_not_opened`, `screen_exception`,
    `renderer_missing`, `renderer_exception`, `item_render_exception`, `tooltip_exception`, `missing_sound_definition`, `missing_sound`, ...
  * `group` is the content group (`SimpleBlocks`, `TileBlocks`, `Machines`, `BasicItems`, `Foci`, `Enchantments`, `KamiGear`, `KamiWorld`) of the registry path in
    `target`, or `Research`, `Core`, `ForeignInTab`, `Unassigned`. The group of a path is decided by `GroupIndex`: the path constants that the registrar
    `content/<Group>.java` passes to `TTRegistry`, and for a computed path (`infused_grain_aer`, `mob_aspect_*`) the group registered just before it;
    `facts.group_assignment` says which rule gave which group.
  * `PASS` lines exist for the things that were looked at and found right; the checks that found nothing wrong still say what they looked at.
* `inventory`: the lists of what was looked at: `blocks`, `items` (as `Group:path`), `block_entity_types`, `block_entity_types_without_renderer`,
  `renderer_classes_in_sources`, `menus`, `enchantments_from_data`, `research_keys`.
* `facts`: `group_assignment`, `tooltips` (the tooltip lines of every stack), `item_grid_cells`.
* `screenshots[]`: `name`, `group`, `shows`, the screen class, and a colour measure (`blank` is true when 99.5 % of the pixels have one colour: nothing was drawn).
* `suspicious_log_lines`: the same entries as `client-log-excerpt.txt`.

## What it checks

The phases run in this order. Each check is its own small class and is isolated: if one throws, it is recorded as `HARNESS_ERROR` and the next one runs.

**Resources (title screen, no world)**

| Check | Looks at |
|---|---|
| `CanaryCheck` | that the checks can fail: an unknown model must come back as the missing model, an unknown sprite as missingno, an unknown key as not translated |
| `BlockModelsCheck` | every state of every block: blockstate file present; baked model (`BlockModelShaper`) is not the missing model; particle sprite is not the missing-texture sprite |
| `ItemModelsCheck` | every item: the model `ItemRenderer.getModel` picks for the default stack is not the missing model, its particle sprite is real |
| `OverridesCheck` | the item-model overrides: every predicate an item model names has a property function registered for the item, and each state of `ItemVariants` (a Kami tool in mode 1 and 2, an enabled talisman, an active Black Hole ring, an activated blood sword) makes the item renderer pick another model than the default stack's; the six infused seeds (one per primal aspect) must each get a model of their own (the first has the default stack's model: a seed without an aspect reads the predicate as 0) |
| `ModelTexturesCheck` | the JSON of every model reached from a blockstate, an item or an override, parents followed: parent files exist, every declared texture has a file and is in the block atlas, every `#variable` a face uses is defined; unreferenced model files; the atlas sources of `atlases/blocks.json` |
| `TranslationsCheck`, `SourceKeysCheck`, `ResearchTextCheck` | the keys of item and block names (the key the game asks for), mob effects, entities, the creative tab, the enchantment descriptions of the data files, every `ttmisc.*`-style key that a source file names as a literal (the files of `common/src/main/java` and of the running loader's glue, `fabric/src/main/java` or `neoforge/src/main/java`), the research tab, `tc.research_name/text.<KEY>` and the page texts of every research the add-on registered, and the research icon and image files: in en_us through `Language.has`, in ru_ru by comparing the language files |
| `ArmorAndEffectTexturesCheck` | the worn-armor layer each armor item draws (outer, inner for leggings) and the status-effect icon of each mob effect: neither is in any model |
| `SoundsCheck`, `ParticlesCheck` | `sounds.json` and the registered sound events, particle definitions; "nothing to check" is stated when the add-on has none |
| `RenderersCheck` | the renderer the game picks for a block entity of every type; every `RenderTile*` class of the sources must be the renderer of some type (a class whose javadoc says 1.7.10 never registered it is reported as a known omission) |
| `ScreensCheck` | every menu type has a screen factory |
| `GuiTexturesCheck` | every `LibResources` path exists |

**World** (a fresh flat creative world made by `WorldPhase`: seed fixed, no structures, peaceful, noon, no weather, no mobs, no fire or random ticks)

| Phase | Does |
|---|---|
| `BlockRowsPhase` | every block, in rows by group, six cells per picture; each cell one representative state (the default and every other value of every property; a property with more than three values gives its first, middle and last), a name tag over each cell; the interface is hidden. Each cell is compared with the empty stage seen by the same camera (`Visibility`): a block that draws nothing is recorded as `block_not_visible` (an `INFO`: for a gas or an empty camouflage that is intended, for anything else it is a defect) |
| `ItemGridPhase` | every item on an inventory-like screen (`ItemGridScreen`: `renderItem` and the item decorations at twice the size, path and translated name under each icon), pages by group; the stacks are the ones of the add-on's creative tab, the items the tab omits and a Mob Display for every mob. Every page is drawn once into a throw-away `GuiGraphics` first, so that an item whose renderer throws is named instead of ending the game. The tooltip of every stack is built and searched for keys without text |
| `MenusPhase` | every menu opened for real: the server-side player right-clicks the block (or uses the item) that opens it, the screen has to be a container screen of that menu type, drawing it once as a guard must not throw, nothing suspicious may reach the log (`LogWatcher`); then a picture. The way each menu is opened is one table in the class; a menu type it does not know is `NOT_RUN`. The warp gate's destinations screen is opened by sneaking above the gate |
| `RendererPhase` | a close-up of every block entity that has a renderer, in a state that makes it draw something (an item in the tablet, the funnel and the repairer, a wand in the enchanter, camouflage); the renderer is called once on its own first, so an exception names the block |
| `CreativeTabPhase` | the creative inventory with the add-on's tab selected (the tab's title is where a missing `itemGroup` key shows). Each loader pages the tabs its own way, so the loader selects it (`SmokePlatform.selectCreativeTab`: Fabric API's `FabricCreativeInventoryScreen`; on NeoForge the page that holds the tab, then the screen's private `selectTab`) |
| `HudPhase` | the Dislocation readout (a wand whose focus carries a chest) and the soul hearts; the tool-mode tooltip is `NOT_RUN` |
| `LanguagePhase`, then `ItemGridPhase` and `MenusPhase` again | the game is switched to ru_ru through the `LanguageManager` and the resources are reloaded; the item pages and the menus are taken again (`-ru_ru` pictures). A name that is still English there is a gap in the translation |

Last, `FinishPhase` reads the client log, writes `client-log-excerpt.txt`, `report.json` and `screenshots/INDEX.md`, and ends the game with `Minecraft.stop()`.

How a picture is taken: `Screenshot.grab` reads the window's frame buffer from the client tick, which runs between two frames, so the buffer holds the last complete
frame. A step waits for the chunk sections to be built and for a number of rendered frames (`Frames`; the software renderer draws far fewer frames than the tick
runs) before it asks for the picture.

### The optional mods (phase `integrations`)

A normal run has neither EMI, JEI nor Jade and records that as `INFO`. `tools/client-smoke.sh -- -PwithEmi -PwithJade` and
`tools/client-smoke.sh -- -PwithJei` (EMI and JEI are run separately; with `--neoforge` before `--` on NeoForge) put the mods into the client
(`modLocalRuntime` in `build.gradle`, and in `neoforge/build.gradle` for the NeoForge builds of the same versions), and the
phase then checks, with numbers taken from the add-on's data and not from its plugins (`ViewerExpectations`):

| Probe | What it compares |
|---|---|
| `EmiProbe` | waits until EMI's recipe manager has Thaumcraft's arcane category filled and its size is steady; the recipes in `thaumictinkerer:necromancy` (one per `EnumMobAspect`) and `thaumictinkerer:infused_seed_drops` (one per aspect with loot); the workstations of the necromancy category; every arcane, crucible and infusion recipe the add-on registered through `ThaumcraftApi` with an item result must be among EMI's recipes for that output in Thaumcraft's category of its kind; pictures of the first page of both categories |
| `JeiProbe` (+ `HarnessJeiPlugin`, the harness's own JEI plugin that keeps JEI's runtime: the `jei_mod_plugin` entrypoint of the Fabric harness mod, found by its `@JeiPlugin` annotation on NeoForge) | the same counts through `IRecipeManager`, the catalysts, the add-on's recipe outputs in Thaumcraft's JEI categories (a focus on the output), pictures |
| `JadeProbe` (+ `HarnessJadePlugin`, a Jade plugin that receives every tooltip Jade collects: the `jade` entrypoint on Fabric, `@WailaPlugin` on NeoForge) | six blocks (Animation Tablet, Transvector Interface, Thaumic Restorer, Celestial Gateway, both attractors) in a known state, the crosshair on the middle of the block's outline, the HUD on; the text of Jade's tooltip must contain the lines the state calls for, translated by the harness from the `ttwaila.*` keys; a picture each |

The numbers of the last runs on both loaders are under "Results on each loader". The first Fabric run with Jade failed both attractors: the harness aimed
at the middle of the block and the attractors' outline is a plate two pixels high under their tall model (as in 1.7.10), so the crosshair hit the floor; the
probe now aims at the middle of the outline. EMI in development mode also listed the add-on's item tags as untranslated; they have names now
(`lang-extra/tags.*.json`). The "47 Warnings" EMI shows in development mode on both loaders are Thaumcraft's own untranslated tags
(`thaumcraft:aspect_family/*`), none of the add-on's.

## Results on each loader

Runs of 2026-10-31, all after the harness was split into the shared tree and the two loader sides, each started as
`GRADLE_CMD='<wrapper> {LOG}' tools/client-smoke.sh [--neoforge] [-- -PwithEmi -PwithJade | -- -PwithJei]` and each `COMPLETED`:

| Loader | Optional mods | PASS | FAIL | HARNESS_ERROR | NOT_RUN | INFO | pictures |
|---|---|---|---|---|---|---|---|
| Fabric | none | 728 | 0 | 0 | 1 | 54 | 80 |
| Fabric | EMI, Jade | 738 | 0 | 0 | 1 | 53 | 88 |
| Fabric | JEI | 732 | 0 | 0 | 1 | 53 | 82 |
| NeoForge | none | 730 | 0 | 0 | 1 | 52 | 80 |
| NeoForge | EMI, Jade | 739 | 0 | 0 | 1 | 52 | 88 |
| NeoForge | JEI | 735 | 0 | 0 | 1 | 50 | 82 |

After the multiplayer mode was added (2026-11-01; `ClientSmoke` picks the mode, `SmokeRun` takes its phases as a list): Fabric 727 PASS, NeoForge 730 PASS,
0 FAIL, 0 HARNESS_ERROR, 1 NOT_RUN, 80 pictures each (the fire blocks' `up=true` states account for the one PASS fewer, see below).

After the modernization of packages C and D (2026-11-01, branch `modernize-c`; the config handshake now also runs over the integrated server's
memory connection before the world loads): Fabric 714 PASS, 52 INFO; NeoForge 719 PASS, 47 INFO; 0 FAIL, 0 HARNESS_ERROR, 1 NOT_RUN, 80 pictures
each. Compared check by check with the runs before (Fabric 726, NeoForge 730 PASS): twelve `resource_path` PASS and four `unused_resource_constant`
INFO fewer, the checks of the `LibResources` constants that were deleted as unused (the four Ichor armour paths named textures that do not exist),
and one fire block's `up=true` state; nothing else changed.

The `NOT_RUN` is the Kami tool-mode tooltip (`HudPhase`) on every run. The last Fabric run before the split had 726 PASS, 0 FAIL, 1 NOT_RUN, 56 INFO, 80 pictures;
the split changed no check: its run without the optional mods (made twice, the second after the whole test suite at the end of the work, both with the
numbers above) differs from that one only in `fire_order up=true` and `fire_chaos up=true`, both drawn this time. The Fabric runs with the optional mods
are the first after the JEI and Jade plugins of the harness moved package (their `fabric.mod.json` entrypoints were renamed with them).

**What differs between the loaders.** The reports were compared check by check (phase, name, target, result), the pictures pixel by pixel:

* **Gaseous Tenebrae** (`gaseous_shadow`, three states): `block_not_visible` (`INFO`, 0.0 % of its cell differs from the empty stage) on Fabric,
  `block_visible` (17.5 % and 22.4 %) on NeoForge, in every pair of runs. The gas itself draws nothing on either loader (render shape invisible); on NeoForge the
  floor tile under it is darker. That darkness is the block's purpose (`getLightBlock` 15, "invisible darkness"), and the difference is the renderer: NeoForge draws
  terrain with vanilla's smooth lighting, whose mean of the four light samples of a vertex keeps the 0 of the gas cell; Fabric API's Indigo renderer
  (`fix-mean-light-calculation`, on by default) replaces a 0 sample by the smallest non-zero one, so a single cell of the gas does not darken the face below it.
  Not NeoForge glue and not a defect of the port; nothing was changed. A whole cloud (every sample 0) is dark on both.
* **The three fire blocks' `up=true` states** (`fire_order`, `fire_chaos`, `fire_water`) are drawn in some runs and not in others, on both loaders (one flame
  frame on a cell edge); they account for every other difference of the counts.
* **Facts:** the add-on's resource pack is `mod/thaumictinkerer` on NeoForge (`thaumictinkerer` on Fabric); `SourceKeysCheck` reads 306 source files on NeoForge
  and 309 on Fabric (the same 64 literal keys and 6 prefixes: the loader glue differs); the development player is `Dev` on NeoForge.
* **Pictures** (NeoForge against the Fabric run of the same mods): 31 to 35 of each set are pixel-identical, 14 to 21 differ in fewer than 0.1 % of the pixels. The
  rest is what moves with time: the particles (fire smoke, warp gate, Dislocation focus), the tool the Dynamism Tablet swings, the enchantment glint of the infused mob
  aspects, the rainbow of the Experience Drain focus, the camera of `renderer-camo.png` (it differs as much between two Fabric runs); plus the darker floor under the
  Gaseous Tenebrae, the creative inventory's page bar (NeoForge draws its "2 / 2" bar above the panel, Fabric its arrows beside the tab title; the tab, its title and
  its items are the same) and the state of JEI's own page buttons. EMI's two pages are pixel-identical on both loaders.
* **Client log:** on NeoForge the excerpt holds only environment entries (no sound device, the profile lookup); no warning or error of the add-on.

**Seen on NeoForge for the first time** (the NeoForge client had only been started to its first screen before): every block in a world, every item
(including the Mob Display of every mob, drawn through the NeoForge item renderer backend `NeoForgeItemRenderers`; its picture `items-machines-02.png` equals
Fabric's in all but 0.01 % of the pixels), the seven menus and the warp gate's destinations screen, twelve block-entity renderer close-ups, the HUD, the creative tab, the
Russian pass, EMI's and JEI's pages and the six Jade tooltips. **NeoForge defects the harness found: none.** What it took to run there was harness work only:
the loader side, the second log format, the creative-tab paging and the annotations of the harness's own JEI and Jade plugins (see "Wiring").

## Wiring

The harness is one loader-neutral tree plus a small side per loader, laid out like the GameTests (`common/src/gameTest` + `fabric/src/gameTest` +
`neoforge/src/gameTest`):

| Tree | Package | What is in it |
|---|---|---|
| `common/src/clientSmoke/java` | `thaumic.tinkerer.clientsmoke` | the whole harness: phases, checks, report, watchdog, the EMI/JEI/Jade probes and the harness's JEI and Jade plugins; `ClientSmoke.arm(SmokePlatform)` starts a run; `SmokePlatform` is everything it asks of the loader |
| `fabric/src/clientSmoke` | `thaumic.tinkerer.fabric.clientsmoke` | `TinkererClientSmoke` (the `client` entrypoint), `FabricSmokePlatform`; `resources/fabric.mod.json` |
| `neoforge/src/clientSmoke` | `thaumic.tinkerer.neoforge.clientsmoke` | `TinkererClientSmokeNeoForge` (`@Mod(value = "thaumictinkerer_clientsmoke", dist = Dist.CLIENT)`), `NeoForgeSmokePlatform`; `resources/META-INF/neoforge.mods.toml` |

`SmokePlatform` has eight methods, the only loader calls of the harness:

| Method | Fabric | NeoForge |
|---|---|---|
| `onEndClientTick` (drives the phases) | `ClientTickEvents.END_CLIENT_TICK` | `ClientTickEvent.Post` (game bus) |
| `onLevelRendered` (the world frame counter of `Frames`) | `WorldRenderEvents.END` | `RenderLevelStageEvent` at `Stage.AFTER_LEVEL` |
| `onScreenRendered` (the screen frame counter) | `ScreenEvents.afterRender`, registered per screen after its first init | `ScreenEvent.Render.Post` |
| `selectCreativeTab` | `FabricCreativeInventoryScreen.setSelectedItemGroup` | the `CreativeTabsScreenPage` that holds the tab (`setCurrentPage`), then `selectTab`; the page list and `selectTab` are private, read by reflection |
| `gameDir` (where `logs/latest.log` is) | `FabricLoader.getGameDir()` | `FMLPaths.GAMEDIR` |
| `isModLoaded` (EMI, JEI, Jade) | `FabricLoader.isModLoaded` | `ModList.isLoaded` |
| `findModPath` (the enchantment data files of the add-on) | `ModContainer.findPath` | `IModFile.findResource` of the mod's file |
| `loader` | `fabric` | `neoforge` (in `report.json`, the picture index, and the loader glue `SourceKeysCheck` reads) |

Why a new package rather than keeping `thaumic.tinkerer.fabric.clientsmoke` and adding the old directory to the NeoForge source set: the harness classes are
package-private and see each other without imports, so the move changed one `package` line per file and nothing else, and a NeoForge harness in a package named
`fabric` (compiled from `fabric/src`) would have been misleading. The loader sides are in packages of their own, as in the GameTest trees; that is why
`ClientSmoke`, `SmokePlatform` and the two viewer plugins are public. The client log is read in both formats, `[12:34:56] [thread/LEVEL]` (Fabric) and
`[31Oct2026 12:34:56.789] [thread/LEVEL] [logger/]` (NeoForge after its first launcher lines).

* Fabric: source set `thaumicTinkererClientSmoke` (`fabric.gradle`): Java from `common/src/clientSmoke/java` and `fabric/src/clientSmoke/java`, resources from
  `fabric/src/clientSmoke/resources` (`fabric.mod.json`: mod id `thaumictinkerer_clientsmoke`, client only, depends on `thaumictinkerer`; entrypoints `client`, `jade`,
  `jei_mod_plugin`). Run `tinkererClientSmoke` (`./gradlew runTinkererClientSmoke`): a client with a 1280x720 window, run directory `run-tinkerer-clientsmoke`, the add-on
  and the harness as two classpath groups (like the GameTest run). `prepareTinkererClientSmoke` resets the options and the result directory first.
* NeoForge: source set `clientSmoke` (`neoforge/build.gradle`): Java from `common/src/clientSmoke/java` and `neoforge/src/clientSmoke/java`, resources from
  `neoforge/src/clientSmoke/resources` (`neoforge.mods.toml`: mod id `thaumictinkerer_clientsmoke`, depends on `thaumictinkerer`, `thaumcraft`, `architectury`, `neoforge`,
  `minecraft`, side `CLIENT`). Run `clientSmoke` (`./gradlew :neoforge:runClientSmoke`): a client with a 1280x720 window, run directory `neoforge/run-clientsmoke`, the
  add-on and the harness as two mods of the run (`mods { thaumictinkerer { sourceSet main }; thaumictinkerer_clientsmoke { sourceSet clientSmoke } }`, like the NeoForge
  GameTest run). `:neoforge:prepareClientSmoke` resets the options and the result directory first.
* Both: the watchdog limit is the Gradle property `tinkererClientSmokeTimeout` (seconds; the script's `-t`). `check` compiles both source sets; no jar task includes
  either (checked: `build/libs/*.jar` and `neoforge/build/libs/*.jar` hold no `clientsmoke` entry and only the add-on's mod id). The task fails unless `report.json`
  says `COMPLETED`.
* The shared tree imports Minecraft, Architectury, the Thaumcraft API, the add-on and the APIs of EMI, JEI and Jade only (the probes are loaded only when their mod
  is); the loader sides import their loader as well.

## Extending it

* A new check of the resource phase: a class implementing `SmokeCheck`, added to the list in `ResourcesPhase`.
* A new menu: one line in `MenusPhase.HOW`. A new block entity that needs a state for its renderer: one line in `RendererPhase.SETUPS`.
* A new item-model override: its predicate is looked up without help; to see it switch, one line in `ItemVariants` (the add-on's own setter or component for that state).
* A new block, item, menu, block entity or enchantment needs nothing: they are read from the registries. A new group needs its name in `GroupIndex.GROUPS`.

## What it cannot show

* **No human.** A `PASS` says a lookup answered or an exception did not happen; it does not say the thing looks right. The pictures are for a person (or a model) to read afterwards,
  and the visibility measure is a number, not a judgement.
* **No real GPU.** Mesa llvmpipe renders everything: shader, blending, mipmap, anisotropy, translucency sorting and animated-texture timing can differ from a GPU, and a
  driver-specific bug cannot appear. The frames are slow, so nothing about frame-rate or time-based motion is learned.
* **No sound output.** OpenAL fails to open a device; sounds are checked only as files (`sounds.json`, `.ogg`), never heard.
* **No input devices.** Nothing is clicked, dragged, typed or scrolled: screens are opened and drawn, their buttons are not pressed, slots are not clicked, keys are not
  bound and pressed (the sneak key is set programmatically to open the warp gate screen). The scroll, drag and click handling of the screens is untested here; the
  multiplayer check (`MULTIPLAYER_VERIFICATION.md`) presses the use, attack, sneak and hotbar keys through the game's own key handling and clicks slots and two screen
  buttons (the Enchanter's, the Animation Tablet's) with synthetic mouse clicks, still without a mouse or a keyboard.
* **No multiplayer.** One integrated server, one client in the same process; packets are real but in-memory. A dedicated server, a second client and a desync
  are the subject of the **multiplayer check** (`MULTIPLAYER_VERIFICATION.md`), which runs this same harness in its multiplayer mode against a real dedicated server.
* **One setting.** 1280x720, GUI scale 2, view distance 8, English (the Russian pictures are a repeat after a language switch), one flat peaceful world at noon, the default
  field of view. Other GUI scales, resolutions, languages (the de/fr/ko/zh files are not compared), resource packs and other mods are not seen; EMI, JEI and Jade are
  seen only in their own runs (above), never EMI and JEI together, and their hover tooltips are not (no input).
* **Default states only.** Items are shown as the default stack of the creative tab (damaged and stacked copies for the decorations); the model overrides are resolved at their default
  values plus the states of `ItemVariants`; a branch that is not in that list is checked only as a file and by its predicate name. Blocks are shown in representative states, the
  block entities in the few states of `RendererPhase`. Animated textures are seen in one frame.
* **Not visited.** The Bedrock dimension and its portal in use, the Thaumonomicon pages of the add-on's research (only their keys are checked), entities (none are registered), the
  wings layer and the Placement Mirror's ghost blocks (loader glue on both loaders that needs the items in use), the Kami tool-mode tooltip, chat messages, the commands, particles over time.
* **Each loader with its own renderer.** Fabric renders terrain through Fabric API's Indigo, NeoForge through vanilla's block renderer (its
  `experimentalForgeLightPipelineEnabled` is off). Their smooth lighting differs where a light value is 0 (below, Gaseous Tenebrae), so a picture can differ
  between the loaders without a defect on either; a difference in the counts has to be read against the pictures.
