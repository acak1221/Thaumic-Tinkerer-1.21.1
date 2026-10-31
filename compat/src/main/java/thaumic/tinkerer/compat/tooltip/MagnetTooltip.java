/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the tooltip of the Kinetic and the Corporeal
 * Attractor. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.tooltip;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.block.BlockMagnet;
import thaumic.tinkerer.common.block.tile.TileMagnet;
import thaumic.tinkerer.common.block.tile.TileMobMagnet;
import thaumic.tinkerer.common.item.ItemSoulMould;
import thaumic.tinkerer.common.lib.LibBlockNames;

/**
 * Both attractors (one block class, as the one 1.7.10 block with its metadata bit), as {@code MagnetProvider} showed them:
 * "Mode: Pulling" / "Mode: Pushing"; the Corporeal Attractor adds what it moves: adults or children, and with something
 * in its slot the entity type of the soul mould ("Pulling: Adult Zombie").
 *
 * <p>The type is the name of the mould's entity type ({@code entity.minecraft.zombie}; 1.7.10 translated
 * {@code entity.<legacy name>.name}). A mould that was never set shows the mould's own "No Pattern"
 * ({@code ttmisc.soulMould.nonAssigned}) where 1.7.10 showed the untranslated key {@code entity..name}; such a mould
 * makes the attractor move nothing, in both versions.</p>
 */
public final class MagnetTooltip extends BlockTooltip<TileMagnet> {
    public static final String TAG_PULLING = "pulling";
    /** Present (true) for the Corporeal Attractor only. */
    public static final String TAG_MOB = "mob";
    public static final String TAG_ADULT = "adult";
    /** The entity type id of the soul mould in the slot ("" for a mould that was never set); absent when the slot is empty. */
    public static final String TAG_PATTERN = "pattern";

    MagnetTooltip() {
        super(LibBlockNames.MAGNET, BlockMagnet.class, TileMagnet.class);
    }

    @Override
    public void write(TileMagnet magnet, CompoundTag data, HolderLookup.Provider registries) {
        data.putBoolean(TAG_PULLING, magnet.isPulling());
        if (magnet instanceof TileMobMagnet mob) {
            data.putBoolean(TAG_MOB, true);
            data.putBoolean(TAG_ADULT, mob.adult);
            // Whatever the slot holds counts, as for the attractor itself (TileMobMagnet.isSelected) and in 1.7.10.
            ItemStack stack = mob.getItem(0);
            if (!stack.isEmpty()) data.putString(TAG_PATTERN, ItemSoulMould.getPatternName(stack));
        }
    }

    @Override
    public List<Component> lines(CompoundTag data, HolderLookup.Provider registries) {
        return linesFor(data.getBoolean(TAG_PULLING), data.getBoolean(TAG_MOB), data.getBoolean(TAG_ADULT),
                data.contains(TAG_PATTERN) ? data.getString(TAG_PATTERN) : null);
    }

    /**
     * The lines of an attractor.
     *
     * @param mob     whether it is the Corporeal Attractor (else {@code adult} and {@code pattern} are not used)
     * @param pattern the entity type id of the mould in the slot, "" for an unset mould, null for an empty slot
     */
    public static List<Component> linesFor(boolean pulling, boolean mob, boolean adult, @Nullable String pattern) {
        List<Component> lines = new ArrayList<>(2);
        lines.add(Component.translatable(pulling ? TooltipKeys.PULLING_MODE : TooltipKeys.PUSHING_MODE));
        if (mob) {
            if (pattern == null) {
                String key = pulling ? adult ? TooltipKeys.PULLING_ADULT : TooltipKeys.PULLING_CHILD
                        : adult ? TooltipKeys.PUSHING_ADULT : TooltipKeys.PUSHING_CHILD;
                lines.add(Component.translatable(key));
            } else {
                String key = pulling ? adult ? TooltipKeys.PULLING_ADULT_TYPE : TooltipKeys.PULLING_CHILD_TYPE
                        : adult ? TooltipKeys.PUSHING_ADULT_TYPE : TooltipKeys.PUSHING_CHILD_TYPE;
                lines.add(Component.translatable(key, patternName(pattern)));
            }
        }
        return lines;
    }

    /** The name shown for a mould's pattern: the entity type's name, "No Pattern" for an unset mould. */
    public static Component patternName(String pattern) {
        return pattern.isEmpty() ? Component.translatable(TooltipKeys.NO_PATTERN) : ItemSoulMould.patternDisplayName(pattern);
    }
}
