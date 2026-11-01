package thaumic.tinkerer.tileblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thaumic.tinkerer.common.item.ItemConnector;
import thaumic.tinkerer.common.network.packet.PacketPlacerButton;
import thaumic.tinkerer.common.network.packet.PacketTabletButton;

/**
 * Test-only code of the port (no 1.7.10 counterpart), CC BY-NC-SA 3.0 like the add-on.
 *
 * <p>The Transvector Binder's memory of a device (its typed component, a dimension and a position; 1.7.10 used
 * {@code posy == -1} in the stack's NBT for "nothing remembered", a real height now) and the wire format of the two menu
 * packets. The component type is not registered in a unit test; a stack holds it all the same, and its codecs are
 * vanilla's.</p>
 */
class ConnectorAndPacketTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aFreshBinderRemembersNothing() {
        ItemStack binder = new ItemStack(Items.STICK);
        assertFalse(ItemConnector.hasTarget(binder));
        assertNull(ItemConnector.getTarget(binder));
    }

    @Test
    void theBinderRemembersAnyHeightIncludingMinusOneAndTheDimension() {
        for (BlockPos pos : new BlockPos[] { new BlockPos(3, 70, -8), new BlockPos(0, -1, 0), new BlockPos(-30000000, -64, 29999999) }) {
            for (var dimension : java.util.List.of(Level.OVERWORLD, Level.NETHER)) {
                ItemStack binder = new ItemStack(Items.STICK);
                ItemConnector.setTarget(binder, GlobalPos.of(dimension, pos));
                assertTrue(ItemConnector.hasTarget(binder), pos.toString());
                assertEquals(GlobalPos.of(dimension, pos), ItemConnector.getTarget(binder));
            }
        }
    }

    @Test
    void theRememberedDeviceSurvivesItsCodecs() {
        GlobalPos device = GlobalPos.of(Level.NETHER, new BlockPos(-5, -1, 77));
        var json = ItemConnector.REMEMBERED_DEVICE.codecOrThrow().encodeStart(JsonOps.INSTANCE, device).getOrThrow();
        assertEquals(device, ItemConnector.REMEMBERED_DEVICE.codecOrThrow().parse(JsonOps.INSTANCE, json).getOrThrow());
        RegistryFriendlyByteBuf buf = buffer();
        ItemConnector.REMEMBERED_DEVICE.streamCodec().encode(buf, device);
        assertEquals(device, ItemConnector.REMEMBERED_DEVICE.streamCodec().decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void forgettingTheDeviceLeavesTheOtherDataAlone() {
        ItemStack binder = new ItemStack(Items.STICK);
        CompoundTag other = new CompoundTag();
        other.putString("mine", "kept");
        binder.set(DataComponents.CUSTOM_DATA, CustomData.of(other));

        ItemConnector.setTarget(binder, GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 2, 3)));
        assertEquals(other, binder.get(DataComponents.CUSTOM_DATA).copyTag(), "the device is not in the custom data");
        ItemConnector.clearTarget(binder);

        assertFalse(ItemConnector.hasTarget(binder));
        assertEquals("kept", binder.get(DataComponents.CUSTOM_DATA).copyTag().getString("mine"));
    }

    @Test
    void forgettingTheDeviceRemovesTheComponent() {
        ItemStack binder = new ItemStack(Items.STICK);
        ItemConnector.setTarget(binder, GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 2, 3)));
        ItemConnector.clearTarget(binder);
        assertNull(binder.get(ItemConnector.REMEMBERED_DEVICE));
        assertNull(binder.get(DataComponents.CUSTOM_DATA));
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void theTabletPacketSurvivesTheWire() {
        for (boolean left : new boolean[] { true, false }) {
            for (boolean redstone : new boolean[] { true, false }) {
                PacketTabletButton sent = new PacketTabletButton(new BlockPos(-5, 12, 900), left, redstone);
                RegistryFriendlyByteBuf buf = buffer();
                PacketTabletButton.STREAM_CODEC.encode(buf, sent);
                assertEquals(sent, PacketTabletButton.STREAM_CODEC.decode(buf));
                assertEquals(0, buf.readableBytes(), "the packet is read completely");
            }
        }
    }

    @Test
    void thePlacerPacketSurvivesTheWire() {
        PacketPlacerButton sent = new PacketPlacerButton(new BlockPos(7, -3, 11), 4);
        RegistryFriendlyByteBuf buf = buffer();
        PacketPlacerButton.STREAM_CODEC.encode(buf, sent);
        assertEquals(sent, PacketPlacerButton.STREAM_CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void theTwoPacketsHaveDistinctTypes() {
        assertEquals("thaumictinkerer:tablet_button", PacketTabletButton.TYPE.id().toString());
        assertEquals("thaumictinkerer:placer_button", PacketPlacerButton.TYPE.id().toString());
    }
}
