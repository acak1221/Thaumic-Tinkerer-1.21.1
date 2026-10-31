/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: the 1.7.10 NEI handlers had no recipe ids). Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.viewer;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * The ids of the two recipe-viewer categories and of their pages. EMI and JEI use the same ids.
 *
 * <p>A page is not a recipe of the recipe manager, so its id follows EMI's convention for synthetic recipes: the path
 * starts with {@code /} ({@code thaumictinkerer:/necromancy/minecraft/zombie}), which no data pack recipe can have. The ids
 * are built from stable names only (the entity type id, the aspect tag, the page number), never from a list position.</p>
 */
public final class ViewerIds {
    /** The category of the Tablet of Necromancy (1.7.10: the NEI overlay {@code thaumictinkerer.necromancy}). */
    public static final ResourceLocation NECROMANCY = LibMisc.id("necromancy");
    /** The category of the infused crop's drops (1.7.10: the NEI overlay {@code thaumictinkerer.infusedseeds}). */
    public static final ResourceLocation INFUSED_SEED_DROPS = LibMisc.id("infused_seed_drops");

    private ViewerIds() {}

    /** {@code thaumictinkerer:/necromancy/<namespace>/<path>} of the summoned entity type. */
    public static ResourceLocation necromancy(ResourceLocation entityType) {
        return LibMisc.id("/" + NECROMANCY.getPath() + "/" + segment(entityType.getNamespace()) + "/" + entityType.getPath());
    }

    /** {@code thaumictinkerer:/infused_seed_drops/<aspect tag>}: the first (or only) page of an aspect. */
    public static ResourceLocation seedDrops(String aspectTag) {
        return LibMisc.id("/" + INFUSED_SEED_DROPS.getPath() + "/" + segment(aspectTag));
    }

    /**
     * The id of page {@code page} (counted from 0) of a seed page split by the viewer: page 0 keeps the id of the view,
     * the following pages append {@code /2}, {@code /3}, ... so a page that exists keeps its id when another one is added.
     */
    public static ResourceLocation page(ResourceLocation view, int page) {
        if (page < 0) throw new IllegalArgumentException("page " + page);
        return page == 0 ? view : view.withPath(view.getPath() + "/" + (page + 1));
    }

    /**
     * A name as one path segment: lower case, and every character a resource path does not allow (and the slash, which
     * would make a second segment) replaced by {@code _}. The aspect tags of Thaumcraft are lower-case letters already.
     */
    public static String segment(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean allowed = c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.';
            out.append(allowed ? c : '_');
        }
        return out.isEmpty() ? "_" : out.toString();
    }
}
