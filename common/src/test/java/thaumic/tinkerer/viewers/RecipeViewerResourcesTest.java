/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only
 * code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.viewers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import thaumic.tinkerer.compat.viewer.ViewerKeys;

/**
 * <p>The files around the recipe-viewer pages: the language fragments and the generated language files, the entrypoints
 * of {@code fabric.mod.json}, and the rule that the shared page package knows neither EMI nor JEI. Read from the
 * repository root (the working directory of the tests).</p>
 */
class RecipeViewerResourcesTest {
    private static final Path LANG = Path.of("common", "src", "main", "resources", "assets", "thaumictinkerer", "lang");
    private static final Path COMPAT = Path.of("compat", "src", "main", "java", "thaumic", "tinkerer", "compat");
    /** The languages whose fragment carries only the necromancy title (the 1.7.10 translations of {@code tile.spawner.name}). */
    private static final List<String> TITLE_ONLY = List.of("de_de", "fr_fr", "ko_kr", "zh_cn");

    private static JsonObject json(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), path + " exists (tests run in the repository root)");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static JsonObject fragment(String lang) throws IOException {
        return json(Path.of("lang-extra", "recipe_viewers." + lang + ".json"));
    }

    @Test
    void englishAndRussianHaveEveryKeyOfThePages() throws IOException {
        JsonObject english = fragment("en_us");
        JsonObject russian = fragment("ru_ru");
        assertEquals(english.keySet(), russian.keySet(), "the same keys in both fragments");
        assertEquals(new java.util.TreeSet<>(ViewerKeys.all()), new java.util.TreeSet<>(english.keySet()),
                "the fragments hold exactly the keys the pages use");
        for (String key : english.keySet()) {
            assertTrue(!english.get(key).getAsString().isBlank() && !russian.get(key).getAsString().isBlank(), key);
        }
    }

    @Test
    void theFormatArgumentsAreTheOnesThePagesPass() throws IOException {
        Map<String, Integer> arguments = Map.of(ViewerKeys.CHANCE, 1, ViewerKeys.PAGE, 2);
        Pattern format = Pattern.compile("%(?:\\d+\\$)?s|%%|%");
        List<String> problems = new ArrayList<>();
        for (String lang : List.of("en_us", "ru_ru")) {
            JsonObject texts = fragment(lang);
            for (String key : texts.keySet()) {
                Matcher m = format.matcher(texts.get(key).getAsString());
                int count = 0;
                while (m.find()) {
                    if (m.group().equals("%")) problems.add(lang + " " + key + ": a lone %");
                    else if (!m.group().equals("%%")) count++;
                }
                if (count != arguments.getOrDefault(key, 0)) problems.add(lang + " " + key + ": " + count + " arguments");
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void theTitlesAreTheTextsTheNeiPagesShowed() throws IOException {
        JsonObject english = fragment("en_us");
        JsonObject base = json(LANG.resolve("en_us.json"));
        // 1.7.10 NEINecromancyHandler.getRecipeName: tile.spawner.name, block.thaumictinkerer.spawner in the port
        assertEquals(base.get("block.thaumictinkerer.spawner").getAsString(), english.get(ViewerKeys.NECROMANCY_TITLE).getAsString());
        // NEIInfusedSeedRecipeHandler.getRecipeName: tt.nei.category.infusedseeds; the tooltip: tt.nei.chance + ": "
        assertEquals(base.get("tt.nei.category.infusedseeds").getAsString(), english.get(ViewerKeys.SEED_DROPS_TITLE).getAsString());
        assertEquals(base.get("tt.nei.chance").getAsString() + ": %s", english.get(ViewerKeys.CHANCE).getAsString());
        for (String lang : List.of("ru_ru", "de_de", "fr_fr", "ko_kr", "zh_cn")) {
            assertEquals(json(LANG.resolve(lang + ".json")).get("block.thaumictinkerer.spawner").getAsString(),
                    fragment(lang).get(ViewerKeys.NECROMANCY_TITLE).getAsString(), lang + ": the original's name of the tablet");
        }
        for (String lang : TITLE_ONLY) {
            assertEquals(java.util.Set.of(ViewerKeys.NECROMANCY_TITLE), fragment(lang).keySet(), lang + ": only the title (the other texts fall back to English)");
        }
    }

    @Test
    void theGeneratedLanguageFilesCarryTheFragments() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String lang : List.of("en_us", "ru_ru", "de_de", "fr_fr", "ko_kr", "zh_cn")) {
            JsonObject texts = fragment(lang);
            JsonObject generated = json(LANG.resolve(lang + ".json"));
            for (String key : texts.keySet()) {
                if (!generated.has(key) || !generated.get(key).getAsString().equals(texts.get(key).getAsString())) {
                    missing.add(lang + ": " + key + " (run tools/porting.sh tt_assets.py)");
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void fabricLoadsBothPluginsThroughTheirEntrypoints() throws IOException {
        JsonObject entrypoints = json(Path.of("fabric", "src", "main", "resources", "fabric.mod.json")).getAsJsonObject("entrypoints");
        assertEquals("thaumic.tinkerer.compat.emi.TinkererEmiPlugin", entrypoints.getAsJsonArray("emi").get(0).getAsString());
        assertEquals("thaumic.tinkerer.compat.jei.TinkererJeiPlugin", entrypoints.getAsJsonArray("jei_mod_plugin").get(0).getAsString());
        String emi = Files.readString(COMPAT.resolve("emi").resolve("TinkererEmiPlugin.java"));
        assertTrue(emi.contains("@EmiEntrypoint") && emi.contains("implements EmiPlugin"), "EMI plugin for Fabric and NeoForge");
        String jei = Files.readString(COMPAT.resolve("jei").resolve("TinkererJeiPlugin.java"));
        assertTrue(jei.contains("@JeiPlugin") && jei.contains("implements IModPlugin"), "JEI plugin for Fabric and NeoForge");
        // EMI skips the JEI plugins of mods that have an EMI plugin by the namespace of the plugin uid
        assertTrue(jei.contains("LibMisc.id(\"jei\")"), "the JEI plugin uid is in the add-on's namespace");
    }

    @Test
    void theSharedPagesKnowNeitherEmiNorJeiNorTheClient() throws IOException {
        List<String> offending = new ArrayList<>();
        try (Stream<Path> files = Files.list(COMPAT.resolve("viewer"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("import ") && (trimmed.contains("dev.emi.") || trimmed.contains("mezz.jei.")
                            || trimmed.contains("net.minecraft.client.") || trimmed.contains("snownee.jade."))) {
                        offending.add(file.getFileName() + ": " + trimmed);
                    }
                }
            }
        }
        assertEquals(List.of(), offending, "compat/viewer is loaded by the GameTest server and by both plugins");
    }
}
