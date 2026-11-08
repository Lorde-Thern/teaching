package space;

/** A top-down text picture of a system at a moment in time: '*' = star, letters = planets, '.' = orbit paths. */
public final class AsciiMap {
    private AsciiMap() {}

    public static String draw(StarSystem s, double time, int width, int height) {
        char[][] g = new char[height][width];
        for (char[] row : g) java.util.Arrays.fill(row, ' ');
        double reach = 0;
        for (Body b : s.bodies) if (b.parent() == null) reach = Math.max(reach, b.orbit().radius());
        // Characters are about twice as tall as wide, so x is stretched 2x: fit both directions.
        double scale = Math.min((width / 2.0 - 1) / 2.0, height / 2.0 - 1) / Math.max(reach, 1);
        int cx = width / 2, cy = height / 2;
        // Orbit paths (only planets', moons are too small at this scale).
        for (Body b : s.bodies) {
            if (b.parent() != null) continue;
            for (int i = 0; i < 360; i += 2) {
                double a = Math.toRadians(i), r = b.orbit().radius() * scale;
                put(g, cx + (int) Math.round(Math.cos(a) * r * 2), cy + (int) Math.round(Math.sin(a) * r), '.');
            }
        }
        put(g, cx, cy, '*');
        char letter = 'A';
        StringBuilder key = new StringBuilder();
        for (Body b : s.bodies) {
            if (b.parent() != null) continue;
            Vec3 rel = s.bodyPosition(b, time).subtract(s.position);
            put(g, cx + (int) Math.round(rel.x() * scale * 2), cy + (int) Math.round(rel.z() * scale), letter);
            key.append("  ").append(letter).append(" = ").append(b.name()).append(" (").append(b.type()).append(")\n");
            letter++;
        }
        StringBuilder out = new StringBuilder();
        for (char[] row : g) out.append(new String(row).replaceAll("\\s+$", "")).append('\n');
        return out.append(key).toString();
    }

    private static void put(char[][] g, int x, int y, char c) {
        if (y >= 0 && y < g.length && x >= 0 && x < g[0].length) g[y][x] = c;
    }
}
