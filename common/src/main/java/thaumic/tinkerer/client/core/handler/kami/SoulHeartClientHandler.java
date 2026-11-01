/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * File Created @ [Dec 29, 2013, 9:35:38 PM (GMT)]
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.client.core.handler.kami;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.architectury.event.events.client.ClientGuiEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.core.handler.kami.SoulHeartHandler;
import thaumic.tinkerer.common.lib.LibMisc;

/**
 * The Soul Hearts on the HUD: black-edged hearts in a row that starts 10 pixels right of the middle of the screen, at the
 * height {@code ConfigHandler.soulHeartHeight} above the bottom (49: the row above the food bar), ten pixels higher while
 * the air bubbles are shown. A full heart is 9 pixels wide, a half heart 7, and they are 8 pixels apart.
 *
 * <p>1.7.10 drew them when the health bar was about to be drawn; here they are drawn after the HUD, only where the health bar
 * is (not in creative or spectator mode, not with the HUD hidden). The number is the local player's copy of the server's pool
 * (the synced attachment {@link SoulHeartHandler#HEARTS}); a new world, a respawn and a new player start without hearts by
 * themselves, because the attachment belongs to the player entity.</p>
 *
 * <p>The two hearts are GUI sprites ({@code textures/gui/sprites/hud/soul_heart_full.png} and {@code soul_heart_half.png}, the
 * 9 by 9 and the 7 by 9 at the top left of the 1.7.10 sheet {@code soulHearts.png}, pixel for pixel), drawn with
 * {@link GuiGraphics#blitSprite} as vanilla draws its hearts, with blending switched on for the row and off again after it.</p>
 */
public final class SoulHeartClientHandler {
    private static final ResourceLocation FULL = LibMisc.id("hud/soul_heart_full");
    private static final ResourceLocation HALF = LibMisc.id("hud/soul_heart_half");

    private SoulHeartClientHandler() {}

    public static void register() {
        ClientGuiEvent.RENDER_HUD.register(SoulHeartClientHandler::renderHud);
    }

    /** Draws one heart: the full heart is 9 by 9, the half heart 7 by 9. */
    private static void renderHeart(GuiGraphics graphics, int x, int y, boolean full) {
        if (full) graphics.blitSprite(FULL, x, y, 9, 9);
        else graphics.blitSprite(HALF, x, y, 7, 9);
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;
        renderHUD(graphics, player.getAirSupply() < player.getMaxAirSupply(), SoulHeartHandler.getHP(player));
    }

    /** 1.7.10 {@code renderHUD}: the row of {@code hp} half hearts; {@code airShown} moves it up past the bubbles. */
    public static void renderHUD(GuiGraphics graphics, boolean airShown, int hp) {
        if (hp <= 0) return;
        int x = graphics.guiWidth() / 2 + 10;
        int y = graphics.guiHeight() - ConfigHandler.soulHeartHeight;
        if (airShown) y = graphics.guiHeight() - (ConfigHandler.soulHeartHeight + 10);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int it = 0;
        for (int i = 0; i < hp; i++) {
            boolean half = i == hp - 1 && hp % 2 != 0;
            if (half || i % 2 == 0) {
                renderHeart(graphics, x + it * 8, y, !half);
                it++;
            }
        }
        RenderSystem.disableBlend();
    }
}
