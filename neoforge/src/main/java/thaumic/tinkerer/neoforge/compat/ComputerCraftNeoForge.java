/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the NeoForge glue of the optional CC: Tweaked
 * integration. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.compat;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.upgrades.UpgradeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumic.tinkerer.common.ThaumicTinkerer;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.compat.computer.cc.ComputerCraftCompat;
import thaumic.tinkerer.compat.computer.cc.TinkererPeripheralProvider;

/**
 * 1.7.10 {@code TTCommonProxy.initCCPeripherals} on NeoForge: the peripheral provider of the add-on and the type of the Fume
 * Dissipator's turtle upgrade. The mod constructor calls {@link #register} only when CC: Tweaked is installed
 * ({@code Computers.ccTweakedLoaded()}); this class is not loaded otherwise.
 *
 * <ul>
 *   <li>{@link PeripheralCapability}: NeoForge asks block capabilities per block entity type, and 1.7.10's provider was asked for
 *       every tile entity, so the provider is registered for every block entity type there is (the registry is complete when
 *       {@link RegisterCapabilitiesEvent} is posted). It answers null quickly for the block entities that are no peripheral of
 *       the add-on, and CC: Tweaked then tries its own generic peripherals as before.</li>
 *   <li>The upgrade type {@code thaumictinkerer:gas_remover}, by a {@link DeferredRegister} of CC: Tweaked's registry of turtle
 *       upgrade types (created by CC: Tweaked in {@code NewRegistryEvent}).</li>
 * </ul>
 */
public final class ComputerCraftNeoForge {
    private ComputerCraftNeoForge() {}

    public static void register(IEventBus modBus) {
        DeferredRegister<UpgradeType<? extends ITurtleUpgrade>> types = DeferredRegister.create(ITurtleUpgrade.typeRegistry(), LibMisc.MOD_ID);
        types.register(ComputerCraftCompat.GAS_REMOVER_ID.getPath(), ComputerCraftCompat::gasRemoverType);
        types.register(modBus);
        modBus.addListener(ComputerCraftNeoForge::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        ThaumicTinkerer.guard("ComputerCraftNeoForge: the peripheral capability", () -> {
            for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) registerPeripheral(event, type);
        });
    }

    private static <T extends BlockEntity> void registerPeripheral(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(PeripheralCapability.get(), type, TinkererPeripheralProvider::find);
    }
}
