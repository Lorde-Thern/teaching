package space;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * LESSON 04. Builds the star system for one galaxy cell from ONLY the galaxy seed and the cell
 * coordinates. Same inputs, same system, on every computer: that's DETERMINISM, and it's why
 * the planets never need to be sent over the network.
 *
 * Simplified from the mod (no textures, rings, rotation), but the decisions and their order
 * are the same.
 */
public final class SystemGenerator {
    static final float DENSITY = 0.6F;       // chance a cell holds a system
    static final double JITTER = 0.15;       // nudge off the cell centre, as a share of a cell
    static final double MAX_ORBIT = 2400;

    private SystemGenerator() {}

    public static Optional<StarSystem> generate(long galaxySeed, int cx, int cz) {
        Random r = new Random(Seeds.mix(galaxySeed, cx, cz));     // NEVER Math.random() here!
        if (r.nextFloat() > DENSITY) return Optional.empty();

        double cell = Galaxy.CELL;
        Vec3 pos = new Vec3((cx + 0.5 + (r.nextDouble() - 0.5) * 2 * JITTER) * cell,
                380 + r.nextDouble() * 260,
                (cz + 0.5 + (r.nextDouble() - 0.5) * 2 * JITTER) * cell);
        String id = "proc/" + cx + "/" + cz;
        String systemName = NameGenerator.name(r);

        // 1. Stars: a weighted pick for the primary; sometimes one or two small companions.
        List<Star> stars = new ArrayList<>();
        StarClass primaryClass = StarClass.random(r);
        double primaryRadius = primaryClass.radius * (0.85 + r.nextDouble() * 0.3);
        stars.add(new Star(systemName, primaryClass, primaryRadius, Orbit.NONE));
        float roll = r.nextFloat();
        int companions = roll < 0.04F ? 2 : roll < 0.18F ? 1 : 0;
        double innerEdge = primaryRadius;
        for (int i = 0; i < companions; i++) {
            StarClass cls = StarClass.randomCompanion(r);
            double radius = cls.radius * (0.8 + r.nextDouble() * 0.3);
            double orbit = primaryRadius * 2.2 + radius + 260 * (i + 1) + r.nextDouble() * 150;
            stars.add(new Star(systemName + " " + (char) ('B' + i), cls, radius,
                    new Orbit(orbit, r.nextDouble() * Math.PI * 2, (20 + r.nextDouble() * 40) * 1200, 0)));
            innerEdge = Math.max(innerEdge, orbit + radius);
        }

        // 2. The habitable zone: further out for bigger (brighter) stars.
        double habitable = 700 + primaryRadius * 3.2;

        // 3. Planets, walking outward; type depends on distance from the habitable zone.
        List<Body> bodies = new ArrayList<>();
        int planetCount = 1 + r.nextInt(6);
        double orbit = innerEdge + 300 + r.nextInt(250);
        for (int i = 0; i < planetCount && orbit < MAX_ORBIT; i++) {
            BodyType type = pickType(r, orbit / habitable);
            double radius = type.giant ? 200 + r.nextDouble() * 170 : 45 + r.nextDouble() * 110;
            String local = "b" + i;
            String name = r.nextBoolean() ? NameGenerator.name(r) : systemName + " " + NameGenerator.roman(i + 1);
            double period = 20 * Math.pow(orbit / 1000.0, 1.5) * (0.8 + r.nextDouble() * 0.4);   // Kepler's third law
            double gravity = type.minGravity + r.nextDouble() * (type.maxGravity - type.minGravity);
            bodies.add(new Body(id + "/" + local, local, name, type, null,
                    new Orbit(orbit, r.nextDouble() * Math.PI * 2, period * 1200, Math.toRadians(r.nextDouble() * 3)),
                    radius, radius * 1.38 + 12, gravity, null));

            // 4. Moons: giants up to two, big rocky planets sometimes one.
            int moons = type.giant ? r.nextInt(3) : (radius > 100 && r.nextFloat() < 0.35F ? 1 : 0);
            double moonOrbit = radius * 1.9 + 40;
            for (int m = 0; m < moons; m++) {
                BodyType moonType = switch (r.nextInt(4)) {
                    case 0 -> BodyType.FROZEN;
                    case 1 -> BodyType.VOLCANIC;
                    default -> BodyType.BARREN;
                };
                double moonRadius = 18 + r.nextDouble() * Math.min(27, radius * 0.2);
                moonOrbit += moonRadius * 2 + 40 + r.nextInt(60);
                double mg = moonType.minGravity + r.nextDouble() * (moonType.maxGravity - moonType.minGravity);
                bodies.add(new Body(id + "/" + local + "m" + m, local + "m" + m, name + " " + (char) ('a' + m), moonType, local,
                        new Orbit(moonOrbit, r.nextDouble() * Math.PI * 2, (6 + r.nextDouble() * 10) * 1200, Math.toRadians(r.nextDouble() * 8)),
                        moonRadius, moonRadius * 1.3 + 12, mg, null));
            }
            orbit += (type.giant ? 520 : 330) + r.nextInt(260) + (moons > 0 ? moonOrbit : 0);
        }

        // 5. An asteroid belt, sometimes.
        double belt = r.nextFloat() < 0.35F ? innerEdge + 500 + r.nextDouble() * (MAX_ORBIT - innerEdge - 500) : 0;
        return Optional.of(new StarSystem(id, systemName, pos, stars, bodies, belt));
    }

    /** zone = orbit / habitable distance: below 0.6 is hot, 0.6 to 1.5 temperate, above that cold. */
    static BodyType pickType(Random r, double zone) {
        BodyType[] options;
        if (zone < 0.6) {
            options = new BodyType[] {BodyType.VOLCANIC, BodyType.VOLCANIC, BodyType.BARREN, BodyType.ARID, BodyType.TOXIC};
        } else if (zone < 1.5) {
            options = new BodyType[] {BodyType.TERRAN, BodyType.TERRAN, BodyType.OCEANIC, BodyType.VERDANT, BodyType.VERDANT,
                    BodyType.ARID, BodyType.TOXIC, BodyType.FROZEN};
        } else {
            options = new BodyType[] {BodyType.GAS_GIANT, BodyType.GAS_GIANT, BodyType.ICE_GIANT, BodyType.FROZEN, BodyType.BARREN};
        }
        return options[r.nextInt(options.length)];
    }
}
