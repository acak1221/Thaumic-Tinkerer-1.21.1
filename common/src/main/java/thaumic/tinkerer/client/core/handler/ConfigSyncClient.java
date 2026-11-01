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
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.core.handler.ConfigSync;

/**
 * The client half of the config handshake ({@link ConfigSync}) that is not part of the configuration phase: the server's values,
 * which the client keeps as its copy when they arrive in the configuration phase ({@link ConfigSync#receive}, called by the loader
 * glue), hold for as long as it is connected. Leaving the server, or being refused by it, clears them, so this side's own file counts
 * again. (The refusal itself is the server's now: a client whose registration options differ is not let in.)
 */
public final class ConfigSyncClient {
    private static boolean registered;

    private ConfigSyncClient() {}

    /** From {@code ThaumicTinkerer.initClient()}, once. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        // Leaving the server (or the world), and a refusal in the configuration phase: Architectury posts the event from
        // Minecraft.disconnect on both loaders, with or without a player.
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> ConfigHandler.setFromServer(null));
    }
}
