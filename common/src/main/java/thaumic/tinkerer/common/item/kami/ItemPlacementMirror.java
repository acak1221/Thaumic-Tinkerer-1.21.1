/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.item.kami;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchPage;
import thaumic.tinkerer.common.hooks.BlockProtection;
import thaumic.tinkerer.common.item.kami.tool.ToolHandler;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.lib.LibResearch;
import thaumic.tinkerer.common.registry.ThaumicTinkererInfusionRecipe;
import thaumic.tinkerer.common.registry.ThaumicTinkererRecipe;
import thaumic.tinkerer.common.registry.TTRegistry;
import thaumic.tinkerer.common.research.ResearchHelper;
import thaumic.tinkerer.common.research.TTResearchItem;

/**
 * The Worldshaper's Looking Glass: sneak-click a block to remember it, sneak-click in the air to change the size of the
 * square (3, 5, 7, 9 or 11), click to place the square against the face you look at, taking the blocks from the
 * inventory and from Black Hole Rings. 1.7.10 stored {@code blockName}/{@code blockMeta}/{@code size} in the stack tag;
 * the block state and the size are components here.
 */
public class ItemPlacementMirror extends KamiItemBase {
    /** How far the player's look reaches for the square: 1.7.10 {@code raytraceFromEntity(..., 5)}. */
    public static final double REACH = 5;

    public ItemPlacementMirror(Item.Properties properties) {
        super(properties.stacksTo(1));
    }

    // ---- stored data ----------------------------------------------------------------------------------------

    private static void setSize(ItemStack stack, int size) {
        stack.set(KamiComponents.MIRROR_SIZE.get(), size | 1);
    }

    public static int getSize(ItemStack stack) {
        return stack.getOrDefault(KamiComponents.MIRROR_SIZE.get(), 3) | 1;
    }

    /**
     * The state the mirror places, or null when it has none. It is the remembered state as {@link #placedState} makes it, so a
     * mirror saved with a whole state before that rule (a double slab, eight layers of snow) places the single block too.
     */
    public static BlockState getBlockState(ItemStack stack) {
        return placedState(stack.get(KamiComponents.MIRROR_STATE.get()));
    }

    /** The properties that only turn a block; they are kept from the block the mirror was clicked on. */
    private static final Set<String> ORIENTATION = Set.of("axis", "facing", "half", "orientation", "rotation");

    /**
     * The properties that stand for something put into the block (honey, charges, a record, books, bottles, layers of a stack); a
     * block whose value of one of them is not the block's default is not remembered at all.
     */
    private static final Set<String> CONTENTS = Set.of(BlockStateProperties.LEVEL_HONEY.getName(),
            BlockStateProperties.RESPAWN_ANCHOR_CHARGES.getName(), BlockStateProperties.HAS_RECORD.getName(),
            BlockStateProperties.HAS_BOOK.getName(), BlockStateProperties.HAS_BOTTLE_0.getName(),
            BlockStateProperties.HAS_BOTTLE_1.getName(), BlockStateProperties.HAS_BOTTLE_2.getName(),
            BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED.getName(),
            BlockStateProperties.CHISELED_BOOKSHELF_SLOT_1_OCCUPIED.getName(),
            BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED.getName(),
            BlockStateProperties.CHISELED_BOOKSHELF_SLOT_3_OCCUPIED.getName(),
            BlockStateProperties.CHISELED_BOOKSHELF_SLOT_4_OCCUPIED.getName(),
            BlockStateProperties.CHISELED_BOOKSHELF_SLOT_5_OCCUPIED.getName(), "level", "eggs", "pickles", "candles", "bites",
            "flower_amount");

