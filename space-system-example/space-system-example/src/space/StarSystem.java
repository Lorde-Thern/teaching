package space;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One or more stars plus the planets and moons that orbit them. */
public final class StarSystem {
    public final String id;
    public final String name;
    public final Vec3 position;
    public final List<Star> stars;
    public final List<Body> bodies;
    public final double beltRadius;   // 0 = no asteroid belt
    private final Map<String, Body> byLocalId = new HashMap<>();
    private final double extent;

    public StarSystem(String id, String name, Vec3 position, List<Star> stars, List<Body> bodies, double beltRadius) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.stars = List.copyOf(stars);
        this.bodies = List.copyOf(bodies);
        this.beltRadius = beltRadius;
        for (Body b : this.bodies) byLocalId.put(b.localId(), b);
        double reach = beltRadius;
        for (Star s : this.stars) reach = Math.max(reach, s.orbit().radius() + s.radius());
        for (Body b : this.bodies) reach = Math.max(reach, distanceFromCentre(b) + b.atmosphereRadius());
        this.extent = reach;
    }

    private double distanceFromCentre(Body b) {
        Body parent = b.parent() == null ? null : byLocalId.get(b.parent());
        return b.orbit().radius() + (parent == null ? 0 : distanceFromCentre(parent));
    }

    /**
     * LESSON 03. RECURSION: a moon's position is its parent's position plus its own offset,
     * and the parent's position is worked out the same way.
     */
    public Vec3 bodyPosition(Body body, double time) {
        Body parent = body.parent() == null ? null : byLocalId.get(body.parent());
        Vec3 centre = parent == null ? position : bodyPosition(parent, time);
        return centre.add(body.orbit().offset(time));
    }

    public Vec3 starPosition(int index, double time) {
        return position.add(stars.get(index).orbit().offset(time));
    }

    public Body local(String localId) {
        return byLocalId.get(localId);
    }

    /** Radius around the centre that contains everything in the system. */
    public double extent() {
        return extent;
    }

    /** A system's fingerprint, for checking two computers generated the same thing. */
    public String describe() {
        StringBuilder sb = new StringBuilder(String.format("%s at %s, %d star(s): ", name, position, stars.size()));
        for (Star s : stars) sb.append(s.starClass()).append(' ');
        sb.append("| ").append(bodies.size()).append(" bodies:");
        for (Body b : bodies) sb.append(' ').append(b.name()).append('(').append(b.type()).append(')');
        return sb.toString();
    }
}
