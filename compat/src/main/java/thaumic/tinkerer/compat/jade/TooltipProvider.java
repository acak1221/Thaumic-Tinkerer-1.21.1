/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the Jade provider of one block tooltip.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import thaumic.tinkerer.compat.tooltip.BlockTooltip;

/**
 * Glue only: the server half hands the looked-at block entity to {@link BlockTooltip#appendServerData}, the client half
 * adds {@link BlockTooltip#linesFromServerData} to the body. One object for both halves with one uid, the way Jade's own
 * providers are written; the uid is also the on/off option in Jade's plugin settings.
 */
final class TooltipProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private final BlockTooltip<?> tooltip;

    TooltipProvider(BlockTooltip<?> tooltip) {
        this.tooltip = tooltip;
    }

    BlockTooltip<?> tooltip() {
        return tooltip;
    }

    @Override
    public ResourceLocation getUid() {
        return tooltip.uid();
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        tooltip.appendServerData(data, accessor.getBlockEntity(), accessor.getLevel().registryAccess());
    }

    @Override
    public void appendTooltip(ITooltip lines, BlockAccessor accessor, IPluginConfig config) {
        for (Component line : tooltip.linesFromServerData(accessor.getServerData(), accessor.getLevel().registryAccess())) {
            lines.add(line);
        }
    }
}
