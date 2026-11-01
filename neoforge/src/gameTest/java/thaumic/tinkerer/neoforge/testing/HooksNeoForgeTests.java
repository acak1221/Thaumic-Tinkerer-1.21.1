/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the NeoForge GameTest harness, test-only. Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.testing;

import java.lang.reflect.Field;
import java.util.Stack;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumic.tinkerer.common.hooks.TinkererEvents;

/**
 * NeoForge-only scenarios of the loader hooks (docs/HOOKS.md): what NeoForge's damage pipeline keeps on an entity
 * ({@code DamageContainer}s), which Fabric does not have. The generator lists this class in
 * {@code TinkererGameTestCatalogue.NEOFORGE_ONLY}. Test-only, not distributed.
 */
@PrefixGameTestTemplate(true)
public final class HooksNeoForgeTests {
    /**
     * A hit that a {@code LIVING_DAMAGE} listener cancels (as the Soul Hearts do for a hit the pool swallows), and one that a NeoForge
     * listener of {@code LivingIncomingDamageEvent} cancels, leave no {@code DamageContainer} on the entity (NeoForge 21.1.259 returns
     * from {@code hurt} without popping the one it pushed; the mixin {@code LivingEntityDamageContainerMixin} pops it); a hit that is
     * dealt leaves none either.
     */
    @GameTest(template = "empty", templateNamespace = "thaumictinkerer")
    public void aCancelledHitLeavesNoDamageContainer(GameTestHelper h) {
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(3, 1, 3));
        pig.setNoAi(true);
        Stack<?> containers = containers(pig);
        h.assertTrue(containers.isEmpty(), "a new pig has no damage container: " + containers.size());
        DamageSource generic = h.getLevel().damageSources().generic();

        TinkererEvents.LivingDamage cancel = (victim, source, amount) -> victim == pig ? 0F : amount;
        TinkererEvents.LIVING_DAMAGE.register(cancel);
        try {
            for (int i = 0; i < 3; i++) h.assertFalse(pig.hurt(generic, 2F), "the listener cancels hit " + i);
        } finally {
            TinkererEvents.LIVING_DAMAGE.unregister(cancel);
        }
        h.assertTrue(containers.isEmpty(), "three hits the add-on cancelled left no container: " + containers.size());

        Consumer<LivingIncomingDamageEvent> neoCancel = event -> {
            if (event.getEntity() == pig) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, LivingIncomingDamageEvent.class, neoCancel);
        try {
            h.assertFalse(pig.hurt(generic, 2F), "a NeoForge listener cancels the hit");
        } finally {
            NeoForge.EVENT_BUS.unregister(neoCancel);
        }
        h.assertTrue(containers.isEmpty(), "a hit a NeoForge listener cancelled left none either: " + containers.size());

        float health = pig.getHealth();
        h.assertTrue(pig.hurt(generic, 1F) && pig.getHealth() < health, "a hit that is dealt hurts the pig");
        h.assertTrue(containers.isEmpty(), "and leaves no container: " + containers.size());
        pig.discard();
        h.succeed();
    }

    /** {@code LivingEntity.damageContainers} (protected, NeoForge's), read by reflection: the names are Mojang's at runtime on NeoForge. */
    private static Stack<?> containers(LivingEntity entity) {
        try {
            Field field = LivingEntity.class.getDeclaredField("damageContainers");
            field.setAccessible(true);
            return (Stack<?>) field.get(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("LivingEntity.damageContainers", e);
        }
    }
}
