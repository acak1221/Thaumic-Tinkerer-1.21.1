/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.client;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import thaumic.tinkerer.common.hooks.CamouflageHooks;

/**
 * The model data of the camouflage block entities on NeoForge: the imitated state under {@link #CAMO}. NeoForge asks a block entity
 * for it on the client thread ({@code IBlockEntityExtension.getModelData}, added to the two classes by
 * {@code thaumic.tinkerer.neoforge.mixin.CamoModelDataMixin}) when it snapshots the region of a chunk section, and the wrapped models
 * ({@link NeoForgeCamoModels}) read it from the snapshot on the chunk builder threads. Holds no client-only class, because the mixin
 * puts the call into the block entities on both sides.
 */
public final class CamoModelData {
    /** The imitated state. */
    public static final ModelProperty<BlockState> CAMO = new ModelProperty<>();

    private CamoModelData() {}

    /** The model data of a camouflage block entity: the camouflage, or none. */
    public static ModelData of(BlockEntity blockEntity) {
        BlockState camo = CamouflageHooks.camouflageOf(blockEntity);
        return camo == null ? ModelData.EMPTY : ModelData.of(CAMO, camo);
    }
}
