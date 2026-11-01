/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.fabric.hooks;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import thaumic.tinkerer.common.core.handler.ConfigSync;
import thaumic.tinkerer.common.network.packet.PacketConfigSync;
import thaumic.tinkerer.common.network.packet.PacketConfigSyncReply;

/**
 * The Fabric side of the config handshake ({@link ConfigSync}), installed by {@code ThaumicTinkererFabric} on both sides: the two
 * configuration payloads, the task and the server's receiver of the answer. The client's receiver is
 * {@code thaumic.tinkerer.fabric.client.FabricConfigurationTaskClient} (Fabric's client networking API is not on a dedicated server).
 *
 * <ul>
 *   <li>{@link ServerConfigurationConnectionEvents#CONFIGURE}: a client that can receive {@link PacketConfigSync} gets the task
 *   ({@link Task}); one that cannot (no add-on) gets none and nothing is sent. Fabric's registry sync is a task of
 *   {@code BEFORE_CONFIGURE}, so it comes first: a client that lacks registry entries of the server is refused by it.</li>
 *   <li>The task sends the server's values and waits for the client's {@link PacketConfigSyncReply}.</li>
 *   <li>The answer arrives on Netty's thread; the comparison runs on the server's thread, which then either completes the task (the
 *   connection goes on to the next task and the game) or disconnects the client with the add-on's message.</li>
 * </ul>
 */
public final class FabricConfigurationTask {
    /** The task's id: {@link ConfigSync#TASK}. */
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(ConfigSync.TASK);

    private static boolean installed;

    private FabricConfigurationTask() {}

    /** From {@code ThaumicTinkererFabric.onInitialize}, both sides, once. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        PayloadTypeRegistry.configurationS2C().register(PacketConfigSync.TYPE, PacketConfigSync.CODEC);
        PayloadTypeRegistry.configurationC2S().register(PacketConfigSyncReply.TYPE, PacketConfigSyncReply.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, PacketConfigSync.TYPE)) handler.addTask(new Task());
        });
        ServerConfigurationNetworking.registerGlobalReceiver(PacketConfigSyncReply.TYPE, (reply, context) -> {
            ServerConfigurationPacketListenerImpl handler = context.networkHandler();
            context.server().execute(() -> {
                Component refusal = ConfigSync.answer(handler.getOwner(), reply);
                if (refusal != null) handler.disconnect(refusal);
                else handler.completeTask(TYPE);
            });
        });
    }

    /** The configuration task: sends the server's values; the client's answer ends it. */
    record Task() implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createS2CPacket(ConfigSync.offer()));
        }

        @Override
        public Type type() {
            return TYPE;
        }
    }
}
