/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import dev.architectury.platform.Platform;

/**
 * Whether the computer mod is installed (1.7.10 {@code LoadedMods.computerCraftLoaded}). This class names no class of the
 * computer mod, so the loader entrypoints may ask it before they touch {@code compat/.../computer/cc} and the loader glue.
 */
public final class Computers {
    /** The mod id of CC: Tweaked. */
    public static final String CC_TWEAKED = "computercraft";

    private Computers() {}

    public static boolean ccTweakedLoaded() {
        return Platform.isModLoaded(CC_TWEAKED);
    }
}
