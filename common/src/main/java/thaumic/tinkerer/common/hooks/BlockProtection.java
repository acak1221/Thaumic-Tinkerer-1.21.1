/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.common.hooks;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * May a player break or place a block at a position, as far as the game and the protection mods of the server are concerned?
 * The add-on's devices and tools that change blocks without going through the player's own break or place path (the Focus of
 * Dislocation, Efreet's Flame, the Placement Mirror, the Transvector Dislocator) ask here first and leave the block alone when
 * the answer is no.
 *
 * <p>Two parts. The game's own rules are checked here, the same on both loaders: the position is loaded and inside the world
 * border, the player may interact there ({@code ServerLevel.mayInteract}: spawn protection) and the game mode does not restrict
 * block actions ({@code blockActionRestricted}: adventure and spectator). The protection mods are reached through the loader's
 * own events by the {@link Backend} the loader module installs (Fabric: the break and place callbacks; NeoForge: the
 * {@code BlockEvent}s), so a claim mod written for that loader is asked as it would be for a real break or placement. Without a
 * backend only the game's rules hold.</p>
 *
 * <p>A backend posts the events as a question: nothing is broken or placed by it. While it does, {@link #isProbing()} is true,
 * and the add-on's own listeners of those events (which act on a real break, e.g. the Tunnel enchantment) must do nothing.</p>
 */
public final class BlockProtection {
    /** The loader side: posts the loader's break or place event for the position and answers whether nobody cancelled it. */
    public interface Backend {
        boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state);

        boolean mayPlace(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state);
    }

    private static final Backend NONE = new Backend() {
        @Override
        public boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
            return true;
        }

        @Override
        public boolean mayPlace(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
            return true;
        }
    };

    private static volatile Backend backend = NONE;
    private static final ThreadLocal<Boolean> PROBING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private BlockProtection() {}

    /** Installed once by the loader module. */
    public static void setBackend(Backend newBackend) {
        backend = Objects.requireNonNull(newBackend);
    }

    /** True while a backend posts a loader event as a question; the add-on's own listeners of break/place events then do nothing. */
    public static boolean isProbing() {
        return PROBING.get();
    }

    /** The game's own rules: loaded, inside the border, spawn protection, adventure/spectator. */
    public static boolean gameAllows(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }

    /** May {@code player} break (or take away, or turn into something else) the block {@code state} at {@code pos}? */
    public static boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!gameAllows(player, level, pos)) return false;
        Boolean outer = PROBING.get();
        PROBING.set(Boolean.TRUE);
        try {
            return backend.mayBreak(player, level, pos, state);
        } finally {
            // a listener that asks again inside the question must not end the outer question's flag
            PROBING.set(outer);
        }
    }

    /** May {@code player} place {@code state} at {@code pos}? */
    public static boolean mayPlace(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!gameAllows(player, level, pos)) return false;
        Boolean outer = PROBING.get();
        PROBING.set(Boolean.TRUE);
        try {
            return backend.mayPlace(player, level, pos, state);
        } finally {
            PROBING.set(outer);
        }
    }
}
