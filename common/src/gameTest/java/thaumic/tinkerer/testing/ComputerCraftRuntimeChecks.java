/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only code, distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.testing;

import static thaumic.tinkerer.testing.LuaComputer.strings;
import static thaumic.tinkerer.testing.LuaComputer.values;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.turtle.ITurtleUpgrade;
import dan200.computercraft.api.turtle.TurtleUpgradeType;
import dan200.computercraft.api.upgrades.UpgradeData;
import dev.architectury.platform.Platform;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.blocks.BlocksTC;
import thaumic.tinkerer.common.ThaumicTinkerer;
import thaumic.tinkerer.common.block.BlockAnimationTablet;
import thaumic.tinkerer.common.block.BlockMagnet;
import thaumic.tinkerer.common.block.tile.TileAspectAnalyzer;
import thaumic.tinkerer.common.block.tile.TileMagnet;
import thaumic.tinkerer.common.block.tile.TileMobMagnet;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.registry.TTRegistry;
import thaumic.tinkerer.compat.computer.cc.ComputerCraftCompat;
import thaumic.tinkerer.compat.computer.cc.FumeTool;

/**
 * The CC: Tweaked peripherals and the turtle upgrade of the add-on (docs/deviations/ComputerCraft.md), with the real mod on the
 * GameTest server of each loader.
 *
 * <ul>
 *   <li>The {@code *AreFoundAt*} checks look each peripheral up the way CC: Tweaked does on that loader (Fabric:
 *       {@code PeripheralLookup}, NeoForge: {@code PeripheralCapability}; the wrapper passes the {@link PeripheralFinder}) and
 *       compare its type and method names with the 1.7.10 sources ({@code getType}, {@code getMethodNames} of
 *       {@code PeripheralHandler}'s peripherals and of the tile entities).</li>
 *   <li>The {@code lua*} checks run real Lua on real computers ({@link LuaComputer}): the program wraps the block next to the
 *       computer with CC: Tweaked's {@code peripheral} API, calls the methods (CC: Tweaked's own dispatch, main-thread tasks and
 *       value conversion), and the test compares the answers and the changes in the world.</li>
 * </ul>
 */
public final class ComputerCraftRuntimeChecks {
    /** How long a test waits for its computers (they boot, then each main-thread call takes a server tick). */
    public static final int ANSWER_TICKS = 560;

    private ComputerCraftRuntimeChecks() {}

    /** The loader's lookup of a peripheral at a block (absolute position), as CC: Tweaked asks it. */
    @FunctionalInterface
    public interface PeripheralFinder {
        @Nullable
        IPeripheral find(ServerLevel level, BlockPos pos, Direction side);
    }

    // ---- the names of 1.7.10 ----------------------------------------------------------------------------------------------

    static final List<String> TABLET = List.of("getRedstone", "setRedstone", "getLeftClick", "setLeftClick", "getRotation",
            "setRotation", "hasItem", "trigger");
    static final List<String> KINETIC = List.of("isPulling", "setPulling", "getSignal");
    static final List<String> CORPOREAL = List.of("isPulling", "setPulling", "getSignal", "getAdultSearch", "setAdultSearch");
    static final List<String> ANALYZER = List.of("hasItem", "itemHasAspects", "getAspects", "getAspectCount");
    static final List<String> GOLEM_CONNECTOR = List.of("getDecorations", "getPosition", "getType", "getHealth", "getCore",
            "getHome", "setHome", "getMarkers", "setMarkers", "newMarker", "addMarker", "saveMarker", "deleteMarker", "getMarker",
            "getMarkerCount");
    static final List<String> ASPECT_CONTAINER = List.of("getAspects", "getAspectCount");
    static final List<String> ESSENTIA_TRANSPORT = List.of("isConnectable", "canInputFrom", "canOutputTo", "getSuctionType",
            "getSuctionAmount", "getEssentiaType", "getEssentiaAmount", "getMinimumSuction");

    // ---- the integration is there ------------------------------------------------------------------------------------------

    /** The GameTest server runs with CC: Tweaked, and the add-on's glue for it registered without a failure. */
    public static void computerCraftIsLoadedAndTheGlueRegistered(GameTestHelper test) {
        test.assertTrue(Platform.isModLoaded("computercraft"), "CC: Tweaked is on the GameTest server");
        test.assertTrue(ThaumicTinkerer.failures().isEmpty(), "no registration failed: " + ThaumicTinkerer.failures());
        test.succeed();
    }

    // ---- the lookups --------------------------------------------------------------------------------------------------------

