package thaumic.tinkerer.clientsmoke;

import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.wands.WandHelper;
import thaumic.tinkerer.common.core.handler.ConfigHandler;

/**
 * The multiplayer mode of the client harness (docs/MULTIPLAYER_VERIFICATION.md): a real client that joins a real dedicated
 * server ({@code --quickPlayMultiplayer}) instead of making a singleplayer world. Test-only code, new in the 1.21.1 port (no
 * 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), not distributed.
 *
 * <p>Chosen by {@code -Dthaumictinkerer.clientSmoke.mode=multiplayer-<role>}; the role is {@code lead} (the player who acts:
 * {@link MultiplayerLeadPhase}), {@code peer} (the second player, who watches: {@link MultiplayerPeerPhase}), {@code mismatch},
 * {@code mismatch-kami} or {@code kami-on} (a client whose own config differs in an option that decides registration:
 * {@link MultiplayerMismatchPhase}). The report,
 * the pictures, the watchdog and the end of the run are those of the singleplayer harness ({@link SmokeReport}, {@link Shots},
 * {@link Watchdog}, {@link FinishPhase}); this class has the scripts the roles share.</p>
 */
final class MultiplayerClient {
    static final String GROUP = "multiplayer";
    /** The blacklist entry the run puts into the server's config only (the clients' own files have an empty list). */
    static final String BLACKLISTED = "minecraft:gold_block";

    /** Every client-to-server payload type of the add-on. */
    static final List<net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?>> C2S = List.of(
            thaumic.tinkerer.common.network.packet.PacketEnchanterAddEnchant.TYPE,
            thaumic.tinkerer.common.network.packet.PacketEnchanterStartWorking.TYPE,
            thaumic.tinkerer.common.network.packet.PacketPlacerButton.TYPE,
            thaumic.tinkerer.common.network.packet.PacketTabletButton.TYPE,
            thaumic.tinkerer.common.network.packet.kami.PacketToggleArmor.TYPE,
            thaumic.tinkerer.common.network.packet.kami.PacketWarpGateButton.TYPE,
            thaumic.tinkerer.common.network.packet.kami.PacketWarpGateTeleport.TYPE);

    private MultiplayerClient() {}

    /** The phases of a role, ending with {@link FinishPhase}. */
    static List<Phase> phases(String role) {
        ServerLink link = new ServerLink();
        link.install();
        Phase main = switch (role) {
            case "lead" -> new MultiplayerLeadPhase(link);
            case "peer" -> new MultiplayerPeerPhase(link);
            case "mismatch" -> new MultiplayerMismatchPhase("mismatch", "general/imbuedFire.enabled", true, true);
            case "mismatch-kami" -> new MultiplayerMismatchPhase("mismatch-kami", "kami/kami.forceenabled", true, false);
            case "kami-on" -> new MultiplayerMismatchPhase("kami-on", "kami/kami.forceenabled", false, true);
            default -> throw new IllegalArgumentException("unknown multiplayer role " + role);
        };
        return List.of(main, new FinishPhase());
    }

    static void record(SmokeRun run, String phase, String name, String target, boolean ok, String detail) {
        run.report().add(phase, name, GROUP, target, ok ? SmokeReport.Result.PASS : SmokeReport.Result.FAIL, detail);
    }

    /** Ends the script with {@code NOT_RUN} when the client is no longer connected (an earlier script lost the connection). */
    static void requireConnected(SmokeRun run, String phase, String name) {
        if (run.client().getConnection() != null && run.client().level != null) return;
        run.report().notRun(phase, name, "skipped: the client is not connected to the server");
        throw new Script.Abort();
    }

    /** Joins: waits until the client stands in the server's world, and records that the connection is a real socket. */
    static Script join(String phase) {
        return new Script("join")
                .until("the client in the server's world", () -> {
                    Minecraft client = Minecraft.getInstance();
                    return client.level != null && client.player != null && client.getConnection() != null && client.player.tickCount > 40
                            && !(client.screen instanceof ReceivingLevelScreen);
                }, 300)
                .run("record the connection", run -> {
                    Minecraft client = run.client();
                    ClientPacketListener connection = client.getConnection();
                    boolean memory = connection.getConnection().isMemoryConnection();
                    String address = String.valueOf(connection.getConnection().getRemoteAddress());
                    run.report().fact("player", client.player.getGameProfile().getName());
                    run.report().fact("server_address", address);
                    run.report().fact("server_brand", String.valueOf(connection.serverBrand()));
                    record(run, phase, "joined_a_dedicated_server_over_a_socket", "", !memory && client.getSingleplayerServer() == null,
                            client.player.getGameProfile().getName() + " joined " + address + " (server brand " + connection.serverBrand()
                                    + "); memory connection " + memory + ", integrated server " + (client.getSingleplayerServer() != null));
                });
    }

