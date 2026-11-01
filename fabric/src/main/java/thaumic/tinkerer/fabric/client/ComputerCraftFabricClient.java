/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the Fabric client glue of the optional CC: Tweaked
 * integration. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.fabric.client;

import dan200.computercraft.api.client.FabricComputerCraftAPIClient;
import dan200.computercraft.api.client.turtle.TurtleUpgradeModeller;
import thaumic.tinkerer.compat.computer.cc.ComputerCraftCompat;

/**
 * The model of the Fume Dissipator's turtle tool: the item, drawn flat on the turtle's side (1.7.10 {@code FumeTool.getIcon},
 * the item's texture {@code ttinkerer:gasRemover}). Called by {@code ThaumicTinkererFabricClient} only when CC: Tweaked is
 * installed.
 */
public final class ComputerCraftFabricClient {
    private ComputerCraftFabricClient() {}

    public static void register() {
        FabricComputerCraftAPIClient.registerTurtleUpgradeModeller(ComputerCraftCompat.gasRemoverType(), TurtleUpgradeModeller.flatItem());
    }
}
