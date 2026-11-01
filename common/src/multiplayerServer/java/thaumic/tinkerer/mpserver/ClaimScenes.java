package thaumic.tinkerer.mpserver;

import dev.architectury.platform.Platform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.wands.WandHelper;
import thaumic.tinkerer.common.block.tile.TileRPlacer;
import thaumic.tinkerer.common.item.foci.FociComponents.DislocationData;
import thaumic.tinkerer.common.item.foci.ItemFocusDislocation;
import thaumic.tinkerer.common.item.kami.KamiComponents;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.registry.TTRegistry;
import thaumic.tinkerer.mpserver.Scenes.Outcome;
import thaumic.tinkerer.mpserver.Scenes.Stage;

/**
 * The claims of a real protection mod in the multiplayer check (docs/MULTIPLAYER_VERIFICATION.md, section h): Open Parties and
 * Claims (OPAC), which only the multiplayer runs put on the server's class path. The lead {@code TTAlpha} claims two chunks with
 * OPAC's own command, as a player does; the second player {@code TTBravo}, who is in no party with it, uses the add-on's tools and
 * devices on blocks inside the claim (refused, nothing changes) and outside it (they work); the lead uses them inside its own claim.
 * Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), never part of a
 * release jar.
 *
 * <p>The add-on knows nothing of OPAC: it asks the loader's break and place events ({@code BlockProtection}) and OPAC answers them.
 * Only this harness looks OPAC up, by reflection (OPAC is not on its compile class path), to read who owns a chunk.</p>
 *
 * <p>The claim: the chunks (6, -2) and (7, -2), x 96..127, z -32..-17. The second player stands in the wilderness west (x &lt;= 95)
 * or south (z &gt;= -16) of it and reaches over the edge; the pit of its pickaxe scene is inside.</p>
 */
final class ClaimScenes {
    private static final int G = Scenes.G;
    static final int CLAIM_X0 = 96;
    static final int CLAIM_Z0 = -32;
    static final int CLAIM_X1 = 127;
    static final int CLAIM_Z1 = -17;
    /** What the lead types, one chunk per command (OPAC 0.33.0 claims only the middle chunk of a 2 x 1 area). */
    static final String CLAIM_COMMAND = "oclaims claim 96 -32 111 -17 and /oclaims claim 112 -32 127 -17";
    static final String OPAC = "openpartiesandclaims";

    // The second player's hotbar.
    static final int SLOT_AXE = 3;

    // ---- the lead, in its own claim ---------------------------------------------------------------------------------
    static final BlockPos A_STONE = new BlockPos(114, G + 1, -30);
    static final BlockPos A_WALL = new BlockPos(120, G, -30);
    static final BlockPos A_BEDROCK = new BlockPos(115, -64, -22);
    static final List<BlockPos> A_TREE = column(new BlockPos(120, G, -22), 4);
    static final BlockPos A_PLACER = new BlockPos(112, G, -24);
    static final BlockPos A_DISLOCATOR = new BlockPos(112, G, -20);
    static final BlockPos A_END = new BlockPos(115, G, -18);

