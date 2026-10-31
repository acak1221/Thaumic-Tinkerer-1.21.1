/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the NeoForge GameTest harness, test-only. Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.testing.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the mock players of a GameTest the channels a real client with the mods would have negotiated.
 *
 * <p>NeoForge refuses to send a mod's payload over a connection that has not negotiated its channel ("Payload ... may not be sent to the
 * client!", an exception in the sender). {@code GameTestHelper.makeMockServerPlayerInLevel} joins with a bare connection, so the
 * payloads that Thaumcraft and the add-on send on login and afterwards (research, knowledge, menus) would fail every check that uses a
 * mock player. NeoForge's testing API sets up the channels of the loaded mods, as the configuration phase does for a real client; on
 * Fabric nothing refuses such a payload. A connection that already has a negotiated setup (a real client) is left alone.</p>
 */
@Mixin(PlayerList.class)
public abstract class TinkererMockConnectionMixin {
    @Inject(method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At("HEAD"))
    private void thaumictinkerer$negotiateMockConnection(Connection connection, ServerPlayer player, CommonListenerCookie cookie,
                                                       CallbackInfo ci) {
        if (ChannelAttributes.getPayloadSetup(connection) == null) NetworkRegistry.configureMockConnection(connection);
    }
}
