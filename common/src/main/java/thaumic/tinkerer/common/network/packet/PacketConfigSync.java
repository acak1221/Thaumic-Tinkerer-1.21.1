/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.common.network.packet;

import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.core.handler.ConfigSync;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * Server to client, once on join ({@link ConfigSync}): the server's values of the options a client evaluates as well
 * ({@link ConfigHandler.Shared}) and of the options that decide what is registered ({@link ConfigHandler#registrationOptions()},
 * by {@code section/key}). The client keeps the first as its copy of the server's and compares the second with its own; the
 * client handler is registered by the client registration ({@code ConfigSyncClient}).
 */
public record PacketConfigSync(boolean enableFlight, List<String> dislocationBlacklist, List<String> forbiddenDimensions,
        boolean cropsAllowBonemeal, Map<String, Boolean> registration) implements CustomPacketPayload {
    public static final Type<PacketConfigSync> TYPE = new Type<>(LibMisc.id("config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketConfigSync> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PacketConfigSync::enableFlight,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PacketConfigSync::dislocationBlacklist,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PacketConfigSync::forbiddenDimensions,
            ByteBufCodecs.BOOL, PacketConfigSync::cropsAllowBonemeal,
            ByteBufCodecs.<ByteBuf, String, Boolean, Map<String, Boolean>>map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8,
                    ByteBufCodecs.BOOL), PacketConfigSync::registration,
            PacketConfigSync::new);

    public PacketConfigSync {
        dislocationBlacklist = List.copyOf(dislocationBlacklist);
        forbiddenDimensions = List.copyOf(forbiddenDimensions);
        registration = Collections.unmodifiableMap(new LinkedHashMap<>(registration));
    }

    /** What this side's file says (what a server sends). */
    public static PacketConfigSync ofThisSide() {
        ConfigHandler.Shared shared = ConfigHandler.Shared.local();
        return new PacketConfigSync(shared.enableFlight(), shared.dislocationBlacklist(), shared.forbiddenDimensions(),
                shared.cropsAllowBonemeal(), ConfigHandler.registrationOptions());
    }

    /** The options the client keeps as its copy of the server's. */
    public ConfigHandler.Shared shared() {
        return new ConfigHandler.Shared(enableFlight, dislocationBlacklist, forbiddenDimensions, cropsAllowBonemeal);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
