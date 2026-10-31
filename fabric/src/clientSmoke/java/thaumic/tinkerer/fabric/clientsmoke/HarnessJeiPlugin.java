package thaumic.tinkerer.fabric.clientsmoke;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * Keeps JEI's runtime for {@link JeiProbe} (the {@code jei_mod_plugin} entrypoint of the harness mod). Test-only code of the
 * port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on, not distributed.
 */
public final class HarnessJeiPlugin implements IModPlugin {
    static volatile IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("thaumictinkerer_clientsmoke", "harness");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }
}
