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
package thaumic.tinkerer.common.item.kami.armor;

import dev.architectury.registry.registries.RegistrySupplier;
import java.util.List;
import java.util.Map;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * {@code EnumHelper.addArmorMaterial("ICHOR", 0, new int[] {3, 8, 6, 3}, 20)}: no durability, enchantability 20. 1.21.1 armour
 * materials are registry entries and name their worn layers by path ({@code textures/models/armor/<name>_layer_1.png} for the
 * helmet, chestplate and boots, {@code _layer_2} for the leggings), so the plain and the awakened armour are two materials of
 * the same numbers with different layers.
 *
 * <p><b>Which piece has which points.</b> The array {@code {3, 8, 6, 3}} is laid out by armour type (helmet 0, chestplate 1,
 * leggings 2, boots 3: {@code ItemArmor.ArmorMaterial.getDamageReductionAmount(int)} reads {@code damageReductionAmountArray[i]},
 * and the {@code ItemArmor} constructor stores {@code getDamageReductionAmount(armorType)} as {@code damageReduceAmount}). The
 * original's {@code ISpecialArmor} methods read the same array with the {@code slot} that Forge 1.7.10 handed them, and Forge
 * handed them the index in {@code InventoryPlayer.armorInventory} ({@code ArmorProperties.ApplyArmor} loops {@code x} over the
 * inventory and calls {@code getProperties(..., x)}, {@code ForgeHooks.getTotalArmorValue} calls {@code getArmorDisplay(player,
 * stack, x)}), where boots are 0 and the helmet 3 ({@code ContainerPlayer} puts armour type {@code t} into inventory slot
 * {@code 39 - t}, that is {@code armorInventory[3 - t]}). So a player's chestplate read index 2 (6 points) and the leggings index 1
 * (8 points): {@link #playerPoints}, which this material carries, so the HUD (1.7.10 {@code getArmorDisplay}) and the absorption
 * (1.7.10 {@code getProperties}, {@link IchorAbsorption}) of a player are the original's. Any other wearer went through vanilla's
 * {@code applyArmorCalculations}, which read {@code damageReduceAmount}, the array by armour type: {@link #mobPoints}
 * ({@code docs/deviations/KamiGear.md} section 3).</p>
 *
 * <p>The toughness is 0, as the original had none. What the pieces really absorb for a player is not the formula of these
 * points but 1.7.10's {@code ArmorProperties(0, points * 0.0425, MAX)}, applied by {@link IchorAbsorption} through
 * {@code TinkererEvents.LIVING_ARMOR_ABSORB}, which <i>replaces</i> the amount of the 1.21.1 formula, so these attribute values
 * never stack with it.</p>
 */
public final class KamiArmorMaterials {
    /** The original has no toughness. */
    public static final float TOUGHNESS = 0.0F;

    public static RegistrySupplier<ArmorMaterial> ICHOR;
    public static RegistrySupplier<ArmorMaterial> ICHOR_GEM;

    private KamiArmorMaterials() {}

    public static void register() {
        ICHOR = TTRegistry.ARMOR_MATERIALS.register("ichor", () -> create("ichor"));
        ICHOR_GEM = TTRegistry.ARMOR_MATERIALS.register("ichor_gem", () -> create("ichor_gem"));
    }

    /** {@code new int[] {3, 8, 6, 3}} of the original's {@code addArmorMaterial}, indexed by the 1.7.10 armour type. */
    private static final int[] REDUCTION_AMOUNTS = { 3, 8, 6, 3 };

    /** The 1.7.10 {@code ItemArmor.armorType} of a piece: helmet 0, chestplate 1, leggings 2, boots 3. */
    public static int armorType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
            case BODY -> throw new IllegalArgumentException("no 1.7.10 armour type for " + type);
        };
    }

    /**
     * The armour points of a piece for a player, as 1.7.10's {@code getProperties} and {@code getArmorDisplay} read them: the array
     * at the index of the piece in {@code armorInventory}, {@code 3 - armorType} (helmet 3, chestplate 6, leggings 8, boots 3).
     */
    public static int playerPoints(ArmorItem.Type type) {
        return REDUCTION_AMOUNTS[3 - armorType(type)];
    }

    /**
     * The armour points of a piece for any other wearer, as 1.7.10's vanilla armour path read them ({@code damageReduceAmount}, the
     * array at the armour type: helmet 3, chestplate 8, leggings 6, boots 3).
     */
    public static int mobPoints(ArmorItem.Type type) {
        return REDUCTION_AMOUNTS[armorType(type)];
    }

    private static ArmorMaterial create(String layer) {
        Map<ArmorItem.Type, Integer> defense = Map.of(ArmorItem.Type.HELMET, playerPoints(ArmorItem.Type.HELMET),
                ArmorItem.Type.CHESTPLATE, playerPoints(ArmorItem.Type.CHESTPLATE), ArmorItem.Type.LEGGINGS,
                playerPoints(ArmorItem.Type.LEGGINGS), ArmorItem.Type.BOOTS, playerPoints(ArmorItem.Type.BOOTS));
        return new ArmorMaterial(defense, 20, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(LibMisc.id(layer))), TOUGHNESS, 0.0F);
    }
}