    /** The five peripherals of the add-on's own blocks, found through the loader's lookup, with the 1.7.10 type and names. */
    public static void addonPeripheralsAreFoundAtTheirBlocks(GameTestHelper test, PeripheralFinder finder) {
        BlockPos tablet = new BlockPos(1, 1, 1);
        BlockPos kinetic = new BlockPos(3, 1, 1);
        BlockPos corporeal = new BlockPos(5, 1, 1);
        BlockPos analyzer = new BlockPos(1, 1, 4);
        BlockPos connector = new BlockPos(3, 1, 4);
        test.setBlock(tablet, TTRegistry.block(LibBlockNames.ANIMATION_TABLET));
        test.setBlock(kinetic, TTRegistry.block(LibBlockNames.MAGNET));
        test.setBlock(corporeal, TTRegistry.block(LibBlockNames.MOB_MAGNET));
        test.setBlock(analyzer, TTRegistry.block(LibBlockNames.ASPECT_ANALYZER));
        test.setBlock(connector, TTRegistry.block(LibBlockNames.GOLEMCONNECTOR));
        expectPeripheral(test, finder, tablet, "tt_animationTablet", TABLET);
        expectPeripheral(test, finder, kinetic, "tt_magnet", KINETIC);
        expectPeripheral(test, finder, corporeal, "tt_magnet", CORPOREAL);
        expectPeripheral(test, finder, analyzer, "tt_aspectanalyzer", ANALYZER);
        expectPeripheral(test, finder, connector, "tt_golemconnector", GOLEM_CONNECTOR);
        test.succeed();
    }

    /**
     * The generic peripherals: a Thaumcraft jar and the add-on's Transvector Interface and Essentia Funnel are aspect containers
     * ({@code tt_aspectContainer}: 1.7.10 tried that before the add-on's own peripherals, so the Interface is one, bound or not);
     * an essentia tube and the Arcane Bore's base are essentia transports ({@code tt_aspectTransport}). A stone block and a
     * chest are none of the add-on's.
     */
    public static void genericPeripheralsAreFoundAtThaumcraftBlocks(GameTestHelper test, PeripheralFinder finder) {
        BlockPos jar = new BlockPos(1, 1, 1);
        BlockPos iface = new BlockPos(3, 1, 1);
        BlockPos funnelHopper = new BlockPos(5, 1, 1);
        BlockPos funnel = funnelHopper.above();
        BlockPos tube = new BlockPos(1, 1, 4);
        BlockPos boreBase = new BlockPos(3, 1, 4);
        BlockPos stone = new BlockPos(5, 1, 4);
        BlockPos chest = new BlockPos(5, 1, 6);
        test.setBlock(jar, BlocksTC.jar());
        test.setBlock(iface, TTRegistry.block(LibBlockNames.INTERFACE));
        test.setBlock(funnelHopper, Blocks.HOPPER);
        test.setBlock(funnel, TTRegistry.block(LibBlockNames.FUNNEL));
        test.setBlock(tube, BlocksTC.require("essentia_tube"));
        test.setBlock(boreBase, BlocksTC.require("arcane_bore_base"));
        test.setBlock(stone, Blocks.STONE);
        test.setBlock(chest, Blocks.CHEST);
        expectPeripheral(test, finder, jar, "tt_aspectContainer", ASPECT_CONTAINER);
        expectPeripheral(test, finder, iface, "tt_aspectContainer", ASPECT_CONTAINER);
        expectPeripheral(test, finder, funnel, "tt_aspectContainer", ASPECT_CONTAINER);
        expectPeripheral(test, finder, tube, "tt_aspectTransport", ESSENTIA_TRANSPORT);
        expectPeripheral(test, finder, boreBase, "tt_aspectTransport", ESSENTIA_TRANSPORT);
        for (BlockPos none : List.of(stone, chest)) {
            IPeripheral found = finder.find(test.getLevel(), test.absolutePos(none), Direction.UP);
            test.assertTrue(found == null || !found.getType().startsWith("tt_"),
                    test.getLevel().getBlockState(test.absolutePos(none)) + " is no peripheral of the add-on: " + found);
        }
        test.succeed();
    }

    private static void expectPeripheral(GameTestHelper test, PeripheralFinder finder, BlockPos pos, String type, List<String> methods) {
        ServerLevel level = test.getLevel();
        BlockPos at = test.absolutePos(pos);
        BlockEntity tile = level.getBlockEntity(at);
        String where = level.getBlockState(at).getBlock() + " at " + pos;
        for (Direction side : Direction.values()) {
            IPeripheral peripheral = finder.find(level, at, side);
            test.assertTrue(peripheral != null, where + " is a peripheral from " + side);
            test.assertTrue(type.equals(peripheral.getType()), where + " has the type " + type + ": " + peripheral.getType());
            test.assertTrue(peripheral instanceof IDynamicPeripheral, where + " lists its methods by name");
            List<String> names = Arrays.asList(((IDynamicPeripheral) peripheral).getMethodNames());
            test.assertTrue(methods.equals(names), where + " has the 1.7.10 methods in their order " + methods + ": " + names);
            test.assertTrue(peripheral.getTarget() == tile, where + " reads its own block entity");
            IPeripheral again = finder.find(level, at, side.getOpposite());
            test.assertTrue(peripheral.equals(again), where + ": two lookups give the same peripheral, so a computer keeps it");
        }
    }

    // ---- real Lua: the Dynamism Tablet ----------------------------------------------------------------------------------------

