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
package thaumic.tinkerer.common.item.foci;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.ThaumcraftSoundIds;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchPage;
import thaumcraft.api.wands.FocusUpgradeType;
import thaumcraft.api.wands.WandHelper;
import thaumic.tinkerer.common.ThaumicTinkerer;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.item.foci.FociComponents.DislocationData;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.common.lib.LibResearch;
import thaumic.tinkerer.common.registry.ThaumicTinkererInfusionRecipe;
import thaumic.tinkerer.common.registry.ThaumicTinkererRecipe;
import thaumic.tinkerer.common.research.IRegisterableResearch;
import thaumic.tinkerer.common.research.ResearchHelper;
import thaumic.tinkerer.common.research.TTResearchItem;

/**
 * Wand Focus: Dislocation. A cast on a block takes it, with its state and the saved data of its block entity, into the
 * focus (and pays for it); a cast on a block with a block in the focus puts that block, with its data, on the side that was
 * hit (free). The block the focus carries is shown beside the wand's dial.
 *
 * <p>The cost is 5 Perditio, 5 Ordo and 1 Terra for a block, five times that for a block with a block entity, and
 * 100 Perditio, 100 Ordo and 50 Terra for a spawner. Blocks of the piston family, those on the blacklist of the config
 * ({@code focusOfDislocationBlacklist}), those of Thaumcraft's portable-hole blacklist
 * ({@code #thaumcraft:portable_hole_blacklist}), those of the add-on's own {@code #thaumictinkerer:dislocation_blacklist}
 * (reinforced deepslate and budding amethyst, which no piston moves either), those that cannot be broken (a destroy time
 * below zero, the game master blocks, and, new, whatever the casting player cannot break: Thaumcraft's warded glass, arcane
 * door and arcane pressure plate), flowing water and lava, and the halves of two-part blocks ({@link #isTwoPart}) cannot be
 * taken. A load is put down only if it is one the focus could have taken by its state ({@link #canCarry}), so a load written
 * by hand into the component cannot place bedrock or a command block.</p>
 *
 * <p>New: the server asks {@link ItemModFocus#mayBreak} before it takes a block and {@link ItemModFocus#mayPlace} before it
 * puts one down (spawn protection, adventure mode, the protection mods). The data of a block entity that only an operator may
 * set ({@code onlyOpCanSetNbt}: spawners, signs, lecterns) is put back only for a player who may use the game master blocks or
 * when the load carries this server's seal ({@link DislocationSeal}), that is when the focus took it itself.</p>
 *
 * <p>Where the 1.21.1 block system differs from 1.7.10: the block's metadata is its state; the contents of a container are
 * not dropped when the block is taken (the block entity is removed first, as in 1.7.10); a block is placed only where the
 * target is replaceable, the block can stand there and nothing blocks the place (1.7.10 checked the first only, so a block
 * that could not stand there popped off as an item). The full list is in {@code docs/deviations/Foci.md}.</p>
 */
public class ItemFocusDislocation extends ItemModFocus {
    /** The cost of taking a block without a block entity, in centivis (casting is free). */
    public static final AspectList VIS_USAGE = new AspectList().add(Aspect.ENTROPY, 500).add(Aspect.ORDER, 500)
            .add(Aspect.EARTH, 100);
    /** The cost of taking a block with a block entity. */
    public static final AspectList VIS_USAGE_TILE = new AspectList().add(Aspect.ENTROPY, 2500).add(Aspect.ORDER, 2500)
            .add(Aspect.EARTH, 500);
    /** The cost of taking a spawner. */
    public static final AspectList VIS_USAGE_SPAWNER = new AspectList().add(Aspect.ENTROPY, 10000)
            .add(Aspect.ORDER, 10000).add(Aspect.EARTH, 5000);

    /**
     * The largest saved block entity the focus takes, in bytes of NBT. The data travels with the wand to every client that
     * can see it, and a client refuses a tag of more than two megabytes, which would then keep the player from joining.
     */
    public static final int MAX_TILE_BYTES = 1_000_000;

