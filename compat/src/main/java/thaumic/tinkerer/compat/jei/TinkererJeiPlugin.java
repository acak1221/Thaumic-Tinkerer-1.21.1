/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: it replaces the NEI integration of 1.7.10, {@code NEIConfig}
 * and the NEI part of {@code TTCommonProxy}, and is written from what those showed, not from their code). Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.compat.viewer.NecromancyView;
import thaumic.tinkerer.compat.viewer.SeedDropView;
import thaumic.tinkerer.compat.viewer.TinkererRecipeViews;
import thaumic.tinkerer.compat.viewer.ViewerIds;

/**
 * Optional JEI integration: the same two pages as the EMI plugin ({@code thaumictinkerer:necromancy},
 * {@code thaumictinkerer:infused_seed_drops}), built from the same {@link TinkererRecipeViews}. Thaumcraft's JEI plugin lists
 * the add-on's arcane, crucible and infusion recipes; they are not repeated here.
 *
 * <p>JEI loads this class itself: on Fabric through the {@code jei_mod_plugin} entrypoint of {@code fabric.mod.json}, on
 * NeoForge through {@link JeiPlugin}. Nothing else names it. When EMI and JEI are both installed, EMI skips the JEI plugins
 * of mods that have an EMI plugin (by the namespace of {@link #getPluginUid()}), so the pages are not listed twice.</p>
 */
@JeiPlugin
public final class TinkererJeiPlugin implements IModPlugin {
    static final RecipeType<NecromancyView> NECROMANCY =
            RecipeType.create(ViewerIds.NECROMANCY.getNamespace(), ViewerIds.NECROMANCY.getPath(), NecromancyView.class);
    static final RecipeType<SeedDropView> SEED_DROPS =
            RecipeType.create(ViewerIds.INFUSED_SEED_DROPS.getNamespace(), ViewerIds.INFUSED_SEED_DROPS.getPath(), SeedDropView.class);
    private static final Logger LOG = LoggerFactory.getLogger("ThaumicTinkerer/JEI");

    @Override
    public ResourceLocation getPluginUid() {
        return LibMisc.id("jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(TinkererRecipeViews.mobDisplayItem(), new JeiPages.MobDisplaySubtypes());
        registration.registerSubtypeInterpreter(TinkererRecipeViews.seedItem(), new JeiPages.SeedSubtypes());
    }

    /** 1.7.10 listed the Mob Display of every mob in NEI's item panel. */
    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        registration.addExtraItemStacks(TinkererRecipeViews.mobDisplays());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new JeiNecromancyCategory(gui), new JeiSeedDropCategory(gui));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(NECROMANCY, TinkererRecipeViews.necromancyCatalysts().toArray(ItemStack[]::new));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<NecromancyView> necromancy = built("necromancy", TinkererRecipeViews::necromancy);
        List<SeedDropView> seedDrops = built("infused seed drops", TinkererRecipeViews::seedDrops);
        registration.addRecipes(NECROMANCY, necromancy);
        registration.addRecipes(SEED_DROPS, seedDrops);
        LOG.info("Thaumic Tinkerer JEI pages: {} necromancy, {} infused seed drops", necromancy.size(), seedDrops.size());
    }

    /** The views, or none when they cannot be built (logged): a broken table must not stop JEI. */
    private static <V> List<V> built(String kind, Supplier<List<V>> views) {
        try {
            return new ArrayList<>(views.get());
        } catch (RuntimeException broken) {
            LOG.error("Thaumic Tinkerer {} pages could not be built for JEI", kind, broken);
            return List.of();
        }
    }
}
