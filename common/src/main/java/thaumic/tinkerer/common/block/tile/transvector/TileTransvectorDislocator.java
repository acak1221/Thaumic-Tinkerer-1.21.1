/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [Nov 24, 2013, 6:48:04 PM (GMT)]
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.block.tile.transvector;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.blocks.BlocksTC;
import thaumic.tinkerer.common.block.camo.PulseDeviceBlock;
import thaumic.tinkerer.common.block.camo.RedstonePulseReceiver;
import thaumic.tinkerer.common.content.TileBlocks;
import thaumic.tinkerer.common.core.fakeplayer.TinkererFakePlayer;
import thaumic.tinkerer.common.hooks.BlockProtection;

/**
 * The Transvector Dislocator: on a redstone pulse it swaps the block in front of it with the block it is bound to (up to
 * sixteen blocks away), block entities with their data, and moves every entity standing in those two blocks to the other
 * one (1.7.10 {@code TileTransvectorDislocator}). The facing is the block state's.
 *
 * <p>New in the port: a block that a player cannot break is not swapped (see {@link #checkBlock}), and the whole swap,
 * blocks and entities, is left out when a player may not change both places ({@link #mayChange}: the world border, spawn
 * protection, the protection mods). The question is asked with a fake player of the Dislocator's own, under the profile
 * of the Remote Placer ({@link TinkererFakePlayer#MINECRAFT_PROFILE}); 1.7.10 asked nobody.</p>
 */
public class TileTransvectorDislocator extends TileTransvector implements RedstonePulseReceiver {

    /** Game ticks between two swaps. */
    private static final int COOLDOWN = 10;

    private int cooldown = 0;
    private boolean pulseStored = false;
    /** The player that asks the protection questions; created the first time it is needed, not saved. */
    @Nullable
    private TinkererFakePlayer player;

    public TileTransvectorDislocator(BlockPos pos, BlockState state) {
        super(TileBlocks.dislocatorType(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileTransvectorDislocator tile) {
        tile.tick();
    }

    private void tick() {
        cooldown = Math.max(0, cooldown - 1);
        if (cooldown == 0 && pulseStored) {
            pulseStored = false;
            receiveRedstonePulse();
        }
    }

    @Override
    public void receiveRedstonePulse() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        getTile(); // sanity check

        BlockPos endCoords = getTarget();
        if (endCoords == null) return;

        if (cooldown > 0) {
            pulseStored = true;
            return;
        }

        // The end must be loaded (1.7.10 loaded it on demand; it still skipped the swap when it was not loaded).
        if (!serverLevel.isLoaded(endCoords)) return;

        BlockPos targetCoords = getBlockTarget();
        List<Entity> entitiesAtEnd = getEntitiesAtPoint(serverLevel, endCoords);
        List<Entity> entitiesAtTarget = getEntitiesAtPoint(serverLevel, targetCoords);

        Vec3 targetToEnd = asVector(targetCoords, endCoords);
        Vec3 endToTarget = asVector(endCoords, targetCoords);

        BlockData endData = new BlockData(serverLevel, endCoords);
        BlockData targetData = new BlockData(serverLevel, targetCoords);

        TinkererFakePlayer fake = player(serverLevel);
        if (!mayChange(serverLevel, fake, targetCoords, targetData.state, endData.state)
                || !mayChange(serverLevel, fake, endCoords, endData.state, targetData.state)) {
            // A protected end: neither the blocks nor the entities change places (moving a mob or a chest out of a claim
            // is as much a change as breaking it).
            cooldown = COOLDOWN;
            return;
        }

        if (checkBlock(serverLevel, targetCoords) && checkBlock(serverLevel, endCoords)) {
            endData.clearBlockEntityAt();
            targetData.clearBlockEntityAt();

            endData.setTo(targetCoords);
            targetData.setTo(endCoords);

            endData.notifyNeighbor(targetCoords);
            targetData.notifyNeighbor(endCoords);
        }

        for (Entity entity : entitiesAtEnd) moveEntity(entity, endToTarget);
        for (Entity entity : entitiesAtTarget) moveEntity(entity, targetToEnd);

        cooldown = COOLDOWN;
    }

    /**
     * {@code checkBlock}: may this block be swapped? Not an aura node, not on the portable hole's blacklist, and breakable
     * by a player: not unbreakable ({@code destroySpeed < 0}: bedrock, barriers, end portal frames, portals) and not a block
     * that only an operator handles ({@link GameMasterBlock}: command, structure and jigsaw blocks). Air passes, so a block
     * can be moved into an empty place.
     *
     * <p>The 1.7.10 expression was {@code (not a node && not blacklisted && hardness != -1) || block != air}: the trailing
     * {@code ||} made it true for every block, so the original swapped nodes, bedrock and the blacklist too. The port drops
     * that operand (the evident intention) and adds the operator blocks, so that the Dislocator cannot take apart what a
     * player cannot (docs/deviations/TileBlocks.md, 2.6).</p>
     */
    public static boolean checkBlock(Level level, BlockPos coords) {
        BlockState state = level.getBlockState(coords);
        return !state.is(BlocksTC.auraNode()) && !ThaumcraftApi.portableHoleBlackList.contains(state.getBlock())
                && state.getDestroySpeed(level, coords) >= 0F && !(state.getBlock() instanceof GameMasterBlock);
    }

    /**
     * May a player take {@code now} away from {@code pos} and put {@code next} there? The game's rules for the place
     * ({@link BlockProtection#gameAllows}, also when both are air: the entities there are moved), then
     * {@link BlockProtection#mayBreak} for a block that goes and {@link BlockProtection#mayPlace} for one that comes.
     */
    private static boolean mayChange(ServerLevel level, TinkererFakePlayer fake, BlockPos pos, BlockState now, BlockState next) {
        if (!BlockProtection.gameAllows(fake, level, pos)) return false;
        if (!now.isAir() && !BlockProtection.mayBreak(fake, level, pos, now)) return false;
        return next.isAir() || BlockProtection.mayPlace(fake, level, pos, next);
    }

    /** The fake player of this Dislocator, standing in it. */
    private TinkererFakePlayer player(ServerLevel serverLevel) {
        if (player == null) player = new TinkererFakePlayer(serverLevel, TinkererFakePlayer.MINECRAFT_PROFILE);
        player.placeAt(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 0F, 0F);
        return player;
    }

    @Override
    public void setRemoved() {
        player = null;
        super.setRemoved();
    }

    private static List<Entity> getEntitiesAtPoint(Level level, BlockPos coords) {
        return level.getEntitiesOfClass(Entity.class, new AABB(coords));
    }

    private static Vec3 asVector(BlockPos source, BlockPos target) {
        return new Vec3(target.getX() - source.getX(), target.getY() - source.getY(), target.getZ() - source.getZ());
    }

    private static void moveEntity(Entity entity, Vec3 vec) {
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(entity.getX() + vec.x, entity.getY() + vec.y, entity.getZ() + vec.z,
                    player.getYRot(), player.getXRot());
        } else {
            entity.setPos(entity.getX() + vec.x, entity.getY() + vec.y, entity.getZ() + vec.z);
        }
    }

