package thaumic.tinkerer.fabric.clientsmoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * The JEI part of {@link IntegrationsPhase}; loaded only when JEI is. Test-only code of the port (no 1.7.10 counterpart);
 * CC BY-NC-SA 3.0 like the add-on, not distributed.
 */
final class JeiProbe {
    static final ResourceLocation NECROMANCY = ResourceLocation.fromNamespaceAndPath("thaumictinkerer", "necromancy");
    static final ResourceLocation SEED_DROPS = ResourceLocation.fromNamespaceAndPath("thaumictinkerer", "infused_seed_drops");

    private JeiProbe() {}

    private static Optional<RecipeType<?>> type(ResourceLocation id) {
        IJeiRuntime runtime = HarnessJeiPlugin.runtime;
        return runtime == null ? Optional.empty() : runtime.getRecipeManager().getRecipeType(id);
    }

    private static <T> long count(IJeiRuntime runtime, RecipeType<T> type) {
        return runtime.getRecipeManager().createRecipeLookup(type).get().count();
    }

    static Script script(SmokeRun run) {
        String phase = IntegrationsPhase.PHASE;
        String group = IntegrationsPhase.GROUP;
        Script script = new Script("jei");
        script.until("JEI's runtime", () -> HarnessJeiPlugin.runtime != null, 240);
        script.run("the add-on's pages", r -> {
            page(r, "jei_necromancy_pages", NECROMANCY, ViewerExpectations.necromancyPages());
            page(r, "jei_seed_drop_pages", SEED_DROPS, ViewerExpectations.seedDropPages());
        });
        script.run("the add-on's Thaumcraft recipes", r -> {
            IJeiRuntime runtime = HarnessJeiPlugin.runtime;
            List<String> missing = new ArrayList<>();
            Map<String, Integer> byCategory = new TreeMap<>();
            List<ViewerExpectations.ThaumcraftRecipe> expected = ViewerExpectations.thaumcraftRecipes();
            for (ViewerExpectations.ThaumcraftRecipe recipe : expected) {
                ResourceLocation want = ResourceLocation.fromNamespaceAndPath("thaumcraft", recipe.kind());
                IFocus<ItemStack> focus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, recipe.output());
                boolean found = runtime.getRecipeManager().createRecipeCategoryLookup().limitFocus(List.of(focus)).get()
                        .map(IRecipeCategory::getRecipeType).anyMatch(t -> want.equals(t.getUid()));
                if (found) byCategory.merge(recipe.kind(), 1, Integer::sum);
                else missing.add(recipe.describe());
            }
            r.report().fact("jei_thaumcraft_categories_tt_recipes", byCategory);
            if (expected.isEmpty()) r.report().fail(phase, "jei_tt_recipes_in_thaumcraft_categories", group, "jei", "the add-on registered no arcane, crucible or infusion recipe with an item result");
            else if (missing.isEmpty()) r.report().pass(phase, "jei_tt_recipes_in_thaumcraft_categories", group, "jei", "all " + expected.size() + " arcane/crucible/infusion recipes of the add-on with an item result have their output in Thaumcraft's JEI category of their kind " + byCategory);
            else r.report().fail(phase, "jei_tt_recipes_in_thaumcraft_categories", group, "jei", missing.size() + " of " + expected.size() + " outputs not found in the Thaumcraft category of their kind: " + missing);
        });
        script.run("catalysts", r -> {
            Optional<RecipeType<?>> necromancy = type(NECROMANCY);
            if (necromancy.isEmpty()) return;
            List<String> catalysts = new ArrayList<>();
            HarnessJeiPlugin.runtime.getRecipeManager().createRecipeCatalystLookup(necromancy.get()).getItemStack()
                    .forEach(stack -> catalysts.add(String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()))));
            if (catalysts.stream().anyMatch(s -> s.startsWith("thaumictinkerer:"))) r.report().pass(phase, "jei_necromancy_catalysts", group, "jei", "catalysts " + catalysts);
            else r.report().fail(phase, "jei_necromancy_catalysts", group, "jei", "no add-on item among the catalysts " + catalysts);
        });
        shoot(script, run, NECROMANCY, "jei-necromancy", "JEI, the add-on's Necromancy category (first page)");
        shoot(script, run, SEED_DROPS, "jei-infused-seed-drops", "JEI, the add-on's Infused Seed Drops category (first page)");
        return script;
    }

    private static void page(SmokeRun run, String check, ResourceLocation id, int expected) {
        String phase = IntegrationsPhase.PHASE;
        Optional<RecipeType<?>> type = type(id);
        if (type.isEmpty()) {
            run.report().fail(phase, check, IntegrationsPhase.GROUP, id.toString(), "JEI has no recipe type " + id);
            return;
        }
        long found = count(HarnessJeiPlugin.runtime, type.get());
        if (found == expected) run.report().pass(phase, check, IntegrationsPhase.GROUP, id.toString(), found + " recipes, as many as the add-on's data has");
        else run.report().fail(phase, check, IntegrationsPhase.GROUP, id.toString(), found + " recipes, the add-on's data has " + expected);
    }

    private static void shoot(Script script, SmokeRun run, ResourceLocation id, String name, String description) {
        script.run("show " + id, r -> {
            Optional<RecipeType<?>> type = type(id);
            if (type.isEmpty()) throw new Script.Abort();
            r.client().options.hideGui = false;
            HarnessJeiPlugin.runtime.getRecipesGui().showTypes(List.of(type.get()));
        });
        script.until("the recipe screen", () -> run.client().screen != null, 20);
        script.frames(20);
        script.then("picture", run.shots().shot(name, IntegrationsPhase.GROUP, description), 60);
        script.run("close", r -> {
            r.client().setScreen(null);
            r.client().options.hideGui = true;
        });
        script.frames(5);
    }
}
