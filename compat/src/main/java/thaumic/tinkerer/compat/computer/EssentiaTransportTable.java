/**
 * Created by Katrina on 03/03/14.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import net.minecraft.core.Direction;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;

/**
 * {@code tt_aspectTransport}: a Thaumcraft {@link IEssentiaTransport} that is not an aspect container (essentia tubes, valves,
 * the Arcane Bore's base ...). 1.7.10 {@code PeripheralEssentiaTransport}. Every method but {@code getMinimumSuction} takes a
 * side, the number of a {@code ForgeDirection}: 0 down, 1 up, 2 north, 3 south, 4 west, 5 east (the 3D data value of a
 * 1.21.1 {@link Direction}, same order); a fraction is cut off as {@code Double.intValue()} did.
 *
 * <p>Answers: {@code isConnectable}, {@code canInputFrom}, {@code canOutputTo}: booleans; {@code getSuctionType},
 * {@code getEssentiaType}: the tag of the aspect; {@code getSuctionAmount}, {@code getEssentiaAmount},
 * {@code getMinimumSuction}: numbers.</p>
 *
 * <p>Two errors are worded anew (docs/deviations/ComputerCraft.md): a side outside 0..5 was {@code ForgeDirection.UNKNOWN},
 * which 1.21.1 does not have, and is the error {@code bad argument #1 (side 0 to 5 expected, got <n>)} now; and {@code getSuctionType} / {@code getEssentiaType} of a side
 * without one called {@code getTag()} on null, a {@code NullPointerException} the script saw as an error, which is an error
 * with a message now.</p>
 */
public final class EssentiaTransportTable implements PeripheralTable {
    public static final String TYPE = "tt_aspectTransport";
    public static final String[] METHOD_NAMES = { "isConnectable", "canInputFrom", "canOutputTo", "getSuctionType",
            "getSuctionAmount", "getEssentiaType", "getEssentiaAmount", "getMinimumSuction" };

    private final IEssentiaTransport pipe;

    public EssentiaTransportTable(IEssentiaTransport pipe) {
        this.pipe = pipe;
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
        return pipe;
    }

    /** 1.7.10 {@code GetDirection} and {@code ForgeDirection.getOrientation}. */
    static Direction direction(PeripheralArgs arguments, int index) {
        int side = (int) arguments.getDouble(index);
        if (side < 0 || side > 5) {
            throw new PeripheralError("bad argument #" + (index + 1) + " (side 0 to 5 expected, got " + side + ")");
        }
        return Direction.from3DDataValue(side);
    }

    @Override
    public Object[] call(int method, PeripheralArgs arguments) {
        return switch (method) {
            case 0 -> new Object[] { pipe.isConnectable(direction(arguments, 0)) };
            case 1 -> new Object[] { pipe.canInputFrom(direction(arguments, 0)) };
            case 2 -> new Object[] { pipe.canOutputTo(direction(arguments, 0)) };
            case 3 -> new Object[] { tag(pipe.getSuctionType(direction(arguments, 0)), "no suction type") };
            case 4 -> new Object[] { pipe.getSuctionAmount(direction(arguments, 0)) };
            case 5 -> new Object[] { tag(pipe.getEssentiaType(direction(arguments, 0)), "no essentia") };
            case 6 -> new Object[] { pipe.getEssentiaAmount(direction(arguments, 0)) };
            case 7 -> new Object[] { pipe.getMinimumSuction() };
            default -> new Object[0];
        };
    }

    private static String tag(Aspect aspect, String none) {
        if (aspect == null) throw new PeripheralError(none + " on that side");
        return aspect.getTag();
    }
}
