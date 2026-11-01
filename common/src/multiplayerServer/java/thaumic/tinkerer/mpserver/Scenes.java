package thaumic.tinkerer.mpserver;

import dev.architectury.networking.NetworkManager;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.ItemApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.wands.WandCap;
import thaumcraft.api.wands.WandHelper;
import thaumcraft.api.wands.WandRod;
import thaumic.tinkerer.common.block.tile.TileEnchanter;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.item.foci.FociComponents.DislocationData;
import thaumic.tinkerer.common.item.foci.ItemFocusDislocation;
import thaumic.tinkerer.common.item.kami.KamiComponents;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.network.packet.PacketConfigSync;
import thaumic.tinkerer.common.network.packet.kami.PacketArmorStatus;
import thaumic.tinkerer.common.network.packet.kami.PacketShadowBeam;
import thaumic.tinkerer.common.network.packet.kami.PacketSoulHearts;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * The world of the multiplayer check and what the server knows about every scenario: where it stands, what the lead player
 * is given, where he is sent ({@code /ttmp stage}) and what the server finds afterwards ({@code /ttmp check}). Test-only code,
 * new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), never part of a release
 * jar.
 *
 * <p>The world is the vanilla flat preset (bedrock at y -64, dirt, grass at -61, the first free block at -60). The world spawn is
 * set to (0, -60, 0); {@code server.properties} has {@code spawn-protection=16}, so a non-operator may not build where
 * {@code max(|x|, |z|) <= 16} ({@code DedicatedServer.isUnderSpawnProtection}). Vanilla switches that protection off while the
 * operator list is empty, so the run's {@code ops.json} names {@link #OPERATOR}, a player who never joins; nobody who plays is an
 * operator.</p>
 *
 * <p>The client harness ({@code common/src/clientSmoke}, the {@code Multiplayer*} classes) knows only the names of the
 * scenarios: positions, the slot to hold and the face to look at come with the answer to {@code stage}. Every block change the
 * scenarios test is made by the client through its real connection (a real click of the use or attack key, a container click, a
 * click on a screen button); this class only builds the stage beforehand and looks at the result.</p>
 */
final class Scenes {
    /** The first free block above the flat world's grass. */
    static final int G = -60;
    static final BlockPos SPAWN = new BlockPos(0, G, 0);
    /** Must equal {@code spawn-protection} of the run's {@code server.properties} (written by the Gradle task). */
    static final int RADIUS = 16;

    static final String LEAD = "TTAlpha";
    static final String PEER = "TTBravo";
    static final String MISMATCH = "TTMismatch";
    /** The client with kami.forceenabled=false: the registry sync of the loader refuses it before it can join (no PLAYER_JOIN). */
    static final String MISMATCH_KAMI = "TTMismatchKami";
    /** The client with Kami on that joins the second server, whose config has kami.forceenabled=false. */
    static final String KAMI_ON = "TTKamiOn";
    /** The one operator of the run's {@code ops.json}; never joins. */
    static final String OPERATOR = "TTServerOp";

    // The lead's hotbar.
    static final int SLOT_DISLOCATION = 0;
    static final int SLOT_MIRROR = 1;
    static final int SLOT_PICK = 2;
    static final int SLOT_SWORD = 3;
    static final int SLOT_TOOL = 4;
    static final int SLOT_STONE = 5;
    static final int SLOT_FLIGHT = 6;
    static final int SLOT_EMPTY = 8;

    static final BlockPos GOLD = new BlockPos(-30, G + 1, -20);
    static final BlockPos STONE_IN = new BlockPos(6, G + 1, 6);
    static final BlockPos STONE_OUT = new BlockPos(30, G + 1, 6);
    static final BlockPos WALL_IN = new BlockPos(8, G, -6);
    /** Outside the radius by one block: the mirror's square (x 15..19) reaches two columns into it. */
    static final BlockPos WALL_EDGE = new BlockPos(RADIUS + 1, G, 0);
    static final BlockPos WALL_OUT = new BlockPos(40, G, 0);
    static final BlockPos BEDROCK_IN = new BlockPos(11, -64, -10);
    static final BlockPos BEDROCK_OUT = new BlockPos(31, -64, -10);
    static final BlockPos ENCHANTER = new BlockPos(30, G, 20);
    static final BlockPos TABLET = new BlockPos(30, G, 30);
    static final int MIRROR_SIZE = 5;

    /**
     * Where a player is put for a scenario: feet, the point he looks at, the block and face the client must see in its cross-hair.
     * {@code prepare} is setup of the harness that runs before the baseline is taken (an item put into a hotbar slot, a piece of
     * armour put on): nothing a scenario tests. {@code holding} is the id of the item the client must see in {@code slot} before it
     * acts; {@code entity} means the cross-hair must show an entity instead of a block.
     */
    record Stage(String name, Vec3 feet, Vec3 aim, @Nullable BlockPos target, @Nullable Direction face, int slot, boolean crouch,
            @Nullable java.util.function.Consumer<ServerPlayer> prepare, @Nullable String holding, boolean entity) {
        Stage(String name, Vec3 feet, Vec3 aim, @Nullable BlockPos target, @Nullable Direction face, int slot, boolean crouch) {
            this(name, feet, aim, target, face, slot, crouch, null, null, false);
        }
    }

    static final Map<String, Stage> STAGES = new LinkedHashMap<>();

