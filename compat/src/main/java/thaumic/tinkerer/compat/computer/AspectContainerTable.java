/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [13 Sep 2013, 00:58:21 (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import java.util.LinkedHashMap;
import java.util.Map;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;

/**
 * {@code tt_aspectContainer}: every block entity that is a Thaumcraft {@link IAspectContainer} (jars, nodes, alembics, the
 * crucible, the Essentia Funnel, the Thaumic Restorer, the Transvector Interface ...). 1.7.10 {@code PeripheralAspectContainer}
 * with the two calls of {@code IAspectContainerImplementation} (by Katrina, 27/03/14).
 *
 * <ul>
 *   <li>{@code getAspects()}: a table of the tags of the aspects in the container, sorted as {@link AspectList#getAspectsSorted}
 *       sorts them (by tag), at the keys 1, 2, 3 ...; an empty table when the container holds nothing.</li>
 *   <li>{@code getAspectCount(tag)}: the amount of the aspect with that tag; 0 for an aspect the container does not hold and
 *       for a tag that names no aspect.</li>
 * </ul>
 *
 * <p>Not ported: 1.7.10 answered {@code getAspects()} of a labelled jar ({@code TileJarFillable} with an {@code aspectFilter})
 * with the label's aspect alone, even when the jar was empty. {@code thaumcraft.api} has no way to read a jar's label, so a
 * labelled empty jar answers an empty table (docs/deviations/ComputerCraft.md).</p>
 */
public final class AspectContainerTable implements PeripheralTable {
    public static final String TYPE = "tt_aspectContainer";
    public static final String[] METHOD_NAMES = { "getAspects", "getAspectCount" };

    private final IAspectContainer container;

    public AspectContainerTable(IAspectContainer container) {
        this.container = container;
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
        return container;
    }

    @Override
    public Object[] call(int method, PeripheralArgs arguments) {
        return switch (method) {
            case 0 -> getAspects(container);
            case 1 -> getAspectCount(container, arguments.getString(0));
            default -> null;
        };
    }

    /** {@code IAspectContainerImplementation.getAspects}. */
    public static Object[] getAspects(IAspectContainer container) {
        Map<Double, String> returnStuff = new LinkedHashMap<>();
        double i = 1;
        AspectList aspects = container.getAspects();
        if (aspects == null || aspects.size() == 0) return new Object[] { returnStuff };
        for (Aspect aspect : aspects.getAspectsSorted()) returnStuff.put(i++, aspect.getTag());
        return new Object[] { returnStuff };
    }

    /** {@code IAspectContainerImplementation.getAspectCount}. */
    public static Object[] getAspectCount(IAspectContainer container, String aspectName) {
        Aspect aspect = Aspect.getAspect(aspectName);
        AspectList aspects = container.getAspects();
        if (aspects == null) return new Object[] { 0 };
        return new Object[] { aspects.getAmount(aspect) };
    }
}
