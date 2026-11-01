/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.block.tile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.ThaumcraftFX;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumic.tinkerer.common.content.Machines;
import thaumic.tinkerer.common.core.helper.EnumMobAspect;
import thaumic.tinkerer.common.item.ItemMobAspect;

/**
 * The Tablet of Necromancy. Every 300 ticks (unless it is powered by redstone) it looks for arcane pedestals around it
 * (within five blocks, at its own height) that carry soul aspects; three of them whose aspects are the aspects of a
 * mob summon that mob. Plain soul aspects are used up; infused ones are kept but the tablet works only every 1200
 * ticks, and remembers them.
 *
 * <p>1.7.10 scanned the square of every radius from 1 to 5 anew and tried every ordered triple of the pedestals it found,
 * looking each pedestal up in the world again for every triple. The port reads the square of radius 5 once per cycle
 * ({@link #scan}: one look-up per position, the pedestal block resolved once), takes the pedestals of each radius from that
 * list in the order 1.7.10 scanned them (x, then z), and tries each unordered triple {@code i < j < k} once
 * ({@link #firstMatch}). The match does not depend on the order within a triple (the aspects are compared as a set), so the
 * first unordered triple is the first ordered triple of the original, with its pedestals in the same order: the same mob and
 * the same trails.</p>
 */
public class TileSummon extends BlockEntity {

    /** The largest square the tablet searches: radius 1 to 5 around it, as 1.7.10 ({@code radius < 6}). */
    public static final int MAX_RADIUS = 5;

    /** A pedestal that carries a soul aspect, as the scan of a cycle found it. */
    public record SoulPedestal(BlockPos pos, Container container, Aspect aspect) {}

    /** Three pedestals whose aspects are those of a row: the row and the pedestals in the order of the trails. */
    public record Match(EnumMobAspect recipe, SoulPedestal first, SoulPedestal second, SoulPedestal third) {
        public List<SoulPedestal> pedestals() {
            return List.of(first, second, third);
        }
    }

