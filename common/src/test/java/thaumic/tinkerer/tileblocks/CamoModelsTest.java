package thaumic.tinkerer.tileblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thaumic.tinkerer.client.render.camo.CamoModels;
import thaumic.tinkerer.common.block.BlockCamo;
import thaumic.tinkerer.common.block.camo.CamoDeviceBlock;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * Test-only code of the port (no 1.7.10 counterpart), CC BY-NC-SA 3.0 like the add-on.
 *
 * <p>The loader-neutral rules of the camouflage models ({@link CamoModels}), which the Fabric and NeoForge wrappers apply to the
 * quads of the imitated state: which faces it culls against its neighbours (the culling of the state itself, as the block entity
 * renderer had it), how a tint multiplies a vertex colour in either colour order, the light a glowing camouflage gives its own faces,
 * and which block states show a camouflage. What the chunk renderer draws with them is the client smoke's camouflage phase.</p>
 */
class CamoModelsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A few blocks around the origin, air elsewhere. */
    private record World(Map<BlockPos, BlockState> blocks) implements BlockGetter {
        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    private static World around(Direction side, BlockState neighbour) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(BlockPos.ZERO.relative(side), neighbour);
        return new World(blocks);
    }

    @Test
    void theCamouflageCullsItsFacesAsTheImitatedBlockWould() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState ice = Blocks.ICE.defaultBlockState();
        assertTrue(CamoModels.drawsFace(stone, around(Direction.UP, Blocks.AIR.defaultBlockState()), BlockPos.ZERO, Direction.UP), "a face to the air is drawn");
        assertFalse(CamoModels.drawsFace(stone, around(Direction.NORTH, stone), BlockPos.ZERO, Direction.NORTH), "a face against stone is not");
        assertFalse(CamoModels.drawsFace(glass, around(Direction.EAST, glass), BlockPos.ZERO, Direction.EAST), "glass hides the face between two glass blocks");
        assertFalse(CamoModels.drawsFace(ice, around(Direction.DOWN, ice), BlockPos.ZERO, Direction.DOWN), "and ice between two ice blocks");
        assertTrue(CamoModels.drawsFace(ice, around(Direction.DOWN, glass), BlockPos.ZERO, Direction.DOWN), "but ice shows its face to glass");
        assertTrue(CamoModels.drawsFace(stone, around(Direction.WEST, Blocks.OAK_LEAVES.defaultBlockState()), BlockPos.ZERO, Direction.WEST),
                "stone shows its face to leaves (they occlude nothing)");
        // the faces only the named side is asked for
        assertTrue(CamoModels.drawsFace(stone, around(Direction.NORTH, stone), BlockPos.ZERO, Direction.SOUTH), "the other faces are drawn");
    }

    @Test
    void aTintMultipliesTheColourChannelByChannelInEitherOrder() {
        int grass = 0x79C05A;
        // ARGB (Fabric's quads): alpha kept, white times the tint is the tint
        assertEquals(0xFF79C05A, CamoModels.multiply(0xFFFFFFFF, grass, 16));
        assertEquals(0x80000000, CamoModels.multiply(0x80000000, grass, 16), "black stays black, alpha is kept");
        int half = CamoModels.multiply(0xFF808080, grass, 16);
        assertEquals(0x3D, (half >> 16) & 0xFF, "red 0x80 * 0x79 / 255");
        assertEquals(0x60, (half >> 8) & 0xFF, "green 0x80 * 0xC0 / 255");
        assertEquals(0x2D, half & 0xFF, "blue 0x80 * 0x5A / 255");
        // ABGR (the vertex data of a baked quad): red in the lowest byte
        assertEquals(0xFF5AC079, CamoModels.multiply(0xFFFFFFFF, grass, 0));
        assertEquals(0x12345678, CamoModels.multiply(0x12345678, -1, 16), "no tint changes nothing");
        assertEquals(0x12345678, CamoModels.multiply(0x12345678, -1, 0));
    }

    @Test
    void aGlowingCamouflageLightsItsOwnFaces() {
        assertEquals(0, CamoModels.ownLight(Blocks.STONE.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        assertEquals(LightTexture.pack(15, 0), CamoModels.ownLight(Blocks.GLOWSTONE.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        assertEquals(LightTexture.FULL_BRIGHT, CamoModels.ownLight(Blocks.MAGMA_BLOCK.defaultBlockState(), EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                "magma renders emissive");
        assertEquals(LightTexture.pack(15, 12), CamoModels.maxLight(LightTexture.pack(15, 0), LightTexture.pack(3, 12)), "block and sky light each the larger");
        assertFalse(CamoModels.allowsAmbientOcclusion(Blocks.GLOWSTONE.defaultBlockState()), "no smooth lighting for a glowing block");
        assertTrue(CamoModels.allowsAmbientOcclusion(Blocks.GRASS_BLOCK.defaultBlockState()));
    }

    @Test
    void theStatesOfBothFamiliesSayWhetherTheyShowACamouflage() {
        BlockState platform = new StateDefinition.Builder<Block, BlockState>(Blocks.OAK_PLANKS).add(BlockCamo.CAMOUFLAGED)
                .create(Block::defaultBlockState, BlockState::new).any();
        BlockState device = new StateDefinition.Builder<Block, BlockState>(Blocks.OAK_PLANKS).add(CamoDeviceBlock.CAMO)
                .create(Block::defaultBlockState, BlockState::new).any();
        assertFalse(CamoModels.camouflaged(platform.setValue(BlockCamo.CAMOUFLAGED, false)));
        assertTrue(CamoModels.camouflaged(platform.setValue(BlockCamo.CAMOUFLAGED, true)));
        assertFalse(CamoModels.camouflaged(device.setValue(CamoDeviceBlock.CAMO, false)));
        assertTrue(CamoModels.camouflaged(device.setValue(CamoDeviceBlock.CAMO, true)));
        assertFalse(CamoModels.camouflaged(Blocks.STONE.defaultBlockState()), "a block without the property");
    }

    @Test
    void theFiveCamouflageBlocksAreWrapped() {
        assertEquals(List.of("platform", "interface", "dislocator", "remote_placer", "golem_connector"), CamoModels.BLOCK_PATHS);
        assertTrue(CamoModels.isCamoBlock(LibMisc.id("platform")));
        assertTrue(CamoModels.isCamoBlock(LibMisc.id("golem_connector")));
        assertFalse(CamoModels.isCamoBlock(LibMisc.id("mobilizer")), "the locomotive is no camouflage block");
        assertFalse(CamoModels.isCamoBlock(net.minecraft.resources.ResourceLocation.withDefaultNamespace("platform")), "another namespace");
    }
}
