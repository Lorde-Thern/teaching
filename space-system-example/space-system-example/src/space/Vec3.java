package space;

/** A point or direction in 3D, in blocks. (Minecraft has its own Vec3; this is a minimal copy.) */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 subtract(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 scale(double k) { return new Vec3(x * k, y * k, z * k); }
    public double length() { return Math.sqrt(x * x + y * y + z * z); }
    public double distanceTo(Vec3 o) { return subtract(o).length(); }
    public Vec3 normalize() { double l = length(); return l < 1e-9 ? ZERO : scale(1 / l); }

    @Override
    public String toString() {
        return String.format("(%.0f, %.0f, %.0f)", x, y, z);
    }
}
