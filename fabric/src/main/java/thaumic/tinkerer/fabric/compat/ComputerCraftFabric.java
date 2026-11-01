/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the Fabric glue of the optional CC: Tweaked integration.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.fabric.compat;

import dan200.computercraft.api.peripheral.PeripheralLookup;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.upgrades.UpgradeType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import thaumic.tinkerer.compat.computer.cc.ComputerCraftCompat;
import thaumic.tinkerer.compat.computer.cc.TinkererPeripheralProvider;

/**
 * 1.7.10 {@code TTCommonProxy.initCCPeripherals} on Fabric: the peripheral provider of the add-on and the type of the Fume
 * Dissipator's turtle upgrade. {@code ThaumicTinkererFabric.onInitialize()} calls {@link #register()} only when CC: Tweaked is
 * installed ({@code Computers.ccTweakedLoaded()}); this class is not loaded otherwise.
 *
 * <ul>
 *   <li>{@link PeripheralLookup}: a fallback provider, asked for every block for which no other provider answered (the place of
 *       1.7.10's {@code IPeripheralProvider}, which was asked for every tile entity): Thaumcraft's aspect containers and essentia
 *       transports of any mod, and the add-on's devices.</li>
 *   <li>The upgrade type {@code thaumictinkerer:gas_remover} in CC: Tweaked's registry of turtle upgrade types. CC: Tweaked creates
 *       that registry when its API service is first asked ({@link ITurtleUpgrade#typeRegistry()}), so it exists here whatever the
 *       order of the mods' initialisers; the registries are still open during mod initialisation.</li>
 * </ul>
 */
public final class ComputerCraftFabric {
    private static boolean registered;

    private ComputerCraftFabric() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        PeripheralLookup.get().registerFallback((level, pos, state, blockEntity, side) -> TinkererPeripheralProvider.find(blockEntity, side));
        Registry.register(upgradeTypes(), ComputerCraftCompat.GAS_REMOVER_ID, ComputerCraftCompat.gasRemoverType());
    }

    @SuppressWarnings("unchecked")
    private static Registry<UpgradeType<? extends ITurtleUpgrade>> upgradeTypes() {
        var key = ITurtleUpgrade.typeRegistry();
        Registry<?> registry = BuiltInRegistries.REGISTRY.get(key.location());
        if (registry == null) throw new IllegalStateException("CC: Tweaked created no registry " + key.location());
        return (Registry<UpgradeType<? extends ITurtleUpgrade>>) registry;
    }
}
