package thaumic.tinkerer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import thaumic.tinkerer.common.core.handler.ConfigHandler;
import thaumic.tinkerer.common.core.handler.ConfigSync;
import thaumic.tinkerer.common.network.packet.PacketConfigSync;

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

        Component message = ConfigSync.mismatchMessage(ConfigHandler.registrationMismatches(server), server);
        assertTrue(message.getContents() instanceof TranslatableContents t && t.getKey().equals(ConfigSync.MISMATCH), message.toString());
        String text = message.toString();
        assertTrue(text.contains(ConfigSync.MISMATCH_OPTION) && text.contains("kami/kami.forceenabled"), text);
    }

    @Test
    void theSyncPayloadRoundTrips() {
        ConfigHandler.enableFlight = false;
        ConfigHandler.blockBlacklist = new String[] {"minecraft:bedrock", "#minecraft:logs"};
        ConfigHandler.forbiddenDimensions = new String[] {"minecraft:the_nether"};
        ConfigHandler.enableKami = false;
        PacketConfigSync sent = PacketConfigSync.ofThisSide();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
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
