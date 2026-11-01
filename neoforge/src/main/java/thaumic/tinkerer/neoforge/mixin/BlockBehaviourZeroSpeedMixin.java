/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.neoforge.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * "Cannot dig" of {@code TinkererEvents.BREAK_SPEED} is a progress of exactly zero on a block of hardness zero too (docs/HOOKS.md
 * section 4). The NeoForge poster ({@code NeoForgeEventPosters.onBreakSpeed}) answers it with a new speed of zero; read in the
 * 21.1.259 bytecode, {@code BlockBehaviour.getDestroyProgress} returns {@code player.getDigSpeed(state, pos) / hardness / (30 or 100)},
 * which is {@code 0 / 0}, a NaN, for a block of hardness zero (a slime block, TNT, a flower). The server and the client both treat a
 * NaN progress as "not yet" (every comparison with it is false), but the number is wrong and anything else that reads it gets a
 * NaN. So a dig speed of zero gives a progress of zero here, whatever the hardness; a negative speed (NeoForge's own answer to a
 * cancelled {@code BreakSpeed} event) is left to NeoForge. The Fabric counterpart is the same rule in
 * {@code BlockBehaviourBreakSpeedMixin} of the Fabric module.
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourZeroSpeedMixin {
    @WrapOperation(method = "getDestroyProgress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDigSpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F"))
    private float thaumictinkerer$noteZeroSpeed(Player player, BlockState state, BlockPos pos, Operation<Float> original,
            @Share("zeroSpeed") LocalBooleanRef zeroSpeed) {
        float speed = original.call(player, state, pos);
        zeroSpeed.set(speed == 0.0F);
        return speed;
    }

    @ModifyReturnValue(method = "getDestroyProgress", at = @At("RETURN"))
    private float thaumictinkerer$zeroProgress(float progress, @Share("zeroSpeed") LocalBooleanRef zeroSpeed) {
        return zeroSpeed.get() ? 0.0F : progress;
    }
}
