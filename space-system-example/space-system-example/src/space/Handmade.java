package space;

import java.util.List;

/**
 * LESSON 02. Hand-made systems. In the mod these are JSON files (see data/vegeta.json) read by a
 * Codec; here they're written in code so the example needs no libraries. Compare them side by side.
 */
public final class Handmade {
    private Handmade() {}

    public static StarSystem vegeta() {
        String id = "vegeta";
        return new StarSystem(id, "Vegeta System", new Vec3(20480, 540, 12288),
                List.of(new Star("Vegeta's Sun", StarClass.K, 240, Orbit.NONE)),
                List.of(
                        Body.handmade(id, "vegeta", "Planet Vegeta", BodyType.ARID, null, Orbit.of(1500, 30, 70, 0), 150, "vegeta"),
                        Body.handmade(id, "vegeta_moon", "Vegeta's Moon", BodyType.BARREN, "vegeta", Orbit.of(380, 200, 11, 0), 34, null),
                        Body.handmade(id, "vampa", "Vampa", BodyType.ARID, null, Orbit.of(800, 250, 35, 0), 90, null),
                        Body.handmade(id, "vegeta_giant", "Kehar", BodyType.GAS_GIANT, null, Orbit.of(2050, 120, 150, 0), 250, null)),
                2400);
    }
}
