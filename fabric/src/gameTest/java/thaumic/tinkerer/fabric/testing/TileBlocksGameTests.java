package thaumic.tinkerer.fabric.testing;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import thaumic.tinkerer.common.ThaumicTinkerer;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.content.TileBlocks;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.fabric.TileBlocksFabric;
import thaumic.tinkerer.fabric.TileBlocksFabricEnergy;
import thaumic.tinkerer.testing.TileBlocksRuntimeChecks;

/**
 * Fabric wrappers of {@link TileBlocksRuntimeChecks}, one per check. Test-only, not distributed. The timeouts are those of
 * the slow checks: the tablet's swing takes thirty ticks, and a locomotive moves only when the game time is 1 modulo 100
 * (after the relays, which look for each other every 40 ticks, have linked it).
 */
public final class TileBlocksGameTests implements FabricGameTest {
    /** Vanilla has no fluid-holding block entity: this test gives barrels a four-bucket tank each. */
    private static final Map<BlockEntity, SingleFluidStorage> TANKS = new WeakHashMap<>();
    private static boolean tankLookupRegistered;

    private static synchronized void registerTankLookup() {
        if (tankLookupRegistered) return;
        tankLookupRegistered = true;
        FluidStorage.SIDED.registerForBlockEntity(
                (barrel, side) -> TANKS.computeIfAbsent(barrel, b -> SingleFluidStorage.withFixedCapacity(4 * FluidConstants.BUCKET, b::setChanged)),
                BlockEntityType.BARREL);
    }

    /**
     * The Transvector Interface hands the fluid storage of the block it is bound to to whoever asks it (the Fabric glue,
     * {@code TileBlocksFabric}, registered by {@code ThaumicTinkererFabric.onThaumcraftReady()}): unbound it has none; bound to a tank it fills
     * and drains that tank.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void interfaceForwardsFluids(GameTestHelper h) {
        registerTankLookup();
        BlockPos ifacePos = new BlockPos(2, 1, 2);
        BlockPos tankPos = new BlockPos(4, 1, 2);
        h.setBlock(ifacePos, TileBlocks.interfaceBlock());
        h.setBlock(tankPos, Blocks.BARREL);
        BlockEntity entity = h.getBlockEntity(ifacePos);
        h.assertTrue(entity instanceof TileTransvectorInterface, "an interface at " + ifacePos + ", found " + entity);
        TileTransvectorInterface device = (TileTransvectorInterface) entity;
        BlockPos absIface = h.absolutePos(ifacePos);
        h.assertTrue(FluidStorage.SIDED.find(h.getLevel(), absIface, Direction.NORTH) == null, "an unbound interface has no fluid storage");

        device.setTarget(h.absolutePos(tankPos));
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(h.getLevel(), absIface, Direction.NORTH);
        h.assertTrue(storage != null, "a bound interface has the storage of its tank");
        try (Transaction tx = Transaction.openOuter()) {
            long in = storage.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, tx);
            h.assertTrue(in == FluidConstants.BUCKET, "a bucket went in through the interface: " + in);
            tx.commit();
        }
        SingleFluidStorage tank = TANKS.get(h.getBlockEntity(tankPos));
        h.assertTrue(tank != null && tank.amount == FluidConstants.BUCKET && tank.variant.isOf(Fluids.WATER), "and sits in the tank");
        try (Transaction tx = Transaction.openOuter()) {
            long out = storage.extract(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET / 2, tx);
            h.assertTrue(out == FluidConstants.BUCKET / 2, "half a bucket came out through the interface: " + out);
            tx.commit();
        }
        h.assertTrue(tank.amount == FluidConstants.BUCKET / 2, "and left the tank: " + tank.amount);
        h.succeed();
    }

    /** Set by {@code -PwithoutTeamRebornEnergy} (fabric.gradle): the run has no Team Reborn Energy. */
    private static final String WITHOUT_TEAM_REBORN_ENERGY = "thaumictinkerer.withoutTeamRebornEnergy";

