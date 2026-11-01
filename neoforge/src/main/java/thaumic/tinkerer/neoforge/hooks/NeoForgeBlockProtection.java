/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.neoforge.hooks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import thaumic.tinkerer.common.hooks.BlockProtection;

/**
 * The NeoForge backend of {@link BlockProtection} (docs/HOOKS.md, "Block protection"), installed by {@code ThaumicTinkererNeoForge}.
 * It posts the events a real break or placement posts on {@link NeoForge#EVENT_BUS} and answers whether they came back uncancelled;
 * nothing is broken or placed. Architectury's {@code BlockEvent.BREAK} and {@code PLACE} are asked through them (Architectury
 * answers NeoForge's events at {@code HIGH}), so they need no call of their own here.
 *
 * <ul>
 *   <li><b>Break</b>: {@link BlockEvent.BreakEvent} with the level, position, state and player. Not through
 *   {@code CommonHooks.fireBlockBreak}: that one also refuses for the game mode and the held item (the game's rules are
 *   {@link BlockProtection#gameAllows}'s) and resends the block to the player's client when the event is cancelled.</li>
 *   <li><b>Place</b>: {@link BlockEvent.EntityPlaceEvent} with a {@link BlockSnapshot} of the position (what is there now), the
 *   block below as the block placed against (the default of NeoForge's own {@code EventHooks.onBlockPlace}) and the player. For a
 *   player NeoForge's event reads the placed block from the world, which a real placement has already changed; a question finds
 *   the old block there, so the event posted is a subclass whose {@code getPlacedBlock()} and {@code getState()} are the state
 *   the add-on means to place. Listeners of {@code EntityPlaceEvent} receive it like NeoForge's own {@code EntityMultiPlaceEvent}.</li>
 * </ul>
 */
public final class NeoForgeBlockProtection implements BlockProtection.Backend {
    public static final NeoForgeBlockProtection INSTANCE = new NeoForgeBlockProtection();

    private NeoForgeBlockProtection() {}

    /** From the mod constructor. */
    public static void install() {
        BlockProtection.setBackend(INSTANCE);
    }

    @Override
    public boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player)).isCanceled();
    }

    @Override
    public boolean mayPlace(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return !NeoForge.EVENT_BUS.post(new QuestionPlaceEvent(snapshot, level.getBlockState(pos.below()), player, state)).isCanceled();
    }

    /** {@link BlockEvent.EntityPlaceEvent} whose placed block is the state to be placed, not what the world holds now. */
    public static final class QuestionPlaceEvent extends BlockEvent.EntityPlaceEvent {
        private final BlockState placed;

        QuestionPlaceEvent(BlockSnapshot snapshot, BlockState placedAgainst, Entity entity, BlockState placed) {
            super(snapshot, placedAgainst, entity);
            this.placed = placed;
        }

        @Override
        public BlockState getPlacedBlock() {
            return placed;
        }

        @Override
        public BlockState getState() {
            return placed;
        }
    }
}
