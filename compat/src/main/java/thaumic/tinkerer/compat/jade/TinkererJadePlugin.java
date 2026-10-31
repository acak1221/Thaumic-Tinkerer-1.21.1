/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the Jade plugin, which replaces the Waila
 * registration of 1.7.10 ({@code TTinkererProvider.callbackRegister}). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.jade;

import java.util.List;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import thaumic.tinkerer.compat.tooltip.TinkererTooltips;

/**
 * Registers every tooltip of {@link TinkererTooltips}: the server data by block entity class (both sides), the body lines
 * by block class (client). Found by Jade through the {@code jade} entrypoint of fabric.mod.json; the annotation is what a
 * NeoForge build of Jade looks for.
 */
@WailaPlugin
public final class TinkererJadePlugin implements IWailaPlugin {
    private static final List<TooltipProvider> PROVIDERS = TinkererTooltips.ALL.stream().map(TooltipProvider::new).toList();

    @Override
    public void register(IWailaCommonRegistration registration) {
        for (TooltipProvider provider : PROVIDERS) {
            registration.registerBlockDataProvider(provider, provider.tooltip().blockEntityClass());
        }
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        for (TooltipProvider provider : PROVIDERS) {
            registration.registerBlockComponent(provider, provider.tooltip().blockClass());
        }
    }
}
