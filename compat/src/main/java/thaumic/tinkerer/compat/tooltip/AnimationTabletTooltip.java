/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the tooltip of the Animation Tablet.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.tooltip;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.block.BlockAnimationTablet;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;
import thaumic.tinkerer.common.lib.LibBlockNames;

/**
 * The Animation Tablet (Tool Dynamism Tablet), as {@code TTinkererProvider} showed it: "Current Tool: &lt;name&gt;" (or
 * "Nothing"), and with a tool the click mode (left / right) and the activation (redstone / autonomous).
 */
public final class AnimationTabletTooltip extends BlockTooltip<TileAnimationTablet> {
    /** The name of the item in the tablet; absent when the tablet is empty. */
    public static final String TAG_TOOL = "tool";
    public static final String TAG_LEFT_CLICK = "leftClick";
    public static final String TAG_REDSTONE = "redstone";

    AnimationTabletTooltip() {
        super(LibBlockNames.ANIMATION_TABLET, BlockAnimationTablet.class, TileAnimationTablet.class);
    }

    @Override
    public void write(TileAnimationTablet tablet, CompoundTag data, HolderLookup.Provider registries) {
        ItemStack stack = tablet.getItem(0);
        if (!stack.isEmpty()) TooltipData.putText(data, TAG_TOOL, stack.getHoverName(), registries);
        data.putBoolean(TAG_LEFT_CLICK, tablet.leftClick);
        data.putBoolean(TAG_REDSTONE, tablet.redstone);
    }

    @Override
    public List<Component> lines(CompoundTag data, HolderLookup.Provider registries) {
        return linesFor(TooltipData.getText(data, TAG_TOOL, registries), data.getBoolean(TAG_LEFT_CLICK), data.getBoolean(TAG_REDSTONE));
    }

    /** The lines for a tablet holding {@code tool} (null: empty). */
    public static List<Component> linesFor(@Nullable Component tool, boolean leftClick, boolean redstone) {
        List<Component> lines = new ArrayList<>(3);
        lines.add(Component.translatable(TooltipKeys.CURRENT_TOOL, tool != null ? tool : Component.translatable(TooltipKeys.NOTHING)));
        if (tool != null) {
            lines.add(Component.translatable(leftClick ? TooltipKeys.LEFT_CLICK : TooltipKeys.RIGHT_CLICK));
            lines.add(Component.translatable(redstone ? TooltipKeys.REDSTONE : TooltipKeys.AUTONOMOUS));
        }
        return lines;
    }
}
