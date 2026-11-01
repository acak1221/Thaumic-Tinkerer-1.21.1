# TileBlocks: deviations from Thaumic Tinkerer 1.7.10 (GTNH fork `28f4def9`)

Group TileBlocks: Animation Tablet (Tool Dynamism Tablet), Remote Placer, Levitational Locomotive and its Relay, Golem
Connector, Transvector Interface, Transvector Dislocator, and the Transvector Binder (`ItemConnector`).

Everything listed here is a place where the port does something other than the original, with the reason. A value that is
not listed (a research position, an aspect, a recipe, a hardness, a sound, a message key, a range, a tick interval) is the
original's. Each entry says whether it is **forced** (1.21.1 or the loader-neutral rule cannot do it), a **fix** (a
reproducible defect of the original, with the evidence), or a **decision** (the port chose between two readings and the lead
may overrule it). Nothing here was run in a game; see the end for what is unverified.

## 1. Mappings that every entry below relies on (forced)

| 1.7.10 | 1.21.1 |
|---|---|
| block metadata: facing (`orientation` field, or metadata 2..5) | block state property `facing` (`FACING`, six ways for the Remote Placer and the Dislocator, `HORIZONTAL_FACING` for the Tablet). `Direction.get3DDataValue()` equals `ForgeDirection.ordinal()`, so the saved `Direction` of the locomotive and the arithmetic of the placer are unchanged |
| metadata bit 8 of the Tablet, metadata 1 of the placer/dislocator ("powered") | block state property `powered` |
| `TileCamo` (block id + metadata of the camouflage) | `CamoDeviceBlockEntity`: the whole `BlockState`, saved as a block-state tag under `camo`; the block's own boolean property `camo` mirrors "has a camouflage" |
| "not bound" = `y == -1` of `TileTransvector` (keys `xt`, `yt`, `zt`) | a nullable `BlockPos`, saved under `target` in the vanilla position format (`NbtUtils.writeBlockPos`/`readBlockPos`) and absent when not bound. `y == -1` is a real height in 1.21.1 (the world starts at -64) |
| the Binder's "nothing remembered" = `posy == -1` (keys `posx`, `posy`, `posz`, no dimension) | a typed data component `thaumictinkerer:connector_device` (`ItemConnector.REMEMBERED_DEVICE`, a `GlobalPos`: dimension and position, vanilla codecs), absent when nothing is remembered. The unused 1.7.10 flag `ConnectingGolem` is gone (see 2.7) |
| the golem id of the Golem Connector (two longs, `UUIDMost`/`UUIDLeast` in the port's earlier revisions) | the vanilla UUID format (`putUUID`/`getUUID`) under `golem` |
| `ForgeDirection`, `ChunkCoordinates`, `Vec3` | `Direction`, `BlockPos`, `Vec3` |
| `IInventory`/`ISidedInventory` | `Container`/`WorldlyContainer`; `getAccessibleSlotsFromSide` = `getSlotsForFace` |
| `IWandable` clicks and `onBlockActivated` | In 1.21.1 `Block.useItemOn` runs before `Item.useOn`, and the wand item would run the block's `IWandable` after it. A plain wand click is therefore handled by the block (turns the Tablet, the Placer, the Dislocator); a sneaking wand click is not (vanilla skips the block when sneaking with an item) and reaches `IWandable.onWandRightClick`, which opens the Remote Placer's menu. For the Tablet and the Dislocator (not `IWandable`) this is what 1.7.10 did. **For the Remote Placer it is not**: see 2.2, "The wand turns the placer" |
| GUI ids and `IGuiHandler` | Architectury extended menus (`animation_tablet`, `remote_placer`), screens registered in `TileBlocksClient` |
| `PacketTabletButton`, `PacketPlacerButton` (SimpleNetworkWrapper) | `CustomPacketPayload` records `thaumictinkerer:tablet_button` and `thaumictinkerer:placer_button`, client to server |
| stack NBT | a data component (the Binder's own typed one, above) |
| `FakePlayer` of Forge | `TinkererFakePlayer extends ServerPlayer` (the add-on is loader neutral and cannot use the Fabric or NeoForge class); see 2.5 |
| `ThaumicTinkerer.registry.getFirstBlockFromClass` | `TileBlocks` accessors and the registry paths of `LibBlockNames` |
| `Item.itemShard:n`, `blockLifter`, `blockMagicalLog`, `blockCosmeticSolid:n`, `itemGolemBell`, `itemResource:10`, `blockStoneDevice` in recipes | Thaumcraft's native items by id (`ItemsTC.stack("earth_shard")`, `"levitator"`, `"greatwood_log"`, `"obsidian_tile"`, `"arcane_stone"`, `"golem_bell"`, `"mirrored_glass"`, `"order_shard"`; the table `docs/api/LEGACY_IDS.md` of Thaumcraft 4 Reload). Until 2026-11-01 they were looked up by the 1.7.10 field and metadata (`TileBlocks.legacyItem/legacyBlock/anyOfLegacyBlocks`, gone), which the API resolves to the same items. `blockStoneDevice` without a metadata was the wildcard, as the 1.7.10 crafting manager treated a bare `Block`: it is the explicit list `BlockRPlacer.STONE_DEVICES` of the eleven stone devices that have an item (the infusion pillars have none), compared with the API's legacy table by the GameTest `theRemotePlacerTakesAnyStoneDevice` |
| `itemGolemCore:100` (the Tablet recipe) | `thaumcraft:golem_core_blank` (`ItemsTC.stack("golem_core_blank")`) |
| a derived tool requirement: `Material.iron` and `Material.rock` needed a pickaxe for drops | `requiresCorrectToolForDrops()` on the Tablet, Remote Placer, Locomotive, Relay, Interface and Dislocator, and the six blocks are in `thaumictinkerer:mineable/tile_blocks/pickaxe` with no `needs/` tier (any pickaxe, as 1.7.10). The Golem Connector (`Material.wood`) needs nothing and is in the axe tag |
| hardness and blast resistance | hardness unchanged: Tablet 3 (metal sound), Interface and Dislocator 3; the Remote Placer, Locomotive, Relay and Golem Connector set none and so are instantly breakable. **Resistance is converted**: 1.7.10 `setResistance(r)` stored `r * 3` and `getExplosionResistance` read it back divided by 5, so the effective value is `0.6 * r`, which is what 1.21.1 `strength(hardness, resistance)` takes (the other groups convert the same way): Tablet `setResistance(50)` is 30, Interface and Dislocator `setResistance(10)` is 6. The default step sound of the 1.7.10 blocks that set none was stone, which is the 1.21.1 default |

## 2. Per object

### 2.1 Animation Tablet

* **Facing at placement (decision).** The original `onBlockPlacedBy` maps the placer's yaw to metadata so that the tablet
  faces back toward the player. `getHorizontalDirection().getOpposite()` is the same rule.
* **The powered mark follows the redstone mode (fix).** In 1.7.10 `onNeighborBlockChange` returned early unless the tablet
  was in redstone mode. A tablet that was powered when the mode was switched off kept bit 8 forever, and the wand then
  refused to turn it ("not rotatable") until the mode was switched on and the power removed. The port computes
  `redstone && (powered here or above)` on every neighbour change and the packet that switches the mode re-runs the check.
  In redstone mode the behaviour is identical.
* **Failure report (decision).** The original sent two red chat lines to everybody within eight blocks on *every tick* of a
  failure. The port logs the exception every time (`ThaumicTinkerer.LOG`) and sends the two lines at most once per 100
  ticks. The text says "Check your game log." where the fork said "Check your FML log." (there is no FML log).
* **Breaking (decision).** The original called the 1.7.10 `ItemInWorldManager` (`onBlockClicked`, `updateBlockRemoving`,
  `tryHarvestBlock`), reading the private field `curblockDamage`/`durabilityRemainingOnBlock` through a mixin accessor.
  1.21.1 has no such state, so the tablet counts the ticks itself: the progress after `n` ticks is `getDestroyProgress *
  (n + 1)` (the 1.7.10 formula), the crack stage `(int) (progress * 10)` is shown every tick it changes, and the block is
  taken with `ServerPlayerGameMode.destroyBlock` at 100 % (drops, tool wear, events as for a player). An instantly
  breakable block goes at the first hit. **The speed is a fifth of a player's on the ground**, as in the original: the
  1.7.10 `FakePlayer` was never ticked and so never landed (`onGround` stayed false), and `EntityPlayer.getBreakSpeed`
  divided by five when not on the ground (`Player.getDestroySpeed` of 1.21.1 has the same rule). An earlier revision of the
  port set `onGround` to true, which made the tablet five times faster than the original; that was reverted. A tablet
  with an iron pickaxe takes about 40 ticks for stone, with a stick about 200 for netherrack.
* **Attacks (decision).** 1.21.1 players have an attack cooldown and a held item's attribute modifiers only apply from the
  tick of a ticked player. The fake player is never ticked, so before each attack it applies the held item's main-hand
  modifiers itself (`TinkererFakePlayer.refreshHeldItemModifiers`) and sets the attack-strength ticker to "fully charged".
  A sword therefore hits with its full damage, as a 1.7.10 swing did, and the tablet's swing cadence (thirty ticks) is the
  only rate limit, as before. The fake player is not on the ground (see "Breaking"), which also keeps two 1.21.1 attack
  features away that 1.7.10 did not have: the *sweep* of a charged sword swing (it needs `onGround`; with it the tablet
  would also have hurt every mob next to its target) and the critical hit (it needs a fall, and the fall distance is reset
  on every tick). One swing hits one target, as before.
* **Tool test for placing (decision).** `canUseItem` accepted `ItemBlock`, `ItemReed`, `ItemSeeds`, `ItemRedstone`; in
  1.21.1 all four are `BlockItem`s, and **so are items that 1.7.10 did not accept**: doors, beds, signs, skulls, carrots,
  potatoes, cocoa beans (`ItemDoor`, `ItemBed`, `ItemSign`, `ItemSkull`, `ItemSeedFood`, `ItemDye` there). A tablet in
  right-click mode with nothing in front of it now also places those onto the block below its target, where it used to
  stay idle. The widening is accepted: it follows from the item classes of 1.21.1, and restricting it would mean a list
  of block classes to maintain. The hoe rule (`ItemHoe` on `Blocks.dirt` or `Blocks.grass`) is kept literally: dirt and
  grass, plus coarse dirt, which was a metadata variant of `Blocks.dirt` in 1.7.10 (podzol was one too, but a 1.21.1 hoe
  does nothing to it, so a tablet would swing at it for nothing), and not the other tillable blocks that a 1.21.1 hoe also
  works on (dirt path, rooted dirt).
* **Right click on the block (decision).** The original asked Forge's `PlayerInteractEvent` and then called
  `onPlayerRightClick`; the port goes through `ServerPlayerGameMode.useItemOn`, which fires the loader's block-interaction
  event itself. The click on the block below an empty target is a `BlockHitResult.miss` on the top face, as the original
  `MovingObjectPosition(..., false)` was a MISS.
* **`isPickable` for the entity test (decision).** `canBeCollidedWith()` of 1.7.10 maps to `isPickable()`, and
  `getCollisionBorderSize()` to `getPickRadius()`.
* **Client swing (forced).** The GregTech World Accelerator hook (`MTEWorldAccelerator.getAccelerationForTEUnsafe`, the "xN"
  text it drew) is dropped: it is a GT5-Unofficial class that does not exist in 1.21.1. The client swings one step per tick.
* **The held item's sway (1.21.1 clock, 2026-11-01).** The renderer swayed the item by the wall clock, as 1.7.10 did
  (`System.currentTimeMillis()`); it now reads the game time of the level with the partial tick, at 50 ms a tick, so the motion is the
  same at 20 ticks a second and pauses with the game and follows `/tick`.
* **Update packet (forced).** A 1.21.1 client ignores a block-entity update with an empty tag, and the original update
  packet carried the item and the two modes only. The packet keeps carrying those; `loadAdditional` reads the swing counters
  only when they are present, so a sync cannot stop a swing that is running on the client.
* **Computer peripheral (forced).** `tt_animationTablet` (ComputerCraft/OpenComputers: `getRedstone`, `setRedstone`,
  `getLeftClick`, `setLeftClick`, `getRotation`, `setRotation`, `hasItem`, `trigger`) has no loader-neutral registration.
  The behaviour is plain methods on the block entity (`trigger()`, `getRotation()`, `setRotation(int)`, `setLeftClick`,
  `setRedstone`, the public fields and `isEmpty()`), ready for a loader module to bridge. `setRotation` also refuses a
  negative value (the original only refused values above 3 and wrote an invalid metadata for a negative one).
* **AE2 `IMovableTile` (forced).** Not applicable to 1.21.1; `setRemoved` stops a break in progress, which is what
  `prepareToMove` was for.
* **The tablet asks before it changes a block (new, for safety).** 1.7.10 asked nobody (Forge's `PlayerInteractEvent` only for the right
  click). Before a click on a block the fake player asks `BlockProtection` (`docs/HOOKS.md` section 16: the world border, a dedicated server's
  spawn protection, adventure and spectator mode, and the protection mods through the loader's break and place events), and the tablet leaves
  the block alone when the answer is no: a break asks `mayBreak` for the block (when the swing starts the break, and again if another block is
  in front while it breaks); a right click asks `mayPlace` for the block in front with its present state, or, with nothing in front, `mayPlace`
  for the empty block in front with the held block item's block (it goes there) or `mayBreak` for the block below it (a hoe tills that).
  Attacks and uses on entities ask nothing: the question is about blocks. The loaders' own events still see some of what the tablet does:
  NeoForge fires its attack and entity-interaction events in `Player.attack`/`interactOn`; Fabric API fires `UseBlockCallback` in
  `ServerPlayerGameMode.useItemOn` and `AttackEntityCallback` in `ServerPlayer.attack`, but `UseEntityCallback` only from the packet handler,
  so a Fabric claim mod does not see the tablet's uses on entities (read in the bytecode of fabric-events-interaction-v0 0.7.14). Run: `tabletRespectsTheWorldBorder` (the game's part; the protection mods' part is the
  core group's backends, `HooksGameTests`).
* **Item cooldowns run out (fix).** 1.21.1 items have cooldowns that run in the player's tick (1.7.10 had none); the fake player is never
  ticked, so the first ender pearl, wind charge or chorus fruit of a tablet started a cooldown that never ended and the item was never used
  again. `TabletFakePlayer.onUpdate` runs the cooldowns up to the level's game time (`TinkererFakePlayer.advanceCooldowns`). Run:
  `tabletCooldownsRunOut` (three pearls, a second one goes after the first one's twenty ticks).
* **The fake player's eye is the tablet's centre (decision, follows from the fake player's fix in 2.9).** An item that looks for a block along
  the player's view on a click into the air (a bucket, a glass bottle) or throws something (a pearl, a snowball) now starts from the middle of
  the tablet, where the tablet's own ray starts. Before, the eye was 1.62 above it (the entity type's eye height, 2.9), in the second block
  above the tablet.
* **Item rendering on the tablet (unverified estimate).** The held item is drawn with `ItemDisplayContext.FIXED` at scale
  1.55 for blocks and 1.9 for other items. The 1.7.10 numbers belong to its own item renderer and cannot be reused; these
  two are a visual estimate and have not been looked at in a game.

### 2.2 Remote Placer

* **Registered although the fork disabled it (decision).** `BlockRPlacer.shouldRegister()` returned `false` in the pinned
  fork, so the block never existed there. The task is to port the block, so it is registered, with its research
  `REMOTE_PLACER` and recipe. If the lead wants the fork's behaviour, the one line that registers it in
  `TileBlocks.register()` (and the research and recipe that hang on the block) is what to remove.
* **New research text (decision).** The converted language base has no text for the two pages of `REMOTE_PLACER` (the entry
  was never shown). `ttresearch.page.REMOTE_PLACER.0` and `.1` were written for the port in `lang-extra/tile_blocks.*.json`
  (English and Russian), describing what the code does: one to four blocks, the redstone pulse, the empty target, the wand,
  sneaking with a wand opening the menu.
* **The east quirk is kept (decision).** In 1.7.10 `receiveRedstonePulse` had `case 5` (east) copied from `case 4` (west),
  so an east-facing placer aimed `x - blocks`. `TileRPlacer.EAST_AIMS_WEST_AS_IN_1_7_10 = true` keeps it; set it to `false`
  to aim east. The facing, the side of the click and the picture of the GUI are unaffected.
* **The fake player stands at the target (decision; fix of the bucket).** The original used `FakePlayerFactory.getMinecraft`, a single
  shared player at the world spawn. The port gives the placer its own `TinkererFakePlayer` with the profile of that shared player
  (`[Minecraft]`, `41C82C87-7AfB-4024-BA57-13D2C99CAE77`, `TinkererFakePlayer.MINECRAFT_PROFILE`), looking along the placer's facing, so
  that what is placed is turned as expected (stairs, logs, lecterns). It stands at the target, its eye just inside the face through which
  that look enters the target (the face toward the placer; the far one for the east quirk below), and it reaches one block
  (`BLOCK_INTERACTION_RANGE` 1). An item that looks for a block along the player's view on the click into the air (a bucket, a glass
  bottle, a boat, a lily pad, a spawn egg) therefore finds the target and the face of the block beyond it, and nothing else: **a water
  bucket pours into the target when there is a block beyond it, and does nothing when there is none**; a thrown item leaves from the
  target. An earlier revision stood the player in the middle of the placer with its eye 1.62 above that (2.9), so the bucket's look started
  in the second block above the placer and ran four and a half blocks along the facing: the water went wherever that ray met a block (for a
  placer facing down, the ray met the placer's own top, and the water landed on it, behind the placer). (The review that found this guessed the eye was inside the placer; it was
  above. Neither was the target. The old behaviour was worked out by reading, it was not run.) Run: `remotePlacerPoursABucketIntoItsTarget`
  (a placer facing north and one facing down fill their targets, nothing behind or above them; with nothing beyond the target the bucket
  stays full).
* **The placer asks before it places (new, for safety).** Before the click the fake player asks `BlockProtection.mayPlace` for the target
  (with the held block item's block, or the target's present state for another item); when the answer is no, nothing happens and the item
  stays in the slot. Run: `placerAndDislocatorRespectTheWorldBorder` (the game's part).
* **Item cooldowns run out (fix).** As for the Tablet (2.1): before each use the fake player's cooldowns are run up to the level's game time
  (at most 6000 ticks at once), so an ender pearl in a placer is thrown again on a pulse after its cooldown, not never.
* **The click (decision).** `onItemUseFirst` and `onItemUse` of the original are `ServerPlayerGameMode.useItemOn` with side
  = the opposite of the facing and the hit at the lower corner of the target (the original passed 0, 0, 0). When nothing
  happens, the right click in the air follows, as `onItemRightClick` did. An item that was used up leaves the slot empty
  (the original left a stack of size zero in the slot).
* **Unloaded target (decision).** The target must be in a loaded chunk; the original loaded it by asking for the block.
* **Distance is clamped (fix).** The screen sends 1 to 4. The packet handler and `setBlocks` clamp to 1..4; the original
  accepted any integer a hacked client sent.
* **The packets are checked (fix).** `PacketPlacerButton` and `PacketTabletButton` act only when the sender has the
  matching menu open on that very block entity (`PacketTile.resolve`). The original applied the packet to whatever tile
  entity sat at the sent dimension and coordinates, for any player at any distance (a tile entity of the wrong type was a
  `ClassCastException`): a client could switch the modes of every Tablet and set the distance of every Placer in a loaded
  chunk. The packet's dimension field is gone (the open menu is in the sender's dimension).
* **Contents drop on break (fix).** The original `BlockRPlacer` had no `breakBlock` override, so the stack in the slot was
  destroyed with the block. The port drops it (as the Tablet already did).
* **Slot (kept).** `isItemValidForSlot` is "a `BlockItem`" (an `ItemBlock` in 1.7.10) and restricts what *automation*
  puts in (`Container.canPlaceItem`: hoppers). The screen's slot is a plain `Slot`, as in 1.7.10, where `Slot.isItemValid`
  returned true whatever the inventory said: **any item can be put in by hand and by shift-click**, and the placer then
  uses it as it uses a block (a bucket, seeds, bone meal). 1.21.1 `Slot.mayPlace` does not ask the container either.
  An earlier revision of this file claimed the screen slot was restricted to blocks; it never was, here or in 1.7.10.
* **The wand turns the placer (decision, new behaviour).** The original `BlockRPlacer.onBlockActivated` turned the placer
  to the clicked side for a wand, but the block also implements `IWandable` and returns 0 from `onWandRightClick` for every
  click, and `ItemWandCasting.onItemUseFirst` treats any result of zero or more as handled (it runs before the block's
  `onBlockActivated` in Forge's `ItemInWorldManager`). So in 1.7.10 the wand branch was unreachable: a plain wand click did
  nothing, and only the sneaking wand click did something, by opening the screen. (The fork never registered the block, so
  nobody could have seen it either way.) The port keeps the author's evident intent: a plain wand click turns the placer
  with the wand sound (like the Dislocator and the Tablet), a sneaking one opens the screen. The 1.7.10 engine is not on
  disk here; this rests on the port's own `IWandable` contract (checked against the `ItemWandCasting` bytecode by the API
  documentation) and on Forge's call order. If the lead wants the literal behaviour, `PulseDeviceBlock.useSpecialItem`
  has to return a "consumed, nothing done" result for `BlockRPlacer` and the second research page loses its sentence
  about the plain click.
* **GUI picture (forced).** The screen uses `remote_placer.png`, the texture the converted base has for it.

### 2.3 Levitational Locomotive and Relay

* **Chunk handling (decision).** 1.7.10 asked `getTileEntity`/`getBlock`, which loaded chunks. The port consults only loaded
  chunks (`Level.isLoaded`) for the relay search, the relay check, the bounce and the move, and does nothing when the chunk is
  not loaded. A track across a chunk border that is not loaded stops until it is.
* **A partner out of reach ends the link (fix).** `TileEntityRelay.verifyPartner` ends the link of a relay whose partner is
  outside what `checkForPartner` can find (offsets -32 to +31 on each axis; `TileEntityRelay.partnerInRange`). 1.7.10 loaded
  the partner's chunk, found no relay there and ended the link; the port does not ask an unloaded chunk (see above), so a
  relay that carries the coordinates of a far-away partner (copied with `/clone` or a structure, edited data) kept its link
  forever, and the beam loops of `tick()` walk the whole rectangle between the two relays: for a partner thousands of
  blocks away that never ends and the server thread hangs. Seen in a real GameTest run: the made-up partner (77, -88) of the
  persistence check, in a run where the test area lay at x = -944,791, z = -6,350,655, stopped the server thread in
  `TileEntityRelay.tick` for good. Every partner a search can set is within the range, so real links are unaffected.
  `DeviceGeometryTest` pins the range, the GameTest `relayDropsAStalePartner` the tick.
* **The partner search keeps the last relay it finds (kept).** `checkForPartner` has no `break`: along the z line and the x
  line, offsets -32 to +31, every relay found becomes the partner and replaces the previous one, so with three relays in a
  line the farthest wins and the nearer one keeps a stale partner that does not point back, which its next check ends. The
  GameTest `relayKeepsTheLastRelayItFinds` pins it. Consequence for tests: the areas of a GameTest run lie side by side
  13 blocks apart, so relays of different checks at the same height find each other; every check of the group that
  places relays uses a height of its own.
* **The move condition (decision).** The original condition `isAirBlock(target) || target.isAir && (...)` reduces to "the
  target is air" by operator precedence; the port tests exactly that. The passenger rules are kept: moved when the space
  above the locomotive is empty or the passenger can be placed above the target; bedrock stays; a piston head is not
  erased. **New (2026-11-01): a block in the convention tag `c:relocation_not_supported` stays as bedrock does** (Fabric API's
  `ConventionalBlockTags.RELOCATION_NOT_SUPPORTED`, NeoForge's `Tags.Blocks.RELOCATION_NOT_SUPPORTED`; `TileEntityMobilizer.carries`).
  The tag is empty in the game itself on both loaders (it only refers to the loaders' legacy tags), so nothing of vanilla changes; a
  mod that marks its blocks as not relocatable is now respected. GameTest `whatMustNotBeRelocatedStaysWhereItIs` (with a lodestone that
  the GameTest data pack puts into the tag).
* **The passenger's block entity (forced).** The original moved only vanilla tile entities (class name starting with
  `net.minecraft.tileentity`) or, with AE2, the ones AE2 allowed, by invalidating the object and re-adding it. The port
  moves the block entities whose class is in `net.minecraft.` through their saved data (`saveWithFullMetadata`, then
  `Clearable.clearContent` on the old one so removing it spills nothing, then `loadWithComponents` on the new one). Other
  block entities are left behind, as in the original; the AE2 branch has no equivalent.
* **The "erase the passenger" quirk (kept).** The moved block is replaced by air with flag 2 only when it is not air or a
  piston head, as in 1.7.10: `Block.getBlockFromName("piston_head")` is the arm block, `Blocks.PISTON_HEAD`, so a piston
  head on top of the locomotive is copied and left where it was. (An earlier revision tested `Blocks.MOVING_PISTON`, the
  1.7.10 `piston_extension`, which is a different block.)
* **No hang beyond 16,777,216 blocks (fix).** The loops over the beam and over the locomotives between two relays
  advance with `i += Math.copySign(1, difference)`, which adds a float to an int and stops advancing once the coordinate
  reaches 2^24 in absolute value; `while (i < partnerX)` then never ends, the relay ticks on both sides every tick, and a
  linked pair out there (the world border is at 30,000,000) hangs the server thread until the watchdog. The port steps by
  the integer `-1` or `+1` (`TileEntityRelay.step`, `+1` for a difference of zero as `copySign(1, 0)` is); everything below
  2^24 behaves as before, including the original's walk that visits only the first coordinate when the partner lies in the
  negative direction. `DeviceGeometryTest` pins both.
* **The locomotive is a new object after a move (forced).** 1.7.10 re-pointed the same tile entity at the new coordinates.
  The port places a new block entity at the target and copies the link; the old one is marked dead, which its ghost check
  already did.
* **Sparkles (API).** `ThaumicTinkerer.tcProxy.sparkle` is `ThaumcraftClientFX.sparkle(level, x, y, z, type)` (A8 of
  `docs/API_GAPS.md`, which the API has now), called on the same loops from the client branch of the relay's tick. The API
  knows the colours 0 to 7 and rolls the original's four-in-six chance itself: the original type 2 (blue) is 2, and the
  original type 14 (white) is 6. The comparison `zCoord > partnerX` of the original (an obvious typo that picks the colour)
  is kept.
* **The relay renderer is not registered (kept).** The original never registered `RenderTileRelay` and `ModelRelay` in its
  client proxy, and `relay.png` is not among the original's textures. The relay is a block with a model. The port had ported
  both classes anyway, unused; they were deleted on 2026-11-01 (dead code; `LibResources.MODEL_RELAY_TEXTURE` is the lead's to
  remove).
* **Research (kept).** The Relay has no research of its own: its recipe is the second page of the
  locomotive's entry, under the key `LEVITATOR_RELAY`, as in 1.7.10.
* **The multiblock diagram page (decision).** The original builds a diagram of the track in `ResearchHelper.LeviationaryHelp`
  (`research/ResearchHelper.java:228` of the pinned source): a `COMPOUND_CRAFTING` page, the list of Thaumcraft's wand
  construct pages, five blocks wide, one high, one deep, no vis, with the blocks relay, empty space, locomotive, empty space,
  relay. **No research of the original uses it**: the entry `LEVITATOR_LOCOMOTIVE` of the pinned commit has three pages (text,
  the infusion, the relay's arcane recipe), and the oracle of `PARITY_REPORT.md` records exactly those. The upstream history
  says why: the page came with the research on 2014-02-04 (commit `c3402593`: pages "0", infusion, "1", relay, "2", diagram)
  and left the entry the same day (`59713058`), leaving the helper unused ever since. The port adds it as a fourth page, after the three of the original.
  `ResearchHelper.LeviationaryHelp()` is the original's list with two forced changes: it goes through a raw `List` cast,
  because the 1.21.1 constructor is typed `ResearchPage(List<Recipe<?>>)`, and the original's "Empty Space" block
  (`new ItemStack(ConfigBlocks.blockHole, 1, 15)`) has no item in 1.21.1, so the two gaps are `ItemStack.EMPTY`, which
  Thaumcraft 4 Reload draws as that block (nothing visible, "Empty Space" on hover). Thaumcraft draws the page as TC4 did, all
  layers at once in its isometric layout over the ground plate, with the item names on hover, from the version that draws
  compound pages (Thaumcraft branch `compound-pages`, 2026-11-01); a Thaumcraft without it skips a page it cannot draw, so the
  entry then shows the three pages of the original. **The Thaumcraft this add-on is pinned to** (`thaumcraft_ref`, main 299724f,
  0.1.0-alpha.11) draws its own construct pages with its own `ConstructBlueprintDisplay` and skips an add-on's compound page: with it the
  diagram page is registered but not shown, until Thaumcraft draws add-on compound pages too. The parity check allows this one difference (`pages.count` 3 to 4,
  `known_differences.json`); the first three pages are still compared and match. The GameTest
  `locomotiveResearchShowsItsDiagram` pins the page, its size and its five blocks.

### 2.4 Golem Connector

* **Registered without ComputerCraft (decision).** The fork registered the block only when ComputerCraft was loaded
  (`shouldRegister() == LoadedMods.computerCraftLoaded`), because its only use is as a computer peripheral. The port
  registers it always, with its research, recipe and the Binder's golem binding, so that the group is complete and a loader
  module can bridge it later. Without that bridge the block does nothing a player can see except remember its golem and
  stay camouflageable.
* **Parent research (decision).** The parent `PERIPHERALS` ("Peripherals", documentation) was also registered only with
  ComputerCraft. The Golem Connector registers it the same way the Machines group does for the Aspectalyzer (only when the
  key is not there yet; a second registration of a key is ignored by Thaumcraft). It belongs in the shared
  `ResearchHelper`: see the shared-file requests of the report.
* **Golem data is not readable (forced, API request A11).** Thaumcraft's golems have no public API in 1.21.1. The golem is
  recognised by its entity type, `ThaumcraftEntities.GOLEM` of the API (the entity class is not reachable), found within thirty blocks every
  100 ticks (`time % 100 == 1`, as before) by its UUID, and the 15 methods of the peripheral
  (`GolemConnectorMethods`: `getDecorations`, `getPosition`, `getType`, `getHealth`, `getCore`, `getHome`, `setHome`,
  `getMarkers`, `setMarkers`, `newMarker`, `addMarker`, `saveMarker`, `deleteMarker`, `getMarker`, `getMarkerCount`, with
  the original's results for "no golem" and its error messages) run on a `GolemAccess`. The only implementation today is
  `UnavailableGolemAccess` ("no golem"), installed by `GolemAccessProvider`; when the API has a golem view, the provider is
  the one place to change. The methods are plain Java and are covered by a JUnit test against a fake golem.
* **Computer peripheral (forced).** As for the Tablet: `tt_golemconnector` is not registered with ComputerCraft or
  OpenComputers.
* **The block says that it is inert (decision).** Its item has a grey tooltip, `tooltip.thaumictinkerer.golem_connector`
  ("The computer link and the golem data are not part of this version yet."), and the research pages `GOLEM_CONNECTOR.0`
  and `.3` are rewritten (`lang-extra/tile_blocks.*.json`, all six languages) so that they do not promise a computer
  that reads golems or send the reader to the 1.7.10 documentation web page (`GuiResearchPeripheral`, ENTER, is not
  ported: the page lived in a GUI that the add-on does not have). Page 1 (binding, thirty blocks) is unchanged and true.
* **A stale id is cleared (fix).** The original `readCustomNBT` kept the previous UUID when the tag had none; the port sets
  it to none, so an unbound connector stays unbound after a sync. The id is saved in the vanilla UUID format (section 1); a connector
  saved by an earlier revision of the port (two longs) loads unbound.

### 2.5 Transvector Interface

* **Items, aspects, essentia (kept).** `getSizeInventory`, `getStackInSlot`, ... forward to the bound block entity when it
  is a `Container`; `getAspects`, `addToContainer`, ... when it is an `IAspectContainer`; the transport methods when it is
  an `IEssentiaTransport` (both are the API's interfaces, which the tubes and jars ask of any block entity). The face rules
  of a non-sided inventory (all slots, insert and extract allowed) are kept.
* **Fluids and energy are forwarded by loader glue (forced; run on Fabric and on NeoForge GameTest servers); IC2 EU is not.** The original implemented `IFluidHandler`, CoFH RF
  (`IEnergyHandler`, `IEnergyReceiver`, `IEnergyProvider`) and IC2 EU (`IEnergySink`, `IEnergySource`,
  `IEnergyConductor`) and forwarded them. 1.21.1 has no interface for these that common code can implement: fluid and
  energy storage are loader capabilities (Fabric Transfer API and Team Reborn Energy, NeoForge capabilities).
  `TileTransvector.getTile()` is the one method a loader module needs to build the forwarding capabilities on. The loader
  modules install the glue themselves; common code names no loader class.
  *Fluids on Fabric:* `thaumic.tinkerer.fabric.TileBlocksFabric` (new, in the Fabric module; called by
  `ThaumicTinkererFabric.onThaumcraftReady()` through `ThaumicTinkerer.guard`, so a failure is recorded) registers `FluidStorage.SIDED` for the Interface's block
  entity type and answers with the fluid storage of the bound block for the same side, so tanks, pipes and buckets work
  through it as `fill`/`drain`/`getTankInfo` did. The Fabric GameTest `interfaceForwardsFluids` covers it (run 2026-10-31 with the
  other 34 checks of `tileblocksgametests.*`: all passed). An Interface bound to another Interface is refused there (no endless chain).
  *Fluids and items on NeoForge:* `thaumic.tinkerer.neoforge.NeoForgeCapabilities` (new) registers, in `RegisterCapabilitiesEvent`,
  `Capabilities.FluidHandler.BLOCK` for the Interface's block entity type, with the same refusals (unbound, bound to itself or to
  another Interface, or a bound block without a fluid handler for that side: none). Two differences in shape, both forced by NeoForge's
  capability caches, which keep what a provider returned: the handler handed out asks the bound block for its handler again on every call
  (a pipe must not keep filling the tank of a binding that ended, or a broken tank), and a change of binding invalidates the
  capabilities at the Interface's position (`TileTransvector.setBindingListener`, new, called by `setTarget` and when `getTile()` ends a
  binding), so a cache that saw "none" asks again. It also registers `Capabilities.ItemHandler.BLOCK` (the Interface's own
  `WorldlyContainer` in NeoForge's `InvWrapper`, or `SidedInvWrapper` for a side), because NeoForge wraps only the vanilla containers,
  where Fabric's Transfer API wraps any; a vanilla hopper reaches the Interface either way. Run on a NeoForge GameTest server (2026-10-31,
  `docs/NEOFORGE.md`, "GameTests on NeoForge"): `interfaceForwardsFluids` through `Capabilities.FluidHandler.BLOCK` (its NeoForge body,
  `TileBlocksNeoForgeChecks.interfaceForwardsFluids`, asks `level.getCapability` without a cache), and two NeoForge-only checks,
  `interfaceItemHandlerReachesTheBoundChest` (the item handler, with and without a side, and a cache made while unbound) and
  `interfaceFluidCapabilityCacheFollowsTheBinding` (the invalidation on binding, on a broken tank and on unbinding); all passed. Not tried:
  other mods' pipes and tanks.
  *Energy* (since 2026-11-01). The original's RF methods (`receiveEnergy`, `extractEnergy`, `getEnergyStored`, `getMaxEnergyStored`,
  `canConnectEnergy`) each handed the call to the bound tile entity with the side it was asked for, when that was an `IEnergyHandler`,
  and answered 0 or `false` otherwise; the target was the one `getTile()` gives. The port does the same with each loader's energy API:
  *NeoForge*: `NeoForgeCapabilities` registers `Capabilities.EnergyStorage.BLOCK` for the Interface exactly as its fluids: the bound
  block's storage for the same side (`level.getCapability`), through a storage that asks again on every call (empty while there is none),
  none (null) while the Interface is unbound, bound into an unloaded chunk, bound to itself or to another Interface
  (`TileTransvectorInterface.bound()`, which also ends such a binding on the server) or bound to a block without a storage for that side,
  and the same invalidation of the Interface's capabilities when the binding changes. *Fabric*: `TileBlocksFabricEnergy` (new) registers
  Team Reborn Energy's `EnergyStorage.SIDED` for the Interface and answers with the bound block's storage for the same side, with the same
  refusals (the shape of the Fabric fluids: Fabric's lookups ask the provider on every `find`). Team Reborn Energy (`teamreborn:energy`
  4.1.0 from `maven.fabricmc.net`, MIT, the release for Minecraft 1.21 to 1.21.4; 4.2.0 needs 1.21.5) is an **optional** dependency: the
  add-on compiles against it, never bundles it (it is not in the jar) and only `suggests` it in `fabric.mod.json`; `ThaumicTinkererFabric`
  calls `TileBlocksFabricEnergy`, the one class that names its API, only when Fabric Loader reports the mod, so without it no class of the
  mod is loaded and the Interface simply has no energy storage on Fabric (as a 1.7.10 Interface had no RF without CoFH).
  `TinkererApiOnlyTest.teamRebornEnergyIsNamedOnlyByItsGuardedGlue` keeps that guard in place; the GameTest run has the mod, and
  `-PwithoutTeamRebornEnergy` runs it without (section 4). **Units (decision):** none converted. The amounts pass as the bound storage
  gives them, as items and fluids do: NeoForge's storages count in ints, Team Reborn Energy's in longs, and on each loader the Interface
  stands between two storages of the same API. `canConnectEnergy` is NeoForge's `canReceive`/`canExtract` and Team Reborn Energy's
  `supportsInsertion`/`supportsExtraction`, the bound storage's answers. **IC2 EU (forced, not ported):** the original also joined IC2's
  energy net (`IndustrialcraftUnloadHelper`: a load event on its first tick, an unload event on `invalidate` and chunk unload) and forwarded
  the sink, source and conductor calls; IC2 has no 1.21.1 release, and the 1.21.1 lookups need no registration in a net. Run: the shared
  checks `interfaceForwardsEnergy` (unbound none; bound to the GameTest energy cell, inserts and extracts reach it with its limits per call
  and its capacity, simulations change nothing, the closed face of the cell is closed at the Interface, no face is the whole cell; bound to a
  chest or unbound, none) and `interfaceMovesNoEnergyThroughABrokenBinding` (a saved binding into an unloaded chunk: none, the binding kept,
  no chunk loaded; bound to itself, or two bound to each other, as saved: none, and the question ends the binding; a broken cell: none, the
  binding ends) on both loaders, and on NeoForge `interfaceEnergyCapabilityCacheFollowsTheBinding` (a `BlockCapabilityCache` through
  binding, a broken cell and unbinding); results in section 4. Not tried: other mods' cables and machines.
  On Fabric items go through the Interface's `WorldlyContainer`, which the Transfer API wraps itself; essentia goes through the
  Thaumcraft interfaces on both loaders.
* **The research text of the Interface says what it does (decision).** `INTERFACE.0` promised BuildCraft, TE3 and IC2
  power; `lang-extra/tile_blocks.*.json` (all six languages) now says power, essentia, items and liquids, that power passes
  both ways and that on Fabric it needs the Team Reborn Energy mod (until 2026-11-01 it said that power did not pass through it).
  `INTERFACE.1` still carries the 1.7.10 note about BuildCraft pipes, which is harmless.
* **An unbound interface does not swallow essentia (fix).** The original `addToContainer` returned `0` (nothing left over)
  when the interface was not bound to an aspect container, so a tube or a phial that poured into an unbound interface lost
  the essentia. The port returns the whole amount (nothing was taken), `doesContainerAccept` is `false`, and `getAspects`
  is an empty list instead of `null`.
* **Never bound to itself or to another Interface (fix, was a server crash).** The Binder refuses Interface to Interface (2.7), but a
  Dislocator can make such a binding: it moves an Interface onto the block that Interface is bound to (the Interface is then bound to its own
  place), or two Interfaces into each other's targets. Every question then asked the Interface again (a hopper's `getContainerSize`, a
  comparator's, a tube's `getEssentiaAmount`, and `setChanged`, which a click on the Interface runs) until the stack overflowed: a crash of
  the server, again after every restart as soon as something asked. Every forwarding path now goes through
  `TileTransvectorInterface.bound()`, which answers null for the Interface itself and for another Interface, on both sides, and on the server
  `getTile()` ends such a binding (`TileTransvector.mayBindTo`, new), so a world saved with one repairs itself at the first question; a
  `setTarget` to itself or to another Interface ends at once. The loader glue (`TileBlocksFabric`, `NeoForgeCapabilities`) already refused
  both; the energy glue (`TileBlocksFabricEnergy`, the energy capability of `NeoForgeCapabilities`) asks `bound()` (checked by
  `interfaceMovesNoEnergyThroughABrokenBinding`). Run: `interfaceMovedOntoItsOwnTargetEndsTheBinding` (a Dislocator moves the Interface onto its chest; a hopper's push and a player's
  click, each on the saved state, end the binding without a crash) and `interfacesBoundToEachOtherEndTheBinding`. Not run: the same against
  the code before the fix (the crash is worked out by reading).
* **The two item rules of 1.21.1's `Container` are forwarded (fix).** `canTakeItem(Container, int, ItemStack)` (may a hopper take this
  stack out) and `getMaxStackSize(ItemStack)` were the `Container` defaults (yes; the Interface's own limit), so the bound block's own rule
  was not asked (a chiseled bookshelf lets a hopper take a book only when the hopper has room for it). Both are the bound inventory's
  answers now (unbound: no, 0). Run:
  `interfaceForwardsTheHopperRules`.
* **Binding that is gone (decision).** `getTile()` ends a binding on the server when the bound block entity is gone (for the
  Interface; the Dislocator needs none) or the target is beyond the range, as the original did by setting `y = -1`; a
  client that has not received the target yet keeps it, and a target in an unloaded chunk answers "no block entity" and keeps
  the binding.
* **Notification (kept).** Every 100 ticks the interface calls `updateNeighborsAt`, as `notifyBlockChange` did, so pipes
  look again.
* **Computer peripheral (forced).** The Interface answered `getType` and the methods of the bound peripheral; not bridged.

### 2.6 Transvector Dislocator

* **`checkBlock` refuses what a player cannot break (deviation from 1.7.10, for safety; lead decision).** The original expression is
  `(!node && !blacklisted && hardness != -1 && block != null) || block != air`. Every non-air block passes through the
  second operand and air passes the first, so in 1.7.10 the check was true for every block: nodes, bedrock and the portable hole's
  blacklist were swapped too, and so a Dislocator could take bedrock out of the floor of the world, barriers, end portal frames and
  command blocks. An earlier revision of the port kept that. The port now drops the trailing `|| block != air` (the evident intention) and
  refuses `destroySpeed < 0` (bedrock, barriers, end portal frames, portals, the light block) and any `GameMasterBlock` (command,
  structure and jigsaw blocks; vanilla's are unbreakable anyway, the clause is for other mods' operator blocks): not a node, not on
  the portable hole's blacklist, breakable, no operator block.
  Air passes, so a block can still be moved into an empty place. A refused block is not swapped, the entities are moved as before (1.7.10
  moved them whatever the check said). Spawners (hardness 5) and other breakable block entities still move with their data. Run:
  `dislocatorRefusesWhatAPlayerCannotBreak` (replaces `dislocatorChecksLetEveryBlockThrough`, which pinned the old behaviour).
* **The blacklists are block tags (fix, 2026-11-01).** The portable hole's blacklist is Thaumcraft's block tag
  `thaumcraft:portable_hole_blacklist` (beds, doors, pistons and their heads, the moving piston, flux goo and gas), read with
  `state.is(tag)`. Until 2026-11-01 the Dislocator asked the API's list `ThaumcraftApi.portableHoleBlackList`, which only mirrors
  the tag and is filled by `refreshPortableHoleBlackList()`, which Thaumcraft 4 Reload calls once in its mod initialisation, before
  any tag is bound: the list was empty, so a Dislocator swapped beds, doors and pistons (one half of a door or bed, the other half
  left behind). **New:** it also refuses the blocks of the cross-mod convention tag `c:relocation_not_supported` (Fabric API's
  `ConventionalBlockTags.RELOCATION_NOT_SUPPORTED`, NeoForge's `Tags.Blocks.RELOCATION_NOT_SUPPORTED`; empty in the game itself, so
  only other mods' blocks are affected). The add-on's own tag `thaumictinkerer:dislocation_blacklist` and the config list
  `focusOfDislocationBlacklist` are the Focus of Dislocation's (Foci), as in 1.7.10, where the Dislocator did not read the focus's
  config either; they are not asked here (the config option keeps working for the focus, unchanged). Run:
  `whatMustNotBeRelocatedStaysWhereItIs` (pistons, a bed, a door and, through the GameTest data pack's tag, a lodestone are refused;
  a real pulse leaves the lodestone and the other end in place).
* **The Dislocator asks before it swaps (new, for safety).** 1.7.10 asked nobody. Before a swap a fake player of the Dislocator's own (the
  Remote Placer's profile `[Minecraft]`, standing in the Dislocator) asks `BlockProtection` for **both ends**: the game's rules for each place
  always (the entities there are moved whatever the blocks do), and, when the blocks will be swapped, `mayBreak` for a block that goes and
  `mayPlace` for one that comes (air is neither). When any answer
  is no, nothing changes places, blocks or entities (moving a mob or a chest out of a claim is as much a change as breaking it), and the
  Dislocator rests its ten ticks. Run: `placerAndDislocatorRespectTheWorldBorder` (the game's part).
* **Block entity swap (forced).** The original swapped metadata and the tile NBT (`writeToNBT`, `createAndLoadEntity`). The
  port swaps the block state and the full block-entity data (`saveWithFullMetadata`, `BlockEntity.loadStatic`), after
  replacing the old block entity by a fresh one so that removing the block does not spill the old one's contents.
* **Unloaded end (decision).** The swap is aborted when the end block's chunk is not loaded (the original tested
  `blockExists`, which is false for an unloaded chunk too, and still moved the entities and started the cooldown).
* **Entities (kept).** The entities inside the two blocks change places by the vector between the blocks; players are
  teleported through their connection.
* **Neighbour update after the swap (forced).** `block.onNeighborBlockChange` is `BlockState.handleNeighborChanged`.

### 2.7 Transvector Binder

* **Messages and sound (kept).** The six `ttmisc.connector.*`/`ttmisc.golemconnector.*` keys and the orb-pickup sound at
  volume 0.8 are the original's. The sound plays only on the server, as before.
* **The golem test (API).** `instanceof EntityGolemBase` is `ThaumcraftEntities.is(entity, ThaumcraftEntities.GOLEM)`
  (A7 of `docs/API_GAPS.md`, which the API has now).
* **A remembered device in an unloaded chunk (kept).** The original asked `getTileEntity` for the remembered position,
  which loads its chunk, so a player who walked away from a remembered device found it ("too far"), not forgotten. The
  port does the same (`Level.getBlockEntity` on the server loads the chunk); an earlier revision looked only at loaded
  chunks and so cleared the memory with "not present". The golem branch is the same.
* **The device is also updated (decision).** The original marked the *new target* for an update. The port marks both the
  target and the device, so a client sees the binding of an Interface at once.
* **Interface to interface (kept).** Two Interfaces cannot be bound to each other ("interffail"); an Interface can still
  be bound to a Dislocator, as in the original. (A Dislocator could still make such a binding; the Interface ends it, 2.5.)
* **The remembered device has a dimension (fix).** 1.7.10 kept only the coordinates, so a player who remembered a device in the
  Overworld and clicked in the Nether bound whatever device stood at those coordinates there, or loaded the Nether chunk at those
  coordinates, however far from anything loaded, to find nothing. The Binder keeps a `GlobalPos` (section 1); a device remembered in
  another dimension is not present: the Binder forgets it with "notpresent" (the golem branch the same), and the level is not asked, so no
  chunk is loaded. Run: `binderComparesTheDimension`. The flag `ConnectingGolem` of 1.7.10's stack NBT, which nothing in the port set or read
  but a unit test, is gone.
* **A remembered Golem Connector clicked on a block (kept).** The original forgets it with "notpresent", because it is
  not a `TileTransvector`: the connector is bound by sneaking and clicking the golem. The port does the same.

### 2.8 Camouflage (the shared base of the Interface, Dislocator, Remote Placer and Golem Connector)

* **Rendering: the device's model draws the camouflage (1.21.1 technique, 2026-11-01).** 1.7.10 asked the camouflaged block for the
  icon of each side from inside the device block. Until 2026-11-01 the port made the device `RenderShape.INVISIBLE` while `camo=true`
  and drew the imitated state with a block entity renderer (`RenderTileCamoDevice`, `BlockRenderDispatcher.renderBatched` every frame,
  "forced"). Now the device is always drawn from its model, which the loader glue wraps (Fabric `FabricCamoModels`: a Renderer API
  model after baking that reads the block entity's render data; NeoForge `NeoForgeCamoModels`: a `BakedModelWrapper` in
  `ModifyBakingResult` that reads its `ModelData`; `docs/HOOKS.md` section 17, the same as the Ethereal Platform's,
  `SimpleBlocks.md` 2.4). While `camo=true` the wrapper puts the imitated state's model into the chunk mesh as the block renderer
  would draw that state at the device's place: the faces that state culls against its neighbours, its tint, its render type (cut-out,
  translucent), its own light when it emits light; otherwise the device's own model. A change of camouflage rebuilds the chunk section
  on the client (`CamouflageHooks`). Kept as before: a device in the state `camo=true` whose camouflage has not arrived draws nothing; the
  device occludes nothing while camouflaged (next point); its breaking particles are its own. New for a player: the cracks of a device
  being broken are drawn on its camouflage (the block entity renderer drew none; 1.7.10 did). **Not changed, recorded:** a device is a full
  cube for smooth lighting in both looks, so it darkens the floor next to it even when it imitates glass (a real glass block does not;
  the client smoke's comparison shows exactly that and nothing else, see below); 1.7.10's camouflage blocks were not "normal cubes" and
  cast no ambient occlusion. **Seen** in the client smoke's camouflage phase (`docs/CLIENT_VERIFICATION.md`, Mesa software renderer):
  the Interface, Dislocator, Remote Placer and two Golem Connectors imitating grass (tinted), oak leaves (tinted, cut out), glass, ice
  (translucent), glowstone and an oak log on its side, first camouflaged as stone and then changed, look like the real blocks in the same
  places (`camo-devices.png` against `camo-real-blocks.png`). **Not seen:** a GPU, play, the camouflage lit by torches, other mods'
  connected textures or animated models.
* **A camouflaged device occludes nothing (fix, found by the first client run).** While `camo=true` the device's own model is
  not drawn (until 2026-11-01 the block was `RenderShape.INVISIBLE` and a block entity renderer drew the camouflage; now its wrapped
  model draws the camouflage, see the first point). The Golem
  Connector is opaque (`isOpaqueCube() == true`, kept), and its full-cube occlusion shape made the chunk renderer cull the
  face of the floor under it, so a connector in the state `camo=true` was a hole to the sky (`blocks-tileblocks-03.png`,
  first cell; the hole had exactly the colour of the sky, 179/207/255). The Interface, Dislocator and Remote Placer have
  `noOcclusion()` and never did. `CamoDeviceBlock.getOcclusionShape` now returns the empty shape while `camo=true` (the game
  caches the occlusion shape per block state, so it may depend on the state), so no device culls the faces of its neighbours
  while it shows a camouflage, and a camouflage that is not a solid cube (glass, ice, leaves) shows what is behind it.
  In its own look the Golem Connector still hides them. It also keeps **blocking light in both states**
  (`BlockGolemConnector.getLightBlock` is the maximum, the one opacity that `isOpaqueCube` gave it in 1.7.10): the default
  would have been derived from the (now empty) occlusion shape and let light through a camouflaged connector. After the fix
  the same picture shows the floor where the connector stands, as for the three other devices. In 1.7.10 the opaque
  connector also culled its neighbours' faces whatever it imitated, so a connector imitating glass probably showed the same
  hole there; that was not checked (no 1.7.10 run is possible here). The state `camo=true` with no camouflage block is not
  reachable by a click (`setCamo` sets both); a saved camouflage whose block no longer exists reads as air, which leaves the
  property true and the device drawn as nothing until an empty hand clears it (read from `readCustomData`, not tried). The
  GameTest `camouflagedDevicesLeaveTheFacesOfTheirNeighbours` pins the rule with the game's own `Block.shouldRenderFace`.
* **What can be imitated (forced).** The original accepted render types 0 (plain block), 31 (log) and 39 (quartz pillar),
  which the device drew as a cube with the imitated block's textures. The port accepts a block whose `RenderShape` is
  `MODEL` **and** whose collision shape is the full cube: stairs, slabs, fences, panes, doors, torches, plants, carpets,
  ladders, rails and so on are refused (as they were), fluids, invisible blocks and blocks drawn by a special renderer
  (chests, beds, skulls, banners, signs) too, and glass, leaves, ice, a furnace or a dispenser are accepted. **Narrower
  than 1.7.10 in one place:** render type 0 also covered blocks that were not cubes (slabs, farmland, soul sand, snow
  layers, an enchanting table), which the device showed as a cube with their textures; the port draws the model, which
  would be smaller than the device, so these are refused. The camouflageable devices of this group (`CamoDeviceBlock`) and the Ethereal Platform of the SimpleBlocks group (by its
  registry path `thaumictinkerer:platform`; the check named `thaumictinkerer:camo`, the platform's block entity type, until 2026-11-01, and the
  platform was refused all the same by its empty collision shape) are refused, as `block instanceof BlockCamo` refused them. The platform
  applies the same rule since 2026-11-01 (`deviations/SimpleBlocks.md` section 2.4; GameTest `thePlatformAndTheDevicesImitateByTheOneRuleOfTheOriginal`).
* **Variants (forced).** 1.7.10 copied the item's damage as the camouflage's metadata. A 1.21.1 block item is one block
  with its default state, with the horizontal facing set to the clicked side for the pumpkins (`BlockDirectional` was the
  one directional class among the accepted cube-like blocks; a furnace or dispenser, which was not directional, keeps its
  default facing), with the same side-to-facing rule.
* **Both hands (decision).** Only the main hand clicks count, as 1.7.10 had one hand. An empty main hand clears the
  camouflage, as an empty hand did.

### 2.9 Fake player

* **A new class, no 1.7.10 counterpart.** `TinkererFakePlayer` is a `ServerPlayer` that is never added to the level and
  never ticked: no connection (a silent listener drops every packet), no menus, no chat, invulnerable, no collision box
  (dimensions 0 by 0), no game mode change, no spawn-height lookup (so creating one does not load chunks). The Tablet's
  `TabletFakePlayer` positions it at the tablet's centre with the tablet's yaw and a pitch of -15 and keeps only slot 0.
  The 1.7.10 mixin `AccessorItemInWorldManager` is not needed (see 2.1). It is not on the ground (see 2.1).
* **The 0 by 0 size now holds (fix).** The `Entity` constructor takes the size and the eye height of the entity type (a player's 0.6 by
  1.8, eye 1.62); the `getDefaultDimensions` override is only asked when the dimensions are refreshed, which a player that is never ticked and
  never changes its pose does not do by itself. So the box was 0.6 by 1.8 and the eye 1.62 above the position, which this section used to
  call "0 by 0". The constructor refreshes the dimensions now: the box is 0 by 0 and **the eye is the position**, which the owners set (the
  Tablet's centre, 2.1; just inside the Remote Placer's target, 2.2). What changes: the start of an item's look and of a thrown item.
* **No statistics, scores, rides, beds or screens (fix).** Vanilla gave the fake player all of these. Now refused: `awardStat`/`resetStat`
  (a scoreboard objective on a statistic, blocks mined or items used, listed "[ThaumcraftTablet]" or "[Minecraft]" with the devices' work),
  `awardKillScore` (the kill-count and team-kill objectives, the player's score), `startRiding` (a Tablet in right-click mode in front of a
  boat or a minecart sat a player that is never ticked in it, and the vehicle stood still with it for good), `startSleepInBed` and
  `startSleeping` (at night it lay down in a bed in front of a Tablet, and the bed stayed occupied by a player that never wakes; in the day
  vanilla set its respawn point to the bed), `openHorseInventory` (vanilla opens it without `openMenu`). A villager or wandering trader that
  started trading with the fake player is let go at once (`interactWithEntity`), or it would stay "busy" for every real player. On NeoForge
  the extended menus (`openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)`, which Architectury's `openExtendedMenu`, NeoForge's
  `openMenu(MenuProvider, BlockPos)` and NeoForge's plain `openMenu` go through) are refused as the plain one is, and `isFakePlayer()` is true;
  both are written without `@Override` in the loader-neutral class (vanilla has neither method) and override NeoForge's by dispatch. Run:
  `fakePlayerHasNoStatsScoresOrScreens`, `tabletDoesNotBoardVehicles`, `tabletDoesNotSleepInABed` (a fake player's click on a bed in a night of
  one synchronous call, so the time of the other checks does not change, then the tablet's own clicks in the day), and on NeoForge only
  `fakePlayerIsAFakePlayerWithoutExtendedMenus` (`TileBlocksNeoForgeTests`). Fabric API's `FakePlayer` class cannot be used from common code, so
  a Fabric mod that tests `instanceof FakePlayer` does not see the devices' player as one.
* **Item cooldowns (fix).** `advanceCooldowns()` runs the item cooldowns up to the level's game time, at most 6000 ticks per call; the Tablet
  calls it every tick, the Remote Placer before each use (2.1, 2.2).
* **No advancements (decision).** The `ServerPlayer` constructor registers the criterion listeners of the profile, so a kill
  of the tablet or a block of the placer could complete an advancement of `[ThaumcraftTablet]` or `[Minecraft]`, give its
  rewards and announce it in the chat. `TinkererFakePlayer.getAdvancements()` takes the listeners off whenever the
  advancements are asked for (every trigger asks, a data pack reload re-registers them), so nothing fires. The 1.7.10
  `FakePlayer` had no advancements. The GameTest `fakePlayerEarnsNoAdvancements` checks it with "Monster Hunter".

## 3. Not done (kept visible)

* IC2 EU through the Interface (2.5): IC2 has no 1.21.1 release. (Fluids and energy are forwarded on both loaders, energy on Fabric with
  the optional Team Reborn Energy, and on NeoForge items through a capability as well; the GameTests of both loaders run them, 2.5.)
* ComputerCraft/OpenComputers peripherals of the Tablet, the Interface and the Golem Connector (2.1, 2.4, 2.5): the method
  tables exist as plain Java, nothing registers them.
* Golem data of the Golem Connector (2.4): API request A11.
* The multiblock diagram page of the Locomotive research (N1).
* The Tablet's "xN" text of the GregTech accelerator is absent (2.1).
* AE2 handling of the Locomotive's passenger (2.3).
* The rendering of the group was seen only in the client smoke harness (software renderer, one frame, no input; 2.8 for the
  camouflage): not on a GPU, not in play, no click on a screen. The Tablet's item and overlays and the screens are not
  described in this document from those pictures.

## 4. Unverified

Round of 2026-11-01, energy (2.5: the Interface's energy on NeoForge and, with the optional Team Reborn Energy 4.1.0, on Fabric): on a Fabric
GameTest server `-PtinkererGameTestFilter='tileblocksgametests.*'` ran the group's 48 GameTests ("All 48 required tests passed"; the 46 before
and the two new energy checks; the log lists `team_reborn_energy 4.1.0` among 48 mods), and on a NeoForge GameTest server
`tileblocksgametests.*,tileblocksneoforgetests.*` ran 52 ("All 52 required tests passed": the same 48 and four NeoForge-only checks, one of
them new, `interfaceEnergyCapabilityCacheFollowsTheBinding`). **Without Team Reborn Energy:** `runTinkererGameTest -PwithoutTeamRebornEnergy
-PtinkererGameTestFilter='tileblocksgametests.*,coregametests.*'` ("All 51 required tests passed"; 47 mods loaded, none of them Team Reborn
Energy, and the log names no class of it): the add-on started, recorded no failure, and the Interface's items and fluids passed as before,
while the two energy checks checked the absence (the run says the mod is missing, the add-on only suggests it, no failure). **Mutation:** with
the side passed on as `null` instead of the side asked (in `TileBlocksFabricEnergy` and in `NeoForgeCapabilities.boundEnergy`),
`interfaceForwardsEnergy` failed on both loaders at "asked on its bottom face, the interface asks the cell's bottom face: no storage", so it
sees the side; the glue was put back afterwards. **Full run** (`check runTinkererGameTest :neoforge:runGameTest assemble :neoforge:assemble
--continue`, one run with the final tree): JUnit 425 tests, 0 failures (the new one: `teamRebornEnergyIsNamedOnlyByItsGuardedGlue`); Fabric "All
358 required tests passed"; NeoForge "All 367 required tests passed"; both release jars built, neither holds a class of Team Reborn Energy or a
nested jar. The first two full NeoForge runs of that tree failed `kamiworldgametests.soulHeartsAbsorbDamageBeforeHealth` and
`kamigeargametests.soulModeGivesAHeartOnlyForAHitThatLands`: the KamiGear check replaced the global Soul Heart hook for two ticks and took the
heart the KamiWorld check gave in the same ticks (on main both passed; that the three new tests changed which tests share those ticks is inferred, not seen); its stand-in now records only
its own players and passes the others on (`KamiGear.md`), and the full run above includes that. **Not run:** another mod's cable, machine or
generator on either loader (only the GameTest energy cell); a client; the self-bound crash of the code before the fix against the energy path
(the energy glue was written with `bound()` from the start).

Round of 2026-11-01 (the multiblock diagram page, 2.3): the full add-on run `check runTinkererGameTest :neoforge:runGameTest assemble
:neoforge:assemble --continue` passed (`tinkererTest` 424 tests with `ParityComparatorTest` and `KnownDifferencesTest` counting the twelfth
documented difference; "All 357 required tests passed" on Fabric and "All 365 required tests passed" on NeoForge, `locomotiveResearchShowsItsDiagram`
among them on both; the parity report lists `LEVITATOR_LOCOMOTIVE: pages.count: original=3 | port=4` as allowed). The page was **seen** in a
Fabric dev client of Thaumcraft 4 Reload's branch `compound-pages` (software rendering under Xvfb, the add-on's release jar in its `mods`
folder): the fourth page of the entry shows "Mystical Construct", the relay, the locomotive and the relay on a diagonal with the two gaps, over
the ground plate and without an aspect row, and hovering shows "Levitational Locomotive Relay", "Empty Space" and "Levitational Locomotive".
**Not seen:** the page in a NeoForge client, and with the Thaumcraft that this repository's `thaumcraft_ref` pins (that one has no compound
drawing and skips the page).

Round of 2026-11-01 (the fixes of the review: the Interface's self-binding, the Dislocator's check, `BlockProtection` in the three devices,
the Remote Placer's bucket, the fake player's cooldowns, size, statistics, rides, beds and menus, the Binder's dimension, the saved formats,
the two forwarded item rules): on a Fabric GameTest server `-PtinkererGameTestFilter='tileblocksgametests.*'` ran the group's 46 GameTests
("All 46 required tests passed"; the 35 before, 11 new, `dislocatorChecksLetEveryBlockThrough` replaced by
`dislocatorRefusesWhatAPlayerCannotBreak`), and on a NeoForge GameTest server `tileblocksgametests.*,tileblocksneoforgetests.*` ran 49 (the
same 46 and the three NeoForge-only checks, one of them new: "All 49 required tests passed"); the loaders' `BlockProtection` backends were
installed in both runs. The JUnit classes of the `tileblocks` package (37 tests), `EnchantmentResourcesTest` (the Enchanter's mineable tag
moved to `mineable/enchantments/pickaxe`), `KamiWorldResourcesTest` and `NeoForgeGameTestCatalogueTest` passed. **Not run:** the new checks
against the code before the fixes (what the old code did, the crash of the self-bound Interface, the old bucket's ray, is worked out by
reading); spawn protection and adventure mode (only the world border of the game's rules is exercised, by moving it for one synchronous call);
a real protection mod; a client.

NeoForge (2026-10-31, `docs/NEOFORGE.md`): the group's 35 GameTests passed on a NeoForge GameTest server
(`-PtinkererGameTestFilter='tileblocksgametests.*'`: "All 35 required tests passed"), and the two NeoForge-only checks of the
Interface (2.5) passed with them in the full run of 314. Nothing of the group has been seen in a NeoForge client yet.

Round of 2026-10-30 (the first client run, 2.8): the add-on's JUnit suite (`tinkererTest`, 364 tests of all groups, 37 of
them in the `tileblocks` package) and the 35 GameTests of the group (`-PtinkererGameTestFilter=tileblocksgametests.*`: the 34
below and the new `camouflagedDevicesLeaveTheFacesOfTheirNeighbours`) ran with no failure, error or skipped entry. The new
check was also run once with the occlusion override of `CamoDeviceBlock` taken out: it failed at its first assertion
("interface camouflaged occludes nothing"), so it does see the missing override; the assertions about the Golem Connector's
floor face were not reached in that run.

An earlier round, before the camouflage check above existed: on a Fabric dedicated server (`runTinkererGameTest`, filter
`tileblocksgametests.*`) the 34 GameTests of the group (the 33 checks of `TileBlocksRuntimeChecks` and `interfaceForwardsFluids`
of the Fabric wrapper) passed, in two runs whose test areas lay at different random positions. The add-on's JUnit suite of that
round (`tinkererTest`, 296 tests then, 37 of them in the group's `tileblocks` package) passed. What the first real run showed, and was
fixed: the relay fix above (a stale partner hung the server thread), and six checks whose set-up was wrong, not the code
(the Interface was bound to a stone block, which has no block entity to stand in for and so ends the binding at once;
`GameTestHelper.relativePos` of 1.21.1 is not the inverse of `absolutePos`; `GameTestHelper.destroyBlock` drops nothing;
the relays of neighbouring checks found each other, see "keeps the last relay it finds"). **Still not run:** the Fabric glue
`TileBlocksFabric` is only exercised through `interfaceForwardsFluids`; the NeoForge capabilities of the Interface
(`NeoForgeCapabilities`, 2.5) had not run in that round (they have since, on a NeoForge GameTest server, above); the client ran only in the smoke harness (2.8: the
camouflage, since 2026-11-01 the devices' wrapped models, drew in it on a software renderer); multiplayer, survival play and any run on a GPU
have never happened. Every behaviour above that says "as the
original" is by reading the original code, not by running both; the claims about 1.7.10 engine code (the break-speed rule,
Forge's call order of `onItemUseFirst`) are from memory of that code, whose engine source is not on disk here.


## Research cell of the Remote Placer (lead decision)

The Remote Placer is registered although the pinned fork disabled it (`shouldRegister() == false`). Its research sat at
(-6, 3) in 1.7.10, the cell of Magnets, which only worked because the block was never registered. It is at (-8, 3) now, below
its parent the Animation Tablet. The registration reports a research that Thaumcraft drops because its cell is taken
(`ThaumicTinkerer.failures()`), so such a clash can not go unnoticed again.
