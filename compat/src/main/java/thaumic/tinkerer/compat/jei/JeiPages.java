/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.jei;

import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.compat.viewer.TinkererRecipeViews;
import thaumic.tinkerer.compat.viewer.ViewerText;

/** Client helpers of the JEI pages, and the two subtype interpreters. */
final class JeiPages {
    private static final String ELLIPSIS = "...";

    private JeiPages() {}

    /** Draws a line of text cut to a width with an ellipsis (the full text is in the tooltip of the line). */
    static void drawLine(GuiGraphics graphics, Component text, int x, int y, int width) {
        Font font = Minecraft.getInstance().font;
        if (font.width(text) <= width) {
            graphics.drawString(font, text, x, y, ViewerText.COLOR, false);
            return;
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width(ELLIPSIS)));
        graphics.drawString(font, Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of(ELLIPSIS, text.getStyle()))),
                x, y, ViewerText.COLOR, false);
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Mob Displays are one item; the mob is the component of the display ({@code MachineComponents.DISPLAYED_MOB}). Without
     * this JEI takes all thirty for one ingredient: the item list would show one, and the recipes of a zombie would list
     * every mob.
     */
    static final class MobDisplaySubtypes implements ISubtypeInterpreter<ItemStack> {
        @Override
        @Nullable
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            String type = TinkererRecipeViews.mobDisplayKey(stack.get(TinkererRecipeViews.mobDisplayComponent()));
            return type.isEmpty() ? null : type;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            return TinkererRecipeViews.mobDisplayKey(stack.get(TinkererRecipeViews.mobDisplayComponent()));
        }
    }

    /** Infused Seeds are one item; the seeds of one aspect are one ingredient whatever their tendencies. */
    static final class SeedSubtypes implements ISubtypeInterpreter<ItemStack> {
        @Override
        @Nullable
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            return TinkererRecipeViews.seedKey(stack);
        }

        @Override
        @SuppressWarnings("deprecation")
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            String aspect = TinkererRecipeViews.seedKey(stack);
            return aspect == null ? "" : aspect;
        }
    }
}