    static {
        // The Focus of Uprising, cast into the open sky: the client must not lift its own player (modFlight.enabled is off on the server).
        stage("flight", new Vec3(-29.5, G, 0.5), new Vec3(-29.5, G + 40, -4.5), null, null, SLOT_FLIGHT, false);
        stage("dislocation_blacklisted", new Vec3(GOLD.getX() + 0.5, G, GOLD.getZ() + 3.5), faceCenter(GOLD, Direction.SOUTH),
                GOLD, Direction.SOUTH, SLOT_DISLOCATION, false);
        stage("dislocation_inside", new Vec3(STONE_IN.getX() + 0.5, G, STONE_IN.getZ() + 3.5), faceCenter(STONE_IN, Direction.SOUTH),
                STONE_IN, Direction.SOUTH, SLOT_DISLOCATION, false);
        stage("dislocation_outside", new Vec3(STONE_OUT.getX() + 0.5, G, STONE_OUT.getZ() + 3.5), faceCenter(STONE_OUT, Direction.SOUTH),
                STONE_OUT, Direction.SOUTH, SLOT_DISLOCATION, false);
        // Putting the carried stone down (after dislocation_outside took it): against the stone inside the radius, which must be
        // refused, then on top of the cobblestone outside it, where the stone came from.
        stage("dislocation_place_inside", new Vec3(STONE_IN.getX() + 0.5, G, STONE_IN.getZ() + 3.5), faceCenter(STONE_IN, Direction.SOUTH),
                STONE_IN, Direction.SOUTH, SLOT_DISLOCATION, false);
        stage("dislocation_place_outside", new Vec3(STONE_OUT.getX() + 0.5, G, STONE_OUT.getZ() + 3.5), faceCenter(STONE_OUT.below(), Direction.UP),
                STONE_OUT.below(), Direction.UP, SLOT_DISLOCATION, false);
        stage("mirror_inside", new Vec3(WALL_IN.getX() + 0.5, G, WALL_IN.getZ() + 3.5), faceCenter(WALL_IN, Direction.SOUTH),
                WALL_IN, Direction.SOUTH, SLOT_MIRROR, false);
        stage("mirror_boundary", new Vec3(WALL_EDGE.getX() + 0.5, G, WALL_EDGE.getZ() + 3.5), faceCenter(WALL_EDGE, Direction.SOUTH),
                WALL_EDGE, Direction.SOUTH, SLOT_MIRROR, false);
        stage("mirror_outside", new Vec3(WALL_OUT.getX() + 0.5, G, WALL_OUT.getZ() + 3.5), faceCenter(WALL_OUT, Direction.SOUTH),
                WALL_OUT, Direction.SOUTH, SLOT_MIRROR, false);
        // In a pit down to the bedrock; the player stands on the bedrock west of the one he clicks (sneaking, as the pickaxe wants).
        stage("pickaxe_inside", new Vec3(BEDROCK_IN.getX() - 0.5, -63, BEDROCK_IN.getZ() + 0.5), faceCenter(BEDROCK_IN, Direction.UP),
                BEDROCK_IN, Direction.UP, SLOT_PICK, true);
        stage("pickaxe_outside", new Vec3(BEDROCK_OUT.getX() - 0.5, -63, BEDROCK_OUT.getZ() + 0.5), faceCenter(BEDROCK_OUT, Direction.UP),
                BEDROCK_OUT, Direction.UP, SLOT_PICK, true);
        // The Enchanter is 12 pixels high, the Animation Tablet 2.
        stage("enchanter", new Vec3(ENCHANTER.getX() + 0.5, G, ENCHANTER.getZ() + 3.5),
                new Vec3(ENCHANTER.getX() + 0.5, G + 0.375, ENCHANTER.getZ() + 1.0), ENCHANTER, Direction.SOUTH, SLOT_EMPTY, false);
        stage("tablet", new Vec3(TABLET.getX() + 0.5, G, TABLET.getZ() + 2.5),
                new Vec3(TABLET.getX() + 0.5, G + 0.125, TABLET.getZ() + 0.5), TABLET, Direction.UP, SLOT_EMPTY, false);
        // The second player watches the tablet from the south-east.
        stage("peer", new Vec3(TABLET.getX() + 3.5, G, TABLET.getZ() + 3.5),
                new Vec3(TABLET.getX() + 0.5, G + 0.125, TABLET.getZ() + 0.5), TABLET, null, SLOT_EMPTY, false);
        // The second check (2026-11-01): the devices under spawn protection, the packets of the screens and keys, the claims of
        // Open Parties and Claims.
        DeviceScenes.define(STAGES);
        ClaimScenes.define(STAGES);
        // The lead waits here at the end until the second player has left (it still acts in the lead's claim until then).
        stage("wait_peer", new Vec3(SPAWN.getX() + 0.5, G, SPAWN.getZ() + 0.5), new Vec3(SPAWN.getX() + 0.5, G + 1.62, SPAWN.getZ() - 4.5),
                null, null, SLOT_EMPTY, false);
    }

    /** The scenarios that must have a server-side result by the end of the second server's run (variant {@code kami-off}). */
    static final List<String> EXPECTED_KAMI_OFF = List.of("setup", "join_kami_on", "kami_on");

    private static String variant = "";

    static void variant(String name) {
        variant = name;
    }

    /** The scenarios that must have a server-side result by the end of the run. */
    static final List<String> EXPECTED;

