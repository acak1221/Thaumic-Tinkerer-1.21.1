/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the Fabric energy glue of the Transvector Interface.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.fabric;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.content.TileBlocks;

/**
 * Forwards the energy storage of the block a Transvector Interface is bound to, through Team Reborn Energy (1.7.10: the interface
 * was a CoFH {@code IEnergyHandler} that handed {@code receiveEnergy}, {@code extractEnergy}, {@code getEnergyStored},
 * {@code getMaxEnergyStored} and {@code canConnectEnergy} to the bound tile entity with the side it was asked for, and answered 0 or
 * {@code false} while it had none).
 *
 * <p>Team Reborn Energy is an <b>optional</b> dependency: the add-on compiles against it and never bundles or requires it. This is the
 * only class of the add-on that names its API, and {@code ThaumicTinkererFabric.onThaumcraftReady()} calls {@link #register()} only when
 * {@link TileBlocksFabric#teamRebornEnergyLoaded()}, so without the mod this class is never loaded and the Interface has no energy
 * storage (as a 1.7.10 interface without CoFH had none).</p>
 *
 * <p>The shape is that of the fluids ({@link TileBlocksFabric}): the lookup answers with the bound block's own storage for the same side,
 * so the amounts are that storage's, with no conversion; Fabric's lookups ask the provider on every {@code find}, so nothing goes stale.
 * Unbound, bound to a block without a storage for that side, bound into an unloaded chunk, bound to itself or to another Interface
 * ({@link TileTransvectorInterface#bound()}, which also ends such a binding on the server): no storage. The NeoForge counterpart is
 * {@code NeoForgeCapabilities} ({@code Capabilities.EnergyStorage.BLOCK}).</p>
 */
public final class TileBlocksFabricEnergy {
    private static boolean registered;

    private TileBlocksFabricEnergy() {}

    /** Registers the energy lookup of the Transvector Interface (once). Only call it when Team Reborn Energy is installed. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        EnergyStorage.SIDED.registerForBlockEntity(TileBlocksFabricEnergy::boundStorage, TileBlocks.interfaceType());
    }

    /** Has {@link #register()} run (the Interface forwards energy)? */
    public static synchronized boolean registered() {
        return registered;
    }

    /** The energy storage of the bound block for {@code side}, or null (see the class comment). */
    @Nullable
    static EnergyStorage boundStorage(TileTransvectorInterface device, @Nullable Direction side) {
        Level level = device.getLevel();
        BlockEntity bound = device.bound();
        if (level == null || bound == null) return null;
        return EnergyStorage.SIDED.find(level, bound.getBlockPos(), bound.getBlockState(), bound, side);
    }
}
