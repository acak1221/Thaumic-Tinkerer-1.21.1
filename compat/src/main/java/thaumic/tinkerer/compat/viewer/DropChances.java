/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: written for EMI and JEI after the arithmetic of the 1.7.10 NEI handler
 * of the infused seeds, not from its code). Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.viewer;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The plain arithmetic of the seed pages: the chance of a drop, its text, and the split of a long drop list into pages.
 *
 * <p>A ripe infused crop draws one stack from the weighted list of its aspect ({@code AspectCropLootManager.getLootForAspect}):
 * a stack of weight {@code w} in a list of total weight {@code W} comes with the chance {@code w / W}. The 1.7.10 NEI handler
 * showed it as {@code weight / totalWeight * 100} percent, formatted with {@code DecimalFormat("#.##")}; so does this class.</p>
 */
public final class DropChances {
    /** At most two decimals, no trailing zeros ({@code 12.5}, {@code 100}, {@code 2.08}), as the 1.7.10 tooltip. */
    private static final String PATTERN = "#.##";

    private DropChances() {}

    /** The chance in percent of one entry of weight {@code weight} in a list of total weight {@code totalWeight}. */
    public static double percent(int weight, int totalWeight) {
        if (weight < 0 || totalWeight <= 0 || weight > totalWeight) {
            throw new IllegalArgumentException("weight " + weight + " of " + totalWeight);
        }
        return weight * 100.0 / totalWeight;
    }

    /** The chances in percent of every entry of a weighted list, in its order; they add up to 100 up to rounding. */
    public static List<Double> percents(List<Integer> weights) {
        int total = 0;
        for (int weight : weights) total += weight;
        List<Double> chances = new ArrayList<>(weights.size());
        for (int weight : weights) chances.add(percent(weight, total));
        return chances;
    }

    /**
     * The number of a chance as the tooltip shows it: {@code #.##} with the default rounding of {@link DecimalFormat}
     * (half-even), as 1.7.10 did. The decimal separator is always the point: 1.7.10 used the locale of the Java runtime,
     * which is not the language the game is set to; the language decides only the words around the number.
     */
    public static String format(double percent) {
        DecimalFormat format = new DecimalFormat(PATTERN, DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        return format.format(percent);
    }

    /**
     * Splits a list into pages of at most {@code perPage} entries, in order. An empty list is one empty page, so that a
     * caller always has a first page.
     */
    public static <T> List<List<T>> pages(List<T> items, int perPage) {
        if (perPage <= 0) throw new IllegalArgumentException("perPage " + perPage);
        List<List<T>> pages = new ArrayList<>();
        for (int from = 0; from < items.size(); from += perPage) {
            pages.add(List.copyOf(items.subList(from, Math.min(items.size(), from + perPage))));
        }
        if (pages.isEmpty()) pages.add(List.of());
        return pages;
    }
}
