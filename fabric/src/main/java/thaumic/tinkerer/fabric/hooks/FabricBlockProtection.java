/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.fabric.hooks;

import dev.architectury.event.events.common.BlockEvent;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import thaumic.tinkerer.common.hooks.BlockProtection;

/**
 * The Fabric backend of {@link BlockProtection} (docs/HOOKS.md, "Block protection"), installed by {@code ThaumicTinkererFabric}.
 * It asks the listeners a real break or placement would ask, without breaking or placing anything:
 *
 * <ul>
 *   <li><b>Break</b>: Fabric API's {@link PlayerBlockBreakEvents#BEFORE} (what Fabric's own mixin in
 *   {@code ServerPlayerGameMode.destroyBlock} invokes; the claim mods written for Fabric listen there), then Architectury's
 *   {@link BlockEvent#BREAK} (on Fabric posted by Architectury's own mixin in the same method, not by Fabric's event, so it
 *   has to be asked separately). The first that refuses decides. {@code PlayerBlockBreakEvents.CANCELED} is not invoked:
 *   it exists to resend the block to the client of a cancelled real break, and nothing was broken here.</li>
 *   <li><b>Place</b>: Architectury's {@link BlockEvent#PLACE} with the state to be placed and the player as the placer, then Fabric
 *   API's {@link PlayerBlockBreakEvents#BEFORE} for the position with what is there now (air, water, grass: what the placement
 *   replaces). Fabric API has no block place event of its own, so the claim mods written for Fabric guard a position through its
 *   break event and the click ({@code UseBlockCallback}, which cannot be asked as a question: its listeners act). Open Parties and
 *   Claims 0.33.0 is one: it listens to {@code PlayerBlockBreakEvents.BEFORE} and {@code UseBlockCallback} but to no place event on
 *   Fabric, so without this a placement it should refuse was allowed (docs/MULTIPLAYER_VERIFICATION.md, section h). A mod that
 *   refuses the change of a position refuses the placement there too; one that allows breaking allows it.</li>
 * </ul>
 */
public final class FabricBlockProtection implements BlockProtection.Backend {
    public static final FabricBlockProtection INSTANCE = new FabricBlockProtection();

    private FabricBlockProtection() {}

    /** From the main entrypoint. */
    public static void install() {
        BlockProtection.setBackend(INSTANCE);
    }

    @Override
    public boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos))) {
            return false;
        }
        return !BlockEvent.BREAK.invoker().breakBlock(level, pos, state, player, null).isFalse();
    }

    @Override
    public boolean mayPlace(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (BlockEvent.PLACE.invoker().placeBlock(level, pos, state, player).isFalse()) return false;
        BlockState there = level.getBlockState(pos);
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, there, level.getBlockEntity(pos));
    }
}
