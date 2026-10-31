/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the NeoForge GameTest harness, test-only. Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.testing;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.content.TileBlocks;

/**
 * NeoForge-only scenarios of the TileBlocks group: the block capabilities of the Transvector Interface ({@code NeoForgeCapabilities}), which
 * have no Fabric counterpart with the same shape (Fabric's lookups have no cache to invalidate). They are not generated from the Fabric
 * wrappers: tools/neoforge_gametests.py lists every {@code *NeoForgeTests} class in {@code TinkererGameTestCatalogue.NEOFORGE_ONLY} and gives it
 * its template {@code thaumictinkerer:<class in lower case>.empty}. Test-only, not distributed.
 */
@PrefixGameTestTemplate(true)
public final class TileBlocksNeoForgeTests {
    private static final int BUCKET = FluidType.BUCKET_VOLUME;

    /**
     * Items put in through {@code Capabilities.ItemHandler.BLOCK} at a bound Interface go into the bound chest, and come out of it the same
     * way; unbound the Interface has no slots. A cache made while the Interface was unbound reaches the chest once it is bound (the handler
     * reads the Interface on every call).
     */
    @GameTest(template = "empty", templateNamespace = "thaumictinkerer")
    public void interfaceItemHandlerReachesTheBoundChest(GameTestHelper h) {
        BlockPos ifacePos = new BlockPos(2, 1, 2);
        BlockPos chestPos = new BlockPos(4, 1, 2);
        h.setBlock(ifacePos, TileBlocks.interfaceBlock());
        h.setBlock(chestPos, Blocks.CHEST);
        TileTransvectorInterface device = h.getBlockEntity(ifacePos);
        ChestBlockEntity chest = h.getBlockEntity(chestPos);
        BlockPos absIface = h.absolutePos(ifacePos);
        BlockCapabilityCache<IItemHandler, Direction> cache =
                BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, h.getLevel(), absIface, Direction.UP);