    /** The block in front of the dislocator. */
    public BlockPos getBlockTarget() {
        return worldPosition.relative(getFacing());
    }

    public Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(PulseDeviceBlock.FACING) ? state.getValue(PulseDeviceBlock.FACING) : Direction.NORTH;
    }

    @Override
    public int getMaxDistance() {
        return TileBlocks.DISLOCATOR_DISTANCE;
    }

    @Override
    boolean tileRequiredAtLink() {
        return false;
    }

    /** A block with its state and the data of its block entity, as it is at the moment of the swap. */
    private final class BlockData {
        private final ServerLevel world;
        private final BlockState state;
        @Nullable
        private final CompoundTag tile;
        private final BlockPos coords;

        BlockData(ServerLevel world, BlockPos coords) {
            this.world = world;
            this.coords = coords;
            this.state = world.getBlockState(coords);
            BlockEntity entity = world.getBlockEntity(coords);
            this.tile = entity == null ? null : entity.saveWithFullMetadata(world.registryAccess());
        }

        /**
         * Replaces the block entity at the block's place with a fresh one of the same kind, so that taking the block away
         * does not spill the contents of the old one (the data is in {@link #tile}).
         */
        void clearBlockEntityAt() {
            if (state.getBlock() instanceof EntityBlock entityBlock) {
                BlockEntity fresh = entityBlock.newBlockEntity(coords, state);
                if (fresh != null) world.setBlockEntity(fresh);
            }
        }

        void setTo(BlockPos destination) {
            world.setBlock(destination, state, Block.UPDATE_CLIENTS);

            BlockEntity entity = tile == null ? null : BlockEntity.loadStatic(destination, state, tile, world.registryAccess());
            if (entity != null) {
                world.setBlockEntity(entity);
            } else if (world.getBlockEntity(destination) != null) {
                world.removeBlockEntity(destination);
            }
        }

        void notifyNeighbor(BlockPos at) {
            state.handleNeighborChanged(world, at, TileTransvectorDislocator.this.getBlockState().getBlock(), worldPosition, false);
        }
    }
}
