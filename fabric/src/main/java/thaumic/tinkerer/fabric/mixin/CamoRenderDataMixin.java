/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md): Fabric glue. Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.fabric.mixin;

import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import thaumic.tinkerer.common.block.camo.CamoDeviceBlockEntity;
import thaumic.tinkerer.common.block.tile.TileCamo;
import thaumic.tinkerer.common.hooks.CamouflageHooks;

/**
 * The render data of the camouflage block entities (the Ethereal Platform's {@code TileCamo}, the devices'
 * {@code CamoDeviceBlockEntity}): the imitated {@code BlockState}, or null. Fabric API reads it on the client thread when it builds
 * the region of a chunk section, and the wrapped models ({@code thaumic.tinkerer.fabric.client.FabricCamoModels}) read it from there
 * with {@code getBlockEntityRenderData}. A block state is immutable, so the chunk builder threads may hold it. No injector: the
 * method overrides the default of {@code RenderDataBlockEntity}, which Fabric API adds to every block entity.
 */
@Mixin({ TileCamo.class, CamoDeviceBlockEntity.class })
public abstract class CamoRenderDataMixin implements RenderDataBlockEntity {
    @Override
    @Nullable
    public Object getRenderData() {
        return CamouflageHooks.camouflageOf((BlockEntity) (Object) this);
    }
}
