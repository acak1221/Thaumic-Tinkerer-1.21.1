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

## Verified (2026-11-01, after all fixes, one run of each)

| Check | Result |
|---|---|
| JUnit (`tinkererTest`) | 423 tests, 0 failures |
| Fabric GameTests (`runTinkererGameTest`) | All 354 required tests passed (312 before) |
| NeoForge GameTests (`:neoforge:runGameTest`) | All 362 required tests passed (314 before) |
| Client smoke, Fabric / NeoForge | 728 / 731 PASS, 0 FAIL, 1 NOT_RUN |
| Client smoke, Fabric with EMI + Jade / with JEI | 737 / 732 PASS, 0 FAIL, 1 NOT_RUN |

## Not verified

- The new tests were not run against the code before the fixes; that each would have caught its bug is reasoned, not seen.
- A real protection mod (claims), third-party pipes, a GPU. Spawn protection on a real dedicated server, a real client receiving the
  config payload and multiplayer with real clients were not verified when this audit was made; they are now, by the multiplayer check
  (`MULTIPLAYER_VERIFICATION.md`, next section). Still not seen with real clients: the Remote Placer, the Transvector Dislocator and the
  Animation Tablet's fake player under spawn protection (they ask the same `BlockProtection.gameAllows`, whose spawn-protection answer
  the check confirms for the tools that use it), and any latency or packet loss (loopback only).

## Verified later: the multiplayer check

MULTIPLAYER_ROWS
- Not fixed on purpose: the Bedrock dimension's ore generation stays a hand-written decoration (documented, low value); tree mode of the
  Ichorium Axe asks the game's rules but no protection mod (documented in `KamiGear.md` §4).
