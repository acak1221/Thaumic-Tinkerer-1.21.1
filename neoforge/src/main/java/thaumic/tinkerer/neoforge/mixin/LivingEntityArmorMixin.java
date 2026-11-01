/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import thaumic.tinkerer.common.hooks.TinkererEvents;

/**
 * Posts {@code TinkererEvents.LIVING_ARMOR_ABSORB} on NeoForge, the same wrapper as the Fabric mixin of the same name (docs/HOOKS.md
 * section 14). NeoForge's patched {@code LivingEntity.actuallyHurt} and {@code Player.actuallyHurt} still call
 * {@code getDamageAfterArmorAbsorb(source, container.getNewDamage())} and record {@code newDamage - result} as the container's
 * {@code ARMOR} reduction, so the original runs untouched here (the wear included), the listeners see what it took off and may replace
 * it, and NeoForge takes the amount this returns (for every source: one that bypasses armour is posted with nothing absorbed). The
 * test of the amount and the bit-for-bit pass-through are in {@link TinkererEvents#damageAfterArmorAbsorb} and
 * {@link TinkererEvents#postLivingArmorAbsorb}, the one rule for both loaders.
 *
 * <p><b>Why not {@code LivingIncomingDamageEvent#addReductionModifier(ARMOR, ...)}.</b> Thaumcraft's own mixin wraps the same method on
 * NeoForge too (its common mixin config is loaded there): the runic shield and the worn wards come off the blow first, and the armour
 * formula sees what is left. A reduction function runs after the whole method and gets the blow as it came to the armour step and a
 * reduction that already holds the ward's and the shield's share, so a listener that replaces the amount (the Ichorcloth) would throw that
 * share away. The wrapper of priority 900, below Thaumcraft's default, is applied first and is the inner one: the event gets the damage
 * that is left after Thaumcraft's pass, as on Fabric.</p>
 */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class LivingEntityArmorMixin {
    @WrapMethod(method = "getDamageAfterArmorAbsorb")
    private float thaumictinkerer$armorAbsorb(DamageSource source, float damage, Operation<Float> original) {
        return TinkererEvents.damageAfterArmorAbsorb((LivingEntity) (Object) this, source, damage, original.call(source, damage));
    }
}
