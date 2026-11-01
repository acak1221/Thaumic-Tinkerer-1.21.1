/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.common.core.golems;

import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import thaumcraft.api.golems.GolemApi;

/**
 * The single place that turns a golem entity into a {@link GolemAccess}: {@link ApiGolemAccess}, over the golem view of
 * {@code thaumcraft.api.golems} (request A11), for a Thaumcraft golem; the honest stub {@link UnavailableGolemAccess} (the
 * answers of a connector without a golem) for anything else, and for a golem that is gone.
 */
public final class GolemAccessProvider {
    private static final Function<Entity, GolemAccess> API = entity -> GolemApi.isGolem(entity) ? new ApiGolemAccess(entity)
            : UnavailableGolemAccess.INSTANCE;
    private static volatile Function<Entity, GolemAccess> provider = API;

    private GolemAccessProvider() {}

    public static GolemAccess forEntity(Entity golem) {
        return golem == null || golem.isRemoved() ? UnavailableGolemAccess.INSTANCE : provider.apply(golem);
    }

    /** Installs another access (for tests); null restores the one over the API. */
    public static void setProvider(Function<Entity, GolemAccess> newProvider) {
        provider = newProvider == null ? API : newProvider;
    }
}
