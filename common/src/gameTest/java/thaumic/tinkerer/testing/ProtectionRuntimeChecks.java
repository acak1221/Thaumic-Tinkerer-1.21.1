/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md): test-only code, CC BY-NC-SA 3.0 like the add-on.
 */
package thaumic.tinkerer.testing;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import thaumic.tinkerer.common.enchantment.ModEnchantments;
import thaumic.tinkerer.common.hooks.BlockProtection;

/**
 * Runtime checks of {@link BlockProtection} (docs/HOOKS.md, "Block protection") on a real server, through the loader backend
 * the loader module installed: Fabric posts Fabric API's and Architectury's break callbacks and Architectury's place callback,
 * NeoForge its {@code BlockEvent.BreakEvent} and {@code EntityPlaceEvent} (which Architectury answers). The listeners here are
 * Architectury's, so the same check runs on both loaders and on NeoForge proves that the backend's NeoForge events reach them;
 * the loader-native listeners are the Fabric-native wrapper {@code HooksGameTests.blockProtectionAsksTheLoadersOwnBreakAndPlaceEvents}
 * and its NeoForge counterpart. Every listener answers only for the position of its own check (checks of a batch run side by
 * side) and is taken away again in a {@code finally}.
 *
 * <p>Spawn protection exists only on a dedicated server ({@code DedicatedServer.isUnderSpawnProtection}; the GameTest server's is
 * vanilla's {@code false}); the test mixin {@code TinkererSpawnProtectionMixin} of each loader's harness makes
 * {@code MinecraftServer.isUnderSpawnProtection} answer true for the positions in {@link #isSpawnProtected}, so that the check
 * sees what {@code ServerLevel.mayInteract} does on a protected spawn.</p>
 */
public final class ProtectionRuntimeChecks {
    /** {@code dimension|pos} of the positions the test mixin puts under spawn protection. */
    private static final Set<String> SPAWN_PROTECTED = ConcurrentHashMap.newKeySet();

    private ProtectionRuntimeChecks() {}