    /**
     * A computer under a Dynamism Tablet that holds a stick: every method of {@code tt_animationTablet}, the setters' effects
     * on the block entity and the block, the errors of {@code setRotation} (above 3, and below 0) and of a wrong argument type.
     */
    public static void luaDrivesTheDynamismTablet(GameTestHelper test) {
        BlockPos tabletPos = new BlockPos(2, 2, 2);
        test.setBlock(tabletPos, TTRegistry.block(LibBlockNames.ANIMATION_TABLET));
        TileAnimationTablet tablet = test.getBlockEntity(tabletPos);
        tablet.setItem(0, new ItemStack(Items.STICK));
        LuaComputer computer = LuaComputer.computer(test, tabletPos.below(), """
                local p = peripheral.wrap("top")
                local r = {}
                r.type = { peripheral.getType("top") }
                r.methods = peripheral.getMethods("top")
                r.getRedstone = { p.getRedstone() }
                r.setRedstone = { p.setRedstone(true) }
                r.getRedstoneAfter = { p.getRedstone() }
                r.getLeftClick = { p.getLeftClick() }
                r.setLeftClick = { p.setLeftClick(false) }
                r.getLeftClickAfter = { p.getLeftClick() }
                r.getRotation = { p.getRotation() }
                r.setRotation = { p.setRotation(3) }
                r.getRotationAfter = { p.getRotation() }
                r.setRotationTooHigh = { pcall(p.setRotation, 4) }
                r.setRotationNegative = { pcall(p.setRotation, -1) }
                r.setRedstoneWrongType = { pcall(p.setRedstone, "yes") }
                r.hasItem = { p.hasItem() }
                r.trigger = { p.trigger() }
                r.triggerWhileBusy = { p.trigger() }
                return r
                """);
        LuaComputer.whenAnswered(test, ANSWER_TICKS, List.of(computer), answers -> {
            JsonObject r = answers.get(0);
            expectTypeAndMethods(test, r, "tt_animationTablet", TABLET);
            expectValues(test, r, "getRedstone", false);
            expectValues(test, r, "setRedstone");
            expectValues(test, r, "getRedstoneAfter", true);
            expectValues(test, r, "getLeftClick", true);
            expectValues(test, r, "setLeftClick");
            expectValues(test, r, "getLeftClickAfter", false);
            expectValues(test, r, "getRotation", 0);
            expectValues(test, r, "setRotation");
            expectValues(test, r, "getRotationAfter", 3);
            expectError(test, r, "setRotationTooHigh", "Invalid value: 4.");
            expectError(test, r, "setRotationNegative", "Invalid value: -1.");
            expectError(test, r, "setRedstoneWrongType", "bad argument #1 (boolean expected, got string)");
            expectValues(test, r, "hasItem", true);
            expectValues(test, r, "trigger", true);
            expectValues(test, r, "triggerWhileBusy", false);
            test.assertTrue(tablet.redstone, "setRedstone(true) switched the Redstone Control mode on");
            test.assertTrue(!tablet.leftClick, "setLeftClick(false) switched it to the right click");
            test.assertTrue(test.getBlockState(tabletPos).getValue(BlockAnimationTablet.FACING) == Direction.EAST,
                    "setRotation(3) turned the tablet east: " + test.getBlockState(tabletPos));
            test.assertTrue(!tablet.isIdle(), "trigger() started a swing");
        });
    }

    // ---- real Lua: the Attractors -------------------------------------------------------------------------------------------

