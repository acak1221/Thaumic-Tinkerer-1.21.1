# Machines: deviations from Thaumic Tinkerer 1.7.10

Group Machines: Aspectalyzer, Thaumic Restorer, Essentia Funnel, Kinetic Attractor, Corporeal Attractor, Tablet of
Necromancy, soul mould, soul aspects, mob display, Cursed Spirit's Blade. Specification: the 1.7.10 sources in
GTNewHorizons/ThaumicTinkerer (commit in `NOTICE.md`). Everything not listed here is ported as it was:
research keys, coordinates, aspects, recipes, hardness and resistance, ranges, intervals, messages. Numbers that were
checked against the original on purpose are in section 9. Requests to the API and to the lead are in
`docs/api-requests/Machines.md`.

Reading key: **forced** means 1.21.1 cannot do it the old way; **fix** means the original crashed or was exploitable;
**choice** means a free decision of the port.

## 1. Metadata becomes blocks, block states and items (forced)

| 1.7.10 | 1.21.1 |
|---|---|
| `BlockMagnet` metadata bit 0 (0 pulls, "blue"; 1 pushes, "red") | block state `pulling` (true pulls); two block models and one blockstate file |
| `BlockMagnet` metadata bit 1 (the Corporeal Attractor) | a second block `mob_magnet` of the same class (`BlockMagnet(properties, mob = true)`), with its own block entity `TileMobMagnet` |
| `ItemBlockMagnet` (names by metadata, tooltip) | removed; two block items with the plain names |
| `BlockRepairer` metadata 2..5 (facing) | block state `facing` (horizontal); the tube side is the facing, as before |
| `BlockSummon` | registry path `spawner` (the 1.7.10 name), class name unchanged |
| `ItemMobAspect` damage 0..16 basic, 21..37 condensed, 42..58 infused (17 aspects in the order of `NumericAspectHelper`; `aspectCount` was 21 with padding) | 51 items `mob_aspect_<tag>`, `mob_aspect_condensed_<tag>`, `mob_aspect_infused_<tag>`; `ItemMobAspect.ASPECTS` keeps the old order; the lang keys are the old ones (`item.mobAspect.condensed.name`, `item.mobAspect.infused.name`, `item.thaumictinkerer.mob_aspect` for the basic form) |
| `ItemMobDisplay` damage = index of the mob row | one item `mob_display`, the row in the component `thaumictinkerer:mob_display_mob` (section 2); not in the creative tab |

Neither the stacks nor the worlds of 1.7.10 are migrated (fresh worlds only).

## 2. Data on stacks (forced)

The stack tag of 1.7.10 is four typed data components (`MachineComponents`, registered from `Machines.register()`, each with
a codec for saving and a stream codec for the network). A value equal to the default is stored as no component, so a blank
mould, a blade switched off again and an unused soul aspect are the same stacks as new ones. None of them uses
`minecraft:custom_data` (the first version of the port did, with the old key names; stacks it saved are not converted, no
world of the port was released); the GameTest `theStackDataOfTheMachinesSurvivesSavingAndTheNetwork` saves, loads, sends
and receives each of them.

| 1.7.10 tag | 1.21.1 component |
|---|---|
| soul mould `pattern` (legacy entity name, `EntityList.getEntityString`, e.g. `Pig`) | `thaumictinkerer:soul_mould_pattern`: the registry key of the entity type (`minecraft:pig`) |
| mob display `type` (row name) | `thaumictinkerer:mob_display_mob`: the row of `EnumMobAspect`, saved as its name (`Wisp`), sent as its index |
| blade `Activated` (int 0/1) | `thaumictinkerer:blood_sword_activated`: boolean, absent = off |
| infused soul aspect `LastX`/`LastY`/`LastZ` | `thaumictinkerer:mob_aspect_last_tablet`: a `GlobalPos`, the tablet's position **and dimension** |

- **Soul mould.** The pattern is a key, not a holder: a mould keeps the pattern of an entity type that is not registered (a
  mod removed) and its tooltip shows the id; the tooltip names a known mob with the entity type's own description. The
  pattern is written to the stack the player holds (as `getCurrentEquippedItem` did), not to the stack the item callback
  receives: in creative mode `Player.interactOn` passes a copy there, which would lose the pattern. The Corporeal Attractor,
  its screen and the Jade tooltip read the component (the tooltip sends the id as text, as before).
- **Cursed Spirit's Blade.** The "active" icon (1.7.10: icon index by the NBT flag) is the item property
  `thaumictinkerer:activated` (`MachinesClient`, 1 while the component is set) and the override of `models/item/blood_sword.json`
  on it; the blade no longer sets `custom_model_data`, which a resource pack or another mod may want for itself.
- **Infused soul aspects.** A stack without the component matches every tablet, as a stack without a tag did in 1.7.10
  (`lastUsedTabletMatches`). **Deviation (fix):** 1.7.10 compared the coordinates only, so a set moved to a tablet at the same
  coordinates in another dimension counted as the same tablet and summoned at once; the port compares the dimension as well,
  so such a set waits one cycle like any set moved to another tablet. (1.7.10 also compared a stack that had a tag for another
  reason, a name, with the coordinates 0, 0, 0; the component has no such case.)
- Block entities save with `saveAdditional` / `loadAdditional` (`HolderLookup.Provider`), keys unchanged: `Items`
  (`ContainerHelper`), `tookLastTick`, `adultCheck`. The Attractor's polarity is not saved by the block entity any more,
  it is the block state.

## 3. Aspectalyzer

- **Always registered (choice).** The fork registered the block only with ComputerCraft loaded (`shouldRegister`). Without
  a computer mod the block would be unobtainable, so it is registered always; its research parent `PERIPHERALS` (also a
  CC-only entry in the fork) is created by `BlockAspectAnalyzer.ensurePeripheralsEntry()` if the lead's `ResearchHelper`
  has not registered it (a second registration of the key is ignored by Thaumcraft). See the shared-file requests.
