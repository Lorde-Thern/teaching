package space;

import java.util.List;
import java.util.Optional;

/**
 * Runs one demo per lesson of the guide.
 *
 *   java -cp out space.Main          every lesson, one after another
 *   java -cp out space.Main 4        just lesson 4
 */
public final class Main {
    private static final long WORLD_SEED = 1234567890L;

    public static void main(String[] args) {
        int only = args.length > 0 ? Integer.parseInt(args[0]) : -1;
        Runnable[] lessons = {null, null, Main::lesson02, Main::lesson03, Main::lesson04, Main::lesson05,
                Main::lesson06, Main::lesson07, Main::lesson08, Main::lesson09};
        for (int i = 0; i < lessons.length; i++) {
            if (lessons[i] == null || (only >= 0 && only != i)) continue;
            lessons[i].run();
            System.out.println();
        }
        if (only >= 0 && (only >= lessons.length || lessons[only] == null)) {
            System.out.println("There's a demo for lessons 2 to 9. Try: java -cp out space.Main 4");
        }
    }

    private static void title(String text) {
        System.out.println("=".repeat(78));
        System.out.println(text);
        System.out.println("=".repeat(78));
    }

    // ---------------------------------------------------------------- 02
    static void lesson02() {
        title("LESSON 02  Describing a solar system: records and defaults");
        StarSystem vegeta = Handmade.vegeta();
        System.out.println("Compare with data/vegeta.json, the mod's real file for this system.\n");
        for (Body b : vegeta.bodies) {
            System.out.printf("%-14s %-9s radius %4.0f  atmosphere %5.0f  gravity x%.2f  orbits %s%n", b.name(), b.type(),
                    b.radius(), b.atmosphereRadius(), b.gravity(), b.parent() == null ? "the sun" : vegeta.local(b.parent()).name());
        }
        System.out.println("\nNo atmosphere or gravity was given for these, so they come from the type:");
        System.out.println("  atmosphere = radius x 1.4,  gravity = halfway between the type's min and max.");
        System.out.println("(The real Vegeta file sets atmosphere 205 and gravity 2.0 explicitly; this example leaves them out to show the defaults.)");
        Orbit o = Orbit.of(1500, 30, 70, 0);
        System.out.printf("%nUnits convert at the border: phase 30 degrees -> %.4f radians, period 70 minutes -> %.0f ticks.%n",
                o.phase(), o.periodTicks());
    }

    // ---------------------------------------------------------------- 03
    static void lesson03() {
        title("LESSON 03  Making things orbit: position is a function of time");
        StarSystem vegeta = Handmade.vegeta();
        Body planet = vegeta.local("vegeta"), moon = vegeta.local("vegeta_moon");
        System.out.println("Nothing is stored or moved. Ask for a time, get a position:\n");
        for (int minute = 0; minute <= 70; minute += 14) {
            double t = minute * Orbit.TICKS_PER_MINUTE;
            Vec3 p = vegeta.bodyPosition(planet, t), m = vegeta.bodyPosition(moon, t);
            System.out.printf("  minute %2d   Vegeta %-22s  its moon %-22s  moon-to-planet %.0f%n",
                    minute, p, m, m.distanceTo(p));
        }
        System.out.println("\nThe moon is always 380 blocks from Vegeta: its position is Vegeta's position");
        System.out.println("PLUS its own orbit offset (bodyPosition calls itself for the parent: recursion).");
        System.out.println("After 70 minutes Vegeta is back where it started: its period is 70 minutes.\n");
        System.out.println("Top-down map at minute 0:");
        System.out.print(AsciiMap.draw(vegeta, 0, 73, 25));
    }

