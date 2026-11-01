/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the Fabric GameTest harness, test-only. Distributed under the same
 * licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.fabric.testing;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import thaumic.tinkerer.fabric.TileBlocksFabric;
import thaumic.tinkerer.testing.TestEnergyCell;

/**
 * The {@code main} entrypoint of the harness mod {@code thaumictinkerer_gametest}: registers what the checks need of their own, the energy
 * cell of the Transvector Interface's energy checks ({@link TestEnergyCell}, block and block entity type), and, when Team Reborn Energy is
 * installed, its energy storage ({@link TeamRebornEnergyTesting}, the only class of the harness that names that mod's API, loaded only
 * then). The NeoForge harness registers the same in {@code ThaumicTinkererGameTestMod}.
 */
public final class TinkererGameTestSetup implements ModInitializer {
    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, TestEnergyCell.ID, TestEnergyCell.block());
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, TestEnergyCell.ID, TestEnergyCell.type());
        if (TileBlocksFabric.teamRebornEnergyLoaded()) TeamRebornEnergyTesting.registerCell();
    }
}