- **Peripherals not ported (forced / scope).** ComputerCraft and OpenComputers are not in the 1.21.1 build. The four
  calls `hasItem`, `itemHasAspects`, `getAspects`, `getAspectCount` are plain methods of `TileAspectAnalyzer`
  (`hasItemMethod`, `itemHasAspectsMethod`, `getAspectsMethod`, `getAspectsAmountsMethod`); a compat module only has to
  call them. `getAspectsMethod` returns the tags sorted as the game sorts aspects.
- **Slot filter.** `SlotLimitedHasAspects` becomes "`ThaumcraftApiHelper.getObjectAspects(stack, level)` is not empty",
  which includes the contents of essentia containers and the bonus tags, as a scan does. The slot holds a stack of 64.
- **Scan check.** As in the original, the screen shows the aspects only if the player scanned the item:
  `AnalyzerKnowledge.hasScanned` is `ThaumcraftApiHelper.hasScannedItem(player, stack)`, which covers the `@` and `#`
  markers (a scan and an advanced scan) and works for the client's own player. The item is identified by its registry id,
  not by a hash of item and damage, because 1.21.1 has no metadata.
- **Aspect icons.** `UtilsFX.drawTag(x, y, aspect, amount, 0, zLevel)` is `AspectRenderer.draw(graphics, aspect, x, y, amount, 0)`.
- **GUI.** `openGui` with a GUI id becomes a `MenuType` (`ContainerAspectAnalyzer`) opened with `player.openMenu`;
  shift-click moves between the slot and the inventory. A click opens it also with an item in the hand, as the original.

## 4. Thaumic Restorer

- **Behaviour kept:** every 10 ticks one essentia is drawn from the tube on the front face when the neighbour outputs,
  sucks the aspect and sucks less than the repairer (suction 128 while the slot holds a repairable tool); instrumentum
  mends 8 points of durability, fabrico 5, ordo 3; 1.7.10's unordered `HashMap` becomes an ordered one (only one aspect can
  match, so the order is irrelevant); the redstone strong power 15 while the tool in the slot is damaged; hopper rules
  (accepts damaged tools, gives back mended ones); the tool is returned by a click on an occupied repairer whether
  mended or not.
- **What it mends (forced, data).** A damaged item (`isDamageableItem`, damage above 0) that is not in the item tag
  `thaumictinkerer:repairer_blacklist` (`TileRepairer.BLACKLIST`). 1.7.10 asked `Item.isRepairable()`, which `setNoRepair()`
  turned off; 1.21.1 has no such flag, so the items that called it are the tag: the Spellbinding Cloth (the Kami foci called it
  too, but have no durability). A blacklisted item is refused by a click and by a hopper, and one put in by other means sets
  no suction, so no tube gives essentia for it (as 1.7.10: suction 0); a hopper may take it out. The first version of the port
  had no such rule and mended the cloth. A pack may add items to the tag (EMI names it "Not Mended by the Thaumic Restorer",
  `lang-extra/machines.*.json`). GameTest `theRestorerNeitherTakesNorMendsTheSpellbindingCloth`, JUnit
  `theRestorerBlacklistHoldsTheSpellbindingCloth`.
- **Saving and syncing (fix of the port).** A work cycle saves the block entity and sends it to the clients only when it
  mended something (once, with the new damage and the flag) or when the flag of the sparkles changed (1.7.10
  `markBlockForUpdate` on a change of `tookLastTick`). The first version saved and sent the Restorer every ten ticks while a
  damaged tool sat in it without essentia, and sent it twice in a cycle that changed the flag. 1.7.10 itself called `markDirty`
  (a save, no packet) every cycle with a damaged tool; the port does not save a cycle that changed nothing. GameTest
  `theRestorerSavesAndSyncsOnlyWhenSomethingChanged` counts the saves of a Restorer ticked by hand (one in ten cycles without
  essentia, for the flag; none for a blacklisted item or an empty slot). The number of block updates sent is not counted
  (a GameTest cannot see it); every one of them goes with a counted save.
- **TConstruct tools and the `repairTConTools` option are not ported** (the compat is not in the 1.21.1 build). All tools
  are plain damageable items.
- **Redstone signal changes (fix).** 1.7.10 never told the neighbours when the signal changed, so a lever-like device
  showed the new level only with its next block update. The port updates the neighbours when the tool becomes damaged or
  mended.
- **`getAspects()` of an empty repairer** returns an empty list instead of `null` (goggles read it every frame). Stacks
  that are in the slot still show `ENTROPY = damage`, also at 0.
- **Update packet.** The slot and `tookLastTick` are sent with the standard block entity update packet and update tag.
- **Sparkles.** `ThaumcraftClientFX.sparkle(level, x, y, z, 0)` (violet) every 10 ticks while `tookLastTick`, at the same
  random point above the block as the original.
- **Not a redstone conductor (fix).** 1.7.10 `isNormalCube()` was false for the Restorer (`renderAsNormalBlock()` false,
  `canProvidePower()` true), so a repeater or a lever pointing into it did not power it or the wires beside it. The 1.21.1
  default (`isRedstoneConductor` = full collision shape) would make it a conductor, so the block properties switch that
  off. The Funnel stays a conductor, as in 1.7.10 (a normal cube there); the Attractors and the Tablet have partial shapes.
- **Model and renderer.** The frame and the glass (`ModelRepairer`) are a generated JSON model (`repairer`, from the box
  list of the Java model), textured with `textures/models/repairer.png` and rotated by the blockstate, in the translucent
  render layer. `RenderTileRepairer` is the block entity renderer and draws what the 1.7.10 tile renderer drew besides the
  box: the tool turning and bobbing inside the glass and the magic circle on top (the circle texture changes with
  `tookLastTick`). The 1.7.10 inventory renderer called the tile renderer; the block item of 1.21.1 is the static box
  model, so the inventory icon has no circle and no tool.

