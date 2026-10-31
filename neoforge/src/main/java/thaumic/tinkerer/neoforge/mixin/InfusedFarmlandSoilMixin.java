/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;
import org.spongepowered.asm.mixin.Mixin;
import thaumic.tinkerer.common.block.BlockInfusedFarmland;
import thaumic.tinkerer.common.block.BlockInfusedGrain;

/**
 * Infused Farmland grows Infused Grain only, as in 1.7.10 and on Fabric. NeoForge patches {@code CropBlock.mayPlaceOn} to accept any
 * {@code FarmBlock} (vanilla accepts {@code Blocks.FARMLAND} only), and Infused Farmland is a {@code FarmBlock}: without this, wheat, stems
 * and saplings would grow on it there. The soil's answer comes first in NeoForge's plant checks; the grain keeps its own rule
 * ({@code TriState.DEFAULT} falls back to its {@code mayPlaceOn}). Checked by the GameTest {@code simpleblocksgametests.acropneedsfarmland}.
 */
@Mixin(BlockInfusedFarmland.class)
public abstract class InfusedFarmlandSoilMixin {
    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos soilPosition, Direction facing, BlockState plant) {
        return plant.getBlock() instanceof BlockInfusedGrain ? TriState.DEFAULT : TriState.FALSE;
    }
}
