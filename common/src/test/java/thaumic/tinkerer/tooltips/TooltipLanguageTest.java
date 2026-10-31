/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only
 * code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.tooltips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thaumic.tinkerer.compat.tooltip.AnimationTabletTooltip;
import thaumic.tinkerer.compat.tooltip.BlockTooltip;
import thaumic.tinkerer.compat.tooltip.MagnetTooltip;
import thaumic.tinkerer.compat.tooltip.RepairerTooltip;
import thaumic.tinkerer.compat.tooltip.TinkererTooltips;
import thaumic.tinkerer.compat.tooltip.TooltipKeys;
import thaumic.tinkerer.compat.tooltip.TransvectorInterfaceTooltip;
import thaumic.tinkerer.compat.tooltip.WarpGateTooltip;

/**
 * The texts of the block tooltips in the generated language files (run from the repository root): every key the lines
 * use exists with as many {@code %s} as the lines pass, every Jade option has its label, and the lines read as they
 * should in English and Russian (the language is put in place the way the client does: the chosen file over en_us).
 */
class TooltipLanguageTest {
    private static final Path LANG = Path.of("common", "src", "main", "resources", "assets", "thaumictinkerer", "lang");
    private static final List<String> LOCALES = List.of("en_us", "ru_ru", "de_de", "fr_fr", "ko_kr", "zh_cn");
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?s");
    private static Language before;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        before = Language.getInstance();
    }

    @AfterAll
    static void restore() {
        Language.inject(before);
    }

    private static Map<String, String> load(String locale) throws IOException {
        Path file = LANG.resolve(locale + ".json");
        assertTrue(Files.isRegularFile(file), "the generated language file exists (tests run in the repository root): " + file.toAbsolutePath());
        Map<String, String> map = new HashMap<>();
        try (InputStream in = Files.newInputStream(file)) {
            Language.loadFromJson(in, map::put);
        }
        return map;
    }

    private static int placeholders(String text) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static void use(Map<String, String> texts) {
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return texts.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return texts.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.EMPTY;
            }
        });
    }

    private static List<String> strings(List<Component> lines) {
        return lines.stream().map(Component::getString).toList();
    }

    @Test
    void everyKeyOfTheLinesIsInEnglishAndRussianWithItsArguments() throws IOException {
        for (String locale : List.of("en_us", "ru_ru")) {
            Map<String, String> texts = load(locale);
            for (TooltipKeys.KeyArgs key : TooltipKeys.ALL) {
                assertTrue(texts.containsKey(key.key()), locale + " has " + key.key());
                assertEquals(key.args(), placeholders(texts.get(key.key())), locale + " " + key.key() + ": " + texts.get(key.key()));
            }
        }
    }

    @Test
    void theOtherLanguagesTakeAsManyArgumentsWhereTheyHaveTheKey() throws IOException {
        for (String locale : LOCALES) {
            Map<String, String> texts = load(locale);
            for (TooltipKeys.KeyArgs key : TooltipKeys.ALL) {
                if (texts.containsKey(key.key())) {
                    assertEquals(key.args(), placeholders(texts.get(key.key())), locale + " " + key.key() + ": " + texts.get(key.key()));
                }
            }
        }
    }

    @Test
    void everyJadeOptionHasALabel() throws IOException {
        for (String locale : LOCALES) {
            Map<String, String> texts = load(locale);
            for (BlockTooltip<?> tooltip : TinkererTooltips.ALL) {
                String label = texts.get(tooltip.configKey());
                assertTrue(label != null && !label.isBlank(), locale + " labels " + tooltip.configKey());
            }
        }
    }

    @Test
    void theLinesReadInEnglish() throws IOException {
        Map<String, String> texts = load("en_us");
        texts.put("entity.minecraft.zombie", "Zombie"); // the game's own text (not in the add-on's files)
        use(texts);
        assertEquals(List.of("Current Tool: Nothing"), strings(AnimationTabletTooltip.linesFor(null, true, false)));
        assertEquals(List.of("Current Tool: Pick", "Right Click", "Redstone Activated"),
                strings(AnimationTabletTooltip.linesFor(Component.literal("Pick"), false, true)));
        assertEquals(List.of("Connected to: Nothing"), strings(TransvectorInterfaceTooltip.linesFor(null, null)));
        assertEquals(List.of("Connected to: Chest", "x: 1 y: 2 z: 3"),
                strings(TransvectorInterfaceTooltip.linesFor(Component.literal("Chest"), new BlockPos(1, 2, 3))));
        assertEquals(List.of("Repairing: Sword"), strings(RepairerTooltip.linesFor(Component.literal("Sword"), true)));
        assertEquals(List.of("Finished repairing: Sword"), strings(RepairerTooltip.linesFor(Component.literal("Sword"), false)));
        assertEquals(List.of("Doesn't allow incoming teleports"), strings(WarpGateTooltip.linesFor(true)));
        assertEquals(List.of("Allows incoming teleports"), strings(WarpGateTooltip.linesFor(false)));
        assertEquals(List.of("Mode: Pushing"), strings(MagnetTooltip.linesFor(false, false, true, null)));
        assertEquals(List.of("Mode: Pulling", "Pulling: Children"), strings(MagnetTooltip.linesFor(true, true, false, null)));
        assertEquals(List.of("Mode: Pushing", "Pushing: Adult No Pattern"), strings(MagnetTooltip.linesFor(false, true, true, "")));
        assertEquals(List.of("Mode: Pulling", "Pulling: Adult Zombie"), strings(MagnetTooltip.linesFor(true, true, true, "minecraft:zombie")));
        assertEquals(List.of("Mode: Pulling", "Pulling: Child othermod:beast"),
                strings(MagnetTooltip.linesFor(true, true, false, "othermod:beast")));
    }

    @Test
    void theLinesReadInRussian() throws IOException {
        Map<String, String> texts = load("en_us");
        texts.putAll(load("ru_ru"));
        texts.put("entity.minecraft.zombie", "Зомби"); // the game's own text (not in the add-on's files)
        use(texts);
        assertEquals(List.of("Инструмент: Ничего"), strings(AnimationTabletTooltip.linesFor(null, true, false)));
        assertEquals(List.of("Инструмент: Кирка", "ЛКМ", "Автономно"),
                strings(AnimationTabletTooltip.linesFor(Component.literal("Кирка"), true, false)));
        assertEquals(List.of("Не принимает входящие телепортации"), strings(WarpGateTooltip.linesFor(true)));
        assertEquals(List.of("Принимает входящие телепортации"), strings(WarpGateTooltip.linesFor(false)));
        assertEquals(List.of("Восстановление завершено: Меч"), strings(RepairerTooltip.linesFor(Component.literal("Меч"), false)));
        assertEquals(List.of("Режим: притягивание", "Притягивает взрослых: Зомби"),
                strings(MagnetTooltip.linesFor(true, true, true, "minecraft:zombie")));
        assertEquals(List.of("Режим: отталкивание", "Отталкивает детёнышей"), strings(MagnetTooltip.linesFor(false, true, false, null)));
        assertEquals(List.of("Режим: отталкивание", "Отталкивает детёнышей: Нет образца души"),
                strings(MagnetTooltip.linesFor(false, true, false, "")));
    }
}
