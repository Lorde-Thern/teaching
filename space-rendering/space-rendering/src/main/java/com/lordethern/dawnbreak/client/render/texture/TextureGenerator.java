package com.lordethern.dawnbreak.client.render.texture;

import com.lordethern.dawnbreak.galaxy.BodyType;
import com.lordethern.dawnbreak.galaxy.Palette;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.world.phys.Vec3;

/**
 * Procedural equirectangular textures. Every pixel is sampled on the unit sphere with 3D
 * noise, so there are no seams or pinched poles. Runs on worker threads.
 *
 * <p>Surface alpha encodes emission: 255 = plain surface, lower = glows on the night side
 * (lava, molten cracks).</p>
 */
public final class TextureGenerator {
    private TextureGenerator() {}

    // ---------------------------------------------------------------- palettes

    /** Base (land, ocean, accent) colours per type. */
    private static int[] basePalette(BodyType type) {
        return switch (type) {
            case TERRAN -> new int[]{0x3E7B2F, 0x1C4E9C, 0x9C8A5E};
            case OCEANIC -> new int[]{0x5A8A3A, 0x165FB0, 0xE0D3A0};
            case VERDANT -> new int[]{0x2E8F3E, 0x1F6F8F, 0x86C452};
            case ARID -> new int[]{0xC98A4E, 0x8A5A3A, 0x7A4426};
            case FROZEN -> new int[]{0xE6F0F8, 0x2E5E8E, 0x9FC4E0};
            case VOLCANIC -> new int[]{0x2A2220, 0xFF5A10, 0x4A3A34};
            case BARREN -> new int[]{0x9A9894, 0x5E5C58, 0xB8B6B0};
            case TOXIC -> new int[]{0xB8C040, 0x6E8A20, 0xE0E878};
            case GAS_GIANT -> new int[]{0xD8B68C, 0xF2E6D0, 0xA0663E};
            case ICE_GIANT -> new int[]{0x7FD0E8, 0xBFEFFF, 0x4A9FC8};
        };
    }

    public static int[] palette(TextureSpec spec) {
        int[] base = basePalette(spec.type());
        Palette p = spec.palette();
        int[] colours = {p.land().orElse(base[0]), p.ocean().orElse(base[1]), p.accent().orElse(base[2])};
        if (spec.vary()) {
            RandomSource r = RandomSource.create(spec.seed() ^ 0x5DEECE66DL);
            float hue = (r.nextFloat() - 0.5F) * 0.12F;
            float sat = 0.85F + r.nextFloat() * 0.35F;
            for (int i = 0; i < 3; i++) colours[i] = shift(colours[i], hue, sat);
        }
        return colours;
    }

    /** Average colour as seen from far away, for dots and placeholders. */
    public static int previewColor(TextureSpec spec) {
        if (spec.star()) return spec.starColor();
        int[] c = palette(spec);
        return switch (spec.type()) {
            case TERRAN, OCEANIC -> mix(c[1], c[0], 0.35F);
            case VERDANT -> mix(c[0], c[1], 0.3F);
            case FROZEN -> c[0];
            default -> mix(c[0], c[2], 0.3F);
        };
    }

    // ---------------------------------------------------------------- entry points

    public static NativeImage surface(TextureSpec spec, int width, int height) {
        int[] pal = palette(spec);
        float[][] p = {rgb(pal[0]), rgb(pal[1]), rgb(pal[2])};
        Noise n = new Noise(spec.seed());
        Crater[] craters = spec.type() == BodyType.BARREN ? craters(spec.seed(), 140) : null;
        GasBands bands = spec.type().isGiant() ? new GasBands(spec.seed(), spec.type() == BodyType.ICE_GIANT) : null;
        float[] starRgb = rgb(spec.starColor());

        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        float[] out = new float[4];
        for (int y = 0; y < height; y++) {
            double lat = Math.PI / 2 - Math.PI * (y + 0.5) / height;
            double cl = Math.cos(lat), sl = Math.sin(lat);
            for (int x = 0; x < width; x++) {
                double lon = Math.PI * 2 * (x + 0.5) / width;
                double px = cl * Math.cos(lon), py = sl, pz = cl * Math.sin(lon);
                out[3] = 1;
                if (spec.star()) {
                    star(n, px, py, pz, starRgb, out);
                } else {
                    switch (spec.type()) {
                        case TERRAN -> earthlike(n, px, py, pz, p, 0.02, out);
                        case OCEANIC -> earthlike(n, px, py, pz, p, 0.24, out);
                        case VERDANT -> earthlike(n, px, py, pz, p, -0.1, out);
                        case ARID -> arid(n, px, py, pz, p, out);
                        case FROZEN -> frozen(n, px, py, pz, p, out);
                        case VOLCANIC -> volcanic(n, px, py, pz, p, out);
                        case BARREN -> barren(n, px, py, pz, p, craters, out);
                        case TOXIC -> toxic(n, px, py, pz, p, out);
                        case GAS_GIANT, ICE_GIANT -> bands.sample(n, px, py, pz, lon, lat, p, out);
                    }
                }
                image.setPixelRGBA(x, y, pack(out[0], out[1], out[2], out[3]));
            }
        }
        return image;
    }