    static {
        List<String> expected = new ArrayList<>(List.of("setup", "join_lead", "join_peer", "join_mismatch", "spawn_protection", "flight",
                "dislocation_blacklisted", "dislocation_inside", "dislocation_outside", "dislocation_place_inside", "dislocation_place_outside",
                "mirror_inside", "mirror_boundary", "mirror_outside", "pickaxe_inside", "pickaxe_outside", "enchanter", "tablet",
                "peer_saw_tablet", "mismatch"));
        expected.addAll(DeviceScenes.NAMES);
        expected.addAll(ClaimScenes.NAMES);
        EXPECTED = List.copyOf(expected);
    }

    /**
     * Every server-to-client payload type of the add-on in the play phase. The config payload ({@code PacketConfigSync}) belongs to the
     * configuration phase; whether a client got it is told by {@link #answered}.
     */
    static final List<CustomPacketPayload.Type<?>> S2C = List.of(PacketSoulHearts.TYPE, PacketArmorStatus.TYPE,
            PacketShadowBeam.TYPE);

    private static final Map<String, Map<String, Object>> BASELINES = new HashMap<>();
    private static final Map<String, Long> JOINED_AT = new HashMap<>();
    /** The answers of the config handshake ({@code ConfigSync.ANSWERED}) by player name: the client's options and those that differed. */
    private static final Map<String, Map<String, Boolean>> ANSWERED_OPTIONS = new HashMap<>();
    private static final Map<String, List<String>> ANSWERED_MISMATCHES = new HashMap<>();
    /** Every player that got into the game ({@code PLAYER_JOIN}). */
    private static final java.util.Set<String> EVER_JOINED = new java.util.HashSet<>();
    private static boolean peerReady;

    private Scenes() {}

    private static void stage(String name, Vec3 feet, Vec3 aim, @Nullable BlockPos target, @Nullable Direction face, int slot, boolean crouch) {
        STAGES.put(name, new Stage(name, feet, aim, target, face, slot, crouch));
    }

    /** Whether the second player has left the server (the lead waits for it at the end). */
    private static boolean peerGone;

    static Vec3 faceCenter(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
    }

    // ---- the world, at SERVER_STARTED ------------------------------------------------------------------------------

