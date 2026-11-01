# Thaumic Tinkerer on NeoForge

## GameTests on NeoForge

The GameTests of the add-on (356 since the follow-up of the defect audit of 2026-11-01, `AUDIT.md`) run on a NeoForge dedicated GameTest server as well as on Fabric: the same shared checks
(`common/src/gameTest/java/thaumic/tinkerer/testing/*RuntimeChecks.java`), under the same test names, with the same timeouts and batches.
The NeoForge wrappers are not written by hand: `tools/neoforge_gametests.py` generates them from the Fabric ones, and a JUnit test fails
when the two drift apart. Eight NeoForge-only scenarios (the block capabilities, the fake player's NeoForge menus, the damage container) come on top, so a
NeoForge run has 364 tests (the last full run, 2026-11-01: "All 364 required tests passed"). The table "Last run" below records the
runs of 2026-10-31, when there were 312 + 2.

```bash
./gradlew :neoforge:runGameTest                                              # every test
./gradlew :neoforge:runGameTest -PtinkererGameTestFilter='kamiworldgametests.*'   # one group or test: the syntax of the Fabric run
python3 tools/neoforge_gametests.py                                          # --check (default): are the NeoForge wrappers in step?
python3 tools/neoforge_gametests.py --write                                  # regenerate them after a change of the Fabric wrappers
```

A good run ends with the log line `All 364 required tests passed :)` and `BUILD SUCCESSFUL`, like the Fabric run; a run with failures ends
with `N required tests failed :(`, the list of the failed tests, and the exit code N of the server (so the Gradle task fails). The report is
`neoforge/build/tinkerer-gametest-results.xml` (JUnit XML, one `testcase` per test); every failure is also logged with its stack trace by the
logger `ThaumicTinkererGameTest`. The run directory is `neoforge/run-gametest/` (git-ignored); its `world/` and the report of the last run are
deleted before every run, so that no result depends on an earlier one. The parity check writes `neoforge/run-gametest/tinkerer-parity-report.txt`.

### How it works

| Part | Where | Counterpart on Fabric |
|---|---|---|
| Source set `gameTest`: `common/src/gameTest/java` + `neoforge/src/gameTest/java`, resources `common/src/gameTest/resources` (the parity oracle) + `neoforge/src/gameTest/resources` | `neoforge/build.gradle`, section "GameTests on NeoForge" | `thaumicTinkererGameTest` in `fabric.gradle` |
| Run `gameTest` (task `runGameTest`): a server with `-Xmx3G`, `-Dneoforge.enableGameTest=true`, `-Dneoforge.gameTestServer=true`, `-Dneoforge.enabledGameTestNamespaces=thaumictinkerer`, `-Dthaumictinkerer.gameTestReportFile=<report>`, and `-Dthaumictinkerer.gameTestFilter=<filter>` from `-PtinkererGameTestFilter`. Two mods: `thaumictinkerer` (the `main` source set) and `thaumictinkerer_gametest` (the `gameTest` source set); Thaumcraft is the jar from mavenLocal | `neoforge/build.gradle` | the run `tinkererGameTest` (two classpath groups) |
| The test mod `thaumictinkerer_gametest` (`@Mod`): registers the wrapper classes on `RegisterGameTestsEvent`, the barrel tank capability on `RegisterCapabilitiesEvent`, and the report | `neoforge/src/gameTest/java/thaumic/tinkerer/neoforge/testing/ThaumicTinkererGameTestMod.java`, `neoforge/src/gameTest/resources/META-INF/neoforge.mods.toml` | `fabric/src/gameTest/resources/fabric.mod.json` (entrypoints `fabric-gametest`) |
| The wrappers: 13 classes, 356 tests (354 after the audit of 2026-11-01, 312 before it), generated | `neoforge/src/gameTest/java/thaumic/tinkerer/neoforge/testing/*GameTests.java`, the class list `TinkererGameTestCatalogue.java` | `fabric/src/gameTest/java/thaumic/tinkerer/fabric/testing/*GameTests.java` |
| The templates: an empty 8x8x8 structure of air per class, `thaumictinkerer:<class in lower case>.empty` | `neoforge/src/gameTest/resources/data/thaumictinkerer/structure/*.empty.nbt` (generated) | Fabric API's `fabric-gametest-api-v1:empty` (the same 8x8x8 air) |
| The flat world preset of the GameTest server, with the dimension `thaumictinkerer:bedrock` the KamiWorld checks need | `neoforge/src/gameTest/resources/data/minecraft/worldgen/world_preset/flat.json` (a byte copy, generated) | `fabric/src/gameTest/resources/data/minecraft/worldgen/world_preset/flat.json` |
| The filter `-PtinkererGameTestFilter=a,b,group*` (an unknown name is an error) | mixin `testing/mixin/TinkererGameTestSelectionMixin.java` | the mixin of the same name in `fabric/src/gameTest` |
| The report: vanilla's `JUnitLikeTestReporter` plus the stack trace of each failure in the log | `ThaumicTinkererGameTestMod` | Fabric API, `-Dfabric-api.gametest.report-file` |
| Negotiated channels for mock players (see below) | mixin `testing/mixin/TinkererMockConnectionMixin.java` | not needed |
| The NeoForge body of a Fabric-native wrapper (see below) | `testing/TileBlocksNeoForgeChecks.java`, `testing/HooksNeoForgeChecks.java` | the inline bodies of `TileBlocksGameTests.interfaceForwardsFluids` and `HooksGameTests.blockProtectionAsksTheLoadersOwnBreakAndPlaceEvents` |
| NeoForge-only scenarios: hand-written classes `*NeoForgeTests`, listed in `TinkererGameTestCatalogue.NEOFORGE_ONLY` (generated) | `testing/TileBlocksNeoForgeTests.java`, `testing/ItemCapabilitiesNeoForgeTests.java`, `testing/HooksNeoForgeTests.java` | none |
| Spawn protection for the `BlockProtection` checks (the GameTest server has none) | mixin `testing/mixin/TinkererSpawnProtectionMixin.java` | the mixin of the same name in `fabric/src/gameTest` |
| A run that never started fails the task (the server's `Main` catches a failure to start and exits with 0): no report, or one without a test, is an error | `neoforge/build.gradle`, `runGameTest` | none (`fabric.gradle` has no such check) |

**Test names.** NeoForge names a test `<class in lower case>.<method in lower case>`, the name it has on Fabric, only when the class name
prefixes the template too (`@PrefixGameTestTemplate(true)`, NeoForge's default). With `@PrefixGameTestTemplate(false)` (one shared template,
as Thaumcraft's own NeoForge harness has it) a test is named after its method alone: the first run of this harness did that, and two tests
were called `registrationiscomplete` and two `registrationhadnofailures`, and a group filter such as `kamiworldgametests.*` could not work.
So the wrappers keep the prefix and every class has its own copy of the empty structure.

**Fabric-native wrappers.** A Fabric wrapper whose body is one call of a shared check gets the same body on NeoForge. Any other body uses the
Fabric API or fixtures of its class (`TileBlocksGameTests.interfaceForwardsFluids`, which drives the Transvector Interface through
Fabric's transfer API, and since 2026-11-01 `HooksGameTests.blockProtectionAsksTheLoadersOwnBreakAndPlaceEvents`, which refuses a break from Fabric API's
`PlayerBlockBreakEvents.BEFORE`); its NeoForge wrapper calls the method of the same name of the hand-written class `<Group>NeoForgeChecks`
(`TileBlocksNeoForgeChecks.interfaceForwardsFluids`: the same steps through `Capabilities.FluidHandler.BLOCK`, with a four-bucket tank that the
test mod gives every barrel; `HooksNeoForgeChecks.blockProtectionAsksTheLoadersOwnBreakAndPlaceEvents`: listeners of NeoForge's `BlockEvent.BreakEvent` and
`EntityPlaceEvent` that cancel). The generator refuses a Fabric-native wrapper without such a method, and a method that no wrapper needs. A new
check belongs in the shared checks, so that it needs no counterpart.

**NeoForge-only scenarios.** What exists only on NeoForge is tested in hand-written classes whose name ends in `NeoForgeTests` (in the same
package, with the template and the `@PrefixGameTestTemplate(true)` of the wrappers; the generator lists them in
`TinkererGameTestCatalogue.NEOFORGE_ONLY`, gives each its structure and checks the template). Today `TileBlocksNeoForgeTests`:

* `tileblocksneoforgetests.interfaceItemHandlerReachesTheBoundChest`: items put in through `Capabilities.ItemHandler.BLOCK` at a bound
  Interface (a side and no side) go into the bound chest and come out of it; unbound the handler has no slots and takes nothing; a
  `BlockCapabilityCache` made while unbound reaches the chest once bound.
* `tileblocksneoforgetests.interfaceFluidCapabilityCacheFollowsTheBinding`: a `BlockCapabilityCache` of `Capabilities.FluidHandler.BLOCK` at the
  Interface has no handler while unbound; `setTarget` invalidates it (its listener is called) and it then has a handler that fills the tank;
  after the tank is broken the handler it still holds fills nothing, the Interface lets the binding go and the cache has no handler; bound to a
  second tank it fills that one, and after `setTarget(null)` it is invalidated again, has no handler, and the handler it had fills nothing.

Since 2026-11-01 also `ItemCapabilitiesNeoForgeTests` (the item capability of the other devices, "Block capabilities" below) and `HooksNeoForgeTests`:

* `itemcapabilitiesneoforgetests.everyContainerOfTheAddonHasAnItemHandler`: every block entity type of the add-on, made for one of its blocks: a container has
  an item handler for a side (with `getSlotsForFace` slots for a `WorldlyContainer`) and one without a side (the whole container); the others have none; the nine
  containers (eight without the Kami group) are found.
* `itemcapabilitiesneoforgetests.theRestorerKeepsItsSideRules`: through a side an undamaged tool is refused, a damaged one goes in (one), does not come out until
  mended, and comes out once mended.
* `itemcapabilitiesneoforgetests.theEnchanterTakesNothingThroughItsSides`: no slot from any of the six sides, nothing in or out there; without a side the whole
  container, which still takes nothing in.
* `itemcapabilitiesneoforgetests.theRemotePlacerTakesBlocksOnly`: a stick is refused, ten stone go in, four come out.
* `hooksneoforgetests.aCancelledHitLeavesNoDamageContainer`: no `DamageContainer` is left on a pig by hits that a `LIVING_DAMAGE` listener or a NeoForge listener
  cancels, or by a hit that is dealt (`docs/HOOKS.md` section 3).

**Mock players.** NeoForge refuses to send a mod's payload over a connection that has not negotiated its channel (`Payload ... may not be sent to
the client!`, an exception in the sender), and `GameTestHelper.makeMockServerPlayerInLevel` joins with a bare connection. Thaumcraft and the
add-on send payloads to players on login and afterwards, so the test mod's mixin on `PlayerList.placeNewPlayer` gives every connection without a
setup the channels of the loaded mods (`NetworkRegistry.configureMockConnection`, NeoForge's testing API), as the configuration phase does for a
real client. On Fabric nothing refuses such a payload.

**Keeping the two catalogues in step.** `tools/neoforge_gametests.py --check` (MIT) compares every generated file byte for byte with what the
Fabric wrappers and `fabric.mod.json` generate, and fails on a missing or stray file, a Fabric-native wrapper without its counterpart and a
counterpart without its wrapper; `--write` regenerates the files (and deletes stray wrapper classes and structures). The JUnit test
`common/src/test/java/thaumic/tinkerer/NeoForgeGameTestCatalogueTest.java` (part of `./gradlew tinkererTest` and `check`) parses both trees
itself, without Python: the same classes in the order of `fabric.mod.json`, the same tests in the same order with the same attributes and
bodies, the counterparts, the templates and the world preset. The CI runs both, then `:neoforge:assemble :neoforge:runGameTest`
(`.github/workflows/ci.yml`).

### Last run

The first runs of the add-on on NeoForge at all (2026-10-31, NeoForge 21.1.259, Thaumcraft `0.1.0-alpha.7` from mavenLocal), while the event
posters, the capabilities and the soil mixin were being written. Runs 1 to 4 started all 312 tests, runs 5 and 6 the 314; each of runs 1 to 5 took
about 17 s of test time and 45 s in all. **The last full run of that day is run 6: all 314 passed.** After the defect audit of 2026-11-01 (`AUDIT.md`) a full run passed 362 of 362 (354 + 8); after its follow-up (the ore feature and tree mode, two new tests) 364 of 364 (356 + 8).

| Run | Result |
|---|---|
| 1, before the event posters (`neoforge/.../hooks/NeoForgeEventPosters.java` was empty) and the capabilities | the add-on and the harness started, 53 failed: the hook-based behaviour (damage, armour, jump, fall, break speed, drops, item-use ticks) and the interface's fluids, as expected without the posters; and the test names had no class prefix (fixed, see "Test names") |
| 2, with the event posters | 3 failed: `tileblocksgametests.interfaceforwardsfluids` (no capabilities yet), `simpleblocksgametests.acropneedsfarmland`, `hooksgametests.livingarmorabsorbhandsbackthegamesownfloatandsurvivesaninfiniteblow` |
| 3, with the capabilities (`NeoForgeCapabilities.java`) | 2 failed: `simpleblocksgametests.acropneedsfarmland`, `hooksgametests.livingarmorabsorbhandsbackthegamesownfloatandsurvivesaninfiniteblow` |
| 4, after the armour check had been adapted to NeoForge (docs/HOOKS.md) | **311 passed, 1 failed**: `simpleblocksgametests.acropneedsfarmland` |
| filtered: the two NeoForge-only scenarios with `tileblocksgametests.interfaceForwardsFluids` and `interfaceForwardsItems` | `All 4 required tests passed :)` |
| 5, with the two NeoForge-only scenarios | 314 ran, **313 passed, 1 failed**: `simpleblocksgametests.acropneedsfarmland` |
| filtered: `-PtinkererGameTestFilter='kamiworldgametests.*,tileblocksgametests.interfaceForwardsFluids'`, then `'coregametests.*'` | `All 21 required tests passed :)`, `All 3 required tests passed :)` |
| filtered: `-PtinkererGameTestFilter=kamiworldgametests.nosuchtest` | the server refuses to start (`Expected one GameTest for 'kamiworldgametests.nosuchtest', found 0`) and the task fails because there is no report |
| 6 (the last full run), with the soil mixin of the Infused Farmland (`InfusedFarmlandSoilMixin`, below) | 314 ran: **`All 314 required tests passed :)`**, the hook test of the armour step and `acropneedsfarmland` among them |

The failure of runs 2 to 5, **`simpleblocksgametests.acropneedsfarmland`**, `wheat does not`: on NeoForge wheat could stand on the Infused Farmland.
NeoForge patches `CropBlock.mayPlaceOn` to `state.getBlock() instanceof FarmBlock` (vanilla, and so Fabric: `state.is(Blocks.FARMLAND)`), and
`BlockInfusedFarmland` extends `FarmBlock`; NeoForge's `BushBlock.canSurvive` asks the soil's `canSustainPlant` first, which the farmland does not
override, so the patched `mayPlaceOn` decides (read from the NeoForge 21.1.259 bytecode). The sapling, the next assertion of the check, would get
in the same way (NeoForge's `BushBlock.mayPlaceOn` takes any `FarmBlock` too). Not the harness: the farmland needs a NeoForge `canSustainPlant`
that refuses every plant but the Infused Grain (1.7.10: the soil refused every other plant itself), which shared code cannot declare because
`TriState` is NeoForge's, so NeoForge glue: the mixin `neoforge/.../mixin/InfusedFarmlandSoilMixin.java` on `BlockInfusedFarmland` gives it a
`canSustainPlant` (`IBlockExtension`) that answers `FALSE` for every plant but the Infused Grain and `DEFAULT` for the grain (which keeps its own
`mayPlaceOn`). With it the check passed (run 6; `deviations/SimpleBlocks.md` section 2.6).

The armour check of runs 2 and 3, for the record (passing since run 4): NeoForge's patched `LivingEntity.actuallyHurt` records the armour step as the reduction
`newDamage - getDamageAfterArmorAbsorb(...)` of its `DamageContainer`, which subtracts it from `newDamage` again, so even with no listener (and
without the add-on) the damage is `damage - (damage - vanilla)`, not vanilla's float (run 2: `no listener: the health is exactly what vanilla's own
float leaves: 19.508457 (expected 19.50846)`), and an infinite blow leaves a NaN health (run 3: `an infinite blow does not leave a NaN health`).
That is the loader's own arithmetic, not the harness; the check now expects it on NeoForge.

## Client glue and block capabilities on NeoForge

The NeoForge entry (`neoforge/.../ThaumicTinkererNeoForge.java`) calls `NeoForgeCapabilities.register(bus)` and, on the client,
`ThaumicTinkererNeoForgeClient.register(bus)`, which calls `NeoForgeClientHooks.register(bus)` and runs the shared client setup
(`ThaumicTinkerer.initClient()`, every group's `*Client.register()`) from `RegisterClientExtensionsEvent`, then
`NeoForgeClientHooks.onRegisterClientExtensions(event)`. That event is the first one `ClientHooks.initClientHooks` posts from the `Minecraft`
constructor (read in the 21.1.259 patch of `Minecraft` and in `ClientHooks`): the registries are filled by then (`ClientModLoader.begin`, earlier
in the constructor), and the events in which Architectury applies screens, model layers and key mappings come after it.

### How the loader glue is started (both loaders)

Common code names no loader class any more: the two `Class.forName("thaumic.tinkerer.fabric...")` calls (`KamiGearClient.register()`,
`TileBlocks.ready()`) are gone, and each loader module installs its own glue from its entry, at the same phase as before on Fabric:

| Glue | Fabric | NeoForge |
|---|---|---|
| Kami gear client hooks | `ThaumicTinkererFabricClient` calls `KamiGearFabricClient.register()` after `initClient()` | `NeoForgeClientHooks.register` calls `KamiGearNeoForgeClient.register(bus)` from the mod constructor |
| Transvector Interface | `ThaumicTinkererFabric.onThaumcraftReady()` calls `TileBlocksFabric.register()` through `ThaumicTinkerer.guard` (now public), so a failure is recorded in `ThaumicTinkerer.failures()` | `NeoForgeCapabilities.register` adds the `RegisterCapabilitiesEvent` listener; the registrations run through `ThaumicTinkerer.guard` |
| Item handlers of the other devices | none needed: Fabric's Transfer API wraps every `Container` block entity | the same `RegisterCapabilitiesEvent` listener (below) |
| `BlockProtection` backend (`docs/HOOKS.md` section 16) | `ThaumicTinkererFabric.onInitialize()` installs `FabricBlockProtection` through `ThaumicTinkerer.guard` | the mod constructor installs `NeoForgeBlockProtection` through `ThaumicTinkerer.guard` |
| `ItemRenderers` backend | `FabricItemRenderers.install()`, first thing of the client entrypoint | `NeoForgeItemRenderers.install()`, from the mod constructor |

### Client (`neoforge/src/main/java/thaumic/tinkerer/neoforge/client/`)

| Piece | Class | What it does | Fabric counterpart |
|---|---|---|---|
| Built-in item renderers (`ItemRenderers`; the Mob Display) | `NeoForgeItemRenderers` | The backend is installed from the mod constructor, so it collects what the groups queue in `initClient()`. After `initClient()`, in the same `RegisterClientExtensionsEvent`, it registers one `IClientItemExtensions` per item; its `getCustomRenderer()` is a `BlockEntityWithoutLevelRenderer` (made on first use) whose `renderByItem(stack, context, pose, buffers, light, overlay)` calls the renderer. A renderer queued after the event is an `IllegalStateException`. The item model needs the parent `minecraft:builtin/entity` (the Mob Display's has it): NeoForge's patched `ItemRenderer` then calls `IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(...)`. Nothing else is needed. | `FabricItemRenderers` (`BuiltinItemRendererRegistry`) |
| Wings of the Robes of the Stratosphere | `KamiGearNeoForgeClient` | `EntityRenderersEvent.AddLayers` (mod bus, posted at every resource reload for the new renderers) adds `WingsLayer` to the `PlayerRenderer` of every skin model (`getSkins()`, `getSkin(...)`), with `getEntityModels()`. `LivingEntityRenderer.addLayer` is public through NeoForge's access transformer. | `KamiGearFabricClient` (`LivingEntityFeatureRendererRegistrationCallback`) |
| Ghost blocks of the Worldshaper's Looking Glass | `KamiGearNeoForgeClient` | `RenderLevelStageEvent` (game bus) at `AFTER_TRANSLUCENT_BLOCKS`, when `showPlacementMirrorBlocks` is on, calls `PlacementMirrorPredictionRenderer.render(event.getPoseStack(), renderBuffers().bufferSource(), event.getCamera())`. The stage is posted from `renderSectionLayer(translucent)` inside `LevelRenderer.renderLevel`, after the model view stack got the camera rotation (`Matrix4fStack.mul(frustumMatrix)`, popped at the end of `renderLevel`), with a new identity pose stack. That is the state of Fabric's `AFTER_TRANSLUCENT` (the identity pose stack of `renderLevel`, the rotation on the model view stack), which the renderer expects. Read in the 21.1.259 bytecode and in fabric-rendering-v1 5.2.1; not seen. | `KamiGearFabricClient` (`WorldRenderEvents.AFTER_TRANSLUCENT`) |

Both Kami hooks are registered only while `ConfigHandler.enableKami` is on (as on Fabric).

**The rest of the shared client setup needs no NeoForge glue.** What Architectury 13.0.11 does on NeoForge for each registry the groups use
(read in `architectury-neoforge-13.0.11-sources.jar`), and whether that still works when it is called from `RegisterClientExtensionsEvent`
(the order of `ClientHooks.initClientHooks`: `RegisterClientExtensionsEvent`, `RegisterMenuScreensEvent`, `RegisterClientReloadListenersEvent`,
`RegisterLayerDefinitions`, `RegisterRenderers`, ..., `RegisterKeyMappingsEvent`, the GUI layers):

| Registry | Architectury on NeoForge | Called from `RegisterClientExtensionsEvent` |
|---|---|---|
| `MenuRegistry.registerScreenFactory` (7 screens) | adds a listener of `RegisterMenuScreensEvent` when called | the event comes later: applied |
| `EntityModelLayerRegistry.register` (the wings) | a map, read in `RegisterLayerDefinitions` | later: applied |
| `BlockEntityRendererRegistry.register` | vanilla `BlockEntityRenderers.register`, read at the first resource reload | applied |
| `RenderTypeRegistry.register` | `ItemBlockRenderTypes.setRenderLayer` (deprecated by NeoForge, still read by the default `getRenderTypes` of a model without `render_type`) | applied |
| `ColorHandlerRegistry` | registers on `ItemColors` / `BlockColors` directly when they exist (they are made before `initClientHooks`), else queues for `RegisterColorHandlersEvent` | applied (directly) |
| `ItemPropertiesRegistry` | vanilla `ItemProperties.register` | applied |
| `KeyMappingRegistry` (the armour toggle) | queued until `RegisterKeyMappingsEvent` | later: applied |
| `ReloadListenerRegistry` (client resources) | registered on the client resource manager directly | before the first reload: applied |
| `NetworkManager.registerReceiver` (S2C) | adds a listener of `RegisterPayloadHandlersEvent` (posted at the end of mod loading) | later: applied |
| `ClientGuiEvent.RENDER_HUD`, `ClientTickEvent`, `ClientPlayerEvent` | game-bus events | any time |

The groups register no particle providers, entity renderers or GUI layers of their own (no entities; the HUD goes through `RENDER_HUD`). The
recipe-viewer and Jade plugins are found by their annotations (`@EmiEntrypoint`, `@JeiPlugin`, `@WailaPlugin`). One order differs from
Fabric: on NeoForge the shared client setup runs in the `Minecraft` constructor, before `FMLCommonSetupEvent`, so before Thaumcraft calls
`onThaumcraftReady` (Thaumcraft's NeoForge side runs the add-on hooks in common setup). The eight `*Client.register()` methods and the
handlers they start read registry objects and register callbacks only (read in the code), so the order does not matter to them.

### Block capabilities (`neoforge/.../NeoForgeCapabilities.java`)

`RegisterCapabilitiesEvent` (mod bus; NeoForge posts it in its "Registration events" step, after common and client setup) registers for the
Transvector Interface's block entity type:

* **`Capabilities.FluidHandler.BLOCK`**: none (null) while the interface is unbound, bound to itself or to another interface, or while the
  bound block has no fluid handler for that side (the refusals of Fabric's `TileBlocksFabric`). Otherwise the provider returns a handler that
  asks the bound block for its handler again on every call (an empty handler while there is none). NeoForge's `BlockCapabilityCache` keeps what
  a provider returned, so returning the tank's own handler would let a pipe keep filling a tank that was broken or unbound since; Fabric's
  lookup cache asks the provider every time and needs no such handler.
* **Invalidation**: `TileTransvector.setBindingListener(BlockEntity::invalidateCapabilities)`. The new common hook runs when `setTarget`
  changes the binding and when `getTile()` ends one, so a cache that saw "none" asks again. NeoForge requires a provider to invalidate when its
  answer changes (Javadoc of `BlockCapability`).
* **`Capabilities.ItemHandler.BLOCK`**: the interface's own `WorldlyContainer` in NeoForge's `InvWrapper` (no side) or `SidedInvWrapper` (a
  side). Fabric's Transfer API wraps any `Container` block entity by itself, so items reach the interface there without glue. NeoForge wraps
  only the vanilla containers (`CapabilityHooks`), so without this a modded pipe would not see the interface. A vanilla hopper reaches it either
  way, through the vanilla container path. The wrapper reads the interface on every call and never goes stale.
* **`Capabilities.ItemHandler.BLOCK` of every other device** (since 2026-11-01; until then only the Interface had one, so a modded pipe on NeoForge could not reach
  the Restorer, the Funnel, the Enchanter, the Dynamism Tablet, the Remote Placer, the Corporeal Attractor, the Aspect Analyzer or the Celestial Gateway, which
  Fabric's Transfer API reaches by itself): `NeoForgeCapabilities.containerItems` is registered for every block entity type in the add-on's namespace but the
  Interface's (taken from the registry, so a new device is covered without a change): a `WorldlyContainer` asked for a side gets `SidedInvWrapper` (its
  `getSlotsForFace`, `canPlaceItemThroughFace` and `canTakeItemThroughFace` hold), otherwise, and for a plain `Container`, `InvWrapper` (the whole container;
  `canPlaceItem` still decides what goes in, nothing decides what comes out); a block entity that is no container gets none. That is NeoForge's own convention for
  its sided vanilla containers (furnace, brewing stand); Fabric's `InventoryStorage.of(container, side)` likewise uses the side rules only for a side (whether
  Fabric checks anything on extraction without a side was not read). So a mod that asks without a side can take the Enchanter's or the Restorer's tool out on
  NeoForge. The wrappers read the block entity on every call.
* **Not registered: energy**, on neither loader (`deviations/TileBlocks.md` 2.5). Essentia goes through the Thaumcraft API interfaces.

### What was verified, and how (2026-10-31)

| What | How | Result |
|---|---|---|
| The NeoForge tree compiles | `:neoforge:compileJava` | BUILD SUCCESSFUL |
| Fabric still compiles and its unit tests pass | `:check` (root project: the Fabric source sets, `tinkererTest`) | BUILD SUCCESSFUL; 410 tests, 0 failures |
| Fabric: the Interface's fluids, now registered from `ThaumicTinkererFabric` | `:runTinkererGameTest -PtinkererGameTestFilter='tileblocksgametests.*'` | "All 35 required tests passed", `interfaceforwardsfluids` among them |
| NeoForge: the Interface's fluid capability | `:neoforge:runGameTest -PtinkererGameTestFilter='tileblocksgametests.*'` (the GameTest harness above; its `TileBlocksNeoForgeChecks.interfaceForwardsFluids` asks `level.getCapability` without a cache: unbound none, bound to a barrel tank it fills a bucket and drains half) | "All 35 required tests passed", `interfaceforwardsfluids` among them |
| NeoForge client start | `:neoforge:runClient` under `xvfb-run` with Mesa llvmpipe (`LIBGL_ALWAYS_SOFTWARE=1`), Kami content on, stopped by a watchdog after the log had been quiet for 60 s (so Gradle reported exit 1) | The client loaded and showed the first-launch screen (the accessibility prompt before the title screen), with no error or recorded failure from the add-on in the log. `WingsLayer`'s constructor bakes `ModelWings.LAYER` and throws if that layer is missing; the `AddLayers` handler builds it at the first reload. So the layer registration from `RegisterClientExtensionsEvent` and the handler are inferred to work; neither writes a log line. |
| NeoForge client in a world | the client smoke harness ("Client smoke on NeoForge" below): `tools/client-smoke.sh --neoforge`, then with `-- -PwithEmi -PwithJade` and with `-- -PwithJei` | All three runs `COMPLETED`, 0 FAIL, 0 HARNESS_ERROR (730, 739 and 735 PASS; 80, 88 and 82 pictures); the Mob Display drawn through `NeoForgeItemRenderers`, the screens, the block-entity renderers and the HUD as on Fabric |

Since then (2026-10-31): the client smoke harness runs on NeoForge ("Client smoke on NeoForge" below), and two NeoForge-only GameTests
(`TileBlocksNeoForgeTests`, in every `:neoforge:runGameTest`) cover the capabilities:
`tileblocksneoforgetests.interfaceItemHandlerReachesTheBoundChest` (`Capabilities.ItemHandler.BLOCK` at the interface: unbound no slots and
nothing taken; bound, the chest's 27 slots by side and without side, items in and out; a `BlockCapabilityCache` made while unbound reaches the chest
once bound) and `tileblocksneoforgetests.interfaceFluidCapabilityCacheFollowsTheBinding` (a `BlockCapabilityCache` of
`Capabilities.FluidHandler.BLOCK`: none while unbound; binding invalidates and the cached handler fills the tank; after the tank is broken the held
handler reaches nothing, the interface lets the binding go and the cache has none; rebound, then unbound by `setTarget(null)`, none again).

On 2026-11-01 (the item capabilities of the other devices, the two mixins of `docs/HOOKS.md` sections 3 and 4, `NeoForgeBlockProtection`):
`:neoforge:compileJava` and `:neoforge:compileGameTestJava` passed, and `:neoforge:runGameTest
-PtinkererGameTestFilter='coregametests.*,hooksgametests.*,hooksneoforgetests.*,itemcapabilitiesneoforgetests.*,tileblocksneoforgetests.*,tileblocksgametests.*,machinesgametests.*,enchantmentsgametests.*,kamiworldgametests.*'`
ended in `All 185 required tests passed :)`: the five new NeoForge-only scenarios, the hook and `BlockProtection` checks, and the groups whose devices now have an
item capability (vanilla hoppers reach them through it now: read in the 21.1.259 bytecode, `HopperBlockEntity` first calls
`VanillaInventoryCodeHooks.insertHook` / `extractHook`, which ask `Level.getCapability(Capabilities.ItemHandler.BLOCK, ...)`, and takes the vanilla container path
only when there is no handler). The whole suite was not run then.

**Not verified on NeoForge:**

* The wings on a player and the ghost blocks in a world (`KamiGearNeoForgeClient`): the client smoke does not put on the robes or use the Looking Glass, on
  either loader.
* A GPU, other mods' pipes and tanks (the item capabilities of the devices were tested through `level.getCapability` and its handlers, not with
  another mod's pipe). A dedicated NeoForge server with real clients is now covered by the multiplayer check (`MULTIPLAYER_VERIFICATION.md`), for its
  scenarios only.

## Multiplayer on NeoForge

`tools/multiplayer-smoke.sh --neoforge` (`:neoforge:runMultiplayer`, `MULTIPLAYER_VERIFICATION.md`): a NeoForge dedicated server with the add-on and the
test mod `thaumictinkerer_mpserver` (`@Mod(dist = Dist.DEDICATED_SERVER)`, source set `multiplayerServer`), and the same five clients as on Fabric. What
differed from Fabric:

* **Architectury 13.0.11 posts no `ClientSystemMessageEvent` on NeoForge.** Its NeoForge side maps `ClientChatReceivedEvent` (whose `System` subclass is
  the system message) to `ClientChatEvent.RECEIVED` only (read in `architectury-neoforge-13.0.11.jar`, `EventHandlerImplClient`); on Fabric the system
  message is a `ClientSystemMessageEvent`. The add-on itself listens to neither; the harness's `/ttmp` answers are heard through both.
* **`NetworkManager.canPlayerReceive` needs both sides on NeoForge.** On the second server (Kami off) the three Kami payloads are not registered on the
  server, and NeoForge then answers false for a client that has them; Fabric answers from the client's announcement alone. The census expects only
  `config_sync` there.
* **The registry sync's message** for a client without Kami on a server with Kami: NeoForge's "The server send registries with unknown keys: ..." with the
  50 keys (`neoforge.network.registries.sync.server-with-unknown-keys`), where Fabric says "Received 50 registry entries that are unknown to this client".

The run of 2026-11-01 (`BUILD SUCCESSFUL in 3m 46s`): all seven reports `COMPLETED`, 67 PASS, 0 FAIL, 0 HARNESS_ERROR, 0 NOT_RUN (server 25, second server 6,
lead 20, second player 8 and one INFO, the three mismatch clients 3, 2 and 3), the same scenarios and verdicts as on Fabric; six pictures, none blank.
**NeoForge defects found: none.** The first NeoForge run failed on the harness only (the two points above), and was repeated after they were fixed.

## Client smoke on NeoForge

The client smoke harness of `docs/CLIENT_VERIFICATION.md` runs on NeoForge as well: a real client under Xvfb with Mesa llvmpipe builds a flat world,
draws every block, item, menu, block-entity renderer and HUD element of the add-on, checks the models, textures, translations, screens and renderers, and
writes `neoforge/build/tinkerer-client-smoke/report.json` and the pictures.

```bash
tools/client-smoke.sh --neoforge                            # :neoforge:runClientSmoke under xvfb-run; about 2 minutes
tools/client-smoke.sh --neoforge -- -PwithEmi -PwithJade    # with EMI and Jade in the client
tools/client-smoke.sh --neoforge -- -PwithJei               # with JEI
```

**Wiring.** The harness is the loader-neutral tree `common/src/clientSmoke/java` (package `thaumic.tinkerer.clientsmoke`, shared with the Fabric run) and
the NeoForge side `neoforge/src/clientSmoke`: the client-only test mod `thaumictinkerer_clientsmoke` (`TinkererClientSmokeNeoForge`,
`@Mod(value = "thaumictinkerer_clientsmoke", dist = Dist.CLIENT)`, its own `META-INF/neoforge.mods.toml`) and `NeoForgeSmokePlatform`, the eight loader
calls of the harness (`ClientTickEvent.Post`, `RenderLevelStageEvent` at `AFTER_LEVEL`, `ScreenEvent.Render.Post`, the creative-tab pages, `FMLPaths.GAMEDIR`,
`ModList`). In `neoforge/build.gradle`: the source set `clientSmoke` (`check` compiles it), the run `clientSmoke` with the add-on and the harness as two mods
(`mods { thaumictinkerer { sourceSet main }; thaumictinkerer_clientsmoke { sourceSet clientSmoke } }`, like the GameTest run), run directory
`neoforge/run-clientsmoke`, `-Dthaumictinkerer.clientSmoke=true` and the out directory, source root and timeout properties, a 1280x720 window, and
`prepareClientSmoke` (options and result directory reset, as `prepareTinkererClientSmoke` on Fabric). `runClientSmoke` fails unless the report says
`COMPLETED`. Neither release jar contains the harness (`build/libs/*.jar`, `neoforge/build/libs/*.jar`: no `clientsmoke` entry, only the add-on's mod id).

**What had to change for NeoForge** (all in the harness; no add-on code):

| Where | Why |
|---|---|
| `NeoForgeSmokePlatform.selectCreativeTab` | NeoForge pages the creative tabs itself (`CreativeTabsScreenPage`); its screen has a public `setCurrentPage` but keeps the page list and `selectTab` private (no access transformer for them), so the harness finds the page that holds the tab and calls `selectTab` by reflection. Fabric uses `FabricCreativeInventoryScreen` |
| `LogExcerpt` (shared) | NeoForge's client log writes `[31Oct2026 12:34:56.789] [thread/LEVEL] [logger/]:` after the launcher's first lines (`[12:34:56] [thread/LEVEL]`); the excerpt and `LogWatcher` read both. A Fabric line still matches exactly as before |
| `HarnessJeiPlugin`, `HarnessJadePlugin` (shared) | NeoForge's JEI and Jade find plugins by annotation: `@JeiPlugin`, `@WailaPlugin` (Fabric's read only the entrypoints of `fabric.mod.json`, which stay) |
| `SourceKeysCheck` (shared) | reads the shared sources and the running loader's glue (`neoforge/src/main/java` here, `fabric/src/main/java` on Fabric) |

**Last runs (2026-10-31):**

| Optional mods | PASS | FAIL | HARNESS_ERROR | NOT_RUN | INFO | pictures |
|---|---|---|---|---|---|---|
| none | 730 | 0 | 0 | 1 | 52 | 80 |
| EMI, Jade | 739 | 0 | 0 | 1 | 52 | 88 |
| JEI | 735 | 0 | 0 | 1 | 50 | 82 |

All three `COMPLETED`; the `NOT_RUN` is the Kami tool-mode tooltip (no input device), as on Fabric. Compared check by check with the Fabric runs of the same
mods, the only differences are the three states of Gaseous Tenebrae, drawn as "visible" on NeoForge only because vanilla's smooth lighting (NeoForge, with
`experimentalForgeLightPipelineEnabled` off) darkens the floor under the gas cell (light opacity 15) where Fabric API's Indigo renderer does not (its
`fix-mean-light-calculation` replaces a 0 light sample by the smallest non-zero one), and the `up=true` states of the three fire blocks, which vary from
run to run on both loaders. The EMI, JEI and Jade checks (necromancy and seed-drop pages, catalysts and workstations, the add-on's recipes in Thaumcraft's
categories, the six Jade tooltips) pass as on Fabric. The pictures were compared pixel by pixel and read; the details are in `docs/CLIENT_VERIFICATION.md`
("Results on each loader").

**Client glue seen in a world for the first time:** the built-in item renderer backend (`NeoForgeItemRenderers`) draws the Mob Display of every mob
(`items-machines-02.png`, equal to Fabric's in all but 0.01 % of the pixels); the screens registered through Architectury from `RegisterClientExtensionsEvent`
open (seven menus and the warp gate's destinations), the block-entity renderers draw (twelve close-ups), the soul hearts and the Dislocation readout are on the
HUD, and the Russian pass reloads the resources. The log has no warning or error of the add-on.

**NeoForge client defects found by the harness: none.** Not seen by it (on either loader): the wings of the Robes of the Stratosphere and the Placement
Mirror's ghost blocks (`KamiGearNeoForgeClient`), the Kami tool-mode tooltip, input handling, a GPU.
