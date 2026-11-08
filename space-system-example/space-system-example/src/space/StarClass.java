package space;

import java.util.Random;

/** LESSON 04. Spectral classes. Weight = how common: red dwarfs (M) are everywhere, blue giants (O) rare. */
public enum StarClass {
    O(380, 1), B(320, 3), A(270, 6), F(235, 10), G(215, 14), K(185, 18), M(150, 28),
    RED_GIANT(480, 3), WHITE_DWARF(60, 4);

    public final double radius;
    public final int weight;

    StarClass(double radius, int weight) {
        this.radius = radius;
        this.weight = weight;
    }

    /** A WEIGHTED random pick: roll a number up to the total weight, then walk the list subtracting. */
    public static StarClass random(Random r) {
        int total = 0;
        for (StarClass c : values()) total += c.weight;
        int roll = r.nextInt(total);
        for (StarClass c : values()) {
            roll -= c.weight;
            if (roll < 0) return c;
        }
        return G;
    }

    public static StarClass randomCompanion(Random r) {
        StarClass[] options = {K, M, M, WHITE_DWARF, G};
        return options[r.nextInt(options.length)];
    }
}
