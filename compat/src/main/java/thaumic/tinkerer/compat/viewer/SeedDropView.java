/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: written for EMI and JEI after what the 1.7.10 NEI handler
 * {@code NEIInfusedSeedRecipeHandler} showed, not from its code). Distributed under the same licence as the add-on (CC BY-NC-SA 3.0,
 * see NOTICE.md).
 */
package thaumic.tinkerer.compat.viewer;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;

/**
 * One page of the infused crops: the seed of an aspect and everything a ripe crop of that aspect can yield.
 *
 * @param id          the page id ({@link ViewerIds#seedDrops})
 * @param aspect      the aspect of the seed and the crop
 * @param seed        Infused Seeds of the aspect (no tendencies), the input of the page
 * @param drops       the weighted list of the aspect, one entry per entry of {@code AspectCropLootManager} in its order
 *                    (an item that is listed twice is shown twice, as 1.7.10 did)
 * @param totalWeight the sum of the weights, the denominator of every chance
 */
public record SeedDropView(ResourceLocation id, Aspect aspect, ItemStack seed, List<Drop> drops, int totalWeight) {

    /**
     * One possible drop.
     *
     * @param stack  the stack, with its count
     * @param weight its weight in the list
     * @param chance its chance per draw in percent: {@code weight * 100 / totalWeight}
     */
    public record Drop(ItemStack stack, int weight, double chance) {}

    public SeedDropView {
        drops = List.copyOf(drops);
    }

    /** The drops split into the pages of {@link ViewerLayout#DROPS_PER_PAGE} (EMI). */
    public List<List<Drop>> pages() {
        return DropChances.pages(drops, ViewerLayout.DROPS_PER_PAGE);
    }
}
