/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only
 * code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.fabric.testing;

import dan200.computercraft.api.peripheral.PeripheralLookup;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import thaumic.tinkerer.testing.ComputerCraftRuntimeChecks;

/**
 * Fabric wrappers of {@link ComputerCraftRuntimeChecks} (the optional CC: Tweaked integration): one per check. The two lookup
 * checks get Fabric's way to find a peripheral, CC: Tweaked's {@link PeripheralLookup}, which is what CC: Tweaked itself asks on
 * Fabric. Test-only, not distributed.
 */
public final class ComputerCraftGameTests implements FabricGameTest {
    private static final ComputerCraftRuntimeChecks.PeripheralFinder LOOKUP = (level, pos, side) -> PeripheralLookup.get().find(level, pos, side);

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void computerCraftIsLoadedAndTheGlueRegistered(GameTestHelper test) {
        ComputerCraftRuntimeChecks.computerCraftIsLoadedAndTheGlueRegistered(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void addonPeripheralsAreFoundAtTheirBlocks(GameTestHelper test) {
        ComputerCraftRuntimeChecks.addonPeripheralsAreFoundAtTheirBlocks(test, LOOKUP);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void genericPeripheralsAreFoundAtThaumcraftBlocks(GameTestHelper test) {
        ComputerCraftRuntimeChecks.genericPeripheralsAreFoundAtThaumcraftBlocks(test, LOOKUP);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thaumcraftMachinesAreFoundAtTheirBlocks(GameTestHelper test) {
        ComputerCraftRuntimeChecks.thaumcraftMachinesAreFoundAtTheirBlocks(test, LOOKUP);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60000)
    public void luaDrivesTheDynamismTablet(GameTestHelper test) {
        ComputerCraftRuntimeChecks.luaDrivesTheDynamismTablet(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60000)
    public void luaDrivesTheAttractors(GameTestHelper test) {
        ComputerCraftRuntimeChecks.luaDrivesTheAttractors(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60000)
    public void luaReadsTheAspectalyzer(GameTestHelper test) {
        ComputerCraftRuntimeChecks.luaReadsTheAspectalyzer(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60000)
    public void luaAsksTheGolemConnectorWithoutAGolem(GameTestHelper test) {
        ComputerCraftRuntimeChecks.luaAsksTheGolemConnectorWithoutAGolem(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60000)
    public void luaReadsAspectContainers(GameTestHelper test) {
        ComputerCraftRuntimeChecks.luaReadsAspectContainers(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60000)
    public void luaReadsEssentiaTransports(GameTestHelper test) {
        ComputerCraftRuntimeChecks.luaReadsEssentiaTransports(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60000)
    public void luaReadsThaumcraftMachines(GameTestHelper test) {
        ComputerCraftRuntimeChecks.luaReadsThaumcraftMachines(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60000)
    public void luaReadsAndSteersAGolem(GameTestHelper test) {
        ComputerCraftRuntimeChecks.luaReadsAndSteersAGolem(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fumeDissipatorIsATurtleUpgrade(GameTestHelper test) {
        ComputerCraftRuntimeChecks.fumeDissipatorIsATurtleUpgrade(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60000)
    public void luaTurtleDissipatesGas(GameTestHelper test) {
        ComputerCraftRuntimeChecks.luaTurtleDissipatesGas(test);
    }
}
