package thaumic.tinkerer.mpserver;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /ttmp <action> <name> <id> [arg]}: how the clients of the multiplayer check talk to the server's harness, over their
 * real connection, as chat commands that every player may use. Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart);
 * CC BY-NC-SA 3.0 like the add-on (see NOTICE.md); registered only by the server harness ({@link MultiplayerServer}), which no
 * release jar contains.
 *
 * <p>The answer is a system message to the sender, {@code [ttmp] <id> <STATUS> key=value ... | text}: {@code OK}, {@code WAIT} (ask
 * again), {@code PASS}, {@code FAIL} or {@code ERROR}. The command only moves the sender, records and reads; nothing it does
 * stands in for an action of the add-on that a scenario tests.</p>
 */
final class TtmpCommand {
    private TtmpCommand() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, ServerReport report) {
        dispatcher.register(Commands.literal("ttmp")
                .then(Commands.argument("action", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                        .executes(context -> run(context, null, report))
                                        .then(Commands.argument("arg", StringArgumentType.greedyString())
                                                .executes(context -> run(context, StringArgumentType.getString(context, "arg"), report)))))));
    }

    private static int run(CommandContext<CommandSourceStack> context, @Nullable String arg, ServerReport report)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String action = StringArgumentType.getString(context, "action");
        String name = StringArgumentType.getString(context, "name");
        int id = IntegerArgumentType.getInteger(context, "id");
        String answer;
        try {
            answer = switch (action) {
                case "stage" -> Scenes.stage(player, name, report);
                case "check" -> Scenes.check(player, name, arg, report);
                case "ready" -> Scenes.ready(player, report);
                case "saw" -> Scenes.saw(player, arg, report);
                case "note" -> {
                    // A client's own result, kept next to the server's (the client's report has it as well).
                    report.event("client_note", player.getGameProfile().getName(), name + " " + (arg == null ? "" : arg));
                    yield "OK";
                }
                default -> "ERROR unknown action " + action;
            };
        } catch (RuntimeException | LinkageError e) {
            report.add(name, action + "_threw", ServerReport.Result.HARNESS_ERROR, e.toString());
            MultiplayerServer.LOG.error("/ttmp {} {} threw", action, name, e);
            answer = "ERROR " + e;
        }
        report.event("command", player.getGameProfile().getName(), action + " " + name + " " + id + (arg == null ? "" : " " + arg)
                + " -> " + answer);
        player.sendSystemMessage(Component.literal("[ttmp] " + id + " " + answer));
        return 1;
    }
}
