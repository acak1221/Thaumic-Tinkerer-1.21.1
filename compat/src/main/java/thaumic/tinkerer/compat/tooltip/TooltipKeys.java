/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the language keys of the block tooltips.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.tooltip;

import java.util.List;

/** The keys of the 1.7.10 Waila lines ({@code ttwaila.*}, in the converted language files) and the one other key used. */
public final class TooltipKeys {
    private TooltipKeys() {}

    public static final String NOTHING = "ttwaila.nothing";

    // Animation Tablet
    public static final String CURRENT_TOOL = "ttwaila.currentTool";
    public static final String LEFT_CLICK = "ttwaila.leftClick";
    public static final String RIGHT_CLICK = "ttwaila.rightClick";
    public static final String REDSTONE = "ttwaila.redstone";
    public static final String AUTONOMOUS = "ttwaila.autonomous";

    // Transvector Interface
    public static final String CONNECTED = "ttwaila.connected";

    // Thaumic Restorer
    public static final String REPAIRING = "ttwaila.repairing";
    public static final String FINISHED_REPAIRING = "ttwaila.finishedRepairing";

    // Celestial Gateway
    public static final String ALLOW_INCOMING = "ttwaila.allowIncoming";
    public static final String DISALLOW_INCOMING = "ttwaila.disallowIncoming";

    // Kinetic and Corporeal Attractor
    public static final String PULLING_MODE = "ttwaila.pullingMode";
    public static final String PUSHING_MODE = "ttwaila.pushingMode";
    public static final String PULLING_ADULT = "ttwaila.pullingAdult";
    public static final String PULLING_CHILD = "ttwaila.pullingChild";
    public static final String PUSHING_ADULT = "ttwaila.pushingAdult";
    public static final String PUSHING_CHILD = "ttwaila.pushingChild";
    public static final String PULLING_ADULT_TYPE = "ttwaila.pullingAdultType";
    public static final String PULLING_CHILD_TYPE = "ttwaila.pullingChildType";
    public static final String PUSHING_ADULT_TYPE = "ttwaila.pushingAdultType";
    public static final String PUSHING_CHILD_TYPE = "ttwaila.pushingChildType";
    /** The soul mould's own "No Pattern": the type of a mould that was never set (1.7.10 showed the raw key {@code entity..name}). */
    public static final String NO_PATTERN = "ttmisc.soulMould.nonAssigned";

    /** Every key above, with the number of {@code %s} its text takes. */
    public static final List<KeyArgs> ALL = List.of(
            new KeyArgs(NOTHING, 0),
            new KeyArgs(CURRENT_TOOL, 1), new KeyArgs(LEFT_CLICK, 0), new KeyArgs(RIGHT_CLICK, 0),
            new KeyArgs(REDSTONE, 0), new KeyArgs(AUTONOMOUS, 0),
            new KeyArgs(CONNECTED, 1),
            new KeyArgs(REPAIRING, 1), new KeyArgs(FINISHED_REPAIRING, 1),
            new KeyArgs(ALLOW_INCOMING, 0), new KeyArgs(DISALLOW_INCOMING, 0),
            new KeyArgs(PULLING_MODE, 0), new KeyArgs(PUSHING_MODE, 0),
            new KeyArgs(PULLING_ADULT, 0), new KeyArgs(PULLING_CHILD, 0),
            new KeyArgs(PUSHING_ADULT, 0), new KeyArgs(PUSHING_CHILD, 0),
            new KeyArgs(PULLING_ADULT_TYPE, 1), new KeyArgs(PULLING_CHILD_TYPE, 1),
            new KeyArgs(PUSHING_ADULT_TYPE, 1), new KeyArgs(PUSHING_CHILD_TYPE, 1),
            new KeyArgs(NO_PATTERN, 0));

    /** A language key and the number of arguments the lines pass to it. */
    public record KeyArgs(String key, int args) {}
}
