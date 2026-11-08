package space;

/**
 * LESSON 02 and 06. What a planet is made of. It gives defaults (gravity, air) and, for planets
 * without their own world, which shared "archetype" world they land in. Giants have none:
 * you can't land on them.
 */
public enum BodyType {
    TERRAN("verdant", true, 0.8, 1.3, false), OCEANIC("oceanic", true, 0.7, 1.2, false),
    VERDANT("verdant", true, 0.7, 1.3, false), ARID("arid", true, 0.4, 1.6, false),
    FROZEN("frozen", true, 0.5, 1.2, false), VOLCANIC("volcanic", false, 0.8, 2.0, false),
    BARREN("barren", false, 0.1, 0.6, false), TOXIC("toxic", false, 0.8, 1.5, false),
    GAS_GIANT(null, false, 2.0, 3.0, true), ICE_GIANT(null, false, 1.5, 2.5, true);

    public final String archetype;
    public final boolean breathable;
    public final double minGravity, maxGravity;
    public final boolean giant;

    BodyType(String archetype, boolean breathable, double minGravity, double maxGravity, boolean giant) {
        this.archetype = archetype;
        this.breathable = breathable;
        this.minGravity = minGravity;
        this.maxGravity = maxGravity;
        this.giant = giant;
    }
}
