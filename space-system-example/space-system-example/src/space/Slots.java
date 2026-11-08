package space;

import java.util.HashMap;
import java.util.Map;

/**
 * LESSON 06. Thousands of planets share a handful of "archetype" worlds (all desert planets
 * land in world_arid). Each planet gets its own REGION-sized square, its slot, so two planets
 * in the same world never meet. In the mod this is saved with the world (SavedData).
 *
 * Two maps, so the question can be asked both ways: planet -> slot, and slot -> planet.
 */
public final class Slots {
    public static final int REGION = 20000;
    static final int SLOT_RANGE = 700;

    private final Map<String, Long> slotsByBody = new HashMap<>();
    private final Map<Long, String> bodiesBySlot = new HashMap<>();

    public long slotFor(Body body) {
        Long existing = slotsByBody.get(body.id());
        if (existing != null) return existing;
        // Start from a slot picked by hashing the planet's id; if it's taken, walk to the next free one.
        long hash = Seeds.mix(body.id().hashCode(), body.id().length(), 17);
        int span = SLOT_RANGE * 2 + 1;
        int sx = (int) Math.floorMod(hash, (long) span) - SLOT_RANGE;
        int sz = (int) Math.floorMod(hash >>> 21, (long) span) - SLOT_RANGE;
        long slot = pack(sx, sz);
        while (bodiesBySlot.containsKey(slot) || (sx == 0 && sz == 0)) {    // (0,0) stays free: the world's spawn
            sx = sx >= SLOT_RANGE ? -SLOT_RANGE : sx + 1;
            slot = pack(sx, sz);
        }
        slotsByBody.put(body.id(), slot);
        bodiesBySlot.put(slot, body.id());
        return slot;
    }

    /** "Which planet am I on?" for a position in an archetype world. */
    public String bodyAt(double x, double z) {
        return bodiesBySlot.get(pack((int) Math.round(x / REGION), (int) Math.round(z / REGION)));
    }

    public static Vec3 centre(long slot) {
        return new Vec3((int) (slot >> 32) * (double) REGION, 0, (int) slot * (double) REGION);
    }

    static long pack(int sx, int sz) {
        return ((long) sx << 32) | (sz & 0xFFFFFFFFL);
    }
}
