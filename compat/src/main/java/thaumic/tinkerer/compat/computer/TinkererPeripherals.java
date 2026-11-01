/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [13 Sep 2013, 00:56:19 (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.machines.IArcaneBore;
import thaumcraft.api.machines.IArcaneEar;
import thaumcraft.api.machines.IBrainJar;
import thaumcraft.api.machines.IDeconstructor;
import thaumic.tinkerer.common.block.tile.TileAspectAnalyzer;
import thaumic.tinkerer.common.block.tile.TileGolemConnector;
import thaumic.tinkerer.common.block.tile.TileMagnet;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;

/**
 * 1.7.10 {@code PeripheralHandler.getPeripheral(TileEntity)}: which peripheral a block entity is, tried in the original order.
 *
 * <ol>
 *   <li>any {@link IAspectContainer}: {@code tt_aspectContainer} ({@link AspectContainerTable});</li>
 *   <li>the Deconstruction Table ({@code tt_deconstructor}), the Brain in a Jar ({@code tt_braininajar}), the Arcane Ear
 *       ({@code tt_arcaneear}) and the Arcane Bore ({@code tt_arcanebore}) of Thaumcraft, found by their interfaces of
 *       {@code thaumcraft.api.machines} (1.7.10 asked for the internal tile entity classes);</li>
 *   <li>any {@link IEssentiaTransport}: {@code tt_aspectTransport} ({@link EssentiaTransportTable});</li>
 *   <li>the add-on's own block entities that were {@code IPeripheral}s ("Hacky", in 1.7.10 every {@code IPeripheral} tile entity
 *       of a class in {@code thaumic.tinkerer}): the Dynamism Tablet, the two Attractors, the Aspectalyzer, the Golem
 *       Connector.</li>
 * </ol>
 *
 * <p>The order matters once: the Transvector Interface is an {@link IAspectContainer} (it forwards the bound block's aspects),
 * so 1.7.10 answered {@code tt_aspectContainer} for it and its own {@code IPeripheral} methods, which forwarded the bound
 * block's peripheral, were never reached. The port answers the same. The side a computer asks from is ignored, as in 1.7.10.</p>
 *
 * <p>Not ported: the blacklist of class names that other mods could send by IMC ({@code ADD_CC_BLACKLIST}); 1.21.1 has no
 * IMC (docs/API_GAPS.md section 1.12, row 14).</p>
 */
public final class TinkererPeripherals {
    private TinkererPeripherals() {}

    /** The peripheral of a block entity, or null when it is none. */
    @Nullable
    public static PeripheralTable find(@Nullable Object tile) {
        if (tile == null) return null;

        if (tile instanceof IAspectContainer container) return new AspectContainerTable(container);

        if (tile instanceof IDeconstructor deconstructor) return new DeconstructorTable(deconstructor);

        if (tile instanceof IBrainJar jar) return new BrainInAJarTable(jar);

        if (tile instanceof IArcaneEar ear) return new ArcaneEarTable(ear);

        if (tile instanceof IArcaneBore bore) return new ArcaneBoreTable(bore);

        if (tile instanceof IEssentiaTransport transport) return new EssentiaTransportTable(transport);

        if (tile instanceof TileAnimationTablet tablet) return new AnimationTabletTable(tablet);
        if (tile instanceof TileMagnet magnet) return new MagnetTable(magnet);
        if (tile instanceof TileAspectAnalyzer analyzer) return new AspectAnalyzerTable(analyzer);
        if (tile instanceof TileGolemConnector connector) return new GolemConnectorTable(connector);
        return null;
    }
}
