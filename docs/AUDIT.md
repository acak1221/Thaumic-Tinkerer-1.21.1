# Defect audit and 1.21.1 adaptation (2026-11-01)

Six reviewers read the whole add-on, one area each (core and loader glue, simple blocks, tile blocks, machines and basic items,
foci and enchantments, Kami gear and world). They looked for two kinds of problems: **defects** (wrong logic, crashes, duplication,
desync, trusted packets, protection bypass, performance traps) and places **not adapted to 1.21.1** (raw `CustomData` where a typed
data component belongs, `new Random()` instead of the level's `RandomSource`, hand-written serialisation, 1.7.10 helpers). The lead
checked the critical and major findings in the code; six fixers then fixed them, each with tests, and the lead ran everything
together. Details are in the group documents (`deviations/*.md`), `HOOKS.md` and `NEOFORGE.md`; this file is the summary.

## Fixed: critical and major

| Area | Problem | Fix | Test |
|---|---|---|---|
| Kami gear | The awakened Ichorium Pickaxe (and the Protoclay, the Hoe of Growth handler) acted on any block position the client sent: left click is posted before the game's reach and spawn-protection checks on both loaders. A modified client turned bedrock into portals or air at any distance and made the server load chunks | Reach, loaded and game-rule checks before the block is read; bedrock conversion asks `BlockProtection.mayBreak` | `awakenedPickaxeIgnoresBedrockClicksThePlayerCannotMake` and three more |
| Tile blocks | A Transvector Interface moved by a Dislocator onto its own target (or bound to another Interface) recursed until the server crashed, again after every restart | No forwarding through such a binding; the server ends it, so a saved world repairs itself | `interfaceMovedOntoItsOwnTargetEndsTheBinding`, `interfacesBoundToEachOtherEndTheBinding` |
| Tile blocks | The Dislocator moved blocks no player can break (bedrock: a hole into the void; barriers, end portal frames, command blocks) | Refuses a destroy time below zero and game-master blocks (a deviation from 1.7.10) | `dislocatorRefusesWhatAPlayerCannotBreak` |
| Kami gear | The Placement Mirror stored the whole block state and charged one item: double slabs, eight-layer snow, filled beehives and charged anchors were duplicated | The remembered state keeps orientation only; blocks whose properties carry contents are refused | `mirrorPlacesASingleSlabForADoubleOne`, `mirrorPlacesOneLayerOfSnowAndRefusesFilledBlocks` |
| Foci | The Focus of Dislocation took Thaumcraft's warded glass, arcane door and arcane pressure plate (a regression from 1.7.10), and placed any load a creative non-op wrote into it (spawners, command blocks) | Refuses what the player cannot break (`getDestroyProgress`); a load is put down only if a cast could have taken it; block-entity data restricted by vanilla needs the server's seal of a real take | `dislocationRefusesWhatThePlayerCannotBreak`, `dislocationPutsDownOnlyWhatItCouldHaveTaken`, `dislocationMovesASpawnerItTookWithItsMob` |
| Foci, Kami gear, tile blocks | Dislocation, Efreet's Flame, the Placement Mirror and the devices changed blocks without asking protection mods | New shared `hooks/BlockProtection` (the game's rules + the loader's own break/place events as a question; Fabric and NeoForge backends); all of them ask it | `mayBreakAndMayPlaceFollowTheListeners`, `dislocationAndEfreetsFlameAskTheProtectionOfTheServer`, `mirrorPlacesNothingWhereThePlayerMayNotBuild`, … |
| Core | The server's options were never sent to clients: with flight disabled on a dedicated server, the Focus of Uprising flew for free; options that change registration could differ between client and server | Gameplay options sent on join and read on the client; a client whose registration options differ is disconnected with a message | `ConfigHandlerTest` |
| Core (NeoForge) | Only the Transvector Interface had an item capability, so modded pipes could not reach the Restorer, Funnel, Enchanter, Tablet, Placer, … | Item capability for every add-on container | `ItemCapabilitiesNeoForgeTests` |
| Core | The fake player of the Tablet and the Placer entered scoreboards, boarded boats and carts (freezing them), slept in beds for good and opened NeoForge menus | All refused; `isFakePlayer()` true for claim mods | `fakePlayerHasNoStatsScoresOrScreens`, `tabletDoesNotBoardVehicles`, `tabletDoesNotSleepInABed` |
| Simple blocks | Seed tendencies were unbounded: a creative or `/give` stack hung the server at harvest | Amounts clamped to 0..50 everywhere, loops bounded, command refuses negative counts | `anOutOfRangeSeedStackCannotHangTheHarvest` |
| Simple blocks | Water and lava destroyed the Ethereal Platform | `forceSolidOn()` (wood held liquids in 1.7.10) | `thePlatformHoldsBackWaterAndLava` |
| Machines | The Thaumic Restorer mended the Spellbinding Cloth (endless enchantment wiping) | Tag `thaumictinkerer:repairer_blacklist` | `theRestorerNeitherTakesNorMendsTheSpellbindingCloth` |
| Machines | The Tablet of Necromancy's pedestal search could be built into a lag machine | One scan per cycle, unordered triples, the same first match as 1.7.10 | `theTabletPicksTheSameSetAsTheOriginalSearch`, `theTabletLooksUpEveryPositionOnceACycle` |

## Fixed: minor

Restorer syncs only on change; blocking with the Cursed Spirit's Blade no longer cancels `/kill` and void damage; the Spellbinding Cloth
keeps curses (as the grindstone does); the Bottomless Pouch cannot be nested in shulker boxes or bundles (and refuses them); Soul Mode
grants only for a hit that lands (no armour-stand farming); switched-off Gem Boots give the walking step again; the camouflage renderer no
longer reseeds the client level's random; trees grow through gas clouds; the Remote Placer pours a bucket into its target (the fake
player's eye height was wrong); fake players' item cooldowns run out; the Interface forwards `canTakeItem` and `getMaxStackSize`; the
Enchanter's packet refuses what its screen does not offer; a config file that fails to parse is no longer overwritten; three unused options
were removed; NeoForge no longer keeps a damage container per cancelled hit; "cannot dig" on a block of hardness 0 is progress 0, not NaN.

## Adapted to 1.21.1

- Typed data components instead of raw `CustomData`: Soul Mould pattern (`ResourceKey<EntityType>`), Mob Display mob, the Blade's
  activated flag (with an item property instead of `custom_model_data`), the essences' last tablet (`GlobalPos`), the Binder's device
  (`GlobalPos`), Focused/Dispersed Strike memory and Tunnel direction. The 1.7.10 `ItemNBTHelper` is gone.
- `EnumMobAspect` keys entity types by `ResourceKey`.
- The level's `RandomSource` instead of `new Random()`/`Math.random()` everywhere in gameplay.
- Vanilla helpers for saved positions and UUIDs; the Enchanter's icons are drawn without reading PNG headers.

## Follow-up: the two items left open (2026-11-01, branch `kami-followups`)

| Area | Was | Now | Test |
|---|---|---|---|
| Kami world | The Bedrock dimension's ore clusters were a hand-written decoration: `ChunkGeneratorBedrock` overrode `applyBiomeDecoration` and seeded a `java.util.Random` from the world seed as 1.7.10 did | A registered world-generation feature `thaumictinkerer:bedrock_ore_clusters` (`BedrockOreClustersFeature`), its configured and placed feature as data, listed by the Bedrock biome in the underground-ores step and seeded by the game; the same ore rules (`EnumOreFrequency`, the ore tags, `bedrockOreDensity`, `bedrockOreBlacklist`), so the same kind and amount of ore at other positions. The feature type is registered with Kami off too (the biome names it). The generator stays, without a decoration of its own: `minecraft:flat` would change the sea level from 1 to -63 (`KamiWorld.md` §2) | `bedrockOreClustersAreAFeatureOfTheBiomeWithTheOresOfTheTable` (GameTest: registration, the biome's steps, sixteen new chunks with a blacklist by name, tag and block id: only allowed ores, none blacklisted, 47,230 ore blocks against about 47,387 expected); `theOreClustersAreAFeatureOfTheBiomeInTheUndergroundOresStep` (JUnit, the data) |
| Kami gear | Tree mode of the Ichorium Axe asked the game's rules (inside the API's `breakFurthestBlock`) but no protection mod | Every log is found with the API's lookup `findFurthestBlock`, asked about with `BlockProtection.mayBreak`, then broken by the API as before (same blocks, order, drops and cost); a refused log ends the felling (`KamiGear.md` §4) | `awakenedAxeTreeModeAsksTheProtectionAboutEveryLog` (a refusing backend: nothing felled; one refused log: the felling stops there; the installed backend: the whole trunk) |

