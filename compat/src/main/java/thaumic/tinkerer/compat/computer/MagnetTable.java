/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [12 Sep 2013, 17:10:55 (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import thaumic.tinkerer.common.block.tile.TileMagnet;
import thaumic.tinkerer.common.block.tile.TileMobMagnet;

/**
 * {@code tt_magnet}: the Kinetic Attractor ({@code TileMagnet}) and the Corporeal Attractor ({@code TileMobMagnet}, which
 * inherited the type and added two methods), the {@code IPeripheral} parts of both 1.7.10 classes.
 *
 * <ul>
 *   <li>{@code isPulling()}: true while the attractor pulls (blue). {@code setPulling(boolean)}: true pulls, false pushes, as a
 *       click does; answers nothing.</li>
 *   <li>{@code getSignal()}: the strongest redstone signal of the six neighbours (1.7.10 asked
 *       {@code getIndirectPowerLevelTo} of each side, the vanilla {@code getBestNeighborSignal}).</li>
 *   <li>Corporeal Attractor only: {@code getAdultSearch()} and {@code setAdultSearch(boolean)}, whether it moves adult animals
 *       (true) or children (false); the setter answers nothing.</li>
 * </ul>
 */
public final class MagnetTable implements PeripheralTable {
    public static final String TYPE = "tt_magnet";
    public static final String[] KINETIC_METHOD_NAMES = { "isPulling", "setPulling", "getSignal" };
    public static final String[] CORPOREAL_METHOD_NAMES = { "isPulling", "setPulling", "getSignal", "getAdultSearch",
            "setAdultSearch" };

    private final TileMagnet magnet;

    public MagnetTable(TileMagnet magnet) {
        this.magnet = magnet;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String[] methodNames() {
        return (magnet instanceof TileMobMagnet ? CORPOREAL_METHOD_NAMES : KINETIC_METHOD_NAMES).clone();
    }

    @Override
    public Object target() {
        return magnet;
    }

    @Override
    public Object[] call(int method, PeripheralArgs arguments) {
        if (magnet instanceof TileMobMagnet mob) {
            // TileMobMagnet.callMethod: its two, then those of TileMagnet
            switch (method) {
                case 3:
                    return new Object[] { mob.adult };
                case 4:
                    mob.setAdult(arguments.getBoolean(0));
                    return null;
                default:
                    break;
            }
        }
        return switch (method) {
            case 0 -> new Object[] { magnet.isPulling() };
            case 1 -> {
                magnet.setPulling(arguments.getBoolean(0));
                yield null;
            }
            case 2 -> new Object[] { magnet.getRedstone() };
            default -> null;
        };
    }
}
