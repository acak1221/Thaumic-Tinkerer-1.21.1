/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: written for EMI and JEI after what the 1.7.10 NEI handlers
 * {@code NEINecromancyHandler} and {@code NEIInfusedSeedRecipeHandler} listed, not from their code). Distributed under the same
 * licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.viewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumcraft.api.aspects.Aspect;
import thaumic.tinkerer.common.core.helper.AspectCropLootManager;
import thaumic.tinkerer.common.core.helper.EnumMobAspect;
import thaumic.tinkerer.common.item.InfusedSeedComponents;
import thaumic.tinkerer.common.item.ItemInfusedSeeds;
import thaumic.tinkerer.common.item.ItemMobAspect;
import thaumic.tinkerer.common.item.ItemMobDisplay;
import thaumic.tinkerer.common.item.MachineComponents;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * Builds the pages of both recipe viewers from the port's own data, at the moment a viewer asks (on the client, after the
 * server's tags arrived): the necromancy table {@link EnumMobAspect} and the crop table {@link AspectCropLootManager}.
 * Nothing here knows EMI or JEI; the GameTests check these lists against what the tablet and the crop really do.
 *
 * <p>When is the crop table there? {@code AspectCropLootManager.populateLootMap()} runs in {@code SimpleBlocks.ready()},
 * which {@code ThaumicTinkerer.onThaumcraftReady()} calls on both physical sides when Thaumcraft is ready, before any
 * world loads; a client therefore has its own table (the server does not send it). The entries that 1.7.10 found in the
 * ore dictionary are item tags here and are resolved by {@link AspectCropLootManager#getAllLoot()} when it is called. On a
 * client the item tags are the ones the server sent on joining; EMI and JEI build their pages after that, so the tag
 * entries are those of the server's data packs.</p>
 */
public final class TinkererRecipeViews {
    private static final Logger LOG = LoggerFactory.getLogger("ThaumicTinkerer/RecipeViewers");

    private TinkererRecipeViews() {}

    // ---- necromancy --------------------------------------------------------------------------------------------------

    /** The Tablet of Necromancy (block item {@code spawner}): the icon of the page and its tablet. */
    public static ItemStack tablet() {
        return TTRegistry.stack(LibBlockNames.SPAWNER);
    }

    /** The workstations of the necromancy page: the Cursed Spirit's Blade and the tablet (1.7.10 NEI order). */
    public static List<ItemStack> necromancyCatalysts() {
        return List.of(TTRegistry.stack(LibItemNames.BLOOD_SWORD), tablet());
    }

    /** One page per row of the necromancy table, in the order of the table. */
    public static List<NecromancyView> necromancy() {
        List<NecromancyView> views = new ArrayList<>();
        List<ItemStack> catalysts = necromancyCatalysts();
        for (EnumMobAspect mob : EnumMobAspect.values()) {
            EntityType<?> type = mob.getEntityType();
            if (type == null) {
                // only possible for an entity of a mod that is not loaded; Thaumcraft is a hard dependency
                LOG.warn("Necromancy page of {} skipped: its entity type does not exist", mob.name());
                continue;
            }
            ResourceLocation typeId = mob.getEntityTypeKey().location();
            List<Aspect> aspects = List.of(mob.aspects);
            List<List<ItemStack>> essences = new ArrayList<>();
            for (Aspect aspect : aspects) essences.add(acceptedEssences(aspect));
            views.add(new NecromancyView(ViewerIds.necromancy(typeId), mob, typeId, aspects, essences,
                    ItemMobDisplay.stackFor(mob), catalysts));
        }
        return views;
    }

    /** Basic, condensed and infused soul aspect of an aspect: every form the tablet accepts. */
    public static List<ItemStack> acceptedEssences(Aspect aspect) {
        return List.of(ItemMobAspect.getStackFromAspect(aspect), ItemMobAspect.getCondensedStackFromAspect(aspect),
                ItemMobAspect.getInfusedStackFromAspect(aspect));
    }

    /**
     * A Mob Display of every mob, in the order of the table. 1.7.10 listed these in NEI's item panel (the
     * {@code getSubItems} of {@code ItemMobDisplay}); the viewers add them to their item lists.
     */
    public static List<ItemStack> mobDisplays() {
        List<ItemStack> displays = new ArrayList<>();
        for (EnumMobAspect mob : EnumMobAspect.values()) displays.add(ItemMobDisplay.stackFor(mob));
        return displays;
    }

    /**
     * What tells two Mob Displays apart: the name of the row of the table the display shows ("" for none). Takes the value
     * of the component {@link #mobDisplayComponent()} ({@code stack.get(...)}), which EMI hands out without a stack.
     */
    public static String mobDisplayKey(@Nullable EnumMobAspect mob) {
        return mob == null ? "" : mob.name();
    }

    /** The component that holds the mob of a Mob Display ({@code MachineComponents.DISPLAYED_MOB}). */
    public static DataComponentType<EnumMobAspect> mobDisplayComponent() {
        return MachineComponents.DISPLAYED_MOB.get();
    }

    public static Item mobDisplayItem() {
        return TTRegistry.item(LibItemNames.MOB_DISPLAY);
    }

    // ---- infused seeds -----------------------------------------------------------------------------------------------

    /** The icon of the seed page: Infused Seeds of Aer (1.7.10: the display stack of the NEI handler). */
    public static ItemStack seedIcon() {
        return ItemInfusedSeeds.getStackFromAspect(Aspect.AIR);
    }

    public static Item seedItem() {
        return ItemInfusedSeeds.item();
    }

    /**
     * What tells two Infused Seeds apart on a page: the aspect only. A seed that a crop dropped carries the crop's
     * tendencies and the creative tab's seeds carry empty tendencies; all of them are the seed of their aspect.
     */
    @Nullable
    public static String seedKey(ItemStack stack) {
        return stack.get(InfusedSeedComponents.aspectType());
    }

    /** One page per aspect that has loot, in the order of {@link Aspect#aspects} (what {@code getAllLoot} iterates). */
    public static List<SeedDropView> seedDrops() {
        List<SeedDropView> views = new ArrayList<>();
        for (Map.Entry<Aspect, List<AspectCropLootManager.LootEntry>> entry : AspectCropLootManager.getAllLoot().entrySet()) {
            List<AspectCropLootManager.LootEntry> loot = entry.getValue();
            if (loot == null || loot.isEmpty()) continue;
            int total = 0;
            for (AspectCropLootManager.LootEntry drop : loot) total += drop.weight;
            if (total <= 0) {
                LOG.warn("Seed page of {} skipped: its loot has no weight", entry.getKey().getTag());
                continue;
            }
            List<SeedDropView.Drop> drops = new ArrayList<>(loot.size());
            for (AspectCropLootManager.LootEntry drop : loot) {
                drops.add(new SeedDropView.Drop(drop.stack.copy(), drop.weight, DropChances.percent(drop.weight, total)));
            }
            Aspect aspect = entry.getKey();
            views.add(new SeedDropView(ViewerIds.seedDrops(aspect.getTag()), aspect, ItemInfusedSeeds.getStackFromAspect(aspect),
                    drops, total));
        }
        return views;
    }
}
