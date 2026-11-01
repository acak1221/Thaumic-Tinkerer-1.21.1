/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the NeoForge GameTest harness, test-only. Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.testing;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import thaumic.tinkerer.common.block.tile.TileEnchanter;
import thaumic.tinkerer.common.block.tile.TileRPlacer;
import thaumic.tinkerer.common.block.tile.TileRepairer;
import thaumic.tinkerer.common.content.Machines;
import thaumic.tinkerer.common.content.TileBlocks;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * NeoForge-only scenarios: the item capability ({@code Capabilities.ItemHandler.BLOCK}) of the add-on's devices that are
 * containers ({@code NeoForgeCapabilities.containerItems}). Fabric's Transfer API wraps every {@code Container} block entity by
 * itself, so there is nothing of the add-on to test there; NeoForge wraps only vanilla's. The generator lists this class in
 * {@code TinkererGameTestCatalogue.NEOFORGE_ONLY}. Test-only, not distributed.
 */
@PrefixGameTestTemplate(true)
public final class ItemCapabilitiesNeoForgeTests {
    /**
     * Every block entity type of the add-on, made for one of its blocks: a container has an item handler for a side (as many
     * slots as {@code getSlotsForFace} gives, for a {@code WorldlyContainer}) and one without a side (the whole container); a
     * block entity that is no container has none. The nine containers (eight without the Kami group) are all there.
     */
    @GameTest(template = "empty", templateNamespace = "thaumictinkerer")
    public void everyContainerOfTheAddonHasAnItemHandler(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        List<String> problems = new ArrayList<>();
        List<String> containers = new ArrayList<>();
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (id == null || !LibMisc.MOD_ID.equals(id.getNamespace())) continue;
            Block block = type.getValidBlocks().stream().findFirst().orElse(null);
            if (block == null) {
                problems.add(id + ": no valid block");
                continue;
            }
            BlockState state = block.defaultBlockState();
            BlockEntity entity = type.create(at, state);
            if (entity == null) {
                problems.add(id + ": no block entity for " + state);
                continue;
            }
            IItemHandler sided = Capabilities.ItemHandler.BLOCK.getCapability(level, at, state, entity, Direction.UP);
            IItemHandler whole = Capabilities.ItemHandler.BLOCK.getCapability(level, at, state, entity, null);
            if (entity instanceof Container container) {
                containers.add(id.getPath());
                if (sided == null || whole == null) {
                    problems.add(id + ": a container without an item handler (side " + sided + ", none " + whole + ")");
                    continue;
                }
                if (whole.getSlots() != container.getContainerSize()) {
                    problems.add(id + ": without a side " + whole.getSlots() + " slots, the container has " + container.getContainerSize());
                }
                int up = entity instanceof WorldlyContainer worldly ? worldly.getSlotsForFace(Direction.UP).length : container.getContainerSize();
                if (sided.getSlots() != up) problems.add(id + ": from above " + sided.getSlots() + " slots, expected " + up);
            } else if (sided != null || whole != null) {
                problems.add(id + ": not a container, but an item handler");
            }
        }
        h.assertTrue(problems.isEmpty(), "item handlers: " + problems);
        int expected = ConfigHandler.enableKami ? 9 : 8;
        h.assertTrue(containers.size() == expected, "the " + expected + " containers of the add-on: " + containers);
        h.succeed();
    }

    /**
     * The Thaumic Restorer through a side keeps its side rules: a tool that is not damaged is refused, a damaged one goes in (one
     * at a time), it does not come out while it is not mended, and it comes out once it is.
     */
    @GameTest(template = "empty", templateNamespace = "thaumictinkerer")
    public void theRestorerKeepsItsSideRules(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 1, 2);
        h.setBlock(rel, Machines.REPAIRER.get());
        TileRepairer repairer = h.getBlockEntity(rel);
        IItemHandler side = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(rel), Direction.NORTH);
        h.assertTrue(side != null && side.getSlots() == 1, "the restorer has one slot from the side: " + (side == null ? null : side.getSlots()));

        ItemStack whole = new ItemStack(Items.IRON_PICKAXE);
        ItemStack left = side.insertItem(0, whole.copy(), false);
        h.assertTrue(ItemStack.matches(left, whole) && repairer.getItem(0).isEmpty(), "a tool that is not damaged is refused: " + left);

        ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(100);
        left = side.insertItem(0, damaged.copy(), false);
        h.assertTrue(left.isEmpty() && repairer.getItem(0).getDamageValue() == 100, "a damaged tool goes in: " + left + ", " + repairer.getItem(0));
        left = side.insertItem(0, damaged.copy(), true);
        h.assertTrue(left.getCount() == 1, "a second one does not fit (one item at a time): " + left);

        h.assertTrue(side.extractItem(0, 1, false).isEmpty() && !repairer.getItem(0).isEmpty(), "a tool that is not mended does not come out");
        repairer.getItem(0).setDamageValue(0);
        ItemStack out = side.extractItem(0, 1, false);
        h.assertTrue(out.is(Items.IRON_PICKAXE) && out.getDamageValue() == 0 && repairer.getItem(0).isEmpty(), "the mended tool comes out: " + out);
        h.succeed();
    }

    /**
     * The Enchanter offers no slot through a side (1.7.10 {@code getAccessibleSlotsFromSide}: none), so nothing goes in or comes out
     * there; without a side the handler is the whole container (NeoForge's convention), which still refuses what
     * {@code canPlaceItem} refuses (everything).
     */
    @GameTest(template = "empty", templateNamespace = "thaumictinkerer")
    public void theEnchanterTakesNothingThroughItsSides(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 1, 2);
        h.setBlock(rel, thaumic.tinkerer.common.content.Enchantments.enchanterBlock());
        TileEnchanter enchanter = h.getBlockEntity(rel);
        enchanter.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        BlockPos abs = h.absolutePos(rel);

        for (Direction direction : Direction.values()) {
            IItemHandler side = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, direction);
            h.assertTrue(side != null && side.getSlots() == 0, "no slot from " + direction + ": " + (side == null ? null : side.getSlots()));
            ItemStack refused = ItemHandlerHelper.insertItem(side, new ItemStack(Items.LAPIS_LAZULI, 3), false);
            h.assertTrue(refused.getCount() == 3, "nothing goes in from " + direction);
        }
        h.assertTrue(enchanter.getItem(0).is(Items.DIAMOND_SWORD), "and the sword was not taken out");

        IItemHandler whole = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, abs, null);
        h.assertTrue(whole != null && whole.getSlots() == enchanter.getContainerSize(), "without a side: the whole container");
        ItemStack refused = ItemHandlerHelper.insertItem(whole, new ItemStack(Items.DIAMOND_PICKAXE), false);
        h.assertTrue(refused.is(Items.DIAMOND_PICKAXE), "which takes nothing in either (canPlaceItem is false)");
        h.assertTrue(whole.extractItem(0, 1, true).is(Items.DIAMOND_SWORD), "and shows what it holds");
        h.succeed();
    }

    /** The Remote Placer from any side: blocks go in (a stack of 64), other items do not, and blocks come out again. */
    @GameTest(template = "empty", templateNamespace = "thaumictinkerer")
    public void theRemotePlacerTakesBlocksOnly(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 1, 2);
        h.setBlock(rel, TileBlocks.remotePlacerBlock());
        TileRPlacer placer = h.getBlockEntity(rel);
        IItemHandler side = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(rel), Direction.EAST);
        h.assertTrue(side != null && side.getSlots() == 1, "the placer has its one slot from a side");

        ItemStack left = side.insertItem(0, new ItemStack(Items.STICK, 5), false);
        h.assertTrue(left.getCount() == 5 && placer.getItem(0).isEmpty(), "a stick is not a block: refused");
        left = side.insertItem(0, new ItemStack(Items.STONE, 10), false);
        h.assertTrue(left.isEmpty() && placer.getItem(0).is(Items.STONE) && placer.getItem(0).getCount() == 10, "ten stone go in");
        ItemStack out = side.extractItem(0, 4, false);
        h.assertTrue(out.is(Items.STONE) && out.getCount() == 4 && placer.getItem(0).getCount() == 6, "four come out: " + out);
        h.succeed();
    }
}
