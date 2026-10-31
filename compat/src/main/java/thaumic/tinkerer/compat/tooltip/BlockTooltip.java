/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the tooltip data of one block, shared by the
 * Jade plugin and the tests. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.tooltip;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * The body lines that the 1.7.10 Waila providers ({@code TTinkererProvider}, {@code MagnetProvider}) showed for one
 * block, split in two halves so that they can be checked without a tooltip mod:
 * <ul>
 *   <li>{@link #write}: on the server, what the lines need from the block entity, into a tag (the client does not
 *       always have the inventory, the mode or the binding of a block entity);</li>
 *   <li>{@link #lines}: on the client, the lines from that tag.</li>
 * </ul>
 *
 * <p>A tooltip mod sends one tag per looked-at block for all its providers, so {@link #appendServerData} puts this
 * tooltip's tag under its {@link #uid()} and {@link #linesFromServerData} reads it from there; a tag without it (a
 * server that does not have the add-on's plugin) gives no line.</p>
 *
 * @param <T> the block entity the data comes from
 */
public abstract class BlockTooltip<T extends BlockEntity> {
    private final String name;
    private final Class<? extends Block> blockClass;
    private final Class<T> blockEntityClass;

    protected BlockTooltip(String name, Class<? extends Block> blockClass, Class<T> blockEntityClass) {
        this.name = name;
        this.blockClass = blockClass;
        this.blockEntityClass = blockEntityClass;
    }

    /** The path of the provider id, {@code thaumictinkerer:<name>}. */
    public final String name() {
        return name;
    }

    /** The provider id: also the key of this tooltip's tag in the server data, and the key of its on/off option. */
    public final ResourceLocation uid() {
        return LibMisc.id(name);
    }

    /** The language key of the on/off option in Jade's plugin settings ({@code config.jade.plugin_<namespace>.<path>}). */
    public final String configKey() {
        return "config.jade.plugin_" + LibMisc.MOD_ID + "." + name;
    }

    /** The blocks the lines are shown for (this class and its subclasses). */
    public final Class<? extends Block> blockClass() {
        return blockClass;
    }

    /** The block entities the server data comes from (this class and its subclasses). */
    public final Class<T> blockEntityClass() {
        return blockEntityClass;
    }

    /** Server: writes what {@link #lines} needs about {@code tile} into {@code data}. */
    public abstract void write(T tile, CompoundTag data, HolderLookup.Provider registries);

    /** Client: the body lines, from what {@link #write} wrote. */
    public abstract List<Component> lines(CompoundTag data, HolderLookup.Provider registries);

    /**
     * Server: writes this tooltip's tag into the server data of a looked-at block, under {@link #uid()}.
     *
     * @return false (and nothing written) when {@code tile} is not a block entity of this tooltip
     */
    public final boolean appendServerData(CompoundTag serverData, @Nullable BlockEntity tile, HolderLookup.Provider registries) {
        if (!blockEntityClass.isInstance(tile)) return false;
        CompoundTag data = new CompoundTag();
        write(blockEntityClass.cast(tile), data, registries);
        serverData.put(uid().toString(), data);
        return true;
    }

    /** Client: the lines from the server data of a looked-at block; none when it carries no tag of this tooltip. */
    public final List<Component> linesFromServerData(CompoundTag serverData, HolderLookup.Provider registries) {
        String key = uid().toString();
        if (!serverData.contains(key, Tag.TAG_COMPOUND)) return List.of();
        return lines(serverData.getCompound(key), registries);
    }
}
