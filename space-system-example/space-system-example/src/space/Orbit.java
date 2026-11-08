package space;

/**
 * LESSON 03. A circular orbit. Position is a FUNCTION OF TIME: nothing is ever "moved".
 *
 * Stored in maths units (radians, ticks). Humans write degrees and minutes, so the
 * factory method converts once, at the border. Minecraft runs 20 ticks a second,
 * so one minute is 1,200 ticks.
 */
public record Orbit(double radius, double phase, double periodTicks, double inclination) {
    public static final Orbit NONE = new Orbit(0, 0, 0, 0);
    public static final double TICKS_PER_MINUTE = 1200;

    /** The human-friendly way to make an orbit: degrees and minutes, like the mod's JSON files. */
    public static Orbit of(double radius, double phaseDegrees, double periodMinutes, double inclinationDegrees) {
        return new Orbit(radius, Math.toRadians(phaseDegrees), periodMinutes * TICKS_PER_MINUTE, Math.toRadians(inclinationDegrees));
    }

    /** Offset from the orbit's centre at the given time (ticks). */
    public Vec3 offset(double time) {
        if (radius <= 0) return Vec3.ZERO;
        double angle = phase + (periodTicks > 0 ? Math.PI * 2 * time / periodTicks : 0);
        double along = radius * Math.sin(angle);
        return new Vec3(radius * Math.cos(angle), along * Math.sin(inclination), along * Math.cos(inclination));
    }
}
