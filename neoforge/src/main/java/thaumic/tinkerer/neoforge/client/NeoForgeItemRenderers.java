/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import thaumic.tinkerer.client.hooks.ItemRenderers;

/**
 * The NeoForge backend of {@link ItemRenderers} (the counterpart of {@code FabricItemRenderers}). NeoForge takes a built-in
 * item renderer only inside {@link RegisterClientExtensionsEvent}, as an {@link IClientItemExtensions} whose
 * {@link IClientItemExtensions#getCustomRenderer() custom renderer} is a {@link BlockEntityWithoutLevelRenderer}; its
 * {@code ItemRenderer} calls that renderer for every item whose model is {@code minecraft:builtin/entity} (the Mob Display's
 * is). So the backend only collects: {@link #install()} runs from the mod constructor, before the shared client setup queues
 * the renderers, and {@link #registerAll} hands them to the event after that setup ran. A renderer that arrives later than
 * the event is an {@code IllegalStateException}, which the group's guard records.
 */
public final class NeoForgeItemRenderers {
    private static final List<Map.Entry<Item, ItemRenderers.Renderer>> COLLECTED = new ArrayList<>();
    private static boolean installed;
    private static boolean handedOver;

    private NeoForgeItemRenderers() {}

    /** Installs the backend (and takes what the groups asked for before). Safe to call more than once. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        ItemRenderers.setBackend(NeoForgeItemRenderers::collect);
    }

    private static synchronized void collect(Item item, ItemRenderers.Renderer renderer) {
        if (handedOver) {
            throw new IllegalStateException("NeoForge has collected the client item extensions already; too late for the built-in renderer of " + item);
        }
        COLLECTED.add(Map.entry(item, renderer));
    }

    /** From {@link RegisterClientExtensionsEvent}, after the shared client setup: one extension per collected item. */
    public static synchronized void registerAll(RegisterClientExtensionsEvent event) {
        if (handedOver) return;
        handedOver = true;
        for (Map.Entry<Item, ItemRenderers.Renderer> entry : COLLECTED) {
            event.registerItem(extensions(entry.getValue()), entry.getKey());
        }
    }

    /**
     * The extension of one item. The renderer object is made on first use: when the event is posted, the game has its block
     * entity render dispatcher and model set, but nothing is drawn before the first resource reload anyway.
     */
    static IClientItemExtensions extensions(ItemRenderers.Renderer renderer) {
        return new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer adapter;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (adapter == null) adapter = new Adapter(renderer);
                return adapter;
            }
        };
    }

    /** A {@link BlockEntityWithoutLevelRenderer} that hands {@code renderByItem} to the add-on's renderer, argument for argument. */
    private static final class Adapter extends BlockEntityWithoutLevelRenderer {
        private final ItemRenderers.Renderer renderer;

        Adapter(ItemRenderers.Renderer renderer) {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
            this.renderer = renderer;
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                int light, int overlay) {
            renderer.render(stack, context, pose, buffers, light, overlay);
        }
    }
}
