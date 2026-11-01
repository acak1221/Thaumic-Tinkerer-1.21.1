/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.common.hooks;

import java.util.Objects;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the loaders' chunk renderers need to know about the camouflage blocks (the Ethereal Platform of the SimpleBlocks group,
 * the Transvector Interface, Dislocator, Remote Placer and Golem Connector of the TileBlocks group). 1.7.10 asked the camouflage
 * block for the icons of the imitated block; in 1.21.1 the block's baked model is wrapped by the loader glue (Fabric:
 * {@code thaumic.tinkerer.fabric.client.FabricCamoModels}; NeoForge: {@code thaumic.tinkerer.neoforge.client.NeoForgeCamoModels}),
 * and the wrapper draws the imitated state's model in the chunk mesh. It reads the camouflage from the render data that the
 * loader snapshots from the block entity on the client thread (Fabric: {@code RenderDataBlockEntity.getRenderData}; NeoForge:
 * {@code IBlockEntityExtension.getModelData}), both added to the two block entity classes by a mixin of the loader that calls
 * {@link #camouflageOf}.
 *
 * <p>A change of the camouflage from one block to another does not change the block state, so nothing would build the chunk
 * section again: the block entities call {@link #changed} whenever they read a different camouflage, which on the client
 * marks the section for a rebuild and tells the loader's {@link Listener} (NeoForge: {@code requestModelDataUpdate()}).</p>
 */
public final class CamouflageHooks {
    /** A block entity that imitates another block: {@code TileCamo} and {@code CamoDeviceBlockEntity}. */
    public interface Holder {
        /** The state the block imitates, or null for its own look. */
        @Nullable
        BlockState getCamo();
    }

    /** The loader side: told on the client thread that a block entity's camouflage changed. */
    public interface Listener {
        void changed(BlockEntity blockEntity);
    }

    private static volatile Listener listener = blockEntity -> {};

    private CamouflageHooks() {}

    /** Installed once by the client glue of the loader that needs it. */
    public static void setListener(Listener newListener) {
        listener = Objects.requireNonNull(newListener);
    }

    /** The camouflage of a block entity of either family, or null (no camouflage, or not a camouflage block entity). */
    @Nullable
    public static BlockState camouflageOf(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof Holder holder ? holder.getCamo() : null;
    }

    /**
     * Called by a camouflage block entity after it read a camouflage that differs from the one it had. On a client the chunk
     * section is marked for a rebuild (the block state is the same, so the client's own block update would not do it), after
     * the loader's listener has heard of it; on a server nothing happens (the server sends the change itself).
     */
    public static void changed(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        if (level == null || !level.isClientSide) return;
        listener.changed(blockEntity);
        BlockState state = blockEntity.getBlockState();
        level.sendBlockUpdated(blockEntity.getBlockPos(), state, state, Block.UPDATE_ALL);
    }
}
