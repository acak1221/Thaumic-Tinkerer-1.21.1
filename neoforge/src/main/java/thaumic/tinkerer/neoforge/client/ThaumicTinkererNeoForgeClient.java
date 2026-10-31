/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import thaumic.tinkerer.common.ThaumicTinkerer;

/**
 * The client side of the NeoForge entry. The shared client setup runs from {@link RegisterClientExtensionsEvent}, the first client
 * event NeoForge posts after the registries are filled and before it collects menu screens, model layers and entity renderers, which
 * the Architectury registries apply only inside those events (the same choice as Thaumcraft's NeoForge client). Client setup would be
 * too late for them; the mod constructor too early (the registry objects do not exist yet).
 */
public final class ThaumicTinkererNeoForgeClient {
    private static boolean initialised;

    private ThaumicTinkererNeoForgeClient() {}

    public static void register(IEventBus bus) {
        NeoForgeClientHooks.register(bus);
        bus.addListener(ThaumicTinkererNeoForgeClient::onRegisterClientExtensions);
    }

    private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        if (initialised) return;
        initialised = true;
        ThaumicTinkerer.initClient();
        NeoForgeClientHooks.onRegisterClientExtensions(event);
    }
}
