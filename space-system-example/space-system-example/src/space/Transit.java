package space;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * LESSON 07. The travel rules, as a tiny simulation (no Minecraft): a player is either on a
 * planet (in some world) or in space, and every tick we check two situations.
 *
 *  - On a planet, above the build limit + margin: BREAK ORBIT into space, next to the planet.
 *  - In space, inside a landable planet's atmosphere: ENTER it, landing where you last left.
 *    Gas giants push you back out.
 *
 * Plus the mod's three safety habits: a cooldown, a queue for trips, and a cheap
 * "systems near me" list refreshed once a second.
 */
public final class Transit {
    public static final int BUILD_LIMIT = 320;
    public static final int EXIT_MARGIN = 64;          // orbitExitMargin
    public static final int ARRIVAL_OFFSET = 16;       // arrivalHeightOffset
    public static final int COOLDOWN_TICKS = 60;

    /** A player, reduced to what travel needs. */
    public static final class Player {
        public String world;                 // "space", "vegeta", "world_arid", ...
        public Body planet;                  // the planet they're on (null in space)
        public Vec3 pos;
        public Vec3 facing = new Vec3(1, 0, 0);   // which way they look: they leave orbit on that side
        public long cooldownUntil;
        public final Map<String, Vec3> departures = new HashMap<>();   // where they last left each planet
        List<StarSystem> nearby;

        public Player(String world, Body planet, Vec3 pos) {
            this.world = world;
            this.planet = planet;
            this.pos = pos;
        }
    }

    private final Galaxy galaxy;
    private final Slots slots;
    private final Queue<Runnable> pending = new ArrayDeque<>();
    public final List<String> log = new ArrayList<>();

    public Transit(Galaxy galaxy, Slots slots) {
        this.galaxy = galaxy;
        this.slots = slots;
    }

    /** Called for every player every tick. It only DECIDES; trips are queued (see endOfTick). */
    public void tick(Player p, long time) {
        if (time < p.cooldownUntil) return;
        if (!p.world.equals("space")) {
            if (p.planet != null && p.pos.y() >= BUILD_LIMIT + EXIT_MARGIN) {
                p.cooldownUntil = time + COOLDOWN_TICKS;
                pending.add(() -> breakOrbit(p, time));
            }
            return;
        }
        // Once a second (every 20 ticks), refresh the short list of systems worth checking.
        if (p.nearby == null || time % 20 == 0) {
            p.nearby = new ArrayList<>();
            for (StarSystem s : galaxy.systemsNear(p.pos, 1)) {
                if (s.position.distanceTo(p.pos) < s.extent() + 1200) p.nearby.add(s);
            }
        }
        for (StarSystem s : p.nearby) {
            for (Body b : s.bodies) {
                Vec3 bodyPos = s.bodyPosition(b, time);
                if (bodyPos.distanceTo(p.pos) >= b.atmosphereRadius()) continue;
                if (b.landable()) {
                    p.cooldownUntil = time + COOLDOWN_TICKS;
                    pending.add(() -> enterAtmosphere(p, b, time));
                } else {
                    p.pos = bodyPos.add(p.pos.subtract(bodyPos).normalize().scale(b.atmosphereRadius() + 5));
                    log.add("t=" + time + "  " + b.name() + " is a " + b.type() + ": you can't land, and you're pushed back out");
                }
                return;
            }
        }
    }

    /**
     * Teleports run AFTER everyone's tick. Moving a player to another world while the game is
     * still looping over that world's players can break the loop; afterwards it's always safe.
     */
    public void endOfTick() {
        Runnable next;
        while ((next = pending.poll()) != null) next.run();
    }

    private void breakOrbit(Player p, long time) {
        Body body = p.planet;
        StarSystem system = galaxy.system(body.id().substring(0, body.id().lastIndexOf('/'))).orElseThrow();
        p.departures.put(body.id(), new Vec3(p.pos.x(), 0, p.pos.z()));            // remember where we left
        Vec3 bodyPos = system.bodyPosition(body, time);
        p.world = "space";
        p.planet = null;
        p.pos = bodyPos.add(p.facing.normalize().scale(body.atmosphereRadius() + 24));   // just outside, on the side you face
        log.add("t=" + time + "  left orbit of " + body.name() + " -> space at " + p.pos);
    }

    private void enterAtmosphere(Player p, Body body, long time) {
        // Land where you last left; else the planet's own spawn; else the middle of its slot.
        Vec3 landing = p.departures.get(body.id());
        String how = "where you last left";
        if (landing == null && body.ownDimension() != null) {
            landing = Vec3.ZERO;
            how = "its world's spawn";
        }
        if (landing == null) {
            landing = Slots.centre(slots.slotFor(body));
            how = "the middle of its slot";
        }
        // Arrive BELOW the exit line (min(offset, margin - 16)) or you'd be thrown straight back out.
        int y = BUILD_LIMIT + Math.min(ARRIVAL_OFFSET, EXIT_MARGIN - 16);
        p.world = body.landingDimension();
        p.planet = body;
        p.pos = new Vec3(landing.x(), y, landing.z());
        log.add("t=" + time + "  entered " + body.name() + "'s atmosphere -> " + p.world + " at " + p.pos + " (" + how + ")");
    }
}
