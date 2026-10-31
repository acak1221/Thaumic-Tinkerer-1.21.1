/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.viewer;

import java.util.List;

/**
 * The translation keys of the recipe-viewer pages ({@code lang-extra/recipe_viewers.<lang>.json}).
 *
 * <p>EMI names a category by the key {@code emi.category.<namespace>.<path>}; the JEI categories return the same keys,
 * so both viewers show the same titles. The English titles are the texts the 1.7.10 NEI handlers showed: the
 * necromancy page was titled with {@code tile.spawner.name} ("Tablet of Necromancy"), the seed page with
 * {@code tt.nei.category.infusedseeds} ("Infused Seed Drops"); the chance line reuses {@code tt.nei.chance} ("Drop Chance").</p>
 */
public final class ViewerKeys {
    public static final String NECROMANCY_TITLE = "emi.category.thaumictinkerer.necromancy";
    public static final String SEED_DROPS_TITLE = "emi.category.thaumictinkerer.infused_seed_drops";

    private static final String PREFIX = "thaumictinkerer.recipe_viewers.";
    /** The short line under the ring of soul aspects; its tooltip is {@link #NECROMANCY_RULES}. */
    public static final String NECROMANCY_PEDESTALS = PREFIX + "necromancy.pedestals";
    /** How the tablet really works in the port, one tooltip line each. */
    public static final List<String> NECROMANCY_RULES = List.of(
            PREFIX + "necromancy.rule.pedestals",
            PREFIX + "necromancy.rule.cycle",
            PREFIX + "necromancy.rule.infused",
            PREFIX + "necromancy.rule.redstone");
    /** The line next to the seed; its tooltip is {@link #SEED_DROPS_RULES}. */
    public static final String SEED_DROPS_HEADER = PREFIX + "seed_drops.header";
    public static final List<String> SEED_DROPS_RULES = List.of(
            PREFIX + "seed_drops.rule.roll",
            PREFIX + "seed_drops.rule.order",
            PREFIX + "seed_drops.rule.seeds");
    /** "Drop Chance: %s", the tooltip line of every drop; the argument is {@link DropChances#format} and "%". */
    public static final String CHANCE = PREFIX + "chance";
    /** "Page %s of %s", under a seed page that is split (EMI only; JEI scrolls instead). */
    public static final String PAGE = PREFIX + "page";

    private ViewerKeys() {}

    /** Every key the pages use, for the resource tests. */
    public static List<String> all() {
        List<String> keys = new java.util.ArrayList<>(List.of(NECROMANCY_TITLE, SEED_DROPS_TITLE, NECROMANCY_PEDESTALS,
                SEED_DROPS_HEADER, CHANCE, PAGE));
        keys.addAll(NECROMANCY_RULES);
        keys.addAll(SEED_DROPS_RULES);
        return List.copyOf(keys);
    }
}
