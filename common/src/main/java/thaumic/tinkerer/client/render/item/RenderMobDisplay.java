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
package thaumic.tinkerer.client.render.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import thaumic.tinkerer.common.core.helper.EnumMobAspect;
import thaumic.tinkerer.common.item.ItemMobDisplay;

/**
 * Draws the mob of a {@code mob_display} item in place of the item (the 1.7.10 {@code IItemRenderer}). It has the
 * signature of {@link thaumic.tinkerer.client.hooks.ItemRenderers.Renderer}, the loader layer's built-in item renderer
 * (Fabric's {@code BuiltinItemRendererRegistry}, NeoForge's {@code IClientItemExtensions#getCustomRenderer}), and
 * {@code MachinesClient.register()} registers it there.
 */
public final class RenderMobDisplay {

    private static final EnumMap<EnumMobAspect, Mob> CACHE = new EnumMap<>(EnumMobAspect.class);

    /**
     * The mobs whose model reaches out of a 16 x 16 slot at the original's scale: {@code {factor, lowered}}, drawn smaller by
     * {@code factor} about the middle of the slot and lowered by {@code lowered} slot pixels, in a slot ({@link ItemDisplayContext#GUI})
     * only. Measured on the pictures of the client harness ({@code items-machines-02.png}) before the change: the Horse reached 4.75 slot
     * pixels above the slot (its head) and down to 15.5, the Witch 3.75 above (her hat) and down to 16, the Mooshroom 3 above (the
     * mushrooms) and down to 15.75; each is fitted with half a slot pixel to spare at the top. A decision on the owner's request
     * (docs/deviations/Machines.md section 7): the {@code EnumMobAspect} scales stay the original's, every other mob and every other place
     * (hand, ground, frame) is drawn as before; to undo it, remove the rows.
     */
    private static final EnumMap<EnumMobAspect, float[]> SLOT_FIT = new EnumMap<>(EnumMobAspect.class);

    static {
        SLOT_FIT.put(EnumMobAspect.EntityHorse, new float[] { 0.74F, 1.94F });
        SLOT_FIT.put(EnumMobAspect.Witch, new float[] { 0.75F, 1.41F });
        SLOT_FIT.put(EnumMobAspect.MushroomCow, new float[] { 0.80F, 1.30F });
    }

    /**
     * Straight down the screen, in the frame the renderer gets in a slot: the "gui" display transform of
     * {@code models/item/mob_display.json} turned the frame by 30 degrees about X and then 45 about Y, so the screen's down
     * {@code (0, -1, 0)} is {@code Ry(-45) Rx(-30) (0, -1, 0)} here; one unit of the frame is 0.625 of a block, ten slot pixels.
     */
    private static final float DOWN_X = (float) (-0.5 * Math.sqrt(0.5));
    private static final float DOWN_Y = (float) (-Math.sqrt(3.0) / 2.0);
    private static final float DOWN_Z = (float) (0.5 * Math.sqrt(0.5));
    private static final float SLOT_PIXELS_PER_UNIT = 10F;

    /** The slot fit of a mob in a context ({@link #SLOT_FIT}), or null: the original's drawing. */
    public static float[] slotFit(EnumMobAspect aspect, ItemDisplayContext context) {
        return context == ItemDisplayContext.GUI ? SLOT_FIT.get(aspect) : null;
    }

    private RenderMobDisplay() {}

    /** Forget the mobs (they belong to the level that was left). Call when the client leaves a world. */
    public static void clearCache() {
        CACHE.clear();
    }

    public static void render(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        if (!(stack.getItem() instanceof ItemMobDisplay item)) return;
        EnumMobAspect aspect = item.getEntityType(stack);
        if (aspect == null) return;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        Mob entity = CACHE.get(aspect);
        if (entity == null || entity.level() != level) {
            entity = aspect.createEntity(level);
            if (entity == null) return;
            CACHE.put(aspect, entity);
        }

        float scale = aspect.getScale();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        // 1.7.10 called the entity renderer's doRender, which draws the mob only; the dispatcher also draws the shadow
        // (on the blocks of the level around the cached mob's own position, the world origin) and, with F3+B, the hitbox.
        // The shadow switch has no getter: it is on, unless something else turned it off, and the game itself restores it to on.
        boolean hitBoxes = dispatcher.shouldRenderHitBoxes();
        dispatcher.setRenderShadow(false);
        dispatcher.setRenderHitBoxes(false);
        pose.pushPose();
        try {
            // A builtin model is drawn with the corner of its cell at the origin: the mob stands in the middle. In a slot the
            // pose has already been through the "gui" display transform of models/item/mob_display.json (30 degrees about X,
            // 45 about Y, 0.625 of a block per pixel unit: what the 1.7.10 ItemRendererHelper.INVENTORY_BLOCK set-up did before
            // calling the renderer), so the -30 degrees below are the 1.7.10 renderer's own.
            pose.translate(0.5, 0.5, 0.5);
            float[] fit = slotFit(aspect, context);
            if (fit != null) {
                float down = fit[1] / SLOT_PIXELS_PER_UNIT;
                pose.translate(DOWN_X * down, DOWN_Y * down, DOWN_Z * down);
                pose.scale(fit[0], fit[0], fit[0]);
            }
            pose.mulPose(Axis.XP.rotationDegrees(-30.0F));
            pose.scale(scale, scale, scale);
            pose.translate(0, (-entity.getBbHeight() / 2) + aspect.getVerticalOffset(), 0);
            dispatcher.render(entity, 0, 0, 0, 0, 0, pose, buffers, light);
        } finally {
            pose.popPose();
            dispatcher.setRenderShadow(true);
            dispatcher.setRenderHitBoxes(hitBoxes);
        }
    }
}
