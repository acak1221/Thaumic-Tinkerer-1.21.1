package thaumic.tinkerer.clientsmoke;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.DustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;

/**
 * Counts the particles of a Shadowbeam in this client: the black dust that {@code ShadowBeamClientHandler.show} spawns along the
 * path the server sent ({@code PacketShadowBeam}). Nothing else in the multiplayer check makes a dust particle of that colour
 * (red 0.04, green 0, blue 0.06, which the dust darkens by a random factor of 0.48 to 1), so their number shows that the packet
 * arrived and was applied. Test-only code, new in the 1.21.1 port (no 1.7.10 counterpart); CC BY-NC-SA 3.0 like the add-on (see
 * NOTICE.md), not distributed.
 *
 * <p>The particle engine keeps its particles private, so they are read by reflection (Mojang's names: both loaders run with
 * them in development). When that fails, the total count of the engine is answered instead and {@link #method} says so.</p>
 */
final class BeamParticles {
    private static Field particles;
    private static Field red;
    private static Field green;
    private static Field blue;
    static String method = "none";

    private BeamParticles() {}

    static int count(Minecraft client) {
        ParticleEngine engine = client.particleEngine;
        try {
            if (particles == null) {
                particles = ParticleEngine.class.getDeclaredField("particles");
                particles.setAccessible(true);
                red = Particle.class.getDeclaredField("rCol");
                green = Particle.class.getDeclaredField("gCol");
                blue = Particle.class.getDeclaredField("bCol");
                red.setAccessible(true);
                green.setAccessible(true);
                blue.setAccessible(true);
            }
            int found = 0;
            for (Object queue : ((Map<?, ?>) particles.get(engine)).values()) {
                for (Object particle : (Collection<?>) queue) {
                    if (!(particle instanceof DustParticle dust)) continue;
                    float r = red.getFloat(dust);
                    float g = green.getFloat(dust);
                    float b = blue.getFloat(dust);
                    if (g == 0F && r > 0F && r <= 0.041F && b > r && b <= 0.061F) found++;
                }
            }
            method = "the beam's dust particles (reflection on ParticleEngine.particles)";
            return found;
        } catch (ReflectiveOperationException | RuntimeException e) {
            method = "all particles of the engine (reflection failed: " + e + ")";
            try {
                return Integer.parseInt(engine.countParticles());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
    }
}