## 5. Essentia Funnel

- **Behaviour kept:** the funnel needs a hopper under it (`canSurvive`, and it pops out as an item when the hopper
  goes); every tick it moves one essentia point from its jar into the jar the hopper points at, if that jar is empty and
  its label allows the aspect, or holds the same aspect and has room (or is a void jar); the hopper cannot take the jar out
  from below; a click puts a filled jar in or gives back the slot; shape 2 pixels high for the outline and a full block
  for collisions (`getCollisionBoundingBoxFromPool`).
- **Crash fixed (fix).** The original read `getAspects()[0]` of an empty list in two places: for an empty target jar
  whose label is another aspect (the first condition fails and the second operand is evaluated) and for the funnel's own
  jar right after its last point left (`AspectList.remove` drops the key at zero). Both throw
  `ArrayIndexOutOfBoundsException` in the block entity tick. This was found by reading the code, not by running 1.7.10.
  The port tests for emptiness first: a jar labelled for another aspect simply receives nothing.
- **Jar swap is the jar's business (forced).** 1.7.10 replaced an emptied unlabelled `ItemJarFilled` by an empty jar block
  item in `TileFunnel`. In 1.21.1 the jar is one item whose `setAspects(stack, empty)` clears the contents and keeps the
  label; an unlabelled emptied jar is a plain stackable jar again, a labelled one stays a one-item stack. The funnel's
  `isFilledJar` is "holds essentia, or does not stack".
- **Jar identification** goes through ids and `IEssentiaContainerItem` (request 1 of the API requests).
- **Block entity API.** `ISidedInventory` becomes `WorldlyContainer`; `IAspectContainer` is read-only (shows the jar's
  aspects to goggles), as in 1.7.10.
- **AE2 `IMovableTile` is not ported.**
- **Model and renderer.** JSON model `funnel` (from `ModelFunnel`), plus `RenderTileFunnel` for the jar standing on the
  funnel. 1.7.10 called Thaumcraft's `BlockJarRenderer` / `ItemJarFilledRenderer`; the port draws the jar item with
  `ItemRenderer.renderStatic`, which uses whatever renderer Thaumcraft registers for it. **Seen** in the client harness
  (`renderer-funnel.png`): a Thaumcraft jar with five points of ignis, the glass shell with its cork lid and the essentia at the
  bottom, standing on the funnel frame (the harness put a gold ingot there in its first run, which the funnel never accepts and
  which only showed the generic item model).

## 6. Attractors

- **Kinetic Attractor (`TileMagnet`).** Same loop as the original on both sides: redstone is the strongest neighbour signal
  (`Level.getBestNeighborSignal`), the half width of the box is `signal / 2` (integer division, so 0 and 1 do nothing
  useful), the box spans the range to every side horizontally, from the block's lower edge up to the range above its centre,
  pulling stops within one block (squared distance 1), pushing always moves, speed 0.25 along the vector to the block centre, normalised
  when longer than one block. Dropped items are measured from their centre: 1.7.10 positioned items at their centre
  (`posY` included `yOffset`), 1.21.1 at their feet, so the port adds half the height for items only. Both sides run, as
  before; the client only adds `ThaumcraftClientFX.sparkle` on every entity it moves (type 2 pulling, 4 pushing, at the entity's
  position as the original; a dropped item at its centre).
- **Corporeal Attractor (`TileMobMagnet`).** Selects living entities that are not players; ageable mobs only when the
  adult flag matches `isBaby()`; with a soul mould in the slot only entities of the mould's type
  (`BuiltInRegistries.ENTITY_TYPE` id against the pattern). A mould without a pattern selects nothing, as in 1.7.10
  (`name.equals("")`). 1.7.10's `EntityAgeable` is `AgeableMob`; a baby zombie, which is not ageable in either version,
  is always selected.
- **Menu instead of GUI + packet (forced).** The adult / child buttons were `GuiButtonMMRadio` and the packet
  `PacketMobMagnetButton`. They are menu buttons (`ContainerMobMagnet.clickMenuButton`, ids 0 and 1) and a `ContainerData`
  slot that carries the flag to the client; the screen is `GuiMobMagnet`. Right click with a wand opens it (the sound
  `thaumcraft:key` at pitch 0.5, as the original); a right click without a wand changes the polarity and plays the
  button click.
- **Peripherals not ported:** `isPulling`, `setPulling`, `getSignal` of ComputerCraft / OpenComputers; the methods exist
  (`isPulling`, `setPulling`, `getRedstone` on `TileMagnet`, `setAdult` on `TileMobMagnet`).
- **Recipes.** The arcane recipes keep their keys (`MAGNET`, `MOB_MAGNET`, research `MAGNETS`); the research is carried only
  by the plain block (`getResearchItem` of the mob block returns null) so that it is registered once. The mob magnet's
  ingredient `RecipeHelper.oreDictOrStack(gold ingot, "ingotCopper")` is "the copper ingot if the ore dictionary has one":
  in 1.21.1 vanilla has copper, so it is always the copper ingot (`"ingotCopper"`, resolved to the `c:` tag by the API).
  The focus of telekinesis is `TTRegistry.stack("focus_telekinesis")` (another group).
