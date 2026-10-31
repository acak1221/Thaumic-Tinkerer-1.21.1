/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.hooks;

import net.neoforged.bus.api.IEventBus;

/** Maps NeoForge's events onto {@code TinkererEvents} (docs/HOOKS.md section 9). */
public final class NeoForgeEventPosters {
    private NeoForgeEventPosters() {}

    public static void register(IEventBus modBus) {
    }
}
