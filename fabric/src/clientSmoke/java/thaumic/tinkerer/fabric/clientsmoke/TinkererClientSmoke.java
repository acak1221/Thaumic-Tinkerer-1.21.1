package thaumic.tinkerer.fabric.clientsmoke;

import net.fabricmc.api.ClientModInitializer;
import thaumic.tinkerer.clientsmoke.ClientSmoke;

/**
 * Entry point of the Thaumic Tinkerer client smoke on Fabric (the {@code client} entrypoint of the harness mod
 * {@code thaumictinkerer_clientsmoke}). Test-only code of the port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on,
 * not distributed.
 *
 * <p>The harness itself is loader-neutral (common/src/clientSmoke, {@link ClientSmoke}); this class hands it the Fabric side,
 * {@link FabricSmokePlatform}. Armed only by {@code -Dthaumictinkerer.clientSmoke=true}, which the {@code tinkererClientSmoke} run
 * sets; in any other launch nothing happens. See {@code docs/CLIENT_VERIFICATION.md}.</p>
 */
public final class TinkererClientSmoke implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("thaumictinkerer.clientSmoke")) return;
        ClientSmoke.arm(new FabricSmokePlatform());
    }
}
