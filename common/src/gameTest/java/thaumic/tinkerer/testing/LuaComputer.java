/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only code, distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.testing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.filesystem.MountConstants;
import dan200.computercraft.api.filesystem.WritableMount;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A real CC: Tweaked computer (or turtle) in a GameTest that runs a Lua program and leaves its answer where the test can read it.
 *
 * <p>Only the CC: Tweaked API and data are used: {@link ComputerCraftAPI#createUniqueNumberedSaveDir} allocates a computer id
 * (and its directory {@code computercraft/computer/<id>} of the world), {@link ComputerCraftAPI#createSaveDirMount} writes the
 * program there as {@code startup.lua}, and the computer block is placed and given the saved fields of a computer that was on
 * ({@code ComputerId}, {@code On}: what CC: Tweaked's block entity saves), so it boots by itself on its next tick, as a computer
 * does when its chunk loads. Its shell runs {@code startup.lua}; the program calls the peripherals through CC: Tweaked's own
 * {@code peripheral} API, wraps the answers into a table and writes it as JSON ({@code textutils.serialiseJSON}) to
 * {@code result.json}, by a rename so that the test never reads half a file. A Lua error inside the program becomes
 * {@code {"error": "..."}}.</p>
 */
final class LuaComputer {
    static final ResourceLocation ADVANCED_COMPUTER = ResourceLocation.fromNamespaceAndPath("computercraft", "computer_advanced");
    static final ResourceLocation NORMAL_TURTLE = ResourceLocation.fromNamespaceAndPath("computercraft", "turtle_normal");
    private static final String RESULT = "result.json";
    private static final String PROGRAM = """
            local ok, value = pcall(function()
            %s
            end)
            local out
            if ok then out = value else out = { error = tostring(value) } end
            local handle = fs.open("result.tmp", "w")
            handle.write(textutils.serialiseJSON(out))
            handle.close()
            fs.move("result.tmp", "%s")
            """;

    final BlockPos pos;
    final int id;
    private final WritableMount mount;

    private LuaComputer(BlockPos pos, int id, WritableMount mount) {
        this.pos = pos;
        this.id = id;
        this.mount = mount;
    }

    /** An advanced computer at {@code pos} (relative) that runs {@code body}, the body of a Lua function returning a table. */
    static LuaComputer computer(GameTestHelper test, BlockPos pos, String body) {
        return place(test, pos, ADVANCED_COMPUTER, new CompoundTag(), body);
    }

    /** A turtle at {@code pos} with the extra saved fields {@code turtle} (its upgrades) that runs {@code body}. */
    static LuaComputer turtle(GameTestHelper test, BlockPos pos, CompoundTag turtle, String body) {
        return place(test, pos, NORMAL_TURTLE, turtle, body);
    }

    private static LuaComputer place(GameTestHelper test, BlockPos pos, ResourceLocation blockId, CompoundTag extra, String body) {
        MinecraftServer server = test.getLevel().getServer();
        int id = ComputerCraftAPI.createUniqueNumberedSaveDir(server, "computer");
        test.assertTrue(id >= 0, "CC: Tweaked gave a computer id: " + id);
        WritableMount mount = ComputerCraftAPI.createSaveDirMount(server, "computer/" + id, 1_000_000L);
        write(mount, "startup.lua", PROGRAM.formatted(body, RESULT));
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        test.assertTrue(block != Blocks.AIR, "CC: Tweaked has the block " + blockId);
        test.setBlock(pos, block);
        BlockEntity computer = test.getBlockEntity(pos);
        test.assertTrue(computer != null, "the computer has its block entity");
        CompoundTag tag = extra.copy();
        tag.putInt("ComputerId", id);
        tag.putBoolean("On", true);
        computer.loadCustomOnly(tag, test.getLevel().registryAccess());
        computer.setChanged();
        return new LuaComputer(pos, id, mount);
    }

    private static void write(WritableMount mount, String path, String text) {
        try (SeekableByteChannel channel = mount.openFile(path, MountConstants.WRITE_OPTIONS)) {
            ByteBuffer bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) channel.write(bytes);
        } catch (IOException error) {
            throw new UncheckedIOException("cannot write " + path, error);
        }
    }

    /** The answer of the program, or null while it has not finished. */
    @Nullable
    JsonObject result() {
        try {
            if (!mount.exists(RESULT)) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (SeekableByteChannel channel = mount.openForRead(RESULT)) {
                ByteBuffer buffer = ByteBuffer.allocate(4096);
                while (channel.read(buffer) > 0) {
                    out.write(buffer.array(), 0, buffer.position());
                    buffer.clear();
                }
            }
            return JsonParser.parseString(out.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException error) {
            throw new UncheckedIOException("cannot read the result of computer " + id, error);
        }
    }

    /**
     * Waits for every computer's answer, then runs {@code check} on them (in the order given) and succeeds. A program that
     * failed with a Lua error fails the test with that error; a computer that has not answered after {@code ticks} ticks fails it
     * too.
     */
    static void whenAnswered(GameTestHelper test, int ticks, List<LuaComputer> computers, Consumer<List<JsonObject>> check) {
        boolean[] done = { false };
        test.onEachTick(() -> {
            if (done[0]) return;
            List<JsonObject> answers = new ArrayList<>();
            for (LuaComputer computer : computers) {
                JsonObject answer = computer.result();
                if (answer == null) return;
                if (answer.has("error")) {
                    done[0] = true;
                    test.fail("the Lua program of computer " + computer.id + " failed: " + answer.get("error").getAsString());
                }
                answers.add(answer);
            }
            done[0] = true;
            check.accept(answers);
            test.succeed();
        });
        test.runAfterDelay(ticks, () -> {
            if (done[0]) return;
            List<Integer> silent = new ArrayList<>();
            for (LuaComputer computer : computers) if (computer.result() == null) silent.add(computer.id);
            done[0] = true;
            test.fail("no answer after " + ticks + " ticks from the computers " + silent);
        });
    }

    // ---- reading the answers ---------------------------------------------------------------------------------------------

    /** The values a call returned, captured in Lua as {@code { p.method(...) }}: a JSON array (an empty table is {@code {}}). */
    static JsonArray values(JsonObject answer, String key) {
        JsonElement element = answer.get(key);
        if (element == null) throw new AssertionError("the program did not answer " + key + ": " + answer);
        if (element.isJsonArray()) return element.getAsJsonArray();
        if (element.isJsonObject() && element.getAsJsonObject().size() == 0) return new JsonArray();
        throw new AssertionError(key + " is not a list of values: " + element);
    }

    /** The strings of a Lua list (a table with the keys 1, 2, 3 ...); an empty table is an empty list. */
    static List<String> strings(JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element.isJsonObject() && element.getAsJsonObject().size() == 0) return out;
        for (JsonElement item : element.getAsJsonArray()) out.add(item.getAsString());
        return out;
    }
}
