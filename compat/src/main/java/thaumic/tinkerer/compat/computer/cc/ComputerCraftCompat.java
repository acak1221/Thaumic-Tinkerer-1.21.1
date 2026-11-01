/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): what both loaders register with CC: Tweaked.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer.cc;

import dan200.computercraft.api.upgrades.UpgradeType;
import net.minecraft.resources.ResourceLocation;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * The optional CC: Tweaked integration (1.7.10 {@code TTCommonProxy.initCCPeripherals}: the peripheral provider and the
 * turtle upgrade). Only loader glue that has checked that CC: Tweaked is installed ({@code Computers.ccTweakedLoaded()})
 * touches this package;
 * nothing else of the add-on names a class of it, so the add-on loads and works without CC: Tweaked.
 *
 * <ul>
 *   <li>Peripherals: {@link TinkererPeripheralProvider#find}, registered as a fallback of {@code PeripheralLookup} on Fabric
 *       ({@code ComputerCraftFabric}) and as a {@code PeripheralCapability} provider of every block entity type on NeoForge
 *       ({@code ComputerCraftNeoForge}).</li>
 *   <li>The turtle upgrade: the upgrade type {@link #GAS_REMOVER_ID} ({@link FumeTool}) in CC: Tweaked's registry of turtle
 *       upgrade types; the upgrade itself is the data file {@code data/thaumictinkerer/computercraft/turtle_upgrade/gas_remover.json},
 *       which CC: Tweaked alone reads. Its model ({@code flatItem}) is registered by the client glue of each loader.</li>
 * </ul>
 */
public final class ComputerCraftCompat {
    /** The id of the turtle upgrade type and of the upgrade (1.7.10 upgrade id 171). */
    public static final ResourceLocation GAS_REMOVER_ID = ResourceLocation.fromNamespaceAndPath(LibMisc.MOD_ID, "gas_remover");

    private static final UpgradeType<FumeTool> GAS_REMOVER_TYPE = UpgradeType.simpleWithCustomItem(FumeTool::new);

    private ComputerCraftCompat() {}

    /** The upgrade type of the Fume Dissipator's turtle tool: a simple upgrade whose data file names its crafting item. */
    public static UpgradeType<FumeTool> gasRemoverType() {
        return GAS_REMOVER_TYPE;
    }
}
