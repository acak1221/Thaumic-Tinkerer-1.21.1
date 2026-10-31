/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart). Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.compat.viewer;

/**
 * The geometry both viewers draw the pages with, in page pixels. A slot is the 18 x 18 frame (EMI draws the frame at the
 * given corner; JEI places the 16 x 16 item one pixel inside it); the output slot is the large 26 x 26 frame.
 *
 * <p><b>Necromancy</b>: the three soul aspects stand on a ring around the tablet, the first straight above it and the
 * others 120 degrees further clockwise, as Thaumcraft's infusion pages place the components around the matrix (the 1.7.10
 * page was an infusion page of Aspect Recipe Index); then an arrow and the mob. Under the ring one line of text.</p>
 *
 * <p><b>Seed drops</b>: the seed in the top left corner, a line of text next to it, and the drops below in rows of nine
 * (the 1.7.10 page: nine slots to a row).</p>
 */
public final class ViewerLayout {
    public static final int SLOT = 18;
    public static final int LARGE_SLOT = 26;
    public static final int ARROW_WIDTH = 24;
    public static final int ARROW_HEIGHT = 17;
    public static final int TEXT_HEIGHT = 10;

    // ---- necromancy ------------------------------------------------------------------------------------------------
    public static final int RING_RADIUS = 24;
    public static final int RING_CENTER = RING_RADIUS + SLOT / 2;
    /** The tablet in the middle of the ring. */
    public static final int TABLET_X = RING_CENTER - SLOT / 2;
    public static final int TABLET_Y = RING_CENTER - SLOT / 2;
    public static final int NECROMANCY_ARROW_X = 2 * RING_CENTER + 2;
    public static final int NECROMANCY_ARROW_Y = RING_CENTER - ARROW_HEIGHT / 2 - 6;
    public static final int MOB_X = NECROMANCY_ARROW_X + ARROW_WIDTH + 4;
    public static final int MOB_Y = NECROMANCY_ARROW_Y + ARROW_HEIGHT / 2 - LARGE_SLOT / 2;
    /** The lower edge of the two lower soul aspects (sin 30 degrees = 1/2). */
    public static final int RING_BOTTOM = RING_CENTER + RING_RADIUS / 2 + SLOT / 2;
    public static final int NECROMANCY_TEXT_Y = RING_BOTTOM + 4;
    public static final int NECROMANCY_WIDTH = 140;
    public static final int NECROMANCY_HEIGHT = NECROMANCY_TEXT_Y + TEXT_HEIGHT;

    // ---- seed drops ------------------------------------------------------------------------------------------------
    public static final int COLUMNS = 9;
    /** The rows of one EMI page; a longer list goes on to a second page. JEI shows the same number of rows and scrolls. */
    public static final int ROWS_PER_PAGE = 6;
    public static final int DROPS_PER_PAGE = COLUMNS * ROWS_PER_PAGE;
    /** JEI's page has a fixed height for every recipe of a category: this many rows are visible, the rest scrolls. */
    public static final int JEI_VISIBLE_ROWS = 4;
    public static final int SEED_X = 0;
    public static final int SEED_Y = 0;
    public static final int SEED_TEXT_X = SLOT + 4;
    public static final int SEED_TEXT_Y = (SLOT - 8) / 2;
    public static final int DROPS_Y = SLOT + 4;
    public static final int SEED_WIDTH = COLUMNS * SLOT;

    private ViewerLayout() {}

    /** The top left corner of the frame of soul aspect {@code index} (0, 1, 2) on the ring: {x, y}. */
    public static int[] essence(int index) {
        if (index < 0 || index > 2) throw new IllegalArgumentException("index " + index);
        double angle = Math.toRadians(-90 + index * 120);
        int x = RING_CENTER + (int) Math.round(Math.cos(angle) * RING_RADIUS) - SLOT / 2;
        int y = RING_CENTER + (int) Math.round(Math.sin(angle) * RING_RADIUS) - SLOT / 2;
        return new int[] { x, y };
    }

    /** The rows a list of {@code drops} needs, at least one. */
    public static int rows(int drops) {
        return Math.max(1, (drops + COLUMNS - 1) / COLUMNS);
    }

    /** The top left corner of the frame of drop {@code index} of a page: {x, y}. */
    public static int[] drop(int index) {
        return new int[] { index % COLUMNS * SLOT, DROPS_Y + index / COLUMNS * SLOT };
    }

    /** The height of an EMI seed page that shows {@code drops} drops. */
    public static int seedHeight(int drops) {
        return DROPS_Y + rows(drops) * SLOT;
    }
}
