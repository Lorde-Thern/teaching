package com.lordethern.dawnbreak.client.render;

import com.lordethern.dawnbreak.Dawnbreak;
import com.lordethern.dawnbreak.client.render.texture.SpaceTextures;
import com.lordethern.dawnbreak.client.render.texture.TextureGenerator;
import com.lordethern.dawnbreak.client.render.texture.TextureSpec;
import com.lordethern.dawnbreak.galaxy.AsteroidBelt;
import com.lordethern.dawnbreak.galaxy.Body;
import com.lordethern.dawnbreak.galaxy.Galaxy;
import com.lordethern.dawnbreak.galaxy.Orbit;
import com.lordethern.dawnbreak.galaxy.Star;
import com.lordethern.dawnbreak.galaxy.StarSystem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Everything too far away to need per-frame work: the stars of every system within
 * {@code starRange} cells, asteroid-belt dust and orbit lines of nearby systems.
 *
 * <p>The geometry is built on a worker thread whenever you cross into a new galaxy cell,
 * uploaded once, and drawn as two static VBOs. The vertex shaders do the per-vertex
 * distance compression against the live camera, so the field stays exact while you fly
 * without being rebuilt.</p>
 */
final class FarField {
    private static final int BELT_CELLS = 2;
    private static final int ORBIT_CELLS = 1;
    private static final BufferBuilder BUFFER = new BufferBuilder(1 << 20);

    private static VertexBuffer points;
    private static VertexBuffer lines;
    private static boolean hasLines;
    private static Vec3 origin = Vec3.ZERO;
    private static long builtKey = Long.MIN_VALUE;
    private static long requestedKey = Long.MIN_VALUE;
    private static CompletableFuture<Data> pending;

    private FarField() {}

    /** Plain arrays so the worker never touches GL or shared builders. */
    private static final class Data {
        final Vec3 origin;
        float[] points = new float[4096 * 5];
        int[] pointColours = new int[4096];
        int pointCount;
        float[] lines = new float[4096 * 6];
        int[] lineColours = new int[4096];
        int lineCount;

        Data(Vec3 origin) {
            this.origin = origin;
        }

        void point(Vec3 p, double radius, int colour, float alpha) {
            if (pointCount == pointColours.length) {
                points = Arrays.copyOf(points, points.length * 2);
                pointColours = Arrays.copyOf(pointColours, pointColours.length * 2);
            }
            int i = pointCount * 5;
            points[i] = (float) (p.x - origin.x);
            points[i + 1] = (float) (p.y - origin.y);
            points[i + 2] = (float) (p.z - origin.z);
            points[i + 3] = (float) radius;
            points[i + 4] = alpha;
            pointColours[pointCount++] = colour;
        }

        void line(Vec3 a, Vec3 b, int colour) {
            if (lineCount == lineColours.length) {
                lines = Arrays.copyOf(lines, lines.length * 2);
                lineColours = Arrays.copyOf(lineColours, lineColours.length * 2);
            }
            int i = lineCount * 6;
            lines[i] = (float) (a.x - origin.x);
            lines[i + 1] = (float) (a.y - origin.y);
            lines[i + 2] = (float) (a.z - origin.z);
            lines[i + 3] = (float) (b.x - origin.x);
            lines[i + 4] = (float) (b.y - origin.y);
            lines[i + 5] = (float) (b.z - origin.z);
            lineColours[lineCount++] = colour;
        }
    }

    static void update(Galaxy galaxy, int revision, Vec3 viewpoint, int range, boolean orbits) {
        int cx = Galaxy.cellOf(viewpoint.x), cz = Galaxy.cellOf(viewpoint.z);
        long key = ((((long) cx * 31 + cz) * 31 + range) * 31 + revision) * 2 + (orbits ? 1 : 0);

        if (pending != null && pending.isDone()) {
            Data data = pending.exceptionally(e -> {
                Dawnbreak.LOGGER.error("Failed to build the space far field", e);
                return null;
            }).join();
            pending = null;
            if (data != null) {
                upload(data);
                builtKey = requestedKey;
            }
        }
        if (key != builtKey && key != requestedKey && pending == null) {
            requestedKey = key;
            pending = CompletableFuture.supplyAsync(() -> build(galaxy, cx, cz, range, orbits), Util.backgroundExecutor());
        }
    }

    static void draw(Matrix4f view, Matrix4f projection, Vec3 viewpoint, double pixelScale) {
        if (points == null) return;
        float ox = (float) (origin.x - viewpoint.x), oy = (float) (origin.y - viewpoint.y), oz = (float) (origin.z - viewpoint.z);

        if (hasLines) {
            DawnbreakShaders.skyLine.safeGetUniform("Offset").set(ox, oy, oz);
            lines.bind();
            lines.drawWithShader(view, projection, DawnbreakShaders.skyLine);
            VertexBuffer.unbind();
        }

        DawnbreakShaders.skyPoint.safeGetUniform("Offset").set(ox, oy, oz);
        DawnbreakShaders.skyPoint.safeGetUniform("MinAngle").set((float) (1.3 / pixelScale));
        DawnbreakShaders.skyPoint.safeGetUniform("MaxAngle").set(0.025F);
        RenderSystem.setShaderTexture(0, SpaceTextures.glow());
        points.bind();
        points.drawWithShader(view, projection, DawnbreakShaders.skyPoint);
        VertexBuffer.unbind();
    }

