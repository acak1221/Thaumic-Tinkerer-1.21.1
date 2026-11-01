/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The icons of the enchantments on the buttons of the Enchanter's screen. The textures of 1.7.10 come in two sizes (32
 * pixels, and 512 pixels for the Thaumic Tinkerer ones), and the screen drew whichever it was onto 16 by 16 pixels with the
 * texture coordinates 0 to 1 ({@code drawTexturedModalRect16}). {@code GuiGraphics.blit} turns pixels into texture
 * coordinates by dividing by the size of the texture it is told, so a region of 16 by 16 in a texture said to be 16 by 16 is
 * the whole texture, whatever its real size: no size has to be known (an earlier version read it from the PNG header).
 */
public final class EnchantIcons {
    /** The size of an icon on the screen, and the size the texture is said to have. */
    public static final int SIZE = 16;

    private EnchantIcons() {}

    /** Draws the whole texture of the icon onto 16 by 16 pixels at the position. */
    public static void draw(GuiGraphics graphics, ResourceLocation texture, int x, int y) {
        RenderSystem.enableBlend();
        graphics.blit(texture, x, y, 0F, 0F, SIZE, SIZE, SIZE, SIZE);
        RenderSystem.disableBlend();
    }
}
