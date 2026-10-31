/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.emi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Client helpers of the EMI pages. */
final class EmiPages {
    private static final String ELLIPSIS = "...";

    private EmiPages() {}

    /** The text cut to a width with an ellipsis; the full text is in the tooltip of the line. */
    static FormattedCharSequence trim(Component text, int width) {
        Font font = Minecraft.getInstance().font;
        if (font.width(text) <= width) return text.getVisualOrderText();
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width(ELLIPSIS)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of(ELLIPSIS, text.getStyle())));
    }
}
