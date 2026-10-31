/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: written for JEI after what the 1.7.10 NEI handler
 * {@code NEIInfusedSeedRecipeHandler} showed, not from its code). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.jei;

import static thaumic.tinkerer.compat.viewer.ViewerLayout.*;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import thaumic.tinkerer.compat.viewer.SeedDropView;
import thaumic.tinkerer.compat.viewer.TinkererRecipeViews;
import thaumic.tinkerer.compat.viewer.ViewerKeys;
import thaumic.tinkerer.compat.viewer.ViewerText;

/**
 * The seed page: the seed and the header line as on the EMI page, the drops in JEI's scroll grid of nine columns (JEI
 * gives every page of a category the same height, so a long list scrolls instead of going on over pages).
 */
final class JeiSeedDropCategory implements IRecipeCategory<SeedDropView> {
    /** The scroll bar of JEI's grid is 14 pixels wide with 2 pixels of padding (its {@code AbstractScrollWidget}). */
    private static final int SCROLLBAR = 16;
    static final int WIDTH = SEED_WIDTH + SCROLLBAR;
    static final int HEIGHT = DROPS_Y + JEI_VISIBLE_ROWS * SLOT;
    private final IDrawable icon;

    JeiSeedDropCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(TinkererRecipeViews.seedIcon());
    }

    @Override public RecipeType<SeedDropView> getRecipeType() { return TinkererJeiPlugin.SEED_DROPS; }
    @Override public Component getTitle() { return Component.translatable(ViewerKeys.SEED_DROPS_TITLE); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public ResourceLocation getRegistryName(SeedDropView view) { return view.id(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SeedDropView view, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, SEED_X + 1, SEED_Y + 1).setStandardSlotBackground().addItemStack(view.seed());
        for (SeedDropView.Drop drop : view.drops()) {
            Component chance = ViewerText.chance(drop.chance());
            // no position: the scroll grid of createRecipeExtras places the output slots
            builder.addOutputSlot().addItemStack(drop.stack()).addRichTooltipCallback((slot, tooltip) -> tooltip.add(chance));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, SeedDropView view, IFocusGroup focuses) {
        builder.addScrollGridWidget(builder.getRecipeSlots().getSlots(RecipeIngredientRole.OUTPUT), COLUMNS, JEI_VISIBLE_ROWS)
                .setPosition(0, DROPS_Y);
    }

    @Override
    public void draw(SeedDropView view, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        JeiPages.drawLine(graphics, Component.translatable(ViewerKeys.SEED_DROPS_HEADER), SEED_TEXT_X, SEED_TEXT_Y, WIDTH - SEED_TEXT_X);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, SeedDropView view, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (JeiPages.inside(mouseX, mouseY, SEED_TEXT_X, SEED_Y, WIDTH - SEED_TEXT_X, SLOT)) {
            tooltip.addAll(ViewerText.lines(ViewerKeys.SEED_DROPS_RULES));
        }
    }
}
