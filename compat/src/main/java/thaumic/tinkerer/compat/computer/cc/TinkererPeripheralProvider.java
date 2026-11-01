/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the provider both loaders register with CC: Tweaked
 * (Fabric: {@code PeripheralLookup}, NeoForge: {@code PeripheralCapability}). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer.cc;

import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.compat.computer.PeripheralTable;
import thaumic.tinkerer.compat.computer.TinkererPeripherals;

/**
 * 1.7.10 {@code PeripheralHandler.getPeripheral(World, x, y, z, side)}: the peripheral of the block entity at a position, for
 * every side alike, or null. Only a server asks (CC: Tweaked looks peripherals up on the server); on a client copy it answers
 * null.
 */
public final class TinkererPeripheralProvider {
    private TinkererPeripheralProvider() {}

    @Nullable
    public static IPeripheral find(@Nullable BlockEntity tile, @Nullable Direction side) {
        if (tile == null || tile.isRemoved() || tile.getLevel() == null || tile.getLevel().isClientSide) return null;
        PeripheralTable table = TinkererPeripherals.find(tile);
        return table == null ? null : new TablePeripheral(table);
    }
}
