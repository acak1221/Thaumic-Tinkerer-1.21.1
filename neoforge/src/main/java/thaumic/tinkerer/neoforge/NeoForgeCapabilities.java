/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.ThaumicTinkerer;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvector;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.content.TileBlocks;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * The block capabilities of the add-on on NeoForge, the counterpart of the Fabric lookups ({@code TileBlocksFabric}): what
 * the Transvector Interface hands on of the block it is bound to (1.7.10: the interface was an {@code IFluidHandler} and an
 * {@code ISidedInventory} that forwarded every call to the bound tile entity, side for side).
 *
 * <ul>
 *   <li><b>Fluids</b> ({@code Capabilities.FluidHandler.BLOCK}): present while the interface is bound to a block that has a
 *       fluid handler for the side asked, with the same refusals as Fabric (unbound, bound to itself, bound to another
 *       interface: no handler). The handler handed out asks for the bound block's handler again on every call, because
 *       NeoForge's {@code BlockCapabilityCache} keeps what the provider returned: a pipe must not keep filling the handler of
 *       a tank that was broken or unbound since. When the binding changes, {@link TileTransvector#setBindingListener} has
 *       the position's capabilities invalidated, so a cache that saw "none" asks again.</li>
 *   <li><b>Items</b> ({@code Capabilities.ItemHandler.BLOCK}): the interface's own {@code WorldlyContainer}, wrapped. On
 *       Fabric the Transfer API wraps any {@code Container} block entity by itself; NeoForge wraps only the vanilla ones,
 *       so without this a modded pipe would not see the interface (a vanilla hopper would, through the vanilla container
 *       path). The wrapper reads the interface on every call, so it never goes stale.</li>
 *   <li><b>Items of every other device</b> ({@code Capabilities.ItemHandler.BLOCK}, {@link #containerItems}): for every block
 *       entity type of the add-on, the block entity's {@code Container} wrapped the same way, so that what Fabric's Transfer API
 *       does by itself for the Restorer, the Funnel, the Enchanter, the Dynamism Tablet, the Remote Placer, the Corporeal
 *       Attractor, the Aspect Analyzer and the Celestial Gateway holds on NeoForge too: a {@code WorldlyContainer} asked for a
 *       side keeps its side rules ({@code SidedInvWrapper}: {@code getSlotsForFace}, {@code canPlaceItemThroughFace},
 *       {@code canTakeItemThroughFace}); without a side, or for a plain {@code Container}, the whole container
 *       ({@code InvWrapper}: {@code canPlaceItem} still decides what goes in), the convention of NeoForge's own furnace and brewing
 *       stand. A block entity that is no {@code Container} gets none. The types are taken from the registry (every type in the
 *       add-on's namespace but the Interface's), so a new device is covered without a change here.</li>
 *   <li><b>Not here: energy</b>, which the interface forwards on neither loader (docs/deviations/TileBlocks.md, 2.5).
 *       Essentia goes through the Thaumcraft interfaces, which need no loader glue.</li>
 * </ul>
 */
public final class NeoForgeCapabilities {
    private NeoForgeCapabilities() {}

    /** From the mod constructor: the capability providers and the invalidation on a binding change. */
    public static void register(IEventBus modBus) {
        modBus.addListener(NeoForgeCapabilities::onRegisterCapabilities);
        TileTransvector.setBindingListener(BlockEntity::invalidateCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        ThaumicTinkerer.guard("NeoForgeCapabilities: the Transvector Interface", () -> {
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TileBlocks.interfaceType(), NeoForgeCapabilities::interfaceFluids);
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TileBlocks.interfaceType(), NeoForgeCapabilities::interfaceItems);
        });
        ThaumicTinkerer.guard("NeoForgeCapabilities: the item handlers of the add-on's containers", () -> {
            for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
                ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
                if (id == null || !LibMisc.MOD_ID.equals(id.getNamespace()) || type == TileBlocks.interfaceType()) continue;
                registerContainerItems(event, type);
            }
        });
    }

    private static <T extends BlockEntity> void registerContainerItems(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, NeoForgeCapabilities::containerItems);
    }

    /**
     * The item capability of a device of the add-on (all but the Interface): its {@code WorldlyContainer} for a side, its
     * {@code Container} for no side or when it is a plain container; none when the block entity is no container.
     */
    @Nullable
    static IItemHandler containerItems(BlockEntity device, @Nullable Direction side) {
        if (side != null && device instanceof WorldlyContainer sided) return new SidedInvWrapper(sided, side);
        if (device instanceof Container container) return new InvWrapper(container);
        return null;
    }

    /** The fluid capability of an interface: a forwarding handler while the bound block has one for {@code side}, else none. */
    @Nullable
    static IFluidHandler interfaceFluids(TileTransvectorInterface device, @Nullable Direction side) {
        return boundFluids(device, side) == null ? null : new BoundFluids(device, side);
    }

    /**
     * The fluid handler of the bound block for {@code side}, or null while the interface is not bound, the bound block has
     * none, or it is the interface itself or another interface (which the Binder refuses to bind and which would send the
     * query round in a circle).
     */
    @Nullable
    static IFluidHandler boundFluids(TileTransvectorInterface device, @Nullable Direction side) {
        Level level = device.getLevel();
        BlockEntity bound = device.getTile();
        if (level == null || bound == null || bound == device || bound instanceof TileTransvectorInterface) return null;
        return level.getCapability(Capabilities.FluidHandler.BLOCK, bound.getBlockPos(), bound.getBlockState(), bound, side);
    }

    /** The item capability of an interface: its container for a side, all of it for no side (NeoForge's convention). */
    static IItemHandler interfaceItems(TileTransvectorInterface device, @Nullable Direction side) {
        return side == null ? new InvWrapper(device) : new SidedInvWrapper(device, side);
    }

    /** Hands every call to the bound block's handler of the moment; an empty handler while there is none. */
    private record BoundFluids(TileTransvectorInterface device, @Nullable Direction side) implements IFluidHandler {
        private IFluidHandler bound() {
            IFluidHandler handler = boundFluids(device, side);
            return handler != null ? handler : EmptyFluidHandler.INSTANCE;
        }

        @Override
        public int getTanks() {
            return bound().getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return bound().getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return bound().getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return bound().isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return bound().fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return bound().drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return bound().drain(maxDrain, action);
        }
    }
}
