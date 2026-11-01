package thaumic.tinkerer.fabric.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import thaumic.tinkerer.common.hooks.BlockProtection;
import thaumic.tinkerer.testing.HooksRuntimeChecks;
import thaumic.tinkerer.testing.ProtectionRuntimeChecks;

/**
 * Fabric wrappers of {@link HooksRuntimeChecks} and {@link ProtectionRuntimeChecks}: one per check, and the Fabric-native check of
 * {@code BlockProtection}'s Fabric backend. Test-only, not distributed.
 */
public final class HooksGameTests implements FabricGameTest {
    /** The positions the harness's {@code PlayerBlockBreakEvents.BEFORE} listener refuses, with what it saw (Fabric events cannot be unregistered). */
    private static final Map<BlockPos, List<String>> REFUSED_BREAKS = new ConcurrentHashMap<>();

    static {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            List<String> seen = REFUSED_BREAKS.get(pos);
            if (seen == null) return true;
            seen.add("probing=" + BlockProtection.isProbing() + " " + state.getBlock());
            return false;
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void livingDamageIsPostedBeforeArmourAndChainsInOrder(GameTestHelper test) {
        HooksRuntimeChecks.livingDamageIsPostedBeforeArmourAndChainsInOrder(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void livingDamageCancelIsLikeInvulnerableAndFramesFollowTheListener(GameTestHelper test) {
        HooksRuntimeChecks.livingDamageCancelIsLikeInvulnerableAndFramesFollowTheListener(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void livingDamageIsNotPostedForHitsTheInvulnerabilityFramesRefuse(GameTestHelper test) {
        HooksRuntimeChecks.livingDamageIsNotPostedForHitsTheInvulnerabilityFramesRefuse(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void livingDamageSeesAFakePlayersBlowAndAZombiesBlowOnAPlayer(GameTestHelper test) {
        HooksRuntimeChecks.livingDamageSeesAFakePlayersBlowAndAZombiesBlowOnAPlayer(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void livingDamageSeesAnArrow(GameTestHelper test) {
        HooksRuntimeChecks.livingDamageSeesAnArrow(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void livingArmorAbsorbIsPostedFromTheArmourStepAndReplacesWhatArmourTakesOff(GameTestHelper test) {
        HooksRuntimeChecks.livingArmorAbsorbIsPostedFromTheArmourStepAndReplacesWhatArmourTakesOff(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void livingArmorAbsorbHandsBackTheGamesOwnFloatAndSurvivesAnInfiniteBlow(GameTestHelper test) {
        HooksRuntimeChecks.livingArmorAbsorbHandsBackTheGamesOwnFloatAndSurvivesAnInfiniteBlow(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void breakSpeedIsPostedForAServerPlayerAndAFakePlayer(GameTestHelper test) {
        HooksRuntimeChecks.breakSpeedIsPostedForAServerPlayerAndAFakePlayer(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void breakSpeedCannotDigOnABlockOfHardnessZeroIsProgressZero(GameTestHelper test) {
        HooksRuntimeChecks.breakSpeedCannotDigOnABlockOfHardnessZeroIsProgressZero(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void livingJumpIsPostedAfterTheJumpMotionIsApplied(GameTestHelper test) {
        HooksRuntimeChecks.livingJumpIsPostedAfterTheJumpMotionIsApplied(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void livingFallIsPostedOnLandingAndCanHalveOrCancel(GameTestHelper test) {
        HooksRuntimeChecks.livingFallIsPostedOnLandingAndCanHalveOrCancel(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void useItemTickExtendsABowDraw(GameTestHelper test) {
        HooksRuntimeChecks.useItemTickExtendsABowDraw(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void livingDropsAreEditedBeforeTheyEnterTheWorld(GameTestHelper test) {
        HooksRuntimeChecks.livingDropsAreEditedBeforeTheyEnterTheWorld(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void livingDropsCoverAPlayersInventoryButNotADropByHand(GameTestHelper test) {
        HooksRuntimeChecks.livingDropsCoverAPlayersInventoryButNotADropByHand(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void floatingTicksReadAndResetTheRealCounter(GameTestHelper test) {
        HooksRuntimeChecks.floatingTicksReadAndResetTheRealCounter(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void mayBreakAndMayPlaceFollowTheListeners(GameTestHelper test) {
        ProtectionRuntimeChecks.mayBreakAndMayPlaceFollowTheListeners(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void spawnProtectionAndAdventureModeRefuse(GameTestHelper test) {
        ProtectionRuntimeChecks.spawnProtectionAndAdventureModeRefuse(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theAddonsOwnBreakListenersDoNothingWhileAsked(GameTestHelper test) {
        ProtectionRuntimeChecks.theAddonsOwnBreakListenersDoNothingWhileAsked(test);
    }

    /**
     * Fabric-native: {@code BlockProtection.mayBreak} asks Fabric API's {@code PlayerBlockBreakEvents.BEFORE} (what claim mods
     * written for Fabric listen to): a listener that refuses a position makes it false there, while probing, and nothing is
     * broken; another position stays allowed. Fabric API has no place event, so the place half is the shared check's
     * (Architectury's {@code BlockEvent.PLACE}). The NeoForge counterpart asks NeoForge's {@code BreakEvent} and {@code EntityPlaceEvent}.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void blockProtectionAsksTheLoadersOwnBreakAndPlaceEvents(GameTestHelper test) {
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) test.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        ServerLevel level = test.getLevel();
        BlockPos refusedRel = new BlockPos(2, 1, 2);
        BlockPos otherRel = new BlockPos(4, 1, 2);
        test.setBlock(refusedRel, Blocks.DIRT);
        test.setBlock(otherRel, Blocks.DIRT);
        BlockPos refused = test.absolutePos(refusedRel);
        BlockPos other = test.absolutePos(otherRel);
        BlockState dirt = level.getBlockState(refused);
        ServerPlayer player = survivalPlayer(test);

        List<String> seen = new ArrayList<>();
        REFUSED_BREAKS.put(refused, seen);
        try {
            test.assertFalse(BlockProtection.mayBreak(player, level, refused, dirt), "PlayerBlockBreakEvents.BEFORE refuses: may not break");
            test.assertTrue(seen.size() == 1 && seen.get(0).startsWith("probing=true"), "the listener was asked once, while probing: " + seen);
            test.assertTrue(level.getBlockState(refused).is(Blocks.DIRT), "and nothing was broken");
            test.assertTrue(BlockProtection.mayBreak(player, level, other, dirt), "a position it does not refuse is allowed");
        } finally {
            REFUSED_BREAKS.remove(refused);
        }
        test.assertTrue(BlockProtection.mayBreak(player, level, refused, dirt), "without the refusal the break is allowed");
        player.discard();
        test.succeed();
    }

    @SuppressWarnings("removal") // the only mock that is a ServerPlayer
    private static ServerPlayer survivalPlayer(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = test.absoluteVec(new Vec3(6.5, 1, 6.5));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        return player;
    }
}