    public static NativeImage clouds(TextureSpec spec, int width, int height) {
        Noise n = new Noise(spec.seed() * 31 + 7);
        boolean toxic = spec.type() == BodyType.TOXIC;
        double coverage = switch (spec.type()) {
            case OCEANIC -> 0.05;
            case TOXIC -> -0.1;
            case FROZEN -> 0.12;
            default -> 0.08;
        };
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            double lat = Math.PI / 2 - Math.PI * (y + 0.5) / height;
            double cl = Math.cos(lat), sl = Math.sin(lat);
            for (int x = 0; x < width; x++) {
                double lon = Math.PI * 2 * (x + 0.5) / width;
                double px = cl * Math.cos(lon), py = sl, pz = cl * Math.sin(lon);
                // Stretch along latitude for banded weather, warp for swirls.
                double wx = px + 0.45 * n.fbm(n.c, px * 1.7, py * 1.7, pz * 1.7, 3);
                double wz = pz + 0.45 * n.fbm(n.d, px * 1.7 + 9, py * 1.7, pz * 1.7, 3);
                double c = n.fbm(n.a, wx * 2.6, py * 5.0, wz * 2.6, 6);
                float a = (float) smooth(coverage, coverage + 0.42, c) * 0.92F;
                float shade = (float) (0.88 + 0.12 * n.fbm(n.b, px * 9, py * 9, pz * 9, 2));
                float r = toxic ? 0.86F * shade : shade, g = toxic ? 0.93F * shade : shade, b = toxic ? 0.55F * shade : shade;
                image.setPixelRGBA(x, y, pack(r, g, b, a));
            }
        }
        return image;
    }

    public static NativeImage rings(TextureSpec spec) {
        int width = 512;
        int[] pal = palette(spec);
        float[] a = rgb(pal[0]), b = rgb(pal[2]), c = rgb(pal[1]);
        Noise n = new Noise(spec.seed() ^ 0xA5A5L);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, 4, false);
        for (int x = 0; x < width; x++) {
            double t = x / (double) (width - 1);
            double band = n.fbm(n.a, t * 18, 0.5, 0.5, 5);
            double fine = n.a.getValue(t * 140, 3.1, 0.2) * 0.5 + 0.5;
            double density = Mth.clamp(0.55 + band * 0.9, 0, 1) * (0.75 + 0.25 * fine);
            // A couple of sharp gaps, like Cassini's division.
            double gap1 = Math.abs(t - (0.55 + n.b.getValue(1, 2, 3) * 0.08));
            double gap2 = Math.abs(t - (0.8 + n.b.getValue(4, 5, 6) * 0.05));
            if (gap1 < 0.025) density *= 0.08;
            if (gap2 < 0.012) density *= 0.25;
            density *= smooth(0.0, 0.06, t) * (1 - smooth(0.9, 1.0, t));
            float[] col = lerp(lerp(a, b, (float) Mth.clamp(band + 0.5, 0, 1)), c, (float) fine * 0.3F);
            for (int y = 0; y < 4; y++) image.setPixelRGBA(x, y, pack(col[0], col[1], col[2], (float) density * 0.9F));
        }
        return image;
    }

    /** The galactic backdrop: a dust lane band plus coloured nebula clouds, kept dim. */
    public static NativeImage nebula(long seed, int width, int height) {
        Noise n = new Noise(seed ^ 0x4E4542L);
        Vec3 plane = galacticNormal(seed);
        RandomSource r = RandomSource.create(seed);
        float[] c1 = hsv(0.72F + r.nextFloat() * 0.12F, 0.7F, 1.0F);
        float[] c2 = hsv(0.55F + r.nextFloat() * 0.1F, 0.75F, 1.0F);
        float[] c3 = hsv(0.92F + r.nextFloat() * 0.1F, 0.6F, 1.0F);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            double lat = Math.PI / 2 - Math.PI * (y + 0.5) / height;
            double cl = Math.cos(lat), sl = Math.sin(lat);
            for (int x = 0; x < width; x++) {
                double lon = Math.PI * 2 * (x + 0.5) / width;
                double px = cl * Math.cos(lon), py = sl, pz = cl * Math.sin(lon);
                double b = px * plane.x + py * plane.y + pz * plane.z;
                double band = Math.exp(-(b * b) / (2 * 0.13 * 0.13));
                double dust = n.fbm(n.a, px * 4, py * 4, pz * 4, 6);
                double lanes = smooth(0.05, 0.4, n.fbm(n.b, px * 6, py * 6, pz * 6, 5)) * Math.exp(-(b * b) / (2 * 0.04 * 0.04));
                double milky = band * (0.55 + 0.45 * dust) * (1 - lanes * 0.85);
                double neb1 = smooth(0.15, 0.75, n.fbm(n.c, px * 2.2, py * 2.2, pz * 2.2, 6));
                double neb2 = smooth(0.25, 0.8, n.fbm(n.d, px * 2.8 + 5, py * 2.8, pz * 2.8, 6));
                double neb3 = smooth(0.35, 0.85, n.fbm(n.a, px * 3.5 + 11, py * 3.5, pz * 3.5, 5));
                float rr = (float) (milky * 0.27 + neb1 * c1[0] * 0.26 + neb2 * c2[0] * 0.17 + neb3 * c3[0] * 0.13);
                float gg = (float) (milky * 0.24 + neb1 * c1[1] * 0.26 + neb2 * c2[1] * 0.17 + neb3 * c3[1] * 0.13);
                float bb = (float) (milky * 0.30 + neb1 * c1[2] * 0.26 + neb2 * c2[2] * 0.17 + neb3 * c3[2] * 0.13);
                image.setPixelRGBA(x, y, pack(rr, gg, bb, 1));
            }
        }
        return image;
    }

    /** Soft round glow used for stars, coronas and distant dots. */
    public static NativeImage glow(int size) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = (x + 0.5) / size * 2 - 1, dy = (y + 0.5) / size * 2 - 1;
                double d = Math.min(1, Math.sqrt(dx * dx + dy * dy));
                double a = Math.pow(1 - d, 2.4) * 0.85 + Math.pow(Math.max(0, 1 - d * 3.5), 2) * 0.15;
                image.setPixelRGBA(x, y, pack(1, 1, 1, (float) a));
            }
        }
        return image;
    }

    /** Hollow diamond for waypoints. */
    public static NativeImage marker(int size) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = Math.abs((x + 0.5) / size * 2 - 1), dy = Math.abs((y + 0.5) / size * 2 - 1);
                double d = dx + dy;
                float a = d > 0.72 && d < 0.95 ? 1F : 0F;
                image.setPixelRGBA(x, y, pack(1, 1, 1, a));
            }
        }
        return image;
    }

    public static NativeImage solid(int colour) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, 4, 4, false);
        float[] c = rgb(colour);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) image.setPixelRGBA(x, y, pack(c[0], c[1], c[2], 1));
        return image;
    }

    public static Vec3 galacticNormal(long seed) {
        RandomSource r = RandomSource.create(seed ^ 0x6A1AL);
        return new Vec3(r.nextDouble() - 0.5, 1.6, r.nextDouble() - 0.5).normalize();
    }

    // ---------------------------------------------------------------- surface types

    private static void earthlike(Noise n, double x, double y, double z, float[][] p, double sea, float[] out) {
        double wx = x + 0.32 * n.fbm(n.b, x * 1.3, y * 1.3, z * 1.3, 3);
        double wy = y + 0.32 * n.fbm(n.c, x * 1.3 + 7, y * 1.3, z * 1.3, 3);
        double wz = z + 0.32 * n.fbm(n.d, x * 1.3, y * 1.3 + 7, z * 1.3, 3);
        double h = n.fbm(n.a, wx * 1.5, wy * 1.5, wz * 1.5, 7) + 0.12 * n.ridged(n.b, wx * 4, wy * 4, wz * 4, 3) - 0.06;
        double lat = Math.abs(y);
        double temp = 1 - lat * 1.15 - Math.max(0, h - sea) * 1.4 + n.fbm(n.c, x * 3, y * 3, z * 3, 2) * 0.12;
        float[] col;
        if (temp < 0.1) {
            col = shade(new float[]{0.93F, 0.96F, 0.99F}, 0.94 + 0.06 * n.a.getValue(x * 20, y * 20, z * 20));
        } else if (h < sea) {
            double depth = smooth(0, 0.28, sea - h);
            col = lerp(lighten(p[1], 0.35F), darken(p[1], 0.55F), (float) depth);
        } else {
            double e = h - sea;
            double moisture = n.fbm(n.d, x * 2.2, y * 2.2, z * 2.2, 4);
            col = lerp(lighten(p[0], 0.15F), darken(p[0], 0.55F), (float) smooth(-0.3, 0.4, moisture));
            if (moisture < -0.12 && temp > 0.4) col = lerp(col, p[2], (float) smooth(-0.12, -0.32, moisture));
            if (e < 0.012) col = lerp(new float[]{0.78F, 0.72F, 0.52F}, col, (float) (e / 0.012));
            if (e > 0.2) col = lerp(col, new float[]{0.42F, 0.38F, 0.33F}, (float) smooth(0.2, 0.3, e));
            if (e > 0.32 || temp < 0.18) col = lerp(col, new float[]{0.95F, 0.96F, 0.98F}, (float) Math.max(smooth(0.32, 0.4, e), smooth(0.18, 0.1, temp)));
            col = shade(col, 0.92 + 0.08 * n.a.getValue(x * 24, y * 24, z * 24));
        }
        set(out, col, 1);
    }

    private static void arid(Noise n, double x, double y, double z, float[][] p, float[] out) {
        double h = n.fbm(n.a, x * 1.8, y * 1.8, z * 1.8, 6);
        double canyon = n.ridged(n.b, x * 3, y * 3, z * 3, 4);
        double dunes = Math.sin((x * 55 + y * 20 + n.fbm(n.c, x * 3, y * 3, z * 3, 3) * 9)) * 0.5 + 0.5;
        float[] col = lerp(p[2], p[0], (float) smooth(-0.45, 0.45, h));
        col = shade(col, 0.93 + 0.07 * dunes);
        if (canyon > 0.78) col = lerp(col, darken(p[2], 0.45F), (float) smooth(0.78, 0.92, canyon));
        if (h < -0.35) col = lerp(col, p[1], (float) smooth(-0.35, -0.5, h));
        double lat = Math.abs(y) + n.fbm(n.d, x * 5, y * 5, z * 5, 2) * 0.05;
        if (lat > 0.9) col = lerp(col, new float[]{0.92F, 0.88F, 0.84F}, (float) smooth(0.9, 0.95, lat));
        set(out, col, 1);
    }

    private static void frozen(Noise n, double x, double y, double z, float[][] p, float[] out) {
        double h = n.fbm(n.a, x * 1.6, y * 1.6, z * 1.6, 6);
        double cracks = n.ridged(n.b, x * 5, y * 5, z * 5, 4);
        float[] col = lerp(p[2], p[0], (float) smooth(-0.4, 0.5, h));
        if (cracks > 0.86) col = lerp(col, darken(p[1], 0.8F), (float) smooth(0.86, 0.96, cracks));
        if (h < -0.32) col = lerp(col, darken(p[1], 0.7F), (float) smooth(-0.32, -0.42, h));
        set(out, shade(col, 0.95 + 0.05 * n.a.getValue(x * 30, y * 30, z * 30)), 1);
    }

    private static void volcanic(Noise n, double x, double y, double z, float[][] p, float[] out) {
        double wx = x + 0.25 * n.fbm(n.c, x * 2, y * 2, z * 2, 3);
        double wz = z + 0.25 * n.fbm(n.d, x * 2, y * 2, z * 2, 3);
        double h = n.fbm(n.a, wx * 1.6, y * 1.6, wz * 1.6, 6);
        double rivers = n.ridged(n.b, wx * 2.6, y * 2.6, wz * 2.6, 5);
        float[] col = lerp(p[0], p[2], (float) smooth(-0.3, 0.5, h));
        col = shade(col, 0.85 + 0.15 * n.a.getValue(x * 26, y * 26, z * 26));
        double lava = Math.max(smooth(0.8, 0.93, rivers), smooth(-0.32, -0.45, h));
        float[] hot = lerp(p[1], new float[]{1.0F, 0.85F, 0.4F}, (float) smooth(0.9, 1.0, rivers));
        set(out, lerp(col, hot, (float) lava), (float) (1 - lava * 0.85));
    }

    private static void barren(Noise n, double x, double y, double z, float[][] p, Crater[] craters, float[] out) {
        double h = n.fbm(n.a, x * 1.4, y * 1.4, z * 1.4, 6);
        float[] col = lerp(p[2], p[0], (float) smooth(-0.5, 0.5, h));
        double maria = n.fbm(n.c, x * 1.1, y * 1.1, z * 1.1, 3);
        if (maria < -0.18) col = lerp(col, p[1], (float) smooth(-0.18, -0.35, maria));
        double light = 1;
        for (Crater c : craters) {
            double dot = x * c.x + y * c.y + z * c.z;
            if (dot < c.cos) continue;
            double d = Math.acos(Math.min(1, dot)) / c.radius;
            if (d < 0.8) light *= 0.84 + 0.1 * d;
            else if (d < 1.1) light *= 1 + 0.14 * (1 - Math.abs(d - 0.95) / 0.15);
        }
        set(out, shade(col, light * (0.93 + 0.07 * n.a.getValue(x * 30, y * 30, z * 30))), 1);
    }

    private static void toxic(Noise n, double x, double y, double z, float[][] p, float[] out) {
        double wx = x + 0.6 * n.fbm(n.b, x * 1.5, y * 1.5, z * 1.5, 4);
        double wy = y + 0.6 * n.fbm(n.c, x * 1.5, y * 1.5, z * 1.5, 4);
        double wz = z + 0.6 * n.fbm(n.d, x * 1.5, y * 1.5, z * 1.5, 4);
        double v = n.fbm(n.a, wx * 2, wy * 2, wz * 2, 6);
        float[] col = lerp(p[1], p[0], (float) smooth(-0.35, 0.4, v));
        if (v > 0.45) col = lerp(col, p[2], (float) smooth(0.45, 0.65, v));
        set(out, col, 1);
    }

    private static void star(Noise n, double x, double y, double z, float[] base, float[] out) {
        double cells = 1 - Math.abs(n.a.getValue(x * 26, y * 26, z * 26));
        double granules = 0.8 + 0.22 * cells * cells + 0.08 * n.fbm(n.b, x * 8, y * 8, z * 8, 3);
        double spots = n.fbm(n.c, x * 3, y * 3, z * 3, 3);
        if (spots > 0.5) granules *= 1 - 0.55 * smooth(0.5, 0.62, spots);
        float[] hot = lerp(base, new float[]{1, 1, 1}, 0.35F);
        set(out, shade(hot, granules), 1);
    }

    /** Banded giant with turbulent edges and one large storm. */
    private static final class GasBands {
        final int bandCount;
        final double stormLat, stormLon, stormSize;
        final double contrast;

        GasBands(long seed, boolean ice) {
            RandomSource r = RandomSource.create(seed ^ 0xBA4DL);
            bandCount = ice ? 4 + r.nextInt(3) : 8 + r.nextInt(7);
            stormLat = (r.nextDouble() - 0.5) * 0.9;
            stormLon = r.nextDouble() * Math.PI * 2;
            stormSize = 0.12 + r.nextDouble() * 0.12;
            contrast = ice ? 0.35 : 1.0;
        }

        void sample(Noise n, double x, double y, double z, double lon, double lat, float[][] p, float[] out) {
            double turbulence = n.fbm(n.a, x * 2.5, y * 9, z * 2.5, 5) * 0.09;
            double t = y + turbulence;
            double band = Math.sin(t * Math.PI * bandCount) * 0.5 + 0.5;
            double band2 = Math.sin(t * Math.PI * bandCount * 2.7 + 1.3) * 0.5 + 0.5;
            float[] col = lerp(p[1], p[0], (float) (band * contrast + (1 - contrast) * 0.5));
            col = lerp(col, p[2], (float) (band2 * 0.35 * contrast));
            col = shade(col, 0.95 + 0.05 * n.b.getValue(x * 8, y * 60, z * 8));

            double dLon = Math.atan2(Math.sin(lon - stormLon), Math.cos(lon - stormLon));
            double ex = dLon / (stormSize * 2.2), ey = (lat - stormLat) / stormSize;
            double d = Math.sqrt(ex * ex + ey * ey);
            if (d < 1.2) {
                double swirl = n.a.getValue(ex * 3 + d * 4, ey * 3, 0.5) * 0.5 + 0.5;
                float[] storm = lerp(darken(p[2], 0.85F), lighten(p[2], 0.2F), (float) swirl);
                col = lerp(col, storm, (float) (smooth(1.2, 0.8, d) * contrast));
            }
            set(out, col, 1);
        }
    }

    // ---------------------------------------------------------------- helpers

    private record Crater(double x, double y, double z, double radius, double cos) {}

    private static Crater[] craters(long seed, int count) {
        RandomSource r = RandomSource.create(seed ^ 0xC2A7E2L);
        Crater[] craters = new Crater[count];
        for (int i = 0; i < count; i++) {
            Vec3 c = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            double radius = 0.015 + Math.pow(r.nextDouble(), 3) * 0.22;
            craters[i] = new Crater(c.x, c.y, c.z, radius, Math.cos(radius * 1.1));
        }
        return craters;
    }

    /** Four independent simplex fields plus fBm helpers. */
    private static final class Noise {
        final SimplexNoise a, b, c, d;

        Noise(long seed) {
            RandomSource r = RandomSource.create(seed);
            a = new SimplexNoise(r);
            b = new SimplexNoise(r);
            c = new SimplexNoise(r);
            d = new SimplexNoise(r);
        }

        double fbm(SimplexNoise n, double x, double y, double z, int octaves) {
            double sum = 0, amp = 0.5, freq = 1, norm = 0;
            for (int i = 0; i < octaves; i++) {
                sum += amp * n.getValue(x * freq, y * freq, z * freq);
                norm += amp;
                amp *= 0.5;
                freq *= 2.03;
            }
            return sum / norm;
        }

        double ridged(SimplexNoise n, double x, double y, double z, int octaves) {
            double sum = 0, amp = 0.5, freq = 1, norm = 0;
            for (int i = 0; i < octaves; i++) {
                double v = 1 - Math.abs(n.getValue(x * freq, y * freq, z * freq));
                sum += amp * v * v;
                norm += amp;
                amp *= 0.5;
                freq *= 2.1;
            }
            return sum / norm;
        }
    }

    private static double smooth(double edge0, double edge1, double v) {
        double t = Mth.clamp((v - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F, (c & 0xFF) / 255F};
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static float[] shade(float[] c, double f) {
        return new float[]{(float) (c[0] * f), (float) (c[1] * f), (float) (c[2] * f)};
    }

    private static float[] lighten(float[] c, float t) {
        return lerp(c, new float[]{1, 1, 1}, t);
    }

    private static float[] darken(float[] c, float f) {
        return shade(c, f);
    }

    private static void set(float[] out, float[] col, float alpha) {
        out[0] = col[0];
        out[1] = col[1];
        out[2] = col[2];
        out[3] = alpha;
    }

    private static int mix(int a, int b, float t) {
        float[] c = lerp(rgb(a), rgb(b), t);
        return ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
    }

    private static int shift(int colour, float hueShift, float satScale) {
        float[] c = rgb(colour);
        float max = Math.max(c[0], Math.max(c[1], c[2])), min = Math.min(c[0], Math.min(c[1], c[2]));
        float h = 0, s = max == 0 ? 0 : (max - min) / max, v = max;
        if (max != min) {
            float dl = max - min;
            if (max == c[0]) h = ((c[1] - c[2]) / dl + (c[1] < c[2] ? 6 : 0)) / 6F;
            else if (max == c[1]) h = ((c[2] - c[0]) / dl + 2) / 6F;
            else h = ((c[0] - c[1]) / dl + 4) / 6F;
        }
        float[] out = hsv(h + hueShift, Mth.clamp(s * satScale, 0, 1), v);
        return ((int) (out[0] * 255) << 16) | ((int) (out[1] * 255) << 8) | (int) (out[2] * 255);
    }

    private static float[] hsv(float h, float s, float v) {
        int rgb = Mth.hsvToRgb(((h % 1) + 1) % 1, s, v);
        return rgb(rgb);
    }

    /** NativeImage stores ABGR. */
    private static int pack(float r, float g, float b, float a) {
        int ri = Mth.clamp((int) (r * 255 + 0.5F), 0, 255);
        int gi = Mth.clamp((int) (g * 255 + 0.5F), 0, 255);
        int bi = Mth.clamp((int) (b * 255 + 0.5F), 0, 255);
        int ai = Mth.clamp((int) (a * 255 + 0.5F), 0, 255);
        return (ai << 24) | (bi << 16) | (gi << 8) | ri;
    }
}
