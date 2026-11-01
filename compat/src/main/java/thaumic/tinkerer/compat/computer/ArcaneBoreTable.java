/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [Dec 11, 2013, 10:03:09 PM (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import thaumcraft.api.machines.IArcaneBore;

/**
 * {@code tt_arcanebore}: Thaumcraft's Arcane Bore, 1.7.10 {@code PeripheralArcaneBore}, through {@code thaumcraft.api.machines}
 * (request A12 of docs/API_GAPS.md), which answers each question with the original formula.
 *
 * <ul>
 *   <li>{@code hasPickaxe()}, {@code hasFocus()}: whether slot 1 holds a pickaxe and slot 0 an Excavation focus.</li>
 *   <li>{@code isPickaxeBroken()}: the pickaxe is one use from breaking ({@code damage + 1 == maxDamage}); false without one.</li>
 *   <li>{@code isWorking()}: powered, with a focus and a damageable pickaxe that is not worn down to its last point.</li>
 *   <li>{@code getRadius()}: the width of the hole, {@code 1 + (area + maxRadius) * 2}: the API's radius is
 *       {@code area + maxRadius}.</li>
 *   <li>{@code getSpeed()}: the focus's Potency plus the pickaxe's Efficiency. {@code hasNativeClusters()}: the pickaxe is the
 *       Pickaxe of the Core. {@code getFortune()}: the pickaxe's own Fortune level. {@code hasSilkTouch()}: the pickaxe's Silk
 *       Touch.</li>
 * </ul>
 */
public final class ArcaneBoreTable implements PeripheralTable {
    public static final String TYPE = "tt_arcanebore";
    public static final String[] METHOD_NAMES = { "hasPickaxe", "hasFocus", "isPickaxeBroken", "isWorking", "getRadius", "getSpeed",
            "hasNativeClusters", "getFortune", "hasSilkTouch" };

    private final IArcaneBore bore;

    public ArcaneBoreTable(IArcaneBore bore) {
        this.bore = bore;
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
        return bore;
    }

    @Override
    public Object[] call(int method, PeripheralArgs arguments) {
        return switch (method) {
            case 0 -> new Object[] { bore.hasPickaxe() };
            case 1 -> new Object[] { bore.hasFocus() };
            case 2 -> new Object[] { bore.isPickaxeBroken() };
            case 3 -> new Object[] { bore.isWorking() };
            case 4 -> new Object[] { 1 + bore.getRadius() * 2 };
            case 5 -> new Object[] { bore.getSpeed() };
            case 6 -> new Object[] { bore.hasNativeClusters() };
            case 7 -> new Object[] { bore.getPickaxeFortune() };
            case 8 -> new Object[] { bore.hasSilkTouch() };
            default -> null;
        };
    }
}
