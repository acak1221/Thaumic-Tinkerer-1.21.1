/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): NeoForge glue. Distributed under the same licence as the add-on
 * (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import thaumic.tinkerer.common.hooks.FloatingTicks;

/**
 * Makes the connection of a player implement {@link FloatingTicks.Access}: reads and writes the private
 * {@code aboveGroundTickCount} (the ticks a player has been floating, which kick him above 80 on a server without
 * {@code allow-flight}). The NeoForge copy of the Fabric mixin of the same name; the loader-neutral {@link FloatingTicks} does the rest.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerFloatingMixin implements FloatingTicks.Access {
    @Shadow
    private int aboveGroundTickCount;

    @Override
    public int thaumictinkerer$getAboveGroundTickCount() {
        return this.aboveGroundTickCount;
    }

    @Override
    public void thaumictinkerer$setAboveGroundTickCount(int ticks) {
        this.aboveGroundTickCount = ticks;
    }
}
