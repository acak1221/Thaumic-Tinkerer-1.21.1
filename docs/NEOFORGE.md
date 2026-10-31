# Thaumic Tinkerer on NeoForge

## GameTests on NeoForge

The 312 GameTests of the add-on run on a NeoForge dedicated GameTest server as well as on Fabric: the same shared checks
(`common/src/gameTest/java/thaumic/tinkerer/testing/*RuntimeChecks.java`), under the same test names, with the same timeouts and batches.
The NeoForge wrappers are not written by hand: `tools/neoforge_gametests.py` generates them from the Fabric ones, and a JUnit test fails
when the two drift apart.

```bash
./gradlew :neoforge:runGameTest                                              # every test
./gradlew :neoforge:runGameTest -PtinkererGameTestFilter='kamiworldgametests.*'   # one group or test: the syntax of the Fabric run
python3 tools/neoforge_gametests.py                                          # --check (default): are the NeoForge wrappers in step?
python3 tools/neoforge_gametests.py --write                                  # regenerate them after a change of the Fabric wrappers
```

A good run ends with the log line `All 312 required tests passed :)` and `BUILD SUCCESSFUL`, like the Fabric run; a run with failures ends
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
| The wrappers: 13 classes, 312 tests, generated | `neoforge/src/gameTest/java/thaumic/tinkerer/neoforge/testing/*GameTests.java`, the class list `TinkererGameTestCatalogue.java` | `fabric/src/gameTest/java/thaumic/tinkerer/fabric/testing/*GameTests.java` |
| The templates: an empty 8x8x8 structure of air per class, `thaumictinkerer:<class in lower case>.empty` | `neoforge/src/gameTest/resources/data/thaumictinkerer/structure/*.empty.nbt` (generated) | Fabric API's `fabric-gametest-api-v1:empty` (the same 8x8x8 air) |
| The flat world preset of the GameTest server, with the dimension `thaumictinkerer:bedrock` the KamiWorld checks need | `neoforge/src/gameTest/resources/data/minecraft/worldgen/world_preset/flat.json` (a byte copy, generated) | `fabric/src/gameTest/resources/data/minecraft/worldgen/world_preset/flat.json` |
| The filter `-PtinkererGameTestFilter=a,b,group*` (an unknown name is an error) | mixin `testing/mixin/TinkererGameTestSelectionMixin.java` | the mixin of the same name in `fabric/src/gameTest` |
| The report: vanilla's `JUnitLikeTestReporter` plus the stack trace of each failure in the log | `ThaumicTinkererGameTestMod` | Fabric API, `-Dfabric-api.gametest.report-file` |
| Negotiated channels for mock players (see below) | mixin `testing/mixin/TinkererMockConnectionMixin.java` | not needed |
| The NeoForge body of a Fabric-native wrapper (see below) | `testing/TileBlocksNeoForgeChecks.java` | the inline body of `TileBlocksGameTests.interfaceForwardsFluids` |

