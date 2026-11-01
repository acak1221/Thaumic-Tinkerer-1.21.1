/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [12 Sep 2013, 01:29:47 (GMT)]
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.core.helper;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

/** Odds and ends of 1.7.10 that several groups use: the motion towards a point, an aspect list multiplied. */
public final class MiscHelper {
    private MiscHelper() {}

    /**
     * Sets the motion of the entity to the vector from its centre to the point, normalised when longer than a block,
     * times the modifier (the pull of the magnets and of the Focus of Telekinesis).
     */
    public static void setEntityMotionFromVector(Entity entity, Vec3 originalPosVector, float modifier) {
        Vec3 entityVector = new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ());
        Vec3 finalVector = originalPosVector.subtract(entityVector);

        if (finalVector.length() > 1) finalVector = finalVector.normalize();

        entity.setDeltaMovement(finalVector.x * modifier, finalVector.y * modifier, finalVector.z * modifier);
    }

    /** A copy of the list with every amount multiplied (and truncated), the list itself when the factor is 1. */
    public static AspectList multiplyAspectList(AspectList list, double multiplier) {
        AspectList newList = list.copy();
        if (multiplier == 1) return newList;

        for (Aspect aspect : newList.getAspects()) {
            newList.aspects.put(aspect, (int) ((double) newList.aspects.get(aspect) * multiplier));
        }

        return newList;
    }
}
