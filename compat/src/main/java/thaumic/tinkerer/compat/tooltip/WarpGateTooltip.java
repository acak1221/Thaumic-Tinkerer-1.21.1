/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the tooltip of the Celestial Gateway.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.tooltip;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import thaumic.tinkerer.common.block.kami.BlockWarpGate;
import thaumic.tinkerer.common.block.tile.kami.TileWarpGate;
import thaumic.tinkerer.common.lib.LibBlockNames;

/**
 * The Celestial Gateway (Warp Gate): whether it accepts incoming teleports.
 *
 * <p><b>Deviation:</b> {@code TTinkererProvider} showed "Allows incoming teleports" when the gate's {@code locked} flag was
 * true and "Doesn't allow incoming teleports" when it was false, the opposite of what the flag does (a locked gate refuses
 * every teleport to it). Here the line tells the truth: locked shows {@code ttwaila.disallowIncoming}, not locked
 * {@code ttwaila.allowIncoming} (docs/deviations/Tooltips.md).</p>
 */
public final class WarpGateTooltip extends BlockTooltip<TileWarpGate> {
    public static final String TAG_LOCKED = "locked";

    WarpGateTooltip() {
        super(LibBlockNames.WARP_GATE, BlockWarpGate.class, TileWarpGate.class);
    }

    @Override
    public void write(TileWarpGate gate, CompoundTag data, HolderLookup.Provider registries) {
        data.putBoolean(TAG_LOCKED, gate.locked);
    }

    @Override
    public List<Component> lines(CompoundTag data, HolderLookup.Provider registries) {
        return linesFor(data.getBoolean(TAG_LOCKED));
    }

    /** The line for a gate whose {@code locked} flag is {@code locked}. */
    public static List<Component> linesFor(boolean locked) {
        return List.of(Component.translatable(locked ? TooltipKeys.DISALLOW_INCOMING : TooltipKeys.ALLOW_INCOMING));
    }
}
