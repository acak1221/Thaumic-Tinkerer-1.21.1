/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.network.packet.kami;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * The path of a Shadowbeam (server to client): the first visible point, the points where the beam bounced off a wall and
 * the end. 1.7.10 spawned a dark sparkle at every step of the beam on the side that ran the beam; the client draws the
 * path from these points.
 *
 * <p>At most {@link #MAX_POINTS} points: the record keeps the first ones of a longer path, and the codec (a list of three doubles
 * each, written as a VarInt count and the points) refuses a longer list either way, so a reader never stops in the middle of one.</p>
 */
public record PacketShadowBeam(List<Vec3> points) implements CustomPacketPayload {
    public static final int MAX_POINTS = 96;
    public static final Type<PacketShadowBeam> TYPE = new Type<>(LibMisc.id("shadow_beam"));
    /** One point: x, y and z as doubles. */
    private static final StreamCodec<ByteBuf, Vec3> POINT = StreamCodec.composite(ByteBufCodecs.DOUBLE, Vec3::x,
            ByteBufCodecs.DOUBLE, Vec3::y, ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketShadowBeam> CODEC = StreamCodec.composite(
            POINT.apply(ByteBufCodecs.list(MAX_POINTS)), PacketShadowBeam::points, PacketShadowBeam::new);

    /** The path, cut to its first {@link #MAX_POINTS} points. */
    public PacketShadowBeam {
        points = List.copyOf(points.size() > MAX_POINTS ? points.subList(0, MAX_POINTS) : points);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
