/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The client hooks of the add-on on NeoForge: the item renderer backend and the groups' client glue that Architectury has no
 * registry or event for (the rest of the shared client setup, screens, block entity renderers, model layers, render types,
 * item properties, colours, key mappings, reload listeners, HUD and tick handlers, goes through Architectury; docs/NEOFORGE.md
 * says when each of those is applied on NeoForge).
 */
public final class NeoForgeClientHooks {
    private NeoForgeClientHooks() {}

    /** From the mod constructor (client only), before any client event. */
    public static void register(IEventBus modBus) {
        // First: the groups queue their built-in item renderers on it from the shared client setup.
        NeoForgeItemRenderers.install();
        KamiGearNeoForgeClient.register(modBus);
    }

    /** From {@link RegisterClientExtensionsEvent}, after the shared client setup ran. */
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        NeoForgeItemRenderers.registerAll(event);
    }
}
