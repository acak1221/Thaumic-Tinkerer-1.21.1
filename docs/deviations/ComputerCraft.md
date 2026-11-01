# ComputerCraft (CC: Tweaked): deviations from Thaumic Tinkerer 1.7.10

The 1.7.10 mod made blocks of its own and of Thaumcraft into ComputerCraft peripherals (`common/peripheral/PeripheralHandler.java`,
an `IPeripheralProvider`, and the tile entities that were `IPeripheral`s themselves), gave the same blocks to OpenComputers
(`common/peripheral/OpenComputers/*`), added a turtle tool (`common/compat/FumeTool.java`) and documented the methods on a web page
behind the research `PERIPHERALS` (`client/gui/GuiResearchPeripheral.java`). The port offers the same peripherals and the turtle
tool to **CC: Tweaked 1.116.1** (Minecraft 1.21.1), on **Fabric and NeoForge**, as an optional dependency: the add-on compiles
against CC: Tweaked's API only, does not bundle it, and loads and works without it. OpenComputers has no 1.21.1 release and is
dropped (`docs/API_GAPS.md` section 1.12, row 1). Specification: the 1.7.10 sources (commit in `NOTICE.md`).

Reading key: **forced** means 1.21.1, CC: Tweaked or `thaumcraft.api` cannot do it the old way; **fix** means the original was
wrong; **choice** means a free decision of the port.

## 1. The peripherals

The Lua type and the method names are those of 1.7.10 (`getType()`, `getMethodNames()`), in the same order; so are the arguments
and the shape of the answers (a boolean, a number, a string, a list as a table with the keys 1, 2, 3 ..., several values), except
where section 3 says otherwise. Which block entity gets which peripheral is decided in the order of `PeripheralHandler.getPeripheral`:
an aspect container first, then the Thaumcraft machines, then an essentia transport, then the add-on's own devices.

| Block (1.21.1) | Type | Methods (1.7.10 and port) | Port |
|---|---|---|---|
| Every `IAspectContainer` of any mod: Thaumcraft's jars, nodes, node jars, alembics, centrifuge, Thaumatorium, essentia crystallizer, essentia buffer and filtered tube, wand pedestal, mana pod ...; the add-on's Essentia Funnel, Thaumic Restorer, Infused Farmland and Grain, **Transvector Interface** | `tt_aspectContainer` | `getAspects`, `getAspectCount` | done (`AspectContainerTable`) |
| Every `IEssentiaTransport` that is no aspect container: essentia tubes and valves, the Arcane Bore's base, the arcane furnace nozzle ... | `tt_aspectTransport` | `isConnectable`, `canInputFrom`, `canOutputTo`, `getSuctionType`, `getSuctionAmount`, `getEssentiaType`, `getEssentiaAmount`, `getMinimumSuction` | done (`EssentiaTransportTable`) |
| Dynamism Tablet | `tt_animationTablet` | `getRedstone`, `setRedstone`, `getLeftClick`, `setLeftClick`, `getRotation`, `setRotation`, `hasItem`, `trigger` | done (`AnimationTabletTable`) |
| Kinetic Attractor | `tt_magnet` | `isPulling`, `setPulling`, `getSignal` | done (`MagnetTable`) |
| Corporeal Attractor | `tt_magnet` | `isPulling`, `setPulling`, `getSignal`, `getAdultSearch`, `setAdultSearch` | done (`MagnetTable`) |
| Aspectalyzer | `tt_aspectanalyzer` | `hasItem`, `itemHasAspects`, `getAspects`, `getAspectCount` | done (`AspectAnalyzerTable`) |
| Golem Connector | `tt_golemconnector` | `getDecorations`, `getPosition`, `getType`, `getHealth`, `getCore`, `getHome`, `setHome`, `getMarkers`, `setMarkers`, `newMarker`, `addMarker`, `saveMarker`, `deleteMarker`, `getMarker`, `getMarkerCount` | done (`GolemConnectorTable` over `GolemConnectorMethods`; the golem through `thaumcraft.api.golems`, A11: `ApiGolemAccess`) |
| Deconstruction Table | `tt_deconstructor` | `hasAspect`, `hasItem`, `getAspect` | done (`DeconstructorTable`, `thaumcraft.api.machines.IDeconstructor`, A12) |
| Brain in a Jar | `tt_braininajar` | `getXP` | done (`BrainInAJarTable`, `IBrainJar`) |
| Arcane Ear | `tt_arcaneear` | `getNote`, `setNote` | done (`ArcaneEarTable`, `IArcaneEar`) |
| Arcane Bore | `tt_arcanebore` | `hasPickaxe`, `hasFocus`, `isPickaxeBroken`, `isWorking`, `getRadius`, `getSpeed`, `hasNativeClusters`, `getFortune`, `hasSilkTouch` | done (`ArcaneBoreTable`, `IArcaneBore`) |

