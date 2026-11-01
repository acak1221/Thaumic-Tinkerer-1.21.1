/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [9 Sep 2013, 15:51:34 (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;

/**
 * {@code tt_animationTablet}: the Dynamism Tablet, the {@code IPeripheral} part of 1.7.10 {@code TileAnimationTablet}
 * ({@code getType}, {@code getMethodNames}, {@code callMethod} and its {@code *Implementation} helpers). The behaviour lives on
 * the block entity ({@code TileAnimationTablet.trigger()}, {@code getRotation()}, {@code setRotation(int)},
 * {@code setLeftClick}, {@code setRedstone}).
 *
 * <ul>
 *   <li>{@code getRedstone()} / {@code setRedstone(boolean)}: the Redstone Control mode; the setter answers nothing.</li>
 *   <li>{@code getLeftClick()} / {@code setLeftClick(boolean)}: true is the left click; the setter answers nothing.</li>
 *   <li>{@code getRotation()}: 0 north, 1 south, 2 west, 3 east (the metadata minus two). {@code setRotation(number)} turns the
 *       tablet and answers nothing; a value above 3 is the error {@code Invalid value: <n>.}, as in 1.7.10, and so is a
 *       negative one (1.7.10 wrote an invalid metadata for it, docs/deviations/TileBlocks.md section 2.1).</li>
 *   <li>{@code hasItem()}: whether the tablet holds an item. {@code trigger()}: starts a swing and answers true, or answers
 *       false while the tablet is busy.</li>
 * </ul>
 */
public final class AnimationTabletTable implements PeripheralTable {
    public static final String TYPE = "tt_animationTablet";
    public static final String[] METHOD_NAMES = { "getRedstone", "setRedstone", "getLeftClick", "setLeftClick",
            "getRotation", "setRotation", "hasItem", "trigger" };

    private final TileAnimationTablet tablet;

    public AnimationTabletTable(TileAnimationTablet tablet) {
        this.tablet = tablet;
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
        return tablet;
    }

    @Override
    public Object[] call(int method, PeripheralArgs arguments) {
        return switch (method) {
            case 0 -> new Object[] { tablet.redstone };
            case 1 -> {
                tablet.setRedstone(arguments.getBoolean(0));
                yield null;
            }
            case 2 -> new Object[] { tablet.leftClick };
            case 3 -> {
                tablet.setLeftClick(arguments.getBoolean(0));
                yield null;
            }
            case 4 -> new Object[] { tablet.getRotation() };
            case 5 -> setRotation((int) arguments.getDouble(0));
            case 6 -> new Object[] { !tablet.isEmpty() };
            case 7 -> new Object[] { tablet.trigger() };
            default -> null;
        };
    }

    private Object[] setRotation(int rotation) {
        try {
            tablet.setRotation(rotation);
        } catch (IllegalArgumentException refused) {
            throw new PeripheralError(refused.getMessage());
        }
        return null;
    }
}
