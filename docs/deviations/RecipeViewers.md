# RecipeViewers: the NEI pages of Thaumic Tinkerer 1.7.10 in EMI and JEI

Group RecipeViewers: the two recipe-viewer pages that the 1.7.10 add-on had in NEI, now as optional EMI (1.1.22) and JEI
(19.43.0.398) plugins of the Fabric build. Specification: the 1.7.10 sources in GTNewHorizons/ThaumicTinkerer (commit in
`NOTICE.md`): `client/nei/NEIConfig`, `NEINecromancyHandler`, `NEIInfusedSeedRecipeHandler` and the NEI part of
`common/core/proxy/TTCommonProxy` (`NEIEventHandler`). The plugins are new code; nothing of the NEI handlers could be
ported as code (NEI and Aspect Recipe Index do not exist for 1.21.1).

Reading key: **forced** means 1.21.1 or the viewer API cannot do it the old way; **fix** means the original showed
something wrong; **choice** means a free decision of the port that the lead may overrule.

## 1. What 1.7.10 had

- **Registration.** `NEIConfig.loadConfig` returned at once unless Aspect Recipe Index (ARI) was loaded, and only after
  that check registered both handlers; `TTCommonProxy` registered the handler infos and catalysts only with NEI and ARI.
  So both pages existed only with ARI installed.