    // ---------------------------------------------------------------- 04
    static void lesson04() {
        title("LESSON 04  An infinite galaxy from one number");
        long seed = Seeds.oneWay(WORLD_SEED);
        Galaxy a = new Galaxy(seed, List.of(Handmade.vegeta()));
        Galaxy b = new Galaxy(seed, List.of(Handmade.vegeta()));   // a second, separate computer
        System.out.println("A 5 x 5 patch of cells (8,192 blocks each). '#' = a system, '.' = empty,");
        System.out.println("'V' = the hand-made Vegeta system (cells around it are kept empty):\n");
        int vx = Galaxy.cellOf(20480), vz = Galaxy.cellOf(12288);
        for (int cz = vz - 2; cz <= vz + 2; cz++) {
            StringBuilder row = new StringBuilder("   ");
            for (int cx = vx - 2; cx <= vx + 2; cx++) {
                row.append(cx == vx && cz == vz ? " V" : a.proceduralAt(cx, cz).isPresent() ? " #" : " .");
            }
            System.out.println(row);
        }
        System.out.println("\nDeterminism: two separate Galaxy objects, same seed. Do they agree?");
        int same = 0, checked = 0;
        for (int cx = 100; cx < 110; cx++) {
            for (int cz = -5; cz < 5; cz++) {
                String da = a.proceduralAt(cx, cz).map(StarSystem::describe).orElse("empty");
                String db = b.proceduralAt(cx, cz).map(StarSystem::describe).orElse("empty");
                checked++;
                if (da.equals(db)) same++;
            }
        }
        System.out.println("  " + same + " of " + checked + " cells identical.\n");
        Optional<StarSystem> example = Optional.empty();
        for (int cx = 100; example.isEmpty(); cx++) example = a.proceduralAt(cx, 3);
        StarSystem s = example.get();
        System.out.println("One generated system (" + s.id + "):");
        System.out.println("  " + s.describe());
        System.out.print(AsciiMap.draw(s, 0, 73, 25));
        System.out.println("\nLRU cache: the galaxy has generated " + a.cellsGenerated() + " cells so far, and only when asked.");
        System.out.println("Asking again for the same cell costs nothing: " + (a.proceduralAt(vx - 1, vz - 1) == a.proceduralAt(vx - 1, vz - 1)));
        System.out.println("Finding a body from its id regenerates its cell: " + a.body(s.bodies.get(0).id()).map(Body::name).orElse("?"));
    }

    // ---------------------------------------------------------------- 05
    static void lesson05() {
        title("LESSON 05  Syncing: only a seed crosses the network");
        long sent = Seeds.mix(WORLD_SEED, 0x6A1A, 0xC7);      // what the mod sends today
        System.out.println("Server's world seed:              " + WORLD_SEED);
        System.out.println("Galaxy seed the server sends:     " + sent);
        Galaxy server = new Galaxy(sent, List.of(Handmade.vegeta()));
        Galaxy client = new Galaxy(sent, List.of(Handmade.vegeta()));   // built from the 8-byte seed alone
        String s1 = server.proceduralAt(42, -7).map(StarSystem::describe).orElse("empty");
        String c1 = client.proceduralAt(42, -7).map(StarSystem::describe).orElse("empty");
        System.out.println("Server and client agree on cell (42,-7): " + s1.equals(c1));
        System.out.println("\nThe weakness: mix() can be run backwards.");
        long recovered = Seeds.unmix(sent, 0x6A1A, 0xC7);
        System.out.println("  A player reverses the galaxy seed: " + recovered + (recovered == WORLD_SEED ? "  <- the world seed!" : ""));
        long fixed = Seeds.oneWay(WORLD_SEED);
        System.out.println("\nThe fix: send a one-way hash (SHA-256) instead: " + fixed);
        System.out.println("  Reversing it the same way gives:  " + Seeds.unmix(fixed, 0x6A1A, 0xC7) + "  (just noise)");
        System.out.println("  Galaxies built from it still match on every computer, because it's still one number.");
    }

    // ---------------------------------------------------------------- 06
    static void lesson06() {
        title("LESSON 06  Planets as places: shared worlds split into slots");
        Galaxy galaxy = new Galaxy(Seeds.oneWay(WORLD_SEED), List.of(Handmade.vegeta()));
        Slots slots = new Slots();
        int shown = 0;
        for (int cx = 0; shown < 6 && cx < 50; cx++) {
            Optional<StarSystem> s = galaxy.proceduralAt(cx, 0);
            if (s.isEmpty()) continue;
            for (Body b : s.get().bodies) {
                if (b.parent() != null || shown >= 6) continue;
                if (!b.landable()) {
                    System.out.printf("  %-16s %-9s can't be landed on (a giant has no world)%n", b.name(), b.type());
                    continue;
                }
                long slot = slots.slotFor(b);
                Vec3 c = Slots.centre(slot);
                System.out.printf("  %-16s %-9s lands in %-15s slot centre (%.0f, %.0f)%n", b.name(), b.type(), b.landingDimension(), c.x(), c.z());
                String back = slots.bodyAt(c.x() + 1234, c.z() - 5678);
                if (!back.equals(b.id())) System.out.println("    reverse lookup FAILED");
                shown++;
            }
        }
        Body vegeta = Handmade.vegeta().local("vegeta");
        System.out.println("  " + vegeta.name() + " has its own world: lands in '" + vegeta.landingDimension() + "', no slot needed.");
        System.out.println("\nReverse lookup works for any point inside a slot: \"which planet am I on?\" is one map read.");
    }

