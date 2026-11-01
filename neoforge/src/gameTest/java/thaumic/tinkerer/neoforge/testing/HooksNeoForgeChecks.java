/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the NeoForge GameTest harness, test-only. Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import thaumic.tinkerer.common.hooks.BlockProtection;

/**
 * The NeoForge counterparts of the Fabric-native wrappers of {@code HooksGameTests}: the Fabric body asks Fabric API's own break
 * callback, this one NeoForge's own events, and checks the same things. tools/neoforge_gametests.py makes the generated
 * {@link HooksGameTests} call them under the Fabric names.
 */
public final class HooksNeoForgeChecks {
    private HooksNeoForgeChecks() {}

    /**
     * {@code BlockProtection} asks NeoForge's own events (what claim mods written for NeoForge listen to): a {@code BreakEvent}
     * listener that cancels a position makes {@code mayBreak} false there, an {@code EntityPlaceEvent} listener that cancels makes
     * {@code mayPlace} false, while probing; the place event's placed block is the state to be placed (not the air that is there),
     * its player the player; nothing is broken or placed, and other positions stay allowed.
     */
    public static void blockProtectionAsksTheLoadersOwnBreakAndPlaceEvents(GameTestHelper h) {
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerLevel level = h.getLevel();
        BlockPos refusedRel = new BlockPos(2, 1, 2);
        BlockPos otherRel = new BlockPos(4, 1, 2);
        BlockPos freeRel = new BlockPos(2, 1, 4);
        h.setBlock(refusedRel, Blocks.DIRT);
        h.setBlock(otherRel, Blocks.DIRT);
        BlockPos refused = h.absolutePos(refusedRel);
        BlockPos other = h.absolutePos(otherRel);
        BlockPos free = h.absolutePos(freeRel);
        BlockState dirt = level.getBlockState(refused);
        BlockState stone = Blocks.STONE.defaultBlockState();
        ServerPlayer player = survivalPlayer(h);

        List<String> seen = new ArrayList<>();
        Consumer<BlockEvent.BreakEvent> refuseBreak = event -> {
            if (!event.getPos().equals(refused)) return;
            seen.add("break probing=" + BlockProtection.isProbing() + " player=" + (event.getPlayer() == player));
            event.setCanceled(true);
        };
        Consumer<BlockEvent.EntityPlaceEvent> refusePlace = event -> {
            if (!event.getPos().equals(free)) return;
            seen.add("place probing=" + BlockProtection.isProbing() + " stone=" + (event.getPlacedBlock() == stone)
                    + " player=" + (event.getEntity() == player));
            event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.BreakEvent.class, refuseBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.EntityPlaceEvent.class, refusePlace);
        try {
            h.assertFalse(BlockProtection.mayBreak(player, level, refused, dirt), "a BreakEvent listener cancels: may not break");
            h.assertFalse(BlockProtection.mayPlace(player, level, free, stone), "an EntityPlaceEvent listener cancels: may not place");
            h.assertTrue(seen.equals(List.of("break probing=true player=true", "place probing=true stone=true player=true")),
                    "each was asked once, while probing, with the player and the state to be placed: " + seen);
            h.assertTrue(level.getBlockState(refused).is(Blocks.DIRT) && level.getBlockState(free).isAir(), "nothing was broken or placed");
            h.assertTrue(BlockProtection.mayBreak(player, level, other, dirt), "a position they do not refuse is allowed");
        } finally {
            NeoForge.EVENT_BUS.unregister(refuseBreak);
            NeoForge.EVENT_BUS.unregister(refusePlace);
        }
        h.assertTrue(BlockProtection.mayBreak(player, level, refused, dirt), "without the listeners the break is allowed");
        h.assertTrue(BlockProtection.mayPlace(player, level, free, stone), "and the placement");
        player.discard();
        h.succeed();
    }

    @SuppressWarnings("removal") // the only mock that is a ServerPlayer
    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(6.5, 1, 6.5));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }
}
