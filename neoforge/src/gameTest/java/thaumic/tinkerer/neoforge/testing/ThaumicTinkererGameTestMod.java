/**
 * New in the 1.21.1 port of Thaumic Tinkerer (no 1.7.10 counterpart): the NeoForge GameTest harness, test-only. Distributed under the
 * same licence as the add-on (CC BY-NC-SA 3.0, see NOTICE.md).
 */
package thaumic.tinkerer.neoforge.testing;

import java.io.File;
import javax.xml.parsers.ParserConfigurationException;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.JUnitLikeTestReporter;
import net.minecraft.gametest.framework.TestReporter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GameTest harness of the add-on on NeoForge: the counterpart of the Fabric harness mod {@code thaumictinkerer_gametest}
 * (fabric/src/gameTest/resources/fabric.mod.json). It is a mod of its own, loaded only by {@code :neoforge:runGameTest} (from the output of
 * the {@code gameTest} source set), never part of the add-on's jar.
 *
 * <ul>
 *   <li>The wrapper classes ({@link TinkererGameTestCatalogue}, generated from the Fabric wrappers by tools/neoforge_gametests.py) are
 *   registered on {@link RegisterGameTestsEvent}. The template of a class's wrappers is {@code thaumictinkerer:<class>.empty} (an 8x8x8
 *   structure of air in this mod's resources, the counterpart of Fabric's {@code fabric-gametest-api-v1:empty}; the class name prefixes
 *   it, so that the tests have the names of the Fabric run, {@code <class>.<method>}), and the run enables the namespace
 *   {@code thaumictinkerer}.</li>
 *   <li>The NeoForge-only scenarios ({@link TinkererGameTestCatalogue#NEOFORGE_ONLY}, hand-written classes {@code *NeoForgeTests}): the
 *   capabilities of the Transvector Interface ({@link TileBlocksNeoForgeTests}).</li>
 *   <li>The NeoForge-only fixtures: the fluid tank of the barrels that the fluid test of the Transvector Interface fills and the energy
 *   cell of its energy tests (block, block entity type and energy capability; {@link TileBlocksNeoForgeChecks}), the negotiated channels of the mock players (mixin {@code TinkererMockConnectionMixin}) and the
 *   test filter (mixin {@code TinkererGameTestSelectionMixin}, the Fabric harness's {@code -PtinkererGameTestFilter}).</li>
 *   <li>The JUnit XML report, the file of {@code -Dthaumictinkerer.gameTestReportFile} (Fabric API writes the Fabric one from
 *   {@code -Dfabric-api.gametest.report-file}); every failure is logged with its stack trace too.</li>
 * </ul>
 */
@Mod(ThaumicTinkererGameTestMod.MOD_ID)
public final class ThaumicTinkererGameTestMod {
    public static final String MOD_ID = "thaumictinkerer_gametest";
    /** The namespace of the template of every wrapper; {@code -Dneoforge.enabledGameTestNamespaces} must name it. */
    public static final String TEMPLATE_NAMESPACE = "thaumictinkerer";
    public static final String REPORT_FILE_PROPERTY = "thaumictinkerer.gameTestReportFile";
    private static final Logger LOG = LoggerFactory.getLogger("ThaumicTinkererGameTest");

    public ThaumicTinkererGameTestMod(IEventBus bus) {
        bus.addListener(RegisterGameTestsEvent.class, event -> {
            for (Class<?> wrappers : TinkererGameTestCatalogue.CLASSES) event.register(wrappers);
            for (Class<?> scenarios : TinkererGameTestCatalogue.NEOFORGE_ONLY) event.register(scenarios);
        });
        bus.addListener(RegisterEvent.class, TileBlocksNeoForgeChecks::registerBlocks);
        bus.addListener(RegisterCapabilitiesEvent.class, TileBlocksNeoForgeChecks::registerCapabilities);
        String report = System.getProperty(REPORT_FILE_PROPERTY, "").trim();
        if (GameTestHooks.isGametestServer() && !report.isEmpty()) {
            File destination = new File(report);
            File parent = destination.getAbsoluteFile().getParentFile();
            if (parent != null) parent.mkdirs();
            try {
                GlobalTestReporter.replaceWith(new LoggingJUnitReporter(destination));
            } catch (ParserConfigurationException e) {
                throw new IllegalStateException("Cannot create the GameTest report " + destination, e);
            }
        }
    }

    /**
     * Vanilla's JUnit XML reporter, plus the stack trace of every failure in the server log: the report keeps only the message of a
     * failure, so an exception without one (a {@code NullPointerException}) would read like an empty assertion.
     */
    private static final class LoggingJUnitReporter implements TestReporter {
        private final JUnitLikeTestReporter delegate;

        LoggingJUnitReporter(File destination) throws ParserConfigurationException {
            this.delegate = new JUnitLikeTestReporter(destination);
        }

        @Override
        public void onTestFailed(GameTestInfo info) {
            LOG.error("GameTest {} failed", info.getTestName(), info.getError());
            delegate.onTestFailed(info);
        }

        @Override
        public void onTestSuccess(GameTestInfo info) {
            delegate.onTestSuccess(info);
        }

        @Override
        public void finish() {
            delegate.finish();
        }
    }
}
