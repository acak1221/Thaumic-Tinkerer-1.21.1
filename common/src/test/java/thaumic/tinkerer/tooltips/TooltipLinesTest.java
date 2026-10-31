/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only
 * code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thaumic.tinkerer.common.block.BlockAnimationTablet;
import thaumic.tinkerer.common.block.BlockMagnet;
import thaumic.tinkerer.common.block.BlockRepairer;
import thaumic.tinkerer.common.block.kami.BlockWarpGate;
import thaumic.tinkerer.common.block.tile.TileMagnet;
import thaumic.tinkerer.common.block.tile.TileMobMagnet;
import thaumic.tinkerer.common.block.tile.TileRepairer;
import thaumic.tinkerer.common.block.tile.kami.TileWarpGate;
import thaumic.tinkerer.common.block.tile.tablet.TileAnimationTablet;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorDislocator;
import thaumic.tinkerer.common.block.tile.transvector.TileTransvectorInterface;
import thaumic.tinkerer.common.block.transvector.BlockTransvectorDislocator;
import thaumic.tinkerer.common.block.transvector.BlockTransvectorInterface;
import thaumic.tinkerer.compat.tooltip.AnimationTabletTooltip;
import thaumic.tinkerer.compat.tooltip.BlockTooltip;
import thaumic.tinkerer.compat.tooltip.MagnetTooltip;
import thaumic.tinkerer.compat.tooltip.RepairerTooltip;
import thaumic.tinkerer.compat.tooltip.TinkererTooltips;
import thaumic.tinkerer.compat.tooltip.TooltipData;
import thaumic.tinkerer.compat.tooltip.TooltipKeys;
import thaumic.tinkerer.compat.tooltip.TransvectorInterfaceTooltip;
import thaumic.tinkerer.compat.tooltip.WarpGateTooltip;

/**
 * The client half of the block tooltips without a world: which lines (keys and arguments) a given state gives, how the
 * data tag is read, and how the server data of a block is shared between providers. The server half needs real block
 * entities and is checked by {@code TooltipsRuntimeChecks}.
 */
class TooltipLinesTest {
    /** No registries are needed for plain texts. */
    private static final HolderLookup.Provider NO_REGISTRIES = RegistryAccess.EMPTY;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    // ---- the list ------------------------------------------------------------------------------------------------------

    @Test
    void theFiveTooltipsAreTheBlocksOfTheTwoOriginalProviders() {
        assertEquals(List.of("animation_tablet", "interface", "repairer", "warp_gate", "magnet"),
                TinkererTooltips.ALL.stream().map(BlockTooltip::name).toList());
        Set<String> uids = new HashSet<>();
        for (BlockTooltip<?> tooltip : TinkererTooltips.ALL) {
            assertEquals("thaumictinkerer", tooltip.uid().getNamespace());
            assertEquals(tooltip.name(), tooltip.uid().getPath());
            assertTrue(uids.add(tooltip.uid().toString()), "unique uid " + tooltip.uid());
            assertEquals("config.jade.plugin_thaumictinkerer." + tooltip.name(), tooltip.configKey());
        }
        assertEquals(BlockAnimationTablet.class, TinkererTooltips.ANIMATION_TABLET.blockClass());
        assertEquals(TileAnimationTablet.class, TinkererTooltips.ANIMATION_TABLET.blockEntityClass());
        assertEquals(BlockTransvectorInterface.class, TinkererTooltips.INTERFACE.blockClass());
        assertEquals(TileTransvectorInterface.class, TinkererTooltips.INTERFACE.blockEntityClass());
        assertEquals(BlockRepairer.class, TinkererTooltips.REPAIRER.blockClass());
        assertEquals(TileRepairer.class, TinkererTooltips.REPAIRER.blockEntityClass());
        assertEquals(BlockWarpGate.class, TinkererTooltips.WARP_GATE.blockClass());
        assertEquals(TileWarpGate.class, TinkererTooltips.WARP_GATE.blockEntityClass());
        // One block class for both attractors (1.7.10: one block, bit 1 of the metadata), and the corporeal one's block
        // entity is a kinetic one: one provider covers both, as MagnetProvider did.
        assertEquals(BlockMagnet.class, TinkererTooltips.MAGNET.blockClass());
        assertEquals(TileMagnet.class, TinkererTooltips.MAGNET.blockEntityClass());
        assertTrue(TileMagnet.class.isAssignableFrom(TileMobMagnet.class));
    }