The turtle upgrade: the Fume Dissipator as a turtle tool, "Dissipating Turtle" (section 4).

## 2. How the port is built

- `compat/src/main/java/thaumic/tinkerer/compat/computer/` names no class of CC: Tweaked: `PeripheralTable` (type, names, `call(index,
  arguments)`, target), the tables of section 1 (ports of `PeripheralAspectContainer` with `IAspectContainerImplementation`,
  `PeripheralEssentiaTransport`, `PeripheralDeconstructor`, `PeripheralBrainInAJar`, `PeripheralArcaneEar`, `PeripheralArcaneBore`, and
  of the `IPeripheral` parts of `TileAnimationTablet`, `TileMagnet`, `TileMobMagnet`, `TileAspectAnalyzer`, `TileGolemConnector`),
  `TinkererPeripherals.find` (the port of `PeripheralHandler.getPeripheral`; the Thaumcraft machines are found by their interfaces of
  `thaumcraft.api.machines`, where 1.7.10 named the internal tile entity classes),
  `PeripheralArgs` and `PeripheralError` (the arguments and the Lua error), `Computers.ccTweakedLoaded()`. The behaviour stays on the
  block entities (`TileAnimationTablet.trigger()`, `TileMagnet.setPulling`, `TileAspectAnalyzer.getAspectsMethod` ...) and in the
  Thaumcraft API; the Golem Connector's golem is read and changed through `thaumcraft.api.golems` (`common/.../core/golems/ApiGolemAccess`).
  Both APIs are in Thaumcraft 4 Reload since `316897d`; the add-on is pinned to `c1d8024`, which contains it (`thaumcraft_ref`).
- `compat/src/main/java/thaumic/tinkerer/compat/computer/cc/` is the only package that imports CC: Tweaked's API, with the loader glue:
  `TablePeripheral` (an `IDynamicPeripheral`: a type, the method names and `callMethod` by index, the shape of the 1.7.10
  `IPeripheral`), `TinkererPeripheralProvider`, `FumeTool` (the turtle upgrade), `ComputerCraftCompat` (the upgrade type).
