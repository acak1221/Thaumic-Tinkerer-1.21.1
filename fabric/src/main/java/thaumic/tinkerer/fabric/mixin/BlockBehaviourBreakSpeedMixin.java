/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import thaumic.tinkerer.common.hooks.TinkererEvents;

/**
 * Posts {@link TinkererEvents#BREAK_SPEED} where the dig speed enters the destroy progress:
 * {@code BlockBehaviour.getDestroyProgress} computes {@code player.getDestroySpeed(state) / hardness / (30 or 100)}, and
 * the call of {@code Player.getDestroySpeed} is wrapped so the listeners see the position as well (the method itself has none).
 * Both sides run {@code getDestroyProgress} (the client predicts the crack from it), and so does any code that asks a
 * player's progress, a never-ticked fake player included, so the event is posted for all of them.
 *
 * <p>A speed of zero (a listener said the player cannot dig the block) gives a progress of exactly zero, also on a block of
 * hardness zero (a slime block, TNT, a flower), where the division alone would be {@code 0 / 0}, a NaN: the speed is noted in the
 * wrapper and the method's result is replaced by zero ({@code @Share} between the two injectors). Both sides treated the NaN as
 * "not yet", but the number was wrong for anything else that reads it. A negative speed is not made by this event; the NeoForge
 * counterpart is {@code BlockBehaviourZeroSpeedMixin} of the NeoForge module.</p>
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourBreakSpeedMixin {
    @WrapOperation(method = "getDestroyProgress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F"))
    private float thaumictinkerer$breakSpeed(Player player, BlockState state, Operation<Float> original,
            @Local(argsOnly = true) BlockPos pos, @Share("zeroSpeed") LocalBooleanRef zeroSpeed) {
        float speed = TinkererEvents.postBreakSpeed(player, state, pos, original.call(player, state));
        zeroSpeed.set(speed == 0.0F);
        return speed;
    }

    @ModifyReturnValue(method = "getDestroyProgress", at = @At("RETURN"))
    private float thaumictinkerer$zeroProgress(float progress, @Share("zeroSpeed") LocalBooleanRef zeroSpeed) {
        return zeroSpeed.get() ? 0.0F : progress;
    }
}
