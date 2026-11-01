/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [29 Sep 2013, 11:09:54 (GMT)]
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.lib;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The identity of an enchantment of the add-on. 1.7.10 configured numeric ids here (220 to 225 and 166 to 173); 1.21.1 has
 * none, an enchantment is identified by its registry key and its level is read from the item's enchantments component through
 * that key. The fourteen keys are the constants of {@link thaumic.tinkerer.common.enchantment.ModEnchantments}; this class
 * makes them from the registry paths of {@link LibEnchantNames} (it used to hold a second copy of the fourteen under the
 * 1.7.10 names of the numeric ids, {@code idAscentBoost} and so on).
 */
public final class LibEnchantIDs {

    private LibEnchantIDs() {}

    /** The registry key of an enchantment of the add-on, from its registry path. */
    public static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT, LibMisc.id(path));
    }
}
