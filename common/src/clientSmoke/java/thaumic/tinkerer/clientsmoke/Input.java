package thaumic.tinkerer.clientsmoke;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Predicate;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeX11;
import org.lwjgl.system.linux.X11;
import org.lwjgl.system.linux.XEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * What the multiplayer client does with its hands: the keys a player presses (use, attack, sneak, a hotbar slot), the clicks of
 * a container screen and of a screen button. Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like
 * the add-on (see NOTICE.md), not distributed.
 *
 * <p>A key is "pressed" with {@link KeyMapping#click}, which is what the game's keyboard and mouse handlers do for a real key: the
 * next client tick ({@code Minecraft.handleKeybinds}) then runs the game's own {@code startUseItem} or {@code startAttack} on what
 * is in the cross-hair, so the packets the server gets are the ones a real click sends. Sneaking holds the key down
 * ({@link KeyMapping#setDown}), which the game's {@code KeyboardInput} reads every tick and the player sends as
 * {@code PRESS_SHIFT_KEY}.</p>
 */
final class Input {
    private Input() {}

    static InputConstants.Key key(KeyMapping mapping) {
        return InputConstants.getKey(mapping.saveString());
    }

    static void rightClick(Minecraft client) {
        KeyMapping.click(key(client.options.keyUse));
    }

    static void leftClick(Minecraft client) {
        KeyMapping.click(key(client.options.keyAttack));
    }

    static void sneak(Minecraft client, boolean down) {
        client.options.keyShift.setDown(down);
    }

    static void select(Minecraft client, int slot) {
        KeyMapping.click(key(client.options.keyHotbarSlots[slot]));
    }

    /**
     * A press of the add-on's key mapping {@code name} (its translation key). A mapping that is bound to nothing (the add-on's
     * toggle key is unbound by default, as in 1.7.10) is first bound to {@code fallback}, as a player does in the controls screen
     * ({@code setKey}, then {@code KeyMapping.resetMapping}); then the key is clicked as the keyboard handler does.
     */
    static String pressMapping(Minecraft client, String name, String fallback) {
        for (KeyMapping mapping : client.options.keyMappings) {
            if (!mapping.getName().equals(name)) continue;
            if (mapping.isUnbound()) {
                mapping.setKey(InputConstants.getKey(fallback));
                KeyMapping.resetMapping();
            }
            KeyMapping.click(key(mapping));
            return mapping.saveString();
        }
        throw new IllegalStateException("no key mapping " + name);
    }

    /**
     * A real press or release of the left shift key, sent to this client's own window by the X server (Xvfb): an X {@code KeyPress}
     * or {@code KeyRelease} event with the window's own shift key code, which GLFW reads like a key of the keyboard. Unlike
     * {@link KeyMapping#setDown}, this also changes what GLFW answers for the key, which is what {@code Screen.hasShiftDown()} asks
     * (the Celestial Gateway's destinations screen teleports on a shift click only). The window is addressed by its X id, so a second
     * client on the same display gets nothing. Linux/X11 only (the machine of the check).
     */
    static void realShift(Minecraft client, boolean down) {
        long display = GLFWNativeX11.glfwGetX11Display();
        long window = GLFWNativeX11.glfwGetX11Window(client.getWindow().getWindow());
        int keycode = GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_LEFT_SHIFT);
        if (display == 0L || window == 0L || keycode <= 0) {
            throw new IllegalStateException("no X11 window or shift key code: display " + display + ", window " + window + ", key code " + keycode);
        }
        try (XEvent event = XEvent.calloc()) {
            event.xkey().type(down ? X11.KeyPress : X11.KeyRelease).send_event(true).display(display).window(window)
                    .root(X11.XRootWindow(display, X11.XDefaultScreen(display))).subwindow(0L).time(0L).x(1).y(1).x_root(1).y_root(1)
                    // the modifier state before the event: nothing for the press, shift for the release
                    .state(down ? 0 : 1).keycode(keycode).same_screen(true);
            X11.XSendEvent(display, window, false, down ? X11.KeyPressMask : X11.KeyReleaseMask, event);
        }
    }

    /** What GLFW answers for the left shift key now ({@code Screen.hasShiftDown()} asks this). */
    static boolean glfwShiftDown(Minecraft client) {
        return InputConstants.isKeyDown(client.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT);
    }

    /** The block in the cross-hair, or null. */
    @Nullable
    static BlockHitResult aimed(Minecraft client) {
        return client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    static boolean aimsAt(Minecraft client, BlockPos pos, @Nullable Direction face) {
        BlockHitResult hit = aimed(client);
        return hit != null && hit.getBlockPos().equals(pos) && (face == null || hit.getDirection() == face);
    }

    static String describeAim(Minecraft client) {
        HitResult hit = client.hitResult;
        if (hit == null) return "nothing";
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            return "block " + block.getBlockPos().toShortString() + " face " + block.getDirection().getName();
        }
        return hit.getType().name();
    }

    /** The index in the open menu of the slot that shows slot {@code index} of {@code container}, or -1. */
    static int menuSlot(AbstractContainerMenu menu, Predicate<Container> container, int index) {
        for (Slot slot : menu.slots) {
            if (container.test(slot.container) && slot.getContainerSlot() == index) return slot.index;
        }
        return -1;
    }

    /** A left click on a slot of the open container screen: picks up what is there, or puts down what the cursor holds. */
    static void clickSlot(Minecraft client, int menuSlot) {
        AbstractContainerMenu menu = client.player.containerMenu;
        client.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, 0, ClickType.PICKUP, client.player);
    }

    /** The first widget of the screen that is of {@code type}, or null. */
    @Nullable
    static <T extends AbstractWidget> T widget(Screen screen, Class<T> type) {
        for (GuiEventListener child : screen.children()) {
            if (type.isInstance(child)) return type.cast(child);
        }
        return null;
    }

    /** The widgets of the screen that are of {@code type}, in the order the screen added them. */
    static <T extends AbstractWidget> java.util.List<T> widgets(Screen screen, Class<T> type) {
        java.util.List<T> found = new java.util.ArrayList<>();
        for (GuiEventListener child : screen.children()) if (type.isInstance(child)) found.add(type.cast(child));
        return found;
    }

    /** A left mouse click (press and release) in the middle of the widget, through the screen as the mouse handler sends it. */
    static boolean clickWidget(Screen screen, AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        boolean handled = screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
        return handled;
    }
}
