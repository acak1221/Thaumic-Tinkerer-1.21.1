/**
 * Created by pixlepix on 7/25/14.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.block.tile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.ThaumcraftFX;
import thaumcraft.api.TileThaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumic.tinkerer.common.content.SimpleBlocks;
import thaumic.tinkerer.common.item.InfusedSeedComponents;

/**
 * The block entity of an infused crop: its aspect, its primal tendencies (at most fifty points), the growth of the
 * crop (the crop does not grow by random ticks) and the exchange of tendencies with the crops next to it.
 *
 * <p>Bounds (fix, docs/deviations/SimpleBlocks.md 2.6): every amount of the tendencies is kept in {@code 0..50} and their
 * sum at fifty at most. {@link #reduceSaturatedAspects} clamps and trims, the saved amounts are clamped when they are
 * read (and trimmed on the next tick), and {@link #tendency} is the clamped amount the growth and the harvest read, so
 * that a list somebody filled by hand cannot drive the harvest loops without end.</p>
 */
public class TileInfusedGrain extends TileThaumcraft implements IAspectContainer {

    /** The most points the tendencies hold in all, and the most one aspect holds. */
    public static final int MAX_TENDENCIES = InfusedSeedComponents.MAX_AMOUNT;

    private final String NBT_MAIN_ASPECT = "mainAspect";
    private final String NBT_ASPEPCT_TENDENCIES = "aspectTendencies";
    @Nullable
    public Aspect aspect;
    public AspectList primalTendencies = new AspectList();
    /** Set when saved tendencies above the cap were read: the next server tick trims them (no level random while loading). */
    private boolean trimOnTick;

    public TileInfusedGrain(BlockPos pos, BlockState state) {
        super(SimpleBlocks.TILE_INFUSED_GRAIN.get(), pos, state);
    }

    /** The amount of a tendency as the growth and the harvest read it: clamped to {@code 0..}{@link #MAX_TENDENCIES}. */
    public int tendency(Aspect aspect) {
        return InfusedSeedComponents.clamp(primalTendencies.getAmount(aspect));
    }

    /** The aspect of the crop; the air aspect while none was set (1.7.10 gave it to the tile on its first tick). */
    public Aspect aspectOrAir() {
        return aspect == null ? Aspect.AIR : aspect;
    }

    /**
     * 1.7.10 {@code updateEntity}, on the server (the particles of the exchange are sent by the server as Thaumcraft's
     * essentia trail, where 1.7.10 drew them on the client's own roll of the dice).
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, TileInfusedGrain tile) {
        RandomSource rand = level.random;
        if (tile.trimOnTick) {
            tile.trimOnTick = false;
            tile.reduceSaturatedAspects(rand);
            tile.setChanged();
        }

        // Growth. 1.7.10 getBlockLightValue subtracts the sky darkening of the time of day (skylightSubtracted), so a crop
        // under open sky does not grow at night: the sky light of the cell above, less the current darkening, or the
        // block light, whichever is brighter.
        if (level.getRawBrightness(pos.above(), level.getSkyDarken()) >= 9) {
            int l = state.getValue(CropBlock.AGE);
            if (l < 7) {
                int water = tile.tendency(Aspect.WATER);
                if (rand.nextInt(Math.max(1, (2510 - (int) Math.pow(water, 2)) * 6)) == 0) {
                    ++l;
                    level.setBlock(pos, state.setValue(CropBlock.AGE, l), 3);
                    tile.setChanged();
                }
            }
        }
        if (tile.aspect == null) {
            tile.aspect = Aspect.AIR;
        }
        // Aspect Exchange
        if (rand.nextInt(Math.max(1, (2550 - (int) Math.pow(tile.tendency(Aspect.AIR), 2)) * 10)) == 0
                && !tile.aspect.isPrimal()) {

            for (Direction dir : Direction.values()) {
                BlockPos neighbour = pos.relative(dir);
                if (!level.isLoaded(neighbour)) continue;
                if (level.getBlockEntity(neighbour) instanceof TileInfusedGrain other) {
                    // Exchange aspects
                    Aspect otherAspect = other.aspectOrAir();
                    if (otherAspect.isPrimal()) {
                        if (tile.primalTendencies.getAmount(otherAspect) < 5) {
                            tile.primalTendencies.add(otherAspect, 1);
                            tile.reduceSaturatedAspects(rand);

                            tile.sync(level, pos);
                            trail(level, pos, neighbour, otherAspect);
                            return;
                        }
                    } else {
                        AspectList targetList = other.primalTendencies;
                        if (targetList.getAspects().length == 0 || targetList.getAspects()[0] == null) {
                            return;
                        }

                        Aspect moved = targetList.getAspects()[rand.nextInt(targetList.getAspects().length)];
                        if (targetList.getAmount(moved) >= tile.primalTendencies.getAmount(moved)) {
                            tile.primalTendencies.add(moved, 1);
                            targetList.reduce(moved, 1);
                            tile.reduceSaturatedAspects(rand);
                            trail(level, pos, neighbour, moved);

                            tile.sync(level, pos);
                            other.setChanged();
                        }
                        return;
                    }
                }
            }
        }
    }

    private static void trail(Level level, BlockPos consumer, BlockPos source, Aspect aspect) {
        if (level instanceof ServerLevel server) ThaumcraftFX.essentiaTrail(server, consumer, source, aspect);
    }

    /** 1.7.10 {@code markBlockForUpdate}. */
    private void sync(Level level, BlockPos pos) {
        setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }

