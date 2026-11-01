/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the seams between the Kami gear and the
 * neighbouring feature groups, and a few shared helpers. Distributed under the same licence as the add-on (CC BY-NC-SA
 * 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.common.item.kami;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.ThaumcraftFX;
import thaumcraft.api.ThaumcraftSoundIds;
import thaumic.tinkerer.common.ThaumicTinkerer;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * Everything the Kami gear needs from outside its own group, reached through registry paths and replaceable hooks so
 * that the groups never reference each other's classes (see {@code docs/PORTING_RULES.md} section 1, rule 3).
 *
 * <ul>
 *   <li>{@link #BEDROCK_DIMENSION} and the portal block path are the contract with the KamiWorld group: the Bedrock
 *       dimension is {@code thaumictinkerer:bedrock} and the portal block {@code thaumictinkerer:bedrock_portal}.</li>
 *   <li>{@link #soulHeartSink} is the hook of the Soul Hearts ({@code SoulHeartHandler.addHearts}); the KamiWorld
 *       group assigns it from its {@code register()}. Until then the Soul Mode of the awakened sword is silent.</li>
 *   <li>{@link #warpGateTeleporter} is {@code TileWarpGate.teleportPlayer}, which the KamiWorld group assigns from its
 *       {@code register()} whenever the Kami items exist; until then there is no destination (the original's "no
 *       destination" message).</li>
 * </ul>
 */
public final class KamiHooks {
    /** The dimension of the Bedrock world (the contract with the KamiWorld group). */
    public static final ResourceKey<Level> BEDROCK_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            LibMisc.id("bedrock"));

    /** The size of 1.7.10's {@code proxy.sparkle(x, y, z, color)} and its colour index 6 (white). */
    public static final float SPARKLE_SIZE = 1.5F;
    public static final int SPARKLE_WHITE = 6;

    private static boolean warnedSoulHearts;

    /** Adds one Soul Heart to a player (1.7.10 {@code SoulHeartHandler.addHearts}). */
    public static volatile Consumer<ServerPlayer> soulHeartSink = player -> {
        if (!warnedSoulHearts) {
            warnedSoulHearts = true;
            ThaumicTinkerer.LOG.warn("Kami: the Soul Hearts hook is not connected; the awakened sword's Soul Mode gives no hearts");
        }
    };

    /** Teleports a player to the Celestial Gateway at a position of the player's own dimension (1.7.10 {@code TileWarpGate.teleportPlayer}). */
    public static volatile WarpGateTeleporter warpGateTeleporter = (player, gate) -> {
        player.sendSystemMessage(Component.translatable("ttmisc.noDest"));
        return false;
    };

    private static volatile Consumer<Component> tooltipSink = text -> {};
    private static volatile Supplier<Player> localPlayer = () -> null;
    private KamiHooks() {}

    @FunctionalInterface
    public interface WarpGateTeleporter {
        boolean teleport(ServerPlayer player, BlockPos gate);
    }

    /** Client entry point: where the tool-mode and armour messages are drawn (see {@code ToolModeHUDHandler}). */
    public static void setTooltipSink(Consumer<Component> sink) {
        tooltipSink = sink == null ? text -> {} : sink;
    }

    /** Client entry point: the player of this client, for the tooltips that depend on where the player stands. */
    public static void setLocalPlayerSupplier(Supplier<Player> supplier) {
        localPlayer = supplier == null ? () -> null : supplier;
    }

    /** The player of this client, or null on a server (and before a world is joined). */
    public static Player localPlayer() {
        return localPlayer.get();
    }

    /** Shows a message above the hotbar; does nothing on the server side of a level. */
    public static void showTooltip(Level level, Component text) {
        if (level.isClientSide) tooltipSink.accept(text);
    }

    /** 1.7.10 {@code playSoundAtEntity(player, "random.orb", 0.3F, 0.1F)}: the click of a toggle. */
    public static void playToggleSound(Level level, Player player) {
        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.3F, 0.1F);
    }

    /**
     * Plays a Thaumcraft sound ({@link ThaumcraftSoundIds}, the API's registered events) at the player, <b>including</b> the player,
     * for code that runs on the server only (1.7.10's {@code playSoundAtEntity(player, "thaumcraft:<path>", ...)} reached the actor as
     * well): the Shadowbeam's hum, the Mirror's placing sound, the sounds of the Celestial Recall.
     */
    public static void broadcastThaumcraftSound(Level level, Player at, ResourceLocation sound, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(), ThaumcraftSoundIds.event(sound), SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * 1.7.10 {@code ThaumicTinkerer.tcProxy.sparkle(x, y, z, 6)} {@code count} times around a player: white sparkles at a random
     * point of the player's body, 0.5 blocks to each side (the Celestial Recall's departure and arrival). The original drew them
     * on the client; the API's server twin ({@link ThaumcraftFX#sparkle}) sends them to the players around.
     */
    public static void sparklesAround(ServerLevel level, Player player, int count) {
        for (int i = 0; i < count; i++) {
            ThaumcraftFX.sparkle(level, player.getX() + level.random.nextFloat() - 0.5, player.getY() + level.random.nextFloat(),
                    player.getZ() + level.random.nextFloat() - 0.5, SPARKLE_SIZE, SPARKLE_WHITE, 0F);
        }
    }

    /** The block of the add-on with that path, or null when the group that owns it did not register it. */
    public static Block optionalBlock(String path) {
        return BuiltInRegistries.BLOCK.getOptional(LibMisc.id(path)).orElse(null);
    }

    /** True when the portal block of the KamiWorld group exists (1.7.10: {@code ConfigHandler.bedrockDimensionID != 0}). */
    public static boolean bedrockDimensionAvailable() {
        return optionalBlock(LibBlockNames.PORTAL) != null;
    }
}
