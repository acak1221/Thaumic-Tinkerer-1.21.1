/**
 * This file is new in the Minecraft 1.21.1 port of Thaumic Tinkerer for Thaumcraft 4 Reload (no 1.7.10 counterpart, see
 * NOTICE.md). Thaumic Tinkerer was created by Vazkii; the port is distributed under a Creative
 * Commons Attribution-NonCommercial-ShareAlike 3.0 License (http://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB)
 *
 * ThaumicTinkerer is a Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012
 * (http://www.minecraftforum.net/topic/1585216-)
 */
package thaumic.tinkerer.common.core.golems;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.golems.GolemApi;
import thaumcraft.api.golems.GolemMaterial;
import thaumcraft.api.golems.GolemView;

/**
 * A Thaumcraft golem as the Golem Connector reads and changes it, through {@code thaumcraft.api.golems} (request A11 of
 * docs/API_GAPS.md): the 1.7.10 connector read the fields of {@code EntityGolemBase} and called {@code setHomeArea} and
 * {@code setMarkers} on it.
 *
 * <p>Every read takes a fresh {@link GolemApi#view} of the golem, so a call after {@code setHome} or {@code setMarkers} sees the
 * change. The values: the material's 1.7.10 name ({@code "Straw"} to {@code "Thaumium"}), the core id (-1 for none), the
 * accessory characters, the health, the position, the home and its side as a {@code ForgeDirection} number (the 3D data value of
 * the {@link Direction}), the markers with their dimension as its id ({@code "minecraft:overworld"}; 1.7.10 had a numeric id).</p>
 *
 * <p>Where the API refuses what 1.7.10 did without a check, the call fails with a {@link GolemConnectorMethods.MethodException}
 * instead of changing nothing in silence: a side other than 0 to 5, a colour other than -1 to 15, a dimension that is no id, more
 * than {@value GolemApi#MAX_MARKERS} markers, and the golem's own refusals (a coloured marker on a golem without the Order upgrade,
 * a golem that retires or moves cargo, a home outside the world). Server thread only, as the API requires; the computer
 * peripherals run their calls there.</p>
 */
public final class ApiGolemAccess implements GolemAccess {
    private final Entity golem;

    public ApiGolemAccess(Entity golem) {
        this.golem = golem;
    }

    private Optional<GolemView> view() {
        return GolemApi.view(golem);
    }

    @Override
    public boolean exists() {
        return view().isPresent();
    }

    @Override
    @Nullable
    public String typeName() {
        GolemMaterial material = view().map(GolemView::material).orElse(null);
        return material == null ? null : material.displayName();
    }

    @Override
    public int core() {
        return view().map(GolemView::core).orElse(GolemView.NO_CORE);
    }

    @Override
    public String decoration() {
        return view().map(GolemView::decoration).orElse("");
    }

    @Override
    public float health() {
        return view().map(GolemView::health).orElse(0F);
    }

    @Override
    public double x() {
        return view().map(v -> v.position().x).orElse(0D);
    }

    @Override
    public double y() {
        return view().map(v -> v.position().y).orElse(0D);
    }

    @Override
    public double z() {
        return view().map(v -> v.position().z).orElse(0D);
    }

    @Override
    public BlockPos home() {
        return view().map(GolemView::home).orElse(BlockPos.ZERO);
    }

    @Override
    public int homeFacing() {
        return view().map(v -> v.homeFacing().get3DDataValue()).orElse(0);
    }

    @Override
    public List<Marker> markers() {
        List<Marker> out = new ArrayList<>();
        for (GolemView.Marker marker : view().map(GolemView::markers).orElse(List.of())) {
            out.add(new Marker(marker.pos().getX(), marker.pos().getY(), marker.pos().getZ(), marker.dimension().location().toString(),
                    (byte) marker.side().get3DDataValue(), (byte) marker.color()));
        }
        return out;
    }

    @Override
    public void setHome(BlockPos home, int facing) {
        Direction side = side(facing, "bad argument #4 (side 0 to 5 expected, got " + facing + ")");
        if (!GolemApi.setHome(golem, home, side)) throw new GolemConnectorMethods.MethodException("The golem refused the new home");
    }

    @Override
    public void setMarkers(List<Marker> markers) {
        if (markers.size() > GolemApi.MAX_MARKERS) {
            throw new GolemConnectorMethods.MethodException("A golem keeps at most " + GolemApi.MAX_MARKERS + " markers");
        }
        List<GolemView.Marker> converted = new ArrayList<>(markers.size());
        for (Marker marker : markers) converted.add(toApi(marker));
        if (!GolemApi.setMarkers(golem, converted)) {
            throw new GolemConnectorMethods.MethodException(
                    "The golem refused the markers: a coloured marker needs the Order upgrade, and a golem that retires or moves cargo takes none");
        }
    }

    /** A marker table's values as the API's marker; a value the API cannot hold is a "bad field" error. */
    public static GolemView.Marker toApi(Marker marker) {
        Direction side = side(marker.side(), "bad field 'side' (side 0 to 5 expected, got " + marker.side() + ")");
        if (marker.color() < GolemView.Marker.ANY_COLOR || marker.color() > GolemView.Marker.LAST_COLOR) {
            throw new GolemConnectorMethods.MethodException("bad field 'color' (-1 to 15 expected, got " + marker.color() + ")");
        }
        ResourceLocation dimension = ResourceLocation.tryParse(marker.dimension());
        if (dimension == null) {
            throw new GolemConnectorMethods.MethodException("bad field 'dim' (dimension id expected, got " + marker.dimension() + ")");
        }
        return new GolemView.Marker(new BlockPos(marker.x(), marker.y(), marker.z()), ResourceKey.create(Registries.DIMENSION, dimension),
                side, marker.color());
    }

    private static Direction side(int value, String error) {
        if (value < 0 || value > 5) throw new GolemConnectorMethods.MethodException(error);
        return Direction.from3DDataValue(value);
    }
}
