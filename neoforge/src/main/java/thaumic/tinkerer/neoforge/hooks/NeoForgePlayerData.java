/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.neoforge.hooks;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.hooks.PlayerData;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * The NeoForge backend of {@link PlayerData} (docs/HOOKS.md, section "PlayerData"), registered by {@code ThaumicTinkererNeoForge}:
 * one NeoForge {@link AttachmentType} per key, in a {@code DeferredRegister} of {@code NeoForgeRegistries.ATTACHMENT_TYPES} on the
 * mod bus (so every key must be registered before the registries are built, i.e. from the mod constructor, where the groups'
 * {@code register()} run).
 *
 * <ul>
 *   <li>persistent: {@code Builder.serialize(codec)}; NeoForge writes it into the entity's NBT ({@code neoforge:attachments});</li>
 *   <li>copied on death: {@code Builder.copyOnDeath()}; NeoForge copies only serializable attachments
 *   ({@code AttachmentInternals.copyAttachments}), so a value that is not saved but must be copied gets a serializer that writes
 *   nothing ({@code write} returns null: never saved) and a copy handler that hands the new player the same object;</li>
 *   <li>synced to the owner: {@code Builder.sync((holder, to) -> holder == to, codec)}; {@code setData}/{@code removeData} send the
 *   change, and NeoForge sends the player's own attachments on login, respawn and change of dimension
 *   ({@code AttachmentSync.syncInitialPlayerAttachments}).</li>
 * </ul>
 */
public final class NeoForgePlayerData implements PlayerData.Backend {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            LibMisc.MOD_ID);

    private final Map<PlayerData.Key<?>, Supplier<? extends AttachmentType<?>>> types = new IdentityHashMap<>();

    private NeoForgePlayerData() {}

    /** From the mod constructor: the register of the attachment types, and the backend. */
    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        PlayerData.install(new NeoForgePlayerData());
    }

    @Override
    public String name() {
        return "neoforge";
    }

    @Override
    public synchronized void register(PlayerData.Key<?> key) {
        if (!key.id().getNamespace().equals(LibMisc.MOD_ID)) throw new IllegalArgumentException("not the add-on's namespace: " + key);
        types.put(key, TYPES.register(key.id().getPath(), () -> build(key)));
    }

    private static <T> AttachmentType<T> build(PlayerData.Key<T> key) {
        AttachmentType.Builder<T> builder = AttachmentType.builder(key::initial);
        if (key.codec() != null) {
            builder.serialize(key.codec());
            if (key.copyOnDeath()) builder.copyOnDeath();
        } else if (key.copyOnDeath()) {
            builder.serialize(new IAttachmentSerializer<CompoundTag, T>() {
                @Override
                public T read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
                    return key.initial(); // never written, so never read
                }

                @Override
                @Nullable
                public CompoundTag write(T attachment, HolderLookup.Provider provider) {
                    return null;
                }
            }).copyOnDeath().copyHandler((attachment, holder, provider) -> attachment);
        }
        if (key.syncCodec() != null) builder.sync((holder, to) -> holder == to, key.syncCodec());
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private synchronized <T> AttachmentType<T> type(PlayerData.Key<T> key) {
        Supplier<? extends AttachmentType<?>> type = types.get(key);
        if (type == null) throw new IllegalStateException("player data key not registered: " + key);
        return (AttachmentType<T>) type.get();
    }

    @Override
    @Nullable
    public <T> T getExisting(Player player, PlayerData.Key<T> key) {
        return player.getExistingDataOrNull(type(key));
    }

    @Override
    public <T> void set(Player player, PlayerData.Key<T> key, @Nullable T value) {
        if (value == null) player.removeData(type(key));
        else player.setData(type(key), value);
    }
}
