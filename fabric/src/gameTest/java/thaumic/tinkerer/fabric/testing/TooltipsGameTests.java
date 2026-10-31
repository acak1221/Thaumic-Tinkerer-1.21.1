/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only
 * code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.fabric.testing;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import thaumic.tinkerer.testing.TooltipsRuntimeChecks;

/** Fabric wrappers of {@link TooltipsRuntimeChecks} (the optional integrations): one per check. Test-only, not distributed. */
public final class TooltipsGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void everyBlockHasExactlyOneTooltip(GameTestHelper test) {
        TooltipsRuntimeChecks.everyBlockHasExactlyOneTooltip(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void animationTabletShowsItsToolAndModes(GameTestHelper test) {
        TooltipsRuntimeChecks.animationTabletShowsItsToolAndModes(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void transvectorInterfaceShowsTheBoundBlock(GameTestHelper test) {
        TooltipsRuntimeChecks.transvectorInterfaceShowsTheBoundBlock(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void repairerShowsTheToolItRepairs(GameTestHelper test) {
        TooltipsRuntimeChecks.repairerShowsTheToolItRepairs(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void warpGateTellsWhetherItAcceptsTeleports(GameTestHelper test) {
        TooltipsRuntimeChecks.warpGateTellsWhetherItAcceptsTeleports(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void kineticAttractorShowsItsPolarity(GameTestHelper test) {
        TooltipsRuntimeChecks.kineticAttractorShowsItsPolarity(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void corporealAttractorShowsAgeAndMould(GameTestHelper test) {
        TooltipsRuntimeChecks.corporealAttractorShowsAgeAndMould(test);
    }
}
