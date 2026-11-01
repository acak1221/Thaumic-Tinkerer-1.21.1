/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the data component that replaces the stack tag of
 * the Focus of Dislocation. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see
 * NOTICE.md).
 */
package thaumic.tinkerer.common.item.foci;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * The data components of the wand foci.
 *
 * <p>1.7.10 kept the block the Focus of Dislocation carried in the stack tag of the focus: {@code available},
 * {@code blockName}, {@code blockMeta} and {@code tileCmp} (the saved block entity). Metadata no longer exists, so the
 * component holds the whole block state; the saved block entity stays a compound tag, because it is the block entity's own
 * format. The component sits on the focus stack inside the wand ({@code WandHelper.setFocus} keeps the whole stack), and
 * "available" is the presence of the component.</p>
 *
 * <p>New: the component also carries the server's seal of a load the focus took itself ({@link DislocationSeal}), so that the
 * data of a block entity that only an operator may set (a spawner, a sign, a lectern) is put back for every player when the
 * load is genuine, and not when the component was written by hand (a creative player can give his stacks any component).</p>
 */
public final class FociComponents {
    /** The block, with its state and saved block entity, that a Focus of Dislocation carries; absent when it carries none. */
    public static RegistrySupplier<DataComponentType<DislocationData>> DISLOCATION;

    private static boolean registered;

    private FociComponents() {}

    /**
     * What a Focus of Dislocation holds: the state of the block it took, the saved data of its block entity (empty for none) and
     * the seal of the server that took it ({@link DislocationSeal}; empty for a load the focus did not take).
     */
    public record DislocationData(BlockState state, CompoundTag tile, String seal) {
        public static final Codec<DislocationData> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("state").forGetter(DislocationData::state),
                CompoundTag.CODEC.optionalFieldOf("tile", new CompoundTag()).forGetter(DislocationData::tile),
                Codec.STRING.optionalFieldOf("seal", "").forGetter(DislocationData::seal))
                .apply(i, DislocationData::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, DislocationData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), DislocationData::state, ByteBufCodecs.COMPOUND_TAG,
                DislocationData::tile, ByteBufCodecs.STRING_UTF8, DislocationData::seal, DislocationData::new);

        public DislocationData {
            tile = tile.copy();
            seal = seal == null ? "" : seal;
        }

        /** A load without a seal (made by a command or a test). */
        public DislocationData(BlockState state, CompoundTag tile) {
            this(state, tile, "");
        }

        /** A copy: the tag of a stack component is never shared with the caller. */
        @Override
        public CompoundTag tile() {
            return tile.copy();
        }

        /** The hash of the saved block entity, without copying it. */
        public int tileHash() {
            return tile.hashCode();
        }

        /** True when the block had a block entity (1.7.10: {@code !tileCmp.hasNoTags()}). */
        public boolean hasTile() {
            return !tile.isEmpty();
        }
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        DISLOCATION = TTRegistry.DATA_COMPONENT_TYPES.register("dislocation_picked",
                () -> DataComponentType.<DislocationData>builder().persistent(DislocationData.CODEC)
                        .networkSynchronized(DislocationData.STREAM_CODEC).build());
    }
}