    @Test
    void theDislocatorHasNoTooltipThoughItIsATransvectorToo() {
        // 1.7.10 registered the interface's block class only; the dislocator shares the base block entity class.
        for (BlockTooltip<?> tooltip : TinkererTooltips.ALL) {
            assertFalse(tooltip.blockClass().isAssignableFrom(BlockTransvectorDislocator.class), tooltip.name());
            assertFalse(tooltip.blockEntityClass().isAssignableFrom(TileTransvectorDislocator.class), tooltip.name());
        }
    }

    // ---- Animation Tablet ----------------------------------------------------------------------------------------------

    @Test
    void anEmptyTabletShowsNothingAndNoModes() {
        assertEquals(List.of(tr(TooltipKeys.CURRENT_TOOL, tr(TooltipKeys.NOTHING))), AnimationTabletTooltip.linesFor(null, true, false));
        assertEquals(List.of(tr(TooltipKeys.CURRENT_TOOL, tr(TooltipKeys.NOTHING))), AnimationTabletTooltip.linesFor(null, false, true));
    }

    @Test
    void aTabletWithAToolShowsItsModes() {
        Component tool = Component.literal("Pick");
        assertEquals(List.of(tr(TooltipKeys.CURRENT_TOOL, tool), tr(TooltipKeys.LEFT_CLICK), tr(TooltipKeys.AUTONOMOUS)),
                AnimationTabletTooltip.linesFor(tool, true, false));
        assertEquals(List.of(tr(TooltipKeys.CURRENT_TOOL, tool), tr(TooltipKeys.LEFT_CLICK), tr(TooltipKeys.REDSTONE)),
                AnimationTabletTooltip.linesFor(tool, true, true));
        assertEquals(List.of(tr(TooltipKeys.CURRENT_TOOL, tool), tr(TooltipKeys.RIGHT_CLICK), tr(TooltipKeys.AUTONOMOUS)),
                AnimationTabletTooltip.linesFor(tool, false, false));
        assertEquals(List.of(tr(TooltipKeys.CURRENT_TOOL, tool), tr(TooltipKeys.RIGHT_CLICK), tr(TooltipKeys.REDSTONE)),
                AnimationTabletTooltip.linesFor(tool, false, true));
    }

    @Test
    void theTabletTagIsReadBack() {
        CompoundTag data = new CompoundTag();
        TooltipData.putText(data, AnimationTabletTooltip.TAG_TOOL, tr("item.minecraft.diamond_pickaxe"), NO_REGISTRIES);
        data.putBoolean(AnimationTabletTooltip.TAG_LEFT_CLICK, false);
        data.putBoolean(AnimationTabletTooltip.TAG_REDSTONE, true);
        assertEquals(List.of(tr(TooltipKeys.CURRENT_TOOL, tr("item.minecraft.diamond_pickaxe")), tr(TooltipKeys.RIGHT_CLICK), tr(TooltipKeys.REDSTONE)),
                TinkererTooltips.ANIMATION_TABLET.lines(data, NO_REGISTRIES));
        assertEquals(List.of(tr(TooltipKeys.CURRENT_TOOL, tr(TooltipKeys.NOTHING))), TinkererTooltips.ANIMATION_TABLET.lines(new CompoundTag(), NO_REGISTRIES));
    }

    // ---- texts in the data -------------------------------------------------------------------------------------------

    @Test
    void aTextComesBackAsItWasWritten() {
        Component styled = Component.literal("Named ").append(tr("item.minecraft.iron_sword"));
        CompoundTag data = new CompoundTag();
        TooltipData.putText(data, "t", styled, NO_REGISTRIES);
        assertEquals(styled, TooltipData.getText(data, "t", NO_REGISTRIES));
        assertNull(TooltipData.getText(data, "missing", NO_REGISTRIES));
    }

    @Test
    void anUnreadableTextIsAnEmptyTextNotNoItem() {
        CompoundTag data = new CompoundTag();
        data.putIntArray("t", new int[] { 1, 2, 3 });
        assertEquals(Component.empty(), TooltipData.getText(data, "t", NO_REGISTRIES));
    }

    // ---- Transvector Interface -----------------------------------------------------------------------------------------

    @Test
    void theInterfaceShowsTheBoundBlockAndItsPosition() {
        assertEquals(List.of(tr(TooltipKeys.CONNECTED, tr(TooltipKeys.NOTHING))), TransvectorInterfaceTooltip.linesFor(null, null));
        Component chest = tr("block.minecraft.chest");
        assertEquals(List.of(tr(TooltipKeys.CONNECTED, chest), Component.literal("x: 12 y: -61 z: -300")),
                TransvectorInterfaceTooltip.linesFor(chest, new BlockPos(12, -61, -300)));
    }