    /**
     * New in the port: the state the mirror places for a remembered state, worth exactly the one item that each placed block costs,
     * or null when the mirror refuses it. 1.7.10 stored the block and its metadata and placed that, so a double slab, a stack of
     * snow layers or a hive full of honey came out of a single item. Here the placed state is the block's default state with
     * the properties that only turn it ({@link #ORIENTATION}: axis, facing, half...) taken over; a double slab becomes a bottom slab
     * (a top slab stays one), snow is one layer, leaves are persistent (as when a player places them by hand, so they do not
     * decay), and everything else (waterlogged, lit, powered, the note of a note block...) is the default. A state whose
     * {@link #CONTENTS} differ from the default (a beehive with honey, a charged respawn anchor, a jukebox with a record) is
     * refused.
     */
    public static BlockState placedState(BlockState remembered) {
        if (remembered == null) return null;
        Block block = remembered.getBlock();
        BlockState fresh = block.defaultBlockState();
        BlockState placed = fresh;
        for (Property<?> property : remembered.getProperties()) {
            if (!placed.hasProperty(property)) continue;
            String name = property.getName();
            if (ORIENTATION.contains(name)) {
                placed = copy(remembered, placed, property);
            } else if (CONTENTS.contains(name) && !remembered.getValue(property).equals(fresh.getValue(property))) {
                return null;
            }
        }
        if (block instanceof SlabBlock && remembered.getValue(SlabBlock.TYPE) == SlabType.TOP) {
            placed = placed.setValue(SlabBlock.TYPE, SlabType.TOP);
        } else if (block instanceof SlabBlock) {
            placed = placed.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        }
        if (block instanceof SnowLayerBlock) placed = placed.setValue(SnowLayerBlock.LAYERS, 1);
        if (placed.hasProperty(BlockStateProperties.PERSISTENT)) placed = placed.setValue(BlockStateProperties.PERSISTENT, true);
        return placed;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    public static Block getBlock(ItemStack stack) {
        BlockState state = getBlockState(stack);
        return state == null ? null : state.getBlock();
    }

    // ---- what is in the inventory ---------------------------------------------------------------------------

    /**
     * 1.7.10 {@code hasBlocks}: the inventory and the Black Hole Rings in it must hold {@code requiredAmount} of the block. A
     * creative player always has them (and the placing then takes nothing from the inventory).
     */
    public static boolean hasBlocks(Player player, Block requiredBlock, int requiredAmount) {
        if (player.getAbilities().instabuild) return true;
        Item requiredItem = requiredBlock.asItem();
        if (requiredItem == Items.AIR) return false;
        Item talismanItem = TTRegistry.item(LibItemNames.BLOCK_TALISMAN);

        int current = 0;
        List<ItemStack> talismansToCheck = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stackInSlot = inventory.getItem(i);
            if (stackInSlot.isEmpty()) continue;
            if (stackInSlot.is(requiredItem)) {
                current += stackInSlot.getCount();
                if (current >= requiredAmount) return true;
            } else if (stackInSlot.is(talismanItem) && ItemBlockTalisman.getBlock(stackInSlot) == requiredBlock) {
                talismansToCheck.add(stackInSlot);
            }
        }
        for (ItemStack talisman : talismansToCheck) {
            current += ItemBlockTalisman.getBlockCount(talisman);
            if (current >= requiredAmount) return true;
        }
        return false;
    }

    // ---- the square -----------------------------------------------------------------------------------------

    /**
     * The offsets of the placed square from the block that is aimed at, shifted by the aimed face (1.7.10
     * {@code getBlocksToPlace}). {@code rotation} is the player's facing as 0 to 3, {@code pitch} the look pitch in degrees.
     */
    public static List<int[]> squareOffsets(Direction side, float pitch, int rotation, int size) {
        int range = (size ^ 1) / 2;
        boolean topOrBottom = side == Direction.UP || side == Direction.DOWN;

        int xOff = !(side == Direction.WEST || side == Direction.EAST)
                ? topOrBottom ? pitch > 75 || (rotation & 1) == 0 ? range : 0 : range
                : 0;
        int yOff = topOrBottom ? pitch > 75 ? 0 : range : range;
        int zOff = !(side == Direction.SOUTH || side == Direction.NORTH)
                ? topOrBottom ? pitch > 75 || (rotation & 1) == 1 ? range : 0 : range
                : 0;

        List<int[]> offsets = new ArrayList<>();
        for (int x = -xOff; x < xOff + 1; x++) {
            for (int y = 0; y < yOff * 2 + 1; y++) {
                for (int z = -zOff; z < zOff + 1; z++) {
                    offsets.add(new int[] { x + side.getStepX(), y + side.getStepY(), z + side.getStepZ() });
                }
            }
        }
        return offsets;
    }

