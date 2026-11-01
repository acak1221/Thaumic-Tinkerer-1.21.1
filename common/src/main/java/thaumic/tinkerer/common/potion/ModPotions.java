/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [4 Sep 2013, 16:59:57 (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.potion;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.Aspect;
import thaumic.tinkerer.common.lib.LibMisc;
import thaumic.tinkerer.common.registry.TTRegistry;

/**
 * The four imbued effects. 1.7.10 reached into the private potion array to make room for four potion ids it read from
 * the configuration; here they are ordinary registry entries ({@code thaumictinkerer:potion_fire} and so on) and the
 * four id options of the configuration are gone. Like the {@code DummyPotions} of 1.7.10 they are "bad" effects of
 * colour zero: Air, Fire and Earth do nothing by themselves (they act on the hits of the player who has them, in
 * {@link PotionEffectHandler}); Water turns the lava around the player into obsidian from its own effect tick
 * ({@link WaterImbuedEffect}).
 */
public final class ModPotions {
    /** The registry path of Air Imbued. */
    public static final String AIR = "potion_air";
    /** The registry path of Fire Imbued. */
    public static final String FIRE = "potion_fire";
    /** The registry path of Earth Imbued. */
    public static final String EARTH = "potion_earth";
    /** The registry path of Water Imbued. */
    public static final String WATER = "potion_water";
    /** The registry paths in the order of the infused potions' variants ({@code ItemInfusedPotion.ASPECTS}: aer, ignis, terra, aqua). */
    public static final String[] PATHS = { AIR, FIRE, EARTH, WATER };

    public static RegistrySupplier<MobEffect> potionAir;
    public static RegistrySupplier<MobEffect> potionFire;
    public static RegistrySupplier<MobEffect> potionEarth;
    public static RegistrySupplier<MobEffect> potionWater;

    private ModPotions() {}

    /** The effect of the 1.7.10 {@code DummyPotions}: harmful category, colour 0, no tick effect. */
    public static class ImbuedEffect extends MobEffect {
        public ImbuedEffect() {
            super(MobEffectCategory.HARMFUL, 0);
        }
    }

    /**
     * Water Imbued: while a player has it, every lava block within two blocks of him becomes obsidian, every tick, on the server
     * ({@link PotionEffectHandler#quenchLava}). 1.7.10 did this from a player-tick handler for every player that had the effect;
     * the 1.21.1 effect ticks itself. The effect tick runs on both sides and for any living entity: only a player on a server
     * quenches lava, as only a player's tick on the server did in 1.7.10.
     */
    public static class WaterImbuedEffect extends ImbuedEffect {
        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity instanceof Player player && entity.level() instanceof ServerLevel level) PotionEffectHandler.quenchLava(player, level);
            return true;
        }
    }

    /** Registers the four effects with the deferred register of the add-on. Call once from the register phase. */
    public static void initPotions() {
        if (potionAir != null) return;
        potionAir = TTRegistry.MOB_EFFECTS.register(AIR, ImbuedEffect::new);
        potionFire = TTRegistry.MOB_EFFECTS.register(FIRE, ImbuedEffect::new);
        potionEarth = TTRegistry.MOB_EFFECTS.register(EARTH, ImbuedEffect::new);
        potionWater = TTRegistry.MOB_EFFECTS.register(WATER, WaterImbuedEffect::new);
    }

    public static ResourceKey<MobEffect> key(String path) {
        return ResourceKey.create(Registries.MOB_EFFECT, LibMisc.id(path));
    }

    /**
     * The registered effect of a path ({@link #AIR} and so on), as the registry's holder to apply and to look up: the holder of an
     * effect instance is the key of the entity's effect map, so it must be the registry's own and not the deferred supplier.
     */
    public static Holder<MobEffect> holder(String path) {
        return BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(key(path));
    }

    public static Holder<MobEffect> air() {
        return holder(AIR);
    }

    public static Holder<MobEffect> fire() {
        return holder(FIRE);
    }

    public static Holder<MobEffect> earth() {
        return holder(EARTH);
    }

    public static Holder<MobEffect> water() {
        return holder(WATER);
    }

    /** The effect an infused potion of the aspect gives (aer, ignis, terra, aqua); null for any other aspect. */
    @Nullable
    public static Holder<MobEffect> of(Aspect aspect) {
        if (aspect == Aspect.AIR) return air();
        if (aspect == Aspect.FIRE) return fire();
        if (aspect == Aspect.EARTH) return earth();
        if (aspect == Aspect.WATER) return water();
        return null;
    }
}
