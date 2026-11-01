/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the Fabric GameTest harness, test-only. Distributed under the same
 * licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.fabric.testing;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;
import thaumic.tinkerer.testing.EnergyProbe;
import thaumic.tinkerer.testing.TestEnergyCell;

/**
 * Team Reborn Energy in the Fabric harness: the energy storage of the test cell ({@link TestEnergyCell}) and the probe that the energy
 * checks of the Transvector Interface ask ({@link #PROBE}). Only loaded when Team Reborn Energy is installed (the GameTest run has it,
 * unless {@code -PwithoutTeamRebornEnergy}); {@link TinkererGameTestSetup} and {@link TileBlocksGameTests} check that first.
 */
final class TeamRebornEnergyTesting {
    /** One storage per cell, so that a transaction's snapshots belong to one participant. */
    private static final Map<TestEnergyCell.Cell, CellStorage> STORAGES = new WeakHashMap<>();

    private TeamRebornEnergyTesting() {}

    /** The cell's lookup: a storage on every face but {@link TestEnergyCell#CLOSED_FACE}, and for no face. */
    static void registerCell() {
        EnergyStorage.SIDED.registerForBlockEntity((cell, side) -> cell.hasStorage(side) ? storageOf(cell) : null, TestEnergyCell.type());
    }

    private static synchronized CellStorage storageOf(TestEnergyCell.Cell cell) {
        return STORAGES.computeIfAbsent(cell, CellStorage::new);
    }

    /** Team Reborn Energy's {@code EnergyStorage.SIDED}, asked anew for every call; one outer transaction per call, committed unless simulated. */
    static final EnergyProbe PROBE = new EnergyProbe() {
        @Nullable
        private EnergyStorage find(ServerLevel level, BlockPos pos, @Nullable Direction side) {
            return EnergyStorage.SIDED.find(level, pos, side);
        }

        @Override
        public boolean has(ServerLevel level, BlockPos pos, @Nullable Direction side) {
            return find(level, pos, side) != null;
        }

        @Override
        public long insert(ServerLevel level, BlockPos pos, @Nullable Direction side, long amount, boolean simulate) {
            EnergyStorage storage = find(level, pos, side);
            if (storage == null) return 0;
            try (Transaction tx = Transaction.openOuter()) {
                long in = storage.insert(amount, tx);
                if (!simulate) tx.commit();
                return in;
            }
        }

        @Override
        public long extract(ServerLevel level, BlockPos pos, @Nullable Direction side, long amount, boolean simulate) {
            EnergyStorage storage = find(level, pos, side);
            if (storage == null) return 0;
            try (Transaction tx = Transaction.openOuter()) {
                long out = storage.extract(amount, tx);
                if (!simulate) tx.commit();
                return out;
            }
        }

        @Override
        public long stored(ServerLevel level, BlockPos pos, @Nullable Direction side) {
            EnergyStorage storage = find(level, pos, side);
            return storage == null ? 0 : storage.getAmount();
        }

        @Override
        public long capacity(ServerLevel level, BlockPos pos, @Nullable Direction side) {
            EnergyStorage storage = find(level, pos, side);
            return storage == null ? 0 : storage.getCapacity();
        }
    };

    /** The cell as a Team Reborn Energy storage: the cell's rules, its amount restored when a transaction is aborted. */
    private static final class CellStorage extends SnapshotParticipant<Long> implements EnergyStorage {
        private final TestEnergyCell.Cell cell;

        CellStorage(TestEnergyCell.Cell cell) {
            this.cell = cell;
        }

        @Override
        protected Long createSnapshot() {
            return cell.amount();
        }

        @Override
        protected void readSnapshot(Long snapshot) {
            cell.setAmount(snapshot);
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            long in = cell.insert(maxAmount, true);
            if (in > 0) {
                updateSnapshots(transaction);
                cell.insert(in, false);
            }
            return in;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            long out = cell.extract(maxAmount, true);
            if (out > 0) {
                updateSnapshots(transaction);
                cell.extract(out, false);
            }
            return out;
        }

        @Override
        public long getAmount() {
            return cell.amount();
        }

        @Override
        public long getCapacity() {
            return TestEnergyCell.CAPACITY;
        }
    }
}
