# Tooltips (Waila to Jade): deviations from Thaumic Tinkerer 1.7.10

The 1.7.10 mod showed extra lines in Waila for five blocks: `common/compat/TTinkererProvider.java` (Animation Tablet,
Transvector Interface, Thaumic Restorer, Celestial Gateway) and `common/compat/MagnetProvider.java` (Kinetic and Corporeal
Attractor). The port shows the same lines in **Jade 15.10.6** (Fabric, Minecraft 1.21.1), an optional dependency: the add-on
runs without it, and nothing outside `compat/` refers to it. Specification: the 1.7.10 sources (commit in `NOTICE.md`).

Reading key: **forced** means 1.21.1 or Jade cannot do it the old way; **fix** means the original was wrong; **choice** means a
free decision of the port.

## 1. What the original showed

Body lines only (head, tail and the stack were left alone), with these keys of the converted language files (`ttwaila.*`):

| Block | Lines |
|---|---|
| Animation Tablet (`BlockAnimationTablet`) | `ttwaila.currentTool` with the display name of the item in slot 0, or `ttwaila.nothing`; with an item also `ttwaila.leftClick` / `ttwaila.rightClick` (field `leftClick`) and `ttwaila.redstone` / `ttwaila.autonomous` (field `redstone`) |
| Transvector Interface (`BlockTransvectorInterface`) | `ttwaila.connected` with the localized name of the block of `getTile()`, or `ttwaila.nothing`; with a tile also the literal `x: %d y: %d z: %d` of that tile |
| Thaumic Restorer (`BlockRepairer`) | with an item in slot 0: `ttwaila.repairing` (damage > 0) or `ttwaila.finishedRepairing` (damage 0), with the item's display name; nothing when empty |
| Celestial Gateway (`BlockWarpGate`) | `ttwaila.allowIncoming` when `locked` is **true**, `ttwaila.disallowIncoming` when it is false (inverted, section 4.1) |
| Attractors (`BlockMagnet`, both) | `ttwaila.pullingMode` / `ttwaila.pushingMode` (metadata bit 0); the Corporeal Attractor (`TileMobMagnet`) also: with an empty slot `ttwaila.pullingAdult` / `pullingChild` / `pushingAdult` / `pushingChild`; with something in the slot the `...Type` key of the same four with `translateToLocal("entity." + pattern + ".name")` |

`getNBTData` returned the tag unchanged: the 1.7.10 lines were built on the client from the client's copy of the tile entity.

## 2. How the port is built

- `compat/src/main/java/thaumic/tinkerer/compat/tooltip/` (no Jade import): `BlockTooltip<T>` (one per block class: a name,
  the block class, the block entity class; `write` puts what the lines need into a `CompoundTag` on the server, `lines` builds
  the `Component` lines from that tag), the five tooltips `AnimationTabletTooltip`, `TransvectorInterfaceTooltip`,
  `RepairerTooltip`, `WarpGateTooltip`, `MagnetTooltip` (each also has a plain `linesFor(...)` from values), `TooltipData`
  (a name as a text component in the tag, `ComponentSerialization.CODEC`), `TooltipKeys` (the keys and how many `%s` each
  takes), `TinkererTooltips.ALL`. The block entities are read through their own accessors (`getItem(0)`, `leftClick`,
  `redstone`, `getTile()`, `locked`, `isPulling()`, `adult`, `ItemSoulMould.getPatternName`).
- `compat/src/main/java/thaumic/tinkerer/compat/jade/`: `TinkererJadePlugin` (`IWailaPlugin`, the `jade` entrypoint of
  `fabric.mod.json`, also `@WailaPlugin` for a NeoForge build) and `TooltipProvider` (one object per tooltip that is both the
  `IServerDataProvider<BlockAccessor>` and the `IBlockComponentProvider`, the way Jade's own providers are written; it only
  hands the block entity to `BlockTooltip.appendServerData` and adds `BlockTooltip.linesFromServerData` to the tooltip).
