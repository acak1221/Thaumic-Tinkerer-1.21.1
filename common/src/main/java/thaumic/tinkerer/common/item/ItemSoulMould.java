/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [12 Sep 2013, 17:36:35 (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.item;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumic.tinkerer.common.lib.LibResearch;
import thaumic.tinkerer.common.registry.ITTinkererRegisterable;
import thaumic.tinkerer.common.registry.ThaumicTinkererCrucibleRecipe;
import thaumic.tinkerer.common.registry.ThaumicTinkererRecipe;
import thaumic.tinkerer.common.research.IRegisterableResearch;

/**
 * The soul mould. 1.7.10 stored the pattern as the legacy entity name ({@code EntityList.getEntityString}) in the
 * item NBT key {@code pattern}; the stack carries the registry key of the entity type ({@code minecraft:zombie}) in the
 * component {@link MachineComponents#SOUL_PATTERN}. It is a key, not a holder: a mould keeps the pattern of an entity
 * type that is no longer registered (its tooltip then shows the id).
 */
public class ItemSoulMould extends Item implements ITTinkererRegisterable {

    public ItemSoulMould(Properties properties) {
        super(properties.stacksTo(1));
    }

    private static void setPattern(ItemStack stack, LivingEntity entity) {
        BuiltInRegistries.ENTITY_TYPE.getResourceKey(entity.getType())
                .ifPresent(key -> stack.set(MachineComponents.SOUL_PATTERN.get(), key));
    }

    /** The entity type this mould is set to, or null for a blank mould. */
    @Nullable
    public static ResourceKey<EntityType<?>> getPattern(ItemStack stack) {
        return stack.get(MachineComponents.SOUL_PATTERN.get());
    }

    /** The registry id of the entity type this mould is set to, or the empty string (what a block tooltip sends). */
    public static String getPatternName(ItemStack stack) {
        ResourceKey<EntityType<?>> pattern = getPattern(stack);
        return pattern == null ? "" : pattern.location().toString();
    }

    private static void clearPattern(ItemStack stack) {
        stack.remove(MachineComponents.SOUL_PATTERN.get());
    }

    /** The name of the entity type of a pattern, as the game shows it; the id for a type that is not registered. */
    public static Component patternDisplayName(ResourceKey<EntityType<?>> pattern) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(pattern).<Component>map(EntityType::getDescription)
                .orElseGet(() -> Component.literal(pattern.location().toString()));
    }

    /**
     * {@link #patternDisplayName(ResourceKey)} of a pattern written as its id (a block tooltip sends it so); text that names
     * no registered entity type is shown as it is.
     */
    public static Component patternDisplayName(String pattern) {
        ResourceLocation id = ResourceLocation.tryParse(pattern);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return Component.literal(pattern);
        return patternDisplayName(ResourceKey.create(Registries.ENTITY_TYPE, id));
    }

    @Override
    public IRegisterableResearch getResearchItem() {
        return null;
    }

    @Override
    public ThaumicTinkererRecipe getRecipeItem() {
        return new ThaumicTinkererCrucibleRecipe(LibResearch.KEY_MAGNETS, new ItemStack(this),
                new ItemStack(Items.ENDER_PEARL),
                new AspectList().add(Aspect.BEAST, 4).add(Aspect.MIND, 8).add(Aspect.SENSES, 8));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (target instanceof Player) return InteractionResult.sidedSuccess(player.level().isClientSide);
        if (!player.level().isClientSide) {
            // 1.7.10 set the pattern on the stack the player holds (getCurrentEquippedItem). In 1.21.1 Player.interactOn
            // of a creative player passes a COPY of the held stack here, so the pattern would be lost.
            ItemStack held = player.getItemInHand(hand);
            setPattern(held.getItem() instanceof ItemSoulMould ? held : stack, target);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !level.isClientSide) clearPattern(stack);
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            net.minecraft.world.item.TooltipFlag flag) {
        ResourceKey<EntityType<?>> pattern = getPattern(stack);

        if (pattern == null) tooltip.add(Component.translatable("ttmisc.soulMould.nonAssigned"));
        else tooltip.add(Component.translatable("ttmisc.soulMould.pattern", patternDisplayName(pattern)));
    }
}