- **Models.** The static `ModelMagnet` (blue texture while pulling, red while pushing; the Corporeal Attractor has its own
  two textures) is a JSON model per polarity (`magnet_blue/red`, `mob_magnet_blue/red`), `noOcclusion`, in the cutout
  layer. The three rings of lightning above it (`iconLightningRing` of Thaumcraft's hover harness, blue or red, size by the
  redstone level, drifting with `cos(time / 500)` on the game's clock since section 13, nothing without a signal) are drawn by `RenderTileMagnet<T>`; the
  alpha and the blending are approximated with the translucent entity render type. The 1.7.10 inventory renderer drew
  the tile renderer at full signal; the 1.21.1 block item is the static model without rings.
- **Drops.** Breaking a Corporeal Attractor scatters the mould like `breakBlock` did (`MachineDrops.scatter`); the two
  magnets drop themselves through loot tables (`damageDropped` of the metadata is gone).

## 7. Tablet of Necromancy, soul items, mob display

- **Cycle kept:** the tablet works every 300 ticks of world time unless it is powered by redstone, looks for arcane
  pedestals holding soul aspects at the height of the tablet in growing squares (radius 1 to 5), finds the triple of the
  original order, matches the three aspects as a set against the 30 rows of `EnumMobAspect`, and either consumes the
  three soul aspects (plain or mixed sets) or, for three infused ones, runs only when the world time is a multiple of 1200
  and keeps them, marking them with the tablet; a set that was last marked by another tablet summons nothing but is
  marked again (the 1.7.10 behaviour, `TileSummon` lines 63 to 128; "another tablet" includes the dimension now, section 2).
- **The search (performance fix, same result).** 1.7.10 (and the first version of the port) scanned the square of every
  radius anew and tried every ordered triple of the pedestals, looking each pedestal up in the world again for every triple
  (the port also resolved the pedestal block from the registry for each look-up): with a dozen pedestals some 9,000 look-ups a
  cycle. The port now looks up each position of the square of radius 5 once (121 look-ups, the pedestal block resolved once
  per cycle), takes the pedestals of each radius from that list in the order 1.7.10 scanned that square (x, then z), skips a
  radius that adds no pedestal, and tries each unordered triple `i < j < k` once. Since the aspects are compared as a set, the
  first unordered triple is the first ordered triple of 1.7.10 with its pedestals in the same order: the same mob, the same
  pedestals used up, the essentia trails in the same order. Checked: the JUnit test
  `theUnorderedTriplesFindTheFirstOrderedTripleOfTheOriginal` compares both loops on 1,500 random lists;
  `eachSquareTakenFromTheScanHasTheOrderOfTheOriginalScanOfThatSquare` the order of each square; the GameTest
  `theTabletPicksTheSameSetAsTheOriginalSearch` compares the tablet with a copy of the old search in the world for 44 layouts
  (hand-made and seeded random; 31 with a match) and lets each matching cycle summon (the row's mob, exactly those three
  pedestals used up); `theTabletLooksUpEveryPositionOnceACycle` counts 121 look-ups for twelve pedestals where the old search
  made 8,925.
- **Rows by entity type (forced).** Entity classes become entity types (each row holds the `ResourceKey` of its type; the type
  itself is looked up when needed). 1.7.10's ocelot row
  covers the cat; the horse row covers horse, donkey, mule, skeleton horse and zombie horse; `PigZombie` is the zombified
  piglin; the skeleton row is the plain skeleton and the wither skeleton row the wither skeleton (1.7.10 had one class with
  a type); Thaumcraft's mobs (`thaumcraft:wisp`, `firebat`, `brainy_zombie`, `giant_brainy_zombie`) are recognised by
  registry key (`ThaumcraftEntities.WISP`, `FIREBAT`, `BRAINY_ZOMBIE`, `GIANT_BRAINY_ZOMBIE`, tested with `ThaumcraftEntities.is`)
  because their classes are not API. The "must be before" order comments of the original are kept
  for the subclasses (mooshroom before cow, magma cube before slime, cave spider before spider, brainy zombie before
  zombie).
- **Spawning.** `onSpawnWithEgg(null)` becomes `finalizeSpawn(..., MobSpawnType.SPAWN_EGG, null)`; the wither skeleton
  gets its stone sword from the game itself (1.7.10 had to set it by hand because the egg gave a bow); slimes and magma
  cubes are set to size 1 after `finalizeSpawn` (which randomises the size); `playLivingSound` is `playAmbientSound`. The
  mob appears at `x + 0.5, y + 1, z + 0.5` of the tablet.
- **Essentia trails** are sent by `ThaumcraftFX.essentiaTrail` (server to clients), once per consumed pedestal, from the
  pedestal to the tablet.
- **Pedestals** are the arcane pedestal of Thaumcraft (`BlocksTC.get("arcane_pedestal")`), read as a `Container`.
- **Soul aspect recipes (choice of names).** The 1.7.10 research key `SUMMON1` (a crafting-table recipe of nine plain soul
  aspects into one condensed, registered once per aspect under the same key) is one data recipe per aspect,
  `summon1_<tag>.json`, plus `summon1.json` for the one the research page shows. As in the original, where the last
  registration (`CROP`, tag `messis`) won the key, `summon1.json` is the messis recipe. Names are lowercase because
  resource locations must be. The infusion recipe per aspect (8 condensed around 1 condensed, instability 4,
  10 of the aspect) and the object tag of the basic form (8 of the aspect) are registered as before.
- **Mob display.** `RenderMobDisplay.render` draws the mob in place of the item (model `minecraft:builtin/entity`; 1.7.10
  `MinecraftForgeClient.registerItemRenderer`). Architectury has no registry for a built-in item renderer, so the group uses
  the loader layer's `ItemRenderers` (`docs/HOOKS.md` section 9): `MachinesClient.register()` calls, as its first statement,
  `ItemRenderers.register(TTRegistry.item("mob_display"), RenderMobDisplay::render)` (through the helper
  `MachinesClient.registerItemRenderers(Item)`). On Fabric `FabricItemRenderers` (the backend, installed before the groups
  register their client side) hands it to Fabric API's `BuiltinItemRendererRegistry`; the group's own glue of the first version
  (`MachinesFabricClient`, reached by name from common code, with a second registration of the same item) and the reflection
  that called it are gone, since two registrations of one item are an error. On NeoForge `NeoForgeItemRenderers` (the
  backend, installed from the mod constructor, before the shared client setup runs inside `RegisterClientExtensionsEvent`)
  collects the queue and, after that setup, registers one `IClientItemExtensions` per item whose custom renderer is a
  `BlockEntityWithoutLevelRenderer` handing `renderByItem` to `RenderMobDisplay.render`. The model needs nothing more there:
  NeoForge's `ItemRenderer` asks the item's extensions for that renderer for every `builtin/entity` model (read in the 21.1.259
  patches). That backend compiles, and the NeoForge client smoke drew the Mob Display of every mob through it in the item grid (2026-10-31; the
  pictures equal Fabric's in all but 0.01 % of the pixels, `docs/CLIENT_VERIFICATION.md`). What was checked: the code compiles; a JUnit test
  (`MachinesClientWiringTest`) runs the helper with an item of its own and reads the queue (`ItemRenderers.get`: queued for that
  item and no other, a second registration refused) and reads the source of the registrar (`register()` queues
  `TTRegistry.item("mob_display")` first, once; no other Fabric class uses `BuiltinItemRendererRegistry`); the Fabric backend
  was read (`FabricItemRenderers.install()` forwards `renderer::render` to `BuiltinItemRendererRegistry.register(item,
  DynamicItemRenderer)` of fabric-rendering-v1 5.2.1, whose signature is the same six arguments). A GameTest server cannot do
  more (it cannot load `PoseStack`). NEI/JEI-style "necromancy recipes" for recipe viewers are not provided.
  - **Slot pose (fix of the first client run).** The 1.7.10 renderer declared `shouldUseRenderHelper(INVENTORY_BLOCK)` for every type,
    so Forge drew the mob the way it draws a block icon: 10 pixels per unit, `rotate(210, X)`, `rotate(45, Y)` and a flipped Z on a
    Y-down screen, and only then the renderer's own `rotate(-30, X)`. Written in the Y-up frame that 1.21.1 hands to a builtin
    model that is a 30 degree tilt about X and a 45 degree turn about Y at 0.625 of the 16 pixel unit. The first port skipped that
    set-up (no `display` in the model, so 16 pixels per unit and no turn): the mobs were 1.6 times as large as the original,
    overran their neighbours, and the four-legged ones stood in a head-on view that looked upright. `models/item/mob_display.json`
    now carries `display.gui = {rotation [30, 45, 0], scale 0.625}`, and `RenderMobDisplay` keeps its own -30 degrees and the
    `EnumMobAspect` scales unchanged. Derived from the 1.7.10 GL calls by hand, **not compared with a picture of the original**; some
    mobs are tall for a slot, as the unchanged scale values say they were in 1.7.10 too (not verified against it).
  - **Slot fit of three mobs (decision on the owner's request, 2026-11-01).** In the harness picture `items-machines-02.png` the Horse's
    head, the Witch's hat and the Mooshroom's mushrooms reached 4.75, 3.75 and 3 slot pixels above the 16 x 16 slot (measured on the
    picture: one slot pixel is four screen pixels there). In a slot (`ItemDisplayContext.GUI`) only, `RenderMobDisplay` now draws these
    three smaller about the middle of the slot and lower (`SLOT_FIT`: Horse 0.74 and 1.94 slot pixels, Witch 0.75 and 1.41, Mooshroom 0.80
    and 1.30), which puts each of them from 0.5 to at most 15.5 slot pixels, inside the slot. The `EnumMobAspect` scales stay the
    original's, and every other mob and every other place (hand, ground, frame) is drawn as before; to undo it, remove the three rows. The
    other mobs were measured too and left alone as asked: the Wither Skeleton reaches 2 slot pixels above the slot, the Enderman 1.75, the
    Angry Zombie 1.5, the Cow 1, the Zombified Piglin 0.75, the Villager, Zombie, Skeleton and Firebat 0.5, the Snow and Iron Golem 0.25,
    the Bat 1.5 to the right, the Spider 0.75 to the right and the Ocelot 0.5 to the left; the same table of `SLOT_FIT` would take them in.
    Checks: JUnit `MachinesClientWiringTest.onlyTheHorseTheWitchAndTheMooshroomAreFittedIntoASlot` (which mobs and contexts, and the
    measured extent inside the slot), and the client smoke of both loaders (2026-11-01: Fabric 727 PASS, NeoForge 731 PASS, 0 FAIL): the
    pictures were read, the three mobs lie inside their slots, the other 27 cells are the same as before to the pixel on Fabric except the
    Blaze (82 pixels, its rods turn), and the NeoForge picture equals the Fabric one in all but 840 of 921600 pixels (the Mooshroom and the
    Snow Golem).
  - **No shadow, no hitbox (choice).** `EntityRenderDispatcher.render` draws a shadow (on the blocks of the level around the
    cached mob's own position, the world origin) and, with F3+B, a hitbox; 1.7.10 called `doRender`, which draws neither.
    `RenderMobDisplay` switches both off around the call and restores them. Defensive: the harness world is a flat one whose ground is
    far below the origin (read from the set-up, not measured), so a stray shadow was **not seen** before or after.
  - **Particle icon (choice).** 1.7.10 gave the item no texture (`IconHelper.forItem` named `ttinkerer:mobDisplay`, which does not
    exist), so its break and eat particles were the missing texture there too. The model now names the Soul Mould texture
    (`textures.particle`); the harness's `missing_texture` failure of the item is gone.
  - **A Mob Display without a mob** (a stack without the `type` key, shown as "Unknown Mob") draws nothing, as 1.7.10 did (`aspect ==
    null` returns); the item is not in the creative tab. Kept.
  - **Seen:** in a slot, on a software renderer, by the client harness (`items-machines-02.png`, 30 mobs, English and Russian
    names). **Not seen:** in the hand, on the ground, in an item frame (the model has a `display` for the slot only, so those
    use the builtin default: the mob at the size of a block) and in a recipe viewer.
- **Tooltip of a soul aspect** shows the aspect name as the game spells it (`Aspect.getName()`); the infused ones glow.

## 8. Cursed Spirit's Blade

- **Numbers kept:** 10 attack damage, +25 % movement speed (`ADD_MULTIPLIED_BASE 0.25`) while held, 950 uses, harvest
  level 0, enchantability of thaumium, repairable by Thaumcraft's rule (`IRepairable`), infusion recipe and research as
  listed in section 9. The shift-right-click message keys are the old ones (`ttmisc.bloodSword.activateEssentiaHarvest` and
  `deactivateEssentiaHarvest`).
- **Blocking (forced).** `itemInUse` / `EnumAction.block` become `UseAnim.BLOCK` with a long use duration; every right
  click raises the blade, as in 1.7.10. A player that uses the blade when a blow comes takes 3 magic damage and the blow is
  cancelled; whoever strikes with the blade takes 2 magic damage.
- **`/kill` and the void are not blocked (fix).** Damage in `#minecraft:bypasses_invulnerability` (`generic_kill` of `/kill`,
  `out_of_world` of the void) is neither cancelled nor turned into magic damage. 1.7.10 cancelled `DamageSource.outOfWorld` as
  well, so a player blocking with the blade survived `/kill` (with 3 points of magic damage) and took 3 instead of 4 from the
  void. GameTest `aBlockingWielderIsNotSavedFromKillOrTheVoid` (the void through the real `hurt`; `/kill` by the handler's
  answer only, a real `/kill` of the mock player is not run).
- **Events.** `LivingAttackEvent` is `EntityEvent.LIVING_HURT` (Architectury; forced: it is the nearest event). 1.7.10
  stopped its own nested damage by skipping the next three events of the whole world (`handleNext = 3`), which also swallowed
  unrelated damage; the port skips exactly the damage the handler itself deals (a flag held while it hurts the wielder).
- **Drop replacement (loader hook `LIVING_DROPS`).** `LivingDropsEvent` is `TinkererEvents.LIVING_DROPS` (`docs/HOOKS.md`
  section 8); `ItemBloodSword.onLivingDrops` is registered once by `Machines.register()` (through
  `ItemBloodSword.registerEvents()`) and is the 1.7.10 handler: a mob (`Mob`, 1.7.10 `EntityLiving`) killed by the damage type
  `player_attack` (1.7.10 `damageType.equals("player")`: the melee blow of a player, not an arrow or magic) whose killer holds
  an activated blade in the **main hand** (`getCurrentEquippedItem`) and that has a row in `EnumMobAspect` loses every drop
  (`event.drops.clear()`: the loot table **and the equipment** it drops) and drops one soul aspect per aspect of its row at its own
  position with a pick-up delay of 10 ticks. A mob without a row, another kind of death, a bystander's blade, a blade in the off
  hand and a blade that is not activated leave the loot alone (GameTests). `lootingLevel` and `recentlyHit` are ignored, as the
  1.7.10 handler ignored its event's Looting. Differences forced by the event contract, all written down:
  - **When.** The soul aspects are in the world in the call of the death, as in 1.7.10 (the GameTest reads them right after the
    killing blow); the first version dropped them at the end of the level tick. The death event, the note of the victim, the
    add-entity filter and the end-of-tick pass are deleted.
  - **What is replaced.** The list holds the item entities that the victim itself spawns while its death loot is worked out
    (`spawnAtLocation`: loot table, equipment) and nothing else (`docs/HOOKS.md` section 8); experience orbs are not item entities
    and stay, as in 1.7.10. An item that another mod spawns somewhere else with `addFreshEntity` is not in the list and stays. The
    workaround refused every item entity that appeared at the victim's position, whoever spawned it.
  - **More kills count than in 1.7.10.** `Player.attack` builds its damage source once (`damageSources().playerAttack`) and hurts
    the other mobs of a sweeping attack of a sword with it as well (read in the 1.21.1 bytecode, **not run**: no GameTest drives a
    sweeping attack), so a mob killed by the sweep of the blade reaps too (1.7.10 had no sweeping attack). What a GameTest does
    drive is the plain swing: `aRealSwingOfTheActivatedBladeReapsAndHurtsItsWielder` calls `ServerPlayer.attack(pig)` of a Survival
    player (not a damage source made by hand), so the `player_attack` source is the one `Player.attack` builds, and the blade's own
    `LIVING_HURT` handler hurts the wielder by 2 in the middle of the blow without disturbing the three soul aspects.
  - **Order.** Listeners run in the order of registration: the blade's runs before `DimensionalShardDropHandler` (KamiWorld;
    `Machines` is before `KamiWorld` in `ContentGroups.ALL`, and both register in `register()`), so an Enderman or Zombified
    Piglin reaped in the End or the Nether still rolls its dimensional shard, which is added to the list after the soul aspects.
    The blade never cancels the drops. This is pinned by `aReapedEndermanAndZombifiedPiglinStillRollTheirDimensionalShard`: real
    deaths (`Mob.die`) of an Enderman in the End and of a Zombified Piglin in the Nether, the dice of the shard made certain, the
    killer a player with the activated blade; a listener of the check, registered after both, reads the list and cancels it (the
    End and the Nether stay empty). It requires exactly three soul aspects of the row and then the shard (the helmet or the sword
    that the mob always drops is gone), and, with a player without the blade as control, the equipment and the shard both there.
    Were the blade registered after the shard handler, `drops.clear()` would delete the shard; moving `registerEvents()` into
    `ready()` (after every group's `register()`) made exactly this check fail ("three soul aspects and one shard ...: [three soul
    aspects]").
  - **Sides.** The event is server only, so the handler needs no side test; the soul aspects are made by the server.
  **The group needs the poster of `LIVING_DROPS` on every loader**: on Fabric `LivingEntityDropsMixin` with the capture mixins, on NeoForge
  `NeoForgeEventPosters.onLivingDrops` (a handler of `LivingDropsEvent`, `docs/HOOKS.md` section 8, written 2026-10-31). Without it the blade
  would not reap, and a mob it kills would drop its loot. The group's 47 GameTests, the reaping checks and the order against the shards
  among them, passed on a NeoForge GameTest server (`docs/HOOKS.md` section 9; server side only); after the review fixes of
  2026-11-01 the group's 53 GameTests passed on the Fabric and on the NeoForge GameTest server (with BasicItems, RecipeViewers
  and Tooltips, filtered run).
- **Model.** `blood_sword_0` / `blood_sword_active` with the override on the item property `thaumictinkerer:activated`
  (section 2). The client harness checks that the predicate has a property function and draws the activated variant
  (`ItemVariants` sets the component); not run since the change.
- **Enchanting (as in 1.7.10).** The 1.7.10 blade was an `ItemSword` without overrides of the enchantability (thaumium's) or of the
  book check, so the table (`EnchantmentHelper.mapEnchantmentData`) and the anvil (`Enchantment.canApply`) gave it every enchantment of
  `EnumEnchantmentType.weapon` (`item instanceof ItemSword`: Sharpness, Smite, Bane of Arthropods, Knockback, Fire Aspect, Looting) and of
  `breakable` (Unbreaking); Vampirism refused it (`EnchantmentVampirism.canApply`). Here the blade stays out of `#minecraft:swords` (the
  tag of Vampirism holds it) and is listed in the item tags those enchantments ask, `minecraft:enchantable/sword`, `sharp_weapon` (and so
  `weapon`), `fire_aspect` and `durability` (and so `vanishing`), files of the Enchantments group (`Enchantments.md` section 5). So it also
  takes Sweeping Edge, Mending and Curse of Vanishing, which 1.7.10 did not have, as any sword. The section of this document that said the
  blade could not take them was out of date (the tags were in the first import); the GameTest
  `theBladeTakesTheSwordEnchantmentsAtTheTableAndTheAnvil` checks the table's list at a power of 30 and a book of each in a real anvil menu,
  and the refusal of Vampirism (`docs/DEVIATIONS.md` section 6, point 7).

## 9. Values checked against the original on purpose

Block settings: Aspectalyzer wood 1.7 / 1; Restorer iron 5 / 10; Funnel stone 3 / 8; Attractors iron-material (needs a
pickaxe) 1.7 / 1 with the wood step sound; Tablet iron 3 / 50 with the metal sound. Tools: the Aspectalyzer is mined with
an axe, the others with a pickaxe of any tier (`mineable/machines/*`; the `needs/machines` tags are empty because the
iron material in 1.7.10 required a tool but no level).

Research (key, coordinates, complexity, flags): `ASPECT_ANALYZER` 0,1,2 round concealed, parents `PERIPHERALS`, hidden
`GOGGLES`, `THAUMIUM`; `REPAIRER` -1,-9,3 concealed, parent `FUNNEL`, hidden `THAUMIUM`, `ENCHFABRIC`; `FUNNEL` 0,-7,1
concealed secondary, parent `BRIGHT_NITOR`, hidden `DISTILESSENTIA`; `MAGNETS` -6,3,3 concealed, parent `INTERFACE`,
hidden `FOCUS_TELEKINESIS`; `SUMMON` -5,8,3 warp 3, parent `BLOOD_SWORD`; `BLOOD_SWORD` -4,6,3 warp 1 secondary, parent
`CLEANSING_TALISMAN`. Recipes: Aspectalyzer arcane (order 1, entropy 1) `TWT/WMW/TWT`; Funnel arcane `STS`; Restorer infusion
instability 8 (tool 15, craft 20, order 10, magic 15); Magnets arcane (air 20, order 5, earth 15, entropy 5); Tablet arcane
(order 50, entropy 50) `WWW/SSS`; Blade infusion instability 6 (hunger 20, darkness 5, soul 10, man 6); soul mould
crucible (beast 4, mind 8, senses 8) on an ender pearl under the key `MAGNETS`.

## 10. Resources and files that are not only this group's

- `assets/minecraft/atlases/blocks.json` stitches the five entity-style textures that the JSON models use
  (`textures/models/repairer`, `magnet_n`, `magnet_s`, `mob_magnet_n`, `mob_magnet_s`) into the block atlas. Atlas
  definitions of all packs are merged, but two groups must not both create this file in the same jar: see the
  shared-file requests.
- Lang: the base translations carry all 1.7.10 texts, and `en_us` already has `item.thaumictinkerer.mob_display`
  ("Unknown Mob"). `lang-extra/machines.<lang>.json` adds that one key for `ru_ru`, `de_de`, `fr_fr`, `ko_kr` and `zh_cn`
  (the fr, ko and zh texts are the port's own translation of two words and should be checked by a native speaker), and the
  name of the item tag `tag.item.thaumictinkerer.repairer_blacklist` for `en_us` and `ru_ru`.
- `data/thaumictinkerer/tags/item/repairer_blacklist.json` names the Spellbinding Cloth of the BasicItems group by its id.

## 11. Not done / not ported

- ComputerCraft and OpenComputers peripherals (Aspectalyzer, Attractors), AE2 `IMovableTile`, Tinkers' Construct
  tool repair. (NEI integration and Waila providers were not this group's job; they are ported now as EMI/JEI pages and Jade
  tooltips, `RecipeViewers.md`, `Tooltips.md`.)
- (The enchantability of the blade, listed here before, was there all along: section 8.) (The NeoForge poster of `LIVING_DROPS`, listed here
  before, is written now, section 12; the NeoForge backend of `ItemRenderers` is written, not seen drawn.) (The scan
  check, the sparkles, the aspect tag drawing and the Thaumcraft entity keys use the API since the review.)
- Recipe-viewer pages for the necromancy recipes: done since 2026-10-31 as an EMI and a JEI category (`RecipeViewers.md`).

## 12. What the group needs from the loader modules

| Hook | Used by | Fabric | NeoForge |
|---|---|---|---|
| `TinkererEvents.LIVING_DROPS` (`docs/HOOKS.md` section 8) | `ItemBloodSword.onLivingDrops` (the Cursed Spirit's Blade) | posted by `LivingEntityDropsMixin` and the capture mixins; run by the GameTests of the blade | `NeoForgeEventPosters.onLivingDrops` (a `LivingDropsEvent` handler calling `TinkererEvents.postLivingDrops`; no capture mixins); run by the same GameTests on a NeoForge GameTest server |
| `ItemRenderers` (`docs/HOOKS.md` section 9) | `MachinesClient.register()` (the Mob Display) | `FabricItemRenderers`; run in the client harness (the mobs are drawn in a slot) | `NeoForgeItemRenderers` (an `IClientItemExtensions` per item in `RegisterClientExtensionsEvent`); compiled, installed in a NeoForge dev client that started without an error from the add-on; not seen drawn (no world entered) |

## 13. 1.21.1 technique instead of 1.7.10 leftovers (2026-11-01)

The modernization of the group (`docs/AUDIT.md`, "Modernization (packages C and D)"). Gameplay is the original's; only the third point is
visible, and only as an animation.

- **Thaumcraft's ingredients by their native ids.** The recipes named Thaumcraft's items by their 1.7.10 field and metadata through the API's
  legacy table (`ItemApi.getItem("itemResource", 2)`); they name the 1.21.1 items now (`ItemsTC.stack("thaumium_ingot")`), the same stacks
  (`docs/api/LEGACY_IDS.md` of Thaumcraft 4 Reload): Kinetic and Corporeal Attractors `earth_shard`, `air_shard`, `greatwood_log`; Essentia
  Funnel `thaumium_ingot`; Thaumic Restorer `thaumium_block`, `enchanted_fabric`, `thaumium_ingot`; Tablet of Necromancy `obsidian_tile`;
  Aspectalyzer `greatwood_planks`, `thaumometer`, `thaumium_ingot`; Cursed Spirit's Blade `thaumium_sword`. The parity check against the
  1.7.10 recipes (`tt_oracle`) passes unchanged.
- **The wand's sound on the Corporeal Attractor** is Thaumcraft's registered `thaumcraft:key` event (`ThaumcraftSoundIds.event`). The port
  made a new, unregistered `SoundEvent` of the id on every click, which the sound packet then carried as a whole event instead of its id; the
  sound, volume 1 and pitch 0.5 are the same (GameTest `aWandOpensTheCorporealAttractorWithThaumcraftsRegisteredKeySound`, which reads the
  packet the server sends).
- **The rings above an attractor follow the game's clock (choice, visible).** 1.7.10 and the port until now bobbed the rings by
  `cos(System.currentTimeMillis() / 500F)`. The division is in `float`, which rounds today's epoch milliseconds (between 2^40 and 2^41) to steps of
  131,072 ms: the rings stood still and jumped to another spacing about every two minutes, in 1.7.10 too. The renderer now takes the level's game
  time and the partial tick in `double`, `cos(ticks / 10)` (10 ticks are the 500 ms of the formula at 20 ticks a second), so the rings bob as the
  formula says, stop while the game is paused and follow `/tick`. Size, colour and spacing range are unchanged.
- **The Funnel knows the jars by the API's items** (`BlocksTC.jar()`, `BlocksTC.voidJar()`) instead of the id strings `thaumcraft:jar` and
  `thaumcraft:jar_void`; still only items that hold essentia (`IEssentiaContainerItem`), so the crystal and the node in a jar are no jars for it
  (GameTest `theFunnelKnowsTheTwoJarsOfTheApiAndNoOtherHolderOfEssentia`).
- **The infused soul aspects glint by their item's default component** `minecraft:enchantment_glint_override` (as vanilla's enchanted golden
  apple), not by an `isFoil` override: the form is an item of its own, so the glint is a constant of the item. A saved stack does not carry the
  default; a stack may switch it off (GameTest `theInfusedSoulAspectsGlintByTheirItemsDefaultComponent`).
- **Unused 1.7.10 constants deleted:** `LibResources.PREFIX_LANG`, `EMTPY_TEXTURE`, `NEI_RECIPE_ARROW`, the model texture paths of the
  magnets, the repairer, the Ichor cap and rod and the armour layers (1.21.1 armour textures come from `ArmorMaterial.Layer`), and the icons of
  Potency, Frugal and Treasure (no such enchantments in the table); `LibBlockNames.*_SLAB_FULL` (1.21.1 slabs have `type=double`),
  `LibItemNames.KAMI_RESOURCE` (the metadata item) and `SWORD` (`mob_sword`). The textures stay in the assets. `tt_oracle/provenance.json`, which
  records the hashes of the two `Lib*Names` files, was regenerated with `scripts/porting/tt_oracle.py`; the research and recipe tables of the oracle
  did not change. (`LibResources.MODEL_RELAY_TEXTURE` went at the merge with package B, which deleted `RenderTileRelay`; `LibBlockNames.CAMO` is named by no
  code either, its value is the id of the camouflage block entity, which `SimpleBlocks` spells out; left for its owner.)
