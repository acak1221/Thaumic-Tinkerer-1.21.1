/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart: written for EMI and JEI after what the 1.7.10 NEI handler
 * {@code NEINecromancyHandler} showed, not from its code). Distributed under the same licence as the add-on (CC BY-NC-SA 3.0,
 * see NOTICE.md).
 */
package thaumic.tinkerer.compat.viewer;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumic.tinkerer.common.core.helper.EnumMobAspect;

/**
 * One page of the Tablet of Necromancy: three soul aspects on the pedestals around the tablet summon one mob.
 *
 * @param id             the page id ({@link ViewerIds#necromancy})
 * @param mob            the row of the necromancy table
 * @param entityTypeId   the registry id of the summoned entity type
 * @param aspects        the three aspects of the row, in the order of the table ({@code EnumMobAspect.aspects})
 * @param essences       per aspect, in that order, the soul aspects the tablet accepts for it: the basic one first (the
 *                       stack 1.7.10's NEI page showed), then the condensed and the infused one, which the tablet takes as
 *                       well ({@code TileSummon} asks only for an {@code ItemMobAspect} of the aspect)
 * @param mobDisplay     the output: a Mob Display of the mob
 * @param catalysts      what the page names as its workstations: the Cursed Spirit's Blade (it reaps the soul aspects) and
 *                       the Tablet of Necromancy, in the order of the 1.7.10 NEI catalysts
 */
public record NecromancyView(ResourceLocation id, EnumMobAspect mob, ResourceLocation entityTypeId, List<Aspect> aspects,
        List<List<ItemStack>> essences, ItemStack mobDisplay, List<ItemStack> catalysts) {

    public NecromancyView {
        aspects = List.copyOf(aspects);
        essences = essences.stream().map(List::copyOf).toList();
        catalysts = List.copyOf(catalysts);
        if (aspects.size() != 3 || essences.size() != 3) {
            throw new IllegalArgumentException(mob + ": three aspects and three soul aspect slots, got " + aspects + " / " + essences.size());
        }
    }

    /** The three basic soul aspects, in order: the three stacks of the page. */
    public List<ItemStack> basicEssences() {
        return essences.stream().map(List::getFirst).toList();
    }
}
