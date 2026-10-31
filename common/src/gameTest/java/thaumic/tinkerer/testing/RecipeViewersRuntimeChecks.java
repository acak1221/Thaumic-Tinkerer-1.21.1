/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only
 * code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.testing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumic.tinkerer.common.ThaumicTinkerer;
import thaumic.tinkerer.common.block.BlockInfusedGrain;
import thaumic.tinkerer.common.block.tile.TileInfusedGrain;
import thaumic.tinkerer.common.block.tile.TileSummon;
import thaumic.tinkerer.common.content.Machines;
import thaumic.tinkerer.common.content.SimpleBlocks;
import thaumic.tinkerer.common.core.helper.AspectCropLootManager;
import thaumic.tinkerer.common.core.helper.EnumMobAspect;
import thaumic.tinkerer.common.item.ItemInfusedSeeds;
import thaumic.tinkerer.common.item.ItemMobAspect;
import thaumic.tinkerer.common.item.ItemMobDisplay;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.compat.viewer.DropChances;
import thaumic.tinkerer.compat.viewer.NecromancyView;
import thaumic.tinkerer.compat.viewer.SeedDropView;
import thaumic.tinkerer.compat.viewer.TinkererRecipeViews;
import thaumic.tinkerer.compat.viewer.ViewerIds;
import thaumic.tinkerer.compat.viewer.ViewerLayout;

/**
 * <p>Runtime checks of the recipe-viewer pages (EMI and JEI) in a running server, which has the registries and the item
 * tags. EMI and JEI are client mods and are not loaded here: the checks look at the pages both plugins are built from
 * ({@link TinkererRecipeViews}) and hold them against what the game really does: the tablet is made to summon from
 * every page, and ripe crops of every aspect are harvested against their page. Whether EMI and JEI draw the pages is
 * not checked here.</p>
 */
public final class RecipeViewersRuntimeChecks {
    private static final Logger LOG = LoggerFactory.getLogger("ThaumicTinkerer/RecipeViewersChecks");
    /** The tablet and its three pedestals, as in the Machines checks. */
    private static final BlockPos TABLET = new BlockPos(3, 1, 3);
    private static final BlockPos[] PEDESTALS = { new BlockPos(1, 1, 3), new BlockPos(5, 1, 3), new BlockPos(3, 1, 5) };
    /** Harvests of a ripe crop per drop of its page (at least {@link #MIN_HARVESTS}). */
    private static final int HARVESTS_PER_DROP = 4;
    private static final int MIN_HARVESTS = 40;

    private RecipeViewersRuntimeChecks() {}

    private static void floor(GameTestHelper test) {
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) test.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static boolean empty(ItemStack stack) {
        return stack == null || stack.isEmpty() || stack.is(Items.AIR);
    }

    // ---- shared ----------------------------------------------------------------------------------------------------

