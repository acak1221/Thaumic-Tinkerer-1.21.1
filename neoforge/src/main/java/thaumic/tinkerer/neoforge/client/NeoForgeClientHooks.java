/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** The client hooks of the add-on on NeoForge: the item renderer backend and the groups' client glue. */
public final class NeoForgeClientHooks {
    private NeoForgeClientHooks() {}

    /** From the mod constructor (client only), before any client event. */
    public static void register(IEventBus modBus) {
    }

    /** From {@link RegisterClientExtensionsEvent}, after the shared client setup ran. */
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
    }
}
