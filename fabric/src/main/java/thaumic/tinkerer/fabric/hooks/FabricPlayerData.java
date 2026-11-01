/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.fabric.hooks;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.hooks.PlayerData;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * The Fabric backend of {@link PlayerData} (docs/HOOKS.md, section "PlayerData"), installed by {@code ThaumicTinkererFabric}: one
 * attachment type of Fabric API's Data Attachment API ({@code fabric-data-attachment-api-v1}) per key.
 *
 * <ul>
 *   <li>persistent: {@code Builder.persistent(codec)}; Fabric writes it into the entity's NBT ({@code fabric:attachments}), which the
 *   game saves with the player;</li>
 *   <li>copied on death: {@code Builder.copyOnDeath()}; Fabric copies the attachments to the new player of a respawn
 *   ({@code ServerPlayerEvents.COPY_FROM}), on a death only those with the flag, and does the same on the client for the new
 *   {@code LocalPlayer};</li>
 *   <li>synced to the owner: {@code Builder.syncWith(codec, AttachmentSyncPredicate.targetOnly())}; every {@code setAttached} on the
 *   server sends the change to the player it belongs to, and the join sends what the player loaded with;</li>
 *   <li>not copied on any respawn: Fabric API copies the attachments in its own {@code ServerPlayerEvents.AFTER_RESPAWN} listener (the
 *   default phase), every one after the End; a listener in a phase ordered after it takes these keys away from the new player again
 *   (Architectury's {@code PLAYER_RESPAWN} is a mixin of its own at the same point, whose order against that copy is not fixed).</li>
 * </ul>
 */
public final class FabricPlayerData implements PlayerData.Backend {
    private final Map<PlayerData.Key<?>, AttachmentType<?>> types = new IdentityHashMap<>();

    private FabricPlayerData() {}

    /** The phase of {@code AFTER_RESPAWN} after Fabric API's copy of the attachments (its default phase). */
    private static final ResourceLocation AFTER_ATTACHMENT_COPY = LibMisc.id("after_attachment_copy");

    public static void install() {
        FabricPlayerData backend = new FabricPlayerData();
        PlayerData.install(backend);
        ServerPlayerEvents.AFTER_RESPAWN.addPhaseOrdering(Event.DEFAULT_PHASE, AFTER_ATTACHMENT_COPY);
        ServerPlayerEvents.AFTER_RESPAWN.register(AFTER_ATTACHMENT_COPY, (oldPlayer, newPlayer, alive) -> backend.dropOnRespawn(newPlayer));
    }

    private void dropOnRespawn(ServerPlayer player) {
        for (PlayerData.Key<?> key : PlayerData.keys()) {
            if (key.notCopiedOnRespawn() && getExisting(player, key) != null) set(player, key, null);
        }
    }

    @Override
    public String name() {
        return "fabric";
    }

    @Override
    public synchronized void register(PlayerData.Key<?> key) {
        types.put(key, build(key));
    }

    private static <T> AttachmentType<T> build(PlayerData.Key<T> key) {
        AttachmentRegistry.Builder<T> builder = AttachmentRegistry.builder();
        if (key.codec() != null) builder.persistent(key.codec());
        if (key.copyOnDeath()) builder.copyOnDeath();
        if (key.syncCodec() != null) builder.syncWith(key.syncCodec(), AttachmentSyncPredicate.targetOnly());
        return builder.buildAndRegister(key.id());
    }

    @SuppressWarnings("unchecked")
    private synchronized <T> AttachmentType<T> type(PlayerData.Key<T> key) {
        AttachmentType<?> type = types.get(key);
        if (type == null) throw new IllegalStateException("player data key not registered: " + key);
        return (AttachmentType<T>) type;
    }

    @Override
    @Nullable
    public <T> T getExisting(Player player, PlayerData.Key<T> key) {
        return ((AttachmentTarget) player).getAttached(type(key));
    }

    @Override
    public <T> void set(Player player, PlayerData.Key<T> key, @Nullable T value) {
        ((AttachmentTarget) player).setAttached(type(key), value);
    }
}
