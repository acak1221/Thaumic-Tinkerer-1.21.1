/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: written for EMI after what the 1.7.10 NEI handler
 * {@code NEIInfusedSeedRecipeHandler} showed, not from its code). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.emi;

import static thaumic.tinkerer.compat.viewer.ViewerLayout.*;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.network.chat.Component;
import thaumic.tinkerer.compat.viewer.SeedDropView;
import thaumic.tinkerer.compat.viewer.ViewerIds;
import thaumic.tinkerer.compat.viewer.ViewerKeys;
import thaumic.tinkerer.compat.viewer.ViewerLayout;
import thaumic.tinkerer.compat.viewer.ViewerText;

/**
 * The seed of an aspect and what its ripe crop can yield, nine to a row, each with its chance in the tooltip ("Drop
 * Chance: 12.5%", as 1.7.10). A list longer than {@link ViewerLayout#DROPS_PER_PAGE} goes on over several pages (EMI has no
 * scrolling widget); they say "Page n of m" at the bottom.
 */
final class EmiSeedDropRecipe extends BasicEmiRecipe {
    private final List<SeedDropView.Drop> drops;
    private final int page, pages;

    EmiSeedDropRecipe(EmiRecipeCategory category, SeedDropView view, int page) {
        super(category, ViewerIds.page(view.id(), page), SEED_WIDTH, 0);
        List<List<SeedDropView.Drop>> split = view.pages();
        this.drops = split.get(page);
        this.page = page;
        this.pages = split.size();
        this.height = seedHeight(drops.size()) + (pages > 1 ? 2 + TEXT_HEIGHT : 0);
        this.inputs = List.of(EmiStack.of(view.seed()));
        this.outputs = drops.stream().map(drop -> EmiStack.of(drop.stack())).toList();
    }

    /** A crop's harvest is a draw, not a craft. */
    @Override
    public boolean supportsRecipeTree() {
        return false;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.getFirst(), SEED_X, SEED_Y);
        Component header = Component.translatable(ViewerKeys.SEED_DROPS_HEADER);
        int textWidth = width - SEED_TEXT_X;
        widgets.addText(EmiPages.trim(header, textWidth), SEED_TEXT_X, SEED_TEXT_Y, ViewerText.COLOR, false);
        widgets.addTooltipText(ViewerText.lines(ViewerKeys.SEED_DROPS_RULES), SEED_TEXT_X, SEED_Y, textWidth, SLOT);
        for (int i = 0; i < drops.size(); i++) {
            int[] at = ViewerLayout.drop(i);
            widgets.addSlot(outputs.get(i), at[0], at[1]).appendTooltip(ViewerText.chance(drops.get(i).chance())).recipeContext(this);
        }
        if (pages > 1) {
            widgets.addText(ViewerText.page(page, pages), 0, seedHeight(drops.size()) + 2, ViewerText.COLOR, false);
        }
    }
}
