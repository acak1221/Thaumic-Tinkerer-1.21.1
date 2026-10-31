/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: it replaces the NEI integration of 1.7.10, {@code NEIConfig}
 * and the NEI part of {@code TTCommonProxy}, and is written from what those showed, not from their code). Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.EmiRecipeSorting;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumic.tinkerer.common.item.InfusedSeedComponents;
import thaumic.tinkerer.compat.viewer.NecromancyView;
import thaumic.tinkerer.compat.viewer.SeedDropView;
import thaumic.tinkerer.compat.viewer.TinkererRecipeViews;
import thaumic.tinkerer.compat.viewer.ViewerIds;

/**
 * Optional EMI integration: the two pages the 1.7.10 NEI integration had and Thaumcraft's own plugin does not show.
 * <ul>
 *   <li>{@code thaumictinkerer:necromancy}: per mob of the necromancy table, the three soul aspects around the Tablet of
 *       Necromancy and the Mob Display of the mob; workstations: the Cursed Spirit's Blade and the tablet.</li>
 *   <li>{@code thaumictinkerer:infused_seed_drops}: per aspect with loot, its Infused Seeds and everything a ripe crop of
 *       the aspect can yield, with the chance of each. No workstation: the infused farmland has no item.</li>
 * </ul>
 * The recipes of the add-on that are Thaumcraft recipes (arcane, crucible, infusion, enchantment infusion) are listed by
 * Thaumcraft's EMI plugin from {@code ThaumcraftApi} and are not repeated here.
 *
 * <p>EMI loads this class itself: on Fabric through the {@code emi} entrypoint of {@code fabric.mod.json}, on NeoForge
 * through {@link EmiEntrypoint}. Nothing else names it, so the add-on runs unchanged without EMI. The pages are built by
 * {@link TinkererRecipeViews} (which the JEI plugin uses too) each time EMI reloads, after the server's tags arrived.</p>
 */
@EmiEntrypoint
public final class TinkererEmiPlugin implements EmiPlugin {
    private static final Logger LOG = LoggerFactory.getLogger("ThaumicTinkerer/EMI");

    @Override
    public void register(EmiRegistry registry) {
        EmiStack tablet = EmiStack.of(TinkererRecipeViews.tablet());
        EmiRecipeCategory necromancy = new EmiRecipeCategory(ViewerIds.NECROMANCY, tablet, tablet, EmiRecipeSorting.none());
        EmiStack seedIcon = EmiStack.of(TinkererRecipeViews.seedIcon());
        EmiRecipeCategory seedDrops = new EmiRecipeCategory(ViewerIds.INFUSED_SEED_DROPS, seedIcon, seedIcon, EmiRecipeSorting.none());
        registry.addCategory(necromancy);
        registry.addCategory(seedDrops);
        for (ItemStack catalyst : TinkererRecipeViews.necromancyCatalysts()) registry.addWorkstation(necromancy, EmiStack.of(catalyst));

        // One item each, told apart by data: without these EMI takes every Mob Display for the same stack (the uses and
        // recipes of a zombie would list all thirty mobs) and every Infused Seeds for the same seed.
        registry.setDefaultComparison(TinkererRecipeViews.mobDisplayItem(),
                Comparison.compareData(stack -> TinkererRecipeViews.mobDisplayKey(stack.get(DataComponents.CUSTOM_DATA))));
        registry.setDefaultComparison(TinkererRecipeViews.seedItem(),
                Comparison.compareData(stack -> stack.get(InfusedSeedComponents.aspectType())));

        int necromancyPages = add(registry, "necromancy", TinkererRecipeViews::necromancy,
                view -> List.of(new EmiNecromancyRecipe(necromancy, view, tablet)));
        int seedPages = add(registry, "infused seed drops", TinkererRecipeViews::seedDrops, view -> {
            int pages = view.pages().size();
            List<EmiRecipe> recipes = new java.util.ArrayList<>(pages);
            for (int page = 0; page < pages; page++) recipes.add(new EmiSeedDropRecipe(seedDrops, view, page));
            return recipes;
        });

        // 1.7.10 listed the Mob Display of every mob in NEI's item panel; they follow the tablet in EMI's index.
        List<ItemStack> displays = TinkererRecipeViews.mobDisplays();
        for (int i = displays.size() - 1; i >= 0; i--) registry.addEmiStackAfter(EmiStack.of(displays.get(i)), tablet);
        LOG.info("Thaumic Tinkerer EMI pages: {} necromancy, {} infused seed drops", necromancyPages, seedPages);
    }

    /** Adds the pages of every view; a view that fails is logged and skipped, so it cannot hide the others. */
    private static <V> int add(EmiRegistry registry, String kind, Supplier<List<V>> views,
            java.util.function.Function<V, List<? extends EmiRecipe>> pages) {
        List<V> list;
        try {
            list = views.get();
        } catch (RuntimeException broken) {
            LOG.error("Thaumic Tinkerer {} pages could not be built for EMI", kind, broken);
            return 0;
        }
        int added = 0;
        for (V view : list) {
            try {
                for (EmiRecipe recipe : pages.apply(view)) {
                    registry.addRecipe(recipe);
                    added++;
                }
            } catch (RuntimeException broken) {
                LOG.warn("Skipping Thaumic Tinkerer {} page {} in EMI", kind, id(view), broken);
            }
        }
        return added;
    }

    private static Object id(Object view) {
        if (view instanceof NecromancyView necromancy) return necromancy.id();
        if (view instanceof SeedDropView seeds) return seeds.id();
        return view;
    }
}
