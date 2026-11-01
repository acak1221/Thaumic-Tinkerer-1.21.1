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
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.core.handler.ConfigSync;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * Client to server, in the configuration phase, the answer to {@link PacketConfigSync} ({@link ConfigSync}): the client's own
 * registration options ({@link ConfigHandler#registrationOptions()}, by {@code section/key}), which the server compares with its
 * own before it lets the client in. The server reads at most {@link #MAX_OPTIONS} keys of at most {@link #MAX_KEY_LENGTH}
 * characters (the add-on has three).
 */
public record PacketConfigSyncReply(Map<String, Boolean> registration) implements CustomPacketPayload {
    public static final int MAX_OPTIONS = 64;
    public static final int MAX_KEY_LENGTH = 256;
    public static final Type<PacketConfigSyncReply> TYPE = new Type<>(LibMisc.id("config_sync_reply"));
    public static final StreamCodec<ByteBuf, PacketConfigSyncReply> CODEC = ByteBufCodecs
            .<ByteBuf, String, Boolean, Map<String, Boolean>>map(LinkedHashMap::new, ByteBufCodecs.stringUtf8(MAX_KEY_LENGTH), ByteBufCodecs.BOOL,
                    MAX_OPTIONS)
            .map(PacketConfigSyncReply::new, PacketConfigSyncReply::registration);

    public PacketConfigSyncReply {
        registration = Collections.unmodifiableMap(new LinkedHashMap<>(registration));
    }

    /** What this side's file says (what a client answers). */
    public static PacketConfigSyncReply ofThisSide() {
        return new PacketConfigSyncReply(ConfigHandler.registrationOptions());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
