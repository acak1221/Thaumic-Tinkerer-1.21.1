/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): helpers that carry a text through the tooltip
 * data. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.tooltip;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

/**
 * Names in the tooltip data: the server writes the name of an item as the game shows it (a renamed item keeps its name,
 * as 1.7.10 {@code getDisplayName} did), the client reads it back.
 */
public final class TooltipData {
    private TooltipData() {}

    /** Writes {@code text} under {@code key}; nothing when it cannot be encoded. */
    public static void putText(CompoundTag data, String key, Component text, HolderLookup.Provider registries) {
        ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), text)
                .result().ifPresent(tag -> data.put(key, tag));
    }

    /**
     * The text under {@code key}: null when there is none, an empty text when it is there but cannot be read (so that
     * "there is an item" stays true).
     */
    @Nullable
    public static Component getText(CompoundTag data, String key, HolderLookup.Provider registries) {
        if (!data.contains(key)) return null;
        return ComponentSerialization.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), data.get(key))
                .result().orElse(Component.empty());
    }
}
