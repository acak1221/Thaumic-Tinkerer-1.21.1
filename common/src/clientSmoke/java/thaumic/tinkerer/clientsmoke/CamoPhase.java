package thaumic.tinkerer.clientsmoke;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumic.tinkerer.common.block.camo.CamoDeviceBlockEntity;
import thaumic.tinkerer.common.block.tile.TileCamo;
import thaumic.tinkerer.common.hooks.CamouflageHooks;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * The camouflage blocks with a camouflage set: the Ethereal Platform (SimpleBlocks) and the four camouflageable devices (TileBlocks)
 * imitating six blocks that each need something of the chunk renderer (a tint, a cut-out or translucent render type, light, a state of
 * their own), each compared with a picture of the real blocks at the same places. Test-only code of the port (no 1.7.10 counterpart);
 * CC BY-NC-SA 3.0 like the add-on, not distributed.
 *
 * <p>The camouflage is drawn by the blocks' wrapped baked models in the chunk mesh ({@code CamouflageHooks}), so nothing of it is a
 * block entity renderer and the renderer close-ups of {@link RendererPhase} do not see it. Here the camouflage is first set to stone
 * and, once the client has drawn that, changed to the block of the cell: a change from one camouflage to another changes no block
 * state, so the picture shows the second camouflage only if the client built the chunk section again when the block entity's data
 * arrived.</p>
 *
 * <p>The checks: a camouflaged cell must be seen ({@code camo_visible}, the share of its cube's pixels that differ from the empty stage,
 * as in {@link BlockRowsPhase}) and must look like the real block ({@code camo_like_real}: at most {@link #LIKE_REAL} of the pixels of its
 * silhouette where the real block shows something differ by more than a small amount from the picture of the real block,
 * {@link Visibility#unlikeOnSilhouette}; the floor around the cube, which a real block shades by smooth lighting and a platform does not,
 * is left out). A cleared camouflage must show the block's own model again. The columns (a real block under a camouflaged platform) are for the eye: the numbers are recorded, not judged.</p>
 */
final class CamoPhase extends ScriptedPhase {
    private static final String PHASE = "camouflage";
    /**
     * The largest share of the pixels of a camouflaged cube's silhouette that may differ from the real block. What differs where the
     * drawing is right is the floor seen through the transparent pixels of the cube (the holes of the leaves, the glass): a real block with
     * a full collision shape darkens the floor next to it by smooth lighting (glass does not), a platform (no collision shape without an
     * entity) never does, a device (a full cube) always does, whatever it imitates. The first run on Fabric measured 15 % for the
     * platform's leaves and 0 % to 0.6 % for the rest.
     */
    static final double LIKE_REAL = 0.25;

    /** What the six cells imitate: a tint (grass, leaves), cut-out (glass, leaves), translucent (ice), light (glowstone), a state (the log on its side). */
    private static final List<BlockState> CAMOS = List.of(Blocks.GRASS_BLOCK.defaultBlockState(),
            Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), Blocks.GLASS.defaultBlockState(),
            Blocks.ICE.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState(),
            Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
    /** The camouflage every cell has first, before it is changed to its own. */
    private static final BlockState FIRST = Blocks.STONE.defaultBlockState();
    /** The device of each cell in the device row; the Golem Connector (opaque in its own look) imitates ice and glowstone. */
    private static final List<String> DEVICES = List.of(LibBlockNames.INTERFACE, LibBlockNames.DISLOCATOR, LibBlockNames.REMOTE_PLACER,
            LibBlockNames.GOLEMCONNECTOR, LibBlockNames.GOLEMCONNECTOR, LibBlockNames.INTERFACE);

    private static final String EMPTY = "camo-empty-stage";
    private static final String REAL = "camo-real-blocks";
    private static final String PLATFORMS = "camo-platforms";
    private static final String DEVICE_ROW = "camo-devices";
    private static final String CLEARED = "camo-cleared";
    private static final String COLUMNS_REAL = "camo-columns-real";
    private static final String COLUMNS = "camo-columns";

    private final Visibility.Camera[] camera = { null };

    @Override
    public String name() {
        return PHASE;
    }

    @Override
    protected List<Script> scripts(SmokeRun run) {
        List<Script> scripts = new ArrayList<>();
        // the interface stays hidden for the world pictures, as after the block rows
        scripts.add(new Script("camouflage:interface").run("hide the interface", r -> r.client().options.hideGui = true));
        scripts.add(picture(run, EMPTY, "Core", "the empty stage of the camouflage pictures (six cells)", level -> {}));
        scripts.add(picture(run, REAL, "Core", "the six real blocks the camouflage imitates, left to right: " + names(), level -> {
            for (int i = 0; i < CAMOS.size(); i++) Stage.place(level, cell(level, i), CAMOS.get(i));
        }));
        scripts.add(camouflaged(run, PLATFORMS, "SimpleBlocks", "six Ethereal Platforms", i -> TTRegistry.block(LibBlockNames.PLATFORM)));
        scripts.add(camouflaged(run, DEVICE_ROW, "TileBlocks", "the devices " + DEVICES, i -> TTRegistry.block(DEVICES.get(i))));
        scripts.add(cleared(run));
        scripts.add(picture(run, COLUMNS_REAL, "Core", "columns of two real blocks each: " + names(), level -> {
            for (int i = 0; i < CAMOS.size(); i++) {
                Stage.place(level, cell(level, i), CAMOS.get(i));
                Stage.place(level, cell(level, i).above(), CAMOS.get(i));
            }
        }));
        scripts.add(columns(run));
        return scripts;
    }

    private interface Builder {
        void build(ServerLevel level);
    }

    private interface BlockOf {
        Block block(int cell);
    }

    private static BlockPos cell(ServerLevel level, int index) {
        return Stage.cell(level, index);
    }

    private static String names() {
        StringBuilder text = new StringBuilder();
        for (BlockState camo : CAMOS) text.append(text.length() == 0 ? "" : ", ").append(label(camo));
        return text.toString();
    }

    private static String label(BlockState camo) {
        String path = BuiltInRegistries.BLOCK.getKey(camo.getBlock()).getPath();
        return camo.hasProperty(RotatedPillarBlock.AXIS) ? path + " axis=" + camo.getValue(RotatedPillarBlock.AXIS).getSerializedName() : path;
    }

    /** Clears the stage, builds a scene, labels the six cells with the block they show and takes a picture with the six-cell camera. */
    private Script picture(SmokeRun run, String name, String group, String shows, Builder build) {
        Script script = new Script(name);
        script.then("build " + name, run.onServer(server -> {
            ServerLevel level = server.overworld();
            Stage.clear(server, level);
            build.build(level);
            if (!name.equals(EMPTY)) labels(server, level, name.equals(COLUMNS_REAL) ? 1 : 0);
            Stage.camera(run.serverPlayer(), CAMOS.size(), 0);
        }));
        script.until("the camera", () -> run.client().player.getZ() > Stage.Z + 3, 20);
        return shoot(run, script, name, group, shows);
    }

    private Script shoot(SmokeRun run, Script script, String name, String group, String shows) {
        script.until("the chunk sections to be built", () -> run.client().levelRenderer.hasRenderedAllSections(), 40);
        script.frames(10);
        script.run("the camera of the picture", r -> camera[0] = Visibility.Camera.of(r.client()));
        script.then("picture", run.shots().shot(name, group, shows), 60);
        return script;
    }

    /** A name tag over each of the six cells, {@code above} blocks higher (over the top of a column). */
    private static void labels(MinecraftServer server, ServerLevel level, int above) {
        for (int i = 0; i < CAMOS.size(); i++) Stage.label(server, cell(level, i).above(above), label(CAMOS.get(i)), i % 2);
    }

    /** Every cell has the camouflage {@code expected(i)} on the client (null: none). */
    private static boolean clientHas(SmokeRun run, BlockPos[] cells, java.util.function.IntFunction<BlockState> expected) {
        Level level = run.client().level;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == null) return false;
            if (CamouflageHooks.camouflageOf(level.getBlockEntity(cells[i])) != expected.apply(i)) return false;
        }
        return true;
    }

    private static void setCamo(ServerLevel level, BlockPos pos, BlockState camo) {
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile instanceof TileCamo platform) platform.setCamo(camo);
        else if (tile instanceof CamoDeviceBlockEntity device) device.setCamo(camo);
        else throw new IllegalStateException("no camouflage block entity at " + pos + ": " + tile);
    }

    /** A row of camouflage blocks that imitate stone first and then the six blocks; compared with the real blocks. */
    private Script camouflaged(SmokeRun run, String name, String group, String what, BlockOf blocks) {
        Script script = new Script(name);
        BlockPos[] cells = new BlockPos[CAMOS.size()];
        script.then("place the camouflage blocks with stone", run.onServer(server -> {
            ServerLevel level = server.overworld();
            Stage.clear(server, level);
            for (int i = 0; i < cells.length; i++) {
                cells[i] = cell(level, i);
                Stage.place(level, cells[i], blocks.block(i).defaultBlockState());
                setCamo(level, cells[i], FIRST);
            }
            labels(server, level, 0);
            Stage.camera(run.serverPlayer(), CAMOS.size(), 0);
        }));
        script.until("the client has the stone camouflage", () -> clientHas(run, cells, i -> FIRST), 20);
        script.until("the camera", () -> run.client().player.getZ() > Stage.Z + 3, 20);
        script.until("the chunk sections to be built", () -> run.client().levelRenderer.hasRenderedAllSections(), 40);
        script.frames(6);
        script.then("change the camouflage of every cell", run.onServer(server -> {
            for (int i = 0; i < cells.length; i++) setCamo(server.overworld(), cells[i], CAMOS.get(i));
        }));
        script.until("the client has the new camouflage", () -> clientHas(run, cells, CAMOS::get), 20);
        shoot(run, script, name, group, what + " camouflaged first as stone, then (no block state changes) as, left to right: " + names());
        script.run("compare with the real blocks", r -> compare(r, name, group, cells, what));
        return script;
    }

    private void compare(SmokeRun run, String name, String group, BlockPos[] cells, String what) {
        Path dir = run.report().outDir().resolve("screenshots");
        for (int i = 0; i < cells.length; i++) {
            String target = "camouflage:" + name + "/" + label(CAMOS.get(i));
            double seen = Visibility.changed(dir.resolve(name + ".png"), dir.resolve(EMPTY + ".png"), camera[0], cells[i]);
            double box = Visibility.changed(dir.resolve(name + ".png"), dir.resolve(REAL + ".png"), camera[0], cells[i]);
            double unlike = Visibility.unlikeOnSilhouette(dir.resolve(name + ".png"), dir.resolve(REAL + ".png"), dir.resolve(EMPTY + ".png"),
                    camera[0], cells[i]);
            String detail = "cell " + i + " of " + what + ": " + percent(seen) + " of its cube's box differ from the empty stage; "
                    + percent(unlike) + " of the real block's silhouette differ from the real block (" + percent(box)
                    + " of the box, the floor around it included)";
            if (seen >= Visibility.SEEN) run.report().pass(PHASE, "camo_visible", group, target, detail);
            else run.report().fail(PHASE, "camo_not_visible", group, target, detail + " (the camouflage is not drawn)");
            if (unlike >= 0 && unlike <= LIKE_REAL) run.report().pass(PHASE, "camo_like_real", group, target, detail);
            else run.report().fail(PHASE, "camo_unlike_real", group, target, detail + " (more than " + percent(LIKE_REAL) + ")");
        }
    }

    private static String percent(double share) {
        return share < 0 ? "(not comparable)" : Math.round(share * 1000) / 10.0 + "%";
    }

    /** The platforms of {@link #PLATFORMS} with the camouflage taken off again: the platform's own model, drawn again. */
    private Script cleared(SmokeRun run) {
        Script script = new Script(CLEARED);
        BlockPos[] cells = new BlockPos[CAMOS.size()];
        script.then("place camouflaged platforms", run.onServer(server -> {
            ServerLevel level = server.overworld();
            Stage.clear(server, level);
            for (int i = 0; i < cells.length; i++) {
                cells[i] = cell(level, i);
                Stage.place(level, cells[i], TTRegistry.block(LibBlockNames.PLATFORM).defaultBlockState());
                setCamo(level, cells[i], CAMOS.get(i));
            }
            labels(server, level, 0);
            Stage.camera(run.serverPlayer(), CAMOS.size(), 0);
        }));
        script.until("the client has the camouflage", () -> clientHas(run, cells, CAMOS::get), 20);
        script.until("the camera", () -> run.client().player.getZ() > Stage.Z + 3, 20);
        script.until("the chunk sections to be built", () -> run.client().levelRenderer.hasRenderedAllSections(), 40);
        script.frames(6);
        script.then("take the camouflage off", run.onServer(server -> {
            for (BlockPos pos : cells) setCamo(server.overworld(), pos, null);
        }));
        script.until("the client has no camouflage", () -> clientHas(run, cells, i -> null), 20);
        shoot(run, script, CLEARED, "SimpleBlocks", "six Ethereal Platforms whose camouflage (" + names() + ") was taken off: the platform's own look");
        script.run("the platforms are themselves again", r -> {
            Path dir = r.report().outDir().resolve("screenshots");
            for (int i = 0; i < cells.length; i++) {
                String target = "camouflage:" + CLEARED + "/" + i;
                double seen = Visibility.changed(dir.resolve(CLEARED + ".png"), dir.resolve(EMPTY + ".png"), camera[0], cells[i]);
                double unlike = Visibility.changed(dir.resolve(CLEARED + ".png"), dir.resolve(PLATFORMS + ".png"), camera[0], cells[i]);
                String detail = "cell " + i + ": " + percent(seen) + " differ from the empty stage, " + percent(unlike)
                        + " from the camouflaged platform (" + label(CAMOS.get(i)) + ")";
                if (seen >= Visibility.SEEN && unlike >= Visibility.SEEN) r.report().pass(PHASE, "camo_cleared", "SimpleBlocks", target, detail);
                else r.report().fail(PHASE, "camo_not_cleared", "SimpleBlocks", target, detail + " (the platform's own model is not drawn again)");
            }
        });
        return script;
    }

    /** A real block under a platform that imitates it, for each of the six: the faces between them and the light; recorded, not judged. */
    private Script columns(SmokeRun run) {
        Script script = new Script(COLUMNS);
        BlockPos[] cells = new BlockPos[CAMOS.size()];
        script.then("build the columns", run.onServer(server -> {
            ServerLevel level = server.overworld();
            Stage.clear(server, level);
            for (int i = 0; i < cells.length; i++) {
                BlockPos below = cell(level, i);
                cells[i] = below.above();
                Stage.place(level, below, CAMOS.get(i));
                Stage.place(level, cells[i], TTRegistry.block(LibBlockNames.PLATFORM).defaultBlockState());
                setCamo(level, cells[i], CAMOS.get(i));
            }
            labels(server, level, 1);
            Stage.camera(run.serverPlayer(), CAMOS.size(), 0);
        }));
        script.until("the client has the camouflage", () -> clientHas(run, cells, CAMOS::get), 20);
        script.until("the camera", () -> run.client().player.getZ() > Stage.Z + 3, 20);
        shoot(run, script, COLUMNS, "SimpleBlocks", "columns of a real block under an Ethereal Platform that imitates it, left to right: " + names());
        script.run("compare the upper blocks with the real columns", r -> {
            Path dir = r.report().outDir().resolve("screenshots");
            for (int i = 0; i < cells.length; i++) {
                double unlike = Visibility.changed(dir.resolve(COLUMNS + ".png"), dir.resolve(COLUMNS_REAL + ".png"), camera[0], cells[i]);
                r.report().add(PHASE, "camo_column", "SimpleBlocks", "camouflage:" + COLUMNS + "/" + label(CAMOS.get(i)), SmokeReport.Result.INFO,
                        percent(unlike) + " of the pixels of the upper cube differ from a column of two real blocks (the faces between a real block "
                                + "and a camouflage block are drawn as between that block and a block that occludes nothing)");
            }
        });
        return script;
    }
}
