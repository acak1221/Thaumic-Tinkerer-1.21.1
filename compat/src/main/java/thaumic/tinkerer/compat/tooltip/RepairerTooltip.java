/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the tooltip of the Thaumic Restorer.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.tooltip;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.block.BlockRepairer;
import thaumic.tinkerer.common.block.tile.TileRepairer;
import thaumic.tinkerer.common.lib.LibBlockNames;

/**
 * The Thaumic Restorer, as {@code TTinkererProvider} showed it: "Repairing: &lt;item&gt;" while the item in the slot has
 * damage, "Finished repairing: &lt;item&gt;" when it has none, no line when the slot is empty.
 */
public final class RepairerTooltip extends BlockTooltip<TileRepairer> {
    /** The name of the item in the slot; absent when the slot is empty. */
    public static final String TAG_ITEM = "item";
    /** Whether that item has damage (1.7.10 {@code getItemDamage() > 0}). */
    public static final String TAG_DAMAGED = "damaged";

    RepairerTooltip() {
        super(LibBlockNames.REPAIRER, BlockRepairer.class, TileRepairer.class);
    }

    @Override
    public void write(TileRepairer repairer, CompoundTag data, HolderLookup.Provider registries) {
        ItemStack stack = repairer.getItem(0);
        if (stack.isEmpty()) return;
        TooltipData.putText(data, TAG_ITEM, stack.getHoverName(), registries);
        data.putBoolean(TAG_DAMAGED, stack.getDamageValue() > 0);
    }

    @Override
    public List<Component> lines(CompoundTag data, HolderLookup.Provider registries) {
        return linesFor(TooltipData.getText(data, TAG_ITEM, registries), data.getBoolean(TAG_DAMAGED));
    }

    /** The lines for a restorer holding an item called {@code item} (null: empty). */
    public static List<Component> linesFor(@Nullable Component item, boolean damaged) {
        if (item == null) return List.of();
        return List.of(Component.translatable(damaged ? TooltipKeys.REPAIRING : TooltipKeys.FINISHED_REPAIRING, item));
    }
}
