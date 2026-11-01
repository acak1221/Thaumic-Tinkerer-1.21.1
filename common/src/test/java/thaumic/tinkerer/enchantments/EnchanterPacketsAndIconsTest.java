package thaumic.tinkerer.enchantments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thaumic.tinkerer.client.gui.EnchantIcons;
import thaumic.tinkerer.common.enchantment.ModEnchantments;
import thaumic.tinkerer.common.network.packet.PacketEnchanterAddEnchant;
import thaumic.tinkerer.common.network.packet.PacketEnchanterStartWorking;

/**
 * Test-only code of the port (no 1.7.10 counterpart), CC BY-NC-SA 3.0 like the add-on.
 *
 * <p>The wire format of the two packets of the Enchanter's screen, the reading of a level off a stack, and the size of the
 * icon textures (the screen draws a 32 pixel and a 512 pixel texture onto the same 16 by 16 button, which needs them square).</p>
 */
class EnchanterPacketsAndIconsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void theAddEnchantPacketCarriesThePositionTheEnchantmentAndTheLevel() {
        for (int level : new int[] { -1, 0, 1, 5 }) {
            PacketEnchanterAddEnchant sent = new PacketEnchanterAddEnchant(new BlockPos(-12, 63, 40000),
                    ModEnchantments.finalStrike, level);
            RegistryFriendlyByteBuf buf = buffer();
            PacketEnchanterAddEnchant.STREAM_CODEC.encode(buf, sent);
            PacketEnchanterAddEnchant received = PacketEnchanterAddEnchant.STREAM_CODEC.decode(buf);
            assertEquals(sent, received, "level " + level);
            assertSame(ModEnchantments.finalStrike, received.enchant(), "the key of the enchantment registry, the same object");
            assertEquals(0, buf.readableBytes());
        }
    }

    /**
     * The packet carries the registry key of the enchantment ({@code ResourceKey.streamCodec}); on the wire that is its id, as when
     * the packet carried a bare {@code ResourceLocation}: the position, the id and the level, nothing else. A key the server does
     * not know (another add-on's enchantment) decodes and is left to {@code apply}, which refuses it.
     */
    @Test
    void onTheWireTheEnchantmentIsItsIdAsBefore() {
        BlockPos pos = new BlockPos(3, -60, 17);
        RegistryFriendlyByteBuf buf = buffer();
        PacketEnchanterAddEnchant.STREAM_CODEC.encode(buf, new PacketEnchanterAddEnchant(pos, Enchantments.SHARPNESS, 4));
        assertEquals(pos, BlockPos.STREAM_CODEC.decode(buf));
        assertEquals(ResourceLocation.withDefaultNamespace("sharpness"), ResourceLocation.STREAM_CODEC.decode(buf));
        assertEquals(4, ByteBufCodecs.VAR_INT.decode(buf));
        assertEquals(0, buf.readableBytes());

        RegistryFriendlyByteBuf other = buffer();
        BlockPos.STREAM_CODEC.encode(other, pos);
        ResourceLocation.STREAM_CODEC.encode(other, ResourceLocation.fromNamespaceAndPath("another_mod", "lightning"));
        ByteBufCodecs.VAR_INT.encode(other, 0);
        PacketEnchanterAddEnchant unknown = PacketEnchanterAddEnchant.STREAM_CODEC.decode(other);
        assertEquals(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath("another_mod", "lightning")),
                unknown.enchant());
    }

    @Test
    void theStartPacketCarriesThePosition() {
        PacketEnchanterStartWorking sent = new PacketEnchanterStartWorking(new BlockPos(1, -60, -7));
        RegistryFriendlyByteBuf buf = buffer();
        PacketEnchanterStartWorking.STREAM_CODEC.encode(buf, sent);
        assertEquals(sent, PacketEnchanterStartWorking.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void thePacketsHaveDistinctIdsInTheNamespaceOfTheAddOn() {
        assertEquals("thaumictinkerer:enchanter_add_enchant", PacketEnchanterAddEnchant.TYPE.id().toString());
        assertEquals("thaumictinkerer:enchanter_start_working", PacketEnchanterStartWorking.TYPE.id().toString());
    }

    @Test
    void aLevelIsReadOffTheEnchantmentsComponentOfAStack() {
        // standalone holders are enough: the level is found by the registry key of the holder
        HolderOwner<Enchantment> owner = new HolderOwner<>() {};
        Holder<Enchantment> shatter = Holder.Reference.createStandAlone(owner, ModEnchantments.shatter);
        Holder<Enchantment> sharpness = Holder.Reference.createStandAlone(owner, Enchantments.SHARPNESS);

        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        assertEquals(0, ModEnchantments.level(pickaxe, ModEnchantments.shatter));

        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(shatter, 3);
        enchantments.set(sharpness, 2);
        pickaxe.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());

        assertEquals(3, ModEnchantments.level(pickaxe, ModEnchantments.shatter));
        assertEquals(2, ModEnchantments.level(pickaxe, Enchantments.SHARPNESS));
        assertEquals(0, ModEnchantments.level(pickaxe, ModEnchantments.tunnel));
        assertEquals(0, ModEnchantments.level(ItemStack.EMPTY, ModEnchantments.shatter));
    }

    private static byte[] header(String resource) throws IOException {
        try (InputStream in = EnchanterPacketsAndIconsTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return in.readNBytes(24);
        }
    }

    /** The width or the height in the IHDR chunk of a PNG (bytes 16 to 19 and 20 to 23). */
    private static int pngInt(byte[] header, int offset) {
        assertTrue(header.length >= 24 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G', "a PNG header");
        return ((header[offset] & 0xFF) << 24) | ((header[offset + 1] & 0xFF) << 16) | ((header[offset + 2] & 0xFF) << 8)
                | (header[offset + 3] & 0xFF);
    }

    /**
     * The screen draws the whole texture of an icon onto 16 by 16 pixels by telling {@code blit} that the texture is 16 by
     * 16 ({@link EnchantIcons#draw}); that is right for a texture of any size as long as it is square, which every icon is
     * (the eight of the add-on's own enchantments are 512 pixels, the others 32).
     */
    @Test
    void everyIconIsSquareSoTheWholeTextureFitsTheButton() throws IOException {
        for (String big : new String[] { "pounce", "tunnel", "shatter", "shockwave", "valiance", "final_strike", "focused_strikes",
                "dispersed_strikes" }) {
            byte[] header = header("/assets/thaumictinkerer/textures/enchant/" + big + ".png");
            assertEquals(512, pngInt(header, 16), big);
            assertEquals(512, pngInt(header, 20), big);
        }
        for (String small : new String[] { "protection", "sharpness", "quick_draw", "vamprisim", "ascent_boost", "repair" }) {
            byte[] header = header("/assets/thaumictinkerer/textures/enchant/" + small + ".png");
            assertEquals(32, pngInt(header, 16), small);
            assertEquals(32, pngInt(header, 20), small);
        }
        assertEquals(16, EnchantIcons.SIZE, "the size of an icon on the screen, and the size the texture is said to have");
    }
}