    @Test
    void theInterfaceTagNamesTheBlockByItsId() {
        CompoundTag data = new CompoundTag();
        data.putString(TransvectorInterfaceTooltip.TAG_BLOCK, "minecraft:chest");
        data.putInt(TransvectorInterfaceTooltip.TAG_X, 1);
        data.putInt(TransvectorInterfaceTooltip.TAG_Y, -64);
        data.putInt(TransvectorInterfaceTooltip.TAG_Z, 3);
        assertEquals(List.of(tr(TooltipKeys.CONNECTED, tr("block.minecraft.chest")), Component.literal("x: 1 y: -64 z: 3")),
                TinkererTooltips.INTERFACE.lines(data, NO_REGISTRIES));
        assertEquals(List.of(tr(TooltipKeys.CONNECTED, tr(TooltipKeys.NOTHING))), TinkererTooltips.INTERFACE.lines(new CompoundTag(), NO_REGISTRIES));
        assertEquals(Component.literal("othermod:machine"), TransvectorInterfaceTooltip.blockName("othermod:machine"));
        assertEquals(Component.literal("not an id!"), TransvectorInterfaceTooltip.blockName("not an id!"));
    }

    // ---- Thaumic Restorer ----------------------------------------------------------------------------------------------

    @Test
    void theRestorerShowsRepairingOrFinished() {
        Component sword = tr("item.minecraft.iron_sword");
        assertEquals(List.of(), RepairerTooltip.linesFor(null, true));
        assertEquals(List.of(tr(TooltipKeys.REPAIRING, sword)), RepairerTooltip.linesFor(sword, true));
        assertEquals(List.of(tr(TooltipKeys.FINISHED_REPAIRING, sword)), RepairerTooltip.linesFor(sword, false));
        CompoundTag data = new CompoundTag();
        TooltipData.putText(data, RepairerTooltip.TAG_ITEM, sword, NO_REGISTRIES);
        data.putBoolean(RepairerTooltip.TAG_DAMAGED, true);
        assertEquals(List.of(tr(TooltipKeys.REPAIRING, sword)), TinkererTooltips.REPAIRER.lines(data, NO_REGISTRIES));
        assertEquals(List.of(), TinkererTooltips.REPAIRER.lines(new CompoundTag(), NO_REGISTRIES));
    }

    // ---- Celestial Gateway ---------------------------------------------------------------------------------------------

    @Test
    void aLockedGatewaySaysItRefusesIncomingTeleports() {
        // The fix of the 1.7.10 provider, which showed allowIncoming for locked == true.
        assertEquals(List.of(tr(TooltipKeys.DISALLOW_INCOMING)), WarpGateTooltip.linesFor(true));
        assertEquals(List.of(tr(TooltipKeys.ALLOW_INCOMING)), WarpGateTooltip.linesFor(false));
        CompoundTag locked = new CompoundTag();
        locked.putBoolean(WarpGateTooltip.TAG_LOCKED, true);
        assertEquals(List.of(tr(TooltipKeys.DISALLOW_INCOMING)), TinkererTooltips.WARP_GATE.lines(locked, NO_REGISTRIES));
    }

    // ---- attractors ----------------------------------------------------------------------------------------------------

    @Test
    void theKineticAttractorShowsItsPolarityOnly() {
        assertEquals(List.of(tr(TooltipKeys.PULLING_MODE)), MagnetTooltip.linesFor(true, false, true, null));
        assertEquals(List.of(tr(TooltipKeys.PUSHING_MODE)), MagnetTooltip.linesFor(false, false, false, "minecraft:zombie"));
    }

    @Test
    void theCorporealAttractorWithoutAMouldShowsAdultsOrChildren() {
        assertEquals(List.of(tr(TooltipKeys.PULLING_MODE), tr(TooltipKeys.PULLING_ADULT)), MagnetTooltip.linesFor(true, true, true, null));
        assertEquals(List.of(tr(TooltipKeys.PULLING_MODE), tr(TooltipKeys.PULLING_CHILD)), MagnetTooltip.linesFor(true, true, false, null));
        assertEquals(List.of(tr(TooltipKeys.PUSHING_MODE), tr(TooltipKeys.PUSHING_ADULT)), MagnetTooltip.linesFor(false, true, true, null));
        assertEquals(List.of(tr(TooltipKeys.PUSHING_MODE), tr(TooltipKeys.PUSHING_CHILD)), MagnetTooltip.linesFor(false, true, false, null));
    }

