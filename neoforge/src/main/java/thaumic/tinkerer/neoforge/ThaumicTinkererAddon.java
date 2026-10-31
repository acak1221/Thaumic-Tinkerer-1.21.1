/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge;

import thaumcraft.api.IThaumcraftAddon;
import thaumic.tinkerer.common.ThaumicTinkerer;

/** The add-on hook Thaumcraft calls once every registry is filled (on Fabric the {@code thaumcraft} entrypoint does the same). */
@IThaumcraftAddon.Addon
public final class ThaumicTinkererAddon implements IThaumcraftAddon {
    @Override
    public void onThaumcraftReady() {
        ThaumicTinkerer.onThaumcraftReady();
    }
}
