/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.common.dim;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * The ore clusters of the Bedrock dimension as a world-generation feature, {@code thaumictinkerer:bedrock_ore_clusters} (1.7.10:
 * {@code ChunkProviderBedrock.populate} with its own {@code java.util.Random}). The data pack holds its configured feature
 * ({@code worldgen/configured_feature/bedrock_ore_clusters.json}, no configuration) and its placed feature
 * ({@code worldgen/placed_feature/bedrock_ore_clusters.json}, at the chunk's origin, with the biome filter), and the Bedrock biome
 * lists it in the underground-ores step ({@code GenerationStep.Decoration.UNDERGROUND_ORES}). The game runs it once per chunk when
 * the chunk reaches the features step, with the {@code RandomSource} it seeds for that feature from the world seed, the chunk and
 * the feature's index and step ({@code WorldgenRandom.setFeatureSeed}), like every vanilla feature.
 *
 * <p>One run is what 1.7.10's {@code populate} did for a chunk: {@code bedrockDimension.oreDensity} clusters (a config option, so
 * not a {@code count} placement of the data), each one ore picked by weight from the valid ores ({@link EnumOreFrequency}: the ore
 * tags, without what {@code bedrockDimension.oreBlacklist} names) and 200 veins in one column ({@link OreClusterGenerator}). A
 * vein replaces bedrock only and writes only into the chunk and the one east and south of it ({@link OreClusterGenerator#reaches}),
 * which the features step allows (it may write one chunk around the chunk it decorates).</p>
 *
 * <p>The type is registered whether or not the Kami content is enabled (as the chunk generator type): the biome of the dimension
 * file names the placed feature, and a data pack whose features cannot be decoded stops the world from loading.</p>
 */
public final class BedrockOreClustersFeature extends Feature<NoneFeatureConfiguration> {
    /** The registry path of the feature type, of its configured feature and of its placed feature. */
    public static final String NAME = "bedrock_ore_clusters";
    /** The placed feature that the Bedrock biome lists. */
    public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, LibMisc.id(NAME));

    public BedrockOreClustersFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** The clusters of the chunk that holds the origin; true when at least one ore block was placed. */
    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        int density = OreClusterGenerator.density();
        if (density <= 0) return false;
        WorldGenLevel level = context.level();
        int chunkX = SectionPos.blockToSectionCoord(context.origin().getX());
        int chunkZ = SectionPos.blockToSectionCoord(context.origin().getZ());
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        String[] blacklist = OreClusterGenerator.blacklist();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean[] placed = { false };
        OreClusterGenerator.generate(context.random(), chunkX, chunkZ, density, random -> EnumOreFrequency.getRandomOre(random, blacklist),
                new OreClusterGenerator.ClusterWorld<BlockState>() {
                    @Override
                    public boolean isReplaceable(int x, int y, int z) {
                        if (y < minY || y >= maxY || !OreClusterGenerator.reaches(chunkX, chunkZ, x, z)) return false;
                        return level.getBlockState(pos.set(x, y, z)).is(Blocks.BEDROCK);
                    }

                    @Override
                    public void place(int x, int y, int z, BlockState ore) {
                        // 1.7.10: flag 2 (tell the clients); in a generation region there are no neighbours to update anyway.
                        if (level.setBlock(pos.set(x, y, z), ore, Block.UPDATE_CLIENTS)) placed[0] = true;
                    }
                });
        return placed[0];
    }
}
