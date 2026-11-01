/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): GameTest support, a loader's energy API as the shared checks see
 * it. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.testing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * What the energy checks of the Transvector Interface ask a loader's energy API, at a position and a face, the way a cable of another
 * mod would ask it: is there a storage, put in, take out, how much is stored, how much fits. The common checks cannot name the APIs
 * (they are the loaders'), so each loader's wrappers hand in their own probe: Fabric, Team Reborn Energy's {@code EnergyStorage.SIDED}
 * (with a transaction per call); NeoForge, {@code Capabilities.EnergyStorage.BLOCK}. Every call looks the storage up again. Where there
 * is no storage, the amounts are 0. The amounts are those of the API, unconverted.
 */
public interface EnergyProbe {
    /** Is there an energy storage at {@code pos} for {@code side}? */
    boolean has(ServerLevel level, BlockPos pos, @Nullable Direction side);

    /** Puts up to {@code amount} in (or simulates it); what went in, 0 without a storage. */
    long insert(ServerLevel level, BlockPos pos, @Nullable Direction side, long amount, boolean simulate);

    /** Takes up to {@code amount} out (or simulates it); what came out, 0 without a storage. */
    long extract(ServerLevel level, BlockPos pos, @Nullable Direction side, long amount, boolean simulate);

    /** What the storage says it holds, 0 without one. */
    long stored(ServerLevel level, BlockPos pos, @Nullable Direction side);

    /** What the storage says it can hold, 0 without one. */
    long capacity(ServerLevel level, BlockPos pos, @Nullable Direction side);
}
