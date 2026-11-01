/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the data components that replace the stack tag keys of
 * the strike counter and of Tunnel's direction. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see
 * NOTICE.md).
 */
package thaumic.tinkerer.common.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.architectury.registry.registries.RegistrySupplier;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * What the enchantments remember on the item that carries them.
 *
 * <p>1.7.10 kept the last target of Focused and Dispersed Strikes ({@code TTEnchantLastTarget}, {@code TTEnchantLastTarget2},
 * the two halves of its UUID) and the counter of successive strikes on it ({@code TTEnchantSuccessiveStrike}) in the stack tag
 * of the weapon, and the way the player looked when a tool of Tunnel last broke a block ({@code TTEnchantTunnelDir}) in the
 * stack tag of the tool. Each is a typed component here. Both are synchronised with the client: the client's prediction of the
 * dig speed reads Tunnel's direction, as it read the tag.</p>
 *
 * <p>The components are registered by {@link #register()}, called from the registrar of the group
 * ({@code content/Enchantments}); reading a field before that is a programming error.</p>
 */
public final class EnchantmentComponents {
    /** The last target of Focused or Dispersed Strikes and the counter of strikes on it; absent before the first blow. */
    public static RegistrySupplier<DataComponentType<StrikeMemory>> STRIKE_MEMORY;
    /** The yaw of the player when a tool of Tunnel last broke a block, in degrees; absent before the first block. */
    public static RegistrySupplier<DataComponentType<Float>> TUNNEL_DIRECTION;

    private static boolean registered;

    private EnchantmentComponents() {}

    /** The last target of the strikes and the counter of successive strikes on it (see {@link EnchantmentFormulas#nextSuccessiveStrikes}). */
    public record StrikeMemory(UUID target, int strikes) {
        /** What a weapon that never struck remembers: 1.7.10 read the missing keys as the UUID 0-0 and the counter 0. */
        public static final StrikeMemory NONE = new StrikeMemory(new UUID(0L, 0L), 0);

        public static final Codec<StrikeMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("target").forGetter(StrikeMemory::target),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("strikes").forGetter(StrikeMemory::strikes)).apply(instance, StrikeMemory::new));

        public static final StreamCodec<ByteBuf, StrikeMemory> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, StrikeMemory::target,
                ByteBufCodecs.VAR_INT, StrikeMemory::strikes,
                StrikeMemory::new);
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        STRIKE_MEMORY = TTRegistry.DATA_COMPONENT_TYPES.register("strike_memory", () -> DataComponentType.<StrikeMemory>builder()
                .persistent(StrikeMemory.CODEC).networkSynchronized(StrikeMemory.STREAM_CODEC).build());
        TUNNEL_DIRECTION = TTRegistry.DATA_COMPONENT_TYPES.register("tunnel_direction", () -> DataComponentType.<Float>builder()
                .persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build());
    }
}
