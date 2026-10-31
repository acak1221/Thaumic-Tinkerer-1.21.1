/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import thaumic.tinkerer.client.core.handler.kami.PlacementMirrorPredictionRenderer;
import thaumic.tinkerer.client.render.kami.WingsLayer;
import thaumic.tinkerer.common.core.handler.ConfigHandler;

/**
 * The two client hooks of the Kami gear that Architectury has no event for, on NeoForge (the counterpart of
 * {@code KamiGearFabricClient}):
 *
 * <ul>
 *   <li>the wings of the Robes of the Stratosphere are a render layer, added to the player renderer of every skin model
 *       ({@link EntityRenderersEvent.AddLayers}, mod bus, posted again at every resource reload for the new renderers);</li>
 *   <li>the phantom blocks of the Placement Mirror are drawn after the translucent terrain
 *       ({@link RenderLevelStageEvent} at {@code AFTER_TRANSLUCENT_BLOCKS}, game bus), when
 *       {@code ConfigHandler.showPlacementMirrorBlocks} is on. That stage is posted inside {@code LevelRenderer.renderLevel},
 *       with a new identity pose stack and the camera rotation on the model view stack, the same state as Fabric's
 *       {@code AFTER_TRANSLUCENT} context, which is what {@link PlacementMirrorPredictionRenderer#render} expects.</li>
 * </ul>
 */
public final class KamiGearNeoForgeClient {
    private static boolean registered;

    private KamiGearNeoForgeClient() {}

    /** From the mod constructor (client only; the config is loaded by then). Registers once; nothing while the Kami content is off. */
    public static synchronized void register(IEventBus modBus) {
        if (registered || !ConfigHandler.enableKami) return;
        registered = true;
        modBus.addListener(KamiGearNeoForgeClient::onAddLayers);
        NeoForge.EVENT_BUS.addListener(KamiGearNeoForgeClient::onRenderLevelStage);
    }

    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer players) {
                players.addLayer(new WingsLayer(players, event.getEntityModels()));
            }
        }
    }

    private static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !ConfigHandler.showPlacementMirrorBlocks) return;
        PlacementMirrorPredictionRenderer.render(event.getPoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(),
                event.getCamera());
    }
}
