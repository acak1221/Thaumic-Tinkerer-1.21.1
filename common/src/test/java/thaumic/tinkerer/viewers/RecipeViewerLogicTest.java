/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only
 * code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.viewers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import thaumic.tinkerer.compat.viewer.DropChances;
import thaumic.tinkerer.compat.viewer.ViewerIds;
import thaumic.tinkerer.compat.viewer.ViewerLayout;

/**
 * <p>The plain logic of the recipe-viewer pages that needs no game: the chance arithmetic and its text, the split of a
 * long drop list into pages, the page ids and the geometry both viewers draw with. What the pages contain is checked in a
 * running server ({@code RecipeViewersRuntimeChecks}).</p>
 */
class RecipeViewerLogicTest {

    private static final double EPSILON = 1e-9;

    // ---- chances ---------------------------------------------------------------------------------------------------

    @Test
    void aChanceIsTheWeightOverTheTotalInPercent() {
        // the list of mind (Cognitio): paper 15, books 10, bookshelves 5, knowledge fragments 5
        List<Double> chances = DropChances.percents(List.of(15, 10, 5, 5));
        assertEquals(15 * 100.0 / 35, chances.get(0), EPSILON);
        assertEquals(10 * 100.0 / 35, chances.get(1), EPSILON);
        assertEquals(5 * 100.0 / 35, chances.get(2), EPSILON);
        assertEquals(chances.get(2), chances.get(3), EPSILON);
        assertEquals(100.0, chances.stream().mapToDouble(Double::doubleValue).sum(), EPSILON);
        assertEquals(List.of("42.86", "28.57", "14.29", "14.29"), chances.stream().map(DropChances::format).toList());
    }

    @Test
    void theChancesOfAnyListAddUpToAHundred() {
        List<Integer> weights = new ArrayList<>();
        for (int i = 1; i <= 48; i++) {
            weights.add(i % 7 + 1);
            double sum = DropChances.percents(weights).stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(100.0, sum, EPSILON, weights.toString());
        }
        // forty-eight entries of weight one (the wisp essences of auram): 2.08 each, as 1.7.10 showed
        List<Double> aura = DropChances.percents(Collections.nCopies(48, 1));
        assertEquals("2.08", DropChances.format(aura.getFirst()));
        assertEquals(100.0, aura.stream().mapToDouble(Double::doubleValue).sum(), EPSILON);
    }

    @Test
    void theOnlyEntryIsCertain() {
        assertEquals(100.0, DropChances.percent(100, 100), EPSILON);
        assertEquals("100", DropChances.format(DropChances.percent(1, 1)));
    }

