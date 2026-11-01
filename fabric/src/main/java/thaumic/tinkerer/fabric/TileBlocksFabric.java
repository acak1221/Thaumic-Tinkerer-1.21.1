/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the Fabric glue of the Transvector Interface.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.fabric;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.content.TileBlocks;

/**
 * Forwards the fluid storage of the block a Transvector Interface is bound to (1.7.10: the interface was an
 * {@code IFluidHandler} that handed {@code fill}, {@code drain}, {@code canFill}, {@code canDrain} and {@code getTankInfo}
 * to the bound tile entity, side for side).
 *
 * <p>The Fabric Transfer API asks a block entity for its {@code Storage<FluidVariant>} through
 * {@link FluidStorage#SIDED}; the interface answers with the storage of the bound block for the same side, so a pipe or
 * tank next to the interface fills and drains the bound tank. Items already go through the interface's
 * {@code WorldlyContainer}, which the Transfer API wraps itself, and essentia through the Thaumcraft interfaces, so only
 * fluids need this class.</p>
 *
 * <p><b>Energy</b> (1.7.10: CoFH RF) is forwarded by {@link TileBlocksFabricEnergy}, through Team Reborn Energy, an optional mod:
 * {@code ThaumicTinkererFabric} calls it only when {@link #teamRebornEnergyLoaded()}, so that no class of that mod is loaded
 * without it (this class names none). See docs/deviations/TileBlocks.md, 2.5.</p>
 *
 * <p>{@code ThaumicTinkererFabric.onThaumcraftReady()} calls {@link #register()} once the shared phase ran, so nothing has
 * to be listed in {@code fabric.mod.json}. The NeoForge counterpart is {@code NeoForgeCapabilities} (the fluid capability
 * and, because NeoForge does not wrap a {@code WorldlyContainer} by itself, the item capability).</p>
 */
public final class TileBlocksFabric {
    /** The mod id of Team Reborn Energy (the Fabric energy API), an optional dependency of the add-on. */
    public static final String TEAM_REBORN_ENERGY = "team_reborn_energy";
    private static boolean registered;

    private TileBlocksFabric() {}

    /** Registers the fluid lookup of the Transvector Interface (once). */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        FluidStorage.SIDED.registerForBlockEntity(TileBlocksFabric::boundStorage, TileBlocks.interfaceType());
    }

    /** Is Team Reborn Energy installed, so that {@link TileBlocksFabricEnergy} may be loaded? */
    public static boolean teamRebornEnergyLoaded() {
        return FabricLoader.getInstance().isModLoaded(TEAM_REBORN_ENERGY);
    }

    /**
     * The fluid storage of the bound block for {@code side}, or null while the interface is not bound, the bound block has
     * none, or it is another interface (which the Binder refuses to bind and which would send the lookup round in a circle).
     */
    @Nullable
    static Storage<FluidVariant> boundStorage(TileTransvectorInterface device, @Nullable Direction side) {
        Level level = device.getLevel();
        BlockEntity bound = device.getTile();
        if (level == null || bound == null || bound == device || bound instanceof TileTransvectorInterface) return null;
        return FluidStorage.SIDED.find(level, bound.getBlockPos(), bound.getBlockState(), bound, side);
    }
}
