/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [11 Sep 2013, 17:46:38 (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchPage;
import thaumic.tinkerer.common.block.BlockGas;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.lib.LibResearch;
import thaumic.tinkerer.common.registry.ITTinkererRegisterable;
import thaumic.tinkerer.common.registry.TTRegistry;
import thaumic.tinkerer.common.registry.ThaumicTinkererCrucibleRecipe;
import thaumic.tinkerer.common.registry.ThaumicTinkererRecipe;
import thaumic.tinkerer.common.research.IRegisterableResearch;
import thaumic.tinkerer.common.research.ResearchHelper;
import thaumic.tinkerer.common.research.TTResearchItem;

/**
 * The Hyperenergetic Nitor: while it is in a player's inventory it leaves a cell of Nitor Gas at the player's head
 * every tick (see {@code BlockNitorGas}).
 */
public class ItemBrightNitor extends Item implements ITTinkererRegisterable {

    public ItemBrightNitor(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * 1.7.10 {@code setBlock(x, y, z, world)}: places a cell of Nitor Gas of strength {@code gasMeta} (0 weak, 1 strong) where there
     * is air or Nitor Gas. 1.7.10 read the strength from a public static {@code meta}, which the Kami leggings set around a call of
     * {@code onUpdate}; the leggings place their strong gas themselves here, so the item places the weak one only.
     */
    public static void setBlock(Level level, BlockPos pos, int gasMeta) {
        placeGas(level, pos, gasMeta);
    }

    /** Same as {@link #setBlock(Level, BlockPos, int)}; returns whether the cell was set. */
    public static boolean placeGas(Level level, BlockPos pos, int gasMeta) {
        if (level.isClientSide) return false;
        Block nitor = TTRegistry.block(LibBlockNames.NITOR_GAS);
        BlockState there = level.getBlockState(pos);
        if (there.isAir() || there.is(nitor)) {
            // flag 2 of 1.7.10 (send to clients); onPlace schedules the first tick
            level.setBlock(pos, nitor.defaultBlockState().setValue(BlockGas.LEVEL, gasMeta), Block.UPDATE_CLIENTS);
            return true;
        }
        return false;
    }

    @Override
    public IRegisterableResearch getResearchItem() {
        return (TTResearchItem) new TTResearchItem(
                LibResearch.KEY_BRIGHT_NITOR,
                new AspectList().add(Aspect.LIGHT, 2).add(Aspect.FIRE, 1).add(Aspect.ENERGY, 1).add(Aspect.AIR, 1),
                1,
                -5,
                2,
                new ItemStack(this))
                        .setParents(LibResearch.KEY_GASEOUS_LIGHT).setConcealed()
                        .setPages(
                                new ResearchPage("0"),
                                ResearchHelper.crucibleRecipePage(LibResearch.KEY_BRIGHT_NITOR))
                        .setSecondary();
    }

    @Override
    public ThaumicTinkererRecipe getRecipeItem() {
        // itemResource:1 of 1.7.10, the Nitor
        return new ThaumicTinkererCrucibleRecipe(
                LibResearch.KEY_BRIGHT_NITOR,
                new ItemStack(this),
                ItemsTC.stack("nitor"),
                new AspectList().add(Aspect.ENERGY, 25).add(Aspect.LIGHT, 25).add(Aspect.AIR, 10).add(Aspect.FIRE, 10));
    }

    /** 1.7.10 {@code onUpdate}: the weak gas goes into the cell above the holder's feet. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        placeNear(level, entity, 0);
    }

    /** What {@code onUpdate} did, with the strength given instead of read from 1.7.10's static {@code meta}. */
    public static void placeNear(Level level, Entity entity, int gasMeta) {
        int x = Mth.floor(entity.getX());
        int y = Mth.floor(entity.getY()) + 1;
        int z = Mth.floor(entity.getZ());
        setBlock(level, new BlockPos(x, y, z), gasMeta);
    }
}