    public TileSummon(BlockPos pos, BlockState state) {
        super(Machines.TILE_SUMMON.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TileSummon tablet) {
        tablet.update((ServerLevel) level);
    }

    /**
     * The look-up of the pedestals of a level: the container of the arcane pedestal at a position (1.7.10:
     * {@code TilePedestal}), or null. The pedestal block is resolved once, when the look-up is made.
     */
    public static Function<BlockPos, Container> pedestalLookup(Level level) {
        Block pedestal = BlocksTC.get("arcane_pedestal");
        return pos -> {
            if (pedestal == null || !level.getBlockState(pos).is(pedestal)) return null;
            return level.getBlockEntity(pos) instanceof Container container ? container : null;
        };
    }

    /**
     * The pedestals with a soul aspect in the square of radius {@link #MAX_RADIUS} around {@code tablet}, at its height, in
     * the order 1.7.10 scanned a square (x, then z). Each position is looked up once.
     */
    public static List<SoulPedestal> scan(BlockPos tablet, Function<BlockPos, Container> pedestalAt) {
        List<SoulPedestal> found = new ArrayList<>();
        for (int x = tablet.getX() - MAX_RADIUS; x <= tablet.getX() + MAX_RADIUS; x++) {
            for (int z = tablet.getZ() - MAX_RADIUS; z <= tablet.getZ() + MAX_RADIUS; z++) {
                BlockPos at = new BlockPos(x, tablet.getY(), z);
                Container pedestal = pedestalAt.apply(at);
                if (pedestal == null) continue;
                Aspect aspect = ItemMobAspect.getAspect(pedestal.getItem(0));
                if (aspect != null) found.add(new SoulPedestal(at, pedestal, aspect));
            }
        }
        return found;
    }

    /**
     * The pedestals of the square of one radius, in the order of the scan: the order 1.7.10 found them in when it scanned
     * that square (a sub-square of an x-then-z scan keeps the order).
     */
    public static List<SoulPedestal> within(List<SoulPedestal> scanned, BlockPos tablet, int radius) {
        List<SoulPedestal> inside = new ArrayList<>();
        for (SoulPedestal pedestal : scanned) {
            if (Math.abs(pedestal.pos().getX() - tablet.getX()) <= radius
                    && Math.abs(pedestal.pos().getZ() - tablet.getZ()) <= radius) {
                inside.add(pedestal);
            }
        }
        return inside;
    }

    /**
     * The first triple of the pedestals {@code i < j < k} whose aspects are, as a set, the aspects of a row, with the first
     * row that matches; null when none does.
     */
    @Nullable
    public static Match firstMatch(List<SoulPedestal> pedestals) {
        int n = pedestals.size();
        List<Aspect> aspects = new ArrayList<>(3);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    aspects.clear();
                    aspects.add(pedestals.get(i).aspect());
                    aspects.add(pedestals.get(j).aspect());
                    aspects.add(pedestals.get(k).aspect());
                    for (EnumMobAspect recipe : EnumMobAspect.values()) {
                        if (recipe.matchesAspectSet(aspects)) {
                            return new Match(recipe, pedestals.get(i), pedestals.get(j), pedestals.get(k));
                        }
                    }
                }
            }
        }
        return null;
    }

    /** The pedestals with soul aspects around this tablet, as one cycle sees them ({@link #scan}). */
    public List<SoulPedestal> soulPedestals(Level level) {
        return scan(worldPosition, pedestalLookup(level));
    }

    /** What the next cycle would summon from (the smallest square with a match, then {@link #firstMatch}), or null. */
    @Nullable
    public Match findMatch(Level level) {
        return findMatch(soulPedestals(level));
    }

    @Nullable
    private Match findMatch(List<SoulPedestal> scanned) {
        int previous = 0;
        for (int radius = 1; radius <= MAX_RADIUS; radius++) {
            List<SoulPedestal> pedestals = within(scanned, worldPosition, radius);
            // the same pedestals as the smaller square, which had no match
            if (pedestals.size() == previous) continue;
            previous = pedestals.size();
            Match match = firstMatch(pedestals);
            if (match != null) return match;
        }
        return null;
    }

    private void update(ServerLevel level) {
        if (level.getGameTime() % 300 != 0 || level.hasNeighborSignal(worldPosition)) {
            return;
        }

        summon(level, level.getGameTime());
    }

    /**
     * One work cycle of the tablet, as it runs every 300 ticks: looks for three pedestals whose soul aspects are the
     * aspects of a mob and summons it. {@code gameTime} is the time of the world; infused soul aspects work only when
     * it is a multiple of 1200.
     *
     * @return true when a mob was summoned (or an infused set was recognised and remembered)
     */
    public boolean summon(ServerLevel level, long gameTime) {
        Match match = findMatch(level);
        if (match == null) return false;

        List<SoulPedestal> pedestals = match.pedestals();
        boolean isInfused = true;
        for (SoulPedestal pedestal : pedestals) isInfused &= ItemMobAspect.isInfused(pedestal.container().getItem(0));

        if (isInfused && gameTime % 1200 != 0) return false;

        if (!isInfused) {
            for (SoulPedestal pedestal : pedestals) clear(level, pedestal);
        }

        GlobalPos here = GlobalPos.of(level.dimension(), worldPosition);
        if (!isInfused || lastUsedHere(pedestals, here)) {
            spawn(level, match.recipe());
            for (SoulPedestal pedestal : pedestals) ThaumcraftFX.essentiaTrail(level, worldPosition, pedestal.pos(), pedestal.aspect());
        }
        if (isInfused) {
            for (SoulPedestal pedestal : pedestals) mark(level, pedestal, here);
        }

        return true;
    }

    /** The mob appears on top of the tablet, as {@code spawn.setLocationAndAngles(x + .5, y + 1, z + .5, 0, 0)}. */
    private void spawn(ServerLevel level, EnumMobAspect recipe) {
        Mob spawn = recipe.createEntity(level);
        if (spawn == null) return;
        spawn.moveTo(worldPosition.getX() + .5, worldPosition.getY() + 1, worldPosition.getZ() + .5, 0, 0);
        // onSpawnWithEgg(null): the equipment and variants of a mob that comes out of a spawn egg (a wither skeleton
        // gets its stone sword here, a skeleton its bow).
        spawn.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn.blockPosition()), MobSpawnType.SPAWN_EGG, null);
        recipe.finalizeSummoned(spawn);
        level.addFreshEntityWithPassengers(spawn);
        spawn.playAmbientSound();
    }

    private static boolean lastUsedHere(List<SoulPedestal> pedestals, GlobalPos tablet) {
        for (SoulPedestal pedestal : pedestals) {
            if (!ItemMobAspect.lastUsedTabletMatches(pedestal.container().getItem(0), tablet)) return false;
        }
        return true;
    }

    private static void clear(ServerLevel level, SoulPedestal pedestal) {
        pedestal.container().setItem(0, ItemStack.EMPTY);
        syncPedestal(level, pedestal.pos());
    }

    /** An infused soul aspect remembers this tablet (with its dimension) as the last one that used it. */
    private static void mark(ServerLevel level, SoulPedestal pedestal, GlobalPos tablet) {
        ItemStack stack = pedestal.container().getItem(0);
        if (stack.isEmpty()) return;
        ItemMobAspect.markLastUsedTablet(stack, tablet);
        pedestal.container().setChanged();
        syncPedestal(level, pedestal.pos());
    }

    private static void syncPedestal(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }
}
