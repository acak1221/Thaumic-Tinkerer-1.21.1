/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.client.render.camo.CamoModels;
import thaumic.tinkerer.common.hooks.CamouflageHooks;

/**
 * The camouflage blocks' models on NeoForge: every block-state model of the Ethereal Platform and the four camouflageable devices is
 * wrapped in {@link ModelEvent.ModifyBakingResult}. While the state says {@code camouflaged}/{@code camo} the wrapper answers with the
 * model of the imitated state (its quads, its render types, its smooth lighting); otherwise with the block's own model. The imitated
 * state is the block entity's model data ({@link CamoModelData}), refreshed through {@code requestModelDataUpdate()} whenever the
 * client reads another camouflage ({@link CamouflageHooks}).
 *
 * <p>The chunk builder asks the model for its data with the region of the section ({@code getModelData(level, pos, state, data)}) right
 * before it draws the block; the wrapper keeps the region and the position for that one block ({@link Context}), so that the quads it
 * hands out are what the block renderer would have drawn for the imitated state there: the faces that state culls against its
 * neighbours, its tint (baked into the vertex colours: the renderer would tint with the camouflage block's colour, which has none) and
 * its own light for a state that emits light. The rules are {@link CamoModels}; Fabric does the same in {@code FabricCamoModels}.</p>
 */
public final class NeoForgeCamoModels {
    /** The camouflage at one position for one draw of the chunk builder. */
    private static final ModelProperty<Context> CONTEXT = new ModelProperty<>();

    private NeoForgeCamoModels() {}

    /** From the client side of the mod constructor. */
    public static void register(IEventBus modBus) {
        modBus.addListener(NeoForgeCamoModels::wrap);
        CamouflageHooks.setListener(BlockEntity::requestModelDataUpdate);
    }

    private static void wrap(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Block block : CamoModels.blocks()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation id = BlockModelShaper.stateToModelLocation(state);
                BakedModel own = models.get(id);
                if (own != null && !(own instanceof CamoModel)) models.put(id, new CamoModel(own));
            }
        }
    }

    private static BakedModel modelOf(BlockState camo) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(camo);
    }

    /** The wrapper of one block-state model of a camouflage block. */
    static final class CamoModel extends BakedModelWrapper<BakedModel> {
        CamoModel(BakedModel own) {
            super(own);
        }

        @Nullable
        private static BlockState camo(@Nullable BlockState state, ModelData data) {
            return state != null && CamoModels.camouflaged(state) ? data.get(CamoModelData.CAMO) : null;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            if (!CamoModels.camouflaged(state)) return super.getModelData(level, pos, state, data);
            BlockState camo = data.get(CamoModelData.CAMO);
            if (camo == null) return data;
            return data.derive().with(CONTEXT, new Context(camo, level, pos.immutable())).build();
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            if (!CamoModels.camouflaged(state)) return super.getRenderTypes(state, rand, data);
            BlockState camo = data.get(CamoModelData.CAMO);
            // camouflaged, but the block entity's data is not here (yet): nothing, as the block entity renderer drew nothing
            return camo == null ? ChunkRenderTypeSet.none() : modelOf(camo).getRenderTypes(camo, rand, ModelData.EMPTY);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                @Nullable RenderType renderType) {
            if (state == null || !CamoModels.camouflaged(state)) return super.getQuads(state, side, rand, data, renderType);
            BlockState camo = data.get(CamoModelData.CAMO);
            if (camo == null) return List.of();
            Context context = data.get(CONTEXT);
            if (side != null && context != null && !context.drawsFace(side)) return List.of();
            List<BakedQuad> quads = modelOf(camo).getQuads(camo, side, rand, ModelData.EMPTY, renderType);
            return context == null ? quads : context.adapt(quads);
        }

        @Override
        public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
            BlockState camo = camo(state, data);
            if (camo == null) return super.useAmbientOcclusion(state, data, renderType);
            if (!CamoModels.allowsAmbientOcclusion(camo)) return TriState.FALSE;
            return modelOf(camo).useAmbientOcclusion(camo, ModelData.EMPTY, renderType);
        }
    }

    /** One camouflage at one position, for one draw of the chunk builder (one thread). */
    private static final class Context {
        private final BlockState camo;
        private final BlockAndTintGetter level;
        private final BlockPos pos;
        private final int ownLight;
        /** Bit d: face d tested; bit 6 + d: face d drawn. */
        private int faces;
        private int lastTintIndex = Integer.MIN_VALUE;
        private int lastTint;

        Context(BlockState camo, BlockAndTintGetter level, BlockPos pos) {
            this.camo = camo;
            this.level = level;
            this.pos = pos;
            this.ownLight = CamoModels.ownLight(camo, level, pos);
        }

        boolean drawsFace(Direction face) {
            int tested = 1 << face.get3DDataValue();
            int drawn = tested << 6;
            if ((faces & tested) == 0) {
                faces |= tested;
                if (CamoModels.drawsFace(camo, level, pos, face)) faces |= drawn;
            }
            return (faces & drawn) != 0;
        }

        /** The quads with the tint of the position in their colours and the camouflage's own light in their lightmap. */
        List<BakedQuad> adapt(List<BakedQuad> quads) {
            List<BakedQuad> adapted = null;
            for (int i = 0; i < quads.size(); i++) {
                BakedQuad quad = quads.get(i);
                BakedQuad changed = adapt(quad);
                if (changed != quad && adapted == null) {
                    adapted = new ArrayList<>(quads.size());
                    adapted.addAll(quads.subList(0, i));
                }
                if (adapted != null) adapted.add(changed);
            }
            return adapted == null ? quads : adapted;
        }

        private BakedQuad adapt(BakedQuad quad) {
            if (!quad.isTinted() && ownLight == 0) return quad;
            int[] vertices = quad.getVertices().clone();
            int tint = quad.isTinted() ? tint(quad.getTintIndex()) : -1;
            for (int v = 0; v < 4; v++) {
                int base = v * IQuadTransformer.STRIDE;
                vertices[base + IQuadTransformer.COLOR] = CamoModels.multiply(vertices[base + IQuadTransformer.COLOR], tint, 0);
                if (ownLight != 0) {
                    vertices[base + IQuadTransformer.UV2] = CamoModels.maxLight(vertices[base + IQuadTransformer.UV2], ownLight);
                }
            }
            return new BakedQuad(vertices, -1, quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
        }

        private int tint(int tintIndex) {
            if (tintIndex != lastTintIndex) {
                lastTintIndex = tintIndex;
                lastTint = CamoModels.tint(camo, level, pos, tintIndex);
            }
            return lastTint;
        }
    }
}
