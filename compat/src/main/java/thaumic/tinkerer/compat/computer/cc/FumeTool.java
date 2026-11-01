/**
 * Created by Katrina on 04/02/2015.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer.cc;

import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleCommandResult;
import dan200.computercraft.api.turtle.TurtleSide;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.turtle.TurtleVerb;
import dan200.computercraft.api.upgrades.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import thaumic.tinkerer.common.block.BlockGas;

/**
 * The turtle tool of the Fume Dissipator ("Dissipating Turtle"), 1.7.10 {@code FumeTool}: a tool upgrade (upgrade id 171 in
 * 1.7.10; {@code thaumictinkerer:gas_remover} now, a data file of {@code data/thaumictinkerer/computercraft/turtle_upgrade}),
 * crafted onto a turtle with the Fume Dissipator item and drawn as that item ({@code TurtleUpgradeModeller.flatItem()}, the
 * 1.7.10 icon was the item's texture). The adjective is the original key {@code ttcomputer.dissipator}.
 *
 * <p>{@code turtle.dig()} (also {@code digUp}, {@code digDown}: the direction is ignored, as in 1.7.10) dissolves every gas of
 * the add-on in the cube from the turtle's position minus three to plus two on each axis (the 1.7.10 loops ran to
 * {@code < xs + 3}), with the gas's particle, and answers true; the Fume Dissipator item does the same for a sneaking player.
 * {@code turtle.attack()} fails. No sound, as in 1.7.10 (its sound call was commented out).</p>
 *
 * <p>Not ported: {@code FumePeripheral} (type {@code gasRemover}, no methods). {@code FumeTool.createPeripheral} answered null,
 * so 1.7.10 never created it, and a tool upgrade has no peripheral in CC: Tweaked either.</p>
 */
public final class FumeTool implements ITurtleUpgrade {
    /** The adjective of 1.7.10 {@code getUnlocalisedAdjective()}. */
    public static final String ADJECTIVE = "ttcomputer.dissipator";

    private final ItemStack item;

    /** @param item the crafting item, the {@code item} of the upgrade's data file (the Fume Dissipator) */
    public FumeTool(ItemStack item) {
        this.item = item;
    }

    @Override
    public UpgradeType<FumeTool> getType() {
        return ComputerCraftCompat.gasRemoverType();
    }

    @Override
    public TurtleUpgradeType getUpgradeType() {
        return TurtleUpgradeType.TOOL;
    }

    @Override
    public Component getAdjective() {
        return Component.translatable(ADJECTIVE);
    }

    @Override
    public ItemStack getCraftingItem() {
        return item;
    }

    @Override
    public TurtleCommandResult useTool(ITurtleAccess turtle, TurtleSide side, TurtleVerb verb, Direction direction) {
        if (verb != TurtleVerb.DIG) return TurtleCommandResult.failure();
        dissipate(turtle.getLevel(), turtle.getPosition());
        return TurtleCommandResult.success();
    }

    /** The loops of {@code useTool}: every gas in {@code pos - 3 .. pos + 2} becomes air, with its particle. */
    public static int dissipate(Level level, BlockPos origin) {
        int removed = 0;
        for (int x = origin.getX() - 3; x < origin.getX() + 3; x++) {
            for (int y = origin.getY() - 3; y < origin.getY() + 3; y++) {
                for (int z = origin.getZ() - 3; z < origin.getZ() + 3; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof BlockGas gas) {
                        gas.placeParticle(level, pos);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
                        removed++;
                    }
                }
            }
        }
        return removed;
    }
}
