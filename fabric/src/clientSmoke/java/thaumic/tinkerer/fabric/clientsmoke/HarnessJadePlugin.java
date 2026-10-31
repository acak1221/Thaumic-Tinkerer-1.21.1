package thaumic.tinkerer.fabric.clientsmoke;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;

/**
 * Hands the text of every block tooltip Jade collects to {@link JadeProbe} (the {@code jade} entrypoint of the harness mod).
 * Test-only code of the port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on, not distributed.
 */
public final class HarnessJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addTooltipCollectedCallback((box, accessor) -> {
            if (accessor instanceof BlockAccessor block) JadeProbe.seen(block.getPosition(), box.getTooltip().getMessage());
        });
    }
}
