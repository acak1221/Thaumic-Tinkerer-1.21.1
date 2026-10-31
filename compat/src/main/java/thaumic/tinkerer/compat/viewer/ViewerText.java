/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.viewer;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** The texts of the pages as components (no client classes: the GameTests load this package on a server). */
public final class ViewerText {
    /** The text colour of both viewers' pages (the dark grey of vanilla container titles). */
    public static final int COLOR = 0xFF404040;

    private ViewerText() {}

    /**
     * "Drop Chance: 12.5%": the 1.7.10 tooltip line, grey with the number and the percent sign in yellow
     * ({@code GRAY + "Drop Chance" + ": " + YELLOW + chance + "%"}); the key holds the words, the argument the number.
     */
    public static Component chance(double percent) {
        return Component.translatable(ViewerKeys.CHANCE, Component.literal(DropChances.format(percent) + "%").withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY);
    }

    /** One tooltip line per key. */
    public static List<Component> lines(List<String> keys) {
        return keys.stream().map(key -> (Component) Component.translatable(key)).toList();
    }

    /** "Page 2 of 3". */
    public static Component page(int page, int pages) {
        return Component.translatable(ViewerKeys.PAGE, page + 1, pages);
    }
}
