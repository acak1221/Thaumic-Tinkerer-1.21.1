/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the NeoForge GameTest harness, test-only. Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.testing;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.content.TileBlocks;

/**
 * The NeoForge counterparts of the Fabric-native wrappers of {@code TileBlocksGameTests}: the Fabric bodies use Fabric's transfer API,
 * these the NeoForge capabilities, and check the same things. tools/neoforge_gametests.py makes the generated
 * {@link TileBlocksGameTests} call them under the Fabric names. The NeoForge-only scenarios are in {@link TileBlocksNeoForgeTests}.
 */
public final class TileBlocksNeoForgeChecks {
    private static final int BUCKET = FluidType.BUCKET_VOLUME;
    /** Vanilla has no fluid-holding block entity: the harness gives barrels a four-bucket tank each (as the Fabric test does). */
    private static final Map<BlockEntity, FluidTank> TANKS = new WeakHashMap<>();

    private TileBlocksNeoForgeChecks() {}

    /** NeoForge registers block capabilities once, at start-up (Fabric's lookup is registered by the test itself). */
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, BlockEntityType.BARREL, (barrel, side) -> tankOf(barrel));
    }

    /** The tank of a barrel (made on the first query; also for {@link TileBlocksNeoForgeTests}). */
    static synchronized FluidTank tankOf(BlockEntity barrel) {
        return TANKS.computeIfAbsent(barrel, b -> new FluidTank(4 * BUCKET) {
            @Override
            protected void onContentsChanged() {
                b.setChanged();
            }
        });
    }

    /**
     * The Transvector Interface hands the fluid handler of the block it is bound to to whoever asks it (the NeoForge glue,
     * {@code NeoForgeCapabilities}): unbound it has none; bound to a tank it fills and drains that tank.
     */
    public static void interfaceForwardsFluids(GameTestHelper h) {
        BlockPos ifacePos = new BlockPos(2, 1, 2);
        BlockPos tankPos = new BlockPos(4, 1, 2);
        h.setBlock(ifacePos, TileBlocks.interfaceBlock());
        h.setBlock(tankPos, Blocks.BARREL);
        BlockEntity entity = h.getBlockEntity(ifacePos);
        h.assertTrue(entity instanceof TileTransvectorInterface, "an interface at " + ifacePos + ", found " + entity);
        TileTransvectorInterface device = (TileTransvectorInterface) entity;
        BlockPos absIface = h.absolutePos(ifacePos);
        h.assertTrue(h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, absIface, Direction.NORTH) == null,
                "an unbound interface has no fluid handler");

        device.setTarget(h.absolutePos(tankPos));
        IFluidHandler handler = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, absIface, Direction.NORTH);
        h.assertTrue(handler != null, "a bound interface has the fluid handler of its tank");
        int in = handler.fill(new FluidStack(Fluids.WATER, BUCKET), FluidAction.EXECUTE);
        h.assertTrue(in == BUCKET, "a bucket went in through the interface: " + in);
        FluidTank tank = TANKS.get(h.getBlockEntity(tankPos));
        h.assertTrue(tank != null && tank.getFluidAmount() == BUCKET && tank.getFluid().is(Fluids.WATER), "and sits in the tank");
        FluidStack out = handler.drain(new FluidStack(Fluids.WATER, BUCKET / 2), FluidAction.EXECUTE);
        h.assertTrue(out.getAmount() == BUCKET / 2 && out.is(Fluids.WATER), "half a bucket came out through the interface: " + out);
        h.assertTrue(tank.getFluidAmount() == BUCKET / 2, "and left the tank: " + tank.getFluidAmount());
        h.succeed();
    }
}
