package thaumic.tinkerer.fabric.clientsmoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumic.tinkerer.common.core.helper.AspectCropLootManager;
import thaumic.tinkerer.common.core.helper.EnumMobAspect;
import thaumic.tinkerer.common.registry.TTRecipes;

/**
 * What the recipe viewers should show, computed from the add-on's own data and not from its viewer plugins. Test-only code of
 * the port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on, not distributed.
 */
final class ViewerExpectations {
    /** A recipe the add-on registered through the Thaumcraft API, with the kind of Thaumcraft category that should list it. */
    record ThaumcraftRecipe(String name, String kind, ItemStack output) {
        String describe() {
            return name + " (" + kind + ") -> " + BuiltInRegistries.ITEM.getKey(output.getItem());
        }
    }

    private ViewerExpectations() {}

    static int necromancyPages() {
        return EnumMobAspect.values().length;
    }

    /** The aspects that have at least one drop (a seed without loot has no page). */
    static int seedDropPages() {
        int pages = 0;
        for (Map.Entry<?, ? extends List<?>> entry : AspectCropLootManager.getAllLoot().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) pages++;
        }
        return pages;
    }

    /** Arcane, crucible and infusion recipes with an item result; an infusion that changes the input's data has none and is left out. */
    static List<ThaumcraftRecipe> thaumcraftRecipes() {
        List<ThaumcraftRecipe> recipes = new ArrayList<>();
        for (Map.Entry<String, Object> entry : TTRecipes.all().entrySet()) {
            Object recipe = entry.getValue();
            if (recipe instanceof IArcaneRecipe arcane && !arcane.getRecipeOutput().isEmpty()) {
                recipes.add(new ThaumcraftRecipe(entry.getKey(), "arcane", arcane.getRecipeOutput()));
            } else if (recipe instanceof CrucibleRecipe crucible && !crucible.getRecipeOutput().isEmpty()) {
                recipes.add(new ThaumcraftRecipe(entry.getKey(), "crucible", crucible.getRecipeOutput()));
            } else if (recipe instanceof InfusionRecipe infusion && infusion.getRecipeOutput() instanceof ItemStack stack && !stack.isEmpty()) {
                recipes.add(new ThaumcraftRecipe(entry.getKey(), "infusion", stack));
            }
        }
        return recipes;
    }
}