Both new GameTests fail with the code they replace (mutation runs of that day: the old felling loop fails the tree check at its first
step; a second, differently seeded pass of the clusters, as the old decoration would have been beside the feature, doubles the ore,
94,400 blocks, and fails the ore check, which the older `bedrockDimensionIsSolidBedrockWithOreVeins` does not notice). A second pass with
the game's own seed changes nothing (the same cells), so it is not a defect and no check looks for it.

## Verified (2026-11-01, after all fixes, one run of each)

| Check | Result |
|---|---|
| JUnit (`tinkererTest`) | 423 tests, 0 failures |
| Fabric GameTests (`runTinkererGameTest`) | All 354 required tests passed (312 before) |
| NeoForge GameTests (`:neoforge:runGameTest`) | All 362 required tests passed (314 before) |
| Client smoke, Fabric / NeoForge | 728 / 731 PASS, 0 FAIL, 1 NOT_RUN |
| Client smoke, Fabric with EMI + Jade / with JEI | 737 / 732 PASS, 0 FAIL, 1 NOT_RUN |
| `main` after merging `kami-followups` and `multiplayer`, with the Kami-off load conditions (one run of each) | JUnit 424 tests, 0 failures; Fabric: All 356 required tests passed; NeoForge: All 364 required tests passed; client smoke Fabric 726 / NeoForge 730 PASS, 0 FAIL, 1 NOT_RUN (the fewer PASS are fire blocks' `up=true` states, which vary from run to run, `CLIENT_VERIFICATION.md`); multiplayer 67 PASS, 0 FAIL on each loader, and the Kami-off server logs no data-load error |
| Follow-up (worktree of `kami-followups`, `check runTinkererGameTest :neoforge:runGameTest`, one run) | JUnit 424 tests, 0 failures; Fabric: All 356 required tests passed; NeoForge: All 364 required tests passed. The client smoke runs were not repeated |

## Not verified

- The new tests were not run against the code before the fixes; that each would have caught its bug is reasoned, not seen.
- Third-party pipes, a GPU. A real protection mod (claims), spawn protection on a real dedicated server, a real client receiving the
  config payload and multiplayer with real clients were not verified by this audit; they were afterwards, by the multiplayer check
  (`MULTIPLAYER_VERIFICATION.md`: first part 67 PASS, 0 FAIL on Fabric and on NeoForge; its second part added the Remote Placer, the
  Dislocator and the Tablet under spawn protection, every packet of the add-on, and Open Parties and Claims on both loaders, tree mode among
  its scenes, and found that a place question on Fabric did not reach that mod; fixed). Still not seen: other claims mods, latency and packet
  loss (loopback only).
- The follow-up: the ore
  feature in a world with mods that add features to every biome (the test runs had none: the biome's steps held only the clusters) and in
  a world made before the change (reasoned, not tried: its generated chunks keep their ores, chunks generated afterwards get the
  feature's positions); the Bedrock dimension in a client.

## Modernization (packages C and D, 2026-11-01, branch `modernize-c`)

1.7.10 technique replaced by the 1.21.1 one, gameplay kept (the read-only audit of that day, its package C: machines, enchantments, core
constants; package D: the config handshake). Details in `deviations/Machines.md` section 13 and `deviations/Enchantments.md` section 14.

| Finding | Was | Now | Test |
|---|---|---|---|
| Thaumcraft content by 1.7.10 field + metadata (machines and enchantments) | `ItemApi.getItem("itemResource", 2)`, `BlocksTC.state("blockAiry", 1)` | native ids: `ItemsTC.stack("thaumium_ingot")`, `BlocksTC.get("nitor")`, … (the Obsidian Totem stays `BlocksTC.state("blockCosmeticSolid", 0)`: `cosmetic_solid_tough[variant=0]` has no name in the API, `api-requests/Enchantments.md` section 7) | the recipe parity GameTests; the Enchanter's pillar GameTests |
| The Corporeal Attractor's wand sound | a new, unregistered `SoundEvent` of `thaumcraft:key` per click | `ThaumcraftSoundIds.event(KEY)`, the registered event | `aWandOpensTheCorporealAttractorWithThaumcraftsRegisteredKeySound` (reads the sound packet) |
| The Funnel's jar identity | registry id strings `thaumcraft:jar` / `jar_void` | `stack.is(BlocksTC.jar().asItem())` / `voidJar()` | `theFunnelKnowsTheTwoJarsOfTheApiAndNoOtherHolderOfEssentia` |
| The Enchanter's packet | a bare `ResourceLocation` | `ResourceKey<Enchantment>` (`ResourceKey.streamCodec`; the same bytes) | `onTheWireTheEnchantmentIsItsIdAsBefore` |
| Shears in the Enchanter's digger rule | `instanceof ShearsItem` only | also `#c:tools/shear` (both loaders fill it) | `anItemOfTheConventionShearsTagIsShearsForTheDiggingEnchantments` |
| The infused soul aspect's glint | `isFoil` override | the item's default `enchantment_glint_override` | `theInfusedSoulAspectsGlintByTheirItemsDefaultComponent` |
| The attractor rings' clock | `System.currentTimeMillis() / 500F` (float: the rings jumped every 131 s instead of bobbing, in 1.7.10 too) | game time + partial tick in double | visual only, client smoke |
| Unused constants | `LibResources` (lang prefix, empty texture, NEI arrow, model paths, armour layers, three enchantment icons), `LibBlockNames.*_SLAB_FULL`, `LibItemNames.KAMI_RESOURCE`, `SWORD` | deleted; `tt_oracle/provenance.json` regenerated (the oracle's tables unchanged) | compile, the parity tests |
| Duplicated enchantment keys | `LibEnchantIDs.idAscentBoost` … and copies in `ModEnchantments` | one set in `ModEnchantments` | `theFourteenKeysAreThoseOfTheRegistryPathsOfTheEnchantments` |
| `KamiResearchItem.Blacklist` | a public mutable list filled in a static block | `private static final List.of("MINILITH")` | the Kami parity check |
