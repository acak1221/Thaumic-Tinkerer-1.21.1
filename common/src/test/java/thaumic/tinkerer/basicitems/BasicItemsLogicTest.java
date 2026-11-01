/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: tests). Distributed under the same licence as the
 * add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.basicitems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumic.tinkerer.common.core.helper.MiscHelper;
import thaumic.tinkerer.common.hooks.PlayerData;
import thaumic.tinkerer.common.item.BasicItemComponents.ShareData;
import thaumic.tinkerer.common.item.CleansingLogic;
import thaumic.tinkerer.common.item.ItemCleansingTalisman;
import thaumic.tinkerer.common.item.ItemInfusedInkwell;
import thaumic.tinkerer.common.item.ItemRevealingHelm;
import thaumic.tinkerer.common.item.ItemSpellCloth;
import thaumic.tinkerer.common.item.ItemXPTalisman;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * Test-only code of the port (no 1.7.10 counterpart), CC BY-NC-SA 3.0 like the add-on.
 *
 * <p>The plain rules of the BasicItems group: the numbers of the items, the experience curve of the experience helper, the
 * decisions of the Talisman of Remedium, the codec of the tome's data and the small helpers that other groups use. Nothing
 * here needs a world or a registered item.</p>
 */
class BasicItemsLogicTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- the numbers of the original ------------------------------------------------------------------------------

    @Test
    void theNumbersAreThoseOfTheOriginalLibFeatures() {
        assertEquals(35, ItemSpellCloth.USES, "LibFeatures.SPELL_CLOTH_USES");
        assertEquals(100, ItemCleansingTalisman.USES, "LibFeatures.CLEANSING_TALISMAN_USES");
        assertEquals(101, ItemCleansingTalisman.MAX_DAMAGE, "1.7.10 broke the item above 100: 101 points of wear");
        assertEquals(3, ItemXPTalisman.RANGE, "LibFeatures.XP_TALISMAN_RANGE");
        assertEquals(1500, ItemXPTalisman.MAX_XP, "LibFeatures.XP_TALISMAN_MAX_XP");
        assertEquals(10, ItemXPTalisman.BOTTLE_COST, "LibFeatures.XP_TALISMAN_ENCHANTING_BOTTLE_COST");
        assertEquals(800, ItemInfusedInkwell.INK, "setMaxDamage(800)");
        assertEquals(500, ItemRevealingHelm.DURABILITY, "setMaxDamage(500)");
        assertEquals(5, ItemRevealingHelm.VIS_DISCOUNT, "getVisDiscount");
    }

    @Test
    void theFiveWarpEffectsThatWearTheTalismanTenTimesAsMuch() {
        assertEquals(Set.of(effect("thaumcraft:blurred_vision"), effect("thaumcraft:death_gaze"), effect("thaumcraft:infectious_vis_exhaust"),
                effect("thaumcraft:sun_scorned"), effect("thaumcraft:unnatural_hunger")), ItemCleansingTalisman.WARP_EFFECTS);
    }

    // ---- the Talisman of Withhold ---------------------------------------------------------------------------------

    @Test
    void aMergedOrbIsCountedOrbByOrbWithTheArithmeticOfTheOriginal() {
        ItemXPTalisman.Pass all = new ItemXPTalisman.Pass(1500);
        assertEquals(4, all.offer(7, 4), "four orbs of 7");
        assertEquals(28, all.xpToAdd, "4 x 7 = 28 points, not 7");
        assertFalse(all.full);

        // 50 points of room: 10 (room 40), 20 (room 20), the third does not fit in 20 and the room is spent: two of twenty
        ItemXPTalisman.Pass part = new ItemXPTalisman.Pass(50);
        assertEquals(2, part.offer(10, 20), "two of the twenty orbs");
        assertEquals(20, part.xpToAdd);
        assertTrue(part.full, "and the pass ends (the quirk subtracts the whole total)");

        ItemXPTalisman.Pass none = new ItemXPTalisman.Pass(5);
        assertEquals(0, none.offer(10, 3), "an orb of 10 does not fit in 5");
        assertEquals(0, none.xpToAdd);
        assertFalse(none.full, "the pass goes on to the next entity");
        assertEquals(1, none.offer(5, 1), "a smaller one does");
        assertTrue(none.full);
    }

    @Test
    void aMergedOrbGivesTheSameResultAsTheSeparateOrbsOf1710() {
        java.util.Random random = new java.util.Random(1710);
        for (int round = 0; round < 2000; round++) {
            int room = 1 + random.nextInt(ItemXPTalisman.MAX_XP);
            int entities = 1 + random.nextInt(6);
            int[] values = new int[entities];
            int[] counts = new int[entities];
            for (int i = 0; i < entities; i++) {
                values[i] = 1 + random.nextInt(40);
                counts[i] = 1 + random.nextInt(60);
            }

            // the 1.7.10 loop over one entity per orb, in the same order
            int xpToAdd = 0;
            int maxXP = room;
            int reference = 0;
            loop:
            for (int i = 0; i < entities; i++) {
                for (int unit = 0; unit < counts[i]; unit++) {
                    if (xpToAdd + values[i] <= maxXP) {
                        xpToAdd += values[i];
                        reference++;
                    }
                    maxXP -= xpToAdd;
                    if (maxXP <= 0) break loop;
                }
            }

            ItemXPTalisman.Pass pass = new ItemXPTalisman.Pass(room);
            int taken = 0;
            for (int i = 0; i < entities; i++) {
                taken += pass.offer(values[i], counts[i]);
                if (pass.full) break;
            }
            assertEquals(xpToAdd, pass.xpToAdd, "points, round " + round);
            assertEquals(reference, taken, "orbs taken, round " + round);
            assertTrue(pass.xpToAdd <= room, "never more than the room");
        }
    }

    // ---- the Talisman of Remedium ---------------------------------------------------------------------------------

    private static ResourceKey<MobEffect> effect(String id) {
        return ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.parse(id));
    }

    /**
     * The talisman's memory is player data of the wearer's entity (docs/deviations/BasicItems.md section 3): never saved or synced, kept
     * through a respawn after death as 1.7.10's maps kept it.
     */
    @Test
    void theTalismansMemoryIsTheWearersEntitysOwnAndKeptThroughDeath() {
        PlayerData.Key<CleansingLogic.State> memory = ItemCleansingTalisman.MEMORY;
        assertEquals("thaumictinkerer:cleansing_talisman_memory", memory.id().toString());
        assertTrue(memory.copyOnDeath() && !memory.persistent() && !memory.synced(), memory.toString());
        assertNull(memory.initial().lastRemoved, "a new memory has removed nothing");
        assertNotSame(memory.initial(), memory.initial(), "each player gets a memory of its own");
        assertEquals(LibMisc.MOD_ID + ":burning", CleansingLogic.BURNING.location().toString(), "burning is the add-on's key");
    }

    @Test
    void aFirstHarmfulEffectIsRemovedAtACost() {
        CleansingLogic.State state = new CleansingLogic.State();
        CleansingLogic.Plan plan = CleansingLogic.plan(state, false, List.of(effect("minecraft:poison")));
        assertEquals(Set.of(effect("minecraft:poison")), plan.toRemove());
        assertEquals(effect("minecraft:poison"), plan.removed(), "the removal costs durability");
        assertFalse(plan.permanentRemoved());
        assertEquals(effect("minecraft:poison"), state.lastRemoved);
        assertTrue(state.permanent.isEmpty());
    }

    @Test
    void onlyOneNewEffectIsRemovedPerPassAndTheOthersWaitTheirTurn() {
        CleansingLogic.State state = new CleansingLogic.State();
        List<ResourceKey<MobEffect>> harmful = List.of(effect("minecraft:poison"), effect("minecraft:slowness"), effect("minecraft:hunger"));
        CleansingLogic.Plan first = CleansingLogic.plan(state, false, harmful);
        assertEquals(Set.of(effect("minecraft:poison")), first.toRemove(), "the first in the list goes first");
        assertEquals(effect("minecraft:poison"), first.removed());

        // The poison is gone; the next pass takes the next effect.
        CleansingLogic.Plan second = CleansingLogic.plan(state, false, List.of(effect("minecraft:slowness"), effect("minecraft:hunger")));
        assertEquals(Set.of(effect("minecraft:slowness")), second.toRemove());
        assertEquals(effect("minecraft:slowness"), second.removed());
        CleansingLogic.Plan third = CleansingLogic.plan(state, false, List.of(effect("minecraft:hunger")));
        assertEquals(effect("minecraft:hunger"), third.removed());
        CleansingLogic.Plan fourth = CleansingLogic.plan(state, false, List.of());
        assertNull(fourth.removed(), "nothing to remove costs nothing");
        assertTrue(fourth.toRemove().isEmpty());
        assertNull(state.lastRemoved);
    }

    @Test
    void anEffectThatComesBackRightAfterItsRemovalBecomesPermanentAndIsRemovedForFree() {
        CleansingLogic.State state = new CleansingLogic.State();
        CleansingLogic.plan(state, false, List.of(effect("minecraft:poison")));

        // The same effect again on the next pass: a beacon, a warp effect that is applied again and again.
        CleansingLogic.Plan again = CleansingLogic.plan(state, false, List.of(effect("minecraft:poison")));
        assertEquals(Set.of(effect("minecraft:poison")), again.toRemove(), "it is removed again");
        assertNull(again.removed(), "but for nothing");
        assertEquals(Set.of(effect("minecraft:poison")), state.permanent);
        assertFalse(again.permanentRemoved());

        CleansingLogic.Plan free = CleansingLogic.plan(state, false, List.of(effect("minecraft:poison")));
        assertNull(free.removed());
        assertEquals(Set.of(effect("minecraft:poison")), state.permanent, "it stays permanent while it keeps coming");

        // A different effect meanwhile is still removed at a cost.
        CleansingLogic.Plan other = CleansingLogic.plan(state, false, List.of(effect("minecraft:poison"), effect("minecraft:weakness")));
        assertEquals(Set.of(effect("minecraft:poison"), effect("minecraft:weakness")), other.toRemove());
        assertEquals(effect("minecraft:weakness"), other.removed());

        // The effect stops coming: it is forgotten, and the pass says so (it plays the sound).
        CleansingLogic.Plan gone = CleansingLogic.plan(state, false, List.of());
        assertTrue(gone.permanentRemoved(), "the permanent effect is no longer there");
        assertTrue(state.permanent.isEmpty());
        // When it comes again it is a fresh effect with a cost.
        CleansingLogic.Plan fresh = CleansingLogic.plan(state, false, List.of(effect("minecraft:poison")));
        assertEquals(effect("minecraft:poison"), fresh.removed());
    }

    @Test
    void burningIsTreatedLikeAnEffectAndGoesBeforeThePotions() {
        CleansingLogic.State state = new CleansingLogic.State();
        CleansingLogic.Plan plan = CleansingLogic.plan(state, true, List.of(effect("minecraft:poison")));
        assertEquals(Set.of(CleansingLogic.BURNING), plan.toRemove(), "the potion waits");
        assertEquals(CleansingLogic.BURNING, plan.removed());

        CleansingLogic.Plan next = CleansingLogic.plan(state, true, List.of(effect("minecraft:poison")));
        assertEquals(Set.of(CleansingLogic.BURNING, effect("minecraft:poison")), next.toRemove(),
                "burning is on fire permanently now, the potion takes its turn");
        assertEquals(effect("minecraft:poison"), next.removed());
        assertTrue(state.permanent.contains(CleansingLogic.BURNING));
    }

    // ---- the Tome of Knowledge Sharing -----------------------------------------------------------------------------

    private static ShareData sampleTome() {
        return new ShareData("Steve", UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                List.of("GOGGLES", "THAUMIUM", "SPELL_CLOTH"), List.of("aer", "ignis", "metallum"));
    }

    @Test
    void theTomeDataSurvivesItsCodecs() {
        ShareData data = sampleTome();

        JsonElement json = ShareData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertEquals(data, ShareData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), "JSON");

        Tag nbt = ShareData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        assertEquals(data, ShareData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow(), "NBT");
        CompoundTag tag = (CompoundTag) nbt;
        assertEquals("Steve", tag.getString("player"));
        assertEquals(3, tag.getList("research", Tag.TAG_STRING).size());
        assertEquals(3, tag.getList("aspects", Tag.TAG_STRING).size());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ShareData.STREAM_CODEC.encode(buffer, data);
        assertEquals(data, ShareData.STREAM_CODEC.decode(buffer), "network");
        assertEquals(0, buffer.readableBytes(), "the codec reads what it wrote");
    }

    @Test
    void theTomeDataIsAnImmutableSnapshot() {
        java.util.ArrayList<String> research = new java.util.ArrayList<>(List.of("A"));
        ShareData data = new ShareData("Alex", UUID.randomUUID(), research, List.of());
        research.add("B");
        assertEquals(List.of("A"), data.research(), "later changes of the list do not reach the tome");
        try {
            data.research().add("C");
            throw new AssertionError("the list of a tome cannot be changed");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    // ---- the odds and ends of MiscHelper ---------------------------------------------------------------------------


    @Test
    void multiplyingAnAspectListTruncatesEachAmountAndLeavesTheOriginalAlone() {
        AspectList list = new AspectList().add(Aspect.AIR, 10).add(Aspect.FIRE, 7);
        AspectList half = MiscHelper.multiplyAspectList(list, 0.5);
        assertEquals(5, half.getAmount(Aspect.AIR));
        assertEquals(3, half.getAmount(Aspect.FIRE), "7 / 2 truncates to 3");
        assertEquals(10, list.getAmount(Aspect.AIR), "the original is unchanged");

        AspectList same = MiscHelper.multiplyAspectList(list, 1);
        assertNotSame(list, same, "a factor of 1 is still a copy");
        assertEquals(10, same.getAmount(Aspect.AIR));
        assertEquals(7, same.getAmount(Aspect.FIRE));
    }
}