- Jade sends one tag per looked-at block for all providers; each tooltip writes its own tag under its uid
  (`"thaumictinkerer:warp_gate": {...}`) and reads only that entry. No entry (a server without the add-on's plugin): no line.

| Uid (also the on/off option) | Lines registered for | Server data registered for | Option label key |
|---|---|---|---|
| `thaumictinkerer:animation_tablet` | `BlockAnimationTablet` | `TileAnimationTablet` | `config.jade.plugin_thaumictinkerer.animation_tablet` |
| `thaumictinkerer:interface` | `BlockTransvectorInterface` | `TileTransvectorInterface` | `config.jade.plugin_thaumictinkerer.interface` |
| `thaumictinkerer:repairer` | `BlockRepairer` | `TileRepairer` | `config.jade.plugin_thaumictinkerer.repairer` |
| `thaumictinkerer:warp_gate` | `BlockWarpGate` | `TileWarpGate` | `config.jade.plugin_thaumictinkerer.warp_gate` |
| `thaumictinkerer:magnet` | `BlockMagnet` (both attractors) | `TileMagnet` (and so `TileMobMagnet`) | `config.jade.plugin_thaumictinkerer.magnet` |

The option labels are new keys in `lang-extra/tooltips.<lang>.json` (en_us, ru_ru, and de_de, fr_fr, ko_kr, zh_cn made of
the block names those languages already have in the converted files).

## 3. The data each tooltip sends

Tag of each tooltip (inside Jade's server data, under its uid). The sizes are the whole server data encoded with
`ByteBufCodecs.COMPOUND_TAG` (the codec of Jade's `ReceiveDataPacket`), as logged by the GameTests of section 5 (`[tooltips]`
lines of the run log):

| Tooltip | Keys | Examples (bytes) |
|---|---|---|
| Animation Tablet | `tool` (text: the item's hover name, absent when empty), `leftClick`, `redstone` | empty 63; diamond pickaxe `{translate:"item.minecraft.diamond_pickaxe"}` 115; renamed sword `"Tablet Test Blade"` 89 |
| Transvector Interface | `block` (registry id of the block of `getTile()`, absent when it answers nothing), `x`, `y`, `z` | unbound 31; chest 80; furnace 82 |
| Thaumic Restorer | `item` (text, absent when empty), `damaged` (`getDamageValue() > 0`) | empty 30; iron sword 88; renamed axe 67 |
| Celestial Gateway | `locked` | 41 |
| Attractors | `pulling`; Corporeal only: `mob` (true), `adult`, `pattern` (entity type id of the item in the slot, `""` for an unset mould, absent when the slot is empty) | kinetic 39; corporeal without mould 55; unset mould 67; zombie mould 83 |

The name of an item is the stack's hover name (a custom name wins, as 1.7.10 `getDisplayName` did), sent as a text component and
shown as it is. The interface's block and the mould's entity type are sent as ids and named on the client (`Block.getName()`,
`EntityType.getDescription()`); an id the client does not know is shown as the id.

## 4. Deviations

1. **The gateway line tells the truth (fix, the lead's decision).** 1.7.10 showed "Allows incoming teleports" for a locked gate
   and "Doesn't allow incoming teleports" for an open one, but `locked` makes the gate refuse every teleport to it
   (`TileWarpGate.teleportPlayer`). The port shows `ttwaila.disallowIncoming` when `locked` is true and `ttwaila.allowIncoming`
   when it is false. The GameTest checks the line against the gate's real answer (a mock player is teleported to the open gate
   and refused by the locked one).
2. **An unset soul mould reads "No Pattern" (fix).** 1.7.10 translated `entity..name` for a mould without a pattern, which is not
   a key, so Waila showed the raw `entity..name`. The port passes the mould's own `ttmisc.soulMould.nonAssigned` ("No Pattern",
   the text the mould's item tooltip shows) as the type. Such a mould makes the attractor move nothing, in both versions.
3. **Entity names come from the 1.21.1 entity type (forced).** The pattern is a registry id (`minecraft:zombie`, see
   `Machines.md` section 2), so the name is `entity.minecraft.zombie` instead of `entity.Zombie.name`; the text is the game's.
4. **The data comes from the server (forced, choice of design).** 1.7.10 Waila read the client's tile entity; Jade's way is a
   server data provider, and the add-on's client copies do not all carry what the lines need. Consequence: on a server without
   Jade (Jade installed on the client only) the five tooltips show no extra line. No client-side fallback was written.
5. **The coordinates line** is the same literal text, formatted with `Locale.ROOT` (1.7.10 used the default locale, which can
   change the digits).
6. **One on/off option for both attractors (forced).** Both are one block class (`BlockMagnet`, as the one 1.7.10 block with its
   metadata bit), and Jade registers lines by block class; the option is labelled with both names.
7. **Russian texts (choice).** The converted 1.7.10 Russian file has commands and past tenses where the English describes a
   state ("Разрешить телепортацию" = "allow teleporting" for "Allows incoming teleports"; "Затянуто взрослых" = "adults were
   pulled"; "Режим: Тянуть"). `lang-extra/tooltips.ru_ru.json` overrides 14 `ttwaila.*` keys: `allowIncoming`
   ("Принимает входящие телепортации"), `disallowIncoming` ("Не принимает входящие телепортации"), `finishedRepairing`,
   `redstone`, `pullingMode`, `pushingMode` and the eight adult/child keys (now "Притягивает взрослых: %s",
   "Отталкивает детёнышей" and so on). The other languages keep the converted texts.
8. Unchanged on purpose: the Transvector Dislocator has no tooltip (1.7.10 registered the interface only); the interface shows
   "Nothing" when `getTile()` answers nothing, also for a binding to a chunk that is not loaded (as 1.7.10); on the server that
   `getTile()` call also ends a binding whose target is gone or too far, exactly as every other access of the interface does.

## 5. What was verified

- **JUnit** (`common/src/test/java/thaumic/tinkerer/tooltips/`): `TooltipLinesTest` (18 tests: every branch of the five
  tooltips, keys and arguments; reading the tags; the shared server data; the uids, classes and option keys; the Dislocator has
  no tooltip) and `TooltipLanguageTest` (5 tests: every key in en_us and ru_ru with as many `%s` as the lines pass, the same
  count in the other four languages where they have the key, an option label in all six languages, and the lines rendered
  through `Language` in English, e.g. "Doesn't allow incoming teleports" for a locked gate and "Pulling: Adult Zombie", and in
  Russian, e.g. "Не принимает входящие телепортации"). `tinkererTest`: **387 tests, 0 failures, 0 errors** (from
  `build/test-results/tinkererTest/*.xml`; 364 before this work).
- **GameTests** (`TooltipsRuntimeChecks`, wrappers in `TooltipsGameTests`): the blocks are placed and put in every state the
  original distinguished (tablet empty, with a pickaxe in the four mode combinations, with a renamed sword, emptied; interface
  unbound, bound to a chest and to a furnace with their absolute coordinates, target broken (the binding ends), target 6 blocks
  away (refused), unbound by hand; restorer empty, sword with damage 100 and 1, mended, renamed axe, emptied; gateway open,
  locked, open again, with the real teleport; kinetic attractor pulling and pushing; corporeal attractor in the four
  polarity/age states without a mould, with an unset mould, with a zombie mould in the four states, and after taking the mould
  out). The server data is written, sent through `ByteBufCodecs.COMPOUND_TAG` and compared with the lines expected (key and
  arguments). One check asserts that every block has exactly one tooltip by block and block entity class, the uids, that a
  tooltip refuses another block's entity and keeps the other entries of the shared tag, and `ThaumicTinkerer.failures()` is
  empty. `runTinkererGameTest -PtinkererGameTestFilter='tooltipsgametests.*'`: "All 7 required tests passed".
- **Jade on a server**: the same filtered run with `-PwithJade` loaded Jade 15.10.6 on the GameTest server, which logged
  "Loaded plugin from thaumic.tinkerer.compat.jade.TinkererJadePlugin in 7.905 ms" (no error; Jade checks the registered classes
  and duplicate uids while loading), and "All 7 required tests passed". That run exercises only `register` (the common half).
- **The whole GameTest run** (without Jade, no filter): "All 306 required tests passed" (306 test cases, 0 failures in
  `build/tinkerer-gametest-results.xml`; 299 before this work, plus the 7 above).

## 6. What was not verified

- **Run in a client with Jade (2026-10-31, Fabric dev client under Xvfb, `tools/client-smoke.sh -- -PwithEmi -PwithJade`,
  `docs/CLIENT_VERIFICATION.md`, phase `integrations`).** For each of the six blocks the harness put the block into a known state on
  the integrated server, pointed the crosshair at it with the HUD on, and read the text of the tooltip Jade collected (Jade's own
  callback, not the add-on's code); the expected lines were translated by the harness from the keys, in English. All six passed,
  e.g. "Dynamism Tablet|Current Tool: Diamond Pickaxe|Right Click|Redstone Activated|1 Diamond Pickaxe", "Transvector
  Interface|Connected to: Chest|x: -7 y: -60 z: -14", "Celestial Gateway|Doesn't allow incoming teleports" (locked), "Corporeal
  Attractor|Mode: Pushing|Pushing: Children"; six pictures (`jade-*.png`). The first run missed both attractors: their outline is a
  plate two pixels high (as in 1.7.10) under the tall model, and the harness aimed at the middle of the block; it now aims at the
  middle of the block's shape. Still not seen: the option labels in Jade's plugin settings and the add-on's section title there,
  the Russian lines in Jade, a GPU.
- Not run: a dedicated server with a separate client; Jade on the client only (expected: no extra line, section 4.4); several
  players.
- Not run: NeoForge (there is no NeoForge build; `@WailaPlugin` is untested).
- The Russian, German, French, Korean and Chinese texts were not seen in a game; the Russian lines were rendered only by the JUnit
  test above.