    // ---- the second player: into the claim, over its edge, and outside -------------------------------------------
    static final BlockPos B_STONE_IN = new BlockPos(98, G + 1, -26);
    /**
     * Two chunks west of the claim (chunk 4): OPAC refuses a stranger's item use (a right click into the air, which is how the wand
     * casts its focus) in every chunk next to a claim as well ({@code claims.protection.neighborChunksItemUse}, on by default), so a
     * cast from chunk 5 would be refused by OPAC itself, outside the claim.
     */
    static final BlockPos B_STONE_OUT = new BlockPos(72, G + 1, -30);
    static final BlockPos B_WALL_IN = new BlockPos(104, G, -19);
    /** On the edge: its square, x 93..97, reaches two columns into the claim. */
    static final BlockPos B_WALL_EDGE = new BlockPos(95, G, -30);
    static final BlockPos B_WALL_OUT = new BlockPos(88, G, -30);
    static final BlockPos B_BEDROCK_IN = new BlockPos(106, -64, -24);
    static final BlockPos B_BEDROCK_OUT = new BlockPos(89, -64, -22);
    static final List<BlockPos> B_TREE_IN = column(new BlockPos(100, G, -18), 3);
    /** On the edge: a trunk outside (x 95) with a branch into the claim (x 96, 97) at the top. */
    static final List<BlockPos> B_TREE_EDGE = List.of(new BlockPos(95, G, -22), new BlockPos(95, G + 1, -22), new BlockPos(95, G + 2, -22),
            new BlockPos(96, G + 2, -22), new BlockPos(97, G + 2, -22));
    static final List<BlockPos> B_TREE_OUT = column(new BlockPos(84, G, -22), 3);
    /** A placer in the wilderness south of the claim, aiming two blocks north: into the claim. */
    static final BlockPos B_PLACER_IN = new BlockPos(110, G, -15);
    static final BlockPos B_PLACER_OUT = new BlockPos(84, G, -12);
    /** A Dislocator in the wilderness whose front is the claim's first row (air) and whose end (emerald) is in the wilderness. */
    static final BlockPos B_DISLOCATOR_IN = new BlockPos(118, G, -16);
    static final BlockPos B_END_IN = new BlockPos(121, G, -13);
    static final BlockPos B_DISLOCATOR_OUT = new BlockPos(92, G, -12);
    static final BlockPos B_END_OUT = new BlockPos(95, G, -9);

    static final List<String> LEAD_NAMES = List.of("a_claim", "a_dislocation_own", "a_mirror_own", "a_pickaxe_own", "a_axe_own",
            "a_placer_own", "a_dislocator_own");
    static final List<String> PEER_NAMES = List.of("b_dislocation_out", "b_dislocation_in", "b_dislocation_place_out", "b_mirror_in", "b_mirror_edge", "b_mirror_out",
            "b_pickaxe_in", "b_pickaxe_out", "b_axe_in", "b_axe_edge", "b_axe_out", "b_placer_into_claim", "b_placer_out",
            "b_dislocator_into_claim", "b_dislocator_out");
    static final List<String> NAMES;

    static {
        List<String> names = new ArrayList<>(LEAD_NAMES);
        names.addAll(PEER_NAMES);
        NAMES = List.copyOf(names);
    }

    private ClaimScenes() {}

    private static List<BlockPos> column(BlockPos bottom, int height) {
        List<BlockPos> logs = new ArrayList<>();
        for (int i = 0; i < height; i++) logs.add(bottom.above(i));
        return logs;
    }

    static boolean isMirror(String name) {
        return name.startsWith("a_mirror") || name.startsWith("b_mirror");
    }

    // ---- the stages ------------------------------------------------------------------------------------------------

    private static Stage south(String name, BlockPos target, int slot, double back) {
        return new Stage(name, new Vec3(target.getX() + 0.5, G, target.getZ() + 0.5 + back), Scenes.faceCenter(target, Direction.SOUTH),
                target, Direction.SOUTH, slot, false);
    }

    private static Stage pit(String name, BlockPos bedrock) {
        return new Stage(name, new Vec3(bedrock.getX() - 0.5, -63, bedrock.getZ() + 0.5), Scenes.faceCenter(bedrock, Direction.UP), bedrock,
                Direction.UP, Scenes.SLOT_PICK, true);
    }

    private static Stage press(String name, BlockPos device) {
        BlockPos button = DeviceScenes.buttonOf(device);
        return new Stage(name, new Vec3(device.getX() + 5.5, G, device.getZ() + 0.5), DeviceScenes.buttonFace(button), button, Direction.EAST,
                Scenes.SLOT_EMPTY, false);
    }

    static ItemStack axe() {
        ItemStack axe = new ItemStack(TTRegistry.item(LibItemNames.ICHOR_AXE_GEM));
        axe.set(KamiComponents.TOOL_MODE.get(), 2);
        return axe;
    }