    /** The empty or replaceable positions of the square the player aims at; empty when the player aims at nothing. */
    public static List<BlockPos> getBlocksToPlace(ItemStack stack, Player player) {
        List<BlockPos> coords = new ArrayList<>();
        // 1.7.10 asked the reach of the player's game mode on the server and 5 blocks on the client.
        BlockHitResult hit = ToolHandler.raytrace(player,
                player.level().isClientSide ? REACH : player.blockInteractionRange());
        if (hit == null) return coords;
        Level level = player.level();
        BlockPos target = hit.getBlockPos();
        if (level.getBlockState(target).canBeReplaced()) target = target.below();

        int rotation = Mth.floor(player.getYRot() * 4F / 360F + 0.5D) & 3;
        for (int[] offset : squareOffsets(hit.getDirection(), player.getXRot(), rotation, getSize(stack))) {
            BlockPos pos = target.offset(offset[0], offset[1], offset[2]);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.canBeReplaced()) coords.add(pos);
        }
        return coords;
    }

    // ---- use ------------------------------------------------------------------------------------------------

    /**
     * 1.7.10 remembered a block only when {@code block.getRenderType() == 0}, the plain block model: cubes, slabs, glass,
     * leaves, furnaces; stairs, fences, walls, doors, beds, torches, panes and ladders were refused. 1.21.1 has no render
     * type, and nearly every block is {@link RenderShape#MODEL}, so the rule here is the shape: a block with an item whose
     * collision shape is the full cube, or a slab or a layer of snow (the flat blocks of render type 0). A state that
     * {@link #placedState} refuses (a block with something put into it) is not remembered either.
     */
    public static boolean canRemember(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getRenderShape() != RenderShape.MODEL || state.getBlock().asItem() == Items.AIR) return false;
        Block block = state.getBlock();
        boolean shape = block instanceof SlabBlock || block instanceof SnowLayerBlock
                || Block.isShapeFullBlock(state.getShape(level, pos));
        return shape && placedState(state) != null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null) return InteractionResult.PASS;
        BlockState state = level.getBlockState(context.getClickedPos());

        if (player.isShiftKeyDown()) {
            if (canRemember(level, context.getClickedPos(), state)) {
                stack.set(KamiComponents.MIRROR_STATE.get(), placedState(state));
            }
        } else if (!level.isClientSide) {
            placeAllBlocks(stack, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            int size = getSize(stack);
            int newSize = size == 11 ? 3 : size + 2;
            setSize(stack, newSize);
            KamiHooks.showTooltip(level, Component.literal(newSize + " x " + newSize));
            KamiHooks.playToggleSound(level, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    /**
     * Places the square (server side). The original placed wherever the square reached; here every position is first asked
     * {@link BlockProtection#mayPlace} (the game's rules: loaded, world border, spawn protection, adventure mode; and the loader's
     * place event, which claim mods answer), before anything is paid or placed. The blocks needed are those of the positions that
     * may be placed; a position that is refused costs nothing.
     */
    public void placeAllBlocks(ItemStack stack, Player player) {
        BlockState required = getBlockState(stack);
        if (required == null) return;
        if (!(player instanceof ServerPlayer server) || !(player.level() instanceof ServerLevel level)) return;
        List<BlockPos> allowed = new ArrayList<>();
        for (BlockPos pos : getBlocksToPlace(stack, player)) {
            if (!level.isOutsideBuildHeight(pos) && BlockProtection.mayPlace(server, level, pos, required)) allowed.add(pos);
        }
        if (!hasBlocks(player, required.getBlock(), allowed.size())) return;

        for (BlockPos pos : allowed) placeBlockAndConsume(player, required, pos);
        // Only the server gets here: the sound goes to the player as well.
        KamiHooks.broadcastThaumcraftSound(level, player, "wand", 1F, 1F);
    }

    private void placeBlockAndConsume(Player player, BlockState state, BlockPos pos) {
        player.level().setBlock(pos, state, Block.UPDATE_ALL);
        if (player.getAbilities().instabuild) return;

        Item item = state.getBlock().asItem();
        Item talismanItem = TTRegistry.item(LibItemNames.BLOCK_TALISMAN);
        List<ItemStack> talismansToCheck = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stackInSlot = inventory.getItem(i);
            if (!stackInSlot.isEmpty() && stackInSlot.is(item)) {
                stackInSlot.shrink(1);
                return;
            }
            if (!stackInSlot.isEmpty() && stackInSlot.is(talismanItem)) talismansToCheck.add(stackInSlot);
        }
        for (ItemStack talisman : talismansToCheck) {
            // 1.7.10 stopped at the first ring bound to the block, empty or not, so a bound empty ring in front of a full one
            // made every block free; here the first ring that really gives a block pays.
            if (ItemBlockTalisman.getBlock(talisman) == state.getBlock() && ItemBlockTalisman.remove(talisman, 1) == 1) {
                return;
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int size = getSize(stack);
        tooltip.add(Component.literal(size + " x " + size).withStyle(ChatFormatting.GRAY));
        Block block = getBlock(stack);
        if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
            tooltip.add(Component.translatable(block.getDescriptionId()).withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ThaumicTinkererRecipe getRecipeItem() {
        return new ThaumicTinkererInfusionRecipe(LibResearch.KEY_PLACEMENT_MIRROR, new ItemStack(this), 12,
                new AspectList().add(Aspect.CRAFT, 65).add(Aspect.CRYSTAL, 32).add(Aspect.MAGIC, 50)
                        .add(Aspect.MIND, 32),
                TTRegistry.stack(LibItemNames.BLOCK_TALISMAN), ItemKamiResource.stack(ItemKamiResource.ICHOR),
                new ItemStack(Items.DROPPER), new ItemStack(Items.DIAMOND), new ItemStack(Items.GLASS),
                new ItemStack(Items.BLAZE_POWDER), ItemKamiResource.stack(ItemKamiResource.ICHOR));
    }

    public static TTResearchItem research() {
        return KamiResearch.entry(LibResearch.KEY_PLACEMENT_MIRROR,
                new AspectList().add(Aspect.CRAFT, 2).add(Aspect.CRYSTAL, 1).add(Aspect.ELDRITCH, 1)
                        .add(Aspect.MIND, 1),
                17, 16, TTRegistry.stack(LibItemNames.PLACEMENT_MIRROR), 0,
                KamiResearch.of(LibResearch.KEY_BLOCK_TALISMAN), KamiResearch.NONE, new ResearchPage("0"),
                ResearchHelper.infusionPage(LibResearch.KEY_PLACEMENT_MIRROR));
    }
}
