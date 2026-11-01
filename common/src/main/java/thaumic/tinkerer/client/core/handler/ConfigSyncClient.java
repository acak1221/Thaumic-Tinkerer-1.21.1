/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.client.core.handler;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.networking.NetworkManager;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.core.handler.ConfigSync;
import thaumic.tinkerer.common.network.packet.PacketConfigSync;

/**
 * The client half of the config sync ({@link ConfigSync}): keeps the server's values of the options a client evaluates as its
 * copy ({@link ConfigHandler#setFromServer}) for as long as it is connected, and leaves a server whose registration options
 * ({@link ConfigHandler#registrationOptions()}) differ from its own, with a message that names them (they cannot be changed
 * while the game runs: the client has registered its items, blocks, research and recipes from its own file).
 */
public final class ConfigSyncClient {
    private static final Logger LOG = LoggerFactory.getLogger("ThaumicTinkerer");
    private static boolean registered;

    private ConfigSyncClient() {}

    /** From {@code ThaumicTinkerer.initClient()}, once. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketConfigSync.TYPE, PacketConfigSync.CODEC,
                (packet, context) -> context.queue(() -> receive(packet)));
        // Leaving the server (or the world): this side's own file counts again.
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> ConfigHandler.setFromServer(null));
    }

    private static void receive(PacketConfigSync packet) {
        ConfigHandler.setFromServer(packet.shared());
        List<String> differ = ConfigHandler.registrationMismatches(packet.registration());
        if (differ.isEmpty()) return;
        LOG.error("Thaumic Tinkerer: this server's config differs in options that cannot change while the game runs: {} "
                + "(server {}, here {}); leaving the server", differ, packet.registration(), ConfigHandler.registrationOptions());
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener != null) listener.getConnection().disconnect(ConfigSync.mismatchMessage(differ, packet.registration()));
    }
}
