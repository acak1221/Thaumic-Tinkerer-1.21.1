/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.common.core.handler;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import thaumic.tinkerer.common.network.packet.PacketConfigSync;

/**
 * The server half of the config sync (1.7.10 had none: every side read its own {@code ThaumicTinkerer.cfg}, so a client with
 * {@code modFlight.enabled} on flew with the Focus of Flight on a server that had it off). On every join the server sends
 * {@link PacketConfigSync}: its values of the options a client evaluates too, which that client then uses for its own levels
 * ({@link ConfigHandler#flightEnabled} and the other side-aware accessors), and its values of the options that decide what is
 * registered, which the client compares with its own and, when they differ, leaves the server with
 * {@link #mismatchMessage}. The client half is {@code thaumic.tinkerer.client.core.handler.ConfigSyncClient}.
 *
 * <p>The payload goes only to a client that can receive it (one with the add-on); a client without the add-on gets nothing and
 * reads its own file (it has no add-on code to read it with anyway). On the integrated server the client gets the values of the
 * same file, so single player is unchanged.</p>
 */
public final class ConfigSync {
    /** The message of the disconnect: {@code %s} is the list of {@link #MISMATCH_OPTION} entries. */
    public static final String MISMATCH = "thaumictinkerer.config.mismatch";
    /** One option of the list: {@code %1$s} the option ({@code section/key}), {@code %2$s} the server's value, {@code %3$s} the client's. */
    public static final String MISMATCH_OPTION = "thaumictinkerer.config.mismatch.option";

    private static boolean registered;

    private ConfigSync() {}

    /** From {@code ThaumicTinkerer.init()}, both sides, once. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        // Architectury registers the type of an S2C payload together with its client receiver (ConfigSyncClient), so only a
        // dedicated server registers it here; on a client both calls end in Fabric's PayloadTypeRegistry, which throws for the
        // second one (the integrated server shares the client's registry). The same rule as the other S2C payloads.
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(PacketConfigSync.TYPE, PacketConfigSync.CODEC);
        }
        PlayerEvent.PLAYER_JOIN.register(ConfigSync::send);
    }

    /** Sends this server's values to {@code player}, when its client can receive them. */
    public static void send(ServerPlayer player) {
        if (player.connection == null || !NetworkManager.canPlayerReceive(player, PacketConfigSync.TYPE)) return;
        NetworkManager.sendToPlayer(player, PacketConfigSync.ofThisSide());
    }

    /**
     * The reason a client gives when it leaves a server whose registration options differ from its own: the options, each with
     * the server's value and the client's ({@code mismatched} are keys of {@link ConfigHandler#registrationOptions()}).
     */
    public static Component mismatchMessage(List<String> mismatched, Map<String, Boolean> server) {
        Map<String, Boolean> own = ConfigHandler.registrationOptions();
        MutableComponent list = Component.empty();
        for (int i = 0; i < mismatched.size(); i++) {
            String key = mismatched.get(i);
            if (i > 0) list.append(", ");
            list.append(Component.translatable(MISMATCH_OPTION, key, String.valueOf(server.get(key)), String.valueOf(own.get(key))));
        }
        return Component.translatable(MISMATCH, list);
    }
}
