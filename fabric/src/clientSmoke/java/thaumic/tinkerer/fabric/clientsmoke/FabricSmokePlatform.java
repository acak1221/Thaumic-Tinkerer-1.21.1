package thaumic.tinkerer.fabric.clientsmoke;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.itemgroup.v1.FabricCreativeInventoryScreen;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import thaumic.tinkerer.clientsmoke.SmokePlatform;

/**
 * The Fabric side of the client smoke: Fabric API's client events, its creative-tab pages and Fabric Loader. Test-only code of the
 * port, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), not distributed.
 *
 * <p>These are the calls the harness made itself before it ran on NeoForge too, moved here unchanged.</p>
 */
final class FabricSmokePlatform implements SmokePlatform {
    private final Set<Screen> seen = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public String loader() {
        return "fabric";
    }

    @Override
    public void onEndClientTick(Consumer<Minecraft> listener) {
        ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
    }

    @Override
    public void onLevelRendered(Runnable listener) {
        WorldRenderEvents.END.register(context -> listener.run());
    }

    /** Fabric API registers the render callbacks per screen: once for every screen after its first {@code init}. */
    @Override
    public void onScreenRendered(Runnable listener) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!seen.add(screen)) return;
            ScreenEvents.afterRender(screen).register((rendered, graphics, mouseX, mouseY, delta) -> listener.run());
        });
    }

    @Override
    public boolean selectCreativeTab(CreativeModeInventoryScreen screen, CreativeModeTab tab) {
        return ((FabricCreativeInventoryScreen) screen).setSelectedItemGroup(tab);
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Optional<Path> findModPath(String modId, String path) {
        return FabricLoader.getInstance().getModContainer(modId).flatMap(container -> container.findPath(path));
    }
}