- Fabric: `fabric/.../fabric/compat/ComputerCraftFabric` registers the provider as a fallback of `PeripheralLookup` (asked for every
  block, as 1.7.10's `IPeripheralProvider` was asked for every tile entity) and the upgrade type in CC: Tweaked's registry of turtle
  upgrade types; `fabric/.../fabric/client/ComputerCraftFabricClient` registers its model. NeoForge: `neoforge/.../neoforge/compat/
  ComputerCraftNeoForge` registers the provider for `PeripheralCapability` on every block entity type (NeoForge asks block capabilities
  per type) and the upgrade type by a `DeferredRegister`; `neoforge/.../neoforge/client/ComputerCraftNeoForgeClient` the model, on
  `RegisterTurtleModellersEvent`. The entrypoints call them only behind `Computers.ccTweakedLoaded()`, so without CC: Tweaked no class
  that names it is loaded (`ComputerCraftIsOptionalTest` checks the sources for it).
- The turtle upgrade itself is data: `data/thaumictinkerer/computercraft/turtle_upgrade/gas_remover.json`, read by CC: Tweaked alone.
- Build (`gradle.properties` `cc_tweaked_version`, `build.gradle`, `fabric.gradle`, `neoforge/build.gradle`): the API artifacts
  `cc.tweaked:cc-tweaked-1.21.1-fabric-api` and `-forge-api` (with `-core-api`) are compile-only; the mods `cc-tweaked-1.21.1-fabric` and
  `-forge` are on the GameTest runtimes of both loaders only (configurations `modThaumicTinkererGameTestRuntimeOnly` and
  `modGameTestRuntimeOnly`), so the development client, the client smoke and the multiplayer check run without CC: Tweaked; `-PwithComputerCraft`
  adds it to the development clients. CC: Tweaked's NeoForge jar nests its Lua VM (Cobalt 0.9.6) and jzlib with JarJar, which the
  development runs of this build do not unpack, so `neoforge/build.gradle` adds the two libraries as `forgeRuntimeLibrary` (every NeoForge
  run of the project has them; without CC: Tweaked they are unused); on Fabric Loom resolves them from the mod's Maven metadata.
  `fabric.mod.json` suggests `computercraft`, `neoforge.mods.toml` lists it as optional.
- **The version (forced).** 1.116.1 (2025-07-12) is the newest CC: Tweaked whose Fabric jars this build's Architectury Loom 1.11 accepts:
  1.116.2 and later were built with Fabric Loom 1.12 to 1.17, and Loom 1.11 refuses such a mod jar ("Mod was built with a newer version of
  Loom (1.17.11), you are using Loom (1.11.458)"). The API classes the add-on uses are the same in 1.120.2, source for source, so a newer
  CC: Tweaked in a game is expected to work; it was not run. The NeoForge build uses the same version.

## 3. Deviations of the peripherals

1. **Calls run on the server thread (forced, for safety).** 1.7.10 ComputerCraft called `callMethod` on the computer thread, where the
   original read and changed tile entities and blocks (`setBlockMetadataWithNotify`). CC: Tweaked requires world access on the main
   thread: `TablePeripheral` copies the arguments and runs the method with `ILuaContext.executeMainThreadTask`, so every call waits for
   the next server tick (the research page says so). The answers are the same.
2. **Wrong arguments are "bad argument" errors (forced).** The original cast (`(Double) arguments[0]`, `(Boolean) arguments[0]`, `(String)
   arguments[0]`), so a missing or wrong argument failed with a Java exception that the script saw as `Java Exception Thrown:
   java.lang.ClassCastException`. The port raises CC: Tweaked's own message, `bad argument #1 (number expected, got nil)` (the Lua type
   of the value as CC: Tweaked names it); the call still fails. For the Golem Connector the counts are checked first, with the original
   messages, and the fields of a marker table (`posX`, `posY`, `posZ`, `side`, `color`: numbers) only when a golem is there, which is when
   1.7.10 read them: `bad field 'posY' (number expected, got nil)`.
3. **A side outside 0 to 5 is an error (forced).** `tt_aspectTransport` took a `ForgeDirection` number; any other number was
   `ForgeDirection.UNKNOWN`, and each Thaumcraft block answered for it as it liked. 1.21.1 has no unknown direction: the port raises
   `bad argument #1 (side 0 to 5 expected, got 6)`. The numbers 0 to 5 are unchanged (down, up, north, south, west, east, the 3D data
   values of `Direction`); a fraction is cut off as `Double.intValue()` did.
4. **No suction type or essentia is an error with a message (choice).** `getSuctionType` and `getEssentiaType` called `getTag()` on
   null for a side without one, a `NullPointerException` the script saw as an error. The port raises `no suction type on that side`
   and `no essentia on that side`; the call fails as before.
5. **A labelled empty jar answers no aspect (forced, API).** `IAspectContainerImplementation.getAspects` answered the label's aspect
   for a jar with a label (`TileJarFillable.aspectFilter`), even an empty one. `thaumcraft.api` has no way to read a jar's label, so
   `getAspects()` of a labelled empty jar is an empty table; a labelled jar with essentia answers its aspect, as before (a label only
   admits its own aspect). Request: `docs/api-requests/ComputerCraft.md`.
6. **The Transvector Interface is an aspect container (kept).** 1.7.10 `TileTransvectorInterface` was an `IPeripheral` that forwarded the
   bound block's peripheral (type "Transvector Interface Unconnected Peripherad" when unbound), but `PeripheralHandler` tried
   `IAspectContainer` first and the Interface is one, so a computer always saw `tt_aspectContainer` with the bound block's aspects; the
   forwarding methods were never reached (and `PeripheralTransvectorInterface`, with `isBound` and the other methods the 1.7.10 wiki
   lists, was commented out). The port answers the same and has no forwarding peripheral.
7. **No blacklist (forced).** Other mods could exclude tile entity classes by the IMC message `ADD_CC_BLACKLIST`; 1.21.1 has no IMC
   (`docs/API_GAPS.md` section 1.12, row 14).
8. **One peripheral per block entity (choice).** The original's `equals` was identity, and its provider made a new peripheral on every
   lookup. Two lookups of the same block entity give equal peripherals in the port (same type, same block entity), so a computer keeps
   its peripheral when the block around it changes; a script sees no difference.
9. **The crucible and the infusion matrix are no peripherals (forced, Thaumcraft).** Both were `IAspectContainer`s in Thaumcraft 4
   for 1.7.10, so `tt_aspectContainer`s; in Thaumcraft 4 Reload they are not (`TileCrucible`, `TileInfusionMatrix` implement no aspect
   interface of the API), and the add-on may not reach into them. Every block entity that Thaumcraft 4 Reload makes an
   `IAspectContainer` or `IEssentiaTransport` is one.
10. **The Arcane Ear takes the notes it can hear (forced, API).** `setNote` stored any number as a byte (`(byte) argument`); the ear
    never heard a note outside 0 to 24, and `IArcaneEar.setNote` refuses one. Such a number is the error
    `bad argument #1 (note 0 to 24 expected, got 25)`; 0 to 24 (a fraction cut off) tune the ear as before, saved and synced, without
    playing the note.
11. **The golem refuses what Thaumcraft refuses (forced, API).** 1.7.10 wrote the golem's fields unchecked. `GolemApi.setHome` and
    `GolemApi.setMarkers` answer false for what the golem cannot take, and the connector then raises a Lua error instead of answering
    the unchanged home or markers: a facing or marker side outside 0 to 5 (`bad argument #4 (side 0 to 5 expected, got 9)`,
    `bad field 'side' (...)`), a colour outside -1 to 15, a `dim` that is no dimension id, more than 64 markers, and the golem's own
    refusals (`The golem refused the markers: a coloured marker needs the Order upgrade, and a golem that retires or moves cargo takes
    none`, `The golem refused the new home`). The home restriction radius is the golem's own 32 (1.7.10 set 35, which lasted until the
    golem was loaded again).
