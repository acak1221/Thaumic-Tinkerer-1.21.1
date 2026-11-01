/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.common.hooks;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * Values the add-on keeps on a player: the 1.21.1 data attachments of the player entity, the counterpart of what 1.7.10 kept in
 * the player's entity data ({@code getEntityData()}) or in static maps by player name. Architectury 13 has no attachment API, so
 * the loader module installs a {@link Backend} (Fabric: {@code FabricPlayerData}, Fabric API's Data Attachment API; NeoForge:
 * {@code NeoForgePlayerData}, NeoForge's {@code AttachmentType}s), the pattern of {@link BlockProtection}. docs/HOOKS.md,
 * section "PlayerData".
 *
 * <p>A {@link Key} says per value what the game does with it, each by the loader's own machinery:</p>
 * <ul>
 *   <li><b>persistent</b> (a {@link Codec}): saved with the player (the player's file in {@code playerdata/}, or the level's own
 *   player tag in single player), so it survives logout and server restarts; without a codec the value lives as long as the entity
 *   object;</li>
 *   <li><b>copied on death</b>: a respawn after death creates a new entity, which gets the value only with this flag (1.7.10 lost its
 *   entity data there). A respawn that is not a death (leaving the End through the poem) copies every value on both loaders;</li>
 *   <li><b>synced to the owner</b> (a {@link StreamCodec}): the server sends every change, and the whole value on join, respawn and
 *   change of dimension, to the client of the player it belongs to, and to nobody else; the client reads the same key on its
 *   {@code LocalPlayer}.</li>
 * </ul>
 *
 * <p>Each side has its own value: the client's {@code LocalPlayer} and the server's {@code ServerPlayer} are separate entities, also
 * in single player, so a value that both sides count (the progress of a focus) is per side without a side switch. Keys are
 * registered at mod start-up ({@link #register}, from the groups' {@code register()}) <b>whatever the config says</b>: a value
 * whose type is not registered is dropped when the player is loaded (both loaders log and skip it), so a server that turns
 * {@code kami.forceenabled} off for a while must not lose its players' Soul Hearts.</p>
 *
 * <p>Values are compared and stored as given: a persistent or synced value must be immutable (an {@code Integer}, a {@code Boolean},
 * an immutable set) and is changed with {@link #set}; a transient value may be a mutable object that is changed in place
 * ({@link #getOrCreate}). Server thread for the server's values, client thread for the client's.</p>
 */
public final class PlayerData {
    private static final Logger LOG = LoggerFactory.getLogger("thaumictinkerer");

    /** The loader side: registers the attachment types and reads and writes them on a player. */
    public interface Backend {
        /** A short name for the logs and the checks ({@code fabric}, {@code neoforge}). */
        String name();

        /** Registers the attachment type of a key. Called once per key, at mod start-up. */
        void register(Key<?> key);

        /** The value the player carries, or null when it carries none (the key's initial value is not stored by a read). */
        @Nullable
        <T> T getExisting(Player player, Key<T> key);

        /** Sets the value ({@code null} removes it). A synced key sends the change to the owner's client. */
        <T> void set(Player player, Key<T> key, @Nullable T value);
    }

    /** One value a player carries: its id, its initial value and what the game does with it (see the class comment). */
    public static final class Key<T> {
        private final ResourceLocation id;
        private final Supplier<T> initial;
        @Nullable
        private final Codec<T> codec;
        @Nullable
        private final StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec;
        private final boolean copyOnDeath;

        private Key(Builder<T> builder) {
            this.id = builder.id;
            this.initial = builder.initial;
            this.codec = builder.codec;
            this.syncCodec = builder.syncCodec;
            this.copyOnDeath = builder.copyOnDeath;
        }

        public static <T> Builder<T> builder(ResourceLocation id, Supplier<T> initial) {
            return new Builder<>(id, initial);
        }

        public ResourceLocation id() {
            return id;
        }

        /** A new initial value (what a player that carries none has). */
        public T initial() {
            return initial.get();
        }

        /** The codec it is saved with, or null for a value that lives as long as the entity. */
        @Nullable
        public Codec<T> codec() {
            return codec;
        }

        /** The codec of the owner's client sync, or null for a value the client is not told. */
        @Nullable
        public StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec() {
            return syncCodec;
        }

        public boolean persistent() {
            return codec != null;
        }

        public boolean synced() {
            return syncCodec != null;
        }

        public boolean copyOnDeath() {
            return copyOnDeath;
        }

        @Override
        public String toString() {
            return "PlayerData.Key[" + id + (persistent() ? ", persistent" : "") + (copyOnDeath ? ", copied on death" : "")
                    + (synced() ? ", synced to the owner" : "") + "]";
        }
    }

    public static final class Builder<T> {
        private final ResourceLocation id;
        private final Supplier<T> initial;
        @Nullable
        private Codec<T> codec;
        @Nullable
        private StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec;
        private boolean copyOnDeath;

        private Builder(ResourceLocation id, Supplier<T> initial) {
            this.id = Objects.requireNonNull(id);
            this.initial = Objects.requireNonNull(initial);
        }

        /** Saved with the player. */
        public Builder<T> persistent(Codec<T> codec) {
            this.codec = Objects.requireNonNull(codec);
            return this;
        }

        /** Sent to the client of the player it belongs to (only that one). */
        public Builder<T> syncedToOwner(StreamCodec<? super RegistryFriendlyByteBuf, T> syncCodec) {
            this.syncCodec = Objects.requireNonNull(syncCodec);
            return this;
        }

        /** Given to the new entity of a respawn after death. */
        public Builder<T> copyOnDeath() {
            this.copyOnDeath = true;
            return this;
        }

        public Key<T> build() {
            return new Key<>(this);
        }
    }

    /**
     * The legacy sources a player's values were already taken from (see {@link #migrateOnce}). Saved, kept through death, never
     * synced: a source is read at most once per player, so a stale entry of an old save file can never overwrite a newer value.
     */
    public static final Key<Set<String>> MIGRATED = Key.<Set<String>>builder(LibMisc.id("migrated_legacy_data"), Set::of)
            .persistent(Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf)).copyOnDeath().build();

    private static final Map<ResourceLocation, Key<?>> KEYS = new LinkedHashMap<>();
    private static Backend backend = new MemoryBackend();
    private static boolean installed;

    static {
        KEYS.put(MIGRATED.id(), MIGRATED);
    }

    private PlayerData() {}

    /**
     * Registers keys (once each; the same key again is ignored). Before the loader installed its backend the keys wait for it; the
     * loader registers them all when it does.
     */
    public static synchronized void register(Key<?>... keys) {
        for (Key<?> key : keys) {
            Key<?> known = KEYS.get(key.id());
            if (known == key) continue;
            if (known != null) throw new IllegalStateException("two player data keys with the id " + key.id());
            KEYS.put(key.id(), key);
            if (installed) backend.register(key);
        }
    }

    /** The loader module's backend; registers every key known so far (later keys are registered as they come). */
    public static synchronized void install(Backend loaderBackend) {
        if (installed) throw new IllegalStateException("a PlayerData backend is already installed: " + backend.name());
        backend = Objects.requireNonNull(loaderBackend);
        installed = true;
        for (Key<?> key : KEYS.values()) backend.register(key);
    }

    /** The backend in use: the loader's name, or {@code memory} without a loader (the JUnit tests). */
    public static String backendName() {
        return backend.name();
    }

    /** The keys registered so far, in order. */
    public static synchronized List<Key<?>> keys() {
        return Collections.unmodifiableList(new ArrayList<>(KEYS.values()));
    }

    /** The value of the player, or the key's initial value when it carries none (which is not stored). */
    public static <T> T get(Player player, Key<T> key) {
        T value = backend.getExisting(player, key);
        return value != null ? value : key.initial();
    }

    /** Whether the player carries a value of this key. */
    public static boolean has(Player player, Key<?> key) {
        return backend.getExisting(player, key) != null;
    }

    /** The value of the player; a player that carries none is given a new initial value first (for values changed in place). */
    public static <T> T getOrCreate(Player player, Key<T> key) {
        T value = backend.getExisting(player, key);
        if (value == null) {
            value = key.initial();
            backend.set(player, key, value);
        }
        return value;
    }

    public static <T> void set(Player player, Key<T> key, T value) {
        backend.set(player, key, Objects.requireNonNull(value));
    }

    /** Takes the value away; the player then has the initial value again. */
    public static void remove(Player player, Key<?> key) {
        backend.set(player, key, null);
    }

    /**
     * Runs {@code migration} for the player unless it already ran for {@code source} (a name of the old save file), and records that
     * it did. The migration reads the old file and sets the player's values; the file itself is never written again, so a player that
     * never comes back keeps its entry there and nothing is lost. Returns true when the migration ran.
     */
    public static boolean migrateOnce(ServerPlayer player, String source, Consumer<ServerPlayer> migration) {
        Set<String> done = get(player, MIGRATED);
        if (done.contains(source)) return false;
        migration.accept(player);
        Set<String> now = new HashSet<>(done);
        now.add(source);
        set(player, MIGRATED, Set.copyOf(now));
        return true;
    }

    /**
     * Without a loader (the JUnit tests): the values in memory, per entity object, nothing saved or synced. A loader that forgot to
     * install its backend would lose every persistent value at logout, so its first use is logged.
     */
    private static final class MemoryBackend implements Backend {
        private final Map<Player, Map<Key<?>, Object>> values = Collections.synchronizedMap(new WeakHashMap<>());
        private boolean warned;

        @Override
        public String name() {
            return "memory";
        }

        @Override
        public void register(Key<?> key) {}

        @Override
        @SuppressWarnings("unchecked")
        @Nullable
        public <T> T getExisting(Player player, Key<T> key) {
            warn();
            Map<Key<?>, Object> map = values.get(player);
            return map == null ? null : (T) map.get(key);
        }

        @Override
        public <T> void set(Player player, Key<T> key, @Nullable T value) {
            warn();
            if (value == null) {
                Map<Key<?>, Object> map = values.get(player);
                if (map != null) map.remove(key);
            } else {
                values.computeIfAbsent(player, p -> new IdentityHashMap<>()).put(key, value);
            }
        }

        private void warn() {
            if (warned) return;
            warned = true;
            LOG.warn("Thaumic Tinkerer: no loader backend of PlayerData is installed; player values are kept in memory only");
        }
    }
}
