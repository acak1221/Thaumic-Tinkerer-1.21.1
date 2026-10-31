package thaumic.tinkerer.clientsmoke;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

/**
 * What the client smoke needs from the mod loader, and nothing more: the client tick, two frame counters, the creative-tab
 * pages, the game directory and the mod list. Test-only code of the port, new in the 1.21.1 port (no 1.7.10 counterpart);
 * CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), not distributed.
 *
 * <p>Every other class of the harness names Minecraft, Architectury, the Thaumcraft API and the add-on only. The two
 * implementations are {@code FabricSmokePlatform} (fabric/src/clientSmoke) and {@code NeoForgeSmokePlatform}
 * (neoforge/src/clientSmoke); each loader's harness mod hands its own to {@link ClientSmoke#arm}.</p>
 */
public interface SmokePlatform {
    /** The loader's name, as the report and the picture index give it: {@code fabric} or {@code neoforge}. */
    String loader();

    /** Calls {@code listener} at the end of every client tick, on the render thread. */
    void onEndClientTick(Consumer<Minecraft> listener);

    /** Calls {@code listener} after every frame in which the level was rendered (the end of {@code LevelRenderer.renderLevel}). */
    void onLevelRendered(Runnable listener);

    /** Calls {@code listener} after every frame in which the open screen was rendered. */
    void onScreenRendered(Runnable listener);

    /**
     * Selects {@code tab} on the open creative inventory, switching first to the page that holds it (both loaders page the
     * tabs, each in its own way). False when the tab could not be selected.
     */
    boolean selectCreativeTab(CreativeModeInventoryScreen screen, CreativeModeTab tab);

    /** The game directory of the run (the run directory), absolute and normalized. */
    Path gameDir();

    boolean isModLoaded(String modId);

    /** A file or directory in the resources of mod {@code modId} ({@code path} with {@code /}), if that mod is loaded and has it. */
    Optional<Path> findModPath(String modId, String path);
}
