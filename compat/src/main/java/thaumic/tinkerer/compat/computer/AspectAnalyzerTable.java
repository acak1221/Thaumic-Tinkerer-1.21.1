/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [Dec 11, 2013, 10:33:26 PM (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.compat.computer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import thaumic.tinkerer.common.block.tile.TileAspectAnalyzer;

/**
 * {@code tt_aspectanalyzer}: the Aspectalyzer, the {@code IPeripheral} part of 1.7.10 {@code TileAspectAnalyzer}. The calls
 * are the block entity's ({@code hasItemMethod}, {@code itemHasAspectsMethod}, {@code getAspectsMethod},
 * {@code getAspectsAmountsMethod}).
 *
 * <ul>
 *   <li>{@code hasItem()}: whether the slot holds an item. {@code itemHasAspects()}: whether that item has aspects.</li>
 *   <li>{@code getAspects()}: the tags of the item's aspects, sorted by tag, at the keys 1, 2, 3 ...</li>
 *   <li>{@code getAspectCount()}: no argument; a table from each tag to its amount.</li>
 * </ul>
 */
public final class AspectAnalyzerTable implements PeripheralTable {
    public static final String TYPE = "tt_aspectanalyzer";
    public static final String[] METHOD_NAMES = { "hasItem", "itemHasAspects", "getAspects", "getAspectCount" };

    private final TileAspectAnalyzer analyzer;

    public AspectAnalyzerTable(TileAspectAnalyzer analyzer) {
        this.analyzer = analyzer;
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
        return analyzer;
    }

    @Override
    public Object[] call(int method, PeripheralArgs arguments) {
        return switch (method) {
            case 0 -> new Object[] { analyzer.hasItemMethod() };
            case 1 -> new Object[] { analyzer.itemHasAspectsMethod() };
            case 2 -> new Object[] { numbered(analyzer.getAspectsMethod()) };
            case 3 -> new Object[] { amounts(analyzer.getAspectsAmountsMethod()) };
            default -> null;
        };
    }

    /** 1.7.10 {@code getAspectsMethod}: a {@code HashMap<Double, String>} from 1. */
    static Map<Double, String> numbered(List<String> tags) {
        Map<Double, String> retVals = new LinkedHashMap<>();
        double i = 1;
        for (String tag : tags) retVals.put(i++, tag);
        return retVals;
    }

    /** 1.7.10 {@code getAspectsAmountsMethod}: a {@code HashMap<String, Double>}. */
    static Map<String, Double> amounts(Map<String, Integer> amounts) {
        Map<String, Double> retVals = new LinkedHashMap<>();
        amounts.forEach((tag, amount) -> retVals.put(tag, (double) amount));
        return retVals;
    }
}