    static void define(Map<String, Stage> stages) {
        // The lead claims standing in the claim's first chunk.
        stages.put("a_claim", new Stage("a_claim", new Vec3(104.5, G, -24.5), new Vec3(104.5, G + 1.62, -28.5), null, null, Scenes.SLOT_EMPTY, false));
        stages.put("a_dislocation_own", south("a_dislocation_own", A_STONE, Scenes.SLOT_DISLOCATION, 3.5));
        Stage mirror = south("a_mirror_own", A_WALL, Scenes.SLOT_MIRROR, 3.5);
        // The lead's first stack was used by the spawn protection scenes: the harness gives it another.
        stages.put("a_mirror_own", new Stage(mirror.name(), mirror.feet(), mirror.aim(), mirror.target(), mirror.face(), mirror.slot(), false,
                player -> {
                    player.getInventory().add(new ItemStack(Items.STONE, 64));
                    player.inventoryMenu.broadcastChanges();
                }, null, false));
        stages.put("a_pickaxe_own", pit("a_pickaxe_own", A_BEDROCK));
        Stage tree = south("a_axe_own", A_TREE.get(0), DeviceScenes.SLOT_EXTRA, 2.5);
        stages.put("a_axe_own", new Stage(tree.name(), tree.feet(), tree.aim(), tree.target(), tree.face(), tree.slot(), false,
                player -> DeviceScenes.setHotbar(player, DeviceScenes.SLOT_EXTRA, axe()), "slot", false));
        stages.put("a_placer_own", press("a_placer_own", A_PLACER));
        stages.put("a_dislocator_own", press("a_dislocator_own", A_DISLOCATOR));

        stages.put("b_dislocation_in", new Stage("b_dislocation_in", new Vec3(B_STONE_IN.getX() - 3.5, G, B_STONE_IN.getZ() + 0.5),
                Scenes.faceCenter(B_STONE_IN, Direction.WEST), B_STONE_IN, Direction.WEST, Scenes.SLOT_DISLOCATION, false));
        stages.put("b_dislocation_out", south("b_dislocation_out", B_STONE_OUT, Scenes.SLOT_DISLOCATION, 3.5));
        // After the refused cast into the claim (the focus still carries the stone): the stone goes back onto its cobblestone.
        stages.put("b_dislocation_place_out", new Stage("b_dislocation_place_out", new Vec3(B_STONE_OUT.getX() + 0.5, G, B_STONE_OUT.getZ() + 4.0),
                Scenes.faceCenter(B_STONE_OUT.below(), Direction.UP), B_STONE_OUT.below(), Direction.UP, Scenes.SLOT_DISLOCATION, false));
        stages.put("b_mirror_in", south("b_mirror_in", B_WALL_IN, Scenes.SLOT_MIRROR, 3.5));
        stages.put("b_mirror_edge", south("b_mirror_edge", B_WALL_EDGE, Scenes.SLOT_MIRROR, 3.5));
        stages.put("b_mirror_out", south("b_mirror_out", B_WALL_OUT, Scenes.SLOT_MIRROR, 3.5));
        stages.put("b_pickaxe_in", pit("b_pickaxe_in", B_BEDROCK_IN));
        stages.put("b_pickaxe_out", pit("b_pickaxe_out", B_BEDROCK_OUT));
        stages.put("b_axe_in", south("b_axe_in", B_TREE_IN.get(0), SLOT_AXE, 2.5));
        stages.put("b_axe_edge", south("b_axe_edge", B_TREE_EDGE.get(0), SLOT_AXE, 2.5));
        stages.put("b_axe_out", south("b_axe_out", B_TREE_OUT.get(0), SLOT_AXE, 2.5));
        stages.put("b_placer_into_claim", press("b_placer_into_claim", B_PLACER_IN));
        stages.put("b_placer_out", press("b_placer_out", B_PLACER_OUT));
        stages.put("b_dislocator_into_claim", press("b_dislocator_into_claim", B_DISLOCATOR_IN));
        stages.put("b_dislocator_out", press("b_dislocator_out", B_DISLOCATOR_OUT));
    }

