package thaumic.tinkerer.clientsmoke;

import dev.architectury.event.events.client.ClientPlayerEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.core.handler.ConfigSync;

/**
 * Scenario b of the multiplayer check: a client whose own config differs from the server's in an option that decides what is
 * registered. Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md),
 * not distributed.
 *
 * <ul>
 *   <li>Role {@code mismatch}: {@code imbuedFire.enabled=false} in its file (the server has {@code true}); that option changes
 *   research and recipes, not the registries. In the configuration phase the client gets the server's {@code PacketConfigSync} and
 *   answers with its own options; the server must refuse it before it gets into the game, with the translated message
 *   {@code thaumictinkerer.config.mismatch} naming the option.</li>
 *   <li>Role {@code kami-on}: {@code kami.forceenabled=true} (the default) in its file, on the run's second server, whose config has
 *   {@code false}: this client has the Kami registry entries the server lacks, which the loader's registry sync lets through. It
 *   must be refused with the add-on's message, as {@code mismatch} is.</li>
 *   <li>Role {@code mismatch-kami}: {@code kami.forceenabled=false} in its file (the server has {@code true}); without it the
 *   client registers none of the Kami items and blocks, so the server has registry entries this client lacks. The loader's
 *   registry sync runs in the configuration phase too, before the add-on's task (on both loaders). The harness records which of
 *   the two disconnects the client: it must not get into the game, and the message it shows is recorded as it is.</li>
 * </ul>
 * A picture of the disconnect screen is taken either way.
 */
final class MultiplayerMismatchPhase extends ScriptedPhase {
    private final String phase;
    /** The option as {@link ConfigHandler#registrationOptions()} names it. */
    private final String option;
    /** The server's value of the option; this client's own file has the other. */
    private final boolean serverValue;
    /** True when the add-on's message must be the one shown; false when the loader's registry sync comes first. */
    private final boolean addonMessageExpected;

    /**
     * Set when the client entered the game (Architectury's {@code CLIENT_PLAYER_JOIN}, posted on the login packet). A refused client
     * never does: the add-on's handshake and the loader's registry sync both run in the configuration phase, before that packet.
     */
    private volatile boolean joined;

    MultiplayerMismatchPhase(String phase, String option, boolean serverValue, boolean addonMessageExpected) {
        this.phase = phase;
        this.option = option;
        this.serverValue = serverValue;
        this.addonMessageExpected = addonMessageExpected;
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> joined = true);
    }

    @Override
    public String name() {
        return phase;
    }

    /** The reason a disconnect screen shows: the field of type {@link DisconnectionDetails} (no getter in 1.21.1). */
    static Component reason(DisconnectedScreen screen) throws ReflectiveOperationException {
        for (Field field : DisconnectedScreen.class.getDeclaredFields()) {
            if (field.getType() == DisconnectionDetails.class) {
                field.setAccessible(true);
                return ((DisconnectionDetails) field.get(screen)).reason();
            }
        }
        throw new NoSuchFieldException("DisconnectedScreen has no DisconnectionDetails field: " + Arrays.toString(DisconnectedScreen.class.getDeclaredFields()));
    }

    /**
     * The screen that ends a refused join: the vanilla {@link DisconnectedScreen}, or a screen of the loader's own (NeoForge shows
     * the missing registry entries on one of its own). Any screen after the connection attempt that is not the title screen counts.
     */
    private static boolean ended(Minecraft client) {
        Screen screen = client.screen;
        if (screen instanceof DisconnectedScreen) return true;
        return screen != null && client.getConnection() == null && client.level == null
                && screen.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("disconnect");
    }

    @Override
    protected List<Script> scripts(SmokeRun run) {
        List<Script> scripts = new ArrayList<>();
        scripts.add(new Script(phase)
                .until("this client is disconnected", () -> ended(Minecraft.getInstance()), 300)
                .frames(10)
                .then("picture", run.shots().shot(phase + "-disconnected", MultiplayerClient.GROUP,
                        "The disconnect screen of a client whose config has " + option + "=" + !serverValue + " (the server has " + serverValue + ")"))
                .run("record", r -> {
                    Minecraft client = r.client();
                    Screen screen = client.screen;
                    Component reason = screen instanceof DisconnectedScreen disconnected ? reason(disconnected) : screen.getNarrationMessage();
                    String key = reason.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "(not translatable: "
                            + reason.getContents().getClass().getName() + ")";
                    String text = reason.getString();
                    String expectedStart = "This server's Thaumic Tinkerer config differs from yours";
                    boolean addonMessage = key.equals(ConfigSync.MISMATCH) && text.startsWith(expectedStart)
                            && text.contains(option + " (server: " + serverValue + ", yours: " + !serverValue + ")");
                    String own = "own file: " + ConfigHandler.registrationOptions();
                    r.report().fact("disconnect_screen", screen.getClass().getName());
                    r.report().fact("disconnect_title", screen.getTitle().getString());
                    r.report().fact("disconnect_reason", text);
                    if (addonMessageExpected) {
                        MultiplayerClient.record(r, phase, "mismatch_disconnect_message", option, addonMessage, own + "; the screen \""
                                + screen.getTitle().getString() + "\" shows the component " + key + ", rendered: " + text);
                        // The server refuses it in the configuration phase (ConfigSync), so it never enters the game.
                        MultiplayerClient.record(r, phase, "refused_before_the_game", option, !joined,
                                "this client entered the server's game (CLIENT_PLAYER_JOIN) before it was refused: " + joined);
                    } else {
                        // The loader refuses the join first; the add-on's message cannot be shown, and the client must not get in.
                        MultiplayerClient.record(r, phase, "refused_before_the_game", option, !joined, own + "; entered the game: " + joined
                                + "; the screen " + screen.getClass().getSimpleName() + " \"" + screen.getTitle().getString() + "\" shows "
                                + (addonMessage ? "the add-on's message" : "the loader's message") + " (" + key + "): " + text);
                    }
                    MultiplayerClient.record(r, phase, "synced_copy_cleared_after_leaving", "", ConfigHandler.fromServer() == null,
                            "ConfigHandler.fromServer()=" + ConfigHandler.fromServer());
                }));
        return scripts;
    }
}
