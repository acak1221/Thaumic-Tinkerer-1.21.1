/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import thaumic.tinkerer.neoforge.hooks.LastHurtAccess;

/**
 * Makes every living entity implement {@link LastHurtAccess}: reads the protected {@code lastHurt}, which NeoForge's
 * {@code LivingEntity.hurt} compares a hit with in the invulnerability frames after {@code LivingIncomingDamageEvent} (the Fabric
 * poster has the same field as a {@code @Shadow} of its own injector). A mixin rather than an access transformer: it needs no change to the
 * build or to the Minecraft jar Loom generates, and it widens nothing for other mods.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLastHurtMixin implements LastHurtAccess {
    @Shadow
    protected float lastHurt;

    @Override
    public float thaumictinkerer$getLastHurt() {
        return this.lastHurt;
    }
}
