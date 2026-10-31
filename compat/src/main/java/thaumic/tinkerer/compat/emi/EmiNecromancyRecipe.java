/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: written for EMI after what the 1.7.10 NEI handler
 * {@code NEINecromancyHandler} showed, not from its code). Distributed under the same licence as the add-on (CC BY-NC-SA 3.0,
 * see NOTICE.md).
 */
package thaumic.tinkerer.compat.emi;

import static thaumic.tinkerer.compat.viewer.ViewerLayout.*;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.network.chat.Component;
import thaumic.tinkerer.compat.viewer.NecromancyView;
import thaumic.tinkerer.compat.viewer.ViewerKeys;
import thaumic.tinkerer.compat.viewer.ViewerLayout;
import thaumic.tinkerer.compat.viewer.ViewerText;

/**
 * Three soul aspects on the ring around the Tablet of Necromancy, an arrow, the Mob Display of the mob; under the ring
 * the line "On 3 Arcane Pedestals" with the tablet's rules as its tooltip. Each soul aspect slot cycles through the basic,
 * condensed and infused soul aspect of its aspect: the tablet takes any of them.
 */
final class EmiNecromancyRecipe extends BasicEmiRecipe {
    private final EmiStack tablet;

    EmiNecromancyRecipe(EmiRecipeCategory category, NecromancyView view, EmiStack tablet) {
        super(category, view.id(), NECROMANCY_WIDTH, NECROMANCY_HEIGHT);
        this.tablet = tablet;
        this.inputs = view.essences().stream()
                .map(forms -> EmiIngredient.of(forms.stream().map(EmiStack::of).toList()))
                .toList();
        this.outputs = List.of(EmiStack.of(view.mobDisplay()));
    }

    /** A summoning is not a craft: the Mob Display is no item one gets. */
    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < inputs.size(); i++) {
            int[] at = ViewerLayout.essence(i);
            widgets.addSlot(inputs.get(i), at[0], at[1]);
        }
        widgets.addSlot(tablet, TABLET_X, TABLET_Y).catalyst(true);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, NECROMANCY_ARROW_X, NECROMANCY_ARROW_Y);
        widgets.addSlot(outputs.getFirst(), MOB_X, MOB_Y).large(true).recipeContext(this);
        Component line = Component.translatable(ViewerKeys.NECROMANCY_PEDESTALS);
        widgets.addText(EmiPages.trim(line, width), 0, NECROMANCY_TEXT_Y, ViewerText.COLOR, false);
        widgets.addTooltipText(ViewerText.lines(ViewerKeys.NECROMANCY_RULES), 0, NECROMANCY_TEXT_Y, width, TEXT_HEIGHT);
    }
}
