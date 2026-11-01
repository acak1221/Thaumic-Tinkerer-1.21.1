package thaumic.tinkerer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.core.handler.ConfigSync;
import thaumic.tinkerer.common.network.packet.PacketConfigSync;
import thaumic.tinkerer.common.network.packet.PacketConfigSyncReply;

/** Test-only code of the port (no 1.7.10 counterpart), CC BY-NC-SA 3.0 like the add-on. */
class ConfigHandlerTest {
    @TempDir Path dir;

    @AfterEach
    void restoreDefaults() {
        ConfigHandler.resetToDefaults();
        ConfigHandler.setFromServer(null);
    }

    @Test
    void aMissingFileGetsTheDefaultsAndIsWritten() throws IOException {
        Path file = dir.resolve("thaumictinkerer.json");
        ConfigHandler.loadConfig(file);
        assertTrue(Files.isRegularFile(file), "the defaults are written");
        assertTrue(ConfigHandler.enableKami);
        assertEquals(49, ConfigHandler.soulHeartHeight);
        assertEquals(15, ConfigHandler.visShadowbeamAir);
        String text = Files.readString(file);
        assertTrue(text.contains("\"kami.forceenabled\": true"), text);
    }

    @Test
    void valuesFromTheFileWinAndAreClamped() throws IOException {
        Path file = dir.resolve("thaumictinkerer.json");
        Files.writeString(file, """
                {"general": {"soulHeart.height": 9999, "modFlight.enabled": false, "forbiddenDimensions": ["minecraft:the_end"]},
                 "kami": {"kami.forceenabled": false},
                 "balance.shadowbeam": {"baseDamage": 3}}
                """, StandardCharsets.UTF_8);
        ConfigHandler.loadConfig(file);
        assertEquals(256, ConfigHandler.soulHeartHeight, "clamped to the 1.7.10 range");
        assertFalse(ConfigHandler.enableFlight);
        assertFalse(ConfigHandler.enableKami);
        assertEquals(3, ConfigHandler.baseDamageShadowbeam);
        assertArrayEquals(new String[] {"minecraft:the_end"}, ConfigHandler.forbiddenDimensions);
        String rewritten = Files.readString(file);
        assertTrue(rewritten.contains("\"imbuedFire.cake.enabled\": true"), "unknown keys appear with their defaults");
    }

    @Test
    void aBrokenFileFallsBackToTheDefaultsAndIsLeftAsItIs() throws IOException {
        Path file = dir.resolve("thaumictinkerer.json");
        String broken = "{\"general\": {\"modFlight.enabled\": false,, }";
        Files.writeString(file, broken, StandardCharsets.UTF_8);
        assertFalse(ConfigHandler.loadConfig(file), "the load reports the unreadable file");
        assertTrue(ConfigHandler.enableKami);
        assertTrue(ConfigHandler.enableFlight, "the defaults hold for the run");
        assertEquals(49, ConfigHandler.soulHeartHeight);
        assertEquals(broken, Files.readString(file), "the player's file is not overwritten with the defaults");
    }

    @Test
    void aFileThatIsNotAJsonObjectIsLeftAsItIs() throws IOException {
        Path file = dir.resolve("thaumictinkerer.json");
        Files.writeString(file, "[1, 2]", StandardCharsets.UTF_8);
        assertFalse(ConfigHandler.loadConfig(file));
        assertEquals("[1, 2]", Files.readString(file));
    }

    @Test
    void anEmptyFileGetsTheDefaults() throws IOException {
        Path file = dir.resolve("thaumictinkerer.json");
        Files.writeString(file, "  \n", StandardCharsets.UTF_8);
        assertTrue(ConfigHandler.loadConfig(file));
        assertTrue(Files.readString(file).contains("\"modFlight.enabled\": true"), "an empty file is written with the defaults");
    }

    @Test
    void theThreeUnusedOptionsAreNoLongerWrittenButAnOldFileStillLoads() throws IOException {
        Path file = dir.resolve("thaumictinkerer.json");
        Files.writeString(file, """
                {"general": {"tooltipIndicators.enabled": false, "oreDictMetal.enabled": false, "repairTconTools.enabled": true,
                             "modFlight.enabled": false}}
                """, StandardCharsets.UTF_8);
        assertTrue(ConfigHandler.loadConfig(file));
        assertFalse(ConfigHandler.enableFlight, "the rest of the old file is read");
        String rewritten = Files.readString(file);
        for (String gone : List.of("tooltipIndicators.enabled", "oreDictMetal.enabled", "repairTconTools.enabled")) {
            assertFalse(rewritten.contains(gone), gone + " is not written: " + rewritten);
        }
        assertTrue(rewritten.contains("\"modFlight.enabled\": false"), rewritten);
    }