    static void build(MinecraftServer server, ServerReport report) {
        ServerLevel level = server.overworld();
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_DOFIRETICK).set(false, server);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, server);
        rules.getRule(GameRules.RULE_SPAWN_RADIUS).set(0, server);
        rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        rules.getRule(GameRules.RULE_DOINSOMNIA).set(false, server);
        level.setDayTime(6000L);
        level.setWeatherParameters(24000, 0, false, false);
        level.setDefaultSpawnPos(SPAWN, 0F);
        // The stages lie in the chunks -3..7 (x) and -3..3 (z); they stay loaded for the whole run (the devices tick there too).
        for (int cx = -3; cx <= 8; cx++) for (int cz = -3; cz <= 3; cz++) level.setChunkForced(cx, cz, true);

        put(level, GOLD.below(), Blocks.STONE.defaultBlockState());
        put(level, GOLD, Blocks.GOLD_BLOCK.defaultBlockState());
        put(level, STONE_IN.below(), Blocks.COBBLESTONE.defaultBlockState());
        put(level, STONE_IN, Blocks.STONE.defaultBlockState());
        put(level, STONE_OUT.below(), Blocks.COBBLESTONE.defaultBlockState());
        put(level, STONE_OUT, Blocks.STONE.defaultBlockState());
        for (BlockPos wall : List.of(WALL_IN, WALL_EDGE, WALL_OUT)) {
            put(level, wall, Blocks.POLISHED_ANDESITE.defaultBlockState());
            for (BlockPos pos : square(wall)) put(level, pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos bedrock : List.of(BEDROCK_IN, BEDROCK_OUT)) {
            for (int y = -63; y <= G; y++) {
                put(level, new BlockPos(bedrock.getX() - 1, y, bedrock.getZ()), Blocks.AIR.defaultBlockState());
                put(level, new BlockPos(bedrock.getX(), y, bedrock.getZ()), Blocks.AIR.defaultBlockState());
            }
        }
        put(level, ENCHANTER, TTRegistry.block(LibBlockNames.ENCHANTER).defaultBlockState());
        put(level, TABLET, TTRegistry.block(LibBlockNames.ANIMATION_TABLET).defaultBlockState());
        // The second server (Kami off) has none of the Kami blocks and only one client, which leaves at once.
        if (!variant.equals("kami-off")) {
            DeviceScenes.build(level, report);
            ClaimScenes.build(server, level, report);
        }

        report.fact("dedicated_server", server.isDedicatedServer());
        report.fact("spawn", SPAWN.toShortString());
        report.fact("spawn_protection_radius", server.getSpawnProtectionRadius());
        report.fact("operators", Arrays.asList(server.getPlayerList().getOpNames()));
        report.fact("server_config_modFlight.enabled", ConfigHandler.enableFlight);
        report.fact("server_config_focusOfDislocationBlacklist", Arrays.asList(ConfigHandler.blockBlacklist));
        report.fact("server_config_registration", ConfigHandler.registrationOptions());
        report.fact("bedrock_at_pits", BuiltInRegistries.BLOCK.getKey(level.getBlockState(BEDROCK_IN).getBlock()) + ", "
                + BuiltInRegistries.BLOCK.getKey(level.getBlockState(BEDROCK_OUT).getBlock()));
        boolean protectedServer = server.isDedicatedServer() && server.getSpawnProtectionRadius() == RADIUS
                && server.getPlayerList().getOpNames().length > 0;
        report.check("setup", "dedicated_server_with_spawn_protection", protectedServer, "dedicated=" + server.isDedicatedServer()
                + ", spawn-protection=" + server.getSpawnProtectionRadius() + ", operators=" + Arrays.toString(server.getPlayerList().getOpNames()));
        boolean configured = !ConfigHandler.enableFlight && Arrays.asList(ConfigHandler.blockBlacklist).contains("minecraft:gold_block")
                && ConfigHandler.enableKami == !variant.equals("kami-off");
        report.check("setup", "server_config_as_prepared", configured, "modFlight.enabled=" + ConfigHandler.enableFlight
                + ", focusOfDislocationBlacklist=" + Arrays.toString(ConfigHandler.blockBlacklist) + ", kami.forceenabled=" + ConfigHandler.enableKami);
    }

    static void put(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    /** The positions the Placement Mirror fills for a click on the south face of {@code wall} with size {@link #MIRROR_SIZE}. */
    static List<BlockPos> square(BlockPos wall) {
        int range = MIRROR_SIZE / 2;
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -range; x <= range; x++) {
            for (int y = 0; y <= 2 * range; y++) positions.add(wall.offset(x, y, 1));
        }
        return positions;
    }

    static boolean protectedPos(BlockPos pos) {
        return Math.max(Math.abs(pos.getX() - SPAWN.getX()), Math.abs(pos.getZ() - SPAWN.getZ())) <= RADIUS;
    }

    // ---- players ---------------------------------------------------------------------------------------------------

    /**
     * {@code ConfigSync.ANSWERED}: a client answered the config handshake, in the configuration phase of its connection, before it is
     * let in or refused (and so before {@link #joined}).
     */
    static void answered(String name, Map<String, Boolean> clientOptions, List<String> mismatches, ServerReport report) {
        ANSWERED_OPTIONS.put(name, new LinkedHashMap<>(clientOptions));
        ANSWERED_MISMATCHES.put(name, List.copyOf(mismatches));
        report.event("config_handshake", name, "the client's registration options " + clientOptions + "; differing from this server's: " + mismatches);
        String stage = switch (name) {
            case MISMATCH -> "join_mismatch";
            case KAMI_ON -> "join_kami_on";
            default -> "";
        };
        if (!stage.isEmpty()) {
            report.check(stage, "answered_the_config_handshake", true, name + " got this server's config in the configuration phase and answered"
                    + " with its own registration options " + clientOptions + "; differing: " + mismatches);
        }
        if (name.equals(MISMATCH_KAMI)) {
            report.info("mismatch_kami", "answered_the_config_handshake", MISMATCH_KAMI + " reached the add-on's task (the loader's registry sync"
                    + " let it through): differing " + mismatches);
        }
    }

    /**
     * At the end: a client whose registration options differ is refused in the configuration phase. Its answer named exactly the one
     * option its file changes, and it never got into the game.
     */
    static void refusals(ServerReport report) {
        record Refused(String scenario, String player, String option, boolean serverValue) {}
        List<Refused> refused = variant.equals("kami-off") ? List.of(new Refused("kami_on", KAMI_ON, "kami/kami.forceenabled", false))
                : List.of(new Refused("mismatch", MISMATCH, "general/imbuedFire.enabled", true));
        for (Refused r : refused) {
            List<String> mismatches = ANSWERED_MISMATCHES.get(r.player());
            Map<String, Boolean> options = ANSWERED_OPTIONS.get(r.player());
            boolean named = mismatches != null && mismatches.equals(List.of(r.option())) && options != null
                    && Boolean.valueOf(!r.serverValue()).equals(options.get(r.option()));
            boolean outside = !EVER_JOINED.contains(r.player());
            report.check(r.scenario(), "refused_in_the_configuration_phase", named && outside, r.player() + " answered "
                    + (options == null ? "nothing (it never reached the add-on's task)" : options + ", differing " + mismatches)
                    + "; this server has " + r.option() + "=" + r.serverValue() + "; it got into the game: " + !outside
                    + " (the server refuses it before the game; its own report has the message it showed)");
        }
    }

    static void joined(ServerPlayer player, ServerReport report) {
        MinecraftServer server = player.server;
        String name = player.getGameProfile().getName();
        EVER_JOINED.add(name);
        // The config payload belongs to the configuration phase now: the client got it and answered with equal options before it joined.
        boolean receives = ANSWERED_MISMATCHES.containsKey(name) && ANSWERED_MISMATCHES.get(name).isEmpty();
        boolean socket = player.connection.getRemoteAddress() instanceof InetSocketAddress;
        boolean op = server.getPlayerList().isOp(player.getGameProfile());
        report.event("join", name, "address=" + player.connection.getRemoteAddress() + " operator=" + op + " permission="
                + server.getProfilePermissions(player.getGameProfile()) + " receives_config_sync=" + receives);
        JOINED_AT.put(name, System.nanoTime());
        String role = switch (name) {
            case LEAD -> "lead";
            case PEER -> "peer";
            case MISMATCH -> "mismatch";
            case MISMATCH_KAMI -> "mismatch_kami";
            case KAMI_ON -> "kami_on";
            default -> "";
        };
        if (role.isEmpty()) {
            report.info("join", "unexpected_player", name);
            return;
        }
        report.check("join_" + role, "real_connection_and_config_sync", socket && receives && !op,
                name + " joined over " + player.connection.getRemoteAddress() + "; in the configuration phase its client got the config ("
                        + PacketConfigSync.TYPE.id() + ") and answered with equal registration options: " + receives + " ("
                        + ANSWERED_OPTIONS.get(name) + "); operator: " + op);
        // The add-on's server-to-client payloads: this player's client (a JVM of its own) registered a receiver for each. The Kami ones
        // exist only while kami.forceenabled is on (KamiGear and KamiWorld register nothing otherwise), and NeoForge counts a channel
        // only when both sides have it; so on the second server (Kami off) none is expected.
        List<CustomPacketPayload.Type<?>> expected = ConfigHandler.enableKami ? S2C : List.of();
        List<String> missing = new ArrayList<>();
        for (CustomPacketPayload.Type<?> type : expected) if (!NetworkManager.canPlayerReceive(player, type)) missing.add(type.id().toString());
        report.check("join_" + role, "client_receives_every_addon_payload", missing.isEmpty(), expected.size()
                + " server-to-client payload types of the add-on on this server; " + name + "'s client does not receive " + missing);
        if (role.startsWith("mismatch") || role.equals("kami_on")) return;

        player.setGameMode(GameType.SURVIVAL);
        Inventory inventory = player.getInventory();
        inventory.clearContent();
        if (role.equals("lead")) {
            inventory.setItem(SLOT_DISLOCATION, wand(LibItemNames.FOCUS_DISLOCATION));
            ItemStack mirror = new ItemStack(TTRegistry.item(LibItemNames.PLACEMENT_MIRROR));
            mirror.set(KamiComponents.MIRROR_STATE.get(), Blocks.STONE.defaultBlockState());
            mirror.set(KamiComponents.MIRROR_SIZE.get(), MIRROR_SIZE);
            inventory.setItem(SLOT_MIRROR, mirror);
            inventory.setItem(SLOT_PICK, new ItemStack(TTRegistry.item(LibItemNames.ICHOR_PICK_GEM)));
            inventory.setItem(SLOT_SWORD, new ItemStack(Items.DIAMOND_SWORD));
            inventory.setItem(SLOT_TOOL, new ItemStack(Items.IRON_PICKAXE));
            inventory.setItem(SLOT_STONE, new ItemStack(Items.STONE, 64));
            inventory.setItem(SLOT_FLIGHT, wand(LibItemNames.FOCUS_FLIGHT));
            inventory.selected = SLOT_EMPTY;
            player.teleportTo(player.serverLevel(), SPAWN.getX() + 0.5, G, SPAWN.getZ() + 0.5, 0F, 0F);

            ServerLevel level = player.serverLevel();
            boolean in = server.isUnderSpawnProtection(level, STONE_IN, player);
            boolean out = server.isUnderSpawnProtection(level, STONE_OUT, player);
            boolean edgeIn = server.isUnderSpawnProtection(level, new BlockPos(RADIUS, G, 1), player);
            boolean edgeOut = server.isUnderSpawnProtection(level, new BlockPos(RADIUS + 1, G, 1), player);
            report.check("spawn_protection", "protects_the_lead_inside_the_radius_only", in && !out && edgeIn && !edgeOut && !op,
                    "isUnderSpawnProtection for " + name + " (operator " + op + "): " + STONE_IN.toShortString() + " " + in + ", "
                            + STONE_OUT.toShortString() + " " + out + ", x=" + RADIUS + " " + edgeIn + ", x=" + (RADIUS + 1) + " " + edgeOut);
        } else {
            // The second player of the claims (ClaimScenes): the same tools as the lead, and the Awakened Ichorium Axe in tree mode.
            ClaimScenes.equipPeer(inventory);
            inventory.selected = SLOT_EMPTY;
        }
        player.inventoryMenu.broadcastChanges();
        // The selected slot is the client's: tell it, else it still selects slot 0, its own key press of slot 0 changes nothing and sends
        // nothing, and the server goes on thinking the empty slot is held.
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(inventory.selected));
    }

    static void left(ServerPlayer player, ServerReport report) {
        String name = player.getGameProfile().getName();
        if (name.equals(PEER)) peerGone = true;
        Long joined = JOINED_AT.remove(name);
        long millis = joined == null ? -1 : (System.nanoTime() - joined) / 1_000_000L;
        report.event("leave", name, "after " + millis + " ms");
        if (name.equals(MISMATCH_KAMI) || name.equals(KAMI_ON) || name.equals(MISMATCH)) {
            // refused in the configuration phase (refusals at the end), so it never gets this far
            report.check(name.equals(MISMATCH) ? "mismatch" : name.equals(KAMI_ON) ? "kami_on" : "mismatch_kami", "never_in_the_game", false,
                    name + " got into the game and left " + millis + " ms later; it should have been refused before the game");
        }
    }

    /** A staff of silverwood with thaumium caps, full of every primal, with the add-on's focus {@code focusPath} on it. */
    static ItemStack wand(String focusPath) {
        ItemStack wand = ItemApi.getItem("itemWandCasting", 0);
        WandHelper.setRod(wand, WandRod.byTag("silverwood_staff"));
        WandHelper.setCap(wand, WandCap.byTag("thaumium"));
        for (Aspect primal : Aspect.getPrimalAspects()) WandHelper.addRealVis(wand, primal, WandHelper.getMaxVis(wand), true);
        if (!WandHelper.setFocus(wand, new ItemStack(TTRegistry.item(focusPath)))) {
            throw new IllegalStateException("the focus " + focusPath + " does not mount on the wand");
        }
        return wand;
    }

    static String vis(ItemStack wand) {
        AspectList all = WandHelper.getAllVis(wand);
        StringJoiner text = new StringJoiner(",");
        for (Aspect primal : Aspect.getPrimalAspects()) text.add(primal.getTag() + ":" + all.getAmount(primal));
        return text.toString();
    }

    static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) total += inventory.getItem(i).getCount();
        }
        return total;
    }

    static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    // ---- /ttmp stage -----------------------------------------------------------------------------------------------

    /** Sends the player to the stage of {@code name}, remembers what the check compares with, and says where he stands. */
    static String stage(ServerPlayer player, String name, ServerReport report) {
        if (name.equals("tablet") && !peerReady) return "WAIT the second player has not seen the tablet yet";
        if (name.equals("wait_peer") && !peerGone) return "WAIT the second player is still on the server";
        String wait = ClaimScenes.waitBefore(player, name);
        if (wait != null) return "WAIT " + wait;
        Stage stage = STAGES.get(name);
        if (stage == null) return "ERROR no stage " + name;
        ServerLevel level = player.serverLevel();
        if (stage.prepare() != null) stage.prepare().accept(player);
        Map<String, Object> before = new HashMap<>();
        before.put("vis", vis(player.getInventory().getItem(stage.slot())));
        before.put("stone", count(player, Items.STONE));
        before.put("damage", player.getInventory().getItem(SLOT_PICK).getDamageValue());
        if (stage.target() != null) before.put("state", id(level.getBlockState(stage.target())));
        before.put("staged_at", System.nanoTime());
        DeviceScenes.baseline(player, name, before);
        BASELINES.put(name, before);

        double eye = stage.crouch() ? player.getEyeHeight(Pose.CROUCHING) : player.getEyeHeight(Pose.STANDING);
        Vec3 from = stage.feet().add(0, eye, 0);
        float[] look = look(from, stage.aim());
        player.teleportTo(level, stage.feet().x, stage.feet().y, stage.feet().z, look[0], look[1]);
        player.setYHeadRot(look[0]);
        report.event("stage", player.getGameProfile().getName(), name + " at " + stage.feet() + " yaw " + look[0] + " pitch " + look[1]);

        StringBuilder reply = new StringBuilder("OK");
        reply.append(" x=").append(stage.feet().x).append(" y=").append(stage.feet().y).append(" z=").append(stage.feet().z);
        reply.append(" yaw=").append(look[0]).append(" pitch=").append(look[1]).append(" slot=").append(stage.slot());
        reply.append(" crouch=").append(stage.crouch());
        if (stage.target() != null) reply.append(" target=").append(pos(stage.target()));
        if (stage.face() != null) reply.append(" face=").append(stage.face().getName());
        reply.append(" spawn=").append(pos(SPAWN)).append(" radius=").append(RADIUS);
        if (name.startsWith("mirror_") || ClaimScenes.isMirror(name)) {
            List<BlockPos> square = square(stage.target());
            reply.append(" square=").append(pos(square.get(0))).append(':').append(pos(square.get(square.size() - 1)));
        }
        // The item the harness put into the slot: the client waits until its own inventory shows it.
        if (stage.holding() != null) {
            reply.append(" holding=").append(BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(stage.slot()).getItem()));
        }
        if (stage.entity()) reply.append(" entity=true");
        reply.append(" vis=").append(before.get("vis")).append(" stone=").append(before.get("stone"));
        return reply.toString();
    }

    static String pos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** {@code Entity.lookAt}: the rotation that points the view from {@code eye} at {@code at}. */
    static float[] look(Vec3 eye, Vec3 at) {
        double dx = at.x - eye.x;
        double dy = at.y - eye.y;
        double dz = at.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG)));
        return new float[] { yaw, pitch };
    }

    // ---- /ttmp check -----------------------------------------------------------------------------------------------

    /** What the server finds after the client acted in scenario {@code name}; recorded, and answered with the numbers. */
    static String check(ServerPlayer player, String name, @Nullable String arg, ServerReport report) {
        Map<String, Object> before = BASELINES.get(name);
        if (before == null) return "ERROR the scenario " + name + " was not staged";
        ServerLevel level = player.serverLevel();
        Inventory inventory = player.getInventory();
        Stage stage = STAGES.get(name);
        String shift = " (the server saw the player sneaking: " + player.isShiftKeyDown() + ")";
        boolean ok;
        String detail;
        Map<String, Object> values = new LinkedHashMap<>();
        switch (name) {
            case "flight" -> {
                String now = vis(inventory.getItem(SLOT_FLIGHT));
                values.put("vis", now);
                ok = now.equals(before.get("vis"));
                detail = "the Focus of Uprising with modFlight.enabled=false on the server: vis " + before.get("vis") + " -> " + now;
            }
            case "dislocation_blacklisted", "dislocation_inside" -> {
                ItemStack wand = inventory.getItem(SLOT_DISLOCATION);
                String now = vis(wand);
                BlockState state = level.getBlockState(stage.target());
                DislocationData carried = ItemFocusDislocation.getData(wand);
                values.put("vis", now);
                values.put("state", id(state));
                ok = id(state).equals(before.get("state")) && now.equals(before.get("vis")) && carried == null;
                detail = (name.endsWith("blacklisted") ? "gold block (on the server's focusOfDislocationBlacklist), outside the spawn radius"
                        : "stone inside the spawn radius") + ": block " + before.get("state") + " -> " + id(state) + ", vis "
                        + before.get("vis") + " -> " + now + ", focus carries " + (carried == null ? "nothing" : carried.state());
            }
            case "dislocation_outside" -> {
                ItemStack wand = inventory.getItem(SLOT_DISLOCATION);
                String now = vis(wand);
                BlockState state = level.getBlockState(stage.target());
                DislocationData carried = ItemFocusDislocation.getData(wand);
                values.put("vis", now);
                values.put("state", id(state));
                AspectList was = parse(String.valueOf(before.get("vis")));
                AspectList is = WandHelper.getAllVis(wand);
                boolean paid = is.getAmount(Aspect.ENTROPY) < was.getAmount(Aspect.ENTROPY) && is.getAmount(Aspect.ORDER) < was.getAmount(Aspect.ORDER)
                        && is.getAmount(Aspect.EARTH) < was.getAmount(Aspect.EARTH) && is.getAmount(Aspect.AIR) == was.getAmount(Aspect.AIR)
                        && is.getAmount(Aspect.FIRE) == was.getAmount(Aspect.FIRE) && is.getAmount(Aspect.WATER) == was.getAmount(Aspect.WATER);
                ok = state.isAir() && carried != null && carried.state().is(Blocks.STONE) && paid;
                detail = "stone outside the spawn radius: block " + before.get("state") + " -> " + id(state) + ", vis " + before.get("vis")
                        + " -> " + now + " (paid perditio, ordo and terra only: " + paid + "), focus carries "
                        + (carried == null ? "nothing" : carried.state());
            }
            case "dislocation_place_inside", "dislocation_place_outside" -> {
                ItemStack wand = inventory.getItem(SLOT_DISLOCATION);
                BlockPos at = stage.target().relative(stage.face());
                BlockState state = level.getBlockState(at);
                DislocationData carried = ItemFocusDislocation.getData(wand);
                String now = vis(wand);
                values.put("vis", now);
                values.put("placed", id(state));
                values.put("carried", carried == null ? "nothing" : id(carried.state()));
                boolean inside = name.endsWith("inside");
                ok = inside ? state.isAir() && carried != null && carried.state().is(Blocks.STONE)
                        : state.is(Blocks.STONE) && carried == null;
                ok &= now.equals(before.get("vis"));
                detail = "putting the carried stone down at " + pos(at) + (inside ? " inside" : " outside") + " the spawn radius: the block there is "
                        + id(state) + " (expected " + (inside ? "minecraft:air" : "minecraft:stone") + "), the focus carries "
                        + (carried == null ? "nothing" : carried.state()) + ", vis " + before.get("vis") + " -> " + now;
            }
            case "mirror_inside", "mirror_boundary", "mirror_outside" -> {
                int inside = 0;
                int outside = 0;
                int expectedInside = 0;
                int expectedOutside = 0;
                for (BlockPos pos : square(stage.target())) {
                    boolean stone = level.getBlockState(pos).is(Blocks.STONE);
                    if (protectedPos(pos)) {
                        if (stone) inside++;
                    } else {
                        expectedOutside++;
                        if (stone) outside++;
                    }
                }
                int stoneNow = count(player, Items.STONE);
                int stoneBefore = (Integer) before.get("stone");
                values.put("placed_inside", inside);
                values.put("placed_outside", outside);
                values.put("stone", stoneNow);
                ok = inside == expectedInside && outside == expectedOutside && stoneNow == stoneBefore - outside
                        && (name.equals("mirror_inside") ? outside == 0 : outside > 0);
                detail = "square of " + square(stage.target()).size() + " around " + pos(stage.target()) + ": placed " + inside
                        + " inside the radius (expected 0) and " + outside + " outside (expected " + expectedOutside + "); stone in the inventory "
                        + stoneBefore + " -> " + stoneNow;
            }
            case "pickaxe_inside", "pickaxe_outside" -> {
                BlockState state = level.getBlockState(stage.target());
                int damage = inventory.getItem(SLOT_PICK).getDamageValue();
                values.put("state", id(state));
                values.put("damage", damage);
                String expected = name.endsWith("inside") ? "minecraft:bedrock" : "thaumictinkerer:" + LibBlockNames.PORTAL;
                ok = id(state).equals(expected) && damage == (Integer) before.get("damage") && inventory.getItem(SLOT_PICK).is(TTRegistry.item(LibItemNames.ICHOR_PICK_GEM));
                detail = "sneaking left click on bedrock " + pos(stage.target()) + (name.endsWith("inside") ? " inside" : " outside")
                        + " the spawn radius: " + before.get("state") + " -> " + id(state) + " (expected " + expected + "); pickaxe damage "
                        + before.get("damage") + " -> " + damage + shift;
            }
            case "enchanter" -> {
                if (!(level.getBlockEntity(ENCHANTER) instanceof TileEnchanter enchanter)) {
                    ok = false;
                    detail = "no Enchanter at " + pos(ENCHANTER);
                } else {
                    ItemStack tool = enchanter.getItem(TileEnchanter.SLOT_TOOL);
                    List<String> chosen = enchanter.enchantments.stream().map(ResourceKey::location).map(ResourceLocation::toString).toList();
                    values.put("tool", BuiltInRegistries.ITEM.getKey(tool.getItem()));
                    values.put("enchants", String.join(",", chosen));
                    values.put("levels", enchanter.levels.toString().replace(" ", ""));
                    ok = tool.is(Items.DIAMOND_SWORD) && arg != null && chosen.contains(arg) && count(player, Items.DIAMOND_SWORD) == 0;
                    detail = "the Osmotic Enchanter holds " + BuiltInRegistries.ITEM.getKey(tool.getItem()) + " (put in by a container click)"
                            + " and has the enchantments " + chosen + " with levels " + enchanter.levels + "; the client chose " + arg
                            + " on the screen (PacketEnchanterAddEnchant)";
                }
            }
            case "tablet" -> {
                if (!(level.getBlockEntity(TABLET) instanceof TileAnimationTablet tablet)) {
                    ok = false;
                    detail = "no Animation Tablet at " + pos(TABLET);
                } else {
                    ItemStack held = tablet.getItem(0);
                    values.put("item", BuiltInRegistries.ITEM.getKey(held.getItem()));
                    values.put("leftClick", tablet.leftClick);
                    // a new tablet is in left-click mode; the lead switches it to right click
                    ok = held.is(Items.IRON_PICKAXE) && !tablet.leftClick && count(player, Items.IRON_PICKAXE) == 0;
                    detail = "the Animation Tablet holds " + BuiltInRegistries.ITEM.getKey(held.getItem()) + " (container click) and is in "
                            + (tablet.leftClick ? "left" : "right") + "-click mode (PacketTabletButton)";
                }
            }
            default -> {
                Outcome outcome = DeviceScenes.check(player, name, arg, before);
                if (outcome == null) outcome = ClaimScenes.check(player, name, arg, before);
                if (outcome == null) return "ERROR no check " + name;
                if (outcome.waitFor() != null) return "WAIT " + outcome.waitFor();
                ok = outcome.ok();
                detail = outcome.detail();
                values.putAll(outcome.values());
            }
        }
        report.check(name, name, ok, detail);
        StringBuilder reply = new StringBuilder(ok ? "PASS" : "FAIL");
        values.forEach((key, value) -> reply.append(' ').append(key).append('=').append(value));
        reply.append(" | ").append(detail);
        return reply.toString();
    }

    /** What a check of {@link DeviceScenes} or {@link ClaimScenes} found; {@code waitFor} non-null: ask again (the effect is not there yet). */
    record Outcome(boolean ok, String detail, Map<String, Object> values, @Nullable String waitFor) {
        static Outcome of(boolean ok, String detail, Map<String, Object> values) {
            return new Outcome(ok, detail, values, null);
        }

        static Outcome waiting(String why) {
            return new Outcome(false, "", Map.of(), why);
        }
    }

    /** The blocks at {@code positions} as the reply's {@code blocks=x,y,z:id;...}, which the client compares with its own world. */
    static String blocks(ServerLevel level, List<BlockPos> positions) {
        StringJoiner text = new StringJoiner(";");
        for (BlockPos at : positions) text.add(pos(at) + ":" + id(level.getBlockState(at)));
        return text.toString();
    }

    static AspectList parse(String vis) {
        AspectList list = new AspectList();
        for (String part : vis.split(",")) {
            String[] kv = part.split(":");
            Aspect aspect = Aspect.getAspect(kv[0]);
            if (aspect != null) list.add(aspect, Integer.parseInt(kv[1]));
        }
        return list;
    }

    // ---- the second player -----------------------------------------------------------------------------------------

    static String ready(ServerPlayer player, ServerReport report) {
        peerReady = true;
        report.event("ready", player.getGameProfile().getName(), "sees the tablet at " + pos(TABLET) + " in its own client");
        return "OK";
    }

    /** The second player says what its client shows of the tablet ({@code item=<id> leftClick=<bool>}); compared with the server. */
    static String saw(ServerPlayer player, @Nullable String arg, ServerReport report) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(TABLET) instanceof TileAnimationTablet tablet)) return "FAIL | no tablet on the server";
        String server = "item=" + BuiltInRegistries.ITEM.getKey(tablet.getItem(0).getItem()) + " leftClick=" + tablet.leftClick;
        boolean ok = server.equals(arg) && tablet.getItem(0).is(Items.IRON_PICKAXE) && !tablet.leftClick;
        report.check("peer_saw_tablet", "the_second_client_shows_what_the_first_changed", ok, PEER + "'s client shows [" + arg
                + "], the server has [" + server + "] (changed by " + LEAD + " through the menu)");
        return (ok ? "PASS " : "FAIL ") + server + " | compared";
    }

    /** At SERVER_STOPPING: every scenario has a result. */
    static void finish(ServerReport report) {
        refusals(report);
        List<String> expected = variant.equals("kami-off") ? EXPECTED_KAMI_OFF : EXPECTED;
        List<String> missing = expected.stream().filter(s -> !report.has(s)).toList();
        report.check("end", "every_scenario_has_a_server_result", missing.isEmpty(), missing.isEmpty() ? expected.size()
                + " scenarios recorded" : "no server-side result for " + missing);
    }
}
