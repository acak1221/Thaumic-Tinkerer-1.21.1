/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.testing;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import thaumic.tinkerer.common.hooks.PlayerData;

/**
 * Test helpers for the player data attachments ({@link PlayerData}): what a mock player's client was sent, and a logout and login
 * of the same player through the game's own player list.
 *
 * <p><b>The sync packets.</b> Each loader sends attachment changes as a payload of its own: Fabric API
 * {@code fabric:attachment_sync_v1} ({@code AttachmentSyncPayloadS2C}: a list of changes, each the target, the attachment type and the
 * bytes {@code present + value}), NeoForge {@code neoforge:sync_attachments} ({@code SyncAttachmentsPayload}: the target, the list of
 * types and one byte array of {@code present + value} for each in turn). Both are internal classes of the loader, so they are read
 * here by their record accessors, and the value is decoded with the key's own stream codec. A mock player's connection is an
 * {@code EmbeddedChannel}, which keeps what was written to it.</p>
 *
 * <p>Fabric sends a change to the owner whenever it is set; what a player loads with is sent at join only to a client that told the
 * server in the configuration phase that it knows the type (a mock player has no such phase, so on Fabric a mock is not sent its
 * initial values; NeoForge sends them). The checks therefore look at changes, and the client smoke and the multiplayer check look at a
 * real client.</p>
 */
final class PlayerDataSync {
    private static final ResourceLocation FABRIC_SYNC = ResourceLocation.fromNamespaceAndPath("fabric", "attachment_sync_v1");
    private static final ResourceLocation NEOFORGE_SYNC = ResourceLocation.fromNamespaceAndPath("neoforge", "sync_attachments");

    private PlayerDataSync() {}

    /** The packets that the mock player's connection has been sent so far. */
    static List<Object> sentPackets(ServerPlayer player) {
        try {
            Field connection = ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
            connection.setAccessible(true);
            Field channel = Connection.class.getDeclaredField("channel");
            channel.setAccessible(true);
            EmbeddedChannel embedded = (EmbeddedChannel) channel.get(connection.get(player.connection));
            embedded.flush(); // the server thread writes without flushing (the real connections are flushed at the end of the tick)
            return new ArrayList<>(embedded.outboundMessages());
        } catch (ReflectiveOperationException | ClassCastException e) {
            throw new IllegalStateException("cannot read the packets of the mock player's connection", e);
        }
    }

    /**
     * The values of {@code key} for the entity {@code entityId} that {@code receiver}'s client has been told so far, in order: a value, or
     * empty for a removal (the client then has the key's initial value).
     */
    static <T> List<Optional<T>> valuesSent(ServerPlayer receiver, int entityId, PlayerData.Key<T> key) {
        List<Optional<T>> told = new ArrayList<>();
        for (Object packet : sentPackets(receiver)) collect(packet, receiver, entityId, key, told);
        return told;
    }

    /** {@link #valuesSent} for the receiver's own entity. */
    static <T> List<Optional<T>> valuesSent(ServerPlayer owner, PlayerData.Key<T> key) {
        return valuesSent(owner, owner.getId(), key);
    }

    /** How many attachment sync packets of either loader {@code receiver}'s client has been sent so far, about any entity and key. */
    static int syncPackets(ServerPlayer receiver) {
        int count = 0;
        for (Object packet : sentPackets(receiver)) {
            for (CustomPacketPayload payload : payloads(packet)) {
                ResourceLocation id = payload.type().id();
                if (id.equals(FABRIC_SYNC) || id.equals(NEOFORGE_SYNC)) count++;
            }
        }
        return count;
    }

