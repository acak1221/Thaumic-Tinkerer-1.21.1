package thaumic.tinkerer.clientsmoke;

import java.util.ArrayList;
import java.util.List;

/**
 * The optional integrations, when the run has the mods: the add-on's EMI and JEI pages (necromancy, infused seed drops), its
 * Thaumcraft-type recipes inside Thaumcraft's own EMI/JEI categories, and its Jade tooltips. Test-only code of the port (no
 * 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on, not distributed.
 *
 * <p>A normal run has none of these mods and records that as information. {@code tools/client-smoke.sh -- -PwithEmi -PwithJade}
 * and {@code -- -PwithJei} add them; EMI and JEI are run separately. The probes are separate classes that name the mods' APIs, so
 * this phase loads none of them when the mod is absent.</p>
 */
final class IntegrationsPhase extends ScriptedPhase {
    static final String PHASE = "integrations";
    static final String GROUP = "Integrations";

    @Override
    public String name() {
        return PHASE;
    }

    @Override
    protected List<Script> scripts(SmokeRun run) {
        SmokePlatform loader = ClientSmoke.platform();
        List<String> loaded = new ArrayList<>();
        for (String id : List.of("emi", "jei", "jade")) if (loader.isModLoaded(id)) loaded.add(id);
        run.report().inventory("integrations_loaded", loaded);
        List<Script> scripts = new ArrayList<>();
        if (loaded.isEmpty()) {
            run.report().info(PHASE, "no_integration_mods", "EMI, JEI and Jade are not loaded in this run (tools/client-smoke.sh -- -PwithEmi -PwithJade, or -- -PwithJei)");
            return scripts;
        }
        if (loaded.contains("emi")) scripts.add(EmiProbe.script(run));
        if (loaded.contains("jei")) scripts.add(JeiProbe.script(run));
        if (loaded.contains("jade")) scripts.addAll(JadeProbe.scripts(run));
        return scripts;
    }
}
