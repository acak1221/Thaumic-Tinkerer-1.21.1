/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart, see NOTICE.md): test-only
 * code, distributed under the same licence as the add-on (CC BY-NC-SA 3.0).
 */
package thaumic.tinkerer.fabric.testing;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import thaumic.tinkerer.testing.RecipeViewersRuntimeChecks;

/** Fabric wrappers of {@link RecipeViewersRuntimeChecks} (the optional EMI and JEI pages): one per check. Test-only, not distributed. */
public final class RecipeViewersGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void thePagesAreBuiltAfterRegistration(GameTestHelper test) {
        RecipeViewersRuntimeChecks.thePagesAreBuiltAfterRegistration(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theNecromancyPagesCoverEveryMobOnceWithItsSoulAspectsAndItsMobDisplay(GameTestHelper test) {
        RecipeViewersRuntimeChecks.theNecromancyPagesCoverEveryMobOnceWithItsSoulAspectsAndItsMobDisplay(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theTabletSummonsTheMobOfEveryPageFromEveryFormItShows(GameTestHelper test) {
        RecipeViewersRuntimeChecks.theTabletSummonsTheMobOfEveryPageFromEveryFormItShows(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSeedPagesCoverEveryAspectWithLootAndTheChancesAreTheWeights(GameTestHelper test) {
        RecipeViewersRuntimeChecks.theSeedPagesCoverEveryAspectWithLootAndTheChancesAreTheWeights(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void theSeedPagesSplitIntoPagesWithoutLosingADrop(GameTestHelper test) {
        RecipeViewersRuntimeChecks.theSeedPagesSplitIntoPagesWithoutLosingADrop(test);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void aRipeCropOfEveryPageYieldsOnlyWhatThePageLists(GameTestHelper test) {
        RecipeViewersRuntimeChecks.aRipeCropOfEveryPageYieldsOnlyWhatThePageLists(test);
    }
}