    @Test
    void theCorporealAttractorWithAMouldShowsTheType() {
        Component zombie = tr("entity.minecraft.zombie");
        assertEquals(zombie, MagnetTooltip.patternName("minecraft:zombie"));
        assertEquals(List.of(tr(TooltipKeys.PULLING_MODE), tr(TooltipKeys.PULLING_ADULT_TYPE, zombie)), MagnetTooltip.linesFor(true, true, true, "minecraft:zombie"));
        assertEquals(List.of(tr(TooltipKeys.PULLING_MODE), tr(TooltipKeys.PULLING_CHILD_TYPE, zombie)), MagnetTooltip.linesFor(true, true, false, "minecraft:zombie"));
        assertEquals(List.of(tr(TooltipKeys.PUSHING_MODE), tr(TooltipKeys.PUSHING_ADULT_TYPE, zombie)), MagnetTooltip.linesFor(false, true, true, "minecraft:zombie"));
        assertEquals(List.of(tr(TooltipKeys.PUSHING_MODE), tr(TooltipKeys.PUSHING_CHILD_TYPE, zombie)), MagnetTooltip.linesFor(false, true, false, "minecraft:zombie"));
    }

    @Test
    void anUnsetMouldIsNoPatternAndAnUnknownTypeItsId() {
        assertEquals(tr(TooltipKeys.NO_PATTERN), MagnetTooltip.patternName(""));
        assertEquals(List.of(tr(TooltipKeys.PULLING_MODE), tr(TooltipKeys.PULLING_ADULT_TYPE, tr(TooltipKeys.NO_PATTERN))),
                MagnetTooltip.linesFor(true, true, true, ""));
        assertEquals(Component.literal("othermod:beast"), MagnetTooltip.patternName("othermod:beast"));
    }

    @Test
    void theMagnetTagTellsTheKineticFromTheCorporealAttractor() {
        CompoundTag kinetic = new CompoundTag();
        kinetic.putBoolean(MagnetTooltip.TAG_PULLING, false);
        assertEquals(List.of(tr(TooltipKeys.PUSHING_MODE)), TinkererTooltips.MAGNET.lines(kinetic, NO_REGISTRIES));
        CompoundTag mob = kinetic.copy();
        mob.putBoolean(MagnetTooltip.TAG_MOB, true);
        mob.putBoolean(MagnetTooltip.TAG_ADULT, false);
        assertEquals(List.of(tr(TooltipKeys.PUSHING_MODE), tr(TooltipKeys.PUSHING_CHILD)), TinkererTooltips.MAGNET.lines(mob, NO_REGISTRIES));
        mob.putString(MagnetTooltip.TAG_PATTERN, "minecraft:cow");
        assertEquals(List.of(tr(TooltipKeys.PUSHING_MODE), tr(TooltipKeys.PUSHING_CHILD_TYPE, tr("entity.minecraft.cow"))),
                TinkererTooltips.MAGNET.lines(mob, NO_REGISTRIES));
    }

    // ---- the shared server data ----------------------------------------------------------------------------------------

    @Test
    void eachTooltipReadsOnlyItsOwnEntryOfTheServerData() {
        CompoundTag gate = new CompoundTag();
        gate.putBoolean(WarpGateTooltip.TAG_LOCKED, true);
        CompoundTag serverData = new CompoundTag();
        serverData.put("thaumictinkerer:warp_gate", gate);
        serverData.putString("minecraft:other", "x");
        assertEquals(List.of(tr(TooltipKeys.DISALLOW_INCOMING)), TinkererTooltips.WARP_GATE.linesFromServerData(serverData, NO_REGISTRIES));
        for (BlockTooltip<?> tooltip : TinkererTooltips.ALL) {
            if (tooltip != TinkererTooltips.WARP_GATE) {
                assertEquals(List.of(), tooltip.linesFromServerData(serverData, NO_REGISTRIES), tooltip.name());
            }
            assertEquals(List.of(), tooltip.linesFromServerData(new CompoundTag(), NO_REGISTRIES), tooltip.name() + " without data");
        }
        // An entry of the wrong type (not a compound) is no data either.
        CompoundTag wrong = new CompoundTag();
        wrong.putString("thaumictinkerer:warp_gate", "locked");
        assertEquals(List.of(), TinkererTooltips.WARP_GATE.linesFromServerData(wrong, NO_REGISTRIES));
    }

    @Test
    void aTooltipWritesNothingForABlockEntityThatIsNotItsOwn() {
        CompoundTag serverData = new CompoundTag();
        assertFalse(TinkererTooltips.WARP_GATE.appendServerData(serverData, null, NO_REGISTRIES));
        assertTrue(serverData.isEmpty());
    }
}
