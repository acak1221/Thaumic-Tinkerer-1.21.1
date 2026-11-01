/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the NeoForge GameTest harness, test-only. Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.testing;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.gametest.framework.GameTestHelper;
import thaumic.tinkerer.testing.ComputerCraftRuntimeChecks;

/**
 * The NeoForge counterparts of the Fabric-native wrappers of {@code ComputerCraftGameTests}: the lookup checks with NeoForge's way to
 * find a peripheral, the block capability {@link PeripheralCapability}, which is what CC: Tweaked itself asks on NeoForge. Called by
 * the generated wrappers (tools/neoforge_gametests.py). Test-only, not distributed.
 */
public final class ComputerCraftNeoForgeChecks {
    private static final ComputerCraftRuntimeChecks.PeripheralFinder CAPABILITY =
            (level, pos, side) -> level.getCapability(PeripheralCapability.get(), pos, side);

    private ComputerCraftNeoForgeChecks() {}

    public static void addonPeripheralsAreFoundAtTheirBlocks(GameTestHelper test) {
        ComputerCraftRuntimeChecks.addonPeripheralsAreFoundAtTheirBlocks(test, CAPABILITY);
    }

    public static void genericPeripheralsAreFoundAtThaumcraftBlocks(GameTestHelper test) {
        ComputerCraftRuntimeChecks.genericPeripheralsAreFoundAtThaumcraftBlocks(test, CAPABILITY);
    }
}
