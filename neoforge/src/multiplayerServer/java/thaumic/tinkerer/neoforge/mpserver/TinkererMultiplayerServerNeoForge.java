package thaumic.tinkerer.neoforge.mpserver;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import thaumic.tinkerer.mpserver.MultiplayerServer;

/**
 * Entry point of the server half of the multiplayer check on NeoForge: the dedicated-server-only test mod
 * {@code thaumictinkerer_mpserver} (neoforge/src/multiplayerServer/resources/META-INF/neoforge.mods.toml), the counterpart of the
 * Fabric entrypoint {@code TinkererMultiplayerServer}. Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA
 * 3.0 like the add-on (see NOTICE.md); loaded only by {@code :neoforge:runMpServer}, never part of a release jar. The harness is
 * loader-neutral ({@code common/src/multiplayerServer}, {@link MultiplayerServer}); see {@code docs/MULTIPLAYER_VERIFICATION.md}.
 */
@Mod(value = TinkererMultiplayerServerNeoForge.MOD_ID, dist = Dist.DEDICATED_SERVER)
public final class TinkererMultiplayerServerNeoForge {
    public static final String MOD_ID = "thaumictinkerer_mpserver";

    public TinkererMultiplayerServerNeoForge() {
        MultiplayerServer.arm("neoforge");
    }
}
