/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.neoforge.hooks;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import thaumic.tinkerer.common.core.handler.ConfigSync;
import thaumic.tinkerer.common.network.packet.PacketConfigSync;
import thaumic.tinkerer.common.network.packet.PacketConfigSyncReply;

/**
 * The NeoForge side of the config handshake ({@link ConfigSync}), registered on the mod bus by {@code ThaumicTinkererNeoForge}:
 *
 * <ul>
 *   <li>{@link RegisterPayloadHandlersEvent}: {@link PacketConfigSync} to the client and {@link PacketConfigSyncReply} to the server,
 *   configuration phase, optional (as Architectury registers the add-on's play payloads), so a client without the add-on is not
 *   refused for the channel; the handlers run on the game's thread (NeoForge's default). Neither handler touches a client class, so
 *   both are registered on both sides.</li>
 *   <li>{@link RegisterConfigurationTasksEvent}: a client that has the channel gets the task ({@link Task}); one that does not gets
 *   none and nothing is sent. NeoForge syncs its registries before every task of this event (its {@code SyncRegistries} is an early
 *   task), so a client that lacks registry entries of the server is refused by NeoForge first.</li>
 *   <li>The server's handler of the answer either finishes the task (the connection goes on) or disconnects the client with the
 *   add-on's message.</li>
 * </ul>
 */
public final class NeoForgeConfigurationTask {
    /** The task's id: {@link ConfigSync#TASK}. */
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(ConfigSync.TASK);
    /** The network version of the two payloads. */
    static final String NETWORK_VERSION = "1";

    private NeoForgeConfigurationTask() {}

    /** From the constructor of {@code ThaumicTinkererNeoForge}, both sides. */
    public static void register(IEventBus modBus) {
        modBus.addListener(NeoForgeConfigurationTask::payloads);
        modBus.addListener(NeoForgeConfigurationTask::tasks);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        // The argument is the network version of these payloads (both sides must have the same one when both have the channel).
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION).optional();
        registrar.configurationToClient(PacketConfigSync.TYPE, PacketConfigSync.CODEC,
                (packet, context) -> context.reply(ConfigSync.receive(packet)));
        registrar.configurationToServer(PacketConfigSyncReply.TYPE, PacketConfigSyncReply.CODEC, NeoForgeConfigurationTask::answered);
    }

    private static void answered(PacketConfigSyncReply reply, IPayloadContext context) {
        if (!(context.listener() instanceof ServerCommonPacketListenerImpl listener)) return;
        Component refusal = ConfigSync.answer(listener.getOwner(), reply);
        if (refusal != null) context.disconnect(refusal);
        else context.finishCurrentTask(TYPE);
    }

    private static void tasks(RegisterConfigurationTasksEvent event) {
        if (event.getListener().hasChannel(PacketConfigSync.TYPE)) event.register(new Task());
    }

    /** The configuration task: sends the server's values; the client's answer ends it. */
    record Task() implements ICustomConfigurationTask {
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(ConfigSync.offer());
        }

        @Override
        public Type type() {
            return TYPE;
        }
    }
}
