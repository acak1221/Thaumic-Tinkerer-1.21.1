/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.hooks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import thaumic.tinkerer.common.hooks.TinkererEvents;

/**
 * Maps NeoForge's events onto {@link TinkererEvents} (docs/HOOKS.md section 9): the counterpart of the Fabric mixins of
 * {@code thaumic.tinkerer.fabric.mixin}. Every rule of an event (what a result means, the clamps, the frames test) lives in the
 * {@code TinkererEvents.post...} methods; this class only maps the fields of NeoForge's event onto them and back. All the handlers are on
 * the game bus ({@code NeoForge.EVENT_BUS}) and do not filter by side, by tick or by the kind of player, so a client copy and a fake player
 * that is never ticked get the "both sides" events like any other entity.
 *
 * <p>Two of the hooks are mixins, not events (package {@code thaumic.tinkerer.neoforge.mixin}): {@code LIVING_ARMOR_ABSORB}
 * ({@code LivingEntityArmorMixin}, which keeps the order against Thaumcraft's ward pass that a reduction function of
 * {@code LivingIncomingDamageEvent} could not keep) and {@code FloatingTicks} ({@code ServerGamePacketListenerFloatingMixin}). The
 * {@code lastHurt} that {@code LIVING_DAMAGE} needs is read through {@link LastHurtAccess} ({@code LivingEntityLastHurtMixin}).</p>
 *
 * <p>Priorities: {@code NORMAL} for every handler except {@code BREAK_SPEED}, which runs at {@code LOW}. Architectury posts its own
 * {@code EntityEvent.LIVING_HURT} from {@code LivingIncomingDamageEvent} at {@code HIGH}, so it comes before {@code LIVING_DAMAGE} as it
 * does on Fabric (where its injection is the head of {@code hurt}); a hit cancelled there is not posted here either (the handlers do not
 * receive cancelled events). {@code BREAK_SPEED} on Fabric sees the speed that {@code Player.getDestroySpeed} returned, other mods'
 * changes of that method included (Thaumcraft's hover harness among them); on NeoForge those changes are listeners of the same event
 * (Thaumcraft's hover at {@code NORMAL}), and {@code LOW} puts the add-on after them whatever the order of the mods' construction.</p>
 */
public final class NeoForgeEventPosters {
    private static boolean registered;

    private NeoForgeEventPosters() {}

    /**
     * Subscribes the handlers to the game bus, once. Called by {@code ThaumicTinkererNeoForge} with the mod bus, which none of the
     * hooks needs.
     */
    public static void register(IEventBus modBus) {
        if (registered) return;
        registered = true;
        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(EventPriority.NORMAL, false, LivingIncomingDamageEvent.class, NeoForgeEventPosters::onLivingIncomingDamage);
        game.addListener(EventPriority.LOW, false, PlayerEvent.BreakSpeed.class, NeoForgeEventPosters::onBreakSpeed);
        game.addListener(EventPriority.NORMAL, false, LivingEvent.LivingJumpEvent.class, NeoForgeEventPosters::onLivingJump);
        game.addListener(EventPriority.NORMAL, false, LivingFallEvent.class, NeoForgeEventPosters::onLivingFall);
        game.addListener(EventPriority.NORMAL, false, LivingEntityUseItemEvent.Tick.class, NeoForgeEventPosters::onUseItemTick);
        game.addListener(EventPriority.NORMAL, false, LivingDropsEvent.class, NeoForgeEventPosters::onLivingDrops);
    }

