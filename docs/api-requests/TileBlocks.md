# TileBlocks: requests to the Thaumcraft API and to the loader modules

Group TileBlocks: Animation Tablet, Remote Placer, Levitational Locomotive and Relay, Golem Connector, Transvector
Interface, Transvector Dislocator, Transvector Binder. The numbers `A5`, `A7`, `A8`, `A11` are those of
`docs/API_GAPS.md` section 2. Nothing below was done by reaching into `thaumcraft.common.*`: every open item is a named
stub with the loss written down. Sections 2 to 5 (the entity key, the sparkle, the sound ids, the mirrors switch) are
answered by the API and kept for the record. Priorities are the group's own opinion (P1 changes what the player sees, P3 only
hardens something that works today).

## 1. Golem view for the Golem Connector (A11, P3): answered

**Answered** by `thaumcraft.api.golems` of Thaumcraft 4 Reload since `316897d` (2026-11-01): `GolemApi.view` (a `GolemView` record with
the material, core, decoration, health, position, home and its side, and `GolemView.Marker`s), `GolemApi.setHome`, `GolemApi.setMarkers`.
The add-on implements `GolemAccess` over it (`ApiGolemAccess`, installed by `GolemAccessProvider`); `docs/deviations/TileBlocks.md` 2.4 and
`docs/deviations/ComputerCraft.md` section 3 say how the API's refusals and the dimension ids show. The request as it was written:


**Need.** The Golem Connector exposes a bound golem to computers (ComputerCraft `tt_golemconnector`): type, core,
accessories, health, position, home with facing, and the markers, readable and, for the home and the markers, writable.
Thaumcraft's golems have no API ("Golems had no API in 1.7.10 and have none now", `docs/api/PORTING.md`).

**Proposed signature.** In 1.21.1 types (this is exactly the shape the add-on already codes against,
`thaumic.tinkerer.common.core.golems.GolemAccess`):

```java
package thaumcraft.api.golems;

public interface GolemView {
    record Marker(BlockPos pos, ResourceKey<Level> dimension, byte side, byte color) {}   // colour -1 = any
    String materialName();        // "Clay", "Flesh", "Iron", "Stone", "Straw", "Tallow", "Thaumium", "Wood"; null if unnamed
    int core();                   // the animation core id 0..11, or -1 for a golem without a core
    String decoration();          // one character per accessory, as the original's 'decoration' field
    float health();
    Vec3 position();
    BlockPos home();
    int homeFacing();             // a Direction's 3D data value
    List<Marker> markers();
    void setHome(BlockPos home, int facing);          // the original setHomeArea(x, y, z, 35) and homeFacing
    void setMarkers(List<Marker> markers);
}

public final class GolemApi {
    @Nullable public static GolemView view(Entity entity);   // null for an entity that is no golem
}
```

**Backed by.** `EntityGolemBase` (`getGolemCore`, `getDecoration`, `getHomePosition`, `setHome`, `getMarkers`,
`setMarkers`) and `GolemMarker`; the entity class is `final` and not exported.

**Stub in the add-on.** `GolemAccessProvider.forEntity(Entity)` returns `UnavailableGolemAccess.INSTANCE`, which behaves
like a connector with no golem (the 1.7.10 answers: empty results, and the original's error messages for the writes). The
peripheral logic (`GolemConnectorMethods`, 15 methods) already runs on `GolemAccess`. When the API exists, the body of
`GolemAccessProvider.forEntity` is the one place to change (`GolemAccessProvider.setProvider` already takes a
`Function<Entity, GolemAccess>`).

**Until then.** The Golem Connector remembers its golem (the Binder's sneak-click works, the UUID is saved, the golem is
found every 100 ticks within thirty blocks) but every question about the golem answers "no golem".

## 2. Stable entity key for the golem (A7): answered

`ItemConnector.isGolem` is `ThaumcraftEntities.is(entity, ThaumcraftEntities.GOLEM)`. Nothing to request. If A11 is built,
`GolemApi.view(entity) != null` could replace it.

## 3. Client sparkles for the Relay beam (A8): answered

`TileEntityRelay` draws the beam with `ThaumcraftClientFX.sparkle(level, x, y, z, type)` on the client, as `tcProxy.sparkle`
was called: the original types 2 (blue) and 14 (white) are the API's 2 and 6, and the API rolls the original's four-in-six
chance. Nothing to request.

## 4. Sound constants (A7): answered

The wand click is `ThaumcraftSoundIds.event(ThaumcraftSoundIds.TOOL)` (`DeviceSounds.wandTool()`), resolved when it is
played. Nothing to request.

## 5. Mirrors switch (A5): answered

`TileBlocks.mirrorsAllowed()` calls `ThaumcraftApi.isMirrorsAllowed()`, which the API has now. Nothing to request.

## 6. Requests to the loader modules (to the lead)

* **Fluids through the Transvector Interface: done for Fabric and for NeoForge, both run on a GameTest server (P1 for pipe and tank users).**
  `thaumic.tinkerer.fabric.TileBlocksFabric` (new file of the Fabric module, called by `ThaumicTinkererFabric.onThaumcraftReady()`)
  registers `FluidStorage.SIDED` for `TileBlocks.interfaceType()` and returns the fluid storage of the block
  `TileTransvector.getTile()` is bound to, for the same side. On NeoForge `thaumic.tinkerer.neoforge.NeoForgeCapabilities` registers
  `Capabilities.FluidHandler.BLOCK` in `RegisterCapabilitiesEvent` with the same refusals, a handler that asks the bound block again on
  every call, and the invalidation of the Interface's capabilities when its binding changes (the new common hook
  `TileTransvector.setBindingListener`); it also registers `Capabilities.ItemHandler.BLOCK` over the Interface's `WorldlyContainer`,
  which NeoForge, unlike Fabric's Transfer API, does not wrap by itself (`docs/deviations/TileBlocks.md` 2.5, `docs/NEOFORGE.md`). The NeoForge
  side is checked by `interfaceForwardsFluids` and two NeoForge-only GameTests (the item handler, the cache invalidation), which passed on
  2026-10-31; other mods' pipes and tanks were not tried.
* **Energy through the Transvector Interface (P2).** The 1.7.10 Interface forwarded CoFH RF and IC2 EU. 1.21.1 needs, per
  loader, an energy capability (Fabric: Team Reborn Energy `EnergyStorage.SIDED`, which is not a dependency of the add-on
  and is outside the import list of PORTING_RULES; NeoForge: `Capabilities.EnergyStorage.BLOCK`) that asks
  `TileTransvector.getTile()` for the bound block's own energy storage. Not done on either loader.
* **Hook for loader glue (P3): answered.** Common code no longer reaches a loader class by name: each loader module installs its own
  glue from its entry (Fabric: `ThaumicTinkererFabric`, `ThaumicTinkererFabricClient`; NeoForge: `NeoForgeCapabilities`,
  `NeoForgeClientHooks`), through `ThaumicTinkerer.guard` where a failure must be recorded.
* **Computer peripherals (P3).** The Tablet (`tt_animationTablet`), the Interface and the Golem Connector
  (`tt_golemconnector`) were ComputerCraft and OpenComputers peripherals. The plain-Java surface is there:
  `TileAnimationTablet.trigger/getRotation/setRotation/setLeftClick/setRedstone` (+ fields), and
  `TileGolemConnector.peripheral()` returning `GolemConnectorMethods` (`METHOD_NAMES`, `call(name, args...)`,
  `MethodException` with the original messages). A CC:Tweaked bridge on each loader would register them.
