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
package thaumic.tinkerer.common.block.tile.container.slot.kami;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.wands.IFocusPouch;

/**
 * A slot that refuses focus pouches ({@code !(item instanceof ItemFocusPouch)}): {@link IFocusPouch} is the API form of
 * that class, which Thaumcraft's own pouch and the Bottomless Pouch both implement.
 *
 * <p>New in the port: it also refuses what may not go inside a container item ({@code canFitInsideContainerItems()} false:
 * shulker boxes, and the Bottomless Pouch itself), as a shulker box's own slots do. The pouch refuses to go into a shulker box,
 * but on Fabric a hopper still puts it into one (vanilla's {@code ShulkerBoxBlockEntity.canPlaceItemThroughFace} asks only "is it
 * a shulker box"; NeoForge's also asks {@code canFitInsideContainerItems}), so without this rule a pouch of shulker boxes of
 * pouches could be nested without limit.</p>
 */
public class SlotNoPouches extends Slot {
    public SlotNoPouches(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    /** True for a focus pouch of Thaumcraft or the Bottomless Pouch (or any other pouch of an add-on). */
    public static boolean isPouch(ItemStack stack) {
        return stack.getItem() instanceof IFocusPouch;
    }

    /** What a cell of the Bottomless Pouch takes: anything but a pouch and what may not go inside a container item. */
    public static boolean mayHold(ItemStack stack) {
        return !isPouch(stack) && stack.getItem().canFitInsideContainerItems();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return mayHold(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }
}
