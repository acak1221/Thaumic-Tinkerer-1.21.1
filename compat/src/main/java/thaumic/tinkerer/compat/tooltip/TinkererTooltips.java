/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the list of the block tooltips.
 * Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.tooltip;

import java.util.List;

/**
 * The tooltips of the 1.7.10 Waila providers, one per block class: {@code TTinkererProvider} (Animation Tablet,
 * Transvector Interface, Thaumic Restorer, Celestial Gateway) and {@code MagnetProvider} (both attractors). A tooltip mod
 * plugin registers each of them; nothing here depends on one.
 */
public final class TinkererTooltips {
    private TinkererTooltips() {}

    public static final AnimationTabletTooltip ANIMATION_TABLET = new AnimationTabletTooltip();
    public static final TransvectorInterfaceTooltip INTERFACE = new TransvectorInterfaceTooltip();
    public static final RepairerTooltip REPAIRER = new RepairerTooltip();
    public static final WarpGateTooltip WARP_GATE = new WarpGateTooltip();
    public static final MagnetTooltip MAGNET = new MagnetTooltip();

    /** All of them, in the order of the 1.7.10 providers. */
    public static final List<BlockTooltip<?>> ALL = List.of(ANIMATION_TABLET, INTERFACE, REPAIRER, WARP_GATE, MAGNET);
}
