/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): GameTest support, a block that stores energy, for the energy
 * checks of the Transvector Interface. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.testing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The energy cell of the GameTests: a block of the test harness ({@link #ID}, in the harness's own namespace, so that no check of the
 * add-on's registry sees it) whose block entity stores energy with a fixed capacity and limits per insert and per extract, and has no
 * storage on one face ({@link #CLOSED_FACE}). Vanilla has no block that stores energy.
 *
 * <p>The cell keeps its amount in plain Java; each loader's harness registers the block and its block entity type (Fabric: the
 * harness's {@code main} entrypoint; NeoForge: {@code RegisterEvent}) and offers the cell through its energy API (Fabric: Team Reborn
 * Energy's {@code EnergyStorage.SIDED}; NeoForge: {@code Capabilities.EnergyStorage.BLOCK}), with the rules of {@link Cell#insert} and
 * {@link Cell#extract}. The checks read the amount from the cell itself, so they see what reached it, whatever API carried it.</p>
 */
public final class TestEnergyCell {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("thaumictinkerer_gametest", "energy_cell");
    public static final long CAPACITY = 1000;
    public static final long MAX_INSERT = 400;
    public static final long MAX_EXTRACT = 250;
    /** The face without a storage: an Interface asked on this face must find none, because it asks the cell for the same face. */
    public static final Direction CLOSED_FACE = Direction.DOWN;

    private static Block block;
    private static BlockEntityType<Cell> type;

    private TestEnergyCell() {}

    /** The block, made on the first call; only call it while the block registry takes entries (the loader's registration). */
    public static synchronized Block block() {
        if (block == null) block = new CellBlock(BlockBehaviour.Properties.of().strength(1.0F));
        return block;
    }

    /** The block entity type, made on the first call (after {@link #block()}, during registration). */
    public static synchronized BlockEntityType<Cell> type() {
        if (type == null) type = BlockEntityType.Builder.of(Cell::new, block()).build(null);
        return type;
    }

    private static final class CellBlock extends Block implements EntityBlock {
        CellBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Cell(pos, state);
        }
    }

    /** The block entity: the amount stored and the rules of a storage with a capacity and limits per operation. */
    public static final class Cell extends BlockEntity {
        private long amount;

        public Cell(BlockPos pos, BlockState state) {
            super(type(), pos, state);
        }

        public long amount() {
            return amount;
        }

        /** Sets the amount (the Fabric storage's snapshot restores it when a transaction is aborted). */
        public void setAmount(long amount) {
            this.amount = Math.max(0, Math.min(CAPACITY, amount));
            setChanged();
        }

        /** Does the cell have a storage on {@code side} (null: the whole cell, which it has)? */
        public boolean hasStorage(@Nullable Direction side) {
            return side != CLOSED_FACE;
        }

        /** Takes up to {@code max} (at most {@link #MAX_INSERT}, at most the room left); returns what it took or would take. */
        public long insert(long max, boolean simulate) {
            long in = Math.max(0, Math.min(MAX_INSERT, Math.min(max, CAPACITY - amount)));
            if (!simulate && in > 0) setAmount(amount + in);
            return in;
        }

        /** Gives up to {@code max} (at most {@link #MAX_EXTRACT}, at most what it holds); returns what it gave or would give. */
        public long extract(long max, boolean simulate) {
            long out = Math.max(0, Math.min(MAX_EXTRACT, Math.min(max, amount)));
            if (!simulate && out > 0) setAmount(amount - out);
            return out;
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putLong("amount", amount);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            amount = tag.getLong("amount");
        }
    }
}
