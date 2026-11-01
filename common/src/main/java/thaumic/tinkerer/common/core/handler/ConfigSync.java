/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.common.core.handler;

import com.mojang.authlib.GameProfile;
import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.common.network.packet.PacketConfigSync;
import thaumic.tinkerer.common.network.packet.PacketConfigSyncReply;

/**
 * The config handshake between a server and a client with the add-on (1.7.10 had none: every side read its own
 * {@code ThaumicTinkerer.cfg}, so a client with {@code modFlight.enabled} on flew with the Focus of Flight on a server that had it
 * off). It is a <b>configuration task</b> of the connection, so it runs before the client has a world:
 * <ol>
 *   <li>the server sends {@link PacketConfigSync}: its values of the options a client evaluates too and of the options that decide
 *   what is registered ({@link #offer()});</li>
 *   <li>the client keeps the first as its copy of the server's ({@link ConfigHandler#setFromServer}, read by
 *   {@link ConfigHandler#flightEnabled} and the other side-aware accessors from the first tick of its world on) and answers with
 *   its own registration options, {@link PacketConfigSyncReply} ({@link #receive});</li>
 *   <li>the server compares them with its own ({@link #answer}): equal, the connection goes on; different, the server refuses the
 *   client with {@link #mismatchMessage}, translated on the client, before it entered the game.</li>
 * </ol>
 *
 * <p>Architectury 13 has no configuration phase, so the task is loader glue: {@code FabricConfigurationTask} and
 * {@code FabricConfigurationTaskClient} (Fabric's {@code ServerConfigurationConnectionEvents.CONFIGURE} and configuration
 * networking), {@code NeoForgeConfigurationTask} (NeoForge's {@code RegisterConfigurationTasksEvent} and configuration payloads).
 * Both add the task only for a client that has the add-on's channel; a client without the add-on gets nothing, as before, and
 * whether it may play is the loader's matter (its registry sync). The loaders run their own registry sync before this task, so a
 * client that lacks registry entries of the server is refused by the loader first, with the loader's message. On the integrated
 * server the client gets the values of the same file, so single player is unchanged. The client's copy is cleared when it leaves
 * ({@code thaumic.tinkerer.client.core.handler.ConfigSyncClient}).</p>
 */
public final class ConfigSync {
    /** The message of the refusal: {@code %s} is the list of {@link #MISMATCH_OPTION} entries. */
    public static final String MISMATCH = "thaumictinkerer.config.mismatch";
    /** One option of the list: {@code %1$s} the option ({@code section/key}), {@code %2$s} the server's value, {@code %3$s} the client's. */
    public static final String MISMATCH_OPTION = "thaumictinkerer.config.mismatch.option";
    /** The id of the configuration task on both loaders ({@code ConfigurationTask.Type}). */
    public static final String TASK = LibMisc.id("config_sync").toString();

    private static final Logger LOG = LoggerFactory.getLogger("ThaumicTinkerer");

    /**
     * Posted on the server's thread when a client answered the handshake, before it is let in ({@code mismatches} empty) or refused.
     * For server-side tools that want to know who was refused and why; the add-on itself does not listen.
     */
    public static final Event<Answered> ANSWERED = EventFactory.createLoop();

    @FunctionalInterface
    public interface Answered {
        /**
         * @param profile the joining player
         * @param clientOptions the client's registration options, by {@code section/key}
         * @param mismatches the keys whose values differ from the server's, in the server's order; empty when the client goes on
         */
        void answered(GameProfile profile, Map<String, Boolean> clientOptions, List<String> mismatches);
    }

    private ConfigSync() {}

    /** What a server sends in the configuration phase: the values of its file. */
    public static PacketConfigSync offer() {
        return PacketConfigSync.ofThisSide();
    }

    /**
     * On a client, in the configuration phase (any thread): keeps the server's values as this client's copy, unless the server's
     * registration options differ from this side's (the server will refuse this client then, so its values are never used), and
     * returns the answer for the server: this side's registration options.
     */
    public static PacketConfigSyncReply receive(PacketConfigSync packet) {
        List<String> differ = ConfigHandler.registrationMismatches(packet.registration());
        if (differ.isEmpty()) {
            ConfigHandler.setFromServer(packet.shared());
        } else {
            ConfigHandler.setFromServer(null);
            LOG.warn("Thaumic Tinkerer: this server's config differs in options that cannot change while the game runs: {} (server {}, here {});"
                    + " the server will refuse this client", differ, packet.registration(), ConfigHandler.registrationOptions());
        }
        return PacketConfigSyncReply.ofThisSide();
    }

    /**
     * On the server, on its thread: the client's answer. Posts {@link #ANSWERED} and returns the message to refuse the client with,
     * or null when its registration options equal the server's (a key only one side knows, from another version of the add-on, is
     * not compared).
     */
    @Nullable
    public static Component answer(GameProfile profile, PacketConfigSyncReply reply) {
        Map<String, Boolean> server = ConfigHandler.registrationOptions();
        List<String> differ = ConfigHandler.registrationMismatches(reply.registration());
        ANSWERED.invoker().answered(profile, reply.registration(), differ);
        if (differ.isEmpty()) return null;
        LOG.info("Thaumic Tinkerer: refusing {}: its config differs in options that cannot change while the game runs: {} (server {}, client {})",
                profile.getName(), differ, server, reply.registration());
        return mismatchMessage(differ, server, reply.registration());
    }

    /**
     * The reason a client is refused with: the options that differ ({@code mismatched}, keys of
     * {@link ConfigHandler#registrationOptions()}), each with the server's value and the client's.
     */
    public static Component mismatchMessage(List<String> mismatched, Map<String, Boolean> server, Map<String, Boolean> client) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < mismatched.size(); i++) {
            String key = mismatched.get(i);
            if (i > 0) list.append(", ");
            list.append(Component.translatable(MISMATCH_OPTION, key, String.valueOf(server.get(key)), String.valueOf(client.get(key))));
        }
        return Component.translatable(MISMATCH, list);
    }
}
