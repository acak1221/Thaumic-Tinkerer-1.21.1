package thaumic.tinkerer.neoforge.clientsmoke;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.CreativeTabsScreenPage;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforgespi.language.IModFileInfo;
import thaumic.tinkerer.clientsmoke.SmokePlatform;

/**
 * The NeoForge side of the client smoke (the counterpart of {@code FabricSmokePlatform}): NeoForge's game-bus client events, its
 * creative-tab pages and FML's mod list. Test-only code of the port, new in the 1.21.1 port (no 1.7.10 counterpart);
 * CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), not distributed.
 *
 * <ul>
 *   <li>The client tick is {@link ClientTickEvent.Post} (posted at the end of {@code Minecraft.tick()}, where Fabric API's
 *   {@code END_CLIENT_TICK} is).</li>
 *   <li>A level frame is {@link RenderLevelStageEvent} at {@link RenderLevelStageEvent.Stage#AFTER_LEVEL} (after
 *   {@code LevelRenderer.renderLevel} finished, Fabric's {@code WorldRenderEvents.END}); a screen frame is
 *   {@link ScreenEvent.Render.Post} (after the open screen was drawn, Fabric's {@code ScreenEvents.afterRender}).</li>
 *   <li>NeoForge pages the creative tabs itself ({@link CreativeTabsScreenPage}); its screen has {@code setCurrentPage} but keeps the
 *   pages and {@code selectTab} private, so those two are reached by reflection (Mojang names at run time on NeoForge 21.1).</li>
 * </ul>
 */
final class NeoForgeSmokePlatform implements SmokePlatform {
    @Override
    public String loader() {
        return "neoforge";
    }

    @Override
    public void onEndClientTick(Consumer<Minecraft> listener) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> listener.accept(Minecraft.getInstance()));
    }

    @Override
    public void onLevelRendered(Runnable listener) {
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) listener.run();
        });
    }

    @Override
    public void onScreenRendered(Runnable listener) {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, event -> listener.run());
    }

    @Override
    public boolean selectCreativeTab(CreativeModeInventoryScreen screen, CreativeModeTab tab) {
        try {
            Field pagesField = CreativeModeInventoryScreen.class.getDeclaredField("pages");
            pagesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<CreativeTabsScreenPage> pages = (List<CreativeTabsScreenPage>) pagesField.get(screen);
            CreativeTabsScreenPage page = pages.stream().filter(p -> p.getVisibleTabs().contains(tab)).findFirst().orElse(null);
            if (page == null) return false;
            screen.setCurrentPage(page);
            Method selectTab = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            selectTab.setAccessible(true);
            selectTab.invoke(screen, tab);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("cannot select the creative tab " + tab.getDisplayName().getString() + " on NeoForge's creative screen", e);
        }
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Optional<Path> findModPath(String modId, String path) {
        IModFileInfo file = ModList.get().getModFileById(modId);
        if (file == null) return Optional.empty();
        Path found = file.getFile().findResource(path.split("/"));
        return Files.exists(found) ? Optional.of(found) : Optional.empty();
    }
}
