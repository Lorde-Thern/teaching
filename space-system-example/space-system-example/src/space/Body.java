package space;

/**
 * LESSON 02. A planet or moon. A RECORD: a class that only holds values (Java writes the
 * constructor and getters).
 *
 * @param id           globally unique: "systemId/localId"
 * @param parent       local id of the body it orbits (moons), or null = it orbits the system centre
 * @param ownDimension a world of its own to land in, or null = land in its type's shared world
 */
public record Body(String id, String localId, String name, BodyType type, String parent, Orbit orbit,
                   double radius, double atmosphereRadius, double gravity, String ownDimension) {

    /** Like the mod's JSON loading: anything left out gets a default from the type. */
    public static Body handmade(String systemId, String localId, String name, BodyType type, String parent,
                                Orbit orbit, double radius, String ownDimension) {
        return new Body(systemId + "/" + localId, localId, name, type, parent, orbit, radius,
                radius * 1.4,                                      // default atmosphere
                (type.minGravity + type.maxGravity) / 2,           // default gravity
                ownDimension);
    }

    /** LESSON 06. Where you land: its own world, or its type's shared world (null = can't land). */
    public String landingDimension() {
        if (ownDimension != null) return ownDimension;
        return type.archetype == null ? null : "world_" + type.archetype;
    }

    public boolean landable() {
        return landingDimension() != null;
    }
}