    /**
     * Two computers: one under a Kinetic Attractor with a redstone block beside it, one under a Corporeal Attractor without
     * power. Polarity, signal and the Corporeal Attractor's adult search, read and changed from Lua.
     */
    public static void luaDrivesTheAttractors(GameTestHelper test) {
        BlockPos kineticPos = new BlockPos(1, 2, 1);
        BlockPos corporealPos = new BlockPos(5, 2, 5);
        test.setBlock(kineticPos, TTRegistry.block(LibBlockNames.MAGNET));
        test.setBlock(kineticPos.west(), Blocks.REDSTONE_BLOCK);
        test.setBlock(corporealPos, TTRegistry.block(LibBlockNames.MOB_MAGNET));
        String body = """
                local p = peripheral.wrap("top")
                local r = {}
                r.type = { peripheral.getType("top") }
                r.methods = peripheral.getMethods("top")
                r.isPulling = { p.isPulling() }
                r.setPulling = { p.setPulling(false) }
                r.isPullingAfter = { p.isPulling() }
                r.getSignal = { p.getSignal() }
                r.setPullingWrongType = { pcall(p.setPulling) }
                if p.getAdultSearch then
                  r.getAdultSearch = { p.getAdultSearch() }
                  r.setAdultSearch = { p.setAdultSearch(false) }
                  r.getAdultSearchAfter = { p.getAdultSearch() }
                end
                return r
                """;
        LuaComputer kinetic = LuaComputer.computer(test, kineticPos.below(), body);
        LuaComputer corporeal = LuaComputer.computer(test, corporealPos.below(), body);
        LuaComputer.whenAnswered(test, ANSWER_TICKS, List.of(kinetic, corporeal), answers -> {
            JsonObject k = answers.get(0);
            JsonObject c = answers.get(1);
            expectTypeAndMethods(test, k, "tt_magnet", KINETIC);
            expectTypeAndMethods(test, c, "tt_magnet", CORPOREAL);
            for (JsonObject r : answers) {
                expectValues(test, r, "isPulling", true);
                expectValues(test, r, "setPulling");
                expectValues(test, r, "isPullingAfter", false);
                expectError(test, r, "setPullingWrongType", "bad argument #1 (boolean expected, got nil)");
            }
            expectValues(test, k, "getSignal", 15);
            expectValues(test, c, "getSignal", 0);
            test.assertTrue(!k.has("getAdultSearch"), "the Kinetic Attractor has no adult search");
            expectValues(test, c, "getAdultSearch", true);
            expectValues(test, c, "setAdultSearch");
            expectValues(test, c, "getAdultSearchAfter", false);
            test.assertTrue(!test.getBlockState(kineticPos).getValue(BlockMagnet.PULLING), "the Kinetic Attractor pushes now");
            test.assertTrue(!test.getBlockState(corporealPos).getValue(BlockMagnet.PULLING), "the Corporeal Attractor pushes now");
            TileMagnet kineticTile = test.getBlockEntity(kineticPos);
            test.assertTrue(!(kineticTile instanceof TileMobMagnet), "the kinetic block entity is a plain magnet");
            TileMobMagnet mob = test.getBlockEntity(corporealPos);
            test.assertTrue(!mob.adult, "setAdultSearch(false) makes it look for children");
        });
    }

    // ---- real Lua: the Aspectalyzer ----------------------------------------------------------------------------------------------

    /** Two computers under two Aspectalyzers, one holding a diamond, one empty; the answers are those of the block entity. */
    public static void luaReadsTheAspectalyzer(GameTestHelper test) {
        BlockPos fullPos = new BlockPos(1, 2, 1);
        BlockPos emptyPos = new BlockPos(5, 2, 5);
        test.setBlock(fullPos, TTRegistry.block(LibBlockNames.ASPECT_ANALYZER));
        test.setBlock(emptyPos, TTRegistry.block(LibBlockNames.ASPECT_ANALYZER));
        TileAspectAnalyzer full = test.getBlockEntity(fullPos);
        full.setItem(0, new ItemStack(Items.DIAMOND));
        List<String> tags = full.getAspectsMethod();
        Map<String, Integer> amounts = full.getAspectsAmountsMethod();
        test.assertTrue(!tags.isEmpty(), "a diamond has aspects to read");
        String body = """
                local p = peripheral.wrap("top")
                local r = {}
                r.type = { peripheral.getType("top") }
                r.methods = peripheral.getMethods("top")
                r.hasItem = { p.hasItem() }
                r.itemHasAspects = { p.itemHasAspects() }
                r.getAspects = { p.getAspects() }
                r.getAspectCount = { p.getAspectCount() }
                return r
                """;
        LuaComputer withItem = LuaComputer.computer(test, fullPos.below(), body);
        LuaComputer without = LuaComputer.computer(test, emptyPos.below(), body);
        LuaComputer.whenAnswered(test, ANSWER_TICKS, List.of(withItem, without), answers -> {
            JsonObject f = answers.get(0);
            JsonObject e = answers.get(1);
            expectTypeAndMethods(test, f, "tt_aspectanalyzer", ANALYZER);
            expectValues(test, f, "hasItem", true);
            expectValues(test, f, "itemHasAspects", true);
            JsonArray aspects = values(f, "getAspects");
            test.assertTrue(aspects.size() == 1 && strings(aspects.get(0)).equals(tags),
                    "getAspects() is the sorted list of tags " + tags + ": " + aspects);
            JsonArray counts = values(f, "getAspectCount");
            test.assertTrue(counts.size() == 1 && amountsOf(counts.get(0)).equals(amounts),
                    "getAspectCount() maps each tag to its amount " + amounts + ": " + counts);
            expectValues(test, e, "hasItem", false);
            expectValues(test, e, "itemHasAspects", false);
            expectEmptyTable(test, e, "getAspects");
            expectEmptyTable(test, e, "getAspectCount");
        });
    }

    // ---- real Lua: the Golem Connector without a golem ---------------------------------------------------------------------------