    @Test
    void impossibleWeightsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> DropChances.percent(1, 0));
        assertThrows(IllegalArgumentException.class, () -> DropChances.percent(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> DropChances.percent(6, 5));
    }

    @Test
    void theNumberIsFormattedLikeTheNeiTooltip() {
        // DecimalFormat("#.##"): at most two decimals, no trailing zeros, half-even rounding
        assertEquals("62.5", DropChances.format(62.5));
        assertEquals("31.25", DropChances.format(31.25));
        assertEquals("6.25", DropChances.format(6.25));
        assertEquals("0.12", DropChances.format(0.125));
        assertEquals("0.38", DropChances.format(0.375));
        assertEquals("33.33", DropChances.format(100.0 / 3));
        assertEquals("66.67", DropChances.format(200.0 / 3));
        assertEquals("0", DropChances.format(0.001));
    }

    @Test
    void theDecimalPointDoesNotDependOnTheJavaLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("12.5", DropChances.format(12.5));
            Locale.setDefault(Locale.forLanguageTag("ru-RU"));
            assertEquals("14.29", DropChances.format(100.0 / 7));
        } finally {
            Locale.setDefault(before);
        }
    }

    // ---- pages -------------------------------------------------------------------------------------------------------

    @Test
    void aLongListGoesOnOverPagesInOrder() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 55; i++) items.add(i);
        List<List<Integer>> pages = DropChances.pages(items, ViewerLayout.DROPS_PER_PAGE);
        assertEquals(2, pages.size());
        assertEquals(54, pages.get(0).size());
        assertEquals(List.of(54), pages.get(1));
        List<Integer> joined = new ArrayList<>();
        pages.forEach(joined::addAll);
        assertEquals(items, joined);
        assertEquals(1, DropChances.pages(items.subList(0, 54), 54).size(), "exactly one page's worth is one page");
        assertEquals(List.of(List.of()), DropChances.pages(List.of(), 54), "an empty list is one empty page");
        assertThrows(IllegalArgumentException.class, () -> DropChances.pages(items, 0));
    }

    // ---- ids ---------------------------------------------------------------------------------------------------------

    @Test
    void theCategoryIdsAreTheNamesOfTheTask() {
        assertEquals("thaumictinkerer:necromancy", ViewerIds.NECROMANCY.toString());
        assertEquals("thaumictinkerer:infused_seed_drops", ViewerIds.INFUSED_SEED_DROPS.toString());
    }

    @Test
    void pageIdsAreSyntheticAndStable() {
        assertEquals("thaumictinkerer:/necromancy/minecraft/zombie", ViewerIds.necromancy(ResourceLocation.parse("minecraft:zombie")).toString());
        assertEquals("thaumictinkerer:/necromancy/thaumcraft/brainy_zombie",
                ViewerIds.necromancy(ResourceLocation.parse("thaumcraft:brainy_zombie")).toString());
        ResourceLocation aer = ViewerIds.seedDrops("aer");
        assertEquals("thaumictinkerer:/infused_seed_drops/aer", aer.toString());
        assertSame(aer, ViewerIds.page(aer, 0), "the first page keeps the id");
        assertEquals("thaumictinkerer:/infused_seed_drops/aer/2", ViewerIds.page(aer, 1).toString());
        assertEquals("thaumictinkerer:/infused_seed_drops/aer/3", ViewerIds.page(aer, 2).toString());
        assertThrows(IllegalArgumentException.class, () -> ViewerIds.page(aer, -1));
        // EMI's convention for recipes outside the recipe manager: the path starts with a slash
        assertTrue(aer.getPath().startsWith("/"));
    }

    @Test
    void anyAspectTagBecomesOneLegalPathSegment() {
        assertEquals("perditio", ViewerIds.segment("perditio"));
        assertEquals("some_aspect_x_", ViewerIds.segment("Some Aspect/x!"));
        assertEquals("_", ViewerIds.segment(""));
        // the result is a valid resource location (the parser would throw otherwise) without a second segment
        ResourceLocation odd = ViewerIds.seedDrops("Wéird/Tag");
        assertEquals("/infused_seed_drops/w_ird_tag", odd.getPath());
    }

    // ---- geometry ----------------------------------------------------------------------------------------------------

    private record Box(String name, int x, int y, int w, int h) {
        boolean overlaps(Box o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }
    }

    @Test
    void theSoulAspectsStandOnARingAroundTheTabletStartingAtTheTop() {
        assertArrayEquals(new int[] { 24, 0 }, ViewerLayout.essence(0));
        assertArrayEquals(new int[] { 45, 36 }, ViewerLayout.essence(1));
        assertArrayEquals(new int[] { 3, 36 }, ViewerLayout.essence(2));
        assertThrows(IllegalArgumentException.class, () -> ViewerLayout.essence(3));
    }

    @Test
    void nothingOnTheNecromancyPageOverlapsAndEverythingFits() {
        List<Box> boxes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int[] at = ViewerLayout.essence(i);
            boxes.add(new Box("soul aspect " + i, at[0], at[1], ViewerLayout.SLOT, ViewerLayout.SLOT));
        }
        boxes.add(new Box("tablet", ViewerLayout.TABLET_X, ViewerLayout.TABLET_Y, ViewerLayout.SLOT, ViewerLayout.SLOT));
        boxes.add(new Box("arrow", ViewerLayout.NECROMANCY_ARROW_X, ViewerLayout.NECROMANCY_ARROW_Y, ViewerLayout.ARROW_WIDTH, ViewerLayout.ARROW_HEIGHT));
        boxes.add(new Box("mob", ViewerLayout.MOB_X, ViewerLayout.MOB_Y, ViewerLayout.LARGE_SLOT, ViewerLayout.LARGE_SLOT));
        boxes.add(new Box("text", 0, ViewerLayout.NECROMANCY_TEXT_Y, ViewerLayout.NECROMANCY_WIDTH, ViewerLayout.TEXT_HEIGHT));
        for (int i = 0; i < boxes.size(); i++) {
            Box a = boxes.get(i);
            assertTrue(a.x >= 0 && a.y >= 0 && a.x + a.w <= ViewerLayout.NECROMANCY_WIDTH && a.y + a.h <= ViewerLayout.NECROMANCY_HEIGHT,
                    a + " inside " + ViewerLayout.NECROMANCY_WIDTH + "x" + ViewerLayout.NECROMANCY_HEIGHT);
            for (int j = i + 1; j < boxes.size(); j++) assertTrue(!a.overlaps(boxes.get(j)), a + " overlaps " + boxes.get(j));
        }
    }

    @Test
    void theDropsStandInRowsOfNineUnderTheSeed() {
        assertArrayEquals(new int[] { 0, ViewerLayout.DROPS_Y }, ViewerLayout.drop(0));
        assertArrayEquals(new int[] { 8 * 18, ViewerLayout.DROPS_Y }, ViewerLayout.drop(8));
        assertArrayEquals(new int[] { 0, ViewerLayout.DROPS_Y + 18 }, ViewerLayout.drop(9));
        assertEquals(1, ViewerLayout.rows(0));
        assertEquals(1, ViewerLayout.rows(9));
        assertEquals(2, ViewerLayout.rows(10));
        assertEquals(ViewerLayout.DROPS_Y + 6 * 18, ViewerLayout.seedHeight(ViewerLayout.DROPS_PER_PAGE));
        assertTrue(ViewerLayout.DROPS_Y >= ViewerLayout.SEED_Y + ViewerLayout.SLOT, "the drops start under the seed");
        assertTrue(ViewerLayout.SEED_TEXT_X >= ViewerLayout.SEED_X + ViewerLayout.SLOT, "the header starts right of the seed");
        assertEquals(ViewerLayout.COLUMNS * ViewerLayout.SLOT, ViewerLayout.SEED_WIDTH);
    }
}
