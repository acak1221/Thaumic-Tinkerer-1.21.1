/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [8 Sep 2013, 19:35:02 (GMT)]
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.item;

import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.entities.ThaumcraftEntities;
import thaumcraft.api.items.ItemsTC;
import thaumic.tinkerer.common.block.tile.TileGolemConnector;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvector;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.content.TileBlocks;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.lib.LibResearch;
import thaumic.tinkerer.common.registry.ITTinkererRegisterable;
import thaumic.tinkerer.common.registry.TTRegistry;
import thaumic.tinkerer.common.registry.ThaumicTinkererArcaneRecipe;
import thaumic.tinkerer.common.registry.ThaumicTinkererRecipe;
import thaumic.tinkerer.common.research.IRegisterableResearch;

/**
 * The Transvector Binder: a right click on a Transvector Interface, Dislocator or Golem Connector remembers it, a right
 * click on the block it should be bound to (up to the device's range) binds it; sneaking and right-clicking a golem binds
 * a remembered Golem Connector to that golem.
 *
 * <p>1.7.10 kept the remembered position in the stack's NBT ({@code posx}, {@code posy}, {@code posz}, no dimension) and
 * used {@code posy == -1} for "nothing remembered". Here it is the typed component {@link #REMEMBERED_DEVICE}, a
 * {@link GlobalPos} (dimension and position); "nothing remembered" is its absence. A device remembered in another
 * dimension is not present in this one, and its dimension is compared before the level is asked for the block entity,
 * so no chunk at those coordinates of the wrong dimension is loaded. (1.7.10 also had a {@code ConnectingGolem} flag
 * that nothing set; it is gone.)</p>
 */
public class ItemConnector extends Item implements ITTinkererRegisterable {

