/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the tooltip of the Transvector Interface.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.tooltip;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.block.transvector.BlockTransvectorInterface;
import thaumic.tinkerer.common.lib.LibBlockNames;

/**
 * The Transvector Interface, as {@code TTinkererProvider} showed it: "Connected to: &lt;block&gt;" (or "Nothing") and the
 * position of the bound block. As in 1.7.10 the bound block is the block entity that {@code getTile()} answers, so an
 * interface whose target is gone (or out of range, or in an unloaded chunk) shows "Nothing"; on the server that call is
 * also what ends a binding that is no longer valid.
 */
public final class TransvectorInterfaceTooltip extends BlockTooltip<TileTransvectorInterface> {
    /** The registry id of the bound block; absent when the interface stands in for nothing. */
    public static final String TAG_BLOCK = "block";
    public static final String TAG_X = "x";
    public static final String TAG_Y = "y";
    public static final String TAG_Z = "z";

    TransvectorInterfaceTooltip() {
        super(LibBlockNames.INTERFACE, BlockTransvectorInterface.class, TileTransvectorInterface.class);
    }

    @Override
    public void write(TileTransvectorInterface iface, CompoundTag data, HolderLookup.Provider registries) {
        BlockEntity tile = iface.getTile();
        if (tile == null) return;
        data.putString(TAG_BLOCK, BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock()).toString());
        BlockPos pos = tile.getBlockPos();
        data.putInt(TAG_X, pos.getX());
        data.putInt(TAG_Y, pos.getY());
        data.putInt(TAG_Z, pos.getZ());
    }

    @Override
    public List<Component> lines(CompoundTag data, HolderLookup.Provider registries) {
        if (!data.contains(TAG_BLOCK)) return linesFor(null, null);
        return linesFor(blockName(data.getString(TAG_BLOCK)), new BlockPos(data.getInt(TAG_X), data.getInt(TAG_Y), data.getInt(TAG_Z)));
    }

    /** The lines for an interface bound to a block called {@code block} at {@code pos} (null: bound to nothing). */
    public static List<Component> linesFor(@Nullable Component block, @Nullable BlockPos pos) {
        if (block == null || pos == null) {
            return List.of(Component.translatable(TooltipKeys.CONNECTED, Component.translatable(TooltipKeys.NOTHING)));
        }
        return List.of(Component.translatable(TooltipKeys.CONNECTED, block), coordinates(pos));
    }

    /** The second line, a literal text in 1.7.10 too: {@code x: %d y: %d z: %d}. */
    public static Component coordinates(BlockPos pos) {
        return Component.literal(String.format(Locale.ROOT, "x: %d y: %d z: %d", pos.getX(), pos.getY(), pos.getZ()));
    }

    /** The name of a block by its registry id; the id itself when the block is unknown. */
    public static Component blockName(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null && BuiltInRegistries.BLOCK.containsKey(location)) return BuiltInRegistries.BLOCK.get(location).getName();
        return Component.literal(id);
    }
}
