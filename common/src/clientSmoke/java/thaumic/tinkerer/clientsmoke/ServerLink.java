package thaumic.tinkerer.clientsmoke;

import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.events.client.ClientChatEvent;
import dev.architectury.event.events.client.ClientSystemMessageEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;

/**
 * The multiplayer client's line to the server harness ({@code common/src/multiplayerServer}): it sends
 * {@code /ttmp <action> <name> <id> [arg]} as a chat command over the real connection and waits for the system message
 * {@code [ttmp] <id> <STATUS> key=value ... | text} that answers it. Test-only code, new in the 1.21.1 port (no 1.7.10
 * counterpart); CC BY-NC-SA 3.0 like the add-on (see NOTICE.md), not distributed.
 *
 * <p>Only the coordination goes this way (where to stand, what the server found). What a scenario tests is done by the client
 * the way a player does it (keys, container clicks, screen buttons), so it crosses the same connection as the add-on's own
 * packets.</p>
 */
final class ServerLink {
    /** One answer of the server. */
    record Reply(int id, String status, Map<String, String> values, String text, String raw) {
        String get(String key) {
            return values.getOrDefault(key, "");
        }

        boolean passed() {
            return status.equals("PASS");
        }
    }

    private final Map<Integer, Reply> replies = new ConcurrentHashMap<>();
    /** The ids of the replies already taken (a message may be heard by both events). */
    private final java.util.Set<Integer> seen = ConcurrentHashMap.newKeySet();
    private final List<String> received = Collections.synchronizedList(new ArrayList<>());
    private int next = 1;
    private boolean installed;

    void install() {
        if (installed) return;
        installed = true;
        // Architectury 13.0.11 posts a system message as ClientSystemMessageEvent on Fabric, but on NeoForge only as ClientChatEvent
        // (from ClientChatReceivedEvent, whose System subclass is the system message): both are heard, a reply counts once.
        ClientSystemMessageEvent.RECEIVED.register(message -> {
            heard(message.getString());
            return CompoundEventResult.pass();
        });
        ClientChatEvent.RECEIVED.register((type, message) -> {
            heard(message.getString());
            return CompoundEventResult.pass();
        });
    }

    private void heard(String text) {
        if (!text.startsWith("[ttmp] ")) {
            if (received.size() < 400) received.add(text);
            return;
        }
        Reply reply = parse(text);
        if (reply == null || !seen.add(reply.id())) return;
        if (received.size() < 400) received.add(text);
        replies.put(reply.id(), reply);
    }

    /** The system messages received so far (the first 400), for the report. */
    List<String> received() {
        synchronized (received) {
            return new ArrayList<>(received);
        }
    }

    static Reply parse(String text) {
        String body = text.substring("[ttmp] ".length());
        String free = "";
        int bar = body.indexOf(" | ");
        if (bar >= 0) {
            free = body.substring(bar + 3);
            body = body.substring(0, bar);
        }
        String[] tokens = body.trim().split(" ");
        if (tokens.length < 2) return null;
        int id;
        try {
            id = Integer.parseInt(tokens[0]);
        } catch (NumberFormatException e) {
            return null;
        }
        Map<String, String> values = new LinkedHashMap<>();
        StringBuilder rest = new StringBuilder();
        for (int i = 2; i < tokens.length; i++) {
            int eq = tokens[i].indexOf('=');
            if (eq > 0) values.put(tokens[i].substring(0, eq), tokens[i].substring(eq + 1));
            else rest.append(rest.length() == 0 ? "" : " ").append(tokens[i]);
        }
        String all = rest.length() == 0 ? free : free.isEmpty() ? rest.toString() : rest + " | " + free;
        return new Reply(id, tokens[1], values, all, text);
    }

    /**
     * A step that sends the command once and finishes when its answer is there ({@code into[0]}). An answer {@code WAIT} sends
     * the command again two seconds later, until the step's time limit.
     */
    Script.Step ask(String action, String name, String arg, Reply[] into) {
        return ask(action, name, () -> arg, into);
    }

    /** {@link #ask(String, String, String, Reply[])} with an argument that is known only when the step runs. */
    Script.Step ask(String action, String name, java.util.function.Supplier<String> argument, Reply[] into) {
        int[] id = { -1 };
        long[] retryAt = { 0 };
        boolean[] first = { true };
        return run -> {
            Minecraft client = run.client();
            if (client.getConnection() == null) throw new IllegalStateException("not connected: cannot ask the server " + action + " " + name);
            long now = System.nanoTime();
            if (id[0] >= 0) {
                Reply reply = replies.remove(id[0]);
                if (reply != null && reply.status().equals("WAIT")) {
                    id[0] = -1;
                    retryAt[0] = now + 2_000_000_000L;
                } else if (reply != null) {
                    into[0] = reply;
                    return true;
                }
            }
            if (id[0] < 0 && (first[0] || now - retryAt[0] >= 0)) {
                first[0] = false;
                id[0] = next++;
                String arg = argument.get();
                client.getConnection().sendCommand("ttmp " + action + " " + name + " " + id[0] + (arg == null || arg.isEmpty() ? "" : " " + arg));
            }
            return false;
        };
    }
}