    /** The second player's tools (it joins with them, as the lead does with its own). */
    static void equipPeer(Inventory inventory) {
        inventory.setItem(Scenes.SLOT_DISLOCATION, Scenes.wand(LibItemNames.FOCUS_DISLOCATION));
        ItemStack mirror = new ItemStack(TTRegistry.item(LibItemNames.PLACEMENT_MIRROR));
        mirror.set(KamiComponents.MIRROR_STATE.get(), Blocks.STONE.defaultBlockState());
        mirror.set(KamiComponents.MIRROR_SIZE.get(), Scenes.MIRROR_SIZE);
        inventory.setItem(Scenes.SLOT_MIRROR, mirror);
        inventory.setItem(Scenes.SLOT_PICK, new ItemStack(TTRegistry.item(LibItemNames.ICHOR_PICK_GEM)));
        inventory.setItem(SLOT_AXE, axe());
        inventory.setItem(Scenes.SLOT_STONE, new ItemStack(Items.STONE, 64));
    }

    // ---- the world --------------------------------------------------------------------------------------------------

    static void build(MinecraftServer server, ServerLevel level, ServerReport report) {
        report.fact("opac", Platform.isModLoaded(OPAC) ? Platform.getMod(OPAC).getVersion() : "not installed");
        for (BlockPos stone : List.of(A_STONE, B_STONE_IN, B_STONE_OUT)) {
            Scenes.put(level, stone.below(), Blocks.COBBLESTONE.defaultBlockState());
            Scenes.put(level, stone, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos wall : List.of(A_WALL, B_WALL_IN, B_WALL_EDGE, B_WALL_OUT)) {
            Scenes.put(level, wall, Blocks.POLISHED_ANDESITE.defaultBlockState());
            for (BlockPos at : Scenes.square(wall)) Scenes.put(level, at, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos bedrock : List.of(A_BEDROCK, B_BEDROCK_IN, B_BEDROCK_OUT)) {
            for (int y = -63; y <= G; y++) {
                Scenes.put(level, new BlockPos(bedrock.getX() - 1, y, bedrock.getZ()), Blocks.AIR.defaultBlockState());
                Scenes.put(level, new BlockPos(bedrock.getX(), y, bedrock.getZ()), Blocks.AIR.defaultBlockState());
            }
        }
        for (List<BlockPos> tree : List.of(A_TREE, B_TREE_IN, B_TREE_EDGE, B_TREE_OUT)) {
            for (BlockPos log : tree) Scenes.put(level, log, Blocks.OAK_LOG.defaultBlockState());
        }
        // The placers face north and place two blocks away (one for the lead's); the Dislocators face north too.
        for (BlockPos placer : List.of(A_PLACER, B_PLACER_IN, B_PLACER_OUT)) {
            DeviceScenes.deviceRow(level, placer, DeviceScenes.placer(Direction.NORTH));
            DeviceScenes.loadPlacer(level, placer, placer.equals(A_PLACER) ? 1 : 2);
        }
        DeviceScenes.deviceRow(level, A_DISLOCATOR, DeviceScenes.dislocator(Direction.NORTH));
        Scenes.put(level, A_DISLOCATOR.north(), Blocks.LAPIS_BLOCK.defaultBlockState());
        Scenes.put(level, A_END, Blocks.EMERALD_BLOCK.defaultBlockState());
        DeviceScenes.bind(level, A_DISLOCATOR, A_END);
        for (BlockPos[] pair : new BlockPos[][] { { B_DISLOCATOR_IN, B_END_IN }, { B_DISLOCATOR_OUT, B_END_OUT } }) {
            DeviceScenes.deviceRow(level, pair[0], DeviceScenes.dislocator(Direction.NORTH));
            Scenes.put(level, pair[1], Blocks.EMERALD_BLOCK.defaultBlockState());
            DeviceScenes.bind(level, pair[0], pair[1]);
        }
    }

    // ---- OPAC, by reflection ------------------------------------------------------------------------------------------

    /** The owner of a chunk of the overworld in OPAC, or null when nobody claimed it; throws when OPAC cannot be asked. */
    @Nullable
    static UUID owner(MinecraftServer server, int chunkX, int chunkZ) throws ReflectiveOperationException {
        Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
        Object instance = api.getMethod("get", MinecraftServer.class).invoke(null, server);
        Object manager = api.getMethod("getServerClaimsManager").invoke(instance);
        Class<?> managerApi = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
        Object claim = managerApi.getMethod("get", ResourceLocation.class, int.class, int.class).invoke(manager, Level.OVERWORLD.location(),
                chunkX, chunkZ);
        if (claim == null) return null;
        Class<?> claimApi = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI");
        return (UUID) claimApi.getMethod("getPlayerId").invoke(claim);
    }

    /** Both chunks of the claim belong to the lead. */
    static boolean claimed(MinecraftServer server) {
        ServerPlayer lead = server.getPlayerList().getPlayerByName(Scenes.LEAD);
        try {
            UUID first = owner(server, CLAIM_X0 >> 4, CLAIM_Z0 >> 4);
            UUID second = owner(server, CLAIM_X1 >> 4, CLAIM_Z0 >> 4);
            UUID leadId = lead == null ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + Scenes.LEAD).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    : lead.getUUID();
            return leadId.equals(first) && leadId.equals(second);
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    /** The second player's scenes wait until the lead has claimed. */
    @Nullable
    static String waitBefore(ServerPlayer player, String name) {
        if (name.startsWith("b_") && !claimed(player.server)) return "the lead has not claimed the chunks yet";
        return null;
    }

    // ---- /ttmp check ------------------------------------------------------------------------------------------------

    private static boolean late(Map<String, Object> before, int seconds) {
        Object at = before.get("staged_at");
        return !(at instanceof Long started) || System.nanoTime() - started > seconds * 1_000_000_000L;
    }

    static boolean inClaim(BlockPos pos) {
        return pos.getX() >= CLAIM_X0 && pos.getX() <= CLAIM_X1 && pos.getZ() >= CLAIM_Z0 && pos.getZ() <= CLAIM_Z1;
    }

    private static String slot(ServerLevel level, BlockPos placer) {
        if (!(level.getBlockEntity(placer) instanceof TileRPlacer tile)) return "none";
        ItemStack stack = tile.getItem(0);
        return stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount();
    }

    @Nullable
    static Outcome check(ServerPlayer player, String name, @Nullable String arg, Map<String, Object> before) {
        if (!NAMES.contains(name)) return null;
        ServerLevel level = player.serverLevel();
        Map<String, Object> values = new LinkedHashMap<>();
        String who = name.startsWith("a_") ? Scenes.LEAD + " (the claim's owner)" : Scenes.PEER + " (in no party with the owner)";
        switch (name) {
            case "a_claim" -> {
                boolean claimed = claimed(player.server);
                String owners;
                try {
                    owners = owner(player.server, CLAIM_X0 >> 4, CLAIM_Z0 >> 4) + ", " + owner(player.server, CLAIM_X1 >> 4, CLAIM_Z0 >> 4);
                } catch (ReflectiveOperationException | LinkageError e) {
                    owners = "OPAC could not be asked: " + e;
                }
                if (!claimed && !late(before, 20)) return Outcome.waiting("the chunks are not claimed yet");
                values.put("claimed", claimed);
                return Outcome.of(claimed, Scenes.LEAD + " (" + player.getUUID() + ") ran /" + CLAIM_COMMAND + ": OPAC's owners of the chunks ("
                        + (CLAIM_X0 >> 4) + ", " + (CLAIM_Z0 >> 4) + ") and (" + (CLAIM_X1 >> 4) + ", " + (CLAIM_Z0 >> 4) + "): " + owners, values);
            }
            case "a_dislocation_own", "b_dislocation_out" -> {
                BlockPos stone = name.equals("a_dislocation_own") ? A_STONE : B_STONE_OUT;
                ItemStack wand = player.getInventory().getItem(Scenes.SLOT_DISLOCATION);
                DislocationData carried = ItemFocusDislocation.getData(wand);
                BlockState state = level.getBlockState(stone);
                AspectList was = Scenes.parse(String.valueOf(before.get("vis")));
                AspectList is = WandHelper.getAllVis(wand);
                boolean paid = is.getAmount(Aspect.ENTROPY) < was.getAmount(Aspect.ENTROPY) && is.getAmount(Aspect.ORDER) < was.getAmount(Aspect.ORDER);
                values.put("vis", Scenes.vis(wand));
                values.put("blocks", Scenes.blocks(level, List.of(stone)));
                values.put("carried", carried == null ? "nothing" : Scenes.id(carried.state()));
                values.put("server_using", player.isUsingItem());
                values.put("server_held", BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()) + "@" + player.getInventory().selected);
                boolean ok = state.isAir() && carried != null && carried.state().is(Blocks.STONE) && paid;
                return Outcome.of(ok, who + " cast the Focus of Dislocation on the stone at " + Scenes.pos(stone) + (inClaim(stone) ? " in the claim"
                        : " outside the claim (two chunks away)") + ": block " + before.get("state") + " -> " + Scenes.id(state) + ", vis "
                        + before.get("vis") + " -> " + Scenes.vis(wand) + ", the focus carries " + (carried == null ? "nothing" : carried.state())
                        + " (expected taken and paid)", values);
            }
            case "b_dislocation_in", "b_dislocation_place_out" -> {
                // in: the focus carries the stone taken outside and is cast on the claimed stone's west face, which would put the load at
                // x 97, in the claim; place_out: the load goes back onto its cobblestone outside, after the refusal.
                boolean inside = name.equals("b_dislocation_in");
                BlockPos at = inside ? B_STONE_IN.west() : B_STONE_OUT;
                ItemStack wand = player.getInventory().getItem(Scenes.SLOT_DISLOCATION);
                DislocationData carried = ItemFocusDislocation.getData(wand);
                BlockState there = level.getBlockState(at);
                List<BlockPos> shown = inside ? List.of(at, B_STONE_IN) : List.of(at);
                values.put("vis", Scenes.vis(wand));
                values.put("blocks", Scenes.blocks(level, shown));
                values.put("carried", carried == null ? "nothing" : Scenes.id(carried.state()));
                values.put("server_using", player.isUsingItem());
                values.put("server_held", BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()) + "@" + player.getInventory().selected);
                boolean ok = inside ? there.isAir() && level.getBlockState(B_STONE_IN).is(Blocks.STONE) && carried != null && carried.state().is(Blocks.STONE)
                        : there.is(Blocks.STONE) && carried == null;
                ok &= Scenes.vis(wand).equals(before.get("vis"));
                return Outcome.of(ok, who + (inside ? " cast the loaded Focus of Dislocation on the claimed stone at " + Scenes.pos(B_STONE_IN)
                        + " (the load would go to " + Scenes.pos(at) + ", in the claim)" : " put the load back at " + Scenes.pos(at)
                        + ", outside the claim, right after that refusal") + ": the block there is " + Scenes.id(there) + ", the focus carries "
                        + (carried == null ? "nothing" : carried.state()) + ", vis " + before.get("vis") + " -> " + Scenes.vis(wand) + " (expected "
                        + (inside ? "nothing placed, the load kept" : "placed, the focus empty") + ")", values);
            }
            case "a_mirror_own", "b_mirror_in", "b_mirror_edge", "b_mirror_out" -> {
                BlockPos wall = switch (name) {
                    case "a_mirror_own" -> A_WALL;
                    case "b_mirror_in" -> B_WALL_IN;
                    case "b_mirror_edge" -> B_WALL_EDGE;
                    default -> B_WALL_OUT;
                };
                int inside = 0;
                int outside = 0;
                int squareInside = 0;
                int squareOutside = 0;
                for (BlockPos at : Scenes.square(wall)) {
                    boolean stone = level.getBlockState(at).is(Blocks.STONE);
                    if (inClaim(at)) {
                        squareInside++;
                        if (stone) inside++;
                    } else {
                        squareOutside++;
                        if (stone) outside++;
                    }
                }
                // The owner may build in its claim; the second player may not.
                boolean owner = name.startsWith("a_");
                int expectedInside = owner ? squareInside : 0;
                int expectedOutside = name.equals("b_mirror_in") ? 0 : squareOutside;
                int stoneNow = Scenes.count(player, Items.STONE);
                int stoneBefore = (Integer) before.get("stone");
                values.put("placed_inside", inside);
                values.put("placed_outside", outside);
                values.put("stone", stoneNow);
                values.put("blocks", Scenes.blocks(level, Scenes.square(wall)));
                boolean ok = inside == expectedInside && outside == expectedOutside && stoneNow == stoneBefore - inside - outside;
                return Outcome.of(ok, who + " used the Placement Mirror on the wall at " + Scenes.pos(wall) + ": of its 5 x 5 square " + inside + " of "
                        + squareInside + " in the claim placed (expected " + expectedInside + "), " + outside + " of " + squareOutside
                        + " outside (expected " + expectedOutside + "); stone " + stoneBefore + " -> " + stoneNow, values);
            }
            case "a_pickaxe_own", "b_pickaxe_in", "b_pickaxe_out" -> {
                BlockPos bedrock = name.equals("a_pickaxe_own") ? A_BEDROCK : name.equals("b_pickaxe_in") ? B_BEDROCK_IN : B_BEDROCK_OUT;
                BlockState state = level.getBlockState(bedrock);
                String expected = name.equals("b_pickaxe_in") ? "minecraft:bedrock" : "thaumictinkerer:" + LibBlockNames.PORTAL;
                values.put("blocks", Scenes.blocks(level, List.of(bedrock)));
                return Outcome.of(Scenes.id(state).equals(expected), who + " made a sneaking left click with the Awakened Ichorium Pickaxe on the"
                        + " bedrock at " + Scenes.pos(bedrock) + (inClaim(bedrock) ? " in the claim" : " outside the claim") + ": "
                        + before.get("state") + " -> " + Scenes.id(state) + " (expected " + expected + "; the server saw the player sneaking: "
                        + player.isShiftKeyDown() + ")", values);
            }
            case "a_axe_own", "b_axe_in", "b_axe_edge", "b_axe_out" -> {
                List<BlockPos> tree = switch (name) {
                    case "a_axe_own" -> A_TREE;
                    case "b_axe_in" -> B_TREE_IN;
                    case "b_axe_edge" -> B_TREE_EDGE;
                    default -> B_TREE_OUT;
                };
                int insideLogs = 0;
                int insideTotal = 0;
                int outsideLogs = 0;
                int outsideTotal = 0;
                for (BlockPos log : tree) {
                    boolean there = level.getBlockState(log).is(Blocks.OAK_LOG);
                    if (inClaim(log)) {
                        insideTotal++;
                        if (there) insideLogs++;
                    } else {
                        outsideTotal++;
                        if (there) outsideLogs++;
                    }
                }
                boolean clickedGone = !level.getBlockState(tree.get(0)).is(Blocks.OAK_LOG);
                if (!name.equals("b_axe_in") && !clickedGone && !late(before, 5)) return Outcome.waiting("the log is not broken yet");
                values.put("blocks", Scenes.blocks(level, tree));
                boolean ok = switch (name) {
                    // the owner fells the whole tree
                    case "a_axe_own" -> insideLogs == 0;
                    // the second player cannot even start on a log in the claim
                    case "b_axe_in" -> insideLogs == insideTotal;
                    // the clicked log (outside) goes; the logs in the claim stay
                    case "b_axe_edge" -> clickedGone && insideLogs == insideTotal;
                    default -> outsideLogs == 0;
                };
                return Outcome.of(ok, who + " broke the bottom log at " + Scenes.pos(tree.get(0)) + " with the Awakened Ichorium Axe in tree mode: logs"
                        + " left in the claim " + insideLogs + " of " + insideTotal + ", outside " + outsideLogs + " of " + outsideTotal
                        + " (the clicked log " + (clickedGone ? "broken" : "standing") + ")", values);
            }
            case "a_placer_own", "b_placer_into_claim", "b_placer_out" -> {
                BlockPos placer = name.equals("a_placer_own") ? A_PLACER : name.equals("b_placer_into_claim") ? B_PLACER_IN : B_PLACER_OUT;
                BlockPos target = placer.north(name.equals("a_placer_own") ? 1 : 2);
                BlockState state = level.getBlockState(target);
                boolean expectPlaced = name.equals("b_placer_out");
                if (expectPlaced && !state.is(Blocks.OAK_PLANKS) && !late(before, 10)) return Outcome.waiting("the placer has not placed yet");
                values.put("blocks", Scenes.blocks(level, List.of(target)));
                values.put("device", Scenes.pos(placer));
                values.put("slot", slot(level, placer));
                String expectedSlot = "minecraft:oak_planksx" + (expectPlaced ? DeviceScenes.PLANKS - 1 : DeviceScenes.PLANKS);
                boolean ok = (expectPlaced ? state.is(Blocks.OAK_PLANKS) : state.isAir()) && slot(level, placer).equals(expectedSlot);
                String note = name.equals("a_placer_own") ? "; the placer acts as its fake player [Minecraft], not as the claim's owner, and OPAC refuses"
                        + " it as it refuses any player outside the owner's party (documented: MULTIPLAYER_VERIFICATION.md, h)" : "";
                return Outcome.of(ok, who + " pressed the button of the Remote Placer at " + Scenes.pos(placer) + ", which places at "
                        + Scenes.pos(target) + (inClaim(target) ? " in the claim" : " outside the claim") + ": " + Scenes.id(state) + ", the slot holds "
                        + slot(level, placer) + " (expected " + (expectPlaced ? "planks placed" : "nothing placed") + ")" + note, values);
            }
            case "a_dislocator_own", "b_dislocator_into_claim", "b_dislocator_out" -> {
                BlockPos device = name.equals("a_dislocator_own") ? A_DISLOCATOR : name.equals("b_dislocator_into_claim") ? B_DISLOCATOR_IN : B_DISLOCATOR_OUT;
                BlockPos end = name.equals("a_dislocator_own") ? A_END : name.equals("b_dislocator_into_claim") ? B_END_IN : B_END_OUT;
                BlockPos front = device.north();
                BlockState atFront = level.getBlockState(front);
                BlockState atEnd = level.getBlockState(end);
                boolean expectSwap = name.equals("b_dislocator_out");
                if (expectSwap && !atFront.is(Blocks.EMERALD_BLOCK) && !late(before, 10)) return Outcome.waiting("the Dislocator has not swapped yet");
                values.put("blocks", Scenes.blocks(level, List.of(front, end)));
                BlockState frontBefore = name.equals("a_dislocator_own") ? Blocks.LAPIS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                boolean ok = expectSwap ? atFront.is(Blocks.EMERALD_BLOCK) && atEnd.isAir()
                        : atFront.getBlock() == frontBefore.getBlock() && atEnd.is(Blocks.EMERALD_BLOCK);
                String note = name.equals("a_dislocator_own") ? "; the Dislocator asks as its fake player [Minecraft], not as the claim's owner, and"
                        + " OPAC refuses it (documented: MULTIPLAYER_VERIFICATION.md, h)" : "";
                return Outcome.of(ok, who + " pressed the button of the Transvector Dislocator at " + Scenes.pos(device) + " (front " + Scenes.pos(front)
                        + (inClaim(front) ? " in the claim" : " outside") + ", bound end " + Scenes.pos(end) + (inClaim(end) ? " in the claim" : " outside")
                        + "): front " + Scenes.id(atFront) + ", end " + Scenes.id(atEnd) + " (expected " + (expectSwap ? "swapped" : "unchanged") + ")"
                        + note, values);
            }
            default -> {
                return null;
            }
        }
    }
}
