/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the NeoForge client glue of the optional CC: Tweaked
 * integration. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.client;

import dan200.computercraft.api.client.turtle.RegisterTurtleModellersEvent;
import dan200.computercraft.api.client.turtle.TurtleUpgradeModeller;
import net.neoforged.bus.api.IEventBus;
import thaumic.tinkerer.compat.computer.cc.ComputerCraftCompat;

/**
 * The model of the Fume Dissipator's turtle tool: the item, drawn flat on the turtle's side (1.7.10 {@code FumeTool.getIcon}).
 * Registered from the mod constructor on a client, only when CC: Tweaked is installed.
 */
public final class ComputerCraftNeoForgeClient {
    private ComputerCraftNeoForgeClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ComputerCraftNeoForgeClient::onRegisterTurtleModellers);
    }

    private static void onRegisterTurtleModellers(RegisterTurtleModellersEvent event) {
        event.register(ComputerCraftCompat.gasRemoverType(), TurtleUpgradeModeller.flatItem());
    }
}
