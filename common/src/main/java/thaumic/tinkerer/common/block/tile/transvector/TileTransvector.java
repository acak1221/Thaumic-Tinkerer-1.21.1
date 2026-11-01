/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [Nov 24, 2013, 6:40:49 PM (GMT)]
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.block.tile.transvector;

import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumic.tinkerer.common.block.camo.CamoDeviceBlockEntity;

/**
 * A block entity that is bound to another block by the Transvector Binder (the Transvector Interface and the Dislocator).
 *
 * <p>1.7.10 marked "not bound" with {@code y == -1}, which is a real height in 1.21.1 (the world reaches down to -64), so
 * the binding is an optional position in the saved data ({@code target}, the vanilla position format of
 * {@link NbtUtils#writeBlockPos}; absent when not bound). The binding is dropped when the target is further away than
 * {@link #getMaxDistance()} (unless the "cheaty" flag of the saved data is set), when the target block entity is gone and
 * the device needs one, or when the device may not stand in for the block entity there ({@link #mayBindTo}).</p>
 */
public abstract class TileTransvector extends CamoDeviceBlockEntity {

    private static final String TAG_TARGET = "target";
    private static final String TAG_CHEATY_MODE = "cheatyMode";

    /** Told after the binding of a device changed (see {@link #setBindingListener}); nothing by default. */
    private static Consumer<TileTransvector> bindingListener = device -> {};

    @Nullable
    private BlockPos target;
    private boolean cheaty;

    protected TileTransvector(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** The bound position, or null when the device is not bound. */
    @Nullable
    public final BlockPos getTarget() {
        return target;
    }

    public final boolean isBound() {
        return target != null;
    }

    /** Binds the device to a position (the Transvector Binder checks the distance before it calls this). */
    public final void setTarget(@Nullable BlockPos pos) {
        BlockPos old = target;
        this.target = pos == null ? null : pos.immutable();
        setChanged();
        if (!Objects.equals(old, target)) bindingListener.accept(this);
    }

    /**
     * What a loader module must also do when a binding begins, moves or ends (new in the port). NeoForge: invalidate the
     * capabilities at the device's position, because the Interface's fluid and energy capabilities exist only while it is bound and
     * NeoForge's capability caches must be told (docs/NEOFORGE.md). Runs after the change, on the side that made it.
     */
    public static void setBindingListener(Consumer<TileTransvector> listener) {
        bindingListener = Objects.requireNonNull(listener, "listener");
    }

    @Override
    protected void writeCustomData(CompoundTag tag, HolderLookup.Provider registries) {
        super.writeCustomData(tag, registries);

        if (target != null) tag.put(TAG_TARGET, NbtUtils.writeBlockPos(target));
        tag.putBoolean(TAG_CHEATY_MODE, cheaty);
    }

    @Override
    protected void readCustomData(CompoundTag tag, HolderLookup.Provider registries) {
        super.readCustomData(tag, registries);

        // An update packet carries the whole custom data, so a missing position also unbinds a client.
        target = NbtUtils.readBlockPos(tag, TAG_TARGET).orElse(null);
        cheaty = tag.getBoolean(TAG_CHEATY_MODE);
    }

    /**
     * The block entity the device is bound to, or null. A target outside the loaded chunks answers null and keeps the
     * binding; a target that is too far, missing when it is required, or refused by {@link #mayBindTo} ends the binding
     * (on the server; a client that has not received the target yet keeps it, and is answered null for a refused one).
     */
    @Nullable
    public final BlockEntity getTile() {
        if (target == null || level == null) return null;
        if (!level.isLoaded(target)) return null;

        BlockEntity tile = level.getBlockEntity(target);

        boolean refused = tile != null && !mayBindTo(tile);
        boolean tooFar = Math.abs(target.getX() - worldPosition.getX()) > getMaxDistance()
                || Math.abs(target.getY() - worldPosition.getY()) > getMaxDistance()
                || Math.abs(target.getZ() - worldPosition.getZ()) > getMaxDistance();
        if (!level.isClientSide && (refused || tile == null && tileRequiredAtLink() || tooFar && !cheaty)) {
            target = null;
            setChanged();
            bindingListener.accept(this);
            return null;
        }

        return refused ? null : tile;
    }

    /**
     * May the device stand in for this block entity, the one at its target? Yes by default (the Dislocator needs none);
     * the Interface refuses itself and other Interfaces, which would forward to each other without end.
     */
    protected boolean mayBindTo(BlockEntity tile) {
        return true;
    }

    public abstract int getMaxDistance();

    boolean tileRequiredAtLink() {
        return !cheaty;
    }
}