    /**
     * Scenario a, first half: the server's options arrived ({@code PacketConfigSync}) and the side-aware accessors answer with
     * them for the client's level, although the client's own file (written by the Gradle task: flight on, empty blacklist) says
     * otherwise; the client stays connected (its registration options equal the server's).
     */
    static Script configSync(String phase) {
        return new Script("config_sync")
                .until("the server's config (PacketConfigSync)", () -> ConfigHandler.fromServer() != null, 60)
                .ticks(40)
                .run("check", run -> {
                    Minecraft client = run.client();
                    ConfigHandler.Shared server = ConfigHandler.fromServer();
                    ConfigHandler.Shared own = ConfigHandler.Shared.local();
                    String values = "server's copy " + server + "; this client's file " + own;
                    record(run, phase, "config_sync_arrived", "", server != null, values);
                    boolean flight = own.enableFlight() && server != null && !server.enableFlight() && !ConfigHandler.flightEnabled(client.level)
                            && ConfigHandler.flightEnabled(null);
                    record(run, phase, "flight_follows_the_server", "modFlight.enabled", flight, "own file modFlight.enabled="
                            + own.enableFlight() + ", ConfigHandler.flightEnabled(client level)=" + ConfigHandler.flightEnabled(client.level)
                            + ", flightEnabled(null) (this side's file)=" + ConfigHandler.flightEnabled(null));
                    List<String> synced = Arrays.asList(ConfigHandler.dislocationBlacklist(client.level));
                    boolean blacklist = synced.contains(BLACKLISTED) && !own.dislocationBlacklist().contains(BLACKLISTED);
                    record(run, phase, "dislocation_blacklist_follows_the_server", "focusOfDislocationBlacklist", blacklist,
                            "dislocationBlacklist(client level)=" + synced + ", own file " + own.dislocationBlacklist());
                    // The add-on's client-to-server payloads: the server announced a receiver for each (a separate JVM with its own
                    // registry, which a singleplayer run, sharing one registry, cannot show).
                    List<String> missing = new java.util.ArrayList<>();
                    for (var type : C2S) if (!dev.architectury.networking.NetworkManager.canServerReceive(type)) missing.add(type.id().toString());
                    record(run, phase, "server_receives_every_addon_payload", "", missing.isEmpty(), C2S.size()
                            + " client-to-server payload types of the add-on; the server does not receive " + missing);
                    boolean connected = client.getConnection() != null && client.level != null;
                    record(run, phase, "stays_connected_with_equal_registration_options", "", connected,
                            "registration options here " + ConfigHandler.registrationOptions() + "; connected two seconds after the payload: " + connected);
                });
    }

    /** Scenario a, second half: leaving the server clears the server's copy; this side's file counts again. */
    static Script disconnect(String phase, ServerLink link) {
        return new Script("disconnect")
                .run("leave the server", run -> {
                    Minecraft client = run.client();
                    run.report().inventory("system_messages", link.received());
                    if (client.level != null) client.level.disconnect();
                    client.disconnect(new TitleScreen());
                })
                .until("back on the title screen", () -> Minecraft.getInstance().level == null && Minecraft.getInstance().getConnection() == null, 60)
                .ticks(10)
                .run("check", run -> {
                    ConfigHandler.Shared from = ConfigHandler.fromServer();
                    boolean cleared = from == null && ConfigHandler.shared(true).equals(ConfigHandler.Shared.local())
                            && ConfigHandler.flightEnabled(null) == ConfigHandler.enableFlight;
                    record(run, phase, "synced_copy_cleared_after_leaving", "", cleared, "ConfigHandler.fromServer()=" + from
                            + ", shared(client)=" + ConfigHandler.shared(true) + ", own file " + ConfigHandler.Shared.local());
                });
    }

    /** True when the client player stands where the server's answer to {@code stage} put it. */
    static boolean at(Minecraft client, ServerLink.Reply stage) {
        if (!stage.status().equals("OK")) throw new IllegalStateException("the server did not stage the scenario: " + stage.raw());
        double x = Double.parseDouble(stage.get("x"));
        double y = Double.parseDouble(stage.get("y"));
        double z = Double.parseDouble(stage.get("z"));
        return client.player != null && client.player.position().distanceToSqr(x, y, z) < 0.01;
    }

    static BlockPos pos(String text) {
        String[] parts = text.split(",");
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    /** The vis of a wand as the server's harness writes it ({@code aer:2500,terra:...}). */
    static String vis(ItemStack wand) {
        AspectList all = WandHelper.getAllVis(wand);
        StringJoiner text = new StringJoiner(",");
        for (Aspect primal : Aspect.getPrimalAspects()) text.add(primal.getTag() + ":" + all.getAmount(primal));
        return text.toString();
    }

    static String blockId(Minecraft client, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(client.level.getBlockState(pos).getBlock()).toString();
    }

    static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
