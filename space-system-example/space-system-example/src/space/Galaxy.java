package space;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LESSON 04. The whole galaxy: hand-made systems plus a procedural system in (most) cells.
 * Cells are generated ON DEMAND and CACHED, so the galaxy is effectively infinite but costs
 * nothing until someone looks.
 */
public final class Galaxy {
    public static final int CELL = 8192;
    static final int CACHE_SIZE = 8192;

    public final long seed;
    public final List<StarSystem> handcrafted;
    private int generated;    // for the demo: how many cells were actually generated

    /**
     * A LinkedHashMap made with accessOrder = true keeps entries in order of last use.
     * Overriding removeEldestEntry turns it into a complete LRU ("least recently used") cache.
     */
    private final Map<Long, Optional<StarSystem>> cells = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Optional<StarSystem>> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public Galaxy(long seed, List<StarSystem> handcrafted) {
        this.seed = seed;
        this.handcrafted = List.copyOf(handcrafted);
    }

    public static int cellOf(double coordinate) {
        return (int) Math.floor(coordinate / CELL);
    }

    public Optional<StarSystem> proceduralAt(int cx, int cz) {
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);     // two ints packed into one long
        return cells.computeIfAbsent(key, k -> {
            generated++;
            return blockedByHandcrafted(cx, cz) ? Optional.empty() : SystemGenerator.generate(seed, cx, cz);
        });
    }

    public int cellsGenerated() {
        return generated;
    }

    /** Cells near a hand-made system stay empty, so the two never overlap. */
    private boolean blockedByHandcrafted(int cx, int cz) {
        double x = (cx + 0.5) * CELL, z = (cz + 0.5) * CELL;
        for (StarSystem s : handcrafted) {
            double dx = s.position.x() - x, dz = s.position.z() - z;
            double clearance = s.extent() + CELL * 0.75;
            if (dx * dx + dz * dz < clearance * clearance) return true;
        }
        return false;
    }

    public List<StarSystem> systemsNear(Vec3 pos, int cellRadius) {
        int cx = cellOf(pos.x()), cz = cellOf(pos.z());
        List<StarSystem> out = new ArrayList<>();
        for (int x = cx - cellRadius; x <= cx + cellRadius; x++)
            for (int z = cz - cellRadius; z <= cz + cellRadius; z++) proceduralAt(x, z).ifPresent(out::add);
        for (StarSystem s : handcrafted) {
            if (Math.abs(cellOf(s.position.x()) - cx) <= cellRadius && Math.abs(cellOf(s.position.z()) - cz) <= cellRadius) out.add(s);
        }
        return out;
    }

    /** Find a system from its id. Procedural ids encode their cell, so we just regenerate it. */
    public Optional<StarSystem> system(String id) {
        for (StarSystem s : handcrafted) if (s.id.equals(id)) return Optional.of(s);
        String[] parts = id.split("/");
        if (parts.length != 3 || !parts[0].equals("proc")) return Optional.empty();
        return proceduralAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    /** A body id is its system id plus "/" plus its local id. */
    public Optional<Body> body(String id) {
        int slash = id.lastIndexOf('/');
        if (slash < 0) return Optional.empty();
        return system(id.substring(0, slash)).map(s -> s.local(id.substring(slash + 1)));
    }
}
