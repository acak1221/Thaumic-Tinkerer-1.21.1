/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import thaumic.tinkerer.common.block.camo.CamoDeviceBlockEntity;
import thaumic.tinkerer.common.block.tile.TileCamo;
import thaumic.tinkerer.neoforge.client.CamoModelData;

/**
 * The model data of the camouflage block entities (the Ethereal Platform's {@code TileCamo}, the devices' {@code CamoDeviceBlockEntity}):
 * the imitated state, read by the wrapped models of {@code thaumic.tinkerer.neoforge.client.NeoForgeCamoModels}. No injector: the
 * method overrides the default of NeoForge's {@code IBlockEntityExtension}. The refresh when the camouflage changes on the client is
 * {@code CamouflageHooks}' listener ({@code requestModelDataUpdate}), installed by {@code NeoForgeCamoModels.register}.
 */
@Mixin({ TileCamo.class, CamoDeviceBlockEntity.class })
public abstract class CamoModelDataMixin {
    public ModelData getModelData() {
        return CamoModelData.of((BlockEntity) (Object) this);
    }
}
