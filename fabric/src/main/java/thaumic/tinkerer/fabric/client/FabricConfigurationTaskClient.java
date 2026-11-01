/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import thaumic.tinkerer.common.core.handler.ConfigSync;
import thaumic.tinkerer.common.network.packet.PacketConfigSync;
import thaumic.tinkerer.fabric.hooks.FabricConfigurationTask;

/**
 * The client's receiver of the config handshake on Fabric ({@link ConfigSync}, {@link FabricConfigurationTask}), installed by
 * {@code ThaumicTinkererFabricClient}: in the configuration phase the client keeps the server's values and answers with its own
 * registration options. Registering the receiver is also what tells the server that this client can take the task. The receiver
 * runs on Netty's thread; {@link ConfigSync#receive} only sets a volatile copy and builds the answer.
 */
public final class FabricConfigurationTaskClient {
    private static boolean installed;

    private FabricConfigurationTaskClient() {}

    /** From {@code ThaumicTinkererFabricClient.onInitializeClient}, once, after {@link FabricConfigurationTask#install()}. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        ClientConfigurationNetworking.registerGlobalReceiver(PacketConfigSync.TYPE,
                (packet, context) -> context.responseSender().sendPacket(ConfigSync.receive(packet)));
    }
}
