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
package thaumic.tinkerer.common.item;

import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumic.tinkerer.common.lib.LibItemNames;
import thaumic.tinkerer.common.lib.LibResearch;
import thaumic.tinkerer.common.registry.ITTinkererRegisterable;
import thaumic.tinkerer.common.registry.TTRegistry;
import thaumic.tinkerer.common.registry.ThaumicTinkererCraftingBenchRecipe;
import thaumic.tinkerer.common.registry.ThaumicTinkererInfusionRecipe;
import thaumic.tinkerer.common.registry.ThaumicTinkererRecipe;
import thaumic.tinkerer.common.registry.ThaumicTinkererRecipeMulti;
import thaumic.tinkerer.common.research.IRegisterableResearch;

/**
 * A soul aspect. 1.7.10 made this one item with the metadata {@code aspect index + 21 * form} (the indices of
 * {@code NumericAspectHelper}, the forms basic / condensed / infused); the 51 variants are 51 items here, named
 * {@code mob_aspect_<tag>}, {@code mob_aspect_condensed_<tag>} and {@code mob_aspect_infused_<tag>}.
 */
public class ItemMobAspect extends Item implements ITTinkererRegisterable {

    /** The three forms of a soul aspect. */
    public enum Form {
        BASIC, CONDENSED, INFUSED
    }

    /**
     * The aspects a soul aspect can be of, in the order of 1.7.10 {@code NumericAspectHelper} (the order is the metadata
     * of the old items).
     */
    public static final List<Aspect> ASPECTS = List.of(Aspect.WATER, Aspect.MAN, Aspect.AIR, Aspect.FLIGHT, Aspect.FIRE,
            Aspect.MAGIC, Aspect.UNDEAD, Aspect.FLESH, Aspect.BEAST, Aspect.POISON, Aspect.EARTH, Aspect.ELDRITCH,
            Aspect.TRAVEL, Aspect.METAL, Aspect.SLIME, Aspect.CLOTH, Aspect.CROP);

    /** The name of the data recipe the research page of the necromancy shows (1.7.10: the key {@code SUMMON1}). */
    public static final String CONDENSE_RECIPE_NAME = LibResearch.KEY_SUMMON.toLowerCase() + "1";

    public final Aspect aspect;
    public final Form form;

    public ItemMobAspect(Aspect aspect, Form form) {
        super(properties(form));
        this.aspect = aspect;
        this.form = form;
    }

    /**
     * Stacks of 16; the infused form glints (1.7.10 {@code hasEffect} for the infused metadata). The glint is the item's
     * default {@link DataComponents#ENCHANTMENT_GLINT_OVERRIDE}, as for vanilla's enchanted golden apple, instead of an
     * {@code isFoil} override: the form is an item of its own, so the glint is a constant of the item.
     */
    private static Item.Properties properties(Form form) {
        Item.Properties properties = new Item.Properties().stacksTo(16);
        if (form == Form.INFUSED) properties.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return properties;
    }

    /** The registry path of the item of an aspect and form. */
    public static String path(Aspect aspect, Form form) {
        String base = LibItemNames.MOB_ASPECT;
        return switch (form) {
            case BASIC -> base + "_" + aspect.getTag();
            case CONDENSED -> base + "_condensed_" + aspect.getTag();
            case INFUSED -> base + "_infused_" + aspect.getTag();
        };
    }

    public static Aspect getAspect(ItemStack item) {
        if (item == null || item.isEmpty() || !(item.getItem() instanceof ItemMobAspect mobAspect)) return null;
        return mobAspect.aspect;
    }

    public static ItemStack getStackFromAspect(Aspect a) {
        return TTRegistry.stack(path(a, Form.BASIC));
    }

    public static ItemStack getCondensedStackFromAspect(Aspect a) {
        return TTRegistry.stack(path(a, Form.CONDENSED));
    }

    public static ItemStack getInfusedStackFromAspect(Aspect a) {
        return TTRegistry.stack(path(a, Form.INFUSED));
    }

    public static boolean isCondensed(ItemStack item) {
        return item.getItem() instanceof ItemMobAspect mobAspect && mobAspect.form == Form.CONDENSED;
    }

    public static boolean isInfused(ItemStack item) {
        return item.getItem() instanceof ItemMobAspect mobAspect && mobAspect.form == Form.INFUSED;
    }

    /**
     * An infused soul aspect remembers the tablet that used it last (1.7.10: the keys {@code LastX/Y/Z}; the component
     * {@link MachineComponents#LAST_TABLET} adds the dimension).
     */
    public static void markLastUsedTablet(ItemStack stack, GlobalPos tablet) {
        stack.set(MachineComponents.LAST_TABLET.get(), tablet);
    }

    /** The tablet that used the stack last, or null for a stack that was never used. */
    @Nullable
    public static GlobalPos lastUsedTablet(ItemStack stack) {
        return stack.get(MachineComponents.LAST_TABLET.get());
    }

    /**
     * True for a stack that was never used and for one whose last tablet is this one: the same position in the same
     * dimension (1.7.10 compared the coordinates only).
     */
    public static boolean lastUsedTabletMatches(ItemStack stack, GlobalPos tablet) {
        GlobalPos last = lastUsedTablet(stack);
        return last == null || last.equals(tablet);
    }

    /** The translation key of 1.7.10: {@code item.mobAspect}, {@code .condensed} and {@code .infused}. */
    @Override
    public String getDescriptionId() {
        return switch (form) {
            case BASIC -> "item.thaumictinkerer." + LibItemNames.MOB_ASPECT;
            case CONDENSED -> "item.mobAspect.condensed.name";
            case INFUSED -> "item.mobAspect.infused.name";
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(aspect.getName()));
    }

    @Override
    public boolean shouldDisplayInTab() {
        // 1.7.10 listed the basic and the infused soul aspects of every aspect, not the condensed ones.
        return form != Form.CONDENSED;
    }

    @Override
    public IRegisterableResearch getResearchItem() {
        return null;
    }

    @Override
    public ThaumicTinkererRecipe getRecipeItem() {
        if (form != Form.BASIC) return null;

        ThaumcraftApi.registerObjectTag(new ItemStack(this), new AspectList().add(aspect, 8));

        ItemStack condensed = getCondensedStackFromAspect(aspect);
        ItemStack[] components = new ItemStack[8];
        for (int i = 0; i < components.length; i++) components[i] = condensed.copy();

        return new ThaumicTinkererRecipeMulti(new ThaumicTinkererCraftingBenchRecipe(CONDENSE_RECIPE_NAME),
                new ThaumicTinkererInfusionRecipe(LibResearch.KEY_SUMMON, getInfusedStackFromAspect(aspect), 4,
                        new AspectList().add(aspect, 10), condensed.copy(), components));
    }
}
