package thaumic.tinkerer.clientsmoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Starts the Thaumic Tinkerer client smoke, on either loader. Test-only code of the port, new in the 1.21.1 port (no 1.7.10
 * counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), not distributed.
 *
 * <p>Armed only by {@code -Dthaumictinkerer.clientSmoke=true}, which the client smoke runs set ({@code runTinkererClientSmoke} on
 * Fabric, {@code :neoforge:runClientSmoke} on NeoForge); in any other launch {@link #arm} does nothing. The loader's harness mod
 * calls it once, from its client entry: {@code TinkererClientSmoke} on Fabric, {@code TinkererClientSmokeNeoForge} on NeoForge.
 * See {@code docs/CLIENT_VERIFICATION.md}.</p>
 */
public final class ClientSmoke {
    private static volatile SmokePlatform platform;

    private ClientSmoke() {}

    /** Arms the harness on {@code loader}: the report, the client tick that drives the phases, the watchdog. */
    public static synchronized void arm(SmokePlatform loader) {
        if (!Boolean.getBoolean("thaumictinkerer.clientSmoke") || platform != null) return;
        platform = loader;
        Path outDir = Path.of(System.getProperty("thaumictinkerer.clientSmoke.outDir", "build/tinkerer-client-smoke")).toAbsolutePath();
        Path sourceRoot = Path.of(System.getProperty("thaumictinkerer.clientSmoke.sourceRoot", ".")).toAbsolutePath();
        int timeoutSeconds = Integer.getInteger("thaumictinkerer.clientSmoke.timeoutSeconds", 1500);
        try {
            Files.createDirectories(outDir.resolve("screenshots"));
        } catch (IOException e) {
            throw new IllegalStateException("cannot create " + outDir, e);
        }
        SmokeRun run = new SmokeRun(outDir, sourceRoot);
        run.report().harnessField("loader", loader.loader());
        run.report().write();
        loader.onEndClientTick(client -> {
            if (run.client() == null) run.install(client);
            run.tick(client);
        });
        Watchdog.start(run, timeoutSeconds);
        SmokeRun.LOG.info("Thaumic Tinkerer client smoke armed on {}: out={} sources={} timeout={}s", loader.loader(), outDir, sourceRoot, timeoutSeconds);
    }

    /** The loader of the armed run. */
    static SmokePlatform platform() {
        SmokePlatform armed = platform;
        if (armed == null) throw new IllegalStateException("the client smoke is not armed");
        return armed;
    }
}