    @Test
    void aClientReadsTheServersSharedValuesOnlyForItsOwnLevels() {
        ConfigHandler.enableFlight = true;
        ConfigHandler.blockBlacklist = new String[] {"minecraft:stone"};
        assertTrue(ConfigHandler.flightEnabled(null), "no server values: this side's file");
        assertEquals(ConfigHandler.Shared.local(), ConfigHandler.shared(true), "and on the client side as well");

        ConfigHandler.setFromServer(new ConfigHandler.Shared(false, List.of("#minecraft:logs"), List.of("minecraft:the_end"), true));
        assertTrue(ConfigHandler.flightEnabled(null), "a level that is not the client's reads this side's file");
        assertFalse(ConfigHandler.shared(true).enableFlight(), "the client side reads the server's values");
        assertTrue(ConfigHandler.shared(false).enableFlight(), "the server side its own");
        assertEquals(List.of("#minecraft:logs"), ConfigHandler.shared(true).dislocationBlacklist());
        assertArrayEquals(new String[] {"minecraft:stone"}, ConfigHandler.dislocationBlacklist(null));
        assertTrue(ConfigHandler.shared(true).cropsAllowBonemeal());
        assertFalse(ConfigHandler.cropsAllowBonemeal(null));

        ConfigHandler.setFromServer(null);
        assertTrue(ConfigHandler.shared(true).enableFlight(), "after leaving the server the file counts again");
    }

    @Test
    void registrationOptionsAreComparedKeyByKey() {
        Map<String, Boolean> own = ConfigHandler.registrationOptions();
        assertEquals(List.of("general/imbuedFire.enabled", "general/shareTome.survival.enabled", "kami/kami.forceenabled"),
                List.copyOf(own.keySet()));
        assertTrue(ConfigHandler.registrationMismatches(own).isEmpty(), "the same values: no mismatch");

        Map<String, Boolean> server = new LinkedHashMap<>(own);
        server.put("kami/kami.forceenabled", false);
        server.put("general/imbuedFire.enabled", false);
        server.put("general/aNewerOption.enabled", true);
        assertEquals(List.of("general/imbuedFire.enabled", "kami/kami.forceenabled"), ConfigHandler.registrationMismatches(server),
                "the differing keys in this side's order; a key only the server knows is not compared");

        Component message = ConfigSync.mismatchMessage(ConfigHandler.registrationMismatches(server), server, own);
        assertTrue(message.getContents() instanceof TranslatableContents t && t.getKey().equals(ConfigSync.MISMATCH), message.toString());
        String text = message.toString();
        assertTrue(text.contains(ConfigSync.MISMATCH_OPTION) && text.contains("kami/kami.forceenabled"), text);
    }

    // ---- the handshake (ConfigSync), a configuration task of the connection ------------------------------------------

    /** The arguments of the one {@code MISMATCH_OPTION} entry of a refusal: option, the server's value, the client's. */
    private static List<Object> optionArguments(Component refusal) {
        assertTrue(refusal.getContents() instanceof TranslatableContents t && t.getKey().equals(ConfigSync.MISMATCH), refusal.toString());
        Component list = (Component) ((TranslatableContents) refusal.getContents()).getArgs()[0];
        assertEquals(1, list.getSiblings().size(), "one option: " + list);
        Component option = list.getSiblings().get(0);
        assertTrue(option.getContents() instanceof TranslatableContents t && t.getKey().equals(ConfigSync.MISMATCH_OPTION), option.toString());
        return List.of(((TranslatableContents) option.getContents()).getArgs());
    }

    @Test
    void aClientKeepsTheServersValuesAndAnswersWithItsOwnRegistrationOptions() {
        ConfigHandler.enableFlight = true;
        ConfigHandler.blockBlacklist = new String[0];
        Map<String, Boolean> own = ConfigHandler.registrationOptions();
        PacketConfigSync server = new PacketConfigSync(false, List.of("minecraft:gold_block"), List.of(), true, own);
        PacketConfigSyncReply reply = ConfigSync.receive(server);
        assertEquals(server.shared(), ConfigHandler.fromServer(), "the server's values are this client's copy");
        assertFalse(ConfigHandler.shared(true).enableFlight(), "and hold for the client's levels");
        assertEquals(own, reply.registration(), "the answer: this side's registration options");
        assertEquals(List.copyOf(own.keySet()), List.copyOf(reply.registration().keySet()), "in their order");
    }

    @Test
    void aClientThatTheServerWillRefuseKeepsNothingOfItsValues() {
        ConfigHandler.setFromServer(new ConfigHandler.Shared(false, List.of(), List.of(), false));
        Map<String, Boolean> server = new LinkedHashMap<>(ConfigHandler.registrationOptions());
        server.put("general/imbuedFire.enabled", false);
        PacketConfigSyncReply reply = ConfigSync.receive(new PacketConfigSync(false, List.of("minecraft:gold_block"), List.of(), true, server));
        assertEquals(null, ConfigHandler.fromServer(), "no copy of a server that refuses this client (nor of an earlier one)");
        assertEquals(ConfigHandler.registrationOptions(), reply.registration(), "it still answers, so the server can name the options");
    }

