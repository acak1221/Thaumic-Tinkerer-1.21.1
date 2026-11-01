/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [Dec 11, 2013, 9:36:45 PM (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import thaumcraft.api.machines.IBrainJar;

/**
 * {@code tt_braininajar}: Thaumcraft's Brain in a Jar, 1.7.10 {@code PeripheralBrainInAJar}, through
 * {@code thaumcraft.api.machines} (request A12). {@code getXP()}: the experience points stored in the jar (0 to 2000).
 */
public final class BrainInAJarTable implements PeripheralTable {
    public static final String TYPE = "tt_braininajar";
    public static final String[] METHOD_NAMES = { "getXP" };

    private final IBrainJar jar;

    public BrainInAJarTable(IBrainJar jar) {
        this.jar = jar;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String[] methodNames() {
        return METHOD_NAMES.clone();
    }

    @Override
    public Object target() {
        return jar;
    }

    @Override
    public Object[] call(int method, PeripheralArgs arguments) {
        return new Object[] { jar.getXp() };
    }
}