12. **A marker's `dim` is the dimension's id (forced).** 1.7.10 had numeric dimension ids (`0`); a marker table carries the id of the
    dimension (`"minecraft:overworld"`) in `dim` now, and `newMarker()` fills in the connector's.
13. Kept from the groups: `setRotation` of the tablet also refuses a negative number (`docs/deviations/TileBlocks.md` section 2.1); the
   attractors' `setPulling` changes the block only when the polarity changes (`Machines.md`); `getSignal` is the strongest signal of the
   six neighbours, which is what the original's loop over `getIndirectPowerLevelTo` computed; the Aspectalyzer's aspects are those of a
   scan (`ThaumcraftApiHelper.getObjectAspects`, `Machines.md` section 3).

## 4. The turtle upgrade (Fume Dissipator)

- **Data-driven (forced).** 1.7.10 `FumeTool` was registered with `ComputerCraftAPI.registerTurtleUpgrade` under the number 171. In
  CC: Tweaked for 1.21.1 an upgrade is an entry of a data pack registry: the add-on registers the upgrade type `thaumictinkerer:gas_remover`
  (`UpgradeType.simpleWithCustomItem`, `FumeTool`) and ships the data file
  `data/thaumictinkerer/computercraft/turtle_upgrade/gas_remover.json` (`{"type": "thaumictinkerer:gas_remover", "item":
  "thaumictinkerer:gas_remover"}`), so the upgrade is `thaumictinkerer:gas_remover` and a data pack can change its item.
- Unchanged: a tool upgrade; its adjective is the 1.7.10 key `ttcomputer.dissipator` ("Dissipating"); it is crafted onto a turtle
  with the Fume Dissipator (CC: Tweaked's own turtle upgrade recipe); `turtle.dig()` (also `digUp` and `digDown`: the direction is ignored)
  dissolves every gas of the add-on from the turtle's position minus three to plus two on each axis (the loops were `< xs + 3`), with each
  gas's particle, and answers true; `turtle.attack()` fails; no sound (the original's sound call was commented out).
