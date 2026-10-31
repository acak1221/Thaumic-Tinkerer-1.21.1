/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): GameTest checks of the block tooltips (the port of
 * the Waila providers to Jade). Test-only code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see
 * NOTICE.md).
 */
package thaumic.tinkerer.testing;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import thaumic.tinkerer.common.ThaumicTinkerer;
import thaumic.tinkerer.common.block.tile.TileMagnet;
import thaumic.tinkerer.common.block.tile.TileMobMagnet;
import thaumic.tinkerer.common.block.tile.TileRepairer;
import thaumic.tinkerer.common.block.tile.kami.TileWarpGate;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.content.KamiWorld;
import thaumic.tinkerer.common.content.Machines;
import thaumic.tinkerer.common.content.TileBlocks;
import thaumic.tinkerer.common.item.ItemSoulMould;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.common.registry.TTRegistry;
import thaumic.tinkerer.compat.tooltip.BlockTooltip;
import thaumic.tinkerer.compat.tooltip.TinkererTooltips;
import thaumic.tinkerer.compat.tooltip.TooltipKeys;

/**
 * <p>The tooltips of the 1.7.10 Waila providers, on real block entities in a running server: every block is placed and put
 * in its states, the server half writes its data, the data goes through the codec Jade sends it with
 * ({@code ByteBufCodecs.COMPOUND_TAG}), and the client half's lines are compared, key and arguments, with what they must
 * be. Jade itself is not on the GameTest server (it is a compile-only dependency), so its own drawing is not seen here;
 * the plugin only hands the block entity and the server data to the code checked here.</p>
 */
public final class TooltipsRuntimeChecks {
    private TooltipsRuntimeChecks() {}

    // ---- helpers ---------------------------------------------------------------------------------------------------