    private static List<CustomPacketPayload> payloads(Object packet) {
        List<CustomPacketPayload> out = new ArrayList<>();
        if (packet instanceof ClientboundCustomPayloadPacket custom) out.add(custom.payload());
        else if (packet instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> sub : bundle.subPackets()) out.addAll(payloads(sub));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static <T> void collect(Object packet, ServerPlayer receiver, int entityId, PlayerData.Key<T> key, List<Optional<T>> told) {
        for (CustomPacketPayload payload : payloads(packet)) {
            ResourceLocation id = payload.type().id();
            try {
                if (id.equals(FABRIC_SYNC)) {
                    for (Object change : (List<Object>) call(payload, "attachments")) {
                        Object target = call(change, "targetInfo");
                        if (!target.getClass().getSimpleName().equals("EntityTarget") || (int) call(target, "networkId") != entityId) continue;
                        ResourceLocation type = (ResourceLocation) call(call(change, "type"), "identifier");
                        if (!type.equals(key.id())) continue;
                        told.add(decode(receiver, key, (byte[]) call(change, "data")));
                    }
                } else if (id.equals(NEOFORGE_SYNC)) {
                    Object target = call(payload, "target");
                    if (!target.getClass().getSimpleName().equals("EntityTarget") || (int) call(target, "entity") != entityId) continue;
                    Registry<Object> types = (Registry<Object>) Class.forName("net.neoforged.neoforge.registries.NeoForgeRegistries")
                            .getField("ATTACHMENT_TYPES").get(null);
                    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer((byte[]) call(payload, "syncPayload")),
                            receiver.registryAccess());
                    for (Object type : (List<Object>) call(payload, "types")) {
                        ResourceLocation typeId = types.getKey(type);
                        PlayerData.Key<?> known = PlayerData.keys().stream().filter(k -> k.id().equals(typeId)).findFirst().orElse(null);
                        if (known == null || known.syncCodec() == null) break; // a type of another mod: its bytes cannot be skipped
                        boolean present = buf.readBoolean();
                        Object value = present ? known.syncCodec().decode(buf) : null;
                        if (known == key) told.add(Optional.ofNullable((T) value));
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("cannot read the attachment sync payload " + id, e);
            }
        }
    }

    private static <T> Optional<T> decode(ServerPlayer receiver, PlayerData.Key<T> key, byte[] data) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), receiver.registryAccess());
        if (!buf.readBoolean()) return Optional.empty();
        return Optional.of(key.syncCodec().decode(buf));
    }

    private static Object call(Object target, String accessor) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(accessor);
        method.setAccessible(true);
        return method.invoke(target);
    }

    // ---- logout and login ----------------------------------------------------------------------------------------

    /**
     * A mock player like {@code GameTestHelper.makeMockServerPlayerInLevel}'s, with the given profile, placed by the player list:
     * {@code PlayerList.placeNewPlayer} reads the player's saved file ({@code playerdata/<uuid>.dat}) when there is one, as a login
     * does, and posts the join event. Unlike the helper's mock it answers {@code isCreative()} by its game mode.
     */
    static ServerPlayer login(GameTestHelper test, GameProfile profile) {
        MinecraftServer server = test.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, test.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    /**
     * A logout and a login of the same player: the player list removes it (posting the quit event and saving its file) and a new
     * entity of the same profile is placed, loaded from that file. Returns the new entity.
     */
    static ServerPlayer relog(GameTestHelper test, ServerPlayer player) {
        GameProfile profile = player.getGameProfile();
        test.getLevel().getServer().getPlayerList().remove(player);
        return login(test, profile);
    }

    /**
     * A respawn through the player list, as the game does it: after a death ({@code afterTheEnd} false: the new entity gets only what is
     * copied on death) or on the way back from the End through the poem ({@code true}: every value is copied). Returns the new entity.
     */
    static ServerPlayer respawn(ServerPlayer player, boolean afterTheEnd) {
        return player.server.getPlayerList().respawn(player, afterTheEnd,
                afterTheEnd ? net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION : net.minecraft.world.entity.Entity.RemovalReason.KILLED);
    }

    /**
     * A save file of the earlier port, written to a new directory in the format the game writes ({@code {data: ..., DataVersion: ...}},
     * compressed) and read back through a {@code DimensionDataStorage} of that directory, as the overworld's storage reads its files.
     */
    static <T extends net.minecraft.world.level.saveddata.SavedData> T legacyFile(GameTestHelper test, String name, net.minecraft.nbt.CompoundTag data,
            net.minecraft.world.level.saveddata.SavedData.Factory<T> factory) {
        try {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("tt-legacy-data");
            net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
            root.put("data", data);
            net.minecraft.nbt.NbtUtils.addCurrentDataVersion(root);
            net.minecraft.nbt.NbtIo.writeCompressed(root, dir.resolve(name + ".dat"));
            MinecraftServer server = test.getLevel().getServer();
            T loaded = new net.minecraft.world.level.storage.DimensionDataStorage(dir.toFile(), server.getFixerUpper(), server.registryAccess())
                    .get(factory, name);
            if (loaded == null) throw new IllegalStateException("the legacy file " + name + " was not read back");
            return loaded;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot write the legacy file " + name, e);
        }
    }

    /** Whether a player's saved tag carries a value of the key (the loaders save attachments under their own compound, by id). */
    static boolean saves(ServerPlayer player, PlayerData.Key<?> key) {
        return player.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString().contains(key.id().toString());
    }

    /** Takes a player of {@link #login} or {@link #relog} out of the game again (the quit event, its file saved). */
    static void logout(GameTestHelper test, ServerPlayer player) {
        if (test.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
            test.getLevel().getServer().getPlayerList().remove(player);
        }
    }
}