    // ---------------------------------------------------------------- 07
    static void lesson07() {
        title("LESSON 07  Travel: leaving and landing");
        StarSystem vegeta = Handmade.vegeta();
        Galaxy galaxy = new Galaxy(Seeds.oneWay(WORLD_SEED), List.of(vegeta));
        Transit transit = new Transit(galaxy, new Slots());
        Body planet = vegeta.local("vegeta");
        Transit.Player p = new Transit.Player("vegeta", planet, new Vec3(150, 70, -40));

        long t = 0;
        // Visit Kehar (a gas giant: can't land), then Vampa (lands in a shared world), then home.
        // If something else is in the way, you land there instead; then you just take off again.
        for (String target : new String[] {"vegeta_giant", "vampa", "vegeta"}) {
            Body b = vegeta.local(target);
            for (int attempt = 0; attempt < 3 && p.planet != b; attempt++) {
                // Take off: face the next stop and fly straight up until we leave orbit.
                while (!p.world.equals("space") && t < 100000) {
                    p.facing = vegeta.bodyPosition(b, t).subtract(vegeta.bodyPosition(p.planet, t));
                    p.pos = p.pos.add(new Vec3(0, 4, 0));
                    transit.tick(p, t);
                    transit.endOfTick();
                    t++;
                }
                // Cruise toward it at 6 blocks a tick (120 blocks a second) until we land or get pushed away.
                boolean pushed = false;
                for (int i = 0; i < 4000 && p.world.equals("space"); i++, t++) {
                    Vec3 goal = vegeta.bodyPosition(b, t);
                    p.pos = p.pos.add(goal.subtract(p.pos).normalize().scale(6));
                    int before = transit.log.size();
                    transit.tick(p, t);
                    transit.endOfTick();
                    if (transit.log.size() > before && transit.log.get(transit.log.size() - 1).contains("pushed")) {
                        pushed = true;
                        break;
                    }
                }
                if (pushed) break;
            }
        }
        transit.log.forEach(line -> System.out.println("  " + line));
        System.out.println("\nNotice: back on Planet Vegeta you land where you left from (150, -40),");
        System.out.println("Vampa (no world of its own) lands in the middle of its slot in world_arid,");
        System.out.println("and arrival height " + (Transit.BUILD_LIMIT + Math.min(Transit.ARRIVAL_OFFSET, Transit.EXIT_MARGIN - 16))
                + " is below the exit line " + (Transit.BUILD_LIMIT + Transit.EXIT_MARGIN) + ", so you don't bounce straight back out.");
    }

    // ---------------------------------------------------------------- 08
    static void lesson08() {
        title("LESSON 08  Gravity and air");
        System.out.println("Gravity: an attribute modifier with MULTIPLY_TOTAL multiplies by (1 + amount),");
        System.out.println("so the mod adds (gravity - 1). Heavy gravity also slows you, by log10(gravity) x 25%:\n");
        for (double g : new double[] {0.2, 1.0, 2.0, 10.0, 100.0}) {
            double amount = g - 1.0;
            double fallSpeed = 0.08 * (1 + amount);     // 0.08 = vanilla gravity per tick
            double slow = g > 1.5 ? Math.min(0.6, Math.log10(g) * 0.25) : 0;
            System.out.printf("  gravity x%-6.1f modifier %+6.1f  pull %.2f blocks/tick^2  walking speed %3.0f%%%n",
                    g, amount, fallSpeed, (1 - slow) * 100);
        }
        System.out.println("\nAir: vanilla refills the air bar every tick, so the mod keeps its OWN count and");
        System.out.println("writes it over vanilla's each tick. Air 300 = full; one point lost per interval:");
        int oxygen = 300, interval = 4, tick = 0;
        while (oxygen > -20) {
            tick++;
            if (tick % interval == 0) oxygen--;
        }
        System.out.printf("  with an interval of %d ticks, a full bar lasts %.1f seconds, then you take drowning damage.%n", interval, tick / 20.0);
    }

    // ---------------------------------------------------------------- 09
    static void lesson09() {
        title("LESSON 09  Drawing the sky: compressed distance, exact size");
        double pixelScale = 1080 / 2.0 / Math.tan(Math.toRadians(70) / 2);     // a 1080p screen, 70 degree FOV
        System.out.println("A planet of radius 150 at different true distances:\n");
        System.out.println("  true distance   drawn at   shrink factor   looks (px)   drawn as");
        for (double d : new double[] {400, 3000, 10000, 50000, 200000}) {
            double sky = Sky.skyDistance(d);
            double k = sky / d;
            double px = Sky.pixels(150, d, pixelScale);
            double pxDrawn = Sky.pixels(150 * k, sky, pixelScale);
            System.out.printf("  %13.0f   %8.1f   %13.5f   %10.2f   %s%s%n", d, sky, k, px, Sky.detailFor(px),
                    Math.abs(px - pxDrawn) < 1e-6 ? "" : "  (MISMATCH)");
        }
        System.out.println("\n'looks' is computed from the TRUE size and distance, and it is exactly what");
        System.out.println("the shrunk copy at the compressed distance shows: size / distance doesn't change.");
    }
}