    /**
     * A computer under a Golem Connector that is bound to no golem: the 1.7.10 answers of a connector without a golem (no value),
     * a new marker at the connector, the original messages for a wrong number of arguments, and the errors of arguments of the
     * wrong type.
     */
    public static void luaAsksTheGolemConnectorWithoutAGolem(GameTestHelper test) {
        BlockPos connectorPos = new BlockPos(2, 2, 2);
        test.setBlock(connectorPos, TTRegistry.block(LibBlockNames.GOLEMCONNECTOR));
        LuaComputer computer = LuaComputer.computer(test, connectorPos.below(), """
                local p = peripheral.wrap("top")
                local r = {}
                r.type = { peripheral.getType("top") }
                r.methods = peripheral.getMethods("top")
                local marker = p.newMarker()
                r.newMarker = { marker }
                r.getDecorations = { p.getDecorations() }
                r.getPosition = { p.getPosition() }
                r.getType = { p.getType() }
                r.getHealth = { p.getHealth() }
                r.getCore = { p.getCore() }
                r.getHome = { p.getHome() }
                r.setHome = { p.setHome(1, 2, 3, 4) }
                r.getMarkers = { p.getMarkers() }
                r.setMarkers = { p.setMarkers({ marker }) }
                r.addMarker = { p.addMarker(marker) }
                r.saveMarker = { p.saveMarker(0, marker) }
                r.deleteMarker = { p.deleteMarker(0) }
                r.getMarker = { p.getMarker(0) }
                r.getMarkerCount = { p.getMarkerCount() }
                r.setHomeThree = { pcall(p.setHome, 1, 2, 3) }
                r.setMarkersNone = { pcall(p.setMarkers) }
                r.addMarkerNone = { pcall(p.addMarker) }
                r.saveMarkerOne = { pcall(p.saveMarker, 1) }
                r.deleteMarkerNone = { pcall(p.deleteMarker) }
                r.getMarkerNone = { pcall(p.getMarker) }
                r.addMarkerUnread = { pcall(p.addMarker, { posX = 1 }) }
                r.addMarkerNotATable = { pcall(p.addMarker, 5) }
                r.getMarkerNotANumber = { pcall(p.getMarker, "first") }
                return r
                """);
        LuaComputer.whenAnswered(test, ANSWER_TICKS, List.of(computer), answers -> {
            JsonObject r = answers.get(0);
            expectTypeAndMethods(test, r, "tt_golemconnector", GOLEM_CONNECTOR);
            BlockPos at = test.absolutePos(connectorPos);
            JsonArray marker = values(r, "newMarker");
            test.assertTrue(marker.size() == 1 && marker.get(0).isJsonObject(), "newMarker() answers a table: " + marker);
            JsonObject m = marker.get(0).getAsJsonObject();
            test.assertTrue(m.get("posX").getAsInt() == at.getX() && m.get("posY").getAsInt() == at.getY()
                    && m.get("posZ").getAsInt() == at.getZ(), "the new marker is at the connector " + at + ": " + m);
            test.assertTrue(m.get("dim").getAsString().equals(test.getLevel().dimension().location().toString()),
                    "in its dimension: " + m);
            test.assertTrue(m.get("color").getAsInt() == -1 && m.get("side").getAsInt() == 1, "any colour, the top face: " + m);
            for (String call : List.of("getDecorations", "getPosition", "getType", "getHealth", "getCore", "getHome", "setHome",
                    "getMarkers", "setMarkers", "addMarker", "saveMarker", "deleteMarker", "getMarker", "getMarkerCount")) {
                expectValues(test, r, call);
            }
            expectError(test, r, "setHomeThree", "Invalid arguments");
            expectError(test, r, "setMarkersNone", "setMarkers takes 1 argument");
            expectError(test, r, "addMarkerNone", "addMarker must have 1 argument");
            expectError(test, r, "saveMarkerOne", "saveMarker must have 2 arguments");
            expectError(test, r, "deleteMarkerNone", "deleteMarker must have 1 argument");
            expectError(test, r, "getMarkerNone", "getMarker must have 1 argument");
            // without a golem the marker is not read (1.7.10 returned before toMarkerImplementation), but the argument is cast
            expectValues(test, r, "addMarkerUnread", true);
            expectError(test, r, "addMarkerNotATable", "bad argument #1 (table expected, got number)");
            expectError(test, r, "getMarkerNotANumber", "bad argument #1 (number expected, got string)");
        });
    }

    // ---- real Lua: aspect containers -----------------------------------------------------------------------------------------------