    /**
     * Team Reborn Energy, an optional mod, is on the GameTest run unless {@code -PwithoutTeamRebornEnergy} left it out, and the run must
     * have what it says. Either way the add-on names it as a suggestion only, never as a dependency. With the mod the Interface's energy
     * lookup ({@code TileBlocksFabricEnergy}) is registered and the energy check goes on (false). Without it, this is the check that the
     * add-on runs without it: it started and recorded no failure, and the energy glue was not tried (true; the test has succeeded).
     */
    private static boolean withoutTeamRebornEnergy(GameTestHelper h) {
        boolean expected = !Boolean.getBoolean(WITHOUT_TEAM_REBORN_ENERGY);
        boolean loaded = TileBlocksFabric.teamRebornEnergyLoaded();
        h.assertTrue(loaded == expected, "Team Reborn Energy is " + (loaded ? "" : "not ") + "installed, the run says it "
                + (expected ? "is" : "is not") + " (-D" + WITHOUT_TEAM_REBORN_ENERGY + ")");
        for (ModDependency dependency : FabricLoader.getInstance().getModContainer(LibMisc.MOD_ID).orElseThrow().getMetadata().getDependencies()) {
            if (!TileBlocksFabric.TEAM_REBORN_ENERGY.equals(dependency.getModId())) continue;
            h.assertTrue(dependency.getKind() == ModDependency.Kind.SUGGESTS, "the add-on only suggests Team Reborn Energy: " + dependency.getKind());
        }
        if (loaded) {
            h.assertTrue(TileBlocksFabricEnergy.registered(), "with Team Reborn Energy the Interface's energy lookup is registered");
            return false;
        }
        h.assertTrue(ThaumicTinkerer.failures().isEmpty(), "without Team Reborn Energy the add-on recorded no failure: " + ThaumicTinkerer.failures());
        h.succeed();
        return true;
    }

    /**
     * The Interface hands on the energy storage of the bound block, through Team Reborn Energy ({@code TileBlocksFabricEnergy}): the shared
     * check with that API ({@code TeamRebornEnergyTesting.PROBE}). Without the mod (-PwithoutTeamRebornEnergy): the add-on runs without it.
     */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void interfaceForwardsEnergy(GameTestHelper h) {
        if (withoutTeamRebornEnergy(h)) return;
        TileBlocksRuntimeChecks.interfaceForwardsEnergy(h, TeamRebornEnergyTesting.PROBE);
    }

