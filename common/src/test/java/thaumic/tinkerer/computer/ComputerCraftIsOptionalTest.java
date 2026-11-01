/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only
 * code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.computer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * CC: Tweaked is optional (docs/deviations/ComputerCraft.md): the add-on must load and work without it, so no class that a
 * game without CC: Tweaked loads may name one of its classes. The sources are read (the tests run in the repository root):
 * only the package {@code compat/.../computer/cc} and the four loader glue classes import its API, only the three entrypoints
 * name those, and they do so behind {@code Computers.ccTweakedLoaded()}. The client smoke and the multiplayer check run
 * without CC: Tweaked and show the same at runtime.
 */
class ComputerCraftIsOptionalTest {
    private static final Path CC_PACKAGE = Path.of("compat", "src", "main", "java", "thaumic", "tinkerer", "compat", "computer", "cc");
    private static final List<Path> GLUE = List.of(
            Path.of("fabric", "src", "main", "java", "thaumic", "tinkerer", "fabric", "compat", "ComputerCraftFabric.java"),
            Path.of("fabric", "src", "main", "java", "thaumic", "tinkerer", "fabric", "client", "ComputerCraftFabricClient.java"),
            Path.of("neoforge", "src", "main", "java", "thaumic", "tinkerer", "neoforge", "compat", "ComputerCraftNeoForge.java"),
            Path.of("neoforge", "src", "main", "java", "thaumic", "tinkerer", "neoforge", "client", "ComputerCraftNeoForgeClient.java"));
    private static final List<String> GLUE_NAMES = List.of("ComputerCraftFabric", "ComputerCraftFabricClient", "ComputerCraftNeoForge",
            "ComputerCraftNeoForgeClient", "compat.computer.cc.");
    private static final List<Path> ENTRYPOINTS = List.of(
            Path.of("fabric", "src", "main", "java", "thaumic", "tinkerer", "fabric", "ThaumicTinkererFabric.java"),
            Path.of("fabric", "src", "main", "java", "thaumic", "tinkerer", "fabric", "ThaumicTinkererFabricClient.java"),
            Path.of("neoforge", "src", "main", "java", "thaumic", "tinkerer", "neoforge", "ThaumicTinkererNeoForge.java"));
    private static final List<Path> TREES = List.of(Path.of("common", "src", "main", "java"), Path.of("compat", "src", "main", "java"),
            Path.of("fabric", "src", "main", "java"), Path.of("neoforge", "src", "main", "java"));

    private static List<Path> sources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (Path tree : TREES) {
            assertTrue(Files.isDirectory(tree), "the tests run in the repository root: " + tree.toAbsolutePath());
            try (Stream<Path> files = Files.walk(tree)) {
                files.filter(f -> f.toString().endsWith(".java")).forEach(out::add);
            }
        }
        return out;
    }

    private static boolean isComputerCraftCode(Path file) {
        return file.startsWith(CC_PACKAGE) || GLUE.contains(file);
    }

    @Test
    void onlyTheComputerCraftPackageAndTheGlueNameItsApi() throws IOException {
        List<String> offending = new ArrayList<>();
        int found = 0;
        for (Path file : sources()) {
            boolean names = Files.readString(file).contains("dan200.computercraft");
            if (names) found++;
            if (names && !isComputerCraftCode(file)) offending.add(file.toString());
        }
        assertEquals(List.of(), offending, "files outside compat/.../computer/cc and the loader glue name CC: Tweaked");
        assertTrue(found >= GLUE.size() + 4, "the integration is there: " + found + " files");
        for (Path glue : GLUE) assertTrue(Files.isRegularFile(glue), glue + " exists");
    }

    @Test
    void onlyTheEntrypointsNameTheGlue() throws IOException {
        List<String> offending = new ArrayList<>();
        for (Path file : sources()) {
            if (isComputerCraftCode(file) || ENTRYPOINTS.contains(file)) continue;
            String text = Files.readString(file);
            for (String name : GLUE_NAMES) {
                if (text.matches("(?s).*\\b" + java.util.regex.Pattern.quote(name) + "\\b.*")) offending.add(file + " names " + name);
            }
        }
        assertEquals(List.of(), offending);
    }

    @Test
    void theEntrypointsAskBeforeTheyTouchTheGlue() throws IOException {
        int uses = 0;
        for (Path entrypoint : ENTRYPOINTS) {
            List<String> lines = Files.readAllLines(entrypoint);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.startsWith("import ") || line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) continue;
                if (GLUE_NAMES.stream().noneMatch(line::contains)) continue;
                uses++;
                assertTrue(guarded(lines, i), entrypoint + ":" + (i + 1) + " touches the CC: Tweaked glue without asking first: " + line);
            }
        }
        assertEquals(4, uses, "the four registrations: Fabric common and client, NeoForge common and client");
    }

    /** The line asks itself, or it lies inside the block of an {@code if (Computers.ccTweakedLoaded()) {}}. */
    private static boolean guarded(List<String> lines, int index) {
        if (lines.get(index).contains("Computers.ccTweakedLoaded()")) return true;
        int depth = 0;
        for (int i = index - 1; i >= 0; i--) {
            String line = lines.get(i);
            for (int c = line.length() - 1; c >= 0; c--) {
                if (line.charAt(c) == '}') depth++;
                else if (line.charAt(c) == '{') depth--;
            }
            if (depth < 0) {
                if (line.contains("if (Computers.ccTweakedLoaded())")) return true;
                depth = 0; // an inner block (another if, a lambda): keep looking outwards
            }
        }
        return false;
    }

    @Test
    void theModsDeclareCcTweakedAsOptional() throws IOException {
        JsonObject fabric = JsonParser.parseString(Files.readString(Path.of("fabric", "src", "main", "resources", "fabric.mod.json"))).getAsJsonObject();
        assertTrue(fabric.getAsJsonObject("suggests").has("computercraft"), "fabric.mod.json suggests CC: Tweaked");
        assertFalse(fabric.getAsJsonObject("depends").has("computercraft"), "and does not depend on it");
        String toml = Files.readString(Path.of("neoforge", "src", "main", "resources", "META-INF", "neoforge.mods.toml"));
        int at = toml.indexOf("modId=\"computercraft\"");
        assertTrue(at > 0, "neoforge.mods.toml names CC: Tweaked");
        String entry = toml.substring(at, Math.min(toml.length(), at + 120));
        assertTrue(entry.contains("type=\"optional\""), "as an optional dependency: " + entry);
    }

    @Test
    void theTurtleUpgradeIsADataFileThatOnlyCcTweakedReads() throws IOException {
        Path file = Path.of("common", "src", "main", "resources", "data", "thaumictinkerer", "computercraft", "turtle_upgrade", "gas_remover.json");
        JsonObject upgrade = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("thaumictinkerer:gas_remover", upgrade.get("type").getAsString(), "the add-on's upgrade type");
        assertEquals("thaumictinkerer:gas_remover", upgrade.get("item").getAsString(), "crafted with the Fume Dissipator");
    }
}
