/**
 * This class is new in the port of Thaumic Tinkerer to Minecraft 1.21.1 for Thaumcraft 4 Reload (no 1.7.10
 * counterpart); it is distributed under the same licence as the add-on (Creative Commons
 * Attribution-NonCommercial-ShareAlike 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.common.item;

import com.mojang.serialization.Codec;
import dev.architectury.registry.registries.RegistrySupplier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * The data components of the infused seeds. 1.7.10 kept the aspect of the seeds and its tendencies in the NBT of the
 * stack ({@code mainAspect} and {@code aspectTendencies}, both saved as an aspect list); here the aspect is the tag of
 * the aspect and the tendencies are a map from tag to amount.
 *
 * <p>The Thaumcraft API has {@code Aspect.CODEC} and {@code AspectList.CODEC} now, and the components still do not use
 * them (decision, docs/api-requests/SimpleBlocks.md section 2): an {@code AspectList} compares by identity, so two stacks
 * with equal tendencies would never stack; its codecs drop amounts of zero (1.7.10 kept them) and refuse a whole list
 * for one unknown tag, which would delete a stack whose add-on aspect is gone. The tag map compares by value and drops
 * only the unknown tag ({@link #toList}).</p>
 *
 * <p><b>Bounded values.</b> Every amount of the tendencies is clamped to {@code 0..}{@link #MAX_AMOUNT} when it is read
 * from disk or from the network, when it is written and when it becomes an aspect list: a crop never holds more than
 * fifty points, and an amount out of that range (a command, a creative client, an edited save) would otherwise drive the
 * harvest loops of the crop without end or overflow the sum of its trimming. An amount out of range is clamped, not
 * refused, so that a stack saved with one is kept. The map of the component is unmodifiable.</p>
 */
public final class InfusedSeedComponents {
    public static final String ASPECT_PATH = "infused_aspect";
    public static final String TENDENCIES_PATH = "infused_tendencies";
    /** The largest amount of one tendency (the crop holds at most fifty points in all, {@code TileInfusedGrain}). */
    public static final int MAX_AMOUNT = 50;

    private static RegistrySupplier<DataComponentType<?>> aspect;
    private static RegistrySupplier<DataComponentType<?>> tendencies;

    private InfusedSeedComponents() {}

    /** Registers the two types with the add-on's deferred register. Call once, from the {@code register()} phase. */
    public static void register() {
        if (aspect != null) return;
        aspect = TTRegistry.DATA_COMPONENT_TYPES.register(ASPECT_PATH, () -> DataComponentType.<String>builder()
                .persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).cacheEncoding().build());
        tendencies = TTRegistry.DATA_COMPONENT_TYPES.register(TENDENCIES_PATH, () -> DataComponentType.<Map<String, Integer>>builder()
                .persistent(TENDENCIES_CODEC).networkSynchronized(TENDENCIES_STREAM_CODEC).cacheEncoding().build());
    }

    /** An amount clamped to {@code 0..}{@link #MAX_AMOUNT}; a missing amount is zero. */
    public static int clamp(Integer amount) {
        return amount == null ? 0 : Math.max(0, Math.min(MAX_AMOUNT, amount));
    }

    /** One amount on disk: read and written clamped (lenient, so that a stack saved with a larger amount is kept). */
    private static final Codec<Integer> AMOUNT_CODEC = Codec.INT.xmap(InfusedSeedComponents::clamp, InfusedSeedComponents::clamp);

    /** The tendencies on disk: a map from tag to amount, the amounts clamped, the decoded map unmodifiable. */
    public static final Codec<Map<String, Integer>> TENDENCIES_CODEC = Codec.unboundedMap(Codec.STRING, AMOUNT_CODEC)
            .xmap(InfusedSeedComponents::bounded, InfusedSeedComponents::bounded);

    /** The tendencies on the network: the same, so that a creative client cannot hand the server a larger amount. */
    public static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Integer>> TENDENCIES_STREAM_CODEC = ByteBufCodecs
            .<RegistryFriendlyByteBuf, String, Integer, Map<String, Integer>>map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8,
                    ByteBufCodecs.VAR_INT)
            .map(InfusedSeedComponents::bounded, InfusedSeedComponents::bounded);

    /** An unmodifiable copy of the map in its order, every amount clamped (null keys are dropped). */
    public static Map<String, Integer> bounded(Map<String, Integer> map) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        if (map != null) {
            for (Map.Entry<String, Integer> entry : map.entrySet()) {
                if (entry.getKey() != null) copy.put(entry.getKey(), clamp(entry.getValue()));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    @SuppressWarnings("unchecked")
    public static DataComponentType<String> aspectType() {
        return (DataComponentType<String>) aspect.get();
    }

    @SuppressWarnings("unchecked")
    public static DataComponentType<Map<String, Integer>> tendenciesType() {
        return (DataComponentType<Map<String, Integer>>) tendencies.get();
    }

    /**
     * An aspect list as the map of the component (the order is kept; amounts of zero are kept, as in 1.7.10; every
     * amount clamped to {@code 0..}{@link #MAX_AMOUNT}). The map is unmodifiable.
     */
    public static Map<String, Integer> toMap(AspectList list) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (Aspect aspect : list.getAspects()) {
            if (aspect != null) map.put(aspect.getTag(), clamp(list.getAmount(aspect)));
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * The aspect list of a component map, a new mutable list; tags of unknown aspects are dropped and every amount is
     * clamped to {@code 0..}{@link #MAX_AMOUNT} (a map that was put on a stack by code, not by the codecs, may hold any
     * amount).
     */
    public static AspectList toList(Map<String, Integer> map) {
        AspectList list = new AspectList();
        if (map == null) return list;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Aspect aspect = entry.getKey() == null ? null : Aspect.getAspect(entry.getKey());
            if (aspect != null) list.add(aspect, clamp(entry.getValue()));
        }
        return list;
    }
}