    public static void thePagesAreBuiltAfterRegistration(GameTestHelper test) {
        test.assertTrue(ThaumicTinkerer.failures().isEmpty(), "registration failures: " + ThaumicTinkerer.failures());
        test.assertTrue(ViewerIds.NECROMANCY.equals(LibMisc.id("necromancy")), "category " + ViewerIds.NECROMANCY);
        test.assertTrue(ViewerIds.INFUSED_SEED_DROPS.equals(LibMisc.id("infused_seed_drops")), "category " + ViewerIds.INFUSED_SEED_DROPS);
        List<ItemStack> catalysts = TinkererRecipeViews.necromancyCatalysts();
        test.assertTrue(catalysts.size() == 2 && catalysts.get(0).is(BuiltInRegistries.ITEM.get(LibMisc.id(LibItemNames.BLOOD_SWORD)))
                && catalysts.get(1).is(Machines.SUMMON.get().asItem()), "the blade and the tablet, in the NEI order: " + catalysts);
        test.assertTrue(TinkererRecipeViews.tablet().is(BuiltInRegistries.ITEM.get(LibMisc.id(LibBlockNames.SPAWNER))), "the tablet icon");
        ItemStack seedIcon = TinkererRecipeViews.seedIcon();
        test.assertTrue(seedIcon.is(ItemInfusedSeeds.item()) && ItemInfusedSeeds.getAspect(seedIcon) == Aspect.AIR,
                "the seed page's icon is the seed of aer (the NEI display stack)");
        List<ItemStack> displays = TinkererRecipeViews.mobDisplays();
        Set<String> keys = new HashSet<>();
        for (ItemStack display : displays) {
            test.assertTrue(display.is(TinkererRecipeViews.mobDisplayItem()), "a Mob Display: " + display);
            keys.add(TinkererRecipeViews.mobDisplayKey(display.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)));
        }
        test.assertTrue(displays.size() == EnumMobAspect.values().length && keys.size() == displays.size() && !keys.contains(""),
                "one Mob Display per mob, each told apart by its type: " + keys);
        test.succeed();
    }

    // ---- necromancy ------------------------------------------------------------------------------------------------

    /** The first row of the table whose aspect set the three aspects are: what {@code TileSummon} picks. */
    private static EnumMobAspect tabletPicks(List<Aspect> onPedestals) {
        for (EnumMobAspect row : EnumMobAspect.values()) if (row.matchesAspectSet(new ArrayList<>(onPedestals))) return row;
        return null;
    }

    public static void theNecromancyPagesCoverEveryMobOnceWithItsSoulAspectsAndItsMobDisplay(GameTestHelper test) {
        List<NecromancyView> views = TinkererRecipeViews.necromancy();
        test.assertTrue(views.size() == EnumMobAspect.values().length,
                "one page per row of the table: " + views.size() + " of " + EnumMobAspect.values().length);
        Set<EnumMobAspect> mobs = new HashSet<>();
        Set<ResourceLocation> ids = new HashSet<>();
        Set<ResourceLocation> types = new HashSet<>();
        for (NecromancyView view : views) {
            EnumMobAspect mob = view.mob();
            test.assertTrue(mobs.add(mob), "a second page of " + mob);
            test.assertTrue(ids.add(view.id()), "a second page with the id " + view.id());
            EntityType<?> type = mob.getEntityType();
            test.assertTrue(type != null, mob + ": the entity type exists");
            test.assertTrue(BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(view.entityTypeId()), mob + ": entity type " + view.entityTypeId());
            test.assertTrue(types.add(view.entityTypeId()), "two rows summon " + view.entityTypeId());
            test.assertTrue(view.id().equals(ViewerIds.necromancy(view.entityTypeId())) && view.id().getPath().startsWith("/necromancy/"),
                    mob + ": a synthetic id " + view.id());
            test.assertTrue(view.aspects().equals(List.of(mob.aspects)), mob + ": the aspects of the row in its order");
            for (int i = 0; i < 3; i++) {
                Aspect aspect = mob.aspects[i];
                List<ItemStack> forms = view.essences().get(i);
                test.assertTrue(forms.size() == 3, mob + " slot " + i + ": basic, condensed, infused");
                ItemMobAspect.Form[] order = { ItemMobAspect.Form.BASIC, ItemMobAspect.Form.CONDENSED, ItemMobAspect.Form.INFUSED };
                for (int f = 0; f < 3; f++) {
                    ItemStack stack = forms.get(f);
                    test.assertTrue(!empty(stack) && stack.getItem() instanceof ItemMobAspect soul && soul.aspect == aspect
                            && soul.form == order[f] && stack.getCount() == 1, mob + " slot " + i + " form " + order[f] + ": " + stack);
                }
                test.assertTrue(ItemStack.matches(view.basicEssences().get(i), ItemMobAspect.getStackFromAspect(aspect)),
                        mob + " slot " + i + ": the basic soul aspect first");
            }
            test.assertTrue(tabletPicks(view.aspects()) == mob, mob + ": the tablet picks " + tabletPicks(view.aspects()) + " for these aspects");
            ItemStack display = view.mobDisplay();
            test.assertTrue(!empty(display) && display.getItem() instanceof ItemMobDisplay, mob + ": a Mob Display " + display);
            test.assertTrue(((ItemMobDisplay) display.getItem()).getEntityType(display) == mob, mob + ": the display shows it");
            test.assertTrue(display.getHoverName().getString().equals(mob.getDisplayName().getString()),
                    mob + ": named " + display.getHoverName().getString());
            test.assertTrue(view.catalysts().size() == 2 && view.catalysts().stream().noneMatch(RecipeViewersRuntimeChecks::empty), mob + ": catalysts");
        }
        test.assertTrue(mobs.size() == EnumMobAspect.values().length, "every row: " + mobs.size());
        LOG.info("Recipe viewers: {} necromancy pages, {} distinct entity types", views.size(), types.size());
        test.succeed();
    }

    private static TileSummon tablet(GameTestHelper test) {
        floor(test);
        test.setBlock(TABLET, Machines.SUMMON.get().defaultBlockState());
        for (BlockPos pedestal : PEDESTALS) test.setBlock(pedestal, BlocksTC.get("arcane_pedestal").defaultBlockState());
        return (TileSummon) test.getLevel().getBlockEntity(test.absolutePos(TABLET));
    }

    private static Container pedestal(GameTestHelper test, BlockPos relative) {
        return (Container) test.getLevel().getBlockEntity(test.absolutePos(relative));
    }

    /** The test's own area: neighbouring tests run at the same time, their mobs must not be counted or removed. */
    private static AABB aroundTablet(GameTestHelper test) {
        return test.getBounds();
    }

    private static List<Entity> summoned(GameTestHelper test, EntityType<?> type) {
        return test.getLevel().getEntitiesOfClass(Entity.class, aroundTablet(test), entity -> entity.getType() == type);
    }

    /**
     * Removes every mob around the tablet: the summoned one and whatever came with it ({@code finalizeSpawn} gives some
     * babies a chicken to ride and some spiders a skeleton rider), so that it cannot be counted for a later page.
     */
    private static void clearMobs(GameTestHelper test) {
        test.getLevel().getEntitiesOfClass(Entity.class, aroundTablet(test), entity -> !(entity instanceof net.minecraft.world.entity.player.Player))
                .forEach(Entity::discard);
    }

    /**
     * Every page, made real: its three soul aspects on three arcane pedestals around a tablet, one work cycle, and exactly
     * the page's mob appears. Once with the basic and once with the condensed soul aspects (both used up), once with the
     * infused ones at a multiple of 1200 ticks (kept).
     */
    public static void theTabletSummonsTheMobOfEveryPageFromEveryFormItShows(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        TileSummon tablet = tablet(test);
        test.assertTrue(tablet != null, "the tablet's block entity");
        clearMobs(test);
        int summons = 0;
        for (NecromancyView view : TinkererRecipeViews.necromancy()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(view.entityTypeId());
            for (int form = 0; form < 3; form++) {
                for (int i = 0; i < 3; i++) pedestal(test, PEDESTALS[i]).setItem(0, view.essences().get(i).get(form).copy());
                boolean infused = form == 2;
                long time = infused ? 1200 : 300;
                test.assertTrue(tablet.summon(level, time), view.mob() + " form " + form + ": the tablet works");
                List<Entity> found = summoned(test, type);
                test.assertTrue(found.size() == 1, view.mob() + " form " + form + ": one " + view.entityTypeId() + ", found " + found);
                for (int i = 0; i < 3; i++) {
                    ItemStack left = pedestal(test, PEDESTALS[i]).getItem(0);
                    test.assertTrue(infused ? ItemMobAspect.isInfused(left) : left.isEmpty(),
                            view.mob() + " form " + form + ": pedestal " + i + " holds " + left);
                    pedestal(test, PEDESTALS[i]).setItem(0, ItemStack.EMPTY);
                }
                clearMobs(test);
                summons++;
            }
        }
        LOG.info("Recipe viewers: the tablet summoned {} times from the necromancy pages", summons);
        test.succeed();
    }

    // ---- infused seeds ---------------------------------------------------------------------------------------------

    public static void theSeedPagesCoverEveryAspectWithLootAndTheChancesAreTheWeights(GameTestHelper test) {
        Map<Aspect, List<AspectCropLootManager.LootEntry>> table = AspectCropLootManager.getAllLoot();
        List<Aspect> withLoot = new ArrayList<>();
        for (Map.Entry<Aspect, List<AspectCropLootManager.LootEntry>> entry : table.entrySet()) {
            if (!entry.getValue().isEmpty()) withLoot.add(entry.getKey());
        }
        List<SeedDropView> views = TinkererRecipeViews.seedDrops();
        test.assertTrue(views.stream().map(SeedDropView::aspect).toList().equals(withLoot),
                "one page per aspect with loot, in the table's order: " + views.size() + " pages, " + withLoot.size() + " aspects with loot");
        Set<ResourceLocation> ids = new HashSet<>();
        int drops = 0;
        double worstSum = 0, worstShownSum = 0;
        StringBuilder sizes = new StringBuilder();
        for (SeedDropView view : views) {
            String tag = view.aspect().getTag();
            test.assertTrue(ids.add(view.id()) && view.id().equals(ViewerIds.seedDrops(tag)), tag + ": unique synthetic id " + view.id());
            test.assertTrue(view.seed().is(ItemInfusedSeeds.item()) && ItemInfusedSeeds.getAspect(view.seed()) == view.aspect(),
                    tag + ": the seed of the aspect " + view.seed());
            List<AspectCropLootManager.LootEntry> loot = table.get(view.aspect());
            test.assertTrue(view.drops().size() == loot.size(), tag + ": every entry of the table: " + view.drops().size() + " of " + loot.size());
            int total = 0;
            for (AspectCropLootManager.LootEntry entry : loot) total += entry.weight;
            test.assertTrue(view.totalWeight() == total, tag + ": total weight " + view.totalWeight() + " of " + total);
            double sum = 0, shown = 0;
            for (int i = 0; i < loot.size(); i++) {
                SeedDropView.Drop drop = view.drops().get(i);
                AspectCropLootManager.LootEntry entry = loot.get(i);
                test.assertTrue(!empty(drop.stack()), tag + " drop " + i + ": not empty");
                test.assertTrue(ItemStack.matches(drop.stack(), entry.stack), tag + " drop " + i + ": " + drop.stack() + " is " + entry.stack);
                test.assertTrue(drop.weight() == entry.weight, tag + " drop " + i + ": weight " + drop.weight());
                double expected = entry.weight * 100.0 / total;
                test.assertTrue(Math.abs(drop.chance() - expected) < 1e-9 && drop.chance() > 0,
                        tag + " drop " + i + ": " + drop.chance() + "% is " + entry.weight + "/" + total);
                sum += drop.chance();
                shown += Double.parseDouble(DropChances.format(drop.chance()));
            }
            test.assertTrue(Math.abs(sum - 100) < 1e-9, tag + ": the chances add up to 100, " + sum);
            // every shown number is rounded to 0.005 at most
            test.assertTrue(Math.abs(shown - 100) <= 0.005 * loot.size() + 1e-9, tag + ": the shown chances add up to " + shown);
            worstSum = Math.max(worstSum, Math.abs(sum - 100));
            worstShownSum = Math.max(worstShownSum, Math.abs(shown - 100));
            drops += view.drops().size();
            sizes.append(' ').append(tag).append('=').append(view.drops().size());
        }
        // the entries of 1.7.10's ore dictionary are item tags here: the server's tags must have filled them
        test.assertTrue(dropCount(views, Aspect.TREE) > 1, "arbor lists the logs of #minecraft:logs: " + dropCount(views, Aspect.TREE));
        test.assertTrue(dropCount(views, Aspect.PLANT) > 4, "herba lists the grasses and #minecraft:saplings: " + dropCount(views, Aspect.PLANT));
        test.assertTrue(views.stream().filter(v -> v.aspect() == Aspect.CROP).flatMap(v -> v.drops().stream())
                .anyMatch(d -> d.stack().is(Items.WHEAT_SEEDS)), "messis lists the seeds of #c:seeds");
        LOG.info("Recipe viewers: {} seed pages, {} drops; largest deviation of a page's chances from 100: {} (as shown: {});{}",
                views.size(), drops, worstSum, worstShownSum, sizes);
        test.succeed();
    }

    private static int dropCount(List<SeedDropView> views, Aspect aspect) {
        return views.stream().filter(v -> v.aspect() == aspect).mapToInt(v -> v.drops().size()).sum();
    }

    /** The EMI pages of a seed view hold every drop once, in order, at most a page's worth each; their ids are unique. */
    public static void theSeedPagesSplitIntoPagesWithoutLosingADrop(GameTestHelper test) {
        Set<ResourceLocation> ids = new HashSet<>();
        int split = 0;
        for (SeedDropView view : TinkererRecipeViews.seedDrops()) {
            List<List<SeedDropView.Drop>> pages = view.pages();
            List<SeedDropView.Drop> joined = new ArrayList<>();
            for (int page = 0; page < pages.size(); page++) {
                test.assertTrue(!pages.get(page).isEmpty() && pages.get(page).size() <= ViewerLayout.DROPS_PER_PAGE,
                        view.id() + " page " + page + ": " + pages.get(page).size() + " drops");
                test.assertTrue(ids.add(ViewerIds.page(view.id(), page)), "a second page id " + ViewerIds.page(view.id(), page));
                joined.addAll(pages.get(page));
            }
            test.assertTrue(joined.equals(view.drops()), view.id() + ": the pages hold the drops in order");
            if (pages.size() > 1) split++;
        }
        LOG.info("Recipe viewers: {} EMI seed pages, {} views split over several pages", ids.size(), split);
        test.succeed();
    }

    /**
     * The page tells the truth about the crop: ripe crops (no tendencies, so one draw each) of every aspect with a page are
     * harvested again and again; the seeds are the seeds of the aspect and every other stack is one of the page's drops.
     */
    public static void aRipeCropOfEveryPageYieldsOnlyWhatThePageLists(GameTestHelper test) {
        floor(test);
        ServerLevel level = test.getLevel();
        BlockPos soilRel = new BlockPos(3, 1, 3);
        BlockPos cropRel = soilRel.above();
        BlockPos crop = test.absolutePos(cropRel);
        BlockInfusedGrain grain = SimpleBlocks.INFUSED_GRAIN.get();
        int harvests = 0;
        Set<String> seenAspects = new HashSet<>();
        for (SeedDropView view : TinkererRecipeViews.seedDrops()) {
            Aspect aspect = view.aspect();
            test.setBlock(soilRel, SimpleBlocks.INFUSED_FARMLAND.get());
            BlockState ripe = grain.stateFor(aspect).setValue(CropBlock.AGE, 7);
            test.setBlock(cropRel, ripe);
            TileInfusedGrain tile = (TileInfusedGrain) level.getBlockEntity(crop);
            test.assertTrue(tile != null, aspect.getTag() + ": the crop's block entity");
            tile.aspect = aspect;
            int rounds = Math.max(MIN_HARVESTS, HARVESTS_PER_DROP * view.drops().size());
            for (int round = 0; round < rounds; round++) {
                List<ItemStack> yield = grain.harvest(level, crop, ripe, tile, level.random);
                test.assertTrue(yield.size() == 2, aspect.getTag() + ": seeds and one draw without an Ordo tendency: " + yield);
                test.assertTrue(yield.get(0).is(ItemInfusedSeeds.item()), aspect.getTag() + ": the seeds first " + yield.get(0));
                ItemStack loot = yield.get(1);
                test.assertTrue(view.drops().stream().anyMatch(drop -> ItemStack.matches(drop.stack(), loot)),
                        aspect.getTag() + ": " + loot + " is one of the page's drops");
                harvests++;
            }
            seenAspects.add(aspect.getTag());
        }
        LOG.info("Recipe viewers: {} harvests of ripe crops of {} aspects matched their pages", harvests, seenAspects.size());
        test.succeed();
    }
}
