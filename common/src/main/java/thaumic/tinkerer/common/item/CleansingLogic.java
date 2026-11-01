/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart as a class: this is the decision part of
 * {@code ItemCleansingTalisman.onWornTick}, moved out so that it can be tested without a game). Distributed under the same
 * licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.common.item;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * Which harmful effects the Talisman of Remedium removes in one pass, and what it costs, exactly as the 1.7.10 loop of
 * {@code onWornTick} decided it. The keys are the registry keys of the effects ({@code minecraft:poison}) and
 * {@link #BURNING} for being on fire (1.7.10 used the potion ids and -1).
 *
 * <p>The rule: of the harmful effects the player has, only <em>one new</em> effect is removed per pass, and that one costs
 * durability. When an effect that the last pass removed is there again, the player is affected by it permanently (a beacon,
 * a warp effect that is applied again and again): from then on it is removed on every pass for nothing, and it stays
 * "permanent" for as long as it keeps being removed.</p>
 */
public final class CleansingLogic {
    /**
     * The key of burning (1.7.10 {@code EFFECT_BURNING} = -1): a key of the effect registry that no effect is registered under
     * ({@code thaumictinkerer:burning}), so it cannot be mistaken for one.
     */
    public static final ResourceKey<MobEffect> BURNING = ResourceKey.create(Registries.MOB_EFFECT, LibMisc.id("burning"));

    private CleansingLogic() {}

    /** What the talisman remembers of one player between two passes. */
    public static final class State {
        /** The key the last pass removed at a cost, or null (1.7.10: 0). */
        @Nullable
        public ResourceKey<MobEffect> lastRemoved;
        /** The effects that are removed for free. */
        public final Set<ResourceKey<MobEffect>> permanent = new HashSet<>();
    }

    /**
     * The result of a pass.
     *
     * @param toRemove         everything to take away now, the permanent ones included
     * @param removed          the one effect removed at a cost, or null when none was
     * @param permanentRemoved true when some permanent effect is no longer affecting the player (it was forgotten)
     */
    public record Plan(Set<ResourceKey<MobEffect>> toRemove, @Nullable ResourceKey<MobEffect> removed, boolean permanentRemoved) {}

    /**
     * One pass. The state is changed the way the original changed its maps: effects found permanent are added, permanent
     * effects that are gone are dropped and {@link State#lastRemoved} becomes the effect removed at a cost.
     *
     * @param burning whether the wearer is on fire
     * @param harmful the keys of the harmful effects of the wearer that have at least a second left, in the order the game
     *                lists them
     */
    public static Plan plan(State state, boolean burning, List<ResourceKey<MobEffect>> harmful) {
        ResourceKey<MobEffect> lastEffectRemoved = state.lastRemoved;
        Set<ResourceKey<MobEffect>> permanentEffects = state.permanent;

        // All effects we try to remove in this operation, including permanent effects.
        Set<ResourceKey<MobEffect>> effectsToRemove = new LinkedHashSet<>();
        // One new effect we remove this operation, with a durability cost.
        ResourceKey<MobEffect> effectRemoved = null;

        if (burning) {
            if (permanentEffects.contains(BURNING)) {
                // Remove permanent effects at no cost.
                effectsToRemove.add(BURNING);
            } else if (BURNING.equals(lastEffectRemoved)) {
                // We removed burning last time, player is on fire permanently.
                permanentEffects.add(BURNING);
                effectsToRemove.add(BURNING);
            } else if (effectRemoved == null) {
                // Actually remove burning.
                effectsToRemove.add(BURNING);
                effectRemoved = BURNING;
            }
        }

        for (ResourceKey<MobEffect> id : harmful) {
            if (permanentEffects.contains(id)) {
                // Remove permanent effects at no cost.
                effectsToRemove.add(id);
            } else if (Objects.equals(lastEffectRemoved, id)) {
                // We removed this effect last time, player is affected by this permanently.
                effectsToRemove.add(id);
                permanentEffects.add(id);
            } else if (effectRemoved == null) {
                // Actually remove the effect.
                effectsToRemove.add(id);
                effectRemoved = id;
            }
        }

        // True if some permanent effects are no longer affecting the player.
        boolean permanentRemoved = permanentEffects.retainAll(effectsToRemove);
        state.lastRemoved = effectRemoved;
        return new Plan(effectsToRemove, effectRemoved, permanentRemoved);
    }
}
