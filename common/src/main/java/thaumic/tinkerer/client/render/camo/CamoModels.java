/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.client.render.camo;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import thaumic.tinkerer.common.block.BlockCamo;
import thaumic.tinkerer.common.block.camo.CamoDeviceBlock;
import thaumic.tinkerer.common.lib.LibBlockNames;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * The loader-neutral part of the camouflage models (the Fabric and NeoForge glue wraps the baked models of the camouflage blocks and
 * asks these rules; {@code thaumic.tinkerer.common.hooks.CamouflageHooks} explains the data flow). Client only.
 *
 * <p>What the wrapped model draws for a camouflaged block is the model of the imitated state, at the block's position in the chunk
 * mesh, as the block renderer would draw that state there: the faces it culls against its neighbours ({@link #drawsFace}), its tint
 * at the position ({@link #tint}), its render type, and the light it gives itself ({@link #ownLight}: the renderer computes the light
 * of a face from the state it draws, and the camouflage block itself emits nothing). That is what the block entity renderers of the
 * earlier port drew every frame with {@code BlockRenderDispatcher.renderBatched(camouflage, pos, level, ...)}, and what 1.7.10 drew
 * from the imitated block's icons and colour multiplier.</p>
 */
public final class CamoModels {
    /** The camouflage blocks: the Ethereal Platform (SimpleBlocks) and the four camouflageable devices (TileBlocks). */
    public static final List<String> BLOCK_PATHS = List.of(LibBlockNames.PLATFORM, LibBlockNames.INTERFACE, LibBlockNames.DISLOCATOR,
            LibBlockNames.REMOTE_PLACER, LibBlockNames.GOLEMCONNECTOR);

    private CamoModels() {}

    /** Whether the models of this block are wrapped. */
    public static boolean isCamoBlock(ResourceLocation blockId) {
        return LibMisc.MOD_ID.equals(blockId.getNamespace()) && BLOCK_PATHS.contains(blockId.getPath());
    }

    /** The registered camouflage blocks (after registration). */
    public static List<Block> blocks() {
        List<Block> blocks = new ArrayList<>();
        for (String path : BLOCK_PATHS) blocks.add(TTRegistry.block(path));
        return blocks;
    }

    /**
     * Whether the block state is set to show a camouflage: the platform's {@code camouflaged}, the devices' {@code camo}. A state that
     * says so while its block entity has no camouflage (yet) draws nothing, as before: the block state reaches a client before the
     * block entity's data, and the block's own look must not flash in between.
     */
    public static boolean camouflaged(BlockState state) {
        if (state.hasProperty(BlockCamo.CAMOUFLAGED)) return state.getValue(BlockCamo.CAMOUFLAGED);
        return state.hasProperty(CamoDeviceBlock.CAMO) && state.getValue(CamoDeviceBlock.CAMO);
    }

    /** Whether the face {@code face} of the camouflage at {@code pos} is drawn: the culling of the camouflage state itself. */
    public static boolean drawsFace(BlockState camo, BlockGetter level, BlockPos pos, Direction face) {
        return Block.shouldRenderFace(camo, level, pos, face, pos.relative(face));
    }

    /** The colour of the camouflage's tint index {@code tintIndex} at the position (grass, leaves, water), or -1 for none. */
    public static int tint(BlockState camo, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        return Minecraft.getInstance().getBlockColors().getColor(camo, level, pos, tintIndex);
    }

    /**
     * A vertex colour with four 8-bit channels times a tint {@code 0xRRGGBB}, as the block renderers multiply a tinted quad's colour:
     * the channel at {@code redShift} by the red of the tint, and so on; the fourth channel (alpha) is kept. {@code redShift} is 16
     * for ARGB colours (Fabric's quads) and 0 for ABGR colours (the vertex data of a baked quad). A tint of -1 changes nothing.
     */
    public static int multiply(int color, int tint, int redShift) {
        if (tint == -1) return color;
        int blueShift = 16 - redShift;
        int red = mul((color >> redShift) & 0xFF, (tint >> 16) & 0xFF);
        int green = mul((color >> 8) & 0xFF, (tint >> 8) & 0xFF);
        int blue = mul((color >> blueShift) & 0xFF, tint & 0xFF);
        return (color & 0xFF000000) | red << redShift | green << 8 | blue << blueShift;
    }

    private static int mul(int a, int b) {
        return (a * b + 127) / 255;
    }

    /**
     * The light the camouflage gives its own faces, as a packed lightmap value to be combined with the light of the position by
     * {@link #maxLight}: full brightness for a state that renders emissive (magma), its light emission as block light otherwise
     * (glowstone: 15), 0 for a state that emits nothing. The block renderer folds the drawn state's emission into the light of every
     * face ({@code LevelRenderer.getLightColor}); the camouflage block itself emits nothing (its light in the world is not changed).
     */
    public static int ownLight(BlockState camo, BlockGetter level, BlockPos pos) {
        if (camo.emissiveRendering(level, pos)) return LightTexture.FULL_BRIGHT;
        int emission = camo.getLightEmission();
        return emission <= 0 ? 0 : LightTexture.pack(emission, 0);
    }

    /** The larger block light and the larger sky light of two packed lightmap values. */
    public static int maxLight(int a, int b) {
        int block = Math.max(a & 0xFFFF, b & 0xFFFF);
        int sky = Math.max((a >>> 16) & 0xFFFF, (b >>> 16) & 0xFFFF);
        return block | sky << 16;
    }

    /** Smooth lighting is used for the camouflage only when it emits no light, as the block renderer decides for the state it draws. */
    public static boolean allowsAmbientOcclusion(BlockState camo) {
        return camo.getLightEmission() == 0;
    }
}