    private static final TagKey<Block> PORTABLE_HOLE_BLACKLIST = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "portable_hole_blacklist"));
    /** New: the add-on's own blacklist (reinforced deepslate and budding amethyst, which vanilla lets no piston move). */
    public static final TagKey<Block> DISLOCATION_BLACKLIST = TagKey.create(Registries.BLOCK, LibMisc.id("dislocation_blacklist"));

    public ItemFocusDislocation(Item.Properties properties) {
        super(properties);
    }

    // ---- the cost and what may be taken ----------------------------------------------------------------------------

    /** The price of taking a block: with no block entity, with one, or a spawner's. */
    public static AspectList getCost(@Nullable BlockEntity tile) {
        return (tile == null ? VIS_USAGE : isSpawner(tile) ? VIS_USAGE_SPAWNER : VIS_USAGE_TILE).copy();
    }

    /** A spawner of mobs: the 1.7.10 {@code TileEntityMobSpawner}, and the trial spawner that 1.21.1 adds. */
    public static boolean isSpawner(BlockEntity tile) {
        return tile instanceof SpawnerBlockEntity || tile instanceof TrialSpawnerBlockEntity;
    }

    /**
     * Whether the config's blacklist allows the block. An entry is a block id ({@code minecraft:stone}; the namespace is
     * {@code minecraft} when it is left out) or, new, a block tag with a leading {@code #}.
     */
    public static boolean isBlockAllowed(String[] blacklist, ResourceLocation block, Predicate<ResourceLocation> inTag) {
        for (String blacklistedBlock : blacklist) {
            if (blacklistedBlock == null) continue;
            String entry = blacklistedBlock.trim();
            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null && inTag.test(tag)) return false;
            } else if (block.equals(ResourceLocation.tryParse(entry))) {
                return false;
            }
        }
        return true;
    }

    /** The config's list as it holds for {@code level}: on a client connected to a server, the server's list (sent on join). */
    private static boolean isBlockAllowed(@Nullable BlockGetter level, BlockState state) {
        return isBlockAllowed(ConfigHandler.dislocationBlacklist(level), BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                tag -> state.is(TagKey.create(Registries.BLOCK, tag)));
    }

    /**
     * A state that is one part of a block of two: the halves of a tall plant, a door, a bed. Placing it builds the other half
     * (1.21.1 {@code DoublePlantBlock.setPlacedBy} and {@code BedBlock.setPlacedBy} set it unconditionally, over whatever
     * stands there), and 1.7.10 could not carry it either: a half without its other half dropped as an item at once. So the
     * focus does not take such a block (the doors and beds are on Thaumcraft's blacklist as well, but a data pack can edit that tag).
     */
    public static boolean isTwoPart(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART);
    }

    /** A flowing fluid (a {@link LiquidBlock} that is not a source): 1.7.10's ray stopped only on sources, so the focus takes only those. */
    private static boolean isFlowingFluid(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && !state.getFluidState().isSource();
    }

    /**
     * A state that no cast of this focus puts into it, whatever the config and the tags say: air, the moving parts of a piston,
     * a half of a two-part block, flowing fluid, a block that cannot be broken (a destroy time below zero) and the blocks of
     * the game master ({@link GameMasterBlock}: command, structure and jigsaw blocks). A load of that kind was written by hand
     * (a command, a creative player's component); the focus drops it.
     */
    public static boolean isImpossibleLoad(BlockGetter level, BlockPos pos, BlockState state) {
        return state.isAir() || state.is(Blocks.MOVING_PISTON) || state.is(Blocks.PISTON_HEAD) || isTwoPart(state)
                || isFlowingFluid(state) || state.getDestroySpeed(level, pos) < 0F || state.getBlock() instanceof GameMasterBlock;
    }

    /** On a blacklist: the config's (as it holds for {@code level}), Thaumcraft's portable-hole tag (with the pistons) or the add-on's own tag. */
    public static boolean isBlacklisted(@Nullable BlockGetter level, BlockState state) {
        return !isBlockAllowed(level, state) || state.is(PORTABLE_HOLE_BLACKLIST) || state.is(DISLOCATION_BLACKLIST);
    }

    /** {@link #isBlacklisted(BlockGetter, BlockState)} with this side's own config. */
    public static boolean isBlacklisted(BlockState state) {
        return isBlacklisted(null, state);
    }

    /**
     * Whether the focus may carry a block of this state at all, judged by the state alone (everything the 1.7.10 condition
     * checked except the vis and the player): asked of the block to take and of the load to put down.
     */
    public static boolean canCarry(BlockGetter level, BlockPos pos, BlockState state) {
        return !isImpossibleLoad(level, pos, state) && !isBlacklisted(level, state);
    }

    /**
     * Whether this player may take this block: {@link #canCarry}, and the player can break it at all. New: the mining progress
     * of the player on the block ({@code getDestroyProgress}) must be above zero, which refuses what 1.7.10's hardness test
     * let through, Thaumcraft's warded glass, arcane door and arcane pressure plate (unbreakable for everybody while the
     * warded stone option is on, by a progress of zero); a progress that is not a number counts as none.
     */
    public static boolean canTake(Player player, BlockGetter level, BlockPos pos, BlockState state) {
        return canCarry(level, pos, state) && state.getDestroyProgress(player, level, pos) > 0F;
    }

    /** Whether the carried block can be put at the target: replaceable, able to stand there, free of entities, not protected. */
    public static boolean canPlaceAt(Level level, BlockPos target, BlockState state, Player player) {
        return !isTwoPart(state) && level.isInWorldBounds(target) && level.getBlockState(target).canBeReplaced()
                && state.canSurvive(level, target) && level.isUnobstructed(state, target, CollisionContext.of(player))
                && level.mayInteract(player, target);
    }

    /** A chest that was one half of a double chest is a single chest in the focus. */
    public static BlockState normalize(BlockState state) {
        return state.hasProperty(BlockStateProperties.CHEST_TYPE)
                ? state.setValue(BlockStateProperties.CHEST_TYPE, ChestType.SINGLE) : state;
    }

    // ---- what the focus carries ------------------------------------------------------------------------------------

    /** The focus of a wand's stack, or the stack itself when it is the focus (1.7.10 asked the wand for it by class). */
    private static ItemStack focusOf(ItemStack stack) {
        return WandHelper.isWand(stack) ? WandHelper.getFocusItem(stack) : stack;
    }

    /** What the focus (or the wand holding it) carries, or null. */
    @Nullable
    public static DislocationData getData(ItemStack wandOrFocus) {
        return FociComponents.DISLOCATION == null ? null : focusOf(wandOrFocus).get(FociComponents.DISLOCATION.get());
    }

    /** The carried block as an item (1.7.10 {@code getPickedBlock}); empty when the focus carries none or the block has no item. */
    public static ItemStack getPickedBlock(ItemStack wandOrFocus) {
        DislocationData data = getData(wandOrFocus);
        return data == null ? ItemStack.EMPTY : new ItemStack(data.state().getBlock());
    }

    /** The saved block entity of the carried block (1.7.10 {@code getStackTileEntity}); empty for none. */
    public static CompoundTag getStackTileEntity(ItemStack wandOrFocus) {
        DislocationData data = getData(wandOrFocus);
        return data == null ? new CompoundTag() : data.tile();
    }

    /** What makes two foci with different loads different in the focus selector (1.7.10 {@code getUniqueKey}). */
    public String getUniqueKey(ItemStack wandOrFocus) {
        DislocationData data = getData(wandOrFocus);
        if (data == null) return "";
        return String.format("%s-%d", data.state(), data.tileHash());
    }

    private static void storePickedBlock(ItemStack wand, ItemStack focus, BlockState state, CompoundTag tile, String seal) {
        focus.set(FociComponents.DISLOCATION.get(), new DislocationData(state, tile, seal));
        WandHelper.setFocus(wand, focus);
    }

    private static void clearPickedBlock(ItemStack wand, ItemStack focus) {
        focus.remove(FociComponents.DISLOCATION.get());
        WandHelper.setFocus(wand, focus);
    }

    // ---- the cast --------------------------------------------------------------------------------------------------

    @Override
    public ItemStack onFocusRightClick(ItemStack wandstack, Level level, Player player, InteractionHand hand) {
        BlockHitResult hit = trace(player, player.blockInteractionRange(), true, false);
        if (hit.getType() != HitResult.Type.BLOCK) return wandstack;

        dislocate(wandstack, level, player, hand, hit);
        return wandstack;
    }

    /** The cast on a block, with the hit given: takes the block when the focus is empty, puts the carried block down otherwise. */
    public void dislocate(ItemStack wandstack, Level level, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        if (!player.mayUseItemAt(pos, hit.getDirection(), wandstack)) return;

        ItemStack focus = WandHelper.getFocusItem(wandstack);
        if (focus.isEmpty()) return;
        DislocationData carried = FociComponents.DISLOCATION.isPresent() ? focus.get(FociComponents.DISLOCATION.get()) : null;
        if (carried != null) {
            place(wandstack, focus, level, player, hand, pos.relative(hit.getDirection()), carried);
        } else {
            take(wandstack, focus, level, player, hand, pos);
        }
    }

    private void place(ItemStack wand, ItemStack focus, Level level, Player player, InteractionHand hand, BlockPos target,
            DislocationData carried) {
        BlockState state = carried.state();
        if (isImpossibleLoad(level, target, state)) {
            // A load that no cast of this focus makes (a command or a creative player's component made it): bedrock, a command
            // block, flowing water... A half of a two-part block would build its other half over whatever stands above, and it
            // could never be put down anywhere, so the focus would stay full for good. The focus drops it.
            if (!level.isClientSide) clearPickedBlock(wand, focus);
            return;
        }
        if (isBlacklisted(level, state)) {
            // Taken before the blacklist named it: it stays in the focus (it would be lost if dropped) until the list changes.
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("ttmisc.focusDislocation.blacklisted"), true);
            }
            return;
        }
        if (!canPlaceAt(level, target, state, player)) return;

        if (!level.isClientSide) {
            if (!mayPlace(player, level, target, state)) {
                resync(player);
                return;
            }
            if (!level.setBlock(target, state, Block.UPDATE_ALL)) return;
            BlockState placed = level.getBlockState(target);
            placed.getBlock().setPlacedBy(level, target, placed, player, new ItemStack(placed.getBlock()));
            restoreTile(level, target, carried, player);
            clearPickedBlock(wand, focus);
            FociFx.dislocationBursts((ServerLevel) level, target, FociFx.PLACE_BURST_LIFT);
        } else {
            player.swing(hand);
        }

        FociFx.thaumcraftSound(level, player, ThaumcraftSoundIds.WAND, 0.5F, 1F);
    }

    /**
     * Whether the saved data may go into a block entity of this kind for this player. Vanilla's rule for the block entity data
     * of an item ({@code BlockItem.updateCustomBlockEntityTag}): a block entity that only an operator may set
     * ({@code onlyOpCanSetNbt}: spawners, the command block, signs, lecterns) takes it only from a player who may use the game
     * master blocks. And the port's: or when the load carries this server's seal ({@link DislocationSeal}), that is when the
     * focus took it from the world itself.
     */
    private static boolean mayLoad(BlockEntity entity, Player player, Level level, DislocationData carried) {
        if (!entity.onlyOpCanSetNbt() || player.canUseGameMasterBlocks()) return true;
        return level.getServer() != null && DislocationSeal.isGenuine(level.getServer(), carried);
    }

    /**
     * Gives the block just placed the block entity it carried: the saved tag, loaded into the one the block made, or a new one.
     * A block entity that may not take the data for this player ({@link #mayLoad}) keeps what the placed block gave it.
     */
    private static void restoreTile(Level level, BlockPos pos, DislocationData carried, Player player) {
        if (!carried.hasTile()) return;
        CompoundTag tag = carried.tile();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        BlockState state = level.getBlockState(pos);
        try {
            BlockEntity existing = level.getBlockEntity(pos);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (existing != null && id != null && id.equals(BlockEntityType.getKey(existing.getType()))) {
                if (!mayLoad(existing, player, level, carried)) return;
                existing.loadWithComponents(tag, level.registryAccess());
            } else {
                BlockEntity loaded = BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
                if (loaded == null || !loaded.getType().isValid(state) || !mayLoad(loaded, player, level, carried)) return;
                level.setBlockEntity(loaded);
            }
        } catch (RuntimeException e) {
            ThaumicTinkerer.LOG.warn("Focus of Dislocation: the block entity at {} could not be restored: {}", pos, e.toString());
            return;
        }
        BlockEntity restored = level.getBlockEntity(pos);
        if (restored != null) restored.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    private void take(ItemStack wand, ItemStack focus, Level level, Player player, InteractionHand hand, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!canTake(player, level, pos, state) || !level.mayInteract(player, pos)) return;

        BlockEntity tile = level.getBlockEntity(pos);
        AspectList cost = getCost(tile);

        if (!level.isClientSide) {
            // What only the server knows (the protection mods, the size of the data) undoes the client's prediction.
            if (!mayBreak(player, level, pos, state)) {
                resync(player);
                return;
            }
            CompoundTag saved = tile == null ? new CompoundTag() : tile.saveWithFullMetadata(level.registryAccess());
            if (saved.sizeInBytes() > MAX_TILE_BYTES) {
                player.displayClientMessage(Component.translatable("ttmisc.focusDislocation.tooBig"), true);
                resync(player);
                return;
            }
            if (!WandHelper.consumeAllVis(wand, player, cost, true, false)) return;

            // The block entity goes first, so that a container does not drop what it holds: it travels in the focus.
            level.removeBlockEntity(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            BlockState carried = normalize(state);
            String seal = saved.isEmpty() ? "" : DislocationSeal.seal(level.getServer(), carried, saved);
            storePickedBlock(wand, focus, carried, saved, seal);
            FociFx.dislocationBursts((ServerLevel) level, pos, 0F);
        } else {
            // The client only predicts: the server decides, takes the block and sends the changed wand back.
            if (!WandHelper.consumeAllVis(wand, player, cost, true, false)) return;
            player.swing(hand);
        }
        FociFx.sound(level, player, state.getSoundType().getBreakSound(), 1F, 1F);
        FociFx.thaumcraftSound(level, player, ThaumcraftSoundIds.WAND, 0.5F, 1F);
    }

    // ---- the focus -------------------------------------------------------------------------------------------------

    @Override
    public String getSortingHelper(ItemStack focusstack) {
        return "TTDL" + super.getSortingHelper(focusstack) + getUniqueKey(focusstack);
    }

    @Override
    public int getFocusColor(ItemStack focusstack) {
        return 0xFFB200;
    }

    @Override
    protected boolean hasOrnament() {
        return true;
    }

    @Override
    public AspectList getVisCost(ItemStack focusstack) {
        return VIS_USAGE.copy();
    }

    @Override
    public FocusUpgradeType[] getPossibleUpgradesByRank(ItemStack focusstack, int rank) {
        return rank >= 1 && rank <= 5 ? new FocusUpgradeType[] { FocusUpgradeType.frugal } : null;
    }

    @Override
    public IRegisterableResearch getResearchItem() {
        if (!ThaumcraftApi.isMirrorsAllowed()) {
            return null;
        }
        TTResearchItem research = new TTResearchItem(LibResearch.KEY_FOCUS_DISLOCATION,
                new AspectList().add(Aspect.ELDRITCH, 2).add(Aspect.MAGIC, 1).add(Aspect.EXCHANGE, 1), -5, -5, 2,
                new ItemStack(this));
        research.setSecondary().setParents(LibResearch.KEY_FOCUS_FLIGHT).setConcealed().setPages(new ResearchPage("0"),
                new ResearchPage("1"), ResearchHelper.infusionPage(LibResearch.KEY_FOCUS_DISLOCATION));
        return research;
    }

    @Override
    public ThaumicTinkererRecipe getRecipeItem() {
        return new ThaumicTinkererInfusionRecipe(LibResearch.KEY_FOCUS_DISLOCATION, new ItemStack(this), 8,
                new AspectList().add(Aspect.ELDRITCH, 20).add(Aspect.DARKNESS, 10).add(Aspect.VOID, 25)
                        .add(Aspect.MAGIC, 20).add(Aspect.TAINT, 5),
                new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.QUARTZ), new ItemStack(Items.QUARTZ),
                new ItemStack(Items.QUARTZ), new ItemStack(Items.QUARTZ), ItemsTC.stack("amber_gem"), ItemsTC.stack("amber_gem"),
                ItemsTC.stack("amber_gem"), new ItemStack(Items.DIAMOND));
    }
}
