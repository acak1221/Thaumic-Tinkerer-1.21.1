package thaumic.tinkerer.neoforge.clientsmoke;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import thaumic.tinkerer.clientsmoke.ClientSmoke;

/**
 * Entry point of the Thaumic Tinkerer client smoke on NeoForge: the client-only harness mod {@code thaumictinkerer_clientsmoke}
 * (neoforge/src/clientSmoke/resources/META-INF/neoforge.mods.toml), the counterpart of the Fabric entrypoint
 * {@code TinkererClientSmoke}. Test-only code of the port, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like
 * the add-on (see NOTICE.md), not distributed.
 *
 * <p>Loaded only by {@code :neoforge:runClientSmoke} (from the output of the {@code clientSmoke} source set), never part of the
 * add-on's jar, and armed only by {@code -Dthaumictinkerer.clientSmoke=true}, which that run sets. The harness itself is
 * loader-neutral (common/src/clientSmoke, {@link ClientSmoke}); this class hands it the NeoForge side, {@link NeoForgeSmokePlatform}.
 * It arms from the mod constructor, as the Fabric entrypoint arms from {@code onInitializeClient}: nothing happens before the
 * first client tick, and the first phase waits for the resources to be loaded.</p>
 */
@Mod(value = TinkererClientSmokeNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class TinkererClientSmokeNeoForge {
    public static final String MOD_ID = "thaumictinkerer_clientsmoke";

    public TinkererClientSmokeNeoForge() {
        if (!Boolean.getBoolean("thaumictinkerer.clientSmoke")) return;
        ClientSmoke.arm(new NeoForgeSmokePlatform());
    }
}