    /** No energy through an unloaded, self-bound or looped binding, through Team Reborn Energy; without the mod, as above. */
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void interfaceMovesNoEnergyThroughABrokenBinding(GameTestHelper h) {
        if (withoutTeamRebornEnergy(h)) return;
        TileBlocksRuntimeChecks.interfaceMovesNoEnergyThroughABrokenBinding(h, TeamRebornEnergyTesting.PROBE);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void registrationIsComplete(GameTestHelper h) {
        TileBlocksRuntimeChecks.registrationIsComplete(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void locomotiveResearchShowsItsDiagram(GameTestHelper h) {
        TileBlocksRuntimeChecks.locomotiveResearchShowsItsDiagram(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void blockEntitiesPersist(GameTestHelper h) {
        TileBlocksRuntimeChecks.blockEntitiesPersist(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void tabletBreaksTheBlockInFront(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletBreaksTheBlockInFront(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 450)
    public void tabletBreaksWithoutTheRightToolDropsNothing(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletBreaksWithoutTheRightToolDropsNothing(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void tabletAttacksTheEntityInFront(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletAttacksTheEntityInFront(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void tabletPlacesBlocks(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletPlacesBlocks(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void tabletTillsWithAHoe(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletTillsWithAHoe(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void tabletUsesItemsOnEntities(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletUsesItemsOnEntities(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 250)
    public void tabletInRedstoneModeNeedsPower(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletInRedstoneModeNeedsPower(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void tabletTurnsWithAWand(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletTurnsWithAWand(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void tabletSpillsItsItem(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletSpillsItsItem(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void tabletCooldownsRunOut(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletCooldownsRunOut(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void tabletDoesNotBoardVehicles(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletDoesNotBoardVehicles(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void tabletDoesNotSleepInABed(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletDoesNotSleepInABed(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void tabletRespectsTheWorldBorder(GameTestHelper h) {
        TileBlocksRuntimeChecks.tabletRespectsTheWorldBorder(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void menusHaveTheirSlots(GameTestHelper h) {
        TileBlocksRuntimeChecks.menusHaveTheirSlots(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void menuPacketsCheckTheSender(GameTestHelper h) {
        TileBlocksRuntimeChecks.menuPacketsCheckTheSender(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void placerWandOpensTheMenuOnlyWhileSneaking(GameTestHelper h) {
        TileBlocksRuntimeChecks.placerWandOpensTheMenuOnlyWhileSneaking(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fakePlayerEarnsNoAdvancements(GameTestHelper h) {
        TileBlocksRuntimeChecks.fakePlayerEarnsNoAdvancements(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fakePlayerHasNoStatsScoresOrScreens(GameTestHelper h) {
        TileBlocksRuntimeChecks.fakePlayerHasNoStatsScoresOrScreens(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void remotePlacerPlacesItsBlock(GameTestHelper h) {
        TileBlocksRuntimeChecks.remotePlacerPlacesItsBlock(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void remotePlacerRunsOnRedstone(GameTestHelper h) {
        TileBlocksRuntimeChecks.remotePlacerRunsOnRedstone(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void remotePlacerSpillsAndRefuses(GameTestHelper h) {
        TileBlocksRuntimeChecks.remotePlacerSpillsAndRefuses(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void remotePlacerPoursABucketIntoItsTarget(GameTestHelper h) {
        TileBlocksRuntimeChecks.remotePlacerPoursABucketIntoItsTarget(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void placerAndDislocatorRespectTheWorldBorder(GameTestHelper h) {
        TileBlocksRuntimeChecks.placerAndDislocatorRespectTheWorldBorder(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dislocatorSwapsBlocksWithTheirContents(GameTestHelper h) {
        TileBlocksRuntimeChecks.dislocatorSwapsBlocksWithTheirContents(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dislocatorMovesEntities(GameTestHelper h) {
        TileBlocksRuntimeChecks.dislocatorMovesEntities(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void dislocatorRefusesWhatAPlayerCannotBreak(GameTestHelper h) {
        TileBlocksRuntimeChecks.dislocatorRefusesWhatAPlayerCannotBreak(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void whatMustNotBeRelocatedStaysWhereItIs(GameTestHelper h) {
        TileBlocksRuntimeChecks.whatMustNotBeRelocatedStaysWhereItIs(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theRemotePlacerTakesAnyStoneDevice(GameTestHelper h) {
        TileBlocksRuntimeChecks.theRemotePlacerTakesAnyStoneDevice(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void interfaceForwardsItems(GameTestHelper h) {
        TileBlocksRuntimeChecks.interfaceForwardsItems(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void interfaceForwardsEssentia(GameTestHelper h) {
        TileBlocksRuntimeChecks.interfaceForwardsEssentia(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void interfaceForwardsTheHopperRules(GameTestHelper h) {
        TileBlocksRuntimeChecks.interfaceForwardsTheHopperRules(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void interfaceMovedOntoItsOwnTargetEndsTheBinding(GameTestHelper h) {
        TileBlocksRuntimeChecks.interfaceMovedOntoItsOwnTargetEndsTheBinding(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void interfacesBoundToEachOtherEndTheBinding(GameTestHelper h) {
        TileBlocksRuntimeChecks.interfacesBoundToEachOtherEndTheBinding(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void binderLinksDevices(GameTestHelper h) {
        TileBlocksRuntimeChecks.binderLinksDevices(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void binderComparesTheDimension(GameTestHelper h) {
        TileBlocksRuntimeChecks.binderComparesTheDimension(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void binderBindsGolemConnectors(GameTestHelper h) {
        TileBlocksRuntimeChecks.binderBindsGolemConnectors(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void camouflageIsSetAndCleared(GameTestHelper h) {
        TileBlocksRuntimeChecks.camouflageIsSetAndCleared(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void camouflagedDevicesLeaveTheFacesOfTheirNeighbours(GameTestHelper h) {
        TileBlocksRuntimeChecks.camouflagedDevicesLeaveTheFacesOfTheirNeighbours(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void wandTurnsTheFacingDevices(GameTestHelper h) {
        TileBlocksRuntimeChecks.wandTurnsTheFacingDevices(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void locomotiveCarriesItsPassenger(GameTestHelper h) {
        TileBlocksRuntimeChecks.locomotiveCarriesItsPassenger(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void locomotiveTurnsRoundAtTheEndOfTheTrack(GameTestHelper h) {
        TileBlocksRuntimeChecks.locomotiveTurnsRoundAtTheEndOfTheTrack(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void poweredLocomotiveStays(GameTestHelper h) {
        TileBlocksRuntimeChecks.poweredLocomotiveStays(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void relaysFindEachOther(GameTestHelper h) {
        TileBlocksRuntimeChecks.relaysFindEachOther(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void relayKeepsTheLastRelayItFinds(GameTestHelper h) {
        TileBlocksRuntimeChecks.relayKeepsTheLastRelayItFinds(h);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void relayDropsAStalePartner(GameTestHelper h) {
        TileBlocksRuntimeChecks.relayDropsAStalePartner(h);
    }
}
