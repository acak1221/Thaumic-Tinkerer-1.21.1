/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the Fabric GameTest harness, test-only. Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.fabric.testing.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumic.tinkerer.testing.ProtectionRuntimeChecks;

/**
 * Spawn protection for the GameTest server: only a dedicated server has it ({@code DedicatedServer.isUnderSpawnProtection}), the
 * GameTest server keeps vanilla's {@code false}. {@code ProtectionRuntimeChecks} puts single positions under protection while its
 * check runs, and {@code ServerLevel.mayInteract} then refuses them as a dedicated server's spawn would. Every other position, and
 * every position while no check asks, is left to the game.
 */
@Mixin(MinecraftServer.class)
public abstract class TinkererSpawnProtectionMixin {
    @Inject(method = "isUnderSpawnProtection", at = @At("HEAD"), cancellable = true)
    private void thaumictinkerer$testSpawnProtection(ServerLevel level, BlockPos pos, Player player, CallbackInfoReturnable<Boolean> cir) {
        if (ProtectionRuntimeChecks.isSpawnProtected(level, pos)) cir.setReturnValue(true);
    }
}
