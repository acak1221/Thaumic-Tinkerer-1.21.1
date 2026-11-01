/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the client registrations of the wand foci, called
 * from {@code Foci.registerClient()}. Distributed under the same licence as the add-on (CC BY-NC-SA 3.0, see
 * NOTICE.md).
 */
package thaumic.tinkerer.client;

import thaumic.tinkerer.client.core.handler.DislocationHUDHandler;

/**
 * Client side of the wand foci: the readout of the Focus of Dislocation beside the wand's dial. (The client's own progress of
 * the Mending and Efreet's Flame foci is player data of its {@code LocalPlayer}, which ends with the world; nothing is cleaned up
 * here any more.)
 *
 * <p>The item models, the ornaments and the depth layers are resources; the wand draws them from the focus's textures.</p>
 */
public final class FociClient {
    private FociClient() {}

    public static void register() {
        DislocationHUDHandler.register();
    }
}
