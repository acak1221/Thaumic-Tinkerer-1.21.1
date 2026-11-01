package thaumic.tinkerer.clientsmoke;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The state of one smoke run: the phases in order, the report, the pictures. Test-only code of the port (no 1.7.10
 * counterpart); CC BY-NC-SA 3.0 like the add-on, not distributed.
 *
 * <p>Driven by the client tick. Every phase is isolated: a phase that throws is recorded and the next one starts. The run
 * ends by itself with {@link Minecraft#stop()} after the last phase; the watchdog ({@link Watchdog}, started by {@link ClientSmoke}) ends it when
 * a phase hangs.</p>
 */
final class SmokeRun {
    static final Logger LOG = LoggerFactory.getLogger("thaumictinkerer-clientsmoke");

    private final Path sourceRoot;
    private final SmokeReport report;
    private final Frames frames = new Frames();
    private final Shots shots;
    private final Map<String, Map<String, String>> languages = new HashMap<>();
    private final Deque<Phase> phases = new ArrayDeque<>();
    private Phase current;
    private TinkererContent content;
    private Minecraft client;
    private boolean finished;
    private boolean worldFailed;

    /** The singleplayer run: the phases of the client smoke. */
    SmokeRun(Path outDir, Path sourceRoot) {
        this(outDir, sourceRoot, List.of(new ResourcesPhase(), new WorldPhase(), new BlockRowsPhase(), new ItemGridPhase(""),
                new MenusPhase(""), new RendererPhase(), new HudPhase(), new IntegrationsPhase(), new CreativeTabPhase(),
                new LanguagePhase("ru_ru"), new ItemGridPhase("ru_ru"), new MenusPhase("ru_ru"), new FinishPhase()));
    }

    /** A run of other phases (the multiplayer mode, {@link MultiplayerClient#phases}); the last must be {@link FinishPhase}. */
    SmokeRun(Path outDir, Path sourceRoot, List<Phase> phases) {
        this.sourceRoot = sourceRoot;
        this.report = new SmokeReport(outDir);
        this.shots = new Shots(report);
        this.phases.addAll(phases);
    }

    Minecraft client() {
        return client;
    }

    SmokeReport report() {
        return report;
    }

    Path sourceRoot() {
        return sourceRoot;
    }

    Frames frames() {
        return frames;
    }

    Shots shots() {
        return shots;
    }

    TinkererContent content() {
        if (content == null) content = new TinkererContent(sourceRoot);
        return content;
    }

    /** The language file union of a code, cached. */
    Map<String, String> lang(String code) {
        return languages.computeIfAbsent(code, c -> {
            try {
                return LangFiles.load(client.getResourceManager(), c);
            } catch (IOException e) {
                throw new IllegalStateException("cannot read the " + c + " language files", e);
            }
        });
    }

    MinecraftServer server() {
        return client.getSingleplayerServer();
    }

    ServerPlayer serverPlayer() {
        MinecraftServer server = server();
        return server == null || client.player == null ? null : server.getPlayerList().getPlayer(client.player.getUUID());
    }

    ServerLevel serverLevel() {
        ServerPlayer player = serverPlayer();
        return player == null ? null : player.serverLevel();
    }

    boolean worldFailed() {
        return worldFailed;
    }

    void worldFailed(String reason) {
        worldFailed = true;
        report.fail("world", "world_not_available", "", "", reason);
    }

    /** Hook for phases that need to know a script of theirs failed (the world script, mostly). */
    void onScriptFailed(Script script) {
        LOG.warn("script '{}' failed: {}", script.name(), script.failure());
    }

    void install(Minecraft minecraft) {
        this.client = minecraft;
        frames.install(ClientSmoke.platform());
    }

    /** Runs {@code action} on the integrated server's thread; the step finishes when it did. */
    Script.Step onServer(Consumer<MinecraftServer> action) {
        CompletableFuture<?>[] future = { null };
        return run -> {
            if (future[0] == null) future[0] = CompletableFuture.runAsync(() -> action.accept(run.server()), run.server());
            if (!future[0].isDone()) return false;
            future[0].join();
            return true;
        };
    }

    void tick(Minecraft minecraft) {
        if (finished) return;
        try {
            if (current == null) {
                current = phases.pollFirst();
                if (current == null) return;
                if (current.needsWorld() && worldFailed) {
                    report.notRun(current.name(), current.name(), "skipped: the world of the run could not be created");
                    current = null;
                    return;
                }
                LOG.info("phase {} starts", current.name());
            }
            if (current.tick(this)) {
                LOG.info("phase {} finished", current.name());
                report.write();
                current = null;
            }
        } catch (Throwable error) {
            LOG.error("phase {} threw", current == null ? "?" : current.name(), error);
            report.harnessError(current == null ? "run" : current.name(), "phase", error);
            if (current instanceof WorldPhase) worldFailed = true;
            report.write();
            current = null;
        }
    }

    /** Puts phases after the ones queued (used by the resources phase result). */
    void queueAfterResources(List<Phase> more) {
        Deque<Phase> rest = new ArrayDeque<>(phases);
        phases.clear();
        phases.addAll(more);
        phases.addAll(rest);
    }

    void markFinished() {
        finished = true;
    }

    static Path gameDir() {
        return ClientSmoke.platform().gameDir();
    }
}
