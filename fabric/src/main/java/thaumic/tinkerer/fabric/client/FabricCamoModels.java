/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md): Fabric glue. Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.fabric.client;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import thaumic.tinkerer.client.render.camo.CamoModels;

/**
 * The camouflage blocks' models on Fabric: every block-state model of the Ethereal Platform and the four camouflageable devices is
 * wrapped after baking ({@code fabric-model-loading-api-v1}) in a model of the Fabric Renderer API. While the state says
 * {@code camouflaged}/{@code camo} the wrapper emits the model of the imitated state into the chunk mesh; otherwise the block's own
 * model. The imitated state comes from the render data of the block entity ({@code RenderDataBlockEntity}, added by
 * {@code thaumic.tinkerer.fabric.mixin.CamoRenderDataMixin}), which Fabric API copies on the client thread when it prepares the chunk
 * region, so the chunk builder threads never read the block entity itself.
 *
 * <p>Every quad of the camouflage passes a transform that makes it what the block renderer would have drawn for the imitated state at
 * this position, which Indigo would otherwise decide from the camouflage block's own state: the faces the imitated state culls
 * against its neighbours, its tint (Indigo would tint with the camouflage block's colour, which has none), its render type
 * (cut-out glass and leaves, translucent ice), no smooth lighting and its own light for a state that emits light. The rules are
 * {@link CamoModels}.</p>
 */
public final class FabricCamoModels {
    private FabricCamoModels() {}

    /** From the client entry point, before the first model bake. */
    public static void install() {
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
            ModelResourceLocation id = context.topLevelId();
            if (model == null || id == null || ModelResourceLocation.INVENTORY_VARIANT.equals(id.variant()) || !CamoModels.isCamoBlock(id.id())) {
                return model;
            }
            return model instanceof CamoModel ? model : new CamoModel(model);
        }));
    }

    /** The wrapper of one block-state model of a camouflage block. */
    static final class CamoModel extends ForwardingBakedModel {
        CamoModel(BakedModel own) {
            this.wrapped = own;
        }

        @Override
        public boolean isVanillaAdapter() {
            return false;
        }

        @Override
        public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext context) {
            if (!CamoModels.camouflaged(state)) {
                super.emitBlockQuads(view, state, pos, random, context);
                return;
            }
            // camouflaged, but the block entity's data is not here (yet): nothing, as the block entity renderer drew nothing
            if (!(view.getBlockEntityRenderData(pos) instanceof BlockState camo)) return;
            BakedModel camoModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(camo);
            context.pushTransform(new CamoQuads(view, pos, camo));
            try {
                camoModel.emitBlockQuads(view, camo, pos, random, context);
            } finally {
                context.popTransform();
            }
        }
    }

    /** The transform of one camouflage at one position, for one call of the renderer (one thread). */
    private static final class CamoQuads implements RenderContext.QuadTransform {
        private final BlockAndTintGetter view;
        private final BlockPos pos;
        private final BlockState camo;
        private final BlendMode blendMode;
        private final boolean ambientOcclusion;
        private final boolean emissive;
        private final int ownLight;
        /** Bit d: face d tested; bit 6 + d: face d drawn. */
        private int faces;
        private int lastTintIndex = Integer.MIN_VALUE;
        private int lastTint;
        private RenderMaterial lastIn;
        private RenderMaterial lastOut;

        CamoQuads(BlockAndTintGetter view, BlockPos pos, BlockState camo) {
            this.view = view;
            this.pos = pos;
            this.camo = camo;
            this.blendMode = BlendMode.fromRenderLayer(ItemBlockRenderTypes.getChunkRenderType(camo));
            this.ambientOcclusion = CamoModels.allowsAmbientOcclusion(camo);
            int light = CamoModels.ownLight(camo, view, pos);
            this.emissive = light == net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            this.ownLight = emissive ? 0 : light;
        }

        @Override
        public boolean transform(MutableQuadView quad) {
            Direction cullFace = quad.cullFace();
            if (cullFace != null && !drawsFace(cullFace)) return false;
            int tintIndex = quad.colorIndex();
            if (tintIndex != -1) {
                int tint = tint(tintIndex);
                for (int i = 0; i < 4; i++) quad.color(i, CamoModels.multiply(quad.color(i), tint, 16));
                quad.colorIndex(-1);
            }
            if (ownLight != 0) {
                for (int i = 0; i < 4; i++) quad.lightmap(i, CamoModels.maxLight(quad.lightmap(i), ownLight));
            }
            quad.material(material(quad.material()));
            return true;
        }

        private boolean drawsFace(Direction face) {
            int tested = 1 << face.get3DDataValue();
            int drawn = tested << 6;
            if ((faces & tested) == 0) {
                faces |= tested;
                if (CamoModels.drawsFace(camo, view, pos, face)) faces |= drawn;
            }
            return (faces & drawn) != 0;
        }

        private int tint(int tintIndex) {
            if (tintIndex != lastTintIndex) {
                lastTintIndex = tintIndex;
                lastTint = CamoModels.tint(camo, view, pos, tintIndex);
            }
            return lastTint;
        }

        /** The quad's material with the camouflage's render type, smooth lighting and emissivity. */
        private RenderMaterial material(RenderMaterial in) {
            if (in == lastIn) return lastOut;
            MaterialFinder finder = RendererAccess.INSTANCE.getRenderer().materialFinder().copyFrom(in);
            if (in.blendMode() == BlendMode.DEFAULT) finder.blendMode(blendMode);
            if (!ambientOcclusion) finder.ambientOcclusion(TriState.FALSE);
            if (emissive) finder.emissive(true);
            lastIn = in;
            lastOut = finder.find();
            return lastOut;
        }
    }
}
