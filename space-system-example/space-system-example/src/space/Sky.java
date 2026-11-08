package space;

/**
 * LESSON 09. Space is too big to draw: a graphics card only draws things between a near and a
 * far limit. So each object is drawn at a COMPRESSED distance and SHRUNK BY THE SAME FACTOR.
 * Apparent size depends only on size / distance, so it looks exactly right.
 */
public final class Sky {
    private Sky() {}

    /** Grows with the logarithm of distance: always inside the far limit, and nearer things stay nearer. */
    public static double skyDistance(double dist) {
        return 10 + 8 * Math.log1p(dist / 20);
    }

    /** How many pixels tall something of this radius looks at this distance (pixelScale depends on screen and FOV). */
    public static double pixels(double radius, double dist, double pixelScale) {
        double angle = Math.asin(Math.min(0.999, radius / dist));
        return Math.tan(angle) * pixelScale;
    }

    /** Level of detail: spend effort only where it shows. */
    public static String detailFor(double px) {
        if (px < 1.5) return "glowing dot";
        if (px < 20) return "low-detail sphere";
        if (px < 110) return "medium sphere";
        return "detailed sphere";
    }
}