    /**
     * {@code LIVING_DAMAGE} (section 3). NeoForge posts {@code LivingIncomingDamageEvent} from {@code LivingEntity.hurt} after the four
     * early {@code return false} checks and before {@code isSleeping()}, where the Fabric injection sits: before the shield, freezing, the
     * helmet, the invulnerability frames and the armour. Its event filters nothing, so the frames test comes first, as on Fabric: a hit
     * that the frames will refuse is not posted. A result of zero cancels the event, and {@code hurt} then returns {@code false} at once;
     * any other result becomes the container's new damage, which {@code hurt} reads back right after the event.
     */
    private static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        float incoming = event.getAmount();
        if (!(incoming > 0.0F)) return; // a hit of no damage (a snowball) has nothing to change
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (TinkererEvents.framesRefuse(victim, source, incoming, ((LastHurtAccess) victim).thaumictinkerer$getLastHurt())) return;
        float result = TinkererEvents.postLivingDamage(victim, source, incoming);
        if (result <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setAmount(result);
        }
    }

    /**
     * {@code BREAK_SPEED} (section 4), both sides. NeoForge posts {@code PlayerEvent.BreakSpeed} from {@code Player.getDigSpeed(state, pos)},
     * which {@code BlockBehaviour.getDestroyProgress} calls with the block's position (the only caller in the patched game) for every player,
     * the Animation Tablet's fake player included. "Cannot dig" is a new speed of zero and not a cancel: {@code EventHooks.getBreakSpeed}
     * turns a cancel into a speed of -1, which would give a negative progress, where the event's rule is a progress of exactly zero.
     * {@code Player.getDestroySpeed(state)} asks the same event without a position ({@code getDigSpeed(state, null)}); nothing in the
     * game calls it, and the Fabric poster, which sits in {@code getDestroyProgress} only, does not post for it either, so neither
     * does this.
     */
    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        BlockPos pos = event.getPosition().orElse(null);
        if (pos == null) return;
        event.setNewSpeed(TinkererEvents.postBreakSpeed(event.getEntity(), event.getState(), pos, event.getNewSpeed()));
    }

    /**
     * {@code LIVING_JUMP} (section 5), both sides. NeoForge posts its event at the end of {@code LivingEntity.jumpFromGround}, after the
     * motion and {@code hasImpulse} (not when the jump power is zero), the Fabric {@code TAIL}. It also posts it from the patched
     * {@code Slime.jumpFromGround} and {@code MagmaCube.jumpFromGround} and from the ridden jump of {@code AbstractHorse} and {@code Camel}
     * ({@code executeRidersJump}), which the Fabric poster does not reach; the event is posted for those too.
     */
    private static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        TinkererEvents.postLivingJump(event.getEntity());
    }

    /**
     * {@code LIVING_FALL} (section 6), both sides. NeoForge posts {@code LivingFallEvent} at the head of {@code LivingEntity.causeFallDamage}
     * and goes on with the distance and the multiplier the event holds afterwards; a cancel makes the method return {@code false}.
     */
    private static void onLivingFall(LivingFallEvent event) {
        TinkererEvents.FallData fall = new TinkererEvents.FallData(event.getDistance(), event.getDamageMultiplier());
        if (TinkererEvents.postLivingFall(event.getEntity(), fall)) {
            event.setCanceled(true);
            return;
        }
        event.setDistance(fall.getDistance());
        event.setDamageMultiplier(fall.getDamageMultiplier());
    }

    /**
     * {@code USE_ITEM_TICK} (section 7), both sides. The patched {@code LivingEntity.updateUsingItem} starts with
     * {@code useItemRemaining = EventHooks.onItemUseTick(this, stack, getUseItemRemainingTicks())} (for a stack that is not empty), so the
     * event's duration is the entity's own {@code useItemRemaining} and what the handlers leave in it is written back to that field
     * before {@code onUseTick}, the decrement and the completion test.
     */
    private static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        event.setDuration(TinkererEvents.postUseItemTick(event.getEntity(), event.getItem(), event.getDuration()));
    }

    /**
     * {@code LIVING_DROPS} (section 8), server. NeoForge captures the item entities of {@code LivingEntity.dropAllDeathLoot} itself
     * ({@code captureDrops}: the loot table, the custom loot, the equipment, a player's inventory through {@code ServerPlayer.drop}, and the
     * fox's mouth item, which its patched {@code Fox} drops in {@code dropEquipment}), ends the capture, posts {@code LivingDropsEvent} and
     * adds what is left in the collection to the level unless the event is cancelled. The listeners get a copy as a list; what they leave in
     * it is written back when it differs. With nobody listening nothing is touched.
     */
    private static void onLivingDrops(LivingDropsEvent event) {
        if (!TinkererEvents.hasLivingDropsListeners()) return;
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        DamageSource source = event.getSource();
        Collection<ItemEntity> original = event.getDrops();
        List<ItemEntity> drops = new ArrayList<>(original);
        if (TinkererEvents.postLivingDrops(victim, source, drops, TinkererEvents.lootingLevel(level, source), event.isRecentlyHit())) {
            event.setCanceled(true);
            return;
        }
        if (!sameEntities(original, drops)) {
            original.clear();
            original.addAll(drops);
        }
    }

    /** True when both hold the same entities in the same order (by identity). */
    private static boolean sameEntities(Collection<ItemEntity> original, List<ItemEntity> drops) {
        if (original.size() != drops.size()) return false;
        Iterator<ItemEntity> it = original.iterator();
        for (ItemEntity drop : drops) {
            if (it.next() != drop) return false;
        }
        return true;
    }
}
