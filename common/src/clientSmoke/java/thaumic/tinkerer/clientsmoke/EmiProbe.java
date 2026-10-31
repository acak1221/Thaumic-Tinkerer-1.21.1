package thaumic.tinkerer.clientsmoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;

/**
 * The EMI part of {@link IntegrationsPhase}; loaded only when EMI is. Test-only code of the port (no 1.7.10 counterpart);
 * CC BY-NC-SA 3.0 like the add-on, not distributed.
 */
final class EmiProbe {
    static final ResourceLocation NECROMANCY = ResourceLocation.fromNamespaceAndPath("thaumictinkerer", "necromancy");
    static final ResourceLocation SEED_DROPS = ResourceLocation.fromNamespaceAndPath("thaumictinkerer", "infused_seed_drops");
    private static final ResourceLocation THAUMCRAFT_ARCANE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "arcane");

    private EmiProbe() {}

    static EmiRecipeCategory category(ResourceLocation id) {
        for (EmiRecipeCategory category : EmiApi.getRecipeManager().getCategories()) if (id.equals(category.getId())) return category;
        return null;
    }

    private static int recipes(ResourceLocation id) {
        EmiRecipeCategory category = category(id);
        return category == null ? -1 : EmiApi.getRecipeManager().getRecipes(category).size();
    }

    static Script script(SmokeRun run) {
        String phase = IntegrationsPhase.PHASE;
        String group = IntegrationsPhase.GROUP;
        Script script = new Script("emi");
        int[] stable = { 0, -1 };
        // EMI reloads on its own thread after the world's recipes and tags arrived; it is done when Thaumcraft's own category is filled
        // and the number of recipes stopped moving for a second
        script.until("EMI loaded its recipes", () -> {
            int all = recipes(THAUMCRAFT_ARCANE) > 0 ? EmiApi.getRecipeManager().getRecipes().size() : -1;
            if (all > 0 && all == stable[1]) stable[0]++;
            else stable[0] = 0;
            stable[1] = all;
            return stable[0] >= 20;
        }, 240);
        script.run("the add-on's pages", r -> {
            page(r, "emi_necromancy_pages", NECROMANCY, ViewerExpectations.necromancyPages());
            page(r, "emi_seed_drop_pages", SEED_DROPS, ViewerExpectations.seedDropPages());
        });
        script.run("the add-on's Thaumcraft recipes", r -> {
            List<String> missing = new ArrayList<>();
            Map<String, Integer> byCategory = new TreeMap<>();
            List<ViewerExpectations.ThaumcraftRecipe> expected = ViewerExpectations.thaumcraftRecipes();
            for (ViewerExpectations.ThaumcraftRecipe recipe : expected) {
                ResourceLocation want = ResourceLocation.fromNamespaceAndPath("thaumcraft", recipe.kind());
                boolean found = false;
                for (EmiRecipe candidate : EmiApi.getRecipeManager().getRecipesByOutput(EmiStack.of(recipe.output()))) {
                    if (!want.equals(candidate.getCategory().getId())) continue;
                    boolean sameOutput = candidate.getOutputs().stream().anyMatch(out -> out.getItemStack().is(recipe.output().getItem()));
                    if (sameOutput) {
                        found = true;
                        break;
                    }
                }
                if (found) byCategory.merge(recipe.kind(), 1, Integer::sum);
                else missing.add(recipe.describe());
            }
            r.report().fact("emi_thaumcraft_categories_tt_recipes", byCategory);
            if (expected.isEmpty()) r.report().fail(phase, "emi_tt_recipes_in_thaumcraft_categories", group, "emi", "the add-on registered no arcane, crucible or infusion recipe with an item result");
            else if (missing.isEmpty()) r.report().pass(phase, "emi_tt_recipes_in_thaumcraft_categories", group, "emi", "all " + expected.size() + " arcane/crucible/infusion recipes of the add-on with an item result are listed in Thaumcraft's EMI category of their kind " + byCategory);
            else r.report().fail(phase, "emi_tt_recipes_in_thaumcraft_categories", group, "emi", missing.size() + " of " + expected.size() + " not listed in the Thaumcraft category of their kind: " + missing);
        });
        script.run("workstations", r -> {
            EmiRecipeCategory necromancy = category(NECROMANCY);
            if (necromancy == null) return;
            List<String> stations = new ArrayList<>();
            for (EmiIngredient ingredient : EmiApi.getRecipeManager().getWorkstations(necromancy)) {
                for (EmiStack stack : ingredient.getEmiStacks()) stations.add(String.valueOf(stack.getId()));
            }
            boolean tablet = stations.stream().anyMatch(s -> s.startsWith("thaumictinkerer:"));
            if (tablet) r.report().pass(phase, "emi_necromancy_workstations", group, "emi", "workstations " + stations);
            else r.report().fail(phase, "emi_necromancy_workstations", group, "emi", "no add-on block or item among the workstations " + stations);
        });
        shoot(script, run, NECROMANCY, "emi-necromancy", "EMI, the add-on's Necromancy category (first page): three Infused Essences around the Tablet of Necromancy, the summoned mob as a Mob Display");
        shoot(script, run, SEED_DROPS, "emi-infused-seed-drops", "EMI, the add-on's Infused Seed Drops category (first page): an infused seed and what its crop drops, with chances");
        return script;
    }

    private static void page(SmokeRun run, String check, ResourceLocation id, int expected) {
        int found = recipes(id);
        String phase = IntegrationsPhase.PHASE;
        if (found < 0) run.report().fail(phase, check, IntegrationsPhase.GROUP, id.toString(), "EMI has no category " + id);
        else if (found == expected) run.report().pass(phase, check, IntegrationsPhase.GROUP, id.toString(), found + " recipes, as many as the add-on's data has");
        else run.report().fail(phase, check, IntegrationsPhase.GROUP, id.toString(), found + " recipes, the add-on's data has " + expected);
    }

    private static void shoot(Script script, SmokeRun run, ResourceLocation id, String name, String description) {
        script.run("show " + id, r -> {
            EmiRecipeCategory category = category(id);
            if (category == null) throw new Script.Abort();
            r.client().options.hideGui = false;
            EmiApi.displayRecipeCategory(category);
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