    /**
     * Three computers: under a jar of five Ignis, under a Transvector Interface bound to that jar (an aspect container that forwards
     * the jar's aspects, as in 1.7.10), and under an empty jar labelled Aqua (1.7.10 answered the label; the API cannot read it, so
     * the port answers an empty table, docs/deviations/ComputerCraft.md).
     */
    public static void luaReadsAspectContainers(GameTestHelper test) {
        BlockPos jarPos = new BlockPos(1, 2, 1);
        BlockPos ifacePos = new BlockPos(1, 2, 4);
        BlockPos labelledPos = new BlockPos(5, 2, 1);
        test.setBlock(jarPos, BlocksTC.jar());
        test.setBlock(ifacePos, TTRegistry.block(LibBlockNames.INTERFACE));
        test.setBlock(labelledPos, BlocksTC.jar());
        IAspectContainer jar = (IAspectContainer) test.getLevel().getBlockEntity(test.absolutePos(jarPos));
        test.assertTrue(jar.addToContainer(Aspect.FIRE, 5) == 0, "the jar takes five Ignis");
        TileTransvectorInterface iface = test.getBlockEntity(ifacePos);
        iface.setTarget(test.absolutePos(jarPos));
        BlockEntity labelled = test.getLevel().getBlockEntity(test.absolutePos(labelledPos));
        CompoundTag tag = labelled.saveWithoutMetadata(test.getLevel().registryAccess());
        tag.putString("AspectFilter", Aspect.WATER.getTag());
        labelled.loadCustomOnly(tag, test.getLevel().registryAccess());
        String body = """
                local p = peripheral.wrap("top")
                local r = {}
                r.type = { peripheral.getType("top") }
                r.methods = peripheral.getMethods("top")
                r.getAspects = { p.getAspects() }
                r.countIgnis = { p.getAspectCount("ignis") }
                r.countAqua = { p.getAspectCount("aqua") }
                r.countNoAspect = { p.getAspectCount("no_such_aspect") }
                r.countWrongType = { pcall(p.getAspectCount, 3) }
                return r
                """;
        LuaComputer underJar = LuaComputer.computer(test, jarPos.below(), body);
        LuaComputer underInterface = LuaComputer.computer(test, ifacePos.below(), body);
        LuaComputer underLabelled = LuaComputer.computer(test, labelledPos.below(), body);
        LuaComputer.whenAnswered(test, ANSWER_TICKS, List.of(underJar, underInterface, underLabelled), answers -> {
            for (JsonObject r : answers) {
                expectTypeAndMethods(test, r, "tt_aspectContainer", ASPECT_CONTAINER);
                expectValues(test, r, "countAqua", 0);
                expectValues(test, r, "countNoAspect", 0);
                expectError(test, r, "countWrongType", "bad argument #1 (string expected, got number)");
            }
            for (JsonObject r : answers.subList(0, 2)) {
                JsonArray aspects = values(r, "getAspects");
                test.assertTrue(aspects.size() == 1 && strings(aspects.get(0)).equals(List.of("ignis")), "getAspects() is {ignis}: " + aspects);
                expectValues(test, r, "countIgnis", 5);
            }
            JsonObject l = answers.get(2);
            expectEmptyTable(test, l, "getAspects");
            expectValues(test, l, "countIgnis", 0);
            test.assertTrue(jar.getAspects().getAmount(Aspect.FIRE) == 5, "reading took nothing from the jar");
        });
    }

    // ---- real Lua: essentia transports -------------------------------------------------------------------------------------------

    /**
     * Two computers: under an essentia tube with nothing attached and under the base of an Arcane Bore. Every method for every
     * side 0 to 5, compared with what the block entity answers through {@code IEssentiaTransport}; a side 6 and a string are bad
     * arguments.
     */
    public static void luaReadsEssentiaTransports(GameTestHelper test) {
        BlockPos tubePos = new BlockPos(1, 2, 1);
        BlockPos basePos = new BlockPos(5, 2, 5);
        test.setBlock(tubePos, BlocksTC.require("essentia_tube"));
        test.setBlock(basePos, BlocksTC.require("arcane_bore_base"));
        String body = """
                local p = peripheral.wrap("top")
                local r = {}
                r.type = { peripheral.getType("top") }
                r.methods = peripheral.getMethods("top")
                for side = 0, 5 do
                  r["isConnectable" .. side] = { p.isConnectable(side) }
                  r["canInputFrom" .. side] = { p.canInputFrom(side) }
                  r["canOutputTo" .. side] = { p.canOutputTo(side) }
                  r["getSuctionType" .. side] = { pcall(p.getSuctionType, side) }
                  r["getSuctionAmount" .. side] = { p.getSuctionAmount(side) }
                  r["getEssentiaType" .. side] = { pcall(p.getEssentiaType, side) }
                  r["getEssentiaAmount" .. side] = { p.getEssentiaAmount(side) }
                end
                r.getMinimumSuction = { p.getMinimumSuction() }
                r.sideSix = { pcall(p.isConnectable, 6) }
                r.sideText = { pcall(p.getSuctionAmount, "up") }
                return r
                """;
        LuaComputer underTube = LuaComputer.computer(test, tubePos.below(), body);
        LuaComputer underBase = LuaComputer.computer(test, basePos.below(), body);
        LuaComputer.whenAnswered(test, ANSWER_TICKS, List.of(underTube, underBase), answers -> {
            List<BlockPos> positions = List.of(tubePos, basePos);
            boolean sawSuctionType = false;
            for (int i = 0; i < 2; i++) {
                JsonObject r = answers.get(i);
                IEssentiaTransport transport = (IEssentiaTransport) test.getLevel().getBlockEntity(test.absolutePos(positions.get(i)));
                expectTypeAndMethods(test, r, "tt_aspectTransport", ESSENTIA_TRANSPORT);
                for (Direction face : Direction.values()) {
                    int side = face.get3DDataValue();
                    expectValues(test, r, "isConnectable" + side, transport.isConnectable(face));
                    expectValues(test, r, "canInputFrom" + side, transport.canInputFrom(face));
                    expectValues(test, r, "canOutputTo" + side, transport.canOutputTo(face));
                    expectValues(test, r, "getSuctionAmount" + side, transport.getSuctionAmount(face));
                    expectValues(test, r, "getEssentiaAmount" + side, transport.getEssentiaAmount(face));
                    Aspect suction = transport.getSuctionType(face);
                    if (suction == null) expectError(test, r, "getSuctionType" + side, "no suction type on that side");
                    else expectValues(test, r, "getSuctionType" + side, true, suction.getTag());
                    sawSuctionType |= suction != null;
                    Aspect essentia = transport.getEssentiaType(face);
                    if (essentia == null) expectError(test, r, "getEssentiaType" + side, "no essentia on that side");
                    else expectValues(test, r, "getEssentiaType" + side, true, essentia.getTag());
                }
                expectValues(test, r, "getMinimumSuction", transport.getMinimumSuction());
                expectError(test, r, "sideSix", "bad argument #1 (side 0 to 5 expected, got 6)");
                expectError(test, r, "sideText", "bad argument #1 (number expected, got string)");
            }
            test.assertTrue(sawSuctionType, "the bore base has a suction type (Perditio), so a tag was read too");
        });
    }

