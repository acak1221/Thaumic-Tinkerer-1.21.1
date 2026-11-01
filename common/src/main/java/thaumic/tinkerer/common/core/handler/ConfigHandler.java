/**
 * This class was created by <Vazkii>. It's distributed as part of the ThaumicTinkerer Mod.
 *
 * ThaumicTinkerer is Open Source and distributed under a Creative Commons Attribution-NonCommercial-ShareAlike 3.0
 * License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 *
 * Ported to Minecraft 1.21.1 for Thaumcraft 4 Reload (see NOTICE.md).
 */
package thaumic.tinkerer.common.core.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.architectury.platform.Platform;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The options of the 1.7.10 {@code ThaumicTinkerer.cfg}, in {@code config/thaumictinkerer.json}.
 *
 * <p>The field names are the 1.7.10 ones. Options that only existed because of numeric ids (potion ids, enchantment
 * ids, dimension ids) are gone: the potions, enchantments and the Bedrock dimension are registry entries now, and the
 * dimension is a data pack (see docs/DEVIATIONS.md). So are three options that 1.7.10 wrote but nothing of the port reads:
 * {@code tooltipIndicators.enabled} (the [TT] prefix of the research text is in the language files), {@code oreDictMetal.enabled}
 * (the recipes use the shared tags whenever they have members) and {@code repairTconTools.enabled} (no Tinkers' Construct); a
 * file that still has them keeps loading, and they are left out when it is rewritten. The file is written with the defaults the
 * first time and is rewritten with every known key on each load, so new options appear in it; a file that cannot be read or is
 * not a JSON object is left as it is (the defaults hold for that run, and an error names the file).</p>
 *
 * <p><b>Which side's values count.</b> The file of the side that runs the code is read; on a client connected to a dedicated
 * server that is not the server's file. Two kinds of option therefore get help from the server ({@link ConfigSync}, on join):</p>
 * <ul>
 *   <li>The options a client evaluates as well ({@link Shared}: the Focus of Flight moves the client's own player, the client
 *   predicts what the Focus of Dislocation may take, ...): the server sends its values, and the side-aware accessors
 *   ({@link #flightEnabled}, {@link #dislocationBlacklist}, {@link #forbiddenDimensions(BlockGetter)}, {@link #cropsAllowBonemeal(BlockGetter)})
 *   answer with them whenever the level asked about is the client's. The plain fields stay the values of this side's file.</li>
 *   <li>The options that decide what is registered ({@link #registrationOptions()}: items, blocks, research and recipes are
 *   registered on each side from its own file at start-up and cannot change while the game runs): the server sends its values
 *   and a client whose own differ disconnects with a message that names them.</li>
 * </ul>
 * <p>Everything else is read by one side only (the server, or the client's own display: the Soul Heart bar's height, the
 * Looking Glass's ghost blocks).</p>
 */
public final class ConfigHandler {
    private static final Logger LOG = LoggerFactory.getLogger("ThaumicTinkerer");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // general
    /** "Set to true to enable flight in this mod." */
    public static boolean enableFlight = true;
    public static boolean enableSurvivalShareTome = true;
    public static boolean enableDebugCommands = false;
    /** Dimension ids ({@code namespace:path}) where the Bottomless Pouch inventory is refused. */
    public static String[] forbiddenDimensions = {};
    /** Block ids that the Focus of Dislocation refuses to move. */
    public static String[] blockBlacklist = {};
    public static boolean enableFire = true;
    public static boolean enableCake = true;
    public static boolean enableFireMechanics = true;
    public static boolean cropsAllowBonemeal = false;
    /** The Height of the Soul Heart bar. */
    public static int soulHeartHeight = 49;

    // kami
    public static boolean enableKami = true;
    public static boolean showPlacementMirrorBlocks = true;
    public static boolean shouldKamiLegsDoLightFeature = false;
    public static boolean shouldKamiBootsDoGrass = true;
    /** Ore ids ({@code namespace:path} or item tags) that are not spawned in the Bedrock dimension. */
    public static String[] bedrockOreBlacklist = {};
    public static int bedrockOreDensity = 1;

    // balance.shadowbeam (hundredths of vis)
    public static int visShadowbeamAir = 15;
    public static int visShadowbeamOrder = 25;
    public static int visShadowbeamEntropy = 25;
    public static int baseDamageShadowbeam = 8;

    private ConfigHandler() {}

    public static Path file() {
        return Platform.getConfigFolder().resolve("thaumictinkerer.json");
    }

    /** Reads the file (when there is one), applies it and writes it back with every key (see {@link #loadConfig(Path)}). Safe to call twice. */
    public static synchronized void loadConfig() {
        loadConfig(file());
    }

    /** Puts every option back to its 1.7.10 default. */
    public static synchronized void resetToDefaults() {
        enableFlight = true;
        enableSurvivalShareTome = true;
        enableDebugCommands = false;
        forbiddenDimensions = new String[0];
        blockBlacklist = new String[0];
        enableFire = true;
        enableCake = true;
        enableFireMechanics = true;
        cropsAllowBonemeal = false;
        soulHeartHeight = 49;
        enableKami = true;
        showPlacementMirrorBlocks = true;
        shouldKamiLegsDoLightFeature = false;
        shouldKamiBootsDoGrass = true;
        bedrockOreBlacklist = new String[0];
        bedrockOreDensity = 1;
        visShadowbeamAir = 15;
        visShadowbeamOrder = 25;
        visShadowbeamEntropy = 25;
        baseDamageShadowbeam = 8;
    }

    /**
     * Reads {@code path} (when there is one), applies it and writes it back with every key. A file that cannot be read, is not
     * JSON or is not a JSON object is <b>not</b> rewritten: the defaults hold for this run, an error names the file, and the file
     * stays as the player left it, to be fixed. An empty file counts as a file without settings and gets the defaults.
     *
     * @return false when the file was there but could not be read (and was left alone)
     */
    public static synchronized boolean loadConfig(Path path) {
        resetToDefaults();
        JsonObject json = new JsonObject();
        boolean readable = true;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
                if (parsed != null && parsed.isJsonObject()) {
                    json = parsed.getAsJsonObject();
                } else if (parsed != null && !parsed.isJsonNull()) {
                    throw new JsonParseException("the file is not a JSON object");
                }
            } catch (IOException | RuntimeException e) {
                readable = false;
                LOG.error("Thaumic Tinkerer cannot read its config file {} ({}). The defaults are used until it is fixed; "
                        + "the file is left as it is and is not rewritten.", path.toAbsolutePath(), e.toString());
            }
        }
        JsonObject general = section(json, "general");
        enableFlight = bool(general, "modFlight.enabled", enableFlight);
        enableSurvivalShareTome = bool(general, "shareTome.survival.enabled", enableSurvivalShareTome);
        enableDebugCommands = bool(general, "debugCommands.enabled", enableDebugCommands);
        forbiddenDimensions = strings(general, "forbiddenDimensions", forbiddenDimensions);
        blockBlacklist = strings(general, "focusOfDislocationBlacklist", blockBlacklist);
        enableFire = bool(general, "imbuedFire.enabled", enableFire);
        enableFireMechanics = bool(general, "imbuedFireSpread.enabled", enableFireMechanics);
        enableCake = bool(general, "imbuedFire.cake.enabled", enableCake);
        cropsAllowBonemeal = bool(general, "cropsAllowBonemeal.enabled", cropsAllowBonemeal);
        soulHeartHeight = clamp(integer(general, "soulHeart.height", soulHeartHeight), 0, 256);

        JsonObject kami = section(json, "kami");
        enableKami = bool(kami, "kami.forceenabled", enableKami);
        showPlacementMirrorBlocks = bool(kami, "placementMirror.blocks.show", showPlacementMirrorBlocks);
        shouldKamiLegsDoLightFeature = bool(kami, "kamiLeggingsLightPlacing.enabled", shouldKamiLegsDoLightFeature);
        shouldKamiBootsDoGrass = bool(kami, "kamiBootsDirtIntoGrass.enabled", shouldKamiBootsDoGrass);
        bedrockOreBlacklist = strings(kami, "bedrockDimension.oreBlacklist", bedrockOreBlacklist);
        bedrockOreDensity = clamp(integer(kami, "bedrockDimension.oreDensity", bedrockOreDensity), 0, 1023);

        JsonObject shadowbeam = section(json, "balance.shadowbeam");
        visShadowbeamAir = integer(shadowbeam, "aerVisCost", visShadowbeamAir);
        visShadowbeamOrder = integer(shadowbeam, "ordoVisCost", visShadowbeamOrder);
        visShadowbeamEntropy = integer(shadowbeam, "perditioVisCost", visShadowbeamEntropy);
        baseDamageShadowbeam = integer(shadowbeam, "baseDamage", baseDamageShadowbeam);

        if (readable) write(path);
        return readable;
    }

    private static void write(Path path) {
        Map<String, Object> general = new LinkedHashMap<>();
        general.put("modFlight.enabled", enableFlight);
        general.put("shareTome.survival.enabled", enableSurvivalShareTome);
        general.put("debugCommands.enabled", enableDebugCommands);
        general.put("forbiddenDimensions", forbiddenDimensions);
        general.put("focusOfDislocationBlacklist", blockBlacklist);
        general.put("imbuedFire.enabled", enableFire);
        general.put("imbuedFireSpread.enabled", enableFireMechanics);
        general.put("imbuedFire.cake.enabled", enableCake);
        general.put("cropsAllowBonemeal.enabled", cropsAllowBonemeal);
        general.put("soulHeart.height", soulHeartHeight);
        Map<String, Object> kami = new LinkedHashMap<>();
        kami.put("kami.forceenabled", enableKami);
        kami.put("placementMirror.blocks.show", showPlacementMirrorBlocks);
        kami.put("kamiLeggingsLightPlacing.enabled", shouldKamiLegsDoLightFeature);
        kami.put("kamiBootsDirtIntoGrass.enabled", shouldKamiBootsDoGrass);
        kami.put("bedrockDimension.oreBlacklist", bedrockOreBlacklist);
        kami.put("bedrockDimension.oreDensity", bedrockOreDensity);
        Map<String, Object> shadowbeam = new LinkedHashMap<>();
        shadowbeam.put("aerVisCost", visShadowbeamAir);
        shadowbeam.put("ordoVisCost", visShadowbeamOrder);
        shadowbeam.put("perditioVisCost", visShadowbeamEntropy);
        shadowbeam.put("baseDamage", baseDamageShadowbeam);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("general", general);
        root.put("kami", kami);
        root.put("balance.shadowbeam", shadowbeam);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOG.warn("Cannot write {}: {}", path, e.toString());
        }
    }

    // ---- the server's values on a client (ConfigSync) ------------------------------------------------------------

    /**
     * The options that a client evaluates as well as the server, as one side has them: the server sends its own on join
     * ({@link ConfigSync}), and a client connected to it reads them instead of its own file's.
     *
     * @param enableFlight {@code modFlight.enabled}: the Focus of Flight (the client moves its own player) and the Robe of the
     *        Stratosphere's flight
     * @param dislocationBlacklist {@code focusOfDislocationBlacklist}
     * @param forbiddenDimensions {@code forbiddenDimensions} (the Bottomless Pouch)
     * @param cropsAllowBonemeal {@code cropsAllowBonemeal.enabled} (bone meal on the Infused Grain)
     */
    public record Shared(boolean enableFlight, List<String> dislocationBlacklist, List<String> forbiddenDimensions,
            boolean cropsAllowBonemeal) {
        public Shared {
            dislocationBlacklist = List.copyOf(dislocationBlacklist);
            forbiddenDimensions = List.copyOf(forbiddenDimensions);
        }

        /** This side's own values (its file). */
        public static Shared local() {
            return new Shared(ConfigHandler.enableFlight, List.of(blockBlacklist), List.of(ConfigHandler.forbiddenDimensions),
                    ConfigHandler.cropsAllowBonemeal);
        }
    }

    /** What the server this client is connected to sent; null while none did (not connected, or a server without the add-on). */
    @Nullable
    private static volatile Shared fromServer;

    /** Called by the client when the server's values arrive ({@code null} when it leaves the server). */
    public static void setFromServer(@Nullable Shared values) {
        fromServer = values;
    }

    /** The server's values on this client, or null when none arrived. */
    @Nullable
    public static Shared fromServer() {
        return fromServer;
    }

    /** The values that hold on the given side: on the client the server's when they arrived, otherwise this side's file. */
    public static Shared shared(boolean clientSide) {
        Shared server = fromServer;
        return clientSide && server != null ? server : Shared.local();
    }

    /** True when {@code level} is the client's ({@code Level.isClientSide}); false for a server level, null, or a plain getter. */
    public static boolean isClientSide(@Nullable BlockGetter level) {
        return level instanceof Level real && real.isClientSide();
    }

    /** {@code modFlight.enabled} as it holds for {@code level}: the server's value on a client connected to one. */
    public static boolean flightEnabled(@Nullable BlockGetter level) {
        Shared server = fromServer;
        return isClientSide(level) && server != null ? server.enableFlight() : enableFlight;
    }

    /** {@code focusOfDislocationBlacklist} as it holds for {@code level}: the server's list on a client connected to one. */
    public static String[] dislocationBlacklist(@Nullable BlockGetter level) {
        Shared server = fromServer;
        return isClientSide(level) && server != null ? server.dislocationBlacklist().toArray(new String[0]) : blockBlacklist;
    }

    /** {@code forbiddenDimensions} as it holds for {@code level}: the server's list on a client connected to one. */
    public static String[] forbiddenDimensions(@Nullable BlockGetter level) {
        Shared server = fromServer;
        return isClientSide(level) && server != null ? server.forbiddenDimensions().toArray(new String[0]) : forbiddenDimensions;
    }

    /** {@code cropsAllowBonemeal.enabled} as it holds for {@code level}: the server's value on a client connected to one. */
    public static boolean cropsAllowBonemeal(@Nullable BlockGetter level) {
        Shared server = fromServer;
        return isClientSide(level) && server != null ? server.cropsAllowBonemeal() : cropsAllowBonemeal;
    }

    /**
     * The options that decide what is registered on each side at start-up, by {@code section/key} of the file, in a fixed
     * order: {@code imbuedFire.enabled} (the Imbued Fires' research and recipes, the parents of the Infused Potions' research),
     * {@code shareTome.survival.enabled} (the Tome of Research Sharing's research page and recipe) and {@code kami.forceenabled}
     * (every Kami item, block, research and recipe). A server and a client must agree on them: they cannot be changed while the
     * game runs, so a client that differs is disconnected ({@link ConfigSync}).
     */
    public static Map<String, Boolean> registrationOptions() {
        Map<String, Boolean> options = new LinkedHashMap<>();
        options.put("general/imbuedFire.enabled", enableFire);
        options.put("general/shareTome.survival.enabled", enableSurvivalShareTome);
        options.put("kami/kami.forceenabled", enableKami);
        return Collections.unmodifiableMap(options);
    }

    /**
     * The keys of {@link #registrationOptions()} whose value in {@code server} differs from this side's, in this side's order.
     * A key only one side knows (another version of the add-on) is not compared.
     */
    public static List<String> registrationMismatches(Map<String, Boolean> server) {
        List<String> differ = new ArrayList<>();
        for (Map.Entry<String, Boolean> own : registrationOptions().entrySet()) {
            Boolean theirs = server.get(own.getKey());
            if (theirs != null && !theirs.equals(own.getValue())) differ.add(own.getKey());
        }
        return differ;
    }

    private static JsonObject section(JsonObject root, String name) {
        JsonElement element = root.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        try {
            return o.has(key) ? o.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int integer(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String[] strings(JsonObject o, String key, String[] fallback) {
        try {
            if (!o.has(key)) return fallback;
            var array = o.getAsJsonArray(key);
            String[] out = new String[array.size()];
            for (int i = 0; i < out.length; i++) out[i] = array.get(i).getAsString();
            return out;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
