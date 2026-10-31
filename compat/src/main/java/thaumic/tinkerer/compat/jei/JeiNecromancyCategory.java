/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: written for JEI after what the 1.7.10 NEI handler
 * {@code NEINecromancyHandler} showed, not from its code). Distributed under the same licence as the add-on (CC BY-NC-SA 3.0,
 * see NOTICE.md).
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
import net.minecraft.world.item.ItemStack;
import thaumic.tinkerer.compat.viewer.NecromancyView;
import thaumic.tinkerer.compat.viewer.TinkererRecipeViews;
import thaumic.tinkerer.compat.viewer.ViewerKeys;
import thaumic.tinkerer.compat.viewer.ViewerLayout;
import thaumic.tinkerer.compat.viewer.ViewerText;

/** The necromancy page, laid out as the EMI page ({@link ViewerLayout}); JEI places an item one pixel inside its frame. */
final class JeiNecromancyCategory implements IRecipeCategory<NecromancyView> {
    /** JEI's arrow is 22 x 16, EMI's 24 x 17: centred in the same place. */
    private static final int ARROW_OFFSET = 1;
    private final IDrawable icon;
    private final ItemStack tablet;

    JeiNecromancyCategory(IGuiHelper gui) {
        this.tablet = TinkererRecipeViews.tablet();
        this.icon = gui.createDrawableItemStack(tablet);
    }

    @Override public RecipeType<NecromancyView> getRecipeType() { return TinkererJeiPlugin.NECROMANCY; }
    @Override public Component getTitle() { return Component.translatable(ViewerKeys.NECROMANCY_TITLE); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return NECROMANCY_WIDTH; }
    @Override public int getHeight() { return NECROMANCY_HEIGHT; }
    @Override public ResourceLocation getRegistryName(NecromancyView view) { return view.id(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, NecromancyView view, IFocusGroup focuses) {
        for (int i = 0; i < view.essences().size(); i++) {
            int[] at = ViewerLayout.essence(i);
            builder.addSlot(RecipeIngredientRole.INPUT, at[0] + 1, at[1] + 1).setStandardSlotBackground().addItemStacks(view.essences().get(i));
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, TABLET_X + 1, TABLET_Y + 1).setStandardSlotBackground().addItemStack(tablet);
        // the large frame is 26 x 26 around a 16 x 16 item
        builder.addSlot(RecipeIngredientRole.OUTPUT, MOB_X + (LARGE_SLOT - 16) / 2, MOB_Y + (LARGE_SLOT - 16) / 2)
                .setOutputSlotBackground().addItemStack(view.mobDisplay());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, NecromancyView view, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(NECROMANCY_ARROW_X + ARROW_OFFSET, NECROMANCY_ARROW_Y);
    }

    @Override
    public void draw(NecromancyView view, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        JeiPages.drawLine(graphics, Component.translatable(ViewerKeys.NECROMANCY_PEDESTALS), 0, NECROMANCY_TEXT_Y, NECROMANCY_WIDTH);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, NecromancyView view, IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (JeiPages.inside(mouseX, mouseY, 0, NECROMANCY_TEXT_Y, NECROMANCY_WIDTH, TEXT_HEIGHT)) {
            tooltip.addAll(ViewerText.lines(ViewerKeys.NECROMANCY_RULES));
        }
    }
}
