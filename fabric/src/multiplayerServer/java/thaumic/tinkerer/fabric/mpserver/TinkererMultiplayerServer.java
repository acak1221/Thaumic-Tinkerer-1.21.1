package thaumic.tinkerer.fabric.mpserver;

import net.fabricmc.api.DedicatedServerModInitializer;
import thaumic.tinkerer.mpserver.MultiplayerServer;

/**
 * Entry point of the server half of the multiplayer check on Fabric (the {@code server} entrypoint of the test mod
 * {@code thaumictinkerer_mpserver}). Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the
 * add-on (see NOTICE.md); loaded only by the {@code tinkererMpServer} run, never part of a release jar. The harness itself is
 * loader-neutral ({@code common/src/multiplayerServer}, {@link MultiplayerServer}); see {@code docs/MULTIPLAYER_VERIFICATION.md}.
 */
public final class TinkererMultiplayerServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        MultiplayerServer.arm("fabric");
    }
}
