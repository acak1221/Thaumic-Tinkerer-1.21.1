# Deviations from Thaumic Tinkerer 1.7.10: overview

This is the entry point. Each of the eight feature groups has its own document with every difference from the pinned original
(GTNewHorizons/ThaumicTinkerer at `28f4def938e80d7a0b6f433c2b84300dfb7c35ce`, Minecraft 1.7.10) and the evidence for it. This page only
groups the kinds of difference that run through all of them, and lists what was **not** ported. Values that were copied on purpose
(costs, aspects, research positions, formulas, config switches) are not listed anywhere as deviations: they are the same as in the
original, and `docs/PARITY_REPORT.md` compares the research and recipe definitions with the source mechanically.

| Group | Document | Registrar |
|---|---|---|
| Simple blocks (dark quartz, slabs, gases, platform, forcefield, infused farmland and seeds, elemental fires, imbued effects) | [SimpleBlocks](deviations/SimpleBlocks.md) | `content/SimpleBlocks` |
| Tile-entity blocks (Animation Tablet, Remote Placer, Levitational Locomotive and Relay, Golem Connector, Transvector blocks) | [TileBlocks](deviations/TileBlocks.md) | `content/TileBlocks` |
| Machines (Aspectalyzer, Thaumic Restorer, Essentia Funnel, attractors, Tablet of Necromancy, Cursed Spirit's Blade) | [Machines](deviations/Machines.md) | `content/Machines` |
| Basic items (Spellbinding Cloth, talismans, Tome of Knowledge Sharing, Scribing Tools, Helmet of Revealing) | [BasicItems](deviations/BasicItems.md) | `content/BasicItems` |
| Wand foci (seven ordinary foci, Dislocation HUD) | [Foci](deviations/Foci.md) | `content/Foci` |
| Enchantments (fourteen enchantments, Osmotic Enchanter) | [Enchantments](deviations/Enchantments.md) | `content/Enchantments` |
| Kami gear (Ichorium tools and armour, Placement Mirror, Bottomless Pouch, Feline Amulet, Kami foci) | [KamiGear](deviations/KamiGear.md) | `content/KamiGear` |
| Kami world (Bedrock dimension and portal, Celestial Gateway, Soul Hearts, dimensional shards) | [KamiWorld](deviations/KamiWorld.md) | `content/KamiWorld` |
| Jade tooltips (the Waila providers of 1.7.10: Animation Tablet, Transvector Interface, Thaumic Restorer, Celestial Gateway, attractors) | [Tooltips](deviations/Tooltips.md) | `compat/.../tooltip`, `compat/.../jade` |
| EMI and JEI pages (the NEI pages of 1.7.10: necromancy, infused seed drops) | [RecipeViewers](deviations/RecipeViewers.md) | `compat/.../viewer`, `compat/.../emi`, `compat/.../jei` |

Related: [PORTING_RULES](PORTING_RULES.md) (the rules), [API_GAPS](API_GAPS.md) and [api-requests/](api-requests/) (what the add-on needed
from the Thaumcraft API), [HOOKS](HOOKS.md) (loader events), [lang-key-families](lang-key-families.md) (language keys).

## 1. Differences that every group shares (forced by 1.21.1)

* **Metadata is gone.** A 1.7.10 block or item with several metadata values becomes several blocks, block states or items with
  `snake_case` registry paths (`LibBlockNames`, `LibItemNames`); damage values that carried data become **data components** on the stack.
  The per-group documents have the mapping tables (`SimpleBlocks` and `TileBlocks` section 1, `Machines` section 1, `BasicItems` section 1).
* **Enchantments are data.** In 1.21.1 an enchantment is a JSON entry in a data pack. The fourteen Thaumic Tinkerer enchantments are
  JSON files with empty `effects`; their behaviour is Java code that looks the enchantment up by `ResourceKey<Enchantment>`
  (`Enchantments.md` section 1).
* **Textures and models.** The 1.7.10 textures are converted deterministically by `scripts/porting/tt_assets.py` (the map is
  `scripts/porting/tt_assets_map.json`, with provenance and a `--check` mode); block and item models are written as 1.21.1 JSON.
* **Recipes.** Crafting-table recipes are data files under `data/thaumictinkerer/recipe/`; arcane, crucible and infusion recipes are
  registered through the Thaumcraft API at start-up, as the original did through Thaumcraft 4's API.
* **Random ticks.** `BlockBehaviour.randomTick` does nothing by default in 1.21.1, whereas in 1.7.10 `updateTick` served both the
  scheduled and the random tick. The elemental fires and the gases therefore delegate `randomTick` to `tick` (found by the first GameTest
  run: an old fire never burned out; `SimpleBlocks.md` 2.3 and 2.9).
* **Mock and fake players.** The Animation Tablet and the Remote Placer act through a fake player. Its effects reach other mods only
  through the vanilla paths the fake player really takes (`state.getDestroyProgress`, `attack`).
* **Registry names** are lower-case; the original's mixed-case ids appear only in `LibItemNames`/`LibBlockNames` comments.
* **Config** is JSON (`config/thaumictinkerer.json`, `ConfigHandler`), not a Forge `.cfg`; the switches and defaults are the original's.
  There is a `/maxresearch` debug command (the original's `/maxresearch` equivalent: 99 of every aspect), only when
  `enableDebugCommands` is on. Three options the original wrote are not written any more because nothing in the port reads them
  (`tooltipIndicators.enabled`: the [TT] prefix is in the language files; `oreDictMetal.enabled`: nothing asks it, the Infused Seeds' loot names the
  shared tag `c:ingots/copper` either way; `repairTconTools.enabled`: no Tinkers' Construct); an old file with them still loads. No behaviour changed:
  the three values were read and written back, never used. A file that is not valid JSON, or not a JSON
  object, is **not** overwritten with the defaults: the defaults hold for that run, an error in the log names the file, and the file is left to be fixed.
* **Config on a server** (new; the original had no sync, every side read its own `.cfg`, so a client with `modFlight.enabled` on could fly with the
  Focus of Flight on a server that had it off). On join the server sends its values (`ConfigSync`, `PacketConfigSync`): the options a client evaluates as
  well (`modFlight.enabled`, `focusOfDislocationBlacklist`, `forbiddenDimensions`, `cropsAllowBonemeal.enabled`) are then the server's for the client's own
  levels wherever the code reads them through the side-aware accessors (`ConfigHandler.flightEnabled(level)` and the others; the Focus of Flight and the Robe
  of the Stratosphere do); the options that decide what each side registers at start-up
  (`imbuedFire.enabled`, `shareTome.survival.enabled`, `kami.forceenabled`) cannot change while the game runs, so a client whose own differ leaves the
  server with a translated message that names them (`thaumictinkerer.config.mismatch`). Client display options (`soulHeart.height`,
  `placementMirror.blocks.show`) stay the client's. The payload and the comparison are JUnit-tested, and the client side was run with real clients on a
  real dedicated server on both loaders (`MULTIPLAYER_VERIFICATION.md`): the synced values hold on the client and are cleared when it leaves, and a client with
  `imbuedFire.enabled=false` leaves with the translated message. For `kami.forceenabled` **the direction matters**: a client that has Kami **off** while the
  server has it on lacks 50 registry entries of the server, and the loader's registry sync refuses it in the configuration phase, before the game starts and so
  before the add-on's payload can arrive, with the loader's own message (Fabric: "Received 50 registry entries that are unknown to this client ... thaumictinkerer";
  NeoForge: "The server send registries with unknown keys: ..." with the 50 keys); the add-on's message appears when the client has Kami **on** and the server off.

## 2. Deliberate decisions of the port

* **Research cell of the Remote Placer** moved from (-6,3) to (-8,3) because Thaumcraft 4 Reload's own Magnets entry occupies (-6,3); the
  original relied on Thaumcraft silently dropping one of the two. A research that Thaumcraft drops because its cell is taken is now a
  registration failure (`ThaumicTinkerer.failures()`), so a clash cannot stay unnoticed (`TileBlocks.md`, last section).
* **Cleansing Talisman** removes harmful effects in registry-id order. In 1.7.10 the potion ids were small integers in a map with a fixed
  order; in 1.21.1 the active effects sit in a map keyed by holder identity, so without an explicit order the result would change from run
  to run (`BasicItems.md` section 3).
* **Relay partner out of reach.** A Levitational Relay whose partner lies beyond the search range ends the link at once. The original
  loaded the partner's chunk and cleared the link when it found no relay; the port does not ask unloaded chunks, and a stale link made the
  beam loops walk billions of cells (`TileBlocks.md` 2.3).
* **Wand cap and rod textures** derive their default path from the tag in lower case (a change in the Thaumcraft API: tags such as
  `ICHOR` are legal there, resource paths are not).
* **The research a Kami entry requires** is the union of all non-Kami research: every Kami entry requires every other research that is registered
  (Thaumcraft's five tabs and the add-on's tab, without lost and virtual entries, Kami entries, `MINILITH*` and `*KAMI`), the 19 block-owned research of the
  add-on included. 1.7.10 gave the 23 item-owned Kami entries only the item-owned research that happened to be registered before them, in `HashMap`
  order, so the port is stricter than any set the original could produce and a Kami entry is never available earlier (`KamiGear.md` section 12;
  one of the 12 documented differences of `PARITY_REPORT.md` section 3).
* **The multiblock diagram of the Levitational Locomotive** is the fourth page of its research. The original builds it
  (`ResearchHelper.LeviationaryHelp`) but no research of the original uses it, so the 1.7.10 entry has three pages; the port shows it now that
  Thaumcraft 4 Reload draws `COMPOUND_CRAFTING` pages as TC4 did (a Thaumcraft without that drawing skips the page). One documented difference
  of the parity check (`TileBlocks.md` 2.3).
* **Slow Fall's landing rule** (**a decision the owner may want to change**). The original lowered the fall distance on every tick of the fall, down to a
  floor of 2.9, so a long fall ended at 2.9 and did no damage. The loader event comes once, at the landing, so the port applies the end result at once: a
  landing of 2.9 blocks or more, for a player with Slow Fall on the armour who does not sneak at the landing, becomes a landing of 2.9
  (`EnchantmentFormulas.slowFallLanding`). Falls of 3 to 4 blocks that 1.7.10 may have left with some damage do no damage here, and the level of the
  enchantment no longer matters for the distance. That is by a reading of the formulas; the original was not run (`Enchantments.md` sections 4 and 12).
* **The order of the listeners of the loader events** is a decision about where each group registers (`HOOKS.md` section 2): LIVING_DAMAGE runs the Enchantments'
  listener before KamiWorld's Soul Hearts, and LIVING_DROPS the Cursed Spirit's Blade (Machines) before the dimensional shards (KamiWorld), both as in 1.7.10 (the
  registration phases, `preInit` and `init`, were read in the pinned Thaumic Tinkerer source; that Forge ran listeners of equal priority in registration order is
  recalled, not read, `HOOKS.md` section 2); the shard order is pinned by the GameTest `aReapedEndermanAndZombifiedPiglinStillRollTheirDimensionalShard` and depends on the order of
  `ContentGroups.ALL` and on both groups registering in `register()`, not in `ready()`. LIVING_JUMP runs the Gem Boots (`KamiGear.register()`) before Ascent Boost
  (`Enchantments.ready()`, through `ModEnchantments.initJump()`), as the original's `preInit` and `init` did: `(0.42 + 0.3) × factor`, 1.08 for Ascent Boost I and
  1.80 for III, pinned by `gemBootsAndAscentBoostLeggingsComposeTheirJumps` (the first wiring had it reversed, 0.93 and 1.35). Shockwave's `LOW` priority of 1.7.10 is
  **not** reproduced against the listeners of other groups; it has no live effect, because the boot tags of Slow Fall and Shockwave exclude the Gem Boots.
* **Licence split.** The port is CC BY-NC-SA 3.0 (`LICENSE`; `NOTICE.md` says which files are under which licence). It has its own repository
  (this one); the Thaumcraft mod stays MIT. The add-on's public-API additions in the mod (for example `IBauble.equipsOnUse`, `IFocusPouch`, `BaublesApi.getWorn`,
  `ThaumcraftApiHelper` scan and discovery helpers, `WarpEffects`) contain no Thaumic Tinkerer code.

## 3. Behaviour that depends on loader events

Architectury has no events for several things the original used Forge events for. The groups first worked around each gap and wrote the loss down;
`docs/HOOKS.md` describes the loader layer that closes the gaps with real events (`LIVING_DAMAGE`, `LIVING_ARMOR_ABSORB`, `BREAK_SPEED`, `LIVING_JUMP`,
`LIVING_FALL`, `USE_ITEM_TICK`, `LIVING_DROPS`) and two registries (floating ticks, item renderers). It exists for **Fabric** (Mixins) and for **NeoForge**
(handlers of NeoForge's events and three mixins, `HOOKS.md` section 9; run on a NeoForge GameTest server only, server side). **The groups are rewired to it now**: Enchantments, KamiWorld, KamiGear, Machines and Foci no longer use
their workarounds (SimpleBlocks, TileBlocks and BasicItems never needed an event of this layer that was missing; SimpleBlocks' Imbued effects and the Cursed Spirit's
Blade still use the Architectury `EntityEvent.LIVING_HURT` for 1.7.10's `LivingAttackEvent`). `HOOKS.md` section 10 says, per group, which workaround was removed, which event
replaces it and which check pins it, and what rests on the NeoForge posters. What the events change against the original, each in the group's document (what is said
about Forge's and Minecraft 1.7.10's own behaviour in this section is **recalled, not read**: its source is not on this machine, section 6, point 5):

* **The damage event stands before the shield, the invulnerability frames and the armour**, where Forge's stood after the frames had taken the last hit off.
  A cancel is a refusal of the whole hit and starts no frames. So the Soul Hearts keep the original's frames themselves (`KamiWorld.md` section 5), a covered
  hit gives no knockback and does not wake a sleeper (the sleeper from the 1.21.1 code, not run), and the damage factors of the Enchantments meet two bands of a second blow inside the frames where
  1.7.10 behaved differently, and a sweep of a sword is a hit for each entity it reaches (`Enchantments.md` section 2, `HOOKS.md` section 3).
* **Armour absorption of Kami armour (`ISpecialArmor`) is ported through `LIVING_ARMOR_ABSORB`** (`KamiGear.md` sections 3 and 13, `HOOKS.md` section 14): every
  Ichorcloth piece takes `0.0425 × its armour points` of a blow, flat, the set 85%, and the pieces never wear. It was listed as "not written" before the
  event existed. It is posted on both loaders (on NeoForge by the same wrapper of the armour step as on Fabric, not by a reduction function of NeoForge's damage
  container, so that Thaumcraft's ward pass keeps its order; the KamiGear GameTests passed on NeoForge, `HOOKS.md` section 14). The caveats, all in `KamiGear.md` section 3:
  * **UNVERIFIED: the slot index that 1.7.10 gave `getProperties` and `getArmorDisplay`.** The port keeps the intended assignment (chestplate 8, leggings 6);
    if Forge passed the `armorInventory` index, a chestplate alone took 25.5% and leggings alone 34% (section 6, open point 1).
  * Damage that bypasses armour (starvation, drowning, magic, falling, the void) is not reduced, and the event is not posted for it; whether 1.7.10 reduced such a
    hit by 85% could not be checked (section 6, open point 2). Mobs that wear the pieces get the 85% too (section 6, open point 3).
  * The armour points and the enchantability of the materials are a display (the HUD and the tooltip) and never stack with the 85%; the stand-in toughness of the
    first port is gone. Thaumcraft's own ward pass (priority 1) runs before the event (`LivingEntityArmorMixin` has priority 900).
* **Gem Boots and Ascent Boost, Slow Fall, Shockwave**: see the decisions of section 2 (landing rule, order of the listeners).

## 4. Not ported (kept visible)

* **Other mods' integrations**: Forge Multipart parts (candle and Nitor parts), EnderStorage page of the Ender Rift focus, Botania,
  Blood Magic, AE2 (`IMovableTile`, passenger handling), Tinkers' Construct repair, GregTech accelerator text, EnderIO planter,
  Electro-Magic Tools hoe.
* **Computer peripherals** (ComputerCraft / OpenComputers) of the Animation Tablet, Transvector Interface, Golem Connector, Aspectalyzer and
  the attractors. The method tables exist as plain Java; nothing registers them. The `peripherals` research category exists and is empty. The ComputerCraft turtle
  tool of the Fume Dissipator (`FumeTool`, `FumePeripheral`) is not ported either (`SimpleBlocks.md` section 3).
* **Energy through the Transvector Interface** (RF and EU in 1.7.10) is written for **neither** loader; fluids through it are forwarded on both (Fabric
  `TileBlocksFabric`, run in the GameTests; NeoForge `NeoForgeCapabilities`, which also offers the Interface's items as a capability, run in the NeoForge GameTests; `TileBlocks.md` sections 2.5 and 3, `api-requests/TileBlocks.md` section 6, `NEOFORGE.md`).
* **The Kami foci's `canApplyUpgrade` override** (the original answered true for every upgrade) is dropped; the base rule applies (`Foci.md` section 10, `KamiGear.md` section 9).
* **Gregtech hazard protection and the Thaumic Boots mod** (`IHazardProtector`, `IBoots`) for the Ichorcloth armour: neither mod exists for 1.21.1 (`KamiGear.md` section 3).
* **Golem API** (Golem Connector data): API request A11/A12 is deferred; the Golem Connector block exists without golem data.
* **Configurable dimension id and disabling the Bedrock dimension** (a datapack dimension has a resource id and is switched off by the
  data pack; `KamiWorld.md` section 9).
* **NeoForge** (listed here while it was not ported; it is ported now, and what is still missing there is what is missing on Fabric too, below): the subproject
  `:neoforge` builds the add-on for NeoForge (`neoforge/build.gradle`, the same shared code plus `neoforge/src/main`; release jar
  `neoforge/build/libs/ThaumicTinkerer-neoforge-<version>.jar`, `NEOFORGE.md`), and the posters of `docs/HOOKS.md` are written (section 9 there:
  handlers of `LivingIncomingDamageEvent`, `PlayerEvent.BreakSpeed`, `LivingJumpEvent`, `LivingFallEvent`, `LivingEntityUseItemEvent.Tick` and `LivingDropsEvent`, and
  mixins for the armour step, `lastHurt` and the kick counter); a fourth NeoForge mixin gives the Infused Farmland its soil rule (`SimpleBlocks.md` section 2.6). On a
  NeoForge GameTest server all GameTests passed (2026-10-31: 314, the 312 of the Fabric run plus 2 NeoForge-only checks of the Interface's capabilities; 2026-11-01, after the defect audit: 362), so the Soul
  Hearts, the shard drops, the enchantment effects, the Gem Boots, the Ichorcloth, the blade's reaping and the Uprising's reset ran there on the server; their client
  side was not run (the NeoForge client smoke draws the content in a world, `CLIENT_VERIFICATION.md`, but does not play these effects). Where NeoForge differs from Fabric (`HOOKS.md` section 9): `LIVING_JUMP` is also posted for
  slimes, magma cubes and ridden horses and camels; NeoForge's damage container does its own float round trip after the armour step and leaves a NaN health after an
  infinite blow, whatever the add-on returns. **Loader glue that is not an event** (`HOOKS.md` section 9, "Other loader glue"): the NeoForge side of the `ItemRenderers`
  backend (the Mob Display), of the Kami gear's wings layer and ghost blocks and of the Transvector Interface's fluids (and items) is written (`NEOFORGE.md`, "Client glue
  and block capabilities on NeoForge": compiled, the TileBlocks GameTests pass on both loaders, the Mob Display drawn in the NeoForge client smoke; the wings and ghost blocks not seen on either loader);
  energy forwarding, the flammability hook of the elemental fires and the special effects of the Bedrock dimension are written for neither loader. The one failure
  of the first full NeoForge runs, `simpleblocksgametests.acropneedsfarmland` (wheat stood on the Infused Farmland, because NeoForge lets crops stand on any
  `FarmBlock`), is fixed by that soil mixin (`NEOFORGE.md`, "Last run").

## 5. Verification status and what is not verified

What was run, with the numbers of the last run (2026-10-30 and 2026-10-31; on Fabric except the NeoForge row; each line is what a log or a result file recorded, nothing else is claimed).
The runs up to 2026-10-30 were made while the add-on was still part of the Thaumcraft 4 Reload repository (its tasks then had the prefix
`:fabric:`, its build output was below `fabric/build/`; `NOTICE.md`, "History"); the rows of the add-on's own suites give the runs of 2026-10-31 in
this repository. The two rows marked **Thaumcraft repository**
were runs of Thaumcraft's own suites, which belong to that repository and not to this one:

| What | Result | Where it is recorded |
|---|---|---|
| Add-on JUnit (`tinkererTest`) | 424 tests, 0 failures, 0 skipped (2026-11-01, after the defect audit and its follow-up, `AUDIT.md`; 423 after the audit, 411 before it, 364 before the integrations). Since 2026-10-30 16:28 the documents, language fragments and source trees the tests read are inputs of the task (before, Gradle could restore a result from its cache after a document was edited) | `build/test-results/tinkererTest/*.xml` |
| Add-on GameTests on a Fabric dedicated server (`runTinkererGameTest`) | "All 357 required tests passed" (2026-11-01, with the Levitational Locomotive's diagram page and its check `locomotiveResearchShowsItsDiagram`; 356 after the defect audit and its follow-up, `AUDIT.md`; 354 after the audit, 312 before it, 299 before the 7 Jade-tooltip and 6 recipe-viewer checks) | Gradle log of the run, `build/tinkerer-gametest-results.xml` |
| Parity with the 1.7.10 source (research, recipes, object tags) | 74 research entries and 127 recipes compared field by field; 12 documented differences (11 before the Levitational Locomotive's diagram page of 2026-11-01), 0 undocumented | `PARITY_REPORT.md`, `ParityGameTests` |
| **Thaumcraft repository:** Mod regression after the add-on's changes (`:common:test :fabric:runGameTest`) | JUnit 2761 tests, 5 skipped, 0 failures (the results of 2026-10-29, Gradle found the inputs unchanged); GameTests: "All 515 required tests passed" (2026-10-30 16:26, new world). Two earlier runs the same afternoon on the world that a previous run had left behind failed `mazeAuditGeneratesRealRegionAndReloadsMap` (the test opens, and so removes, the boss lock it generated; the next run finds no lock). Not caused by the add-on; `fabric/build.gradle` now deletes `run-gametest/world` before every run, and `fabric.gradle` does the same for the add-on's run | Gradle logs, `fabric/build/gametest-results.xml` of that repository |
| **Thaumcraft repository:** Python porting scripts (`scripts/porting`, all tools of that repository, the add-on's two among them) | 296 tests OK. The add-on's two tools with their tests are in this repository now: `tools/porting.sh test` ran 153 tests OK (2026-10-31, with both checkouts present) | run output |
| Client, Fabric dev client under Xvfb + Mesa llvmpipe (`tools/client-smoke.sh`) | 726 checks PASS, 0 FAIL, 0 harness errors, 1 NOT_RUN (tool-mode tooltip needs a key press), 80 pictures (read by the engineers in the run before the last, which had 721 PASS); the first run had 3 FAIL and a number of defects seen only in pictures (all fixed, see `CLIENT_VERIFICATION.md`). The last run added a check that the six infused seeds have six different models; with the old model file it fails (5 `override_collapsed`), with the fixed one it passes | `CLIENT_VERIFICATION.md`, `build/tinkerer-client-smoke/report.json` (not committed) |
| Client with the optional mods (2026-10-31, same harness, phase `integrations`) | with EMI and Jade (`-- -PwithEmi -PwithJade`): 737 PASS, 0 FAIL, 1 NOT_RUN, 88 pictures; with JEI (`-- -PwithJei`): 730 PASS, 0 FAIL, 1 NOT_RUN, 82 pictures; without them: 725 PASS, 0 FAIL, 1 NOT_RUN, 80 pictures. In the viewers: 30 necromancy and 45 seed-drop pages, and all 82 arcane/crucible/infusion recipes of the add-on in Thaumcraft's own categories; Jade's tooltip text read for six blocks (`Tooltips.md`, `RecipeViewers.md`) | `CLIENT_VERIFICATION.md` |
| Multiplayer check (`tools/multiplayer-smoke.sh [--neoforge]`, 2026-11-01) | A real dedicated server (spawn protection 16, nobody playing is an operator) and five real clients over the loopback socket, on each loader, then a second server with Kami off: 67 PASS, 0 FAIL, 0 harness errors on Fabric and 67 PASS, 0 FAIL on NeoForge (spawn protection inside, outside and across the edge for the Focus of Dislocation, the Placement Mirror and the Awakened Ichorium Pickaxe; the config sync and its clearing; the Enchanter's and the Animation Tablet's packets; a second player seeing the tablet change; three registration-option mismatches). No defect of the add-on's code found; one statement of this document was wrong (section 1, "Config on a server": a client without Kami is refused by the loader's registry sync, not by the add-on's message) and is corrected | `MULTIPLAYER_VERIFICATION.md`, `build/tinkerer-multiplayer/` and `neoforge/build/tinkerer-multiplayer/` (not committed) |
| NeoForge (`:neoforge`) | Written: the build, the posters, four mixins, the client glue and the Interface's capabilities. On a NeoForge GameTest server (`./gradlew :neoforge:runGameTest`, 2026-11-01, after the defect audit and its follow-up): "All 365 required tests passed" (the 357 generated from the Fabric wrappers plus 8 NeoForge-only checks; 364 before the diagram page, 362 after the audit; on 2026-10-31, 314 of 314; the earlier runs and their failures are in `NEOFORGE.md`, "Last run"). The jar `neoforge/build/libs/ThaumicTinkerer-neoforge-0.1.0-alpha.7.jar` is built by `:neoforge:assemble`. Client: the client smoke on NeoForge (2026-10-31): 730 PASS, 0 FAIL, 1 NOT_RUN, 80 pictures; with EMI and Jade 739, with JEI 735, 0 FAIL; after the defect audit (2026-11-01): 731 PASS, 0 FAIL (`CLIENT_VERIFICATION.md`) | `NEOFORGE.md`, `HOOKS.md` sections 9, 10, 14 |

**Not verified** (a `PASS` of the client harness says a lookup answered and nothing threw; it is not a person looking at the game on a GPU):

* The client on a **real GPU**, with **sound** (the add-on has no sounds of its own; the sound device fails to start on the test machine), with **input** (clicks on the screens,
  keys, drag, scroll are never sent; the multiplayer check presses keys and clicks slots and two buttons through the game's own handlers), in **multiplayer** beyond the scenarios of the multiplayer check (`MULTIPLAYER_VERIFICATION.md`), with **other mods** beyond EMI, JEI and Jade (those three were run, see the table above), and in languages other than English and Russian
  (the de, fr, ko and zh files are compared as files only).
* The screens were drawn, not used: the Enchanter, the Tablet of Necromancy, the Aspectalyzer, the Mob Magnet, the Remote Placer, the Ichor Pouch and the Celestial Gateway were not judged
  against the 1.7.10 layouts (the Ichor Pouch was compared with the original code and texture: same hash, same slot coordinates).
* Particles over time, the Bedrock dimension in use (sky, light, the portal), the Placement Mirror's ghost blocks, the wings layer, the Mending and Ender Rift focus layers on a wand
  (their `_depth` sprites are now filtered out of the block atlas; that the wand renderer still loads them is argued from the code, not seen), break particles of the Mob Display,
  a Mob Display in a hand, on the ground or in an item frame, the camouflage devices with a camouflage block set (the block rows set none).
* **Mob Display scale:** the Horse's head, the Witch's hat and the Mooshroom's mushrooms still overflow a 16x16 slot by up to about 2 slot pixels. The scale values are the original's;
  changing them is a decision for the owner.
* The network behaviour of the packets between a real client and a real server, beyond the scenarios of the multiplayer check (`MULTIPLAYER_VERIFICATION.md`:
  the config payload, `PacketEnchanterAddEnchant`, `PacketTabletButton`, the block-entity updates of the Enchanter and the Animation Tablet, and the
  registration of every payload of the add-on on both sides). The other packets (the Remote Placer's buttons, the Enchanter's start, the Kami armour
  toggles, the Celestial Gateway, Soul Hearts, the shadow beam) are registered on both sides but were not sent.
* The client's copy of the events the groups listen to (`Enchantments.md` section 12): a server cannot show it, and the client harness does not exercise them.
* On **NeoForge**: the client side of every event (the client smoke draws, it does not play), the Kami wings and ghost blocks, a
  dedicated server with real clients beyond the scenarios of the multiplayer check, other mods, EMI, JEI and Jade among them, and other mods' pipes and tanks at the Interface (`NEOFORGE.md`, `HOOKS.md` section 9).
* The **CI workflow** (`.github/workflows/ci.yml`) builds and tests both loaders on every push to `main` and on pull requests (green since run 6; run 8 on 2026-11-01 for
  `b231f66`). It runs JUnit and the GameTests of both loaders, not the client smoke or the multiplayer check (they need Xvfb and take minutes each).

The per-group documents say what each group's own checks were; they record runs in different amounts (KamiWorld, Foci and the first round of KamiGear with numbers; Machines and BasicItems
record no result of their own server-side tests; Enchantments a statement without a result): `README.md` and `HOOKS.md` section 12 say what is recorded.

## 6. Open points (need the owner or a source that is not on this machine)

None of these is a known defect. Each is something the port decided or assumed without being able to check it, or a question for the owner.

1. **The slot index of `ISpecialArmor.getProperties` and `getArmorDisplay` (UNVERIFIED).** The original reads the material's array by armour type (helmet 0,
   chestplate 1, leggings 2, boots 3: 3/8/6/3). As far as remembered, Forge 1.7.10 passed the index in `armorInventory` (boots 0 ... helmet 3), which would have
   swapped chestplate and leggings (a chestplate alone 25.5%, leggings alone 34%; the full set, a helmet and boots are the same either way). No Forge 1.7.10
   source or jar is on this machine. The port keeps the intended assignment; to swap, change `CHESTPLATE` and `LEGGINGS` in `KamiArmorMaterials.create` and the
   expectations of `KamiGearLogicTest` and the GameTests (`KamiGear.md` section 3).
2. **Did Forge 1.7.10 reduce armour-bypassing damage by 85%?** The original `getProperties` has no look at the damage source; whether Forge's damage path called
   `ApplyArmor` for unblockable damage could not be checked. The port does not reduce it. The one place to change it is the `BYPASSES_ARMOR` test in
   `TinkererEvents.postLivingArmorAbsorb`.
3. **Did Forge 1.7.10 consult `ISpecialArmor` for mobs?** To the best of the authors' knowledge it did so from the player's damage path (not verified). The port
   answers for every wearer, so a zombie in the set takes the 85% too.
4. **Recipes with no research.** The four crucible recipes `INFUSED_POTIONSPOT0..3` and the infusion recipe `INFUSED_INKWELL` have no research entry in 1.7.10
   and none in the port (`PARITY_REPORT.md` section 4), so Thaumcraft never unlocks them. The inkwell is documented (`BasicItems.md` section 6); the four
   potion recipes are documented nowhere else. Whether they are meant to be obtainable is the owner's decision, not a parity defect.
5. **The 1.7.10 side of the invulnerability-frames bands** (`HOOKS.md` section 3, `Enchantments.md` sections 2 and 12): taken from `HOOKS.md` and recalled from
   `EntityLivingBase.attackEntityFrom`, not read in the Minecraft 1.7.10 source (not on this machine). The same holds for every statement about Forge's
   `LivingHurtEvent` in `HOOKS.md` section 3 (its place after the frames and after a player's sword block, that a cancel only skipped `damageEntity`, that
   `ISpecialArmor` sat after it) and for the claim that Forge ran listeners of equal priority in registration order, which the listener orders of section 2 lean on.
   The 1.21.1 side has checks (`HOOKS.md` section 12 says what is recorded as run).
6. **Slow Fall's landing rule** (section 2) is a decision of the port that the owner may want to change.
7. **The Cursed Spirit's Blade cannot take the vanilla sword enchantments** (Sharpness, Smite, Looting, Fire Aspect, Unbreaking, Mending ...), which the 1.7.10 `ItemSword`
   took: it is in none of the vanilla sword tags. The fix touches files of other groups (`data/minecraft/tags/item/swords.json` and the Vampirism tag, which must keep refusing
   the blade) and is a request to the lead, **not done** (`Machines.md` section 8, `api-requests/Machines.md` section 5).
8. **The gateway tooltip of the original (Waila, now Jade) was inverted**: it showed "allow incoming" when the gate's `locked` flag is true, which is the opposite of what the flag
   does. Decided: the Jade port shows what the flag does (`deviations/Tooltips.md`). Two more decisions from `KamiWorld.md` section 10:
   **`PORTING_RULES` 1.3** (groups reach each other by registry path) has no exception for the four KamiGear helpers that the gateway uses (`ItemSkyPearl`, `SlotSkyPearl`,
   `ModelSpinningCubes`, `KamiHooks`); and the gateway's map no longer hides a destination at `y == -1` as the original did (`y == -1` is a real height in 1.21.1,
   `KamiWorld.md` section 9).
9. **The `ichor_nuggets` recipe and the Celestial Gateway's loot table are conditional on their items**: each file carries a load condition for both loaders
   (`fabric:load_conditions` / `neoforge:conditions`), so with Kami off they are skipped instead of logging a parse error at data load (`KamiGear.md` section 11).
10. **Two designs of the camouflage base** exist, `BlockCamo` in SimpleBlocks (the Ethereal Platform) and `CamoDeviceBlock` in TileBlocks (the Remote Placer, the Dislocator): the
    lead may merge them (`SimpleBlocks.md` section 2.4); until then the platform's block entity type `thaumictinkerer:camo` is valid for the platform only.