    private static Data build(Galaxy galaxy, int cx, int cz, int range, boolean orbits) {
        Data data = new Data(new Vec3((cx + 0.5) * Galaxy.CELL, 512, (cz + 0.5) * Galaxy.CELL));
        galaxy.forEachSystem(cx - range, cx + range, cz - range, cz + range, system -> {
            for (int i = 0; i < system.stars().size(); i++) {
                Star star = system.stars().get(i);
                data.point(system.starPosition(i, 0), star.radius() * 9, star.color(), 1.0F);
            }
            int dcx = Math.abs(Galaxy.cellOf(system.position().x) - cx), dcz = Math.abs(Galaxy.cellOf(system.position().z) - cz);
            if (dcx <= BELT_CELLS && dcz <= BELT_CELLS && system.belt().isPresent()) belt(data, system, system.belt().get());
            if (orbits && dcx <= ORBIT_CELLS && dcz <= ORBIT_CELLS) orbits(data, system);
        });
        return data;
    }

    /** Dust of small glows along the belt: visible from across the system, unlike block asteroids. */
    private static void belt(Data data, StarSystem system, AsteroidBelt belt) {
        RandomSource r = RandomSource.create(system.id().hashCode() * 7919L);
        int count = (int) Math.min(3000, belt.radius() * Math.PI * 2 / 9);
        for (int i = 0; i < count; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double radius = belt.radius() + r.nextGaussian() * belt.width() * 0.35;
            Vec3 p = system.position().add(Math.cos(angle) * radius, r.nextGaussian() * 16, Math.sin(angle) * radius);
            int shade = 0x70 + r.nextInt(0x40);
            data.point(p, 14 + r.nextDouble() * 26, (shade << 16) | ((shade - 10) << 8) | (shade - 24), 0.5F);
        }
    }

    private static void orbits(Data data, StarSystem system) {
        for (Body body : system.bodies()) {
            if (body.parent().isPresent()) continue;
            int colour = TextureGenerator.previewColor(TextureSpec.of(body));
            int segments = (int) Math.max(96, Math.min(360, body.orbit().radius() / 12));
            Vec3 prev = null;
            for (int i = 0; i <= segments; i++) {
                Vec3 p = system.position().add(new Orbit(body.orbit().radius(), Math.PI * 2 * i / segments, 0, body.orbit().inclination()).offset(0));
                if (prev != null) data.line(prev, p, colour);
                prev = p;
            }
        }
    }

    private static void upload(Data data) {
        origin = data.origin;
        BufferBuilder buffer = BUFFER;
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[] p = data.points;
        for (int n = 0; n < data.pointCount; n++) {
            int i = n * 5;
            int c = data.pointColours[n];
            float r = ((c >> 16) & 0xFF) / 255F, g = ((c >> 8) & 0xFF) / 255F, b = (c & 0xFF) / 255F, a = p[i + 4];
            float size = p[i + 3];
            buffer.vertex(p[i], p[i + 1], p[i + 2]).uv(-size, -size).color(r, g, b, a).endVertex();
            buffer.vertex(p[i], p[i + 1], p[i + 2]).uv(size, -size).color(r, g, b, a).endVertex();
            buffer.vertex(p[i], p[i + 1], p[i + 2]).uv(size, size).color(r, g, b, a).endVertex();
            buffer.vertex(p[i], p[i + 1], p[i + 2]).uv(-size, size).color(r, g, b, a).endVertex();
        }
        if (points == null) points = new VertexBuffer(VertexBuffer.Usage.STATIC);
        points.bind();
        points.upload(buffer.end());
        VertexBuffer.unbind();

        hasLines = data.lineCount > 0;
        if (!hasLines) return;
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        float[] l = data.lines;
        for (int n = 0; n < data.lineCount; n++) {
            int i = n * 6;
            int c = data.lineColours[n];
            float r = ((c >> 16) & 0xFF) / 255F * 0.55F, g = ((c >> 8) & 0xFF) / 255F * 0.55F, b = (c & 0xFF) / 255F * 0.55F;
            buffer.vertex(l[i], l[i + 1], l[i + 2]).color(r, g, b, 0.45F).endVertex();
            buffer.vertex(l[i + 3], l[i + 4], l[i + 5]).color(r, g, b, 0.45F).endVertex();
        }
        if (lines == null) lines = new VertexBuffer(VertexBuffer.Usage.STATIC);
        lines.bind();
        lines.upload(buffer.end());
        VertexBuffer.unbind();
    }

    /** Forget everything (galaxy replaced or leaving the world). */
    static void reset() {
        builtKey = Long.MIN_VALUE;
        requestedKey = Long.MIN_VALUE;
    }
}
