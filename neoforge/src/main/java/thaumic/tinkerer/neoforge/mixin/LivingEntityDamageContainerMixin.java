/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.neoforge.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import java.util.Stack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes away the {@code DamageContainer} that NeoForge leaves on an entity when {@code LivingIncomingDamageEvent} is cancelled
 * (docs/HOOKS.md section 3). Read in the 21.1.259 bytecode of {@code LivingEntity.hurt}: after the four early {@code return false}
 * checks it pushes a new container on {@code damageContainers} and returns {@code false} at once when
 * {@code CommonHooks.onEntityIncomingDamage} says the event was cancelled, without popping it; every other path that pushed pops
 * (the invulnerability frames' refusal and the end of the method). So every cancelled hit left one container on the entity for
 * good, and the add-on cancels hits (a Soul Hearts hit the pool swallows, any {@code LIVING_DAMAGE} listener that returns zero).
 *
 * <p>The fix does not depend on where NeoForge returns: the depth of the stack is remembered at the head of {@code hurt} and, at
 * every return, whatever is above it is taken off. When nothing was left behind (every path but the cancel, or a NeoForge that
 * pops on the cancel itself) nothing is taken; a nested {@code hurt} of the same entity restores its own depth only, so the
 * container of the outer call stays. Not covered: a {@code hurt} that throws (NeoForge leaves its container then too).</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageContainerMixin {
    @Shadow
    protected Stack<DamageContainer> damageContainers;

    @Inject(method = "hurt", at = @At("HEAD"))
    private void thaumictinkerer$rememberContainerDepth(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir,
            @Share("containerDepth") LocalIntRef depth) {
        depth.set(this.damageContainers.size());
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void thaumictinkerer$dropLeftContainers(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir,
            @Share("containerDepth") LocalIntRef depth) {
        while (this.damageContainers.size() > depth.get()) this.damageContainers.pop();
    }
}
