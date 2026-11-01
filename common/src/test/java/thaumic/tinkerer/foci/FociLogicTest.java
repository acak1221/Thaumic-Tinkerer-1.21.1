package thaumic.tinkerer.foci;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.core.helper.ProjectileHelper;
import thaumic.tinkerer.common.hooks.PlayerData;
import thaumic.tinkerer.common.item.foci.FociComponents.DislocationData;
import thaumic.tinkerer.common.item.foci.FociHooks;
import thaumic.tinkerer.common.item.foci.ItemFocusDeflect;
import thaumic.tinkerer.common.item.foci.ItemFocusDislocation;
import thaumic.tinkerer.common.item.foci.ItemFocusEnderChest;
import thaumic.tinkerer.common.item.foci.ItemFocusFlight;
import thaumic.tinkerer.common.item.foci.ItemFocusHeal;
import thaumic.tinkerer.common.item.foci.ItemFocusSmelt;
import thaumic.tinkerer.common.item.foci.ItemFocusTelekinesis;

/**
 * Test-only code of the port (no 1.7.10 counterpart), CC BY-NC-SA 3.0 like the add-on.
 *
 * <p>The plain rules of the seven ordinary foci, each checked against the 1.7.10 source: the vis costs (centivis), whether a
 * focus is held, the colours, the upgrades by rank, the sorting prefixes, the formulas of the healing, melting and flying
 * speeds, the box and the shift of the Distortion shield, the pull of the Telekinesis, the cost of taking a block, the
 * blacklist of the config and the codecs of the Dislocation's load. Nothing here needs a world.</p>
 */
class FociLogicTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static void assertCost(AspectList cost, Object... aspectsAndAmounts) {
        AspectList expected = new AspectList();
        for (int i = 0; i < aspectsAndAmounts.length; i += 2) {
            expected.add((Aspect) aspectsAndAmounts[i], (Integer) aspectsAndAmounts[i + 1]);
        }
        assertEquals(expected.size(), cost.size(), "number of aspects in " + cost.aspects);
        for (Aspect aspect : expected.getAspects()) {
            assertEquals(expected.getAmount(aspect), cost.getAmount(aspect), "amount of " + aspect.getTag());
        }
    }

    // ---- costs, flags, colours ------------------------------------------------------------------------------------

    @Test
    void everyFocusCostsWhatTheOriginalSaid() {
        assertCost(ItemFocusFlight.VIS_USAGE, Aspect.AIR, 15);
        assertCost(ItemFocusDeflect.VIS_USAGE, Aspect.ORDER, 8, Aspect.AIR, 4);
        assertCost(ItemFocusHeal.VIS_USAGE, Aspect.EARTH, 45, Aspect.WATER, 45);
        assertCost(ItemFocusSmelt.VIS_USAGE, Aspect.FIRE, 45, Aspect.ENTROPY, 12);
        assertCost(ItemFocusTelekinesis.VIS_USAGE, Aspect.AIR, 5, Aspect.ENTROPY, 5);
        assertCost(ItemFocusEnderChest.VIS_USAGE, Aspect.ENTROPY, 100, Aspect.ORDER, 100);
        assertCost(ItemFocusDislocation.VIS_USAGE, Aspect.ENTROPY, 500, Aspect.ORDER, 500, Aspect.EARTH, 100);
        assertCost(ItemFocusDislocation.VIS_USAGE_TILE, Aspect.ENTROPY, 2500, Aspect.ORDER, 2500, Aspect.EARTH, 500);
        assertCost(ItemFocusDislocation.VIS_USAGE_SPAWNER, Aspect.ENTROPY, 10000, Aspect.ORDER, 10000, Aspect.EARTH, 5000);
    }

    // ---- the formulas ----------------------------------------------------------------------------------------------

    @Test
    void healingTakesThirtyTicksLessATenthOfThirtyPerPotency() {
        // 30 - potency * 10 / 3 with integer division.
        int[] expected = { 30, 27, 24, 20, 17, 14, 10 };
        for (int potency = 0; potency < expected.length; potency++) {
            assertEquals(expected[potency], ItemFocusHeal.requiredProgress(potency), "potency " + potency);
        }
        assertEquals(1F, ItemFocusHeal.healAmount(0));
        assertEquals(3F, ItemFocusHeal.healAmount(2));
    }

    @Test
    void meltingTakesTwentyTicksAndPotencyCutsFiveEachUpToThree() {
        assertEquals(20, ItemFocusSmelt.startProgress(0));
        assertEquals(15, ItemFocusSmelt.startProgress(1));
        assertEquals(10, ItemFocusSmelt.startProgress(2));
        assertEquals(5, ItemFocusSmelt.startProgress(3));
        assertEquals(5, ItemFocusSmelt.startProgress(4), "potency counts up to three");
        assertEquals(5, ItemFocusSmelt.startProgress(9));
    }

    @Test
    void theLaunchOfTheFlightFocusIsTwoThirdsOfABlockPerTickPlusAFifthPerPotency() {
        assertEquals(2.0 / 3.0, ItemFocusFlight.force(0), 1.0E-12);
        assertEquals(2.0 / 3.0 * 1.2, ItemFocusFlight.force(1), 1.0E-12);
        assertEquals(2.0 / 3.0 * 2.0, ItemFocusFlight.force(5), 1.0E-12);
    }

    @Test
    void theTelekinesisPullsFromASixBlockBoxAroundAPointFiveAhead() {
        assertEquals(6, ItemFocusTelekinesis.RANGE);
        assertEquals(5.0, ItemFocusTelekinesis.DISTANCE);
        assertEquals(0.3333F, ItemFocusTelekinesis.PULL_SPEED);
        AABB box = ItemFocusTelekinesis.pullBox(new Vec3(10, 64, -3));
        assertEquals(new AABB(4, 58, -9, 16, 70, 3), box);
    }

    @Test
    void theShieldBoxIsFourBelowAndThreeAboveAndGrowsWithEnlarge() {
        AABB base = ProjectileHelper.shieldBox(new Vec3(0, 64, 0), 0);
        assertEquals(new AABB(-4, 60, -4, 3, 67, 3), base);
        AABB enlarged = ProjectileHelper.shieldBox(new Vec3(0, 64, 0), 2);
        assertEquals(new AABB(-6, 58, -6, 5, 69, 5), enlarged);
    }

    @Test
    void theShieldCarriesAProjectileByTwiceItsDistanceAlongItsFlight() {
        Vec3 shift = ProjectileHelper.shieldShift(new Vec3(0.0, 0.0, -3.0), 4.0);
        assertEquals(0.0, shift.x, 1.0E-12);
        assertEquals(0.0, shift.y, 1.0E-12);
        assertEquals(-8.0, shift.z, 1.0E-12);
        Vec3 diagonal = ProjectileHelper.shieldShift(new Vec3(1.0, 1.0, 0.0), 1.0);
        assertEquals(2.0, diagonal.length(), 1.0E-12, "twice the distance, whatever the speed");
        assertEquals(diagonal.x, diagonal.y, 1.0E-12, "along the flight");
        assertEquals(Vec3.ZERO, ProjectileHelper.shieldShift(Vec3.ZERO, 5.0), "a projectile at rest does not move");
    }

    /** 1.7.10 {@code setupBlackList} put the experience bottle on the blacklist; the blacklist is the tag, which lists it and nothing else. */
    @Test
    void theExperienceBottleIsTheOnlyEntryOfTheBlacklistToStartWith() throws java.io.IOException {
        assertEquals("thaumictinkerer:deflect_blacklist", ProjectileHelper.DEFLECT_BLACKLIST_TAG.location().toString());
        try (var in = FociLogicTest.class.getResourceAsStream("/data/thaumictinkerer/tags/entity_type/deflect_blacklist.json")) {
            var tag = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertFalse(tag.get("replace").getAsBoolean(), "a pack adds to it");
            assertEquals(List.of("minecraft:experience_bottle"), tag.getAsJsonArray("values").asList().stream()
                    .map(com.google.gson.JsonElement::getAsString).toList());
        }
    }

    // ---- the Dislocation -------------------------------------------------------------------------------------------

    @Test
    void takingABlockCostsFivePerditioFiveOrdoAndOneTerraAndMoreWithABlockEntity() {
        assertCost(ItemFocusDislocation.getCost(null), Aspect.ENTROPY, 500, Aspect.ORDER, 500, Aspect.EARTH, 100);
        ChestBlockEntity chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        assertCost(ItemFocusDislocation.getCost(chest), Aspect.ENTROPY, 2500, Aspect.ORDER, 2500, Aspect.EARTH, 500);
        SpawnerBlockEntity spawner = new SpawnerBlockEntity(BlockPos.ZERO, Blocks.SPAWNER.defaultBlockState());
        assertTrue(ItemFocusDislocation.isSpawner(spawner));
        assertFalse(ItemFocusDislocation.isSpawner(chest));
        assertCost(ItemFocusDislocation.getCost(spawner), Aspect.ENTROPY, 10000, Aspect.ORDER, 10000, Aspect.EARTH, 5000);
    }

    @Test
    void theBlacklistOfTheConfigNamesBlocksAndTags() {
        Predicate<ResourceLocation> noTag = tag -> false;
        ResourceLocation stone = ResourceLocation.withDefaultNamespace("stone");
        ResourceLocation glass = ResourceLocation.withDefaultNamespace("glass");
        assertTrue(ItemFocusDislocation.isBlockAllowed(new String[0], stone, noTag));
        assertFalse(ItemFocusDislocation.isBlockAllowed(new String[] { "minecraft:stone" }, stone, noTag));
        assertTrue(ItemFocusDislocation.isBlockAllowed(new String[] { "minecraft:stone" }, glass, noTag));
        assertFalse(ItemFocusDislocation.isBlockAllowed(new String[] { "glass" }, glass, noTag),
                "a name without a namespace is a minecraft block, as 1.7.10 named them");
        assertFalse(ItemFocusDislocation.isBlockAllowed(new String[] { " minecraft:stone " }, stone, noTag), "spaces are trimmed");
        assertTrue(ItemFocusDislocation.isBlockAllowed(new String[] { "mod:stone" }, stone, noTag));
        assertTrue(ItemFocusDislocation.isBlockAllowed(new String[] { null, "", "not a name!" }, stone, noTag),
                "garbage entries blacklist nothing");
        assertFalse(ItemFocusDislocation.isBlockAllowed(new String[] { "#c:glass_blocks" }, glass,
                tag -> tag.equals(ResourceLocation.fromNamespaceAndPath("c", "glass_blocks"))));
        assertTrue(ItemFocusDislocation.isBlockAllowed(new String[] { "#c:glass_blocks" }, stone,
                tag -> tag.equals(ResourceLocation.fromNamespaceAndPath("c", "other"))));
    }

    @Test
    void onlyBlocksThatCanBeBrokenAndAreNotPistonsOrAirCanBeCarried() {
        BlockPos pos = BlockPos.ZERO;
        assertTrue(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.STONE.defaultBlockState()));
        assertTrue(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.CHEST.defaultBlockState()));
        assertTrue(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.SPAWNER.defaultBlockState()), "a spawner can");
        assertFalse(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.AIR.defaultBlockState()), "air");
        assertFalse(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.BEDROCK.defaultBlockState()),
                "hardness -1");
        assertFalse(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.PISTON_HEAD.defaultBlockState()));
        assertFalse(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.MOVING_PISTON.defaultBlockState()));
        assertFalse(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.COMMAND_BLOCK.defaultBlockState()),
                "hardness -1");
    }

    /**
     * What no cast of the focus can have put into it is an impossible load: a load of that kind (a creative player's
     * component) is dropped instead of being placed. A block on a blacklist is not impossible: it may have been taken before
     * the list named it, and stays in the focus.
     */
    @Test
    void anImpossibleLoadIsWhatNoCastCouldHaveTaken() {
        BlockPos pos = BlockPos.ZERO;
        for (BlockState impossible : List.of(Blocks.AIR.defaultBlockState(), Blocks.BEDROCK.defaultBlockState(),
                Blocks.BARRIER.defaultBlockState(), Blocks.COMMAND_BLOCK.defaultBlockState(),
                Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState(), Blocks.STRUCTURE_BLOCK.defaultBlockState(),
                Blocks.JIGSAW.defaultBlockState(), Blocks.END_PORTAL_FRAME.defaultBlockState(), Blocks.PISTON_HEAD.defaultBlockState(),
                Blocks.MOVING_PISTON.defaultBlockState(), Blocks.ROSE_BUSH.defaultBlockState(),
                Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3))) {
            assertTrue(ItemFocusDislocation.isImpossibleLoad(EmptyBlockGetter.INSTANCE, pos, impossible), impossible + " is impossible");
        }
        for (BlockState possible : List.of(Blocks.STONE.defaultBlockState(), Blocks.SPAWNER.defaultBlockState(),
                Blocks.CHEST.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState())) {
            assertFalse(ItemFocusDislocation.isImpossibleLoad(EmptyBlockGetter.INSTANCE, pos, possible), possible + " is possible");
        }
        String[] blacklist = ConfigHandler.blockBlacklist;
        try {
            ConfigHandler.blockBlacklist = new String[] { "minecraft:glass" };
            assertTrue(ItemFocusDislocation.isBlacklisted(Blocks.GLASS.defaultBlockState()), "on the config's list");
            assertFalse(ItemFocusDislocation.isImpossibleLoad(EmptyBlockGetter.INSTANCE, pos, Blocks.GLASS.defaultBlockState()),
                    "but not impossible: it may have been taken before the list named it");
            assertFalse(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.GLASS.defaultBlockState()));
            assertFalse(ItemFocusDislocation.isBlacklisted(Blocks.STONE.defaultBlockState()));
        } finally {
            ConfigHandler.blockBlacklist = blacklist;
        }
    }

    @Test
    void theHalvesOfTallPlantsDoorsAndBedsAndFlowingWaterCannotBeTaken() {
        BlockPos pos = BlockPos.ZERO;
        BlockState upperRose = Blocks.ROSE_BUSH.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        assertTrue(ItemFocusDislocation.isTwoPart(Blocks.ROSE_BUSH.defaultBlockState()), "lower half of a rose bush");
        assertTrue(ItemFocusDislocation.isTwoPart(upperRose), "upper half of a rose bush");
        assertTrue(ItemFocusDislocation.isTwoPart(Blocks.OAK_DOOR.defaultBlockState()), "a door");
        assertTrue(ItemFocusDislocation.isTwoPart(Blocks.RED_BED.defaultBlockState()), "a bed");
        assertFalse(ItemFocusDislocation.isTwoPart(Blocks.CHEST.defaultBlockState()), "a chest is one block");
        assertFalse(ItemFocusDislocation.isTwoPart(Blocks.OAK_STAIRS.defaultBlockState()), "stairs are one block");
        for (BlockState refused : List.of(Blocks.ROSE_BUSH.defaultBlockState(), upperRose, Blocks.TALL_GRASS.defaultBlockState(),
                Blocks.TALL_SEAGRASS.defaultBlockState(), Blocks.OAK_DOOR.defaultBlockState(), Blocks.RED_BED.defaultBlockState(),
                Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3), Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 5))) {
            assertFalse(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, refused), refused + " cannot be taken");
        }
        assertTrue(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.WATER.defaultBlockState()), "a source of water can");
        assertTrue(ItemFocusDislocation.canCarry(EmptyBlockGetter.INSTANCE, pos, Blocks.LAVA.defaultBlockState()), "a source of lava can");
    }

    @Test
    void theFlightKickResetIsWhatIsInstalledAndNullRemovesIt() {
        java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> original = FociHooks.floatingTickReset;
        assertNull(original, "nothing is installed until the group's start-up (Foci.register) installs the loader layer's reset");
        try {
            FociHooks.setFloatingTickReset(null);
            assertNull(FociHooks.floatingTickReset);
            FociHooks.resetFloatingTicks(null); // with none installed a cast resets nothing, and does not throw

            java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> mine = player -> {};
            FociHooks.setFloatingTickReset(mine);
            assertSame(mine, FociHooks.floatingTickReset);
            FociHooks.setFloatingTickReset(null);
            assertNull(FociHooks.floatingTickReset, "null removes it, as the javadoc says");
        } finally {
            FociHooks.setFloatingTickReset(original);
        }
    }

    @Test
    void halfOfADoubleChestIsASingleChestInTheFocus() {
        BlockState left = Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.CHEST_TYPE, ChestType.LEFT);
        assertEquals(ChestType.SINGLE, ItemFocusDislocation.normalize(left).getValue(BlockStateProperties.CHEST_TYPE));
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertEquals(stone, ItemFocusDislocation.normalize(stone));
    }

    private static DislocationData sampleLoad() {
        CompoundTag tile = new CompoundTag();
        tile.putString("id", "minecraft:chest");
        tile.putInt("x", 3);
        ListTag items = new ListTag();
        CompoundTag slot = new CompoundTag();
        slot.putByte("Slot", (byte) 4);
        slot.putString("id", "minecraft:diamond");
        items.add(slot);
        tile.put("Items", items);
        BlockState state = Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                net.minecraft.core.Direction.EAST);
        return new DislocationData(state, tile, "0123456789abcdef");
    }

    @Test
    void theLoadOfTheFocusSurvivesTheDiskAndTheWire() {
        DislocationData load = sampleLoad();
        // A world saves a component as NBT, which keeps the types of the numbers (JSON would not).
        var saved = DislocationData.CODEC.encodeStart(NbtOps.INSTANCE, load).getOrThrow();
        assertEquals(load, DislocationData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow());

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        DislocationData.STREAM_CODEC.encode(buf, load);
        assertEquals(load, DislocationData.STREAM_CODEC.decode(buf));
    }

    @Test
    void aLoadWithoutABlockEntityHasNoTileAndCanBeReadWithoutTheField() {
        DislocationData plain = new DislocationData(Blocks.DIRT.defaultBlockState(), new CompoundTag());
        assertFalse(plain.hasTile());
        var json = DislocationData.CODEC.encodeStart(JsonOps.INSTANCE, plain).getOrThrow();
        assertEquals(plain, DislocationData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        // A load saved without the optional field reads as a load without a block entity.
        var stateOnly = DislocationData.CODEC.encodeStart(JsonOps.INSTANCE, plain).getOrThrow().getAsJsonObject();
        stateOnly.remove("tile");
        stateOnly.remove("seal");
        assertEquals(plain, DislocationData.CODEC.parse(JsonOps.INSTANCE, stateOnly).getOrThrow());
        assertEquals("", DislocationData.CODEC.parse(JsonOps.INSTANCE, stateOnly).getOrThrow().seal(),
                "a load saved before the seal existed (or written by hand) has none");
        assertTrue(sampleLoad().hasTile());
    }

    @Test
    void theTagOfALoadIsNeverSharedWithTheCaller() {
        CompoundTag original = new CompoundTag();
        original.putInt("a", 1);
        DislocationData load = new DislocationData(Blocks.STONE.defaultBlockState(), original);
        original.putInt("a", 2);
        assertEquals(1, load.tile().getInt("a"), "the record copied the tag it was given");
        load.tile().putInt("a", 3);
        assertEquals(1, load.tile().getInt("a"), "the tag the record hands out is a copy");
        assertNotEquals(original, load.tile());
    }

    @Test
    void aLoadStaysBelowWhatAClientReads() {
        assertTrue(ItemFocusDislocation.MAX_TILE_BYTES > 0 && ItemFocusDislocation.MAX_TILE_BYTES < 2_097_152,
                "below the two megabytes a client reads");
        CompoundTag big = new CompoundTag();
        big.putByteArray("data", new byte[ItemFocusDislocation.MAX_TILE_BYTES + 1]);
        assertTrue(big.sizeInBytes() > ItemFocusDislocation.MAX_TILE_BYTES, "the size of a tag is what the check measures");
    }

    /**
     * The progress of Mending and Efreet's Flame is player data of each side's player entity (docs/deviations/Foci.md sections 3 and 4):
     * never saved or synced, kept through a respawn after death as 1.7.10's maps by name kept it; none at first.
     */
    @Test
    void theFocusProgressIsTheEntitysOwnAndKeptThroughDeath() {
        PlayerData.Key<Integer> heal = ItemFocusHeal.PROGRESS;
        assertEquals("thaumictinkerer:focus_heal_progress", heal.id().toString());
        assertEquals(0, heal.initial());
        assertTrue(heal.copyOnDeath() && !heal.persistent() && !heal.synced(), heal.toString());
        PlayerData.Key<ItemFocusSmelt.SmeltData> smelt = ItemFocusSmelt.SMELTING;
        assertEquals("thaumictinkerer:focus_smelt_progress", smelt.id().toString());
        assertTrue(smelt.copyOnDeath() && !smelt.persistent() && !smelt.synced(), smelt.toString());
        assertEquals(0, smelt.initial().progress, "the initial value has no ticks to go: it smelts nothing");
    }
}
