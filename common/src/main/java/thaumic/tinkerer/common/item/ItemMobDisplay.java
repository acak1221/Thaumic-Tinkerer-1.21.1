/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * Created by Katrina on 11/03/14.
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumic.tinkerer.common.core.helper.EnumMobAspect;
import thaumic.tinkerer.common.registry.ITTinkererRegisterable;
import thaumic.tinkerer.common.registry.TTRegistry;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.research.IRegisterableResearch;

/**
 * An item that stands for a mob: it is shown as the mob, named as the mob, and is meant for recipe viewers (the output
 * of a necromancy recipe). It is not in the creative tab. The mob is a row of {@link EnumMobAspect}, the component
 * {@link MachineComponents#DISPLAYED_MOB} (1.7.10: the row name in the tag {@code type}).
 */
public class ItemMobDisplay extends Item implements ITTinkererRegisterable {

    public ItemMobDisplay(Properties properties) {
        super(properties);
    }

    @Override
    public boolean shouldDisplayInTab() {
        return false;
    }

    @Override
    public IRegisterableResearch getResearchItem() {
        return null;
    }

    /** The mob a stack shows, or null for a stack without one. */
    @Nullable
    public static EnumMobAspect getMob(ItemStack stack) {
        return stack.get(MachineComponents.DISPLAYED_MOB.get());
    }

    @Nullable
    public EnumMobAspect getEntityType(ItemStack stack) {
        return getMob(stack);
    }

    public void setEntityType(ItemStack stack, EnumMobAspect type) {
        stack.set(MachineComponents.DISPLAYED_MOB.get(), type);
    }

    /** A stack of this item that displays the given mob. */
    public static ItemStack stackFor(EnumMobAspect type) {
        ItemStack stack = TTRegistry.stack(LibItemNames.MOB_DISPLAY);
        ((ItemMobDisplay) stack.getItem()).setEntityType(stack, type);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        EnumMobAspect aspect = getEntityType(stack);
        if (aspect == null) return super.getName(stack);
        return aspect.getDisplayName();
    }
}
