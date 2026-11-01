/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only
 * code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.computer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumic.tinkerer.common.core.golems.GolemConnectorMethods;
import thaumic.tinkerer.compat.computer.AnimationTabletTable;
import thaumic.tinkerer.compat.computer.AspectAnalyzerTable;
import thaumic.tinkerer.compat.computer.AspectContainerTable;
import thaumic.tinkerer.compat.computer.EssentiaTransportTable;
import thaumic.tinkerer.compat.computer.MagnetTable;
import thaumic.tinkerer.compat.computer.PeripheralArgs;
import thaumic.tinkerer.compat.computer.PeripheralError;
import thaumic.tinkerer.compat.computer.PeripheralTable;
import thaumic.tinkerer.compat.computer.TinkererPeripherals;

/**
 * The computer peripherals without a computer mod (docs/deviations/ComputerCraft.md): the type and method names against the
 * 1.7.10 sources, the answers of the two generic peripherals for fake Thaumcraft blocks, the order in which a block entity is
 * matched (1.7.10 {@code PeripheralHandler}), and the argument errors. The add-on's own block entities and CC: Tweaked's dispatch
 * are checked by the GameTests ({@code ComputerCraftRuntimeChecks}).
 */
class PeripheralTablesTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ---- the names of 1.7.10 ---------------------------------------------------------------------------------------------

    @Test
    void theTypesAndMethodNamesAreThoseOf1710() {
        // PeripheralAspectContainer, PeripheralEssentiaTransport, TileAnimationTablet, TileMagnet, TileMobMagnet,
        // TileAspectAnalyzer, TileGolemConnector: getType() and getMethodNames()
        assertEquals("tt_aspectContainer", AspectContainerTable.TYPE);
        assertArrayEquals(new String[] { "getAspects", "getAspectCount" }, AspectContainerTable.METHOD_NAMES);
        assertEquals("tt_aspectTransport", EssentiaTransportTable.TYPE);
        assertArrayEquals(new String[] { "isConnectable", "canInputFrom", "canOutputTo", "getSuctionType", "getSuctionAmount",
                "getEssentiaType", "getEssentiaAmount", "getMinimumSuction" }, EssentiaTransportTable.METHOD_NAMES);
        assertEquals("tt_animationTablet", AnimationTabletTable.TYPE);
        assertArrayEquals(new String[] { "getRedstone", "setRedstone", "getLeftClick", "setLeftClick", "getRotation", "setRotation",
                "hasItem", "trigger" }, AnimationTabletTable.METHOD_NAMES);
        assertEquals("tt_magnet", MagnetTable.TYPE);
        assertArrayEquals(new String[] { "isPulling", "setPulling", "getSignal" }, MagnetTable.KINETIC_METHOD_NAMES);
        assertArrayEquals(new String[] { "isPulling", "setPulling", "getSignal", "getAdultSearch", "setAdultSearch" },
                MagnetTable.CORPOREAL_METHOD_NAMES);
        assertEquals("tt_aspectanalyzer", AspectAnalyzerTable.TYPE);
        assertArrayEquals(new String[] { "hasItem", "itemHasAspects", "getAspects", "getAspectCount" }, AspectAnalyzerTable.METHOD_NAMES);
        assertEquals("tt_golemconnector", GolemConnectorMethods.TYPE);
        assertArrayEquals(new String[] { "getDecorations", "getPosition", "getType", "getHealth", "getCore", "getHome", "setHome",
                "getMarkers", "setMarkers", "newMarker", "addMarker", "saveMarker", "deleteMarker", "getMarker", "getMarkerCount" },
                GolemConnectorMethods.METHOD_NAMES);
    }

    @Test
    void theNameListsCannotBeChangedFromOutside() {
        PeripheralTable table = new AspectContainerTable(new FakeContainer(new AspectList()));
        table.methodNames()[0] = "changed";
        assertEquals("getAspects", table.methodNames()[0]);
    }

    // ---- tt_aspectContainer ------------------------------------------------------------------------------------------------

    @Test
    void getAspectsAnswersTheSortedTagsFromOne() {
        AspectList aspects = new AspectList().add(Aspect.WATER, 3).add(Aspect.AIR, 2).add(Aspect.FIRE, 7);
        Object[] answer = new AspectContainerTable(new FakeContainer(aspects)).call(0, PeripheralArgs.of());
        Map<Double, String> expected = new LinkedHashMap<>();
        expected.put(1.0, "aer");
        expected.put(2.0, "aqua");
        expected.put(3.0, "ignis");
        assertEquals(1, answer.length);
        assertEquals(expected, answer[0]);
    }

    @Test
    void anEmptyOrUnreadableContainerAnswersAnEmptyTable() {
        assertEquals(Map.of(), new AspectContainerTable(new FakeContainer(new AspectList())).call(0, PeripheralArgs.of())[0]);
        assertEquals(Map.of(), new AspectContainerTable(new FakeContainer(null)).call(0, PeripheralArgs.of())[0]);
    }

    @Test
    void getAspectCountAnswersTheAmountOrZero() {
        AspectContainerTable table = new AspectContainerTable(new FakeContainer(new AspectList().add(Aspect.FIRE, 7)));
        assertArrayEquals(new Object[] { 7 }, table.call(1, PeripheralArgs.of("ignis")));
        assertArrayEquals(new Object[] { 0 }, table.call(1, PeripheralArgs.of("aqua")));
        assertArrayEquals(new Object[] { 0 }, table.call(1, PeripheralArgs.of("no_such_aspect")), "an unknown tag is no aspect");
        assertArrayEquals(new Object[] { 0 }, new AspectContainerTable(new FakeContainer(null)).call(1, PeripheralArgs.of("ignis")));
        PeripheralError error = assertThrows(PeripheralError.class, () -> table.call(1, PeripheralArgs.of(3.0)));
        assertEquals("bad argument #1 (string expected, got number)", error.getMessage());
        error = assertThrows(PeripheralError.class, () -> table.call(1, PeripheralArgs.of()));
        assertEquals("bad argument #1 (string expected, got nil)", error.getMessage());
    }

    // ---- tt_aspectTransport --------------------------------------------------------------------------------------------------

    @Test
    void theSidesAreTheForgeDirectionNumbers() {
        FakeTransport pipe = new FakeTransport();
        EssentiaTransportTable table = new EssentiaTransportTable(pipe);
        for (int side = 0; side < 6; side++) table.call(0, PeripheralArgs.of((double) side));
        assertEquals(List.of(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST), pipe.asked);
        pipe.asked.clear();
        table.call(1, PeripheralArgs.of(2.7));
        table.call(2, PeripheralArgs.of(5.0));
        assertEquals(List.of(Direction.NORTH, Direction.EAST), pipe.asked, "a fraction is cut off (Double.intValue)");
    }

    @Test
    void everyMethodAnswersWhatTheTransportSays() {
        FakeTransport pipe = new FakeTransport();
        EssentiaTransportTable table = new EssentiaTransportTable(pipe);
        PeripheralArgs up = PeripheralArgs.of(1.0);
        assertArrayEquals(new Object[] { true }, table.call(0, up));
        assertArrayEquals(new Object[] { false }, table.call(1, up));
        assertArrayEquals(new Object[] { true }, table.call(2, up));
        assertArrayEquals(new Object[] { "perditio" }, table.call(3, up));
        assertArrayEquals(new Object[] { 64 }, table.call(4, up));
        assertArrayEquals(new Object[] { "aqua" }, table.call(5, up));
        assertArrayEquals(new Object[] { 3 }, table.call(6, up));
        assertArrayEquals(new Object[] { 16 }, table.call(7, PeripheralArgs.of()));
    }

    @Test
    void aSideWithoutSuctionOrEssentiaIsAnError() {
        FakeTransport pipe = new FakeTransport();
        pipe.suction = null;
        pipe.essentia = null;
        EssentiaTransportTable table = new EssentiaTransportTable(pipe);
        assertEquals("no suction type on that side", assertThrows(PeripheralError.class, () -> table.call(3, PeripheralArgs.of(0.0))).getMessage());
        assertEquals("no essentia on that side", assertThrows(PeripheralError.class, () -> table.call(5, PeripheralArgs.of(0.0))).getMessage());
    }

    @Test
    void aSideOutsideZeroToFiveOrNotANumberIsABadArgument() {
        EssentiaTransportTable table = new EssentiaTransportTable(new FakeTransport());
        assertEquals("bad argument #1 (side 0 to 5 expected, got 6)",
                assertThrows(PeripheralError.class, () -> table.call(0, PeripheralArgs.of(6.0))).getMessage());
        assertEquals("bad argument #1 (side 0 to 5 expected, got -1)",
                assertThrows(PeripheralError.class, () -> table.call(0, PeripheralArgs.of(-1.0))).getMessage());
        assertEquals("bad argument #1 (number expected, got string)",
                assertThrows(PeripheralError.class, () -> table.call(4, PeripheralArgs.of("up"))).getMessage());
    }

    // ---- PeripheralHandler: which peripheral ---------------------------------------------------------------------------------

    @Test
    void anAspectContainerComesBeforeAnEssentiaTransport() {
        // the order of 1.7.10 PeripheralHandler.getPeripheral: a jar (both) is a tt_aspectContainer
        assertInstanceOf(AspectContainerTable.class, TinkererPeripherals.find(new FakeContainerAndTransport()));
        assertInstanceOf(EssentiaTransportTable.class, TinkererPeripherals.find(new FakeTransport()));
        assertInstanceOf(AspectContainerTable.class, TinkererPeripherals.find(new FakeContainer(new AspectList())));
        assertNull(TinkererPeripherals.find(new Object()));
        assertNull(TinkererPeripherals.find(null));
    }

    @Test
    void theTargetIsTheBlockEntity() {
        FakeTransport pipe = new FakeTransport();
        assertEquals(pipe, TinkererPeripherals.find(pipe).target());
    }

    // ---- the arguments ---------------------------------------------------------------------------------------------------------

    @Test
    void theArgumentErrorsAreThoseOfCcTweaked() {
        PeripheralArgs args = new PeripheralArgs(new Object[] { null, "x", 2.0, true, Map.of() },
                new String[] { "function", "string", "number", "boolean", "table" });
        assertEquals("bad argument #1 (number expected, got function)", assertThrows(PeripheralError.class, () -> args.getDouble(0)).getMessage());
        assertEquals("bad argument #2 (boolean expected, got string)", assertThrows(PeripheralError.class, () -> args.getBoolean(1)).getMessage());
        assertEquals("bad argument #3 (string expected, got number)", assertThrows(PeripheralError.class, () -> args.getString(2)).getMessage());
        assertEquals("bad argument #4 (table expected, got boolean)", assertThrows(PeripheralError.class, () -> args.getTable(3)).getMessage());
        assertEquals("bad argument #6 (number expected, got nil)", assertThrows(PeripheralError.class, () -> args.getDouble(5)).getMessage());
        assertEquals(2.0, args.getDouble(2));
        assertEquals(Map.of(), args.getTable(4));
        assertEquals(5, args.count());
        assertEquals(List.of("nil", "string", "number", "boolean", "table"),
                java.util.Arrays.asList(PeripheralArgs.of(null, "a", 1, false, Map.of()).types()));
    }

    // ---- fakes ---------------------------------------------------------------------------------------------------------------------

    private static class FakeContainer implements IAspectContainer {
        private final AspectList aspects;

        FakeContainer(AspectList aspects) {
            this.aspects = aspects;
        }

        @Override public AspectList getAspects() { return aspects; }
        @Override public void setAspects(AspectList aspects) {}
        @Override public boolean doesContainerAccept(Aspect aspect) { return false; }
        @Override public int addToContainer(Aspect aspect, int amount) { return amount; }
        @Override public boolean takeFromContainer(Aspect aspect, int amount) { return false; }
        @Override @Deprecated public boolean takeFromContainer(AspectList aspects) { return false; }
        @Override public boolean doesContainerContainAmount(Aspect aspect, int amount) { return false; }
        @Override @Deprecated public boolean doesContainerContain(AspectList aspects) { return false; }
        @Override public int containerContains(Aspect aspect) { return 0; }
    }

    private static class FakeTransport implements IEssentiaTransport {
        final List<Direction> asked = new ArrayList<>();
        Aspect suction = Aspect.ENTROPY;
        Aspect essentia = Aspect.WATER;

        @Override public boolean isConnectable(Direction face) { asked.add(face); return true; }
        @Override public boolean canInputFrom(Direction face) { asked.add(face); return false; }
        @Override public boolean canOutputTo(Direction face) { asked.add(face); return true; }
        @Override public void setSuction(Aspect aspect, int amount) {}
        @Override public Aspect getSuctionType(Direction face) { return suction; }
        @Override public int getSuctionAmount(Direction face) { return 64; }
        @Override public int takeEssentia(Aspect aspect, int amount, Direction face) { return 0; }
        @Override public int addEssentia(Aspect aspect, int amount, Direction face) { return 0; }
        @Override public Aspect getEssentiaType(Direction face) { return essentia; }
        @Override public int getEssentiaAmount(Direction face) { return 3; }
        @Override public int getMinimumSuction() { return 16; }
        @Override public boolean renderExtendedTube() { return false; }
    }

    private static final class FakeContainerAndTransport extends FakeTransport implements IAspectContainer {
        private final FakeContainer container = new FakeContainer(new AspectList());

        @Override public AspectList getAspects() { return container.getAspects(); }
        @Override public void setAspects(AspectList aspects) {}
        @Override public boolean doesContainerAccept(Aspect aspect) { return false; }
        @Override public int addToContainer(Aspect aspect, int amount) { return amount; }
        @Override public boolean takeFromContainer(Aspect aspect, int amount) { return false; }
        @Override @Deprecated public boolean takeFromContainer(AspectList aspects) { return false; }
        @Override public boolean doesContainerContainAmount(Aspect aspect, int amount) { return false; }
        @Override @Deprecated public boolean doesContainerContain(AspectList aspects) { return false; }
        @Override public int containerContains(Aspect aspect) { return 0; }
    }
}
