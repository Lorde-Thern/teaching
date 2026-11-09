package com.lordethern.dawnbreak.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Shared GPU meshes. Every planet, moon, star and cloud layer reuses the same three
 * unit spheres (picked by on-screen size); position, size, spin and tilt are shader uniforms.
 */
final class Meshes {
    /** (stacks, slices) per level of detail. */
    private static final int[][] SPHERE_LOD = {{12, 24}, {28, 56}, {72, 144}};
    private static final VertexBuffer[] SPHERES = new VertexBuffer[SPHERE_LOD.length];
    private static VertexBuffer ring;
    static final float RING_INNER = 1.25F;
    static final float RING_OUTER = 2.35F;

    private Meshes() {}

    /** Build everything up front: building uses the shared Tesselator, which must be idle. */
    static void ensure() {
        for (int i = 0; i < SPHERES.length; i++) {
            if (SPHERES[i] == null) SPHERES[i] = sphere(SPHERE_LOD[i][0], SPHERE_LOD[i][1]);
        }
        if (ring == null) ring = ring(192);
    }

    static VertexBuffer sphere(int lod) {
        return SPHERES[lod];
    }

    static VertexBuffer ring() {
        return ring;
    }

    private static VertexBuffer sphere(int stacks, int slices) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < stacks; i++) {
            for (int j = 0; j < slices; j++) {
                put(buffer, i, j, stacks, slices);
                put(buffer, i + 1, j, stacks, slices);
                put(buffer, i + 1, j + 1, stacks, slices);
                put(buffer, i, j, stacks, slices);
                put(buffer, i + 1, j + 1, stacks, slices);
                put(buffer, i, j + 1, stacks, slices);
            }
        }
        return upload(buffer);
    }

    /** Same mapping as the texture generator: row 0 is the north pole, u wraps once around. */
    private static void put(BufferBuilder buffer, int stack, int slice, int stacks, int slices) {
        double lat = Math.PI / 2 - Math.PI * stack / stacks;
        double lon = Math.PI * 2 * slice / slices;
        float x = (float) (Math.cos(lat) * Math.cos(lon));
        float y = (float) Math.sin(lat);
        float z = (float) (Math.cos(lat) * Math.sin(lon));
        buffer.vertex(x, y, z).uv(slice / (float) slices, stack / (float) stacks).color(1F, 1F, 1F, 1F).endVertex();
    }

    /** Flat annulus in the XZ plane, in planet radii; u runs from inner to outer edge. */
    private static VertexBuffer ring(int segments) {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            buffer.vertex(c0 * RING_INNER, 0, s0 * RING_INNER).uv(0, 0.5F).color(1F, 1F, 1F, 1F).endVertex();
            buffer.vertex(c0 * RING_OUTER, 0, s0 * RING_OUTER).uv(1, 0.5F).color(1F, 1F, 1F, 1F).endVertex();
            buffer.vertex(c1 * RING_OUTER, 0, s1 * RING_OUTER).uv(1, 0.5F).color(1F, 1F, 1F, 1F).endVertex();
            buffer.vertex(c1 * RING_INNER, 0, s1 * RING_INNER).uv(0, 0.5F).color(1F, 1F, 1F, 1F).endVertex();
        }
        return upload(buffer);
    }

    private static VertexBuffer upload(BufferBuilder buffer) {
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(buffer.end());
        VertexBuffer.unbind();
        return vbo;
    }
}