        IItemHandler unbound = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, absIface, Direction.UP);
        h.assertTrue(unbound != null && unbound.getSlots() == 0, "an unbound interface offers no slots: " + (unbound == null ? null : unbound.getSlots()));
        ItemStack refused = ItemHandlerHelper.insertItem(unbound, new ItemStack(Items.GOLD_INGOT, 5), false);
        h.assertTrue(refused.getCount() == 5, "and takes nothing: " + refused);
        IItemHandler early = cache.getCapability();
        h.assertTrue(early != null, "the cache has the interface's handler while it is unbound");

        device.setTarget(h.absolutePos(chestPos));
        IItemHandler side = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, absIface, Direction.UP);
        h.assertTrue(side != null && side.getSlots() == 27, "bound, the handler has the chest's 27 slots: " + (side == null ? null : side.getSlots()));
        ItemStack left = ItemHandlerHelper.insertItem(side, new ItemStack(Items.GOLD_INGOT, 5), false);
        h.assertTrue(left.isEmpty(), "five gold ingots went in through the interface: " + left);
        h.assertTrue(countIn(chest, new ItemStack(Items.GOLD_INGOT)) == 5, "and are in the chest: " + countIn(chest, new ItemStack(Items.GOLD_INGOT)));
        int slot = firstSlotWith(chest, new ItemStack(Items.GOLD_INGOT));
        ItemStack out = side.extractItem(slot, 2, false);
        h.assertTrue(out.is(Items.GOLD_INGOT) && out.getCount() == 2, "two came out through the interface: " + out);
        h.assertTrue(chest.getItem(slot).getCount() == 3, "and left the chest: " + chest.getItem(slot));

        IItemHandler all = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, absIface, null);
        h.assertTrue(all != null && all.getSlots() == 27, "without a side, the whole chest: " + (all == null ? null : all.getSlots()));
        IItemHandler cached = cache.getCapability();
        h.assertTrue(cached != null && cached.getSlots() == 27, "the cache made while unbound reaches the chest now");
        ItemStack more = ItemHandlerHelper.insertItem(cached, new ItemStack(Items.IRON_INGOT, 4), false);
        h.assertTrue(more.isEmpty() && countIn(chest, new ItemStack(Items.IRON_INGOT)) == 4, "and puts items into it: " + more);
        h.succeed();
    }

    /**
     * A {@code BlockCapabilityCache} of the fluid capability at the Interface follows the binding: no handler while unbound; a handler that
     * fills the tank after {@code setTarget} (the binding listener invalidates the capabilities of the Interface); after the tank is broken
     * the handler it still holds no longer reaches a tank, and the Interface lets the binding go, so the cache is told and has no handler;
     * bound to a new tank and then unbound by {@code setTarget(null)}, it has none again.
     */
    @GameTest(template = "empty", templateNamespace = "thaumictinkerer")
    public void interfaceFluidCapabilityCacheFollowsTheBinding(GameTestHelper h) {
        BlockPos ifacePos = new BlockPos(2, 1, 2);
        BlockPos tankPos = new BlockPos(4, 1, 2);
        BlockPos otherTankPos = new BlockPos(2, 1, 4);
        h.setBlock(ifacePos, TileBlocks.interfaceBlock());
        h.setBlock(tankPos, Blocks.BARREL);
        h.setBlock(otherTankPos, Blocks.BARREL);
        TileTransvectorInterface device = h.getBlockEntity(ifacePos);
        AtomicInteger invalidations = new AtomicInteger();
        BlockCapabilityCache<IFluidHandler, Direction> cache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, h.getLevel(),
                h.absolutePos(ifacePos), Direction.NORTH, () -> true, invalidations::incrementAndGet);
        FluidStack water = new FluidStack(Fluids.WATER, BUCKET);

        h.assertTrue(cache.getCapability() == null, "unbound, the cache has no fluid handler");

        int before = invalidations.get();
        device.setTarget(h.absolutePos(tankPos));
        h.assertTrue(invalidations.get() > before, "binding invalidated the capabilities of the interface");
        IFluidHandler handler = cache.getCapability();
        h.assertTrue(handler != null, "bound, the cache has a fluid handler");
        int in = handler.fill(water.copy(), FluidAction.EXECUTE);
        FluidTank tank = TileBlocksNeoForgeChecks.tankOf(h.getBlockEntity(tankPos));
        h.assertTrue(in == BUCKET && tank.getFluidAmount() == BUCKET, "a bucket went through the cached handler into the tank: " + in + ", "
                + tank.getFluidAmount());

        h.setBlock(tankPos, Blocks.AIR);
        IFluidHandler stale = cache.getCapability();
        int lost = stale == null ? 0 : stale.fill(water.copy(), FluidAction.EXECUTE);
        h.assertTrue(lost == 0, "the tank is broken: what the cache held no longer reaches it: " + lost);
        h.assertTrue(tank.getFluidAmount() == BUCKET, "the broken tank got nothing more: " + tank.getFluidAmount());
        h.assertTrue(!device.isBound(), "the interface let the binding to the broken tank go");
        h.assertTrue(cache.getCapability() == null, "and the cache was told: no handler");

        device.setTarget(h.absolutePos(otherTankPos));
        IFluidHandler again = cache.getCapability();
        h.assertTrue(again != null && again.fill(water.copy(), FluidAction.EXECUTE) == BUCKET, "bound to another tank, the cache fills it");
        before = invalidations.get();
        device.setTarget(null);
        h.assertTrue(invalidations.get() > before, "unbinding invalidated the capabilities of the interface");
        h.assertTrue(cache.getCapability() == null, "unbound by setTarget(null), the cache has no handler");
        h.assertTrue(again.fill(water.copy(), FluidAction.SIMULATE) == 0, "and the handler it had no longer reaches the tank");
        h.assertTrue(TileBlocksNeoForgeChecks.tankOf(h.getBlockEntity(otherTankPos)).getFluidAmount() == BUCKET, "which kept its one bucket");
        h.succeed();
    }

    private static int countIn(ChestBlockEntity chest, ItemStack kind) {
        int count = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (ItemStack.isSameItem(chest.getItem(i), kind)) count += chest.getItem(i).getCount();
        return count;
    }

    private static int firstSlotWith(ChestBlockEntity chest, ItemStack kind) {
        for (int i = 0; i < chest.getContainerSize(); i++) if (ItemStack.isSameItem(chest.getItem(i), kind)) return i;
        return -1;
    }
}