**Test names.** NeoForge names a test `<class in lower case>.<method in lower case>`, the name it has on Fabric, only when the class name
prefixes the template too (`@PrefixGameTestTemplate(true)`, NeoForge's default). With `@PrefixGameTestTemplate(false)` (one shared template,
as Thaumcraft's own NeoForge harness has it) a test is named after its method alone: the first run of this harness did that, and two tests
were called `registrationiscomplete` and two `registrationhadnofailures`, and a group filter such as `kamiworldgametests.*` could not work.
So the wrappers keep the prefix and every class has its own copy of the empty structure.

**Fabric-native wrappers.** A Fabric wrapper whose body is one call of a shared check gets the same body on NeoForge. Any other body uses the
Fabric API or fixtures of its class (today only `TileBlocksGameTests.interfaceForwardsFluids`, which drives the Transvector Interface through
Fabric's transfer API); its NeoForge wrapper calls the method of the same name of the hand-written class `<Group>NeoForgeChecks`
(`TileBlocksNeoForgeChecks.interfaceForwardsFluids`: the same steps through `Capabilities.FluidHandler.BLOCK`, with a four-bucket tank that the
test mod gives every barrel). The generator refuses a Fabric-native wrapper without such a method, and a method that no wrapper needs. A new
check belongs in the shared checks, so that it needs no counterpart.

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
posters and the capabilities were being written. Every run started all 312 tests; each took about 17 s of test time and 45 s in all.

| Run | Result |
|---|---|
| 1, before the event posters (`neoforge/.../hooks/NeoForgeEventPosters.java` was empty) and the capabilities | the add-on and the harness started, 53 failed: the hook-based behaviour (damage, armour, jump, fall, break speed, drops, item-use ticks) and the interface's fluids, as expected without the posters; and the test names had no class prefix (fixed, see "Test names") |
| 2, with the event posters | 3 failed: `tileblocksgametests.interfaceforwardsfluids` (no capabilities yet), `simpleblocksgametests.acropneedsfarmland`, `hooksgametests.livingarmorabsorbhandsbackthegamesownfloatandsurvivesaninfiniteblow` |
| 3, with the capabilities (`NeoForgeCapabilities.java`) | 2 failed: `simpleblocksgametests.acropneedsfarmland`, `hooksgametests.livingarmorabsorbhandsbackthegamesownfloatandsurvivesaninfiniteblow` |
| 4, after the armour check had been adapted to NeoForge (docs/HOOKS.md) | **311 passed, 1 failed**: `simpleblocksgametests.acropneedsfarmland` |
| filtered: `-PtinkererGameTestFilter='kamiworldgametests.*,tileblocksgametests.interfaceForwardsFluids'`, then `'coregametests.*'` | `All 21 required tests passed :)`, `All 3 required tests passed :)` |
| filtered: `-PtinkererGameTestFilter=kamiworldgametests.nosuchtest` | the server refuses to start (`Expected one GameTest for 'kamiworldgametests.nosuchtest', found 0`) and the task fails because there is no report |

The one failure left, **`simpleblocksgametests.acropneedsfarmland`**, `wheat does not`: on NeoForge wheat can stand on the Infused Farmland.
NeoForge patches `CropBlock.mayPlaceOn` to `state.getBlock() instanceof FarmBlock` (vanilla, and so Fabric: `state.is(Blocks.FARMLAND)`), and
`BlockInfusedFarmland` extends `FarmBlock`; NeoForge's `BushBlock.canSurvive` asks the soil's `canSustainPlant` first, which the farmland does not
override, so the patched `mayPlaceOn` decides (read from the NeoForge 21.1.259 bytecode). The sapling, the next assertion of the check, would get
in the same way (NeoForge's `BushBlock.mayPlaceOn` takes any `FarmBlock` too). Not the harness: the farmland needs a NeoForge `canSustainPlant`
that refuses every plant but the Infused Grain (1.7.10: the soil refused every other plant itself), which shared code cannot declare because
`TriState` is NeoForge's, so NeoForge glue (a mixin on `BlockInfusedFarmland` that overrides `IBlockExtension.canSustainPlant`).

The armour check of runs 2 and 3, for the record: NeoForge's patched `LivingEntity.actuallyHurt` records the armour step as the reduction
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
* **Not registered: energy**, on neither loader (`deviations/TileBlocks.md` 2.5). Essentia goes through the Thaumcraft API interfaces.

### What was verified, and how (2026-10-31)

| What | How | Result |
|---|---|---|
| The NeoForge tree compiles | `:neoforge:compileJava` | BUILD SUCCESSFUL |
| Fabric still compiles and its unit tests pass | `:check` (root project: the Fabric source sets, `tinkererTest`) | BUILD SUCCESSFUL; 410 tests, 0 failures |
| Fabric: the Interface's fluids, now registered from `ThaumicTinkererFabric` | `:runTinkererGameTest -PtinkererGameTestFilter='tileblocksgametests.*'` | "All 35 required tests passed", `interfaceforwardsfluids` among them |
| NeoForge: the Interface's fluid capability | `:neoforge:runGameTest -PtinkererGameTestFilter='tileblocksgametests.*'` (the GameTest harness above; its `TileBlocksNeoForgeChecks.interfaceForwardsFluids` asks `level.getCapability` without a cache: unbound none, bound to a barrel tank it fills a bucket and drains half) | "All 35 required tests passed", `interfaceforwardsfluids` among them |
| NeoForge client start | `:neoforge:runClient` under `xvfb-run` with Mesa llvmpipe (`LIBGL_ALWAYS_SOFTWARE=1`), Kami content on, stopped by a watchdog after the log had been quiet for 60 s (so Gradle reported exit 1) | The client loaded and showed the first-launch screen (the accessibility prompt before the title screen), with no error or recorded failure from the add-on in the log. `WingsLayer`'s constructor bakes `ModelWings.LAYER` and throws if that layer is missing; the `AddLayers` handler builds it at the first reload. So the layer registration from `RegisterClientExtensionsEvent` and the handler are inferred to work; neither writes a log line. |

**Not verified on NeoForge:**

* The Mob Display drawn: no world was entered and no item was shown.
* The wings on a player and the ghost blocks in a world.
* The item capability of the Interface: no check asks `Capabilities.ItemHandler.BLOCK` there. The shared `interfaceForwardsItems` uses the
  `Container` methods directly.
* The cache behaviour: no check holds a `BlockCapabilityCache` at the interface across a binding, an unbinding or a broken tank.
* A GPU, a dedicated server with a real client, other mods' pipes and tanks.