- **The model (forced).** The 1.7.10 icon was the item's texture, `ttinkerer:gasRemover`, from a `TextureStitchEvent`. CC: Tweaked draws
  an upgrade with a `TurtleUpgradeModeller`; the add-on registers `TurtleUpgradeModeller.flatItem()`, the item drawn flat on the
  turtle's side.
- **`FumePeripheral` is not ported (kept).** It was a peripheral of type `gasRemover` without methods, and `FumeTool.createPeripheral`
  answered null, so 1.7.10 never made one; a tool upgrade has no peripheral in CC: Tweaked either.

## 5. The research `PERIPHERALS`

- **The methods are in the book (choice).** 1.7.10 had one page, which sent the reader to a web page by pressing Enter
  (`GuiResearchPeripheral`, swapped in by `ClientTickHandler`; `docs/API_GAPS.md` section 1.12, row 17). The port has no such hook into
  the Thaumonomicon and writes the documentation into the entry: the entry `PERIPHERALS` has nine text pages now,
  `ttresearch.page.PERIPHERALS.0` to `.8`: the introduction (the first sentence of 1.7.10, then how a computer finds the blocks, that a call
  waits for the next tick and that the blocks are ordinary blocks without CC: Tweaked), the Dynamism Tablet, the two Attractors, the
  Aspectalyzer, the Golem Connector, the aspect containers, the essentia transports, Thaumcraft's four machines and the Dissipating
  Turtle. The pages follow the code
  where the 1.7.10 wiki page differed from it (the wiki gave `getAspects` of an aspect container as a list of name and amount tables and
  listed the Interface's commented-out methods).
- Position, icon, flags and the parents of the Aspectalyzer and the Golem Connector are unchanged; the entry is registered always,
  as before (`docs/deviations/Machines.md` section 3, `TileBlocks.md` section 2.4). The parity check compares the pages with the
  original and allows exactly this difference (`known_differences.json`, `PERIPHERALS` `pages.count` 1 to 9). The Golem Connector's
  own pages are the 1.7.10 text again, page 3 now points to this entry (`TileBlocks.md` section 2.4).
- Languages: `lang-extra/computercraft.<lang>.json`. English and Russian have all nine pages; German, French, Korean and Chinese have
  the introduction in their language (it says that the following pages are English) and show the English method pages. The
  introduction replaced the text of the earlier port that said the computer link was not part of this version
  (`lang-extra/basic_items.<lang>.json` no longer carries the key).

## 6. What was verified

- **JUnit** (`common/src/test/java/thaumic/tinkerer/computer/`): `PeripheralTablesTest` (the type and the method names of every
  peripheral against the 1.7.10 sources; the answers of `tt_aspectContainer` and `tt_aspectTransport` for fake Thaumcraft blocks: the
  sorted tags from key 1, the amounts, the side numbers, the errors; the four machine tables against fakes of the API: no aspect, the
  note range, the bore's hole width; the order of `PeripheralHandler`; the marker conversion of `ApiGolemAccess` and its "bad field"
  errors; CC: Tweaked's "bad argument" texts) and `ComputerCraftIsOptionalTest` (only `compat/.../computer/cc` and the four glue classes
  name CC: Tweaked, only the three entrypoints name the glue, each behind `Computers.ccTweakedLoaded()`; the mod files declare
  CC: Tweaked optional; the turtle upgrade's data file). `TinkererApiOnlyTest` allows `dan200.computercraft.api.` and nothing else of
  CC: Tweaked. The parity unit tests and the GameTest assert 12 documented differences (`PERIPHERALS` pages).
- **GameTests with CC: Tweaked 1.116.1 on the GameTest servers of both loaders** (`ComputerCraftRuntimeChecks`, wrappers
  `ComputerCraftGameTests`; on NeoForge generated, the lookups through `ComputerCraftNeoForgeChecks`):
  - `addonPeripheralsAreFoundAtTheirBlocks`, `genericPeripheralsAreFoundAtThaumcraftBlocks`, `thaumcraftMachinesAreFoundAtTheirBlocks`:
    each block (Tablet, both Attractors, Aspectalyzer, Golem Connector, a jar, the Interface, the Essentia Funnel, an essentia tube, the
    bore's base, the Deconstruction Table, the Brain in a Jar, the Arcane Ear, the Arcane Bore) is looked up from all six sides through the
    loader's own lookup (Fabric `PeripheralLookup.get().find`, NeoForge `level.getCapability(PeripheralCapability.get(), ...)`): the type
    and the method names in their order are those of 1.7.10, the target is the block entity, two lookups give equal peripherals; a stone
    block and a chest are none of the add-on's.
  - Real Lua on real computers (`LuaComputer`: a `computer_normal` with the program as `startup.lua`, booted by itself; the program
    calls `peripheral.wrap`, `peripheral.getType`, `peripheral.getMethods` and the methods, and writes its answers as JSON):
    `luaDrivesTheDynamismTablet` (every method, the setters' effects on the block entity and the block, `setRotation` errors, a wrong
    argument type, `trigger` while busy), `luaDrivesTheAttractors` (two computers: polarity, signal 15 from a redstone block, adult search),
    `luaReadsTheAspectalyzer` (a diamond and an empty slot), `luaAsksTheGolemConnectorWithoutAGolem` (the 1.7.10 answers without a golem
    and its error messages), `luaReadsAndSteersAGolem` (a real Thaumcraft golem: every reading against `GolemApi.view`, a new home and
    markers set from Lua and read back through the API, the "bad field" errors), `luaReadsAspectContainers` (a jar with five Ignis, the
    Interface bound to it, a labelled empty jar), `luaReadsEssentiaTransports` (a tube and the bore's base, every method for every side
    against `IEssentiaTransport`), `luaReadsThaumcraftMachines` (five computers: two Deconstruction Tables, a Brain in a Jar with 123
    points, an Arcane Ear tuned from Lua, an Arcane Bore with a worn Fortune III pickaxe, against `thaumcraft.api.machines`).
  - The turtle: `fumeDissipatorIsATurtleUpgrade` (the data file made the upgrade with the add-on's type, a tool with the 1.7.10
    adjective and item; a turtle and a Fume Dissipator in a crafting grid give a turtle with it) and `luaTurtleDissipatesGas` (a real
    turtle with the tool runs `turtle.dig()`: the gases in reach are gone, those one block further stay; `turtle.attack()` fails).
  - `computerCraftIsLoadedAndTheGlueRegistered`: CC: Tweaked is on the server and no registration of the add-on failed.
- Runs (2026-11-01, this worktree, through the shared Gradle wrapper): the full suite `check runTinkererGameTest :neoforge:runGameTest
  assemble :neoforge:assemble --continue`: `BUILD SUCCESSFUL`; JUnit 447 tests, 0 failures, 0 errors (`build/test-results/tinkererTest`);
  Fabric "All 370 required tests passed" (370 `testcase` elements, none failed, the 14 of this group among them, the Lua ones 1.4 to 1.8 s
  each); NeoForge "All 378 required tests passed" (378, none failed, the same 14). The release jars contain the 21 classes of
  `compat/.../computer` and the upgrade's data file and no class of CC: Tweaked or Cobalt. Earlier focused runs found and fixed: the
  NeoForge server crashed without Cobalt (`NoClassDefFoundError: org/squiddev/cobalt/LuaValue`; CC: Tweaked's NeoForge jar nests it with
  JarJar, which this development run does not unpack: now `forgeRuntimeLibrary`); in one Fabric run three of the Lua tests timed out on a
  busy machine with a wait counted in ticks (now in seconds, `LuaComputer.whenAnswered`); the golem test expected `setMarkers` to answer
  nothing (it answers the new markers, as 1.7.10); the TileBlocks binder test still asserted the golem stub of A11 (it checks the golem
  read through the API now).

## 7. What was not verified

- **A client with CC: Tweaked.** The client smoke and the multiplayer check run without CC: Tweaked (on purpose, see section 2), so the
  turtle tool's model (`TurtleUpgradeModeller.flatItem()`, registered on both loaders), the turtle's name ("Dissipating Turtle") and a
  computer's screen were not seen; `-PwithComputerCraft` adds CC: Tweaked to the development clients for a look by hand.
- **A dedicated server with CC: Tweaked and players**, wired and wireless modems, several computers on one peripheral, a peripheral
  that is broken while a call waits for its tick.
- **Other versions of CC: Tweaked** than 1.116.1 (the API the add-on uses is unchanged in 1.120.2, by source; not run).
- The OpenComputers drivers are not ported.
