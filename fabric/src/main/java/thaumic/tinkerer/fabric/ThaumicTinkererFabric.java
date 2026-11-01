/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.fabric;

import net.fabricmc.api.ModInitializer;
import thaumcraft.api.IThaumcraftAddon;
import thaumic.tinkerer.common.ThaumicTinkerer;
import thaumic.tinkerer.compat.computer.Computers;
import thaumic.tinkerer.fabric.compat.ComputerCraftFabric;
import thaumic.tinkerer.fabric.hooks.FabricBlockProtection;

/** Fabric {@code main} entrypoint and the {@code "thaumcraft"} entrypoint of the add-on. */
public final class ThaumicTinkererFabric implements ModInitializer, IThaumcraftAddon {
    @Override
    public void onInitialize() {
        ThaumicTinkerer.init();
        // The loader side of BlockProtection: Fabric's and Architectury's break and place listeners (docs/HOOKS.md).
        ThaumicTinkerer.guard("FabricBlockProtection.install", FabricBlockProtection::install);
        // The optional CC: Tweaked peripherals and turtle upgrade (docs/deviations/ComputerCraft.md); no class of it is loaded without it.
        if (Computers.ccTweakedLoaded()) ThaumicTinkerer.guard("ComputerCraftFabric.register", ComputerCraftFabric::register);
    }

    @Override
    public void onThaumcraftReady() {
        ThaumicTinkerer.onThaumcraftReady();
        // The loader glue of the Transvector Interface's fluids (FluidStorage.SIDED), once the block entity type exists.
        ThaumicTinkerer.guard("TileBlocksFabric.register", TileBlocksFabric::register);
    }
}