    @Test
    void theServerLetsInAClientWithEqualOptionsAndRefusesOneThatDiffers() {
        GameProfile profile = new GameProfile(UUID.fromString("0f0e0d0c-0b0a-4908-8706-050403020100"), "TTMismatch");
        List<String> seen = new java.util.ArrayList<>();
        ConfigSync.Answered listener = (who, options, mismatches) -> seen.add(who.getName() + " " + options + " " + mismatches);
        ConfigSync.ANSWERED.register(listener);
        try {
            assertEquals(null, ConfigSync.answer(profile, PacketConfigSyncReply.ofThisSide()), "equal options: the client goes on");
            assertEquals(1, seen.size(), "the answer is posted: " + seen);
            assertTrue(seen.get(0).endsWith(" []"), seen.get(0));

            Map<String, Boolean> client = new LinkedHashMap<>(ConfigHandler.registrationOptions());
            client.put("general/imbuedFire.enabled", false);
            client.put("general/aNewerOption.enabled", true);
            Component refusal = ConfigSync.answer(profile, new PacketConfigSyncReply(client));
            assertTrue(refusal != null, "a client whose imbuedFire.enabled differs is refused");
            assertEquals(List.of("general/imbuedFire.enabled", "true", "false"), optionArguments(refusal),
                    "the option, the server's value, the client's; a key only the client knows is not compared");
            assertEquals(2, seen.size());
            assertTrue(seen.get(1).startsWith("TTMismatch ") && seen.get(1).endsWith(" [general/imbuedFire.enabled]"), seen.get(1));
        } finally {
            ConfigSync.ANSWERED.unregister(listener);
        }
    }

    @Test
    void theReplyRoundTripsAndABoundlessOneIsNotRead() {
        PacketConfigSyncReply sent = PacketConfigSyncReply.ofThisSide();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PacketConfigSyncReply.CODEC.encode(buf, sent);
        assertEquals(sent, PacketConfigSyncReply.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes(), "the payload is read completely");
        assertEquals("thaumictinkerer:config_sync_reply", PacketConfigSyncReply.TYPE.id().toString());
        assertEquals("thaumictinkerer:config_sync", PacketConfigSync.TYPE.id().toString());
        assertEquals("thaumictinkerer:config_sync", ConfigSync.TASK);

        Map<String, Boolean> many = new LinkedHashMap<>();
        for (int i = 0; i <= PacketConfigSyncReply.MAX_OPTIONS; i++) many.put("general/option" + i, true);
        FriendlyByteBuf large = new FriendlyByteBuf(Unpooled.buffer());
        large.writeVarInt(many.size());
        many.forEach((key, value) -> {
            large.writeUtf(key);
            large.writeBoolean(value);
        });
        assertThrows(RuntimeException.class, () -> PacketConfigSyncReply.CODEC.decode(large),
                "more than " + PacketConfigSyncReply.MAX_OPTIONS + " options from a client are refused by the decoder");
    }

    /** The handshake is loader glue: each loader's entry point installs it (a merge that drops the line breaks the sync silently). */
    @Test
    void bothLoadersInstallTheHandshake() throws IOException {
        Path fabric = Path.of("fabric", "src", "main", "java", "thaumic", "tinkerer", "fabric");
        Path neoforge = Path.of("neoforge", "src", "main", "java", "thaumic", "tinkerer", "neoforge");
        assertTrue(code(fabric.resolve("ThaumicTinkererFabric.java")).contains("FabricConfigurationTask::install"), "Fabric, both sides");
        assertTrue(code(fabric.resolve("ThaumicTinkererFabricClient.java")).contains("FabricConfigurationTaskClient::install"), "Fabric, the client");
        assertTrue(code(neoforge.resolve("ThaumicTinkererNeoForge.java")).contains("NeoForgeConfigurationTask.register(bus)"), "NeoForge");
        for (Path glue : List.of(fabric.resolve("hooks/FabricConfigurationTask.java"), neoforge.resolve("hooks/NeoForgeConfigurationTask.java"))) {
            String text = code(glue);
            assertTrue(text.contains("ConfigSync.answer(") && text.contains("ConfigSync.offer()") && text.contains("ConfigSync.TASK"), glue.toString());
        }
        assertFalse(code(Path.of("common", "src", "main", "java", "thaumic", "tinkerer", "common", "core", "handler", "ConfigSync.java"))
                .contains("PLAYER_JOIN"), "nothing is sent in the play phase any more");
    }

    private static String code(Path file) throws IOException {
        return Files.readString(file).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    @Test
    void theSyncPayloadRoundTrips() {
        ConfigHandler.enableFlight = false;
        ConfigHandler.blockBlacklist = new String[] {"minecraft:bedrock", "#minecraft:logs"};
        ConfigHandler.forbiddenDimensions = new String[] {"minecraft:the_nether"};
        ConfigHandler.enableKami = false;
        PacketConfigSync sent = PacketConfigSync.ofThisSide();
        // a configuration payload: no registry access
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        PacketConfigSync.CODEC.encode(buf, sent);
        PacketConfigSync read = PacketConfigSync.CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "the payload is read completely");
        assertEquals(sent, read);
        assertEquals(ConfigHandler.Shared.local(), read.shared());
        assertEquals(List.copyOf(ConfigHandler.registrationOptions().keySet()), List.copyOf(read.registration().keySet()),
                "the registration options keep their order");
        assertEquals(Boolean.FALSE, read.registration().get("kami/kami.forceenabled"));
    }
}