    /**
     * The device the binder remembers ({@code thaumictinkerer:connector_device}, registered by {@link #registerComponents()}).
     * Built here so that the type exists before registration (the unit tests use it on stacks without a registry).
     */
    public static final DataComponentType<GlobalPos> REMEMBERED_DEVICE = DataComponentType.<GlobalPos>builder()
            .persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC).build();

    public ItemConnector() {
        super(new Item.Properties().stacksTo(1));
    }

    /** Registers {@link #REMEMBERED_DEVICE}; called once by {@code TileBlocks.register()}. */
    public static void registerComponents() {
        TTRegistry.DATA_COMPONENT_TYPES.register(LibItemNames.CONNECTOR + "_device", () -> REMEMBERED_DEVICE);
    }

    /** True while the binder remembers a device. */
    public static boolean hasTarget(ItemStack stack) {
        return stack.has(REMEMBERED_DEVICE);
    }

    /** The remembered device, or null. */
    @Nullable
    public static GlobalPos getTarget(ItemStack stack) {
        return stack.get(REMEMBERED_DEVICE);
    }

    public static void setTarget(ItemStack stack, GlobalPos device) {
        stack.set(REMEMBERED_DEVICE, device);
    }

    /** 1.7.10 {@code setY(stack, -1)}: forget the device. */
    public static void clearTarget(ItemStack stack) {
        stack.remove(REMEMBERED_DEVICE);
    }

    /** The block entity of the remembered device, or null when it is in another dimension than {@code level}. */
    @Nullable
    private static BlockEntity rememberedTile(Level level, GlobalPos device) {
        // Like 1.7.10 this asks for the block entity at the remembered place, which loads its chunk: a device the player
        // walked away from is found (and then "too far"), not forgotten. The dimension is compared first.
        return device.dimension() == level.dimension() ? level.getBlockEntity(device.pos()) : null;
    }

    @Override
    public IRegisterableResearch getResearchItem() {
        return null;
    }

    @Override
    public ThaumicTinkererRecipe getRecipeItem() {
        return new ThaumicTinkererArcaneRecipe(
                LibResearch.KEY_INTERFACE + "1",
                LibResearch.KEY_INTERFACE,
                new ItemStack(this),
                new AspectList().add(Aspect.ORDER, 2),
                " I ",
                " WI",
                "S  ",
                'I',
                new ItemStack(Items.IRON_INGOT),
                'W',
                new ItemStack(Items.STICK),
                'S',
                ItemsTC.stack("order_shard"));
    }

    /** {@code onItemUse}: remember a device, or bind the remembered device to the clicked block. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null) return InteractionResult.sidedSuccess(level.isClientSide);

        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        BlockEntity tile = level.getBlockEntity(pos);

        GlobalPos remembered = getTarget(stack);
        if (remembered == null) {
            if (tile instanceof TileTransvector || tile instanceof TileGolemConnector) {
                setTarget(stack, GlobalPos.of(level.dimension(), pos));

                playSound(level, pos);
                if (tile instanceof TileTransvector) player.sendSystemMessage(Component.translatable("ttmisc.connector.set"));
                else player.sendSystemMessage(Component.translatable("ttmisc.golemconnector.set"));
            } else player.sendSystemMessage(Component.translatable("ttmisc.connector.notinterf"));
        } else {
            BlockPos origin = remembered.pos();
            BlockEntity tile1 = rememberedTile(level, remembered);
            if (!(tile1 instanceof TileTransvector trans)) {
                clearTarget(stack);

                player.sendSystemMessage(Component.translatable("ttmisc.connector.notpresent"));
            } else {
                if (tile instanceof TileTransvectorInterface && tile1 instanceof TileTransvectorInterface) {
                    player.sendSystemMessage(Component.translatable("ttmisc.connector.interffail"));
                    return InteractionResult.CONSUME;
                }

                if (Math.abs(origin.getX() - pos.getX()) > trans.getMaxDistance()
                        || Math.abs(origin.getY() - pos.getY()) > trans.getMaxDistance()
                        || Math.abs(origin.getZ() - pos.getZ()) > trans.getMaxDistance()) {
                    player.sendSystemMessage(Component.translatable("ttmisc.connector.toofar"));
                    return InteractionResult.CONSUME;
                }

                trans.setTarget(pos);

                clearTarget(stack);

                playSound(level, pos);
                player.sendSystemMessage(Component.translatable("ttmisc.connector.complete"));
                // 1.7.10 marked the new target for an update; the device itself is sent as well, so that clients see the
                // binding (their tubes and pipes ask the interface).
                BlockState targetState = level.getBlockState(pos);
                level.sendBlockUpdated(pos, targetState, targetState, Block.UPDATE_ALL);
                BlockState deviceState = trans.getBlockState();
                level.sendBlockUpdated(trans.getBlockPos(), deviceState, deviceState, Block.UPDATE_ALL);
            }
        }

        return InteractionResult.CONSUME;
    }

    /** {@code itemInteractionForEntity}: sneak-click a golem to bind the remembered Golem Connector to it. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!player.isShiftKeyDown() || !isGolem(target)) return InteractionResult.PASS;
        Level level = target.level();

        GlobalPos remembered = getTarget(stack);
        if (remembered == null) {
            if (level.isClientSide) return InteractionResult.PASS;
            player.sendSystemMessage(Component.translatable("ttmisc.golemconnector.notinterf"));
            return InteractionResult.CONSUME;
        }
        BlockEntity tile1 = rememberedTile(level, remembered); // loads the chunk, as 1.7.10 did (see useOn)
        if (!(tile1 instanceof TileGolemConnector trans)) {
            clearTarget(stack);
            if (level.isClientSide) return InteractionResult.PASS;
            player.sendSystemMessage(Component.translatable("ttmisc.golemconnector.notpresent"));
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        trans.ConnectGolem(target.getUUID());

        clearTarget(stack);

        playSound(level, target.blockPosition());
        player.sendSystemMessage(Component.translatable("ttmisc.golemconnector.complete"));
        BlockState state = trans.getBlockState();
        level.sendBlockUpdated(trans.getBlockPos(), state, state, Block.UPDATE_ALL);
        return InteractionResult.CONSUME;
    }

    /** {@code instanceof EntityGolemBase}: a Thaumcraft golem, recognised by its entity type key of the API. */
    public static boolean isGolem(Entity entity) {
        return ThaumcraftEntities.is(entity, ThaumcraftEntities.GOLEM);
    }

    /** 1.7.10 {@code random.orb}, volume 0.8. */
    private void playSound(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1F);
        }
    }
}
