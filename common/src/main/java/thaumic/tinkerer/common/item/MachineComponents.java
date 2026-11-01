/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the data components that replace the stack tag of
 * the Machines group. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.common.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.architectury.registry.registries.RegistrySupplier;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.entity.EntityType;
import thaumic.tinkerer.common.core.helper.EnumMobAspect;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * The data components of the soul mould, the mob display, the Cursed Spirit's Blade and the soul aspects.
 *
 * <p>1.7.10 kept these in the stack tag: the mould's {@code pattern} (the legacy entity name), the mob display's
 * {@code type} (the name of a row of {@link EnumMobAspect}), the blade's {@code Activated} (int 0/1) and the infused soul
 * aspect's {@code LastX}/{@code LastY}/{@code LastZ} (the tablet that used it last, without its dimension). Each is a typed
 * component now. A value equal to the default (no pattern, no mob, not activated, never used) is stored as no component.</p>
 *
 * <p>The components are registered by {@link #register()}, called from the registrar of the group; reading a field before
 * that is a programming error.</p>
 */
public final class MachineComponents {
    /** The entity type a soul mould is set to (1.7.10: {@code pattern}); absent for a blank mould. */
    public static RegistrySupplier<DataComponentType<ResourceKey<EntityType<?>>>> SOUL_PATTERN;
    /** The mob a mob display shows (1.7.10: {@code type}); absent for a display without a mob ("Unknown Mob"). */
    public static RegistrySupplier<DataComponentType<EnumMobAspect>> DISPLAYED_MOB;
    /** True while the Cursed Spirit's Blade reaps soul aspects (1.7.10: {@code Activated} = 1); absent means off. */
    public static RegistrySupplier<DataComponentType<Boolean>> BLADE_ACTIVATED;
    /** The tablet that used an infused soul aspect last, with its dimension (1.7.10: {@code LastX/Y/Z}); absent if never. */
    public static RegistrySupplier<DataComponentType<GlobalPos>> LAST_TABLET;

    /** A row of the necromancy table by its constant name ({@code Chicken}, {@code PigZombie}...), as 1.7.10 wrote it. */
    public static final Codec<EnumMobAspect> MOB_CODEC = Codec.STRING.comapFlatMap(MachineComponents::parseMob, EnumMobAspect::name);

    /** A row of the necromancy table by its ordinal (both sides run the same table). */
    public static final StreamCodec<ByteBuf, EnumMobAspect> MOB_STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(EnumMobAspect::ordinal, EnumMobAspect.values(), ByIdMap.OutOfBoundsStrategy.ZERO),
            EnumMobAspect::ordinal);

    private static boolean registered;

    private static DataResult<EnumMobAspect> parseMob(String name) {
        EnumMobAspect row = EnumMobAspect.get(name);
        if (row == null) return DataResult.error(() -> "No row of the necromancy table named " + name);
        return DataResult.success(row);
    }

    private MachineComponents() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        SOUL_PATTERN = TTRegistry.DATA_COMPONENT_TYPES.register("soul_mould_pattern",
                () -> DataComponentType.<ResourceKey<EntityType<?>>>builder()
                        .persistent(ResourceKey.codec(Registries.ENTITY_TYPE))
                        .networkSynchronized(ResourceKey.streamCodec(Registries.ENTITY_TYPE)).build());
        DISPLAYED_MOB = TTRegistry.DATA_COMPONENT_TYPES.register("mob_display_mob",
                () -> DataComponentType.<EnumMobAspect>builder().persistent(MOB_CODEC)
                        .networkSynchronized(MOB_STREAM_CODEC).build());
        BLADE_ACTIVATED = TTRegistry.DATA_COMPONENT_TYPES.register("blood_sword_activated",
                () -> DataComponentType.<Boolean>builder().persistent(Codec.BOOL)
                        .networkSynchronized(ByteBufCodecs.BOOL).build());
        LAST_TABLET = TTRegistry.DATA_COMPONENT_TYPES.register("mob_aspect_last_tablet",
                () -> DataComponentType.<GlobalPos>builder().persistent(GlobalPos.CODEC)
                        .networkSynchronized(GlobalPos.STREAM_CODEC).build());
    }
}
