/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.hooks;

/**
 * Read access to the protected {@code LivingEntity.lastHurt} (what the last hit that was dealt counted for, the bar of the invulnerability
 * frames), which the {@code LIVING_DAMAGE} poster hands to {@code TinkererEvents.framesRefuse} before it posts (docs/HOOKS.md section 3).
 * The mixin {@code LivingEntityLastHurtMixin} makes every living entity implement it; it lives outside the mixin package because the
 * classes of a mixin package cannot be referenced. Do not implement it elsewhere.
 */
public interface LastHurtAccess {
    float thaumictinkerer$getLastHurt();
}