    /** A floor of stone at relative y = 0. */
    private static void floor(GameTestHelper test) {
        for (int x = 0; x <= 7; x++) for (int z = 0; z <= 7; z++) test.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static <T extends BlockEntity> T tile(GameTestHelper test, BlockPos relative, Class<T> type) {
        BlockEntity entity = test.getLevel().getBlockEntity(test.absolutePos(relative));
        test.assertTrue(type.isInstance(entity), "a " + type.getSimpleName() + " at " + relative + ", found " + entity);
        return type.cast(entity);
    }

    private static <T extends BlockEntity> T place(GameTestHelper test, BlockPos relative, BlockState state, Class<T> type) {
        test.setBlock(relative, state);
        return tile(test, relative, type);
    }

    private static HolderLookup.Provider registries(GameTestHelper test) {
        return test.getLevel().registryAccess();
    }

    /**
     * What a client is shown for {@code tile}: the server half writes the server data, the data is encoded and decoded
     * with the codec of Jade's data packet, the client half reads the lines. The size of the data is logged.
     */
    private static List<Component> shown(GameTestHelper test, BlockTooltip<?> tooltip, BlockEntity tile, String state) {
        CompoundTag serverData = new CompoundTag();
        test.assertTrue(tooltip.appendServerData(serverData, tile, registries(test)), tooltip.name() + " takes " + tile);
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.COMPOUND_TAG.encode(buffer, serverData);
        int bytes = buffer.readableBytes();
        CompoundTag received = ByteBufCodecs.COMPOUND_TAG.decode(buffer);
        test.assertTrue(received.equals(serverData), "the data comes through the codec unchanged: " + serverData + " / " + received);
        ThaumicTinkerer.LOG.info("[tooltips] {} ({}): {} bytes, {}", tooltip.uid(), state, bytes, serverData);
        return tooltip.linesFromServerData(received, registries(test));
    }

    private static void assertLines(GameTestHelper test, String state, List<Component> actual, Component... expected) {
        test.assertTrue(actual.equals(List.of(expected)), state + ": expected " + List.of(expected) + ", got " + actual);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /** A mock player in the corner of the test area. */
    private static ServerPlayer mockPlayer(GameTestHelper test) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        Vec3 at = test.absoluteVec(new Vec3(6.5, 1.0, 6.5));
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.getInventory().clearContent();
        return player;
    }

    // ---- registration ----------------------------------------------------------------------------------------------

    /**
     * Every block of the original providers has exactly one tooltip, found by the same block and block entity classes the
     * Jade plugin registers; the ids are {@code thaumictinkerer:<name>}; a tooltip takes only its own block entities and
     * writes only under its own id; without the server data (a server without the plugin) there is no line.
     */
    public static void everyBlockHasExactlyOneTooltip(GameTestHelper test) {
        test.assertTrue(ThaumicTinkerer.failures().isEmpty(), "registration failures: " + ThaumicTinkerer.failures());
        floor(test);
        BlockState[] states = {
                TileBlocks.animationTabletBlock().defaultBlockState(), TileBlocks.interfaceBlock().defaultBlockState(),
                Machines.REPAIRER.get().defaultBlockState(), KamiWorld.warpGateBlock().defaultBlockState(),
                Machines.MAGNET.get().defaultBlockState(), Machines.MOB_MAGNET.get().defaultBlockState() };
        BlockTooltip<?>[] expected = { TinkererTooltips.ANIMATION_TABLET, TinkererTooltips.INTERFACE, TinkererTooltips.REPAIRER,
                TinkererTooltips.WARP_GATE, TinkererTooltips.MAGNET, TinkererTooltips.MAGNET };
        List<BlockEntity> tiles = new ArrayList<>();
        for (int i = 0; i < states.length; i++) {
            BlockPos pos = new BlockPos(1 + i, 1, 1);
            BlockEntity tile = place(test, pos, states[i], BlockEntity.class);
            tiles.add(tile);
            List<BlockTooltip<?>> byBlock = new ArrayList<>();
            List<BlockTooltip<?>> byTile = new ArrayList<>();
            for (BlockTooltip<?> tooltip : TinkererTooltips.ALL) {
                if (tooltip.blockClass().isInstance(states[i].getBlock())) byBlock.add(tooltip);
                if (tooltip.blockEntityClass().isInstance(tile)) byTile.add(tooltip);
            }
            test.assertTrue(byBlock.equals(List.of(expected[i])), states[i] + ": the lines of " + expected[i].name() + " only, found " + byBlock);
            test.assertTrue(byTile.equals(List.of(expected[i])), tile + ": the data of " + expected[i].name() + " only, found " + byTile);
        }

        Set<String> names = new HashSet<>();
        for (BlockTooltip<?> tooltip : TinkererTooltips.ALL) {
            test.assertTrue(names.add(tooltip.name()), "one tooltip per name: " + tooltip.name());
            test.assertTrue(tooltip.uid().equals(LibMisc.id(tooltip.name())), "the uid is thaumictinkerer:<name>: " + tooltip.uid());
            test.assertTrue(tooltip.configKey().equals("config.jade.plugin_thaumictinkerer." + tooltip.name()), tooltip.configKey());
            test.assertTrue(tooltip.linesFromServerData(new CompoundTag(), registries(test)).isEmpty(), tooltip.name() + ": no data, no line");
        }
        test.assertTrue(names.equals(Set.of("animation_tablet", "interface", "repairer", "warp_gate", "magnet")), "the five ids: " + names);

        // One tag carries the data of every provider of a block: another provider's entry stays, and a tooltip does not
        // write for a block entity that is not its own.
        CompoundTag shared = new CompoundTag();
        shared.putString("minecraft:other", "kept");
        test.assertTrue(!TinkererTooltips.REPAIRER.appendServerData(shared, tiles.get(3), registries(test)), "the restorer's tooltip refuses a gateway");
        test.assertTrue(!TinkererTooltips.REPAIRER.appendServerData(shared, null, registries(test)), "and no block entity at all");
        test.assertTrue(shared.size() == 1, "nothing written: " + shared);
        test.assertTrue(TinkererTooltips.WARP_GATE.appendServerData(shared, tiles.get(3), registries(test)), "the gateway's tooltip takes it");
        test.assertTrue(shared.size() == 2 && "kept".equals(shared.getString("minecraft:other"))
                && shared.contains("thaumictinkerer:warp_gate"), "its data under its uid, the other entry kept: " + shared);
        test.succeed();
    }

    // ---- Animation Tablet ------------------------------------------------------------------------------------------

    /** "Current Tool: Nothing"; with a tool its name, the click mode and the activation, in all four combinations. */
    public static void animationTabletShowsItsToolAndModes(GameTestHelper test) {
        floor(test);
        BlockPos pos = new BlockPos(1, 1, 1);
        TileAnimationTablet tablet = place(test, pos, TileBlocks.animationTabletBlock().defaultBlockState(), TileAnimationTablet.class);
        BlockTooltip<?> tooltip = TinkererTooltips.ANIMATION_TABLET;

        assertLines(test, "an empty tablet", shown(test, tooltip, tablet, "empty"), tr(TooltipKeys.CURRENT_TOOL, tr(TooltipKeys.NOTHING)));

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        test.assertTrue(pickaxe.getHoverName().equals(tr("item.minecraft.diamond_pickaxe")), "the pickaxe's name: " + pickaxe.getHoverName());
        tablet.setItem(0, pickaxe);
        test.assertTrue(tablet.leftClick && !tablet.redstone, "a new tablet: left click, autonomous");
        Component tool = tr(TooltipKeys.CURRENT_TOOL, tr("item.minecraft.diamond_pickaxe"));
        assertLines(test, "pickaxe, left, autonomous", shown(test, tooltip, tablet, "pickaxe left autonomous"),
                tool, tr(TooltipKeys.LEFT_CLICK), tr(TooltipKeys.AUTONOMOUS));
        tablet.setRedstone(true);
        assertLines(test, "pickaxe, left, redstone", shown(test, tooltip, tablet, "pickaxe left redstone"),
                tool, tr(TooltipKeys.LEFT_CLICK), tr(TooltipKeys.REDSTONE));
        tablet.setLeftClick(false);
        assertLines(test, "pickaxe, right, redstone", shown(test, tooltip, tablet, "pickaxe right redstone"),
                tool, tr(TooltipKeys.RIGHT_CLICK), tr(TooltipKeys.REDSTONE));
        tablet.setRedstone(false);
        assertLines(test, "pickaxe, right, autonomous", shown(test, tooltip, tablet, "pickaxe right autonomous"),
                tool, tr(TooltipKeys.RIGHT_CLICK), tr(TooltipKeys.AUTONOMOUS));

        // A renamed item shows its name, as 1.7.10 getDisplayName did.
        ItemStack named = new ItemStack(Items.IRON_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Tablet Test Blade"));
        tablet.setItem(0, named);
        assertLines(test, "a renamed sword", shown(test, tooltip, tablet, "renamed sword"),
                tr(TooltipKeys.CURRENT_TOOL, Component.literal("Tablet Test Blade")), tr(TooltipKeys.RIGHT_CLICK), tr(TooltipKeys.AUTONOMOUS));

        // Taken out again: only the first line, whatever the modes are.
        test.assertTrue(!tablet.removeItem(0, 1).isEmpty() && tablet.getItem(0).isEmpty(), "the sword is taken out");
        assertLines(test, "emptied", shown(test, tooltip, tablet, "emptied"), tr(TooltipKeys.CURRENT_TOOL, tr(TooltipKeys.NOTHING)));
        test.succeed();
    }

    // ---- Transvector Interface -------------------------------------------------------------------------------------

    /** "Connected to: Nothing" unbound; bound, the block's name and its absolute position; nothing again once it is gone. */
    public static void transvectorInterfaceShowsTheBoundBlock(GameTestHelper test) {
        floor(test);
        TileTransvectorInterface iface = place(test, new BlockPos(1, 1, 1), TileBlocks.interfaceBlock().defaultBlockState(),
                TileTransvectorInterface.class);
        BlockTooltip<?> tooltip = TinkererTooltips.INTERFACE;
        Component nothing = tr(TooltipKeys.CONNECTED, tr(TooltipKeys.NOTHING));

        assertLines(test, "an unbound interface", shown(test, tooltip, iface, "unbound"), nothing);

        test.setBlock(new BlockPos(1, 1, 3), Blocks.CHEST);
        BlockPos chest = test.absolutePos(new BlockPos(1, 1, 3));
        iface.setTarget(chest);
        test.assertTrue(chest.equals(iface.getTarget()), "bound to the chest: " + iface.getTarget());
        assertLines(test, "bound to a chest", shown(test, tooltip, iface, "chest"), tr(TooltipKeys.CONNECTED, tr("block.minecraft.chest")),
                Component.literal("x: " + chest.getX() + " y: " + chest.getY() + " z: " + chest.getZ()));

        test.setBlock(new BlockPos(3, 1, 1), Blocks.FURNACE);
        BlockPos furnace = test.absolutePos(new BlockPos(3, 1, 1));
        iface.setTarget(furnace);
        assertLines(test, "bound to a furnace", shown(test, tooltip, iface, "furnace"), tr(TooltipKeys.CONNECTED, tr("block.minecraft.furnace")),
                Component.literal("x: " + furnace.getX() + " y: " + furnace.getY() + " z: " + furnace.getZ()));

        // The furnace is broken: the server's getTile() finds no block entity, ends the binding, and the line says so.
        test.setBlock(new BlockPos(3, 1, 1), Blocks.AIR);
        assertLines(test, "the bound furnace is gone", shown(test, tooltip, iface, "target removed"), nothing);
        test.assertTrue(iface.getTarget() == null, "and the binding is over: " + iface.getTarget());

        // A chest beyond the reach of an interface (4 blocks) is no binding either.
        test.setBlock(new BlockPos(1, 1, 7), Blocks.CHEST);
        iface.setTarget(test.absolutePos(new BlockPos(1, 1, 7)));
        assertLines(test, "a chest 6 blocks away", shown(test, tooltip, iface, "too far"), nothing);

        // Bound again, then unbound by hand.
        iface.setTarget(chest);
        test.assertTrue(shown(test, tooltip, iface, "chest again").size() == 2, "bound to the chest again");
        iface.setTarget(null);
        assertLines(test, "unbound by hand", shown(test, tooltip, iface, "unbound again"), nothing);
        test.succeed();
    }

    // ---- Thaumic Restorer ------------------------------------------------------------------------------------------

    /** No line when empty; "Repairing: <item>" with damage (down to 1 point), "Finished repairing: <item>" without. */
    public static void repairerShowsTheToolItRepairs(GameTestHelper test) {
        floor(test);
        TileRepairer repairer = place(test, new BlockPos(1, 1, 1), Machines.REPAIRER.get().defaultBlockState(), TileRepairer.class);
        BlockTooltip<?> tooltip = TinkererTooltips.REPAIRER;

        assertLines(test, "an empty restorer", shown(test, tooltip, repairer, "empty"));

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(100);
        repairer.setItem(0, sword);
        Component name = tr("item.minecraft.iron_sword");
        assertLines(test, "a sword with 100 damage", shown(test, tooltip, repairer, "damage 100"), tr(TooltipKeys.REPAIRING, name));
        repairer.getItem(0).setDamageValue(1);
        assertLines(test, "a sword with 1 damage", shown(test, tooltip, repairer, "damage 1"), tr(TooltipKeys.REPAIRING, name));
        repairer.getItem(0).setDamageValue(0);
        assertLines(test, "the sword mended", shown(test, tooltip, repairer, "damage 0"), tr(TooltipKeys.FINISHED_REPAIRING, name));

        ItemStack named = new ItemStack(Items.DIAMOND_AXE);
        named.setDamageValue(7);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Restorer Test Axe"));
        repairer.setItem(0, named);
        assertLines(test, "a renamed axe", shown(test, tooltip, repairer, "renamed axe"),
                tr(TooltipKeys.REPAIRING, Component.literal("Restorer Test Axe")));

        test.assertTrue(!repairer.removeItem(0, 1).isEmpty(), "the axe is taken out");
        assertLines(test, "emptied", shown(test, tooltip, repairer, "emptied"));
        test.succeed();
    }

    // ---- Celestial Gateway -----------------------------------------------------------------------------------------

    /**
     * The gateway's line says what the gate does: an unlocked gate accepts a teleport and reads "Allows incoming
     * teleports", a locked one refuses it and reads "Doesn't allow incoming teleports" (1.7.10 had the two the wrong way round).
     */
    public static void warpGateTellsWhetherItAcceptsTeleports(GameTestHelper test) {
        floor(test);
        TileWarpGate gate = place(test, new BlockPos(2, 1, 2), KamiWorld.warpGateBlock().defaultBlockState(), TileWarpGate.class);
        BlockTooltip<?> tooltip = TinkererTooltips.WARP_GATE;
        ServerPlayer player = mockPlayer(test);

        test.assertTrue(!gate.locked, "a new gateway is not locked");
        assertLines(test, "a new gateway", shown(test, tooltip, gate, "unlocked"), tr(TooltipKeys.ALLOW_INCOMING));
        test.assertTrue(TileWarpGate.teleportPlayer(player, gate.getBlockPos()), "and it takes the player");

        gate.setLocked(true);
        player.moveTo(test.absoluteVec(new Vec3(6.5, 1.0, 6.5)));
        Vec3 before = player.position();
        assertLines(test, "a locked gateway", shown(test, tooltip, gate, "locked"), tr(TooltipKeys.DISALLOW_INCOMING));
        test.assertTrue(!TileWarpGate.teleportPlayer(player, gate.getBlockPos()) && player.position().equals(before),
                "and it refuses the player");

        gate.setLocked(false);
        assertLines(test, "unlocked again", shown(test, tooltip, gate, "unlocked again"), tr(TooltipKeys.ALLOW_INCOMING));
        player.discard();
        test.succeed();
    }

    // ---- Kinetic and Corporeal Attractor ---------------------------------------------------------------------------

    /** The Kinetic Attractor shows its polarity only. */
    public static void kineticAttractorShowsItsPolarity(GameTestHelper test) {
        floor(test);
        BlockPos pos = new BlockPos(1, 1, 1);
        TileMagnet magnet = place(test, pos, Machines.MAGNET.get().defaultBlockState(), TileMagnet.class);
        BlockTooltip<?> tooltip = TinkererTooltips.MAGNET;
        test.assertTrue(!(magnet instanceof TileMobMagnet), "the kinetic attractor is not the corporeal one");

        assertLines(test, "a new attractor pulls", shown(test, tooltip, magnet, "kinetic pulling"), tr(TooltipKeys.PULLING_MODE));
        magnet.setPulling(false);
        magnet = tile(test, pos, TileMagnet.class);
        test.assertTrue(!magnet.isPulling(), "turned to pushing");
        assertLines(test, "pushing", shown(test, tooltip, magnet, "kinetic pushing"), tr(TooltipKeys.PUSHING_MODE));
        magnet.setPulling(true);
        magnet = tile(test, pos, TileMagnet.class);
        assertLines(test, "pulling again", shown(test, tooltip, magnet, "kinetic pulling again"), tr(TooltipKeys.PULLING_MODE));
        test.succeed();
    }

    /**
     * The Corporeal Attractor: polarity, then adults or children, without a mould and with one (set and unset); the type
     * shown is the type the attractor really moves.
     */
    public static void corporealAttractorShowsAgeAndMould(GameTestHelper test) {
        floor(test);
        BlockPos pos = new BlockPos(1, 1, 1);
        TileMobMagnet magnet = place(test, pos, Machines.MOB_MAGNET.get().defaultBlockState(), TileMobMagnet.class);
        BlockTooltip<?> tooltip = TinkererTooltips.MAGNET;
        Component pull = tr(TooltipKeys.PULLING_MODE);
        Component push = tr(TooltipKeys.PUSHING_MODE);

        // without a mould
        test.assertTrue(magnet.isPulling() && magnet.adult, "a new attractor pulls adults");
        assertLines(test, "pulling adults", shown(test, tooltip, magnet, "pull adult"), pull, tr(TooltipKeys.PULLING_ADULT));
        magnet.setAdult(false);
        assertLines(test, "pulling children", shown(test, tooltip, magnet, "pull child"), pull, tr(TooltipKeys.PULLING_CHILD));
        magnet.setPulling(false);
        magnet = tile(test, pos, TileMobMagnet.class);
        assertLines(test, "pushing children", shown(test, tooltip, magnet, "push child"), push, tr(TooltipKeys.PUSHING_CHILD));
        magnet.setAdult(true);
        assertLines(test, "pushing adults", shown(test, tooltip, magnet, "push adult"), push, tr(TooltipKeys.PUSHING_ADULT));

        // a mould that was never set: "No Pattern" (1.7.10: the raw key entity..name); it makes the attractor move nothing
        magnet.setItem(0, TTRegistry.stack(LibItemNames.SOUL_MOULD));
        assertLines(test, "pushing adults, unset mould", shown(test, tooltip, magnet, "push adult unset mould"),
                push, tr(TooltipKeys.PUSHING_ADULT_TYPE, tr(TooltipKeys.NO_PATTERN)));

        // a mould set to the zombie (the mould's own right click; the zombie is made, not spawned)
        ServerPlayer player = mockPlayer(test);
        Zombie zombie = EntityType.ZOMBIE.create(test.getLevel());
        Cow cow = EntityType.COW.create(test.getLevel());
        test.assertTrue(zombie != null && cow != null, "a zombie and a cow");
        ItemStack mould = TTRegistry.stack(LibItemNames.SOUL_MOULD);
        mould.getItem().interactLivingEntity(mould, player, zombie, InteractionHand.MAIN_HAND);
        test.assertTrue("minecraft:zombie".equals(ItemSoulMould.getPatternName(mould)), "the mould learned the zombie: " + ItemSoulMould.getPatternName(mould));
        magnet.setItem(0, mould);
        Component zombieName = tr("entity.minecraft.zombie");
        test.assertTrue(zombie.getType().getDescription().equals(zombieName), "the zombie's name: " + zombie.getType().getDescription());
        assertLines(test, "pushing adult zombies", shown(test, tooltip, magnet, "push adult zombie"),
                push, tr(TooltipKeys.PUSHING_ADULT_TYPE, zombieName));
        test.assertTrue(magnet.isSelected(zombie) && !magnet.isSelected(cow), "the attractor moves the zombie and not the cow");
        magnet.setAdult(false);
        assertLines(test, "pushing child zombies", shown(test, tooltip, magnet, "push child zombie"),
                push, tr(TooltipKeys.PUSHING_CHILD_TYPE, zombieName));
        magnet.setPulling(true);
        magnet = tile(test, pos, TileMobMagnet.class);
        assertLines(test, "pulling child zombies", shown(test, tooltip, magnet, "pull child zombie"),
                pull, tr(TooltipKeys.PULLING_CHILD_TYPE, zombieName));
        magnet.setAdult(true);
        assertLines(test, "pulling adult zombies", shown(test, tooltip, magnet, "pull adult zombie"),
                pull, tr(TooltipKeys.PULLING_ADULT_TYPE, zombieName));

        // the mould taken out
        test.assertTrue(!magnet.removeItem(0, 1).isEmpty(), "the mould is taken out");
        assertLines(test, "pulling adults again", shown(test, tooltip, magnet, "pull adult no mould"), pull, tr(TooltipKeys.PULLING_ADULT));
        player.discard();
        test.succeed();
    }
}
