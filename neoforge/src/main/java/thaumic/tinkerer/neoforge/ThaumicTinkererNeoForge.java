/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import thaumic.tinkerer.common.ThaumicTinkerer;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.compat.computer.Computers;
import thaumic.tinkerer.neoforge.client.ComputerCraftNeoForgeClient;
import thaumic.tinkerer.neoforge.client.ThaumicTinkererNeoForgeClient;
import thaumic.tinkerer.neoforge.compat.ComputerCraftNeoForge;
import thaumic.tinkerer.neoforge.hooks.NeoForgeBlockProtection;
import thaumic.tinkerer.neoforge.hooks.NeoForgeEventPosters;

/**
 * The NeoForge entry of the add-on: the counterpart of {@code ThaumicTinkererFabric}. The shared code registers everything
 * through Architectury; this class only owns the loader wiring. The Thaumcraft-ready hook is {@link ThaumicTinkererAddon}.
 */
@Mod(LibMisc.MOD_ID)
public final class ThaumicTinkererNeoForge {
    public ThaumicTinkererNeoForge(IEventBus bus) {
        ThaumicTinkerer.init();
        NeoForgeEventPosters.register(bus);
        // The loader side of BlockProtection: NeoForge's BreakEvent and EntityPlaceEvent (docs/HOOKS.md).
        ThaumicTinkerer.guard("NeoForgeBlockProtection.install", NeoForgeBlockProtection::install);
        NeoForgeCapabilities.register(bus);
        if (FMLEnvironment.dist == Dist.CLIENT) ThaumicTinkererNeoForgeClient.register(bus);
        // The optional CC: Tweaked peripherals and turtle upgrade (docs/deviations/ComputerCraft.md); no class of it is loaded without it.
        if (Computers.ccTweakedLoaded()) {
            ThaumicTinkerer.guard("ComputerCraftNeoForge.register", () -> ComputerCraftNeoForge.register(bus));
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ThaumicTinkerer.guard("ComputerCraftNeoForgeClient.register", () -> ComputerCraftNeoForgeClient.register(bus));
            }
        }
    }
}
