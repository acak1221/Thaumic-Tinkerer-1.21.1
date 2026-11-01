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
package thaumic.tinkerer.compat.computer;

import java.util.Map;
import thaumic.tinkerer.common.block.tile.TileGolemConnector;
import thaumic.tinkerer.common.core.golems.GolemConnectorMethods;

/**
 * {@code tt_golemconnector}: the Golem Connector, the {@code IPeripheral} part of 1.7.10 {@code TileGolemConnector}. The fifteen
 * methods, their answers for a connector without a golem and their error messages are {@link GolemConnectorMethods}; each call
 * reads the golem the connector has found at that moment ({@link TileGolemConnector#peripheral()}).
 *
 * <p>The argument counts are checked first, with the original messages ({@code Invalid arguments},
 * {@code setMarkers takes 1 argument} ...). Then, where 1.7.10 cast and so failed with a Java exception, a wrong value is a
 * CC: Tweaked "bad argument" or "bad field" error, at the same moments: the arguments themselves always ({@code callMethod}
 * cast them: numbers for the coordinates, the facing and the marker index, tables for the markers), the fields of a marker
 * only when there is a golem (only then did the original read them, {@code (Double) markerMap.get("posX")}): numbers for
 * {@code posX}, {@code posY}, {@code posZ}, {@code side} and {@code color} ({@code dim} may be anything; it is kept as its
 * text). Without a golem a marker is not read, and the call answers nothing, as in 1.7.10.</p>
 */
public final class GolemConnectorTable implements PeripheralTable {
    private static final String[] MARKER_NUMBERS = { "posX", "posY", "posZ", "side", "color" };

    private final TileGolemConnector connector;

    public GolemConnectorTable(TileGolemConnector connector) {
        this.connector = connector;
    }

    @Override
    public String type() {
        return GolemConnectorMethods.TYPE;
    }

    @Override
    public String[] methodNames() {
        return GolemConnectorMethods.METHOD_NAMES.clone();
    }

    @Override
    public Object target() {
        return connector;
    }

    @Override
    public Object[] call(int method, PeripheralArgs arguments) {
        if (method < 0 || method >= GolemConnectorMethods.METHOD_NAMES.length) return null;
        String name = GolemConnectorMethods.METHOD_NAMES[method];
        GolemConnectorMethods methods = connector.peripheral();
        checkTypes(name, arguments, connector.golemAccess().exists());
        try {
            return methods.call(name, arguments.values());
        } catch (GolemConnectorMethods.MethodException error) {
            throw new PeripheralError(error.getMessage());
        }
    }

    /**
     * The types the original cast to, checked only where the argument count is the one it accepts; the fields of the markers
     * only when {@code golem} is there.
     */
    static void checkTypes(String method, PeripheralArgs arguments, boolean golem) {
        switch (method) {
            case "setHome" -> {
                if (arguments.count() == 4) for (int i = 0; i < 4; i++) arguments.getDouble(i);
            }
            case "setMarkers" -> {
                if (arguments.count() == 1) {
                    Map<?, ?> markers = arguments.getTable(0);
                    if (golem) {
                        for (Object marker : markers.values()) {
                            if (!(marker instanceof Map<?, ?> map)) {
                                throw new PeripheralError("bad argument #1 (table of markers expected, got a " + PeripheralArgs.typeOf(marker) + " in it)");
                            }
                            checkMarker(map);
                        }
                    }
                }
            }
            case "addMarker" -> {
                if (arguments.count() == 1) {
                    Map<?, ?> marker = arguments.getTable(0);
                    if (golem) checkMarker(marker);
                }
            }
            case "saveMarker" -> {
                if (arguments.count() == 2) {
                    arguments.getDouble(0);
                    Map<?, ?> marker = arguments.getTable(1);
                    if (golem) checkMarker(marker);
                }
            }
            case "deleteMarker", "getMarker" -> {
                if (arguments.count() == 1) arguments.getDouble(0);
            }
            default -> {
            }
        }
    }

    private static void checkMarker(Map<?, ?> marker) {
        for (String field : MARKER_NUMBERS) {
            Object value = marker.get(field);
            if (!(value instanceof Number)) {
                throw new PeripheralError("bad field '" + field + "' (number expected, got " + PeripheralArgs.typeOf(value) + ")");
            }
        }
    }
}