- **Necromancy** (`NEINecromancyHandler`, a subclass of ARI's `InfusionRecipeHandler`). Title `tile.spawner.name`
  ("Tablet of Necromancy"). One page per `EnumMobAspect` row (30): the three **basic** soul aspects of the row
  (`ItemMobAspect.getStackFromAspect`) as the surrounding items of an infusion page (ARI's runic matrix drawn behind
  them), the Mob Display of the mob as the result; no essentia, no instability, a "see all recipes" label. Recipes of a
  Mob Display show its mob; uses of a soul aspect show every mob whose row contains its aspect. Catalysts: the Blood
  Sword (Cursed Spirit's Blade) and the tablet; handler height 88, display stack the tablet.
- **Infused seed drops** (`NEIInfusedSeedRecipeHandler`). Title `tt.nei.category.infusedseeds` ("Infused Seed Drops").
  One page per aspect with loot in `AspectCropLootManager.getAllLoot()`: the seed of the aspect
  (`ItemInfusedSeeds.getStackFromAspect`) at the top, a down arrow, the drops nine to a row; the tooltip of a drop is
  `GRAY "Drop Chance" ": " YELLOW <chance>%`, chance `((float) weight / totalWeight) * 100f` formatted with
  `DecimalFormat("#.##")`. Recipes of any drop and uses of an infused seed of the same aspect find the page. No
  catalysts, no favourite and overlay buttons, display stack the seed of aer.
- **Item panel.** `ItemMobDisplay.getSubItems` returned one Mob Display per row and the item had the add-on's creative tab,
  so NEI's item panel (which collects `getSubItems` per creative tab of an item) listed the 30 Mob Displays.

## 2. What EMI and JEI show now

Both plugins are built from the same plain views in `compat/src/main/java/thaumic/tinkerer/compat/viewer`
(`TinkererRecipeViews.necromancy()` / `seedDrops()`, records `NecromancyView`, `SeedDropView`), which know neither EMI nor
JEI; the layout numbers are shared (`ViewerLayout`). EMI: `compat/.../emi/TinkererEmiPlugin` (`emi` entrypoint,
`@EmiEntrypoint`). JEI: `compat/.../jei/TinkererJeiPlugin` (`jei_mod_plugin` entrypoint, `@JeiPlugin`, uid
`thaumictinkerer:jei`).

### 2.1 `thaumictinkerer:necromancy` ("Tablet of Necromancy")

- One page per row of `EnumMobAspect` (30), in the table's order, id `thaumictinkerer:/necromancy/<namespace>/<path>` of
  the summoned entity type (e.g. `/necromancy/minecraft/zombie`, `/necromancy/thaumcraft/brainy_zombie`).
- Three soul aspect slots on a ring around the tablet (first straight above, then 120 degrees clockwise, as Thaumcraft's
  infusion pages place components), the tablet in the middle (EMI: a catalyst slot; JEI: a `CATALYST` slot), an arrow,
  the Mob Display of the mob in the large output slot. Each soul aspect slot cycles through the basic, condensed and
  infused soul aspect of its aspect (section 3.2).
- Under the ring the line "On 3 Arcane Pedestals"; its tooltip holds the rules of the port's tablet (section 3.3).
- Workstations / catalysts: the Cursed Spirit's Blade and the Tablet of Necromancy (the 1.7.10 order).
- Lookups: recipes of a Mob Display show its mob only; uses of any of the 51 soul aspects show the mobs that need its
  aspect; the workstations open the whole category.

### 2.2 `thaumictinkerer:infused_seed_drops` ("Infused Seed Drops")

- One page per aspect whose list in `AspectCropLootManager.getAllLoot()` is not empty, in the order of `Aspect.aspects`,
  id `thaumictinkerer:/infused_seed_drops/<aspect tag>`.
- The seed of the aspect in the top left corner, the line "A ripe crop yields one of:" next to it (tooltip: the harvest
  rules, section 3.5), the drops below, nine to a row, each with its count and the tooltip "Drop Chance: 12.5%" (grey,
  the number yellow, as 1.7.10). Every entry of the list is one slot, in the list's order; an item listed twice is shown
  twice, as 1.7.10 did.
- Chance = `weight * 100 / totalWeight` in double arithmetic, shown with `#.##`, half-even rounding (`DropChances`).
- No workstation (section 3.6).
- Lookups: recipes of any drop show the pages that list it; uses of Infused Seeds show the page of the seed's aspect,
  whatever tendencies the seed carries.

### 2.3 Stacks, comparisons, item lists

- Mob Displays (one item, the mob in the component `thaumictinkerer:mob_display_mob`) and Infused Seeds (one item, the aspect in the component
  `thaumictinkerer:infused_aspect`) are told apart: EMI `setDefaultComparison` on the type / the aspect, JEI subtype
  interpreters on the same data. Without that, EMI and JEI take all Mob Displays (all seeds) for one stack, and the
  recipes of a zombie would list every mob.
- The 30 Mob Displays are added to the item lists (EMI: right after the tablet; JEI: `addExtraItemStacks`, at the end),
  as NEI listed them.
- When EMI and JEI are both installed, EMI does not run the JEI plugins of mods that have an EMI plugin (EMI's
  `PluginCallerMixin`, by the namespace of the plugin uid; read in EMI's sources, not run), so the pages are not doubled.

### 2.4 Texts

`lang-extra/recipe_viewers.<lang>.json`, merged by `tools/porting.sh tt_assets.py`. EMI reads the category names from
`emi.category.thaumictinkerer.necromancy` / `.infused_seed_drops`; the JEI categories return the same keys. The other
keys are `thaumictinkerer.recipe_viewers.*` (the pedestal line, four tablet rules, the seed header, three harvest rules,
`chance` = "Drop Chance: %s", `page` = "Page %s of %s").

- English texts of the titles and the chance line are the 1.7.10 ones: `tile.spawner.name`,
  `tt.nei.category.infusedseeds`, `tt.nei.chance` + ": " (a JUnit test compares them with the converted base).
- Russian: every key. The necromancy title is the 1.7.10 Russian `tile.spawner.name` ("Пьедестал некроманта"); the
  other Russian texts are new (1.7.10 had `tt.nei.*` only in en_US).
- German, French, Korean, Chinese: only the necromancy title, the 1.7.10 translations of `tile.spawner.name`
  ("Nekromantentafel", "Tablette de Nécromancie", "강령술 석판", "死灵祭坛"). Every other text falls back to English: the
  original had no translation of the NEI keys, and no new text was invented for those languages.

### 2.5 What is not here

The add-on's arcane, crucible, infusion and enchantment-infusion recipes are registered through `ThaumcraftApi` and are
listed by Thaumcraft's own EMI and JEI plugins; these plugins do not repeat them.

## 3. Deviations

### 3.1 The pages are always there (choice)

1.7.10 showed both pages only with Aspect Recipe Index (section 1). ARI has no 1.21.1 version, and the pages are the
add-on's own data, so both plugins register them whenever EMI or JEI is installed.

### 3.2 Every form of soul aspect in the necromancy slots (fix)

NEI showed only the basic soul aspect. The tablet (`TileSummon.summon`, 1.7.10 and port alike) takes any `ItemMobAspect`
of the aspect: basic, condensed or infused. The slots cycle through all three (basic first), and the uses of condensed
and infused soul aspects find the pages too. The GameTest makes every page summon its mob from each of the three forms.

### 3.3 The tablet's rules as text (choice)

New: the line "On 3 Arcane Pedestals" and a tooltip with what the port's tablet does (`TileSummon`): the three soul
aspects in any order on arcane pedestals at most 5 blocks from the tablet at its height; every 300 ticks a summon on top
of the tablet, the soul aspects used up; three infused ones are kept but work only every 1200 ticks, and a tablet that
did not use them last first only takes them over; a redstone signal stops the tablet. NEI had no text. The order of the
three slots is the order of the row; the tablet compares sets, so for a row with a repeated aspect (cow: bestia, terra,
bestia) other multisets of the same set summon the mob as well (bestia, terra, terra). The page shows the row as NEI
did; the GameTest checks that for every page the first row the tablet would pick for its three aspects is the page's mob.

### 3.4 No infusion frame (forced)

The 1.7.10 page was an ARI infusion page with the runic matrix drawn behind the ring. EMI and JEI have no such frame;
the ring is drawn with plain slots around the tablet, with the viewers' own arrow and output slot.

### 3.5 The seed page's header and rules (choice)

New: the line "A ripe crop yields one of:" and its tooltip, after `BlockInfusedGrain.harvest`: each harvest of a ripe
crop draws one entry with the shown chance; an Ordo tendency of n draws once more with a chance of n in 75, again and
again; the crop also drops its seeds, an unripe crop only the seeds. The down arrow of the NEI page is left out (the
header stands next to the seed instead).

### 3.6 No workstation for the seed page (forced)

NEI had no catalyst for it. The infused farmland, the only fitting block, has no item (`blockWithoutItem`), so there is
nothing to show.

### 3.7 Each drop's own chance in its tooltip (fix)

NEI's tooltip searched the page for the first drop of the same item and damage (`areStacksSameTypeCrafting`, which
ignores the count; NEI's source is not in this checkout, this is its known behaviour) and showed that drop's chance. The
pannus (cloth) list has white wool twice (16 of weight 30, 4 of weight 1 from the dye loop), so both slots said 53.57%.
Each slot now shows its own chance (53.57% and 1.79%).

### 3.8 Decimal point (fix)

`DecimalFormat("#.##")` of 1.7.10 used the Java runtime's locale, not the game's language, so the separator was a comma
on a German or Russian system. The port always writes a point; the words around it follow the game's language. The
rounding (half-even, at most two decimals) and the numbers are those of 1.7.10: the GameTest formats the 1.7.10 float
arithmetic and the port's double arithmetic for all 280 drops and finds 0 differences.

### 3.9 Long lists: pages in EMI, scrolling in JEI (forced)

NEI made one page as tall as the list. JEI gives every recipe of a category the same height, so the drops are in JEI's
scroll grid (nine columns, four visible rows). EMI has no scroll widget in its API, so a list longer than 54 drops (six
rows) goes on to further pages with "Page n of m" and the ids `/infused_seed_drops/<tag>/2`, `/3`, .... With the
vanilla and Thaumcraft data no list is longer than 48 (auram and sensus), so EMI never splits (GameTest: 0 views split).

### 3.10 Category ids and names (forced)

NEI's overlay ids `thaumictinkerer.necromancy` / `thaumictinkerer.infusedseeds` are the categories
`thaumictinkerer:necromancy` / `thaumictinkerer:infused_seed_drops`. The page ids follow EMI's convention for recipes
outside the recipe manager (path starting with `/`) and are built from the entity type id and the aspect tag only.

### 3.11 Not part of the recipe tree (choice)

Both EMI recipes return `supportsRecipeTree() == false`: a summon and a weighted harvest are no crafts.

## 4. Verified

Commands (Gradle only through `/root/bin/gradle-mirror-loop-in.sh`, logs in `/tmp/tt-split/`):

- `tools/compile.sh -g`: the whole add-on with the GameTest sources compiles. The compat packages also compile with
  `-Xlint:deprecation,removal` without a warning (the deprecated JEI scroll-grid factory, `getBackground`,
  `getTooltipStrings` and the string subtype interpreter are not used).
- `runTinkererGameTest -PtinkererGameTestFilter='recipeviewersgametests.*'` (`viewers-gt2.log`, `exit 0`): "All 6 required
  tests passed".
- Last run, after every change of this group: `tinkererTest runTinkererGameTest` (`viewers-final.log`, `exit 0`).
  JUnit: 406 tests, 0 failures, 0 errors, 0 skipped (from `build/test-results/tinkererTest/*.xml`); new:
  `RecipeViewerLogicTest` 13, `RecipeViewerResourcesTest` 6. GameTests: "All 312 required tests passed"; the XML report
  (`build/tinkerer-gametest-results.xml`) lists 312 test cases, none failed, the 6 of this group among them (299 before
  this group and the Tooltips group, which added 7).

What the GameTests established, with the numbers from the logs:

- 30 necromancy pages for the 30 rows, 30 distinct entity types, unique ids; each slot holds basic, condensed and infused
  soul aspect of the row's aspect in the row's order; the Mob Display shows the row and has the mob's name; the first row
  the tablet picks for the three aspects is the page's mob.
- A tablet with three arcane pedestals summoned the page's mob, exactly one of that type, 90 times (30 pages x basic,
  condensed, infused): basic and condensed used up, infused kept (at game time 1200).
- 45 seed pages (every aspect with loot; bestia, volatus and tempestas have none, as in 1.7.10 without the "cloud" of
  other mods), 280 drops, unique ids; each drop is the table's entry with its weight; each chance equals
  weight * 100 / total (to 1e-9); every page's chances add up to 100 (largest deviation 9.9e-14); the shown, rounded
  chances add up to 100 within 0.16 (bound: 0.005 per drop); 0 differences from the 1.7.10 float numbers.
  Drops per page: aer 1, terra 1, ignis 1, aqua 1, ordo 1, perditio 1, vacuos 7, lux 3, motus 2, gelum 4, vitreus 3,
  victus 1, venenum 2, potentia 1, permutatio 2, metallum 1, mortuus 1, tenebrae 1, spiritus 1, sano 3, iter 1, alienis 3,
  praecantatio 7, auram 48, vitium 1, limus 1, herba 16, arbor 42, corpus 1, exanimis 2, cognitio 4, sensus 48,
  humanus 12, messis 6, perfodio 1, instrumentum 2, meto 1, telum 1, tutamen 4, fames 1, lucrum 2, fabrico 3, pannus 18,
  machina 4, vinculum 13. The tag entries are filled on the server (arbor lists the logs, herba the saplings, messis the
  wheat seeds of `#c:seeds`).
- The EMI page split keeps every drop in order with unique page ids (45 EMI seed pages, 0 split).
- 2308 harvests of ripe crops (no tendencies) of the 45 aspects: each gave the seeds and one stack, and every stack was
  one of its page's drops.

JUnit: the chance arithmetic and format (including half-even rounding and the point under a German and a Russian
default locale), the page split, the ids (category ids, page ids, path segments of odd aspect tags), the geometry (the
ring positions, nothing on the necromancy page overlaps or leaves the page), the fragments (same keys in English and
Russian, exactly the keys the code uses, the format arguments, the titles equal to the 1.7.10 texts in six languages,
the generated language files carry the fragments), the entrypoints of `fabric.mod.json`, and that `compat/viewer`
imports neither EMI, JEI, Jade nor client classes.

## 5. Not verified

- **Run in a client (2026-10-31, Fabric dev client under Xvfb; `docs/CLIENT_VERIFICATION.md`, phase `integrations`).** With EMI
  (`tools/client-smoke.sh -- -PwithEmi -PwithJade`): EMI's recipe manager held 30 recipes in `thaumictinkerer:necromancy` and 45 in
  `thaumictinkerer:infused_seed_drops`, the numbers the add-on's data gives (counted by the harness from `EnumMobAspect` and the crop
  table, not from these views); the necromancy workstations are the Cursed Spirit's Blade and the Tablet of Necromancy; and every one of
  the add-on's 82 arcane, crucible and infusion recipes with an item result is listed in Thaumcraft's own EMI category of its kind
  (28 arcane, 15 crucible, 39 infusion). With JEI (`tools/client-smoke.sh -- -PwithJei`, a separate run): the same 30 and 45, the same
  catalysts, and the output of each of the 82 recipes in Thaumcraft's JEI category of its kind. Pictures of the first page of each
  category in both viewers (`emi-*.png`, `jei-*.png`): the slots, the ring around the tablet, the arrow, the Mob Display drawn as its
  mob, the header and the drop grid are there. Still not checked: the tooltips on hover (no input device), the item-list insertion and
  the comparisons/subtypes in use, the Russian page texts in the viewers, EMI and JEI installed together.
- **When the pages are built on a client.** Read in the viewers' sources, not run: EMI reloads after the recipes and then
  waits for the tags (`EmiReloadManager`); JEI on Fabric starts after the recipe packet (`ClientPacketListenerRecipeUpdateMixin`),
  which in 1.21.1 comes after the configuration phase that sends the tags. The crop table itself is filled on the
  client by `onThaumcraftReady` (both sides), so on a dedicated server the tag entries are the server's tags and the rest
  is the client's own table (the same code).
- **EMI with JEI installed**: the skip of the JEI plugin was read in EMI's sources, not run.
- **NeoForge**: the NeoForge build (`:neoforge`, `neoforge/build.gradle`) compiles `compat/` against the NeoForge APIs of EMI and JEI
  (compile-only, as on Fabric), and the plugins are found there by `@EmiEntrypoint` and `@JeiPlugin`. The group's GameTests passed on a
  NeoForge GameTest server (2026-10-31, in the full run of 314); they check the shared views, not EMI or JEI. Neither viewer was run on
  NeoForge, and nothing of the group has been seen in a NeoForge client yet.
- **The Russian texts** were written for the port and not proofread by a native speaker.
- **The 1.7.10 NEI behaviour** of section 1 is read from the 1.7.10 sources; NEI and ARI themselves were not run, and
  NEI's own source (stack matching, item panel) is not in this checkout.