    /** Asked by the harness's {@code TinkererSpawnProtectionMixin} from {@code MinecraftServer.isUnderSpawnProtection}. */
    public static boolean isSpawnProtected(ServerLevel level, BlockPos pos) {
        return !SPAWN_PROTECTED.isEmpty() && SPAWN_PROTECTED.contains(key(level, pos));
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    /** A floor of stone at relative y = 0, so that the mock player stands. */
    private static void floor(GameTestHelper test) {
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) test.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    /** A survival mock player on the floor with an empty inventory (the helper's mock is flagged creative). */
    @SuppressWarnings("removal") // the only mock that is a ServerPlayer (the game mode needs one)
    static ServerPlayer player(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = test.absoluteVec(new Vec3(6.5, 1, 6.5));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.getInventory().clearContent();
        return player;
    }

    /**
     * Nobody refuses: {@code mayBreak} and {@code mayPlace} are true. An Architectury {@code BlockEvent.BREAK} listener that
     * cancels the break of a position makes {@code mayBreak} false there, and one of {@code BlockEvent.PLACE} that cancels a
     * placement makes {@code mayPlace} false; while they are asked {@link BlockProtection#isProbing()} is true, they get the
     * block that is there (break) or the state to be placed (place) and the player, and nothing is broken or placed. Another
     * position is still allowed, and once the listeners are gone the refused ones are too.
     */
    public static void mayBreakAndMayPlaceFollowTheListeners(GameTestHelper test) {
        floor(test);
        ServerLevel level = test.getLevel();
        BlockPos dirtRel = new BlockPos(2, 1, 2);
        BlockPos freeRel = new BlockPos(4, 1, 4);
        BlockPos otherRel = new BlockPos(2, 1, 4);
        test.setBlock(dirtRel, Blocks.DIRT);
        test.setBlock(otherRel, Blocks.DIRT);
        BlockPos dirt = test.absolutePos(dirtRel);
        BlockPos free = test.absolutePos(freeRel);
        BlockPos other = test.absolutePos(otherRel);
        BlockState dirtState = level.getBlockState(dirt);
        BlockState stone = Blocks.STONE.defaultBlockState();
        ServerPlayer player = player(test);

        test.assertTrue(BlockProtection.mayBreak(player, level, dirt, dirtState), "nobody refuses: the player may break the dirt");
        test.assertTrue(BlockProtection.mayPlace(player, level, free, stone), "nobody refuses: the player may place stone");
        test.assertFalse(BlockProtection.isProbing(), "no question is being asked outside the calls");

        List<String> seen = new ArrayList<>();
        BlockEvent.Break refuseBreak = (l, pos, state, who, xp) -> {
            if (!pos.equals(dirt)) return EventResult.pass();
            seen.add("break probing=" + BlockProtection.isProbing() + " dirt=" + (state == dirtState) + " player=" + (who == player));
            return EventResult.interruptFalse();
        };
        BlockEvent.Place refusePlace = (l, pos, state, placer) -> {
            if (!pos.equals(free)) return EventResult.pass();
            seen.add("place probing=" + BlockProtection.isProbing() + " stone=" + (state == stone) + " player=" + (placer == player));
            return EventResult.interruptFalse();
        };
        BlockEvent.BREAK.register(refuseBreak);
        BlockEvent.PLACE.register(refusePlace);
        try {
            test.assertFalse(BlockProtection.mayBreak(player, level, dirt, dirtState), "a listener that cancels the break: may not break");
            test.assertFalse(BlockProtection.mayPlace(player, level, free, stone), "a listener that cancels the placement: may not place");
            test.assertTrue(seen.equals(List.of("break probing=true dirt=true player=true", "place probing=true stone=true player=true")),
                    "each listener was asked once, while probing, with the block, the state to be placed and the player: " + seen);
            test.assertFalse(BlockProtection.isProbing(), "and the question is over afterwards");
            test.assertTrue(level.getBlockState(dirt).is(Blocks.DIRT) && level.getBlockState(free).isAir(),
                    "nothing was broken or placed by the questions");
            test.assertTrue(BlockProtection.mayBreak(player, level, other, level.getBlockState(other)),
                    "a position the listener does not refuse is still allowed");
        } finally {
            BlockEvent.BREAK.unregister(refuseBreak);
            BlockEvent.PLACE.unregister(refusePlace);
        }
        test.assertTrue(BlockProtection.mayBreak(player, level, dirt, dirtState), "without the listener the break is allowed again");
        test.assertTrue(BlockProtection.mayPlace(player, level, free, stone), "and the placement");
        player.discard();
        test.succeed();
    }

    /**
     * The game's own rules come first and are the same on both loaders: under spawn protection ({@code ServerLevel.mayInteract})
     * neither is allowed, and the listeners are not even asked; in adventure mode with an empty hand
     * ({@code Player.blockActionRestricted}) neither either. Outside the protected position the same player may.
     */
    public static void spawnProtectionAndAdventureModeRefuse(GameTestHelper test) {
        floor(test);
        ServerLevel level = test.getLevel();
        BlockPos dirtRel = new BlockPos(3, 1, 3);
        test.setBlock(dirtRel, Blocks.DIRT);
        BlockPos dirt = test.absolutePos(dirtRel);
        BlockPos above = dirt.above();
        BlockState dirtState = level.getBlockState(dirt);
        BlockState stone = Blocks.STONE.defaultBlockState();
        ServerPlayer player = player(test);

        List<BlockPos> asked = new ArrayList<>();
        BlockEvent.Break watchBreak = (l, pos, state, who, xp) -> {
            if (pos.equals(dirt)) asked.add(pos);
            return EventResult.pass();
        };
        BlockEvent.Place watchPlace = (l, pos, state, placer) -> {
            if (pos.equals(above)) asked.add(pos);
            return EventResult.pass();
        };
        BlockEvent.BREAK.register(watchBreak);
        BlockEvent.PLACE.register(watchPlace);
        String dirtKey = key(level, dirt);
        String aboveKey = key(level, above);
        try {
            test.assertTrue(BlockProtection.mayBreak(player, level, dirt, dirtState), "unprotected, survival: may break");
            test.assertTrue(BlockProtection.mayPlace(player, level, above, stone), "and may place");
            test.assertTrue(asked.size() == 2, "the listeners were asked: " + asked);

            asked.clear();
            SPAWN_PROTECTED.add(dirtKey);
            SPAWN_PROTECTED.add(aboveKey);
            try {
                test.assertFalse(level.mayInteract(player, dirt), "the test mixin protects the position like a dedicated server's spawn");
                test.assertFalse(BlockProtection.mayBreak(player, level, dirt, dirtState), "under spawn protection: may not break");
                test.assertFalse(BlockProtection.mayPlace(player, level, above, stone), "nor place");
                test.assertTrue(asked.isEmpty(), "the game's rule refused before any listener was asked: " + asked);
            } finally {
                SPAWN_PROTECTED.remove(dirtKey);
                SPAWN_PROTECTED.remove(aboveKey);
            }

            player.setGameMode(GameType.ADVENTURE);
            test.assertFalse(BlockProtection.mayBreak(player, level, dirt, dirtState), "adventure mode, empty hand: may not break");
            test.assertFalse(BlockProtection.mayPlace(player, level, above, stone), "nor place");
            player.setGameMode(GameType.SURVIVAL);
            test.assertTrue(BlockProtection.mayBreak(player, level, dirt, dirtState), "back in survival: may break again");
        } finally {
            BlockEvent.BREAK.unregister(watchBreak);
            BlockEvent.PLACE.unregister(watchPlace);
        }
        player.discard();
        test.succeed();
    }

    /**
     * The add-on's own listeners of the break event act on a real break only: while {@code mayBreak} asks, they do nothing. A
     * pickaxe of Tunnel (which remembers the direction of a break) and Disintegrate (which wears the tool by one for a block it
     * breaks) comes out of the question exactly as it went in, and the block is still there.
     */
    public static void theAddonsOwnBreakListenersDoNothingWhileAsked(GameTestHelper test) {
        floor(test);
        ServerLevel level = test.getLevel();
        BlockPos dirtRel = new BlockPos(5, 1, 2);
        test.setBlock(dirtRel, Blocks.DIRT);
        BlockPos dirt = test.absolutePos(dirtRel);
        ServerPlayer player = player(test);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        pick.enchant(enchantments.getHolderOrThrow(ModEnchantments.tunnel), 3);
        pick.enchant(enchantments.getHolderOrThrow(ModEnchantments.desintegrate), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, pick);
        ItemStack before = pick.copy();

        test.assertTrue(BlockProtection.mayBreak(player, level, dirt, level.getBlockState(dirt)), "nobody refuses the break");
        ItemStack after = player.getMainHandItem();
        test.assertTrue(ItemStack.matches(before, after), "the question left the tool alone (no direction kept, no wear): "
                + before.getComponentsPatch() + " -> " + after.getComponentsPatch());
        test.assertTrue(level.getBlockState(dirt).is(Blocks.DIRT), "and the dirt is still there");
        player.discard();
        test.succeed();
    }
}
