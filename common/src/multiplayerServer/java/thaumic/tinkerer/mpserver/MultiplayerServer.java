package thaumic.tinkerer.mpserver;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server half of the multiplayer check of Thaumic Tinkerer (docs/MULTIPLAYER_VERIFICATION.md): a small test mod in a real
 * dedicated server that builds the scenarios, answers the clients' {@code /ttmp} commands and writes {@code server-report.json}.
 * Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md). It lives in
 * its own source set ({@code thaumicTinkererMultiplayerServer} on Fabric, {@code multiplayerServer} on NeoForge) that no jar task
 * includes, and does nothing unless {@code -Dthaumictinkerer.mpServer=true} is set (the multiplayer runs set it).
 *
 * <p>Loader-neutral: everything goes through Architectury's events; the loader's entry ({@code TinkererMultiplayerServer} on
 * Fabric, {@code TinkererMultiplayerServerNeoForge} on NeoForge) only calls {@link #arm}. The status of the report is
 * {@code COMPLETED} when the server stops as the Gradle task asks it (the {@code stop} command on its console), {@code TIMED_OUT}
 * when the watchdog ends a run that hangs (and halts the JVM), {@code ABORTED} when the JVM ends otherwise.</p>
 *
 * <p>The run has two servers, one after the other: the main one (every scenario, {@code server-report.json}) and, with
 * {@code -Dthaumictinkerer.mpServer.variant=kami-off}, a second one whose config has {@code kami.forceenabled=false}, for the one
 * client that has Kami on ({@code server-kami-off-report.json}).</p>
 */
public final class MultiplayerServer {
    static final Logger LOG = LoggerFactory.getLogger("thaumictinkerer-mpserver");
    private static ServerReport report;
    private static volatile MinecraftServer server;

    private MultiplayerServer() {}

    public static synchronized void arm(String loader) {
        if (!Boolean.getBoolean("thaumictinkerer.mpServer") || report != null) return;
        Path out = Path.of(System.getProperty("thaumictinkerer.mpServer.outDir", "build/tinkerer-multiplayer")).toAbsolutePath();
        int timeoutSeconds = Integer.getInteger("thaumictinkerer.mpServer.timeoutSeconds", 2400);
        // The second server of the run (variant "kami-off": kami.forceenabled=false in its config) writes its own files.
        String variant = System.getProperty("thaumictinkerer.mpServer.variant", "");
        Scenes.variant(variant);
        String prefix = variant.isEmpty() ? "server" : "server-" + variant;
        ServerReport armed = new ServerReport(out.resolve(prefix + "-report.json"), loader);
        armed.fact("variant", variant.isEmpty() ? "main" : variant);
        report = armed;

        LifecycleEvent.SERVER_STARTED.register(started -> {
            server = started;
            guarded("setup", () -> Scenes.build(started, armed));
            try {
                // The Gradle task starts the clients when this file is there.
                Files.writeString(out.resolve(prefix + "-ready"), "ready " + Instant.now() + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("cannot write " + out.resolve(prefix + "-ready"), e);
            }
            LOG.info("Thaumic Tinkerer multiplayer server ready on {} (report {})", loader, armed.file());
        });
        LifecycleEvent.SERVER_STOPPING.register(stopping -> {
            guarded("end", () -> Scenes.finish(armed));
            if ("RUNNING".equals(armed.status())) armed.status("COMPLETED", "the server was stopped");
        });
        PlayerEvent.PLAYER_JOIN.register(player -> guarded("join", () -> Scenes.joined(player, armed)));
        // The config handshake, in the configuration phase of every connection (before PLAYER_JOIN, or instead of it for a refused client).
        thaumic.tinkerer.common.core.handler.ConfigSync.ANSWERED.register((profile, options, mismatches) -> guarded("config_handshake",
                () -> Scenes.answered(profile.getName(), options, mismatches, armed)));
        // What reached the server of a player's right clicks (after the protection mods' own listeners, which run first or cancel):
        // a record for the report, never an answer (always pass).
        dev.architectury.event.events.common.InteractionEvent.RIGHT_CLICK_BLOCK.register((player, hand, pos, face) -> {
            if (!player.level().isClientSide && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                armed.event("right_click_block", player.getGameProfile().getName(), net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(player.getMainHandItem().getItem()) + " on " + pos.toShortString() + " face " + face);
            }
            return dev.architectury.event.EventResult.pass();
        });
        dev.architectury.event.events.common.InteractionEvent.RIGHT_CLICK_ITEM.register((player, hand) -> {
            if (!player.level().isClientSide && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                armed.event("right_click_item", player.getGameProfile().getName(), net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(player.getMainHandItem().getItem()) + " at " + player.blockPosition().toShortString());
            }
            return dev.architectury.event.CompoundEventResult.pass();
        });
        PlayerEvent.PLAYER_QUIT.register(player -> guarded("quit", () -> Scenes.left(player, armed)));
        CommandRegistrationEvent.EVENT.register((dispatcher, context, selection) -> TtmpCommand.register(dispatcher, armed));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if ("RUNNING".equals(armed.status())) armed.status("ABORTED", "the JVM ended before the server was stopped");
        }, "tt-mpserver-shutdown"));
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(timeoutSeconds * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            if (!"RUNNING".equals(armed.status())) return;
            LOG.error("multiplayer server watchdog: {} s elapsed, stopping", timeoutSeconds);
            armed.status("TIMED_OUT", "the server was not stopped within " + timeoutSeconds + " s");
            MinecraftServer running = server;
            try {
                if (running != null) running.execute(() -> running.halt(false));
                Thread.sleep(30_000L);
            } catch (InterruptedException | RuntimeException ignored) {
                // halted below either way
            }
            Runtime.getRuntime().halt(3);
        }, "tt-mpserver-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        LOG.info("Thaumic Tinkerer multiplayer server harness armed on {}: report {}, timeout {} s", loader, armed.file(), timeoutSeconds);
    }

    private static void guarded(String scenario, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) {
            LOG.error("multiplayer server harness: {} threw", scenario, e);
            StringWriter text = new StringWriter();
            e.printStackTrace(new PrintWriter(text));
            report.add(scenario, "harness", ServerReport.Result.HARNESS_ERROR, text.toString());
        }
    }
}