    // ---- the turtle upgrade -----------------------------------------------------------------------------------------------------------

    /**
     * The Fume Dissipator's turtle tool comes from its data file with the add-on's upgrade type (1.7.10 {@code FumeTool}: a tool,
     * the adjective {@code ttcomputer.dissipator}, crafted with the Fume Dissipator), and a turtle and a Fume Dissipator side by
     * side in a crafting grid make a turtle with it.
     */
    public static void fumeDissipatorIsATurtleUpgrade(GameTestHelper test) {
        ServerLevel level = test.getLevel();
        Registry<ITurtleUpgrade> upgrades = level.registryAccess().registryOrThrow(ITurtleUpgrade.REGISTRY);
        ITurtleUpgrade upgrade = upgrades.get(ComputerCraftCompat.GAS_REMOVER_ID);
        test.assertTrue(upgrade instanceof FumeTool, "the data file made the upgrade " + ComputerCraftCompat.GAS_REMOVER_ID + ": " + upgrade);
        test.assertTrue(upgrade.getType() == ComputerCraftCompat.gasRemoverType(), "of the add-on's upgrade type");
        Registry<?> types = BuiltInRegistries.REGISTRY.get(ITurtleUpgrade.typeRegistry().location());
        test.assertTrue(types != null && ComputerCraftCompat.GAS_REMOVER_ID.equals(keyOf(types, upgrade.getType())),
                "the type is registered as " + ComputerCraftCompat.GAS_REMOVER_ID);
        test.assertTrue(upgrade.getUpgradeType() == TurtleUpgradeType.TOOL, "a tool, as 1.7.10 TurtleUpgradeType.Tool");
        test.assertTrue(upgrade.getCraftingItem().is(TTRegistry.item(LibItemNames.GAS_REMOVER)),
                "crafted with the Fume Dissipator: " + upgrade.getCraftingItem());
        test.assertTrue(upgrade.getAdjective().getContents() instanceof TranslatableContents t && t.getKey().equals("ttcomputer.dissipator"),
                "the adjective is the 1.7.10 key: " + upgrade.getAdjective());

        ItemStack turtle = new ItemStack(BuiltInRegistries.ITEM.get(LuaComputer.NORMAL_TURTLE));
        ItemStack dissipator = TTRegistry.stack(LibItemNames.GAS_REMOVER);
        CraftingInput grid = CraftingInput.of(2, 1, List.of(turtle, dissipator));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        test.assertTrue(recipe.isPresent(), "a turtle and a Fume Dissipator make something");
        ItemStack crafted = recipe.get().value().assemble(grid, level.registryAccess());
        test.assertTrue(crafted.is(turtle.getItem()), "a turtle: " + crafted);
        List<ResourceLocation> sides = new ArrayList<>();
        for (String side : List.of("left_turtle_upgrade", "right_turtle_upgrade")) {
            DataComponentType<?> component = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath("computercraft", side));
            if (component != null && crafted.get(component) instanceof UpgradeData<?> data) sides.add(data.holder().key().location());
        }
        test.assertTrue(sides.equals(List.of(ComputerCraftCompat.GAS_REMOVER_ID)), "with the gas remover on one side: " + sides);
        test.succeed();
    }

    @SuppressWarnings("unchecked")
    private static <T> ResourceLocation keyOf(Registry<T> registry, Object value) {
        return registry.getKey((T) value);
    }

    /**
     * A turtle with the Fume Dissipator on its left side runs {@code turtle.dig()}: every gas in the cube from its position minus
     * three to plus two goes, the gases one block further stay ({@code FumeTool.useTool}); {@code turtle.attack()} fails.
     */
    public static void luaTurtleDissipatesGas(GameTestHelper test) {
        BlockPos turtlePos = new BlockPos(3, 2, 3);
        Block light = TTRegistry.block(LibBlockNames.GASEOUS_LIGHT);
        Block shadow = TTRegistry.block(LibBlockNames.GASEOUS_SHADOW);
        List<BlockPos> inside = List.of(turtlePos.offset(-3, 0, 0), turtlePos.offset(2, 0, 0), turtlePos.offset(0, 2, 0),
                turtlePos.offset(0, 0, -3), turtlePos.offset(-2, -1, 2));
        List<BlockPos> outside = List.of(turtlePos.offset(3, 0, 0), turtlePos.offset(0, 3, 0), turtlePos.offset(0, 0, 3));
        int n = 0;
        for (BlockPos pos : inside) test.setBlock(pos, n++ % 2 == 0 ? light : shadow);
        for (BlockPos pos : outside) test.setBlock(pos, n++ % 2 == 0 ? light : shadow);
        CompoundTag upgrades = new CompoundTag();
        upgrades.putString("LeftUpgrade", ComputerCraftCompat.GAS_REMOVER_ID.toString());
        LuaComputer turtle = LuaComputer.turtle(test, turtlePos, upgrades, """
                local r = {}
                if turtle.getEquippedLeft then r.equipped = { turtle.getEquippedLeft() } end
                r.dig = { turtle.dig() }
                r.attack = { turtle.attack() }
                return r
                """);
        LuaComputer.whenAnswered(test, ANSWER_TICKS, List.of(turtle), answers -> {
            JsonObject r = answers.get(0);
            if (r.has("equipped")) {
                JsonArray equipped = values(r, "equipped");
                test.assertTrue(equipped.size() == 1 && equipped.get(0).getAsJsonObject().get("name").getAsString()
                        .equals(BuiltInRegistries.ITEM.getKey(TTRegistry.item(LibItemNames.GAS_REMOVER)).toString()),
                        "the turtle has the Fume Dissipator on its left: " + equipped);
            }
            expectValues(test, r, "dig", true);
            JsonArray attack = values(r, "attack");
            test.assertTrue(attack.size() >= 1 && !attack.get(0).getAsBoolean(), "the tool does not attack: " + attack);
            for (BlockPos pos : inside) test.assertTrue(test.getBlockState(pos).isAir(), "the gas at " + pos + " is gone");
            for (BlockPos pos : outside) {
                Block block = test.getBlockState(pos).getBlock();
                test.assertTrue(block == light || block == shadow, "the gas at " + pos + ", out of reach, stays: " + block);
            }
        });
    }

    // ---- comparing the answers ---------------------------------------------------------------------------------------------------------

    private static void expectTypeAndMethods(GameTestHelper test, JsonObject r, String type, List<String> methods) {
        JsonArray types = values(r, "type");
        test.assertTrue(types.size() >= 1 && type.equals(types.get(0).getAsString()), "peripheral.getType is " + type + ": " + types);
        Set<String> listed = new HashSet<>(strings(r.get("methods")));
        test.assertTrue(listed.equals(new HashSet<>(methods)), "peripheral.getMethods lists " + methods + ": " + listed);
    }

    /** The call returned exactly these values (booleans, numbers or strings). */
    private static void expectValues(GameTestHelper test, JsonObject r, String key, Object... expected) {
        JsonArray got = values(r, key);
        boolean same = got.size() == expected.length;
        for (int i = 0; same && i < expected.length; i++) same = matches(got.get(i), expected[i]);
        test.assertTrue(same, key + " answered " + Arrays.toString(expected) + ": " + got);
    }

    private static boolean matches(JsonElement value, Object expected) {
        if (!value.isJsonPrimitive()) return false;
        if (expected instanceof Boolean bool) return value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean() == bool;
        if (expected instanceof Number number) return value.getAsJsonPrimitive().isNumber() && value.getAsDouble() == number.doubleValue();
        return value.getAsJsonPrimitive().isString() && value.getAsString().equals(expected);
    }

    /** {@code { pcall(...) }} of a call that raised a Lua error whose message ends with {@code message}. */
    private static void expectError(GameTestHelper test, JsonObject r, String key, String message) {
        JsonArray got = values(r, key);
        test.assertTrue(got.size() == 2 && !got.get(0).getAsBoolean() && got.get(1).getAsString().endsWith(message),
                key + " raised the error \"" + message + "\": " + got);
    }

    private static void expectEmptyTable(GameTestHelper test, JsonObject r, String key) {
        JsonArray got = values(r, key);
        test.assertTrue(got.size() == 1 && (got.get(0).isJsonObject() && got.get(0).getAsJsonObject().size() == 0
                || got.get(0).isJsonArray() && got.get(0).getAsJsonArray().size() == 0), key + " answered an empty table: " + got);
    }

    private static Map<String, Integer> amountsOf(JsonElement table) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (table.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : table.getAsJsonObject().entrySet()) out.put(entry.getKey(), entry.getValue().getAsInt());
        }
        return out;
    }
}
