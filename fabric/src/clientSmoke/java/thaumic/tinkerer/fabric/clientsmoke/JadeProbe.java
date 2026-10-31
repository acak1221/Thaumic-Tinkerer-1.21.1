package thaumic.tinkerer.fabric.clientsmoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumic.tinkerer.common.block.tile.TileMagnet;
import thaumic.tinkerer.common.block.tile.TileMobMagnet;
import thaumic.tinkerer.common.block.tile.TileRepairer;
import thaumic.tinkerer.common.block.tile.kami.TileWarpGate;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvector;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * The Jade part of {@link IntegrationsPhase}; loaded only when Jade is. For each block of the original Waila provider: the block in
 * a known state, the player looking at it with the HUD on, and the text of the tooltip Jade collected compared with the lines the
 * state calls for (translated here, not by the add-on's provider). Test-only code of the port (no 1.7.10 counterpart); CC BY-NC-SA
 * 3.0 like the add-on, not distributed.
 */
final class JadeProbe {
    private static final Map<BlockPos, String> SEEN = new ConcurrentHashMap<>();

    private JadeProbe() {}

    static void seen(BlockPos pos, String message) {
        SEEN.put(pos.immutable(), message);
    }

    /** A block put into a state on the server, and the lines (already translated) its tooltip must contain. */
    private record Case(String name, String block, BiConsumer<ServerLevel, BlockPos> setUp, Function<BlockPos, List<String>> expected, String description) {}

    static List<Script> scripts(SmokeRun run) {
        List<Case> cases = List.of(
                new Case("jade_animation_tablet", LibBlockNames.ANIMATION_TABLET, (level, pos) -> {
                    TileAnimationTablet tablet = (TileAnimationTablet) level.getBlockEntity(pos);
                    tablet.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
                    tablet.leftClick = false;
                    tablet.redstone = true;
                    changed(level, pos);
                }, pos -> List.of(I18n.get("ttwaila.currentTool", I18n.get(Items.DIAMOND_PICKAXE.getDescriptionId())), I18n.get("ttwaila.rightClick"), I18n.get("ttwaila.redstone")),
                        "Jade over the Animation Tablet with a diamond pickaxe, right click, redstone mode"),
                new Case("jade_transvector_interface", LibBlockNames.INTERFACE, (level, pos) -> {
                    BlockPos chest = pos.east(2);
                    Stage.place(level, chest, Blocks.CHEST.defaultBlockState());
                    ((TileTransvector) level.getBlockEntity(pos)).setTarget(chest);
                    changed(level, pos);
                }, pos -> {
                    BlockPos chest = pos.east(2);
                    return List.of(I18n.get("ttwaila.connected", I18n.get(Blocks.CHEST.getDescriptionId())),
                            String.format("x: %d y: %d z: %d", chest.getX(), chest.getY(), chest.getZ()));
                }, "Jade over the Transvector Interface bound to the chest two blocks east"),
                new Case("jade_repairer", LibBlockNames.REPAIRER, (level, pos) -> {
                    ItemStack sword = new ItemStack(Items.IRON_SWORD);
                    sword.setDamageValue(100);
                    ((TileRepairer) level.getBlockEntity(pos)).setItem(0, sword);
                    changed(level, pos);
                }, pos -> List.of(I18n.get("ttwaila.repairing", I18n.get(Items.IRON_SWORD.getDescriptionId()))),
                        "Jade over the Repairer holding a damaged iron sword"),
                new Case("jade_warp_gate", LibBlockNames.WARP_GATE, (level, pos) -> {
                    ((TileWarpGate) level.getBlockEntity(pos)).locked = true;
                    changed(level, pos);
                }, pos -> List.of(I18n.get("ttwaila.disallowIncoming")),
                        "Jade over a locked Celestial Gateway (the port says it refuses incoming travel; the 1.7.10 line was inverted)"),
                new Case("jade_magnet", LibBlockNames.MAGNET, (level, pos) -> {
                    ((TileMagnet) level.getBlockEntity(pos)).setPulling(true);
                    changed(level, pos);
                }, pos -> List.of(I18n.get("ttwaila.pullingMode")), "Jade over a Kinetic Magnet in pulling mode"),
                new Case("jade_mob_magnet", LibBlockNames.MOB_MAGNET, (level, pos) -> {
                    TileMobMagnet magnet = (TileMobMagnet) level.getBlockEntity(pos);
                    magnet.setPulling(false);
                    magnet.setAdult(false);
                    changed(level, pos);
                }, pos -> List.of(I18n.get("ttwaila.pushingMode"), I18n.get("ttwaila.pushingChild")),
                        "Jade over a Mob Magnet pushing, set to children, without a soul mould"));
        List<Script> scripts = new ArrayList<>();
        for (Case c : cases) scripts.add(script(run, c));
        return scripts;
    }

    private static void changed(ServerLevel level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        entity.setChanged();
        level.sendBlockUpdated(pos, entity.getBlockState(), entity.getBlockState(), 3);
    }

    private static Script script(SmokeRun run, Case c) {
        String phase = IntegrationsPhase.PHASE;
        Script script = new Script(c.name());
        BlockPos[] at = { null };
        script.then("the block in its state", run.onServer(server -> {
            ServerLevel level = server.overworld();
            Stage.clear(server, level);
            BlockPos pos = Stage.cell(level, 0);
            Stage.place(level, pos, TTRegistry.block(c.block()).defaultBlockState());
            c.setUp().accept(level, pos);
            at[0] = pos;
            // the middle of what the crosshair can hit: the magnets are a plate two pixels high under a tall model (as in 1.7.10)
            double middle = level.getBlockState(pos).getShape(level, pos).bounds().getCenter().y;
            look(run.serverPlayer(), pos, middle);
            SEEN.remove(pos);
        }));
        script.run("show the interface", r -> r.client().options.hideGui = false);
        int[] waited = { 0 };
        String[] last = { null };
        script.then("the tooltip", r -> {
            List<String> expected = c.expected().apply(at[0]);
            String message = SEEN.get(at[0]);
            last[0] = message;
            boolean all = message != null && expected.stream().allMatch(message::contains);
            if (all || ++waited[0] > 600) {
                if (all) r.report().pass(phase, c.name(), IntegrationsPhase.GROUP, c.block(), "Jade's tooltip has " + expected + " (whole text: " + oneLine(message) + ")");
                else r.report().fail(phase, c.name(), IntegrationsPhase.GROUP, c.block(), "after " + waited[0] + " ticks the tooltip "
                        + (message == null ? "was never collected for " + at[0] : "lacks some of " + expected + "; whole text: " + oneLine(message)));
                return true;
            }
            return false;
        }, 90);
        script.frames(5);
        script.then("picture", run.shots().shot(c.name().replace('_', '-'), IntegrationsPhase.GROUP, c.description()), 60);
        script.run("hide the interface", r -> r.client().options.hideGui = true);
        return script;
    }

    private static String oneLine(String message) {
        return message == null ? "none" : message.replace('\n', '|');
    }

    /** The player two blocks south of {@code pos}, the crosshair at the height {@code middle} (0..1 within the block) of its middle. */
    private static void look(ServerPlayer player, BlockPos pos, double middle) {
        double distance = 2.2;
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5 + distance;
        double y = pos.getY() + 0.6;
        double eye = y + player.getEyeHeight();
        float pitch = (float) Math.toDegrees(Math.atan2(eye - (pos.getY() + middle), distance));
        player.connection.teleport(x, y, z, 180.0F, pitch);
    }
}
