/**
 * Created by pixlepix on 8/4/14.
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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.TileThaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumic.tinkerer.common.content.SimpleBlocks;

/**
 * The magical residue of the infused soil: the aspects the crops left in it. The soil holds at most
 * {@link #MAX_ASPECTS} points; the aspects are shown by the goggles ({@link IAspectContainer}) and nobody can put in
 * or take out essentia.
 */
public class TileInfusedFarmland extends TileThaumcraft implements IAspectContainer {

    public static final int MAX_ASPECTS = 20;
    public static final String NBT_ASPECT_LIST = "aspectList";
    public AspectList aspectList = new AspectList();

    public TileInfusedFarmland(BlockPos pos, BlockState state) {
        super(SimpleBlocks.TILE_INFUSED_FARMLAND.get(), pos, state);
    }

    @Override
    public void readCustomNBT(CompoundTag nbt) {
        super.readCustomNBT(nbt);
        aspectList.readFromNBT(nbt.getCompound(NBT_ASPECT_LIST));
    }

    @Override
    public void writeCustomNBT(CompoundTag nbt) {
        super.writeCustomNBT(nbt);
        CompoundTag compound = new CompoundTag();

        aspectList.writeToNBT(compound);
        nbt.put(NBT_ASPECT_LIST, compound);
    }

    /** {@link #reduceSaturatedAspects(RandomSource)} with the random source of the tile's level (a new one without a level). */
    public void reduceSaturatedAspects() {
        Level level = getLevel();
        reduceSaturatedAspects(level != null ? level.random : RandomSource.create());
    }

    /**
     * Ensures that the farmland only holds a maximum of 20 aspects: while it holds more, one point of a random aspect is
     * removed. Fixes, as for the crop ({@code TileInfusedGrain.reduceSaturatedAspects}): the amounts are clamped to
     * {@code 0..20} first and summed as a long, the dice are the given random source (1.7.10 made a {@code new Random()}
     * per point), and only an aspect that still holds a point is drawn.
     */
    public void reduceSaturatedAspects(RandomSource rand) {
        long sum = 0;
        for (Map.Entry<Aspect, Integer> entry : aspectList.aspects.entrySet()) {
            Integer amount = entry.getValue();
            int clamped = amount == null ? 0 : Math.max(0, Math.min(MAX_ASPECTS, amount));
            if (amount == null || amount != clamped) entry.setValue(clamped);
            if (entry.getKey() != null) sum += clamped;
        }
        while (sum > MAX_ASPECTS) {
            List<Aspect> held = new ArrayList<>();
            for (Map.Entry<Aspect, Integer> entry : aspectList.aspects.entrySet()) {
                if (entry.getKey() != null && entry.getValue() > 0) held.add(entry.getKey());
            }
            if (held.isEmpty()) break;
            aspectList.remove(held.get(rand.nextInt(held.size())), 1);
            sum--;
        }
    }

    @Override
    public AspectList getAspects() {
        return aspectList;
    }

    @Override
    public void setAspects(AspectList aspectList) {
        this.aspectList = aspectList;
    }

    @Override
    public boolean doesContainerAccept(Aspect aspect) {
        return false;
    }

    @Override
    public int addToContainer(Aspect aspect, int i) {
        return 0;
    }

    @Override
    public boolean takeFromContainer(Aspect aspect, int i) {
        return false;
    }

    @Override
    @Deprecated
    public boolean takeFromContainer(AspectList aspectList) {
        return false;
    }

    @Override
    public boolean doesContainerContainAmount(Aspect aspect, int i) {
        return false;
    }

    @Override
    @Deprecated
    public boolean doesContainerContain(AspectList aspectList) {
        return false;
    }

    @Override
    public int containerContains(Aspect aspect) {
        return 0;
    }
}
