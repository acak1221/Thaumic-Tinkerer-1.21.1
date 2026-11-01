/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [13 Sep 2013, 01:10:52 (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.machines.IDeconstructor;

/**
 * {@code tt_deconstructor}: Thaumcraft's Deconstruction Table, 1.7.10 {@code PeripheralDeconstructor}, through
 * {@code thaumcraft.api.machines} (request A12).
 *
 * <ul>
 *   <li>{@code hasAspect()}: whether a primal aspect waits on the table to be taken.</li>
 *   <li>{@code hasItem()}: whether an item lies in the input slot.</li>
 *   <li>{@code getAspect()}: the tag of the waiting aspect; nothing (no value) while there is none.</li>
 * </ul>
 */
public final class DeconstructorTable implements PeripheralTable {
    public static final String TYPE = "tt_deconstructor";
    public static final String[] METHOD_NAMES = { "hasAspect", "hasItem", "getAspect" };

    private final IDeconstructor deconstructor;

    public DeconstructorTable(IDeconstructor deconstructor) {
        this.deconstructor = deconstructor;
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
        return deconstructor;
    }

    @Override
    public Object[] call(int method, PeripheralArgs arguments) {
        Aspect aspect = deconstructor.getReward();
        return switch (method) {
            case 0 -> new Object[] { aspect != null };
            case 1 -> new Object[] { deconstructor.hasItem() };
            case 2 -> aspect == null ? null : new Object[] { aspect.getTag() };
            default -> null;
        };
    }
}