    /** {@link #reduceSaturatedAspects(RandomSource)} with the random source of the tile's level (a new one without a level). */
    public void reduceSaturatedAspects() {
        Level level = getLevel();
        reduceSaturatedAspects(level != null ? level.random : RandomSource.create());
    }

    /**
     * 1.7.10 {@code reduceSaturatedAspects}: while the tendencies hold more than fifty points, one point of a random
     * aspect is removed (an aspect that reaches zero leaves the list, as before). Fixes: every amount is clamped to
     * {@code 0..50} first (so the sum cannot overflow and the loop runs at most fifty times per aspect), the dice are the
     * given random source (1.7.10 made a {@code new Random()} per point), and only an aspect that still holds a point is
     * drawn (1.7.10 also drew aspects of zero, which removed nothing and left the sum above fifty).
     */
    public void reduceSaturatedAspects(RandomSource rand) {
        clampTendencies(primalTendencies);
        long sum = sum(primalTendencies);
        while (sum > MAX_TENDENCIES) {
            List<Aspect> held = new ArrayList<>();
            for (Map.Entry<Aspect, Integer> entry : primalTendencies.aspects.entrySet()) {
                if (entry.getKey() != null && entry.getValue() > 0) held.add(entry.getKey());
            }
            if (held.isEmpty()) break;
            primalTendencies.remove(held.get(rand.nextInt(held.size())), 1);
            sum--;
        }
    }

    /** Clamps every amount of the list to {@code 0..50} in place (a missing amount becomes zero). */
    static void clampTendencies(AspectList list) {
        for (Map.Entry<Aspect, Integer> entry : list.aspects.entrySet()) {
            Integer amount = entry.getValue();
            int clamped = InfusedSeedComponents.clamp(amount);
            if (amount == null || amount != clamped) entry.setValue(clamped);
        }
    }

    /** The sum of the amounts of the aspects of the list (a long, the amounts may be anything). */
    static long sum(AspectList list) {
        long sum = 0;
        for (Map.Entry<Aspect, Integer> entry : list.aspects.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) sum += entry.getValue();
        }
        return sum;
    }

    @Override
    public void writeCustomNBT(CompoundTag nbt) {
        super.writeCustomNBT(nbt);
        CompoundTag aspectCompound = new CompoundTag();
        new AspectList().add(aspect, 1).writeToNBT(aspectCompound);
        nbt.put(NBT_MAIN_ASPECT, aspectCompound);

        CompoundTag tendencyCompound = new CompoundTag();
        primalTendencies.writeToNBT(tendencyCompound);
        nbt.put(NBT_ASPEPCT_TENDENCIES, tendencyCompound);
    }

    @Override
    public void readCustomNBT(CompoundTag nbt) {
        super.readCustomNBT(nbt);
        AspectList aspectList = new AspectList();
        aspectList.readFromNBT(nbt.getCompound(NBT_MAIN_ASPECT));
        Aspect[] read = aspectList.getAspects();
        aspect = read.length > 0 ? read[0] : null;
        aspectList.readFromNBT(nbt.getCompound(NBT_ASPEPCT_TENDENCIES));
        clampTendencies(aspectList);
        primalTendencies = aspectList;
        trimOnTick = sum(aspectList) > MAX_TENDENCIES;
    }

    @Override
    public AspectList getAspects() {
        return new AspectList().add(aspect, 1);
    }

    @Override
    public void setAspects(AspectList paramAspectList) {}

    @Override
    public boolean doesContainerAccept(Aspect paramAspect) {

        return false;
    }

    @Override
    public int addToContainer(Aspect paramAspect, int paramInt) {
        return 0;
    }

    @Override
    public boolean takeFromContainer(Aspect paramAspect, int paramInt) {
        return false;
    }

    @Override
    @Deprecated
    public boolean takeFromContainer(AspectList paramAspectList) {
        return false;
    }

    @Override
    public boolean doesContainerContainAmount(Aspect paramAspect, int paramInt) {
        return false;
    }

    @Override
    @Deprecated
    public boolean doesContainerContain(AspectList paramAspectList) {
        return false;
    }

    @Override
    public int containerContains(Aspect paramAspect) {
        return 0;
    }
}
