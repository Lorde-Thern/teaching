package com.lordethern.dawnbreak.client.render;

import com.lordethern.dawnbreak.client.Waypoint;
import com.lordethern.dawnbreak.config.DawnbreakConfig;
import com.lordethern.dawnbreak.client.render.texture.SpaceTextures;
import com.lordethern.dawnbreak.client.render.texture.TextureGenerator;
import com.lordethern.dawnbreak.client.render.texture.TextureSpec;
import com.lordethern.dawnbreak.galaxy.Bodies;
import com.lordethern.dawnbreak.galaxy.Body;
import com.lordethern.dawnbreak.galaxy.Galaxies;
import com.lordethern.dawnbreak.galaxy.Galaxy;
import com.lordethern.dawnbreak.galaxy.Star;
import com.lordethern.dawnbreak.galaxy.StarSystem;
import com.lordethern.dawnbreak.space.ModDimensions;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Draws space: nebula backdrop, background stars, every nearby star system as a real star
 * in the sky (with parallax as you fly), and full detail — textured, lit, spinning planets,
 * clouds, rings, atmospheres and suns — for the systems you are in or near.
 *
 * <p><b>Scale.</b> Each object is drawn at a compressed "sky distance" (logarithmic in its
 * true distance) and scaled by the same factor, so its apparent size is exact and nearer
 * objects still sort in front of farther ones.</p>
 *
 * <p><b>Level of detail.</b> On-screen size picks the mesh (3 levels) and texture tier
 * (3 resolutions, generated in the background). Anything under ~1.5 px becomes a glow dot;
 * systems beyond the detail range are a single star glow each.</p>
 */
public final class SpaceRenderer {
    private static final double SHELL = 100.0;
    /** Systems this close (beyond their own reach) get per-body rendering: spheres or dots. */
    private static final double DETAIL_RANGE = 16000;
    private static final float AMBIENT = 0.06F;
    private static final BufferBuilder GLOWS = new BufferBuilder(1 << 18);

    private static VertexBuffer backgroundStars;
    private static long backgroundSeed = Long.MIN_VALUE;

    // Cached "which systems get detail" — refreshed when the viewpoint moves, not every frame.
    private static final List<StarSystem> DETAILED = new ArrayList<>();
    private static Vec3 detailedFrom;
    private static int detailedRevision = -1;

    // Last frame's camera, for projecting labels in the HUD.
    private static final Matrix4f LAST_VIEW = new Matrix4f();
    private static final Matrix4f LAST_PROJECTION = new Matrix4f();
    private static final List<Label> LABELS = new ArrayList<>();
    private static long lastFrameNanos;

    /** A name to draw next to something in the sky. {@code rel} is its true offset from the viewpoint. */
    public record Label(Component text, Vec3 rel, int colour, double distance) {}

    private SpaceRenderer() {}

    public static void render(ClientLevel level, float partialTick, PoseStack poseStack, Matrix4f projection, Camera camera) {
        FogRenderer.setupNoFog();
        if (!DawnbreakShaders.ready()) return;
        Meshes.ensure();
        SpaceTextures.beginFrame();

        Galaxy galaxy = Galaxies.get(true);
        double time = Galaxy.time(level, partialTick);
        Matrix4f view = new Matrix4f(poseStack.last().pose());
        Vec3 cam = camera.getPosition();

        Optional<Body> here = Optional.empty();
        Vec3 viewpoint = null;
        if (ModDimensions.isSpace(level)) {
            viewpoint = cam;
        } else {
            here = Bodies.at(level, cam.x, cam.z);
            Optional<Body> body = here;
            viewpoint = here.flatMap(b -> galaxy.positionOf(b, time)).map(p -> p.add(0, body.get().radius() + 2, 0)).orElse(null);
        }

        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1, 1, 1, 1);

        Minecraft mc = Minecraft.getInstance();
        double pixelScale = mc.getWindow().getHeight() / 2.0 / Math.tan(Math.toRadians(mc.options.fov().get()) / 2);
        LAST_VIEW.set(view);
        LAST_PROJECTION.set(projection);
        LABELS.clear();
        lastFrameNanos = System.nanoTime();

        drawSkyDome(view, projection, galaxy.seed());
        drawBackgroundStars(view, projection, galaxy.seed());
        if (viewpoint != null) {
            FarField.update(galaxy, Galaxies.clientRevision(), viewpoint,
                    DawnbreakConfig.CLIENT.starRange.get(), DawnbreakConfig.CLIENT.showOrbits.get());
            FarField.draw(view, projection, viewpoint, pixelScale);
            drawGalaxy(galaxy, time, viewpoint, here.orElse(null), view, projection, pixelScale);
        }

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    // ------------------------------------------------------------------ backdrop

    private static void drawSkyDome(Matrix4f view, Matrix4f projection, long seed) {
        ResourceLocation nebula = SpaceTextures.nebula(seed);
        if (nebula == null) return;
        ShaderInstance shader = DawnbreakShaders.planet;
        setBodyUniforms(shader, Vec3.ZERO, 60, 0, 0, new Vec3(0, 1, 0), 0, 0, 4, 1, 1);
        draw(shader, Meshes.sphere(1), nebula, view, projection);
    }

    private static void drawBackgroundStars(Matrix4f view, Matrix4f projection, long seed) {
        if (backgroundStars == null || backgroundSeed != seed) {
            if (backgroundStars != null) backgroundStars.close();
            backgroundStars = buildBackgroundStars(seed);
            backgroundSeed = seed;
        }
        RenderSystem.setShaderTexture(0, SpaceTextures.glow());
        backgroundStars.bind();
        backgroundStars.drawWithShader(view, projection, DawnbreakShaders.glow);
        VertexBuffer.unbind();
    }

    /** Thousands of faint stars, about half of them crowded into the galactic band. */
    private static VertexBuffer buildBackgroundStars(long seed) {
        RandomSource r = RandomSource.create(seed ^ 0x57A25L);
        Vec3 plane = TextureGenerator.galacticNormal(seed);
        int[] temperatures = {0x9BB0FF, 0xCAD7FF, 0xF8F4FF, 0xFFF4E0, 0xFFE0B0, 0xFFC488, 0xFFA070};
        BufferBuilder buffer = GLOWS;
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < 14000; i++) {
            Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            if (i % 2 == 0) {
                // Pull toward the galactic plane.
                double off = dir.dot(plane);
                dir = dir.subtract(plane.scale(off * (0.85 + r.nextDouble() * 0.15))).normalize();
            }
            double size = 0.07 + Math.pow(r.nextDouble(), 7) * 0.55;
            int colour = temperatures[r.nextInt(temperatures.length)];
            float alpha = 0.3F + r.nextFloat() * 0.7F;
            quad(buffer, null, dir.scale(SHELL), dir, size, colour, alpha);
        }
        VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(buffer.end());
        VertexBuffer.unbind();
        return vbo;
    }

    // ------------------------------------------------------------------ galaxy

    private record Job(double distance, Runnable draw) {}

    private static void drawGalaxy(Galaxy galaxy, double time, Vec3 viewpoint, @Nullable Body here, Matrix4f view, Matrix4f projection, double pixelScale) {
        List<Job> opaque = new ArrayList<>();
        List<Job> translucent = new ArrayList<>();
        List<Job> halos = new ArrayList<>();

        BufferBuilder glows = GLOWS;
        glows.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        List<StarSystem> detailed = detailedSystems(galaxy, viewpoint);
        StarSystem inside = containing(detailed, viewpoint);

        for (StarSystem system : detailed) {
            boolean insideThis = system == inside;
            Vec3 primary = system.starPosition(0, time);
            for (int i = 0; i < system.stars().size(); i++) {
                Star star = system.stars().get(i);
                Vec3 rel = system.starPosition(i, time).subtract(viewpoint);
                double dist = rel.length();
                if (dist < star.radius() * 1.02) continue;
                Vec3 dir = rel.scale(1 / dist);
                if (i == 0) LABELS.add(new Label(insideThis ? star.displayName(system.displayName()) : system.displayName(), rel, star.color(), dist));
                // Corona and wide bloom, behind the disc.
                quad(glows, view, dir.scale(SHELL), dir, Math.min(0.6, star.radius() * 3.4 / dist) * SHELL, star.color(), 0.9F);
                quad(glows, view, dir.scale(SHELL), dir, Math.min(1.2, star.radius() * 9 / dist) * SHELL, star.color(), 0.22F);
                double px = pixels(star.radius(), dist, pixelScale);
                if (px < 1.2) continue;
                TextureSpec spec = TextureSpec.of(system.id() + "/star" + i, star, system.id().hashCode() * 31L + i);
                ResourceLocation loaded = SpaceTextures.surface(spec, tier(px));
                ResourceLocation tex = texture(loaded);
                int tint = loaded == null ? star.color() : 0xFFFFFF;
                float spin = (float) (time / 6000.0);
                opaque.add(new Job(dist, () -> {
                    setBodyUniforms(DawnbreakShaders.planet, rel, star.radius(), spin, 0, dir, 0, 0, 2, 1, 1);
                    drawTinted(DawnbreakShaders.planet, Meshes.sphere(mesh(px)), tex, tint, view, projection);
                }));
            }

            for (Body body : system.bodies()) {
                if (here != null && here.id().equals(body.id())) continue;
                Vec3 pos = system.bodyPosition(body, time);
                Vec3 rel = pos.subtract(viewpoint);
                double dist = rel.length();
                if (dist < body.radius() * 1.02) continue;
                Vec3 light = primary.subtract(pos).normalize();
                double px = pixels(body.radius(), dist, pixelScale);
                TextureSpec spec = TextureSpec.of(body);
                Vec3 dir = rel.scale(1 / dist);
                int preview = SpaceTextures.previewColor(spec);
                if (insideThis) LABELS.add(new Label(body.displayName(), rel, preview, dist));

                if (px < 1.5) {
                    // Phase-lit dot, never smaller than ~1.3 px, so planets are findable across a system.
                    float lit = (float) (0.35 + 0.65 * Math.max(0, light.dot(dir.scale(-1))));
                    double size = Math.max(body.radius() * 1.8 / dist, 1.3 / pixelScale) * SHELL;
                    quad(glows, view, dir.scale(SHELL), dir, size, preview, Math.min(1F, lit * (float) Math.max(0.55, px)));
                    continue;
                }

                int tier = tier(px);
                ResourceLocation surface = SpaceTextures.surface(spec, tier);
                ResourceLocation tex = texture(surface);
                int tint = surface == null ? preview : 0xFFFFFF;
                float spin = body.rotationTicks() > 0 ? (float) (Math.PI * 2 * time / body.rotationTicks()) : 0;
                int atmo = body.atmosphereColor().orElse(0);
                float atmoStrength = body.atmosphereColor().isPresent() ? 0.9F : 0F;
                VertexBuffer sphere = Meshes.sphere(mesh(px));

                opaque.add(new Job(dist, () -> {
                    setBodyUniforms(DawnbreakShaders.planet, rel, body.radius(), spin, body.tilt(), light, atmo, atmoStrength, 0, AMBIENT, 1);
                    drawTinted(DawnbreakShaders.planet, sphere, tex, tint, view, projection);
                }));

                if (body.type().hasClouds() && px > 6) {
                    ResourceLocation clouds = SpaceTextures.clouds(spec, tier);
                    if (clouds != null) {
                        translucent.add(new Job(dist, () -> {
                            setBodyUniforms(DawnbreakShaders.planetTranslucent, rel, body.radius() * 1.012, spin * 1.25F + 0.7F, body.tilt(), light, 0, 0, 1, AMBIENT, 1);
                            draw(DawnbreakShaders.planetTranslucent, sphere, clouds, view, projection);
                        }));
                    }
                }
                if (body.rings()) {
                    ResourceLocation rings = SpaceTextures.rings(spec);
                    translucent.add(new Job(dist, () -> {
                        setBodyUniforms(DawnbreakShaders.planetTranslucent, rel, body.radius(), 0, body.tilt(), light, 0, 0, 3, 0.08F, 1);
                        draw(DawnbreakShaders.planetTranslucent, Meshes.ring(), rings, view, projection);
                    }));
                }
                if (atmoStrength > 0 && px > 3) {
                    float shell = 1.13F;
                    halos.add(new Job(dist, () -> {
                        setBodyUniforms(DawnbreakShaders.atmosphere, rel, body.radius() * shell, spin, body.tilt(), light, atmo, 1.0F, 0, 0, shell);
                        draw(DawnbreakShaders.atmosphere, sphere, SpaceTextures.white(), view, projection);
                    }));
                }
            }
        }

        // Glows first (no depth), then solid bodies with depth so they cover glows and each other.
        RenderSystem.setShaderTexture(0, SpaceTextures.glow());
        RenderSystem.setShader(() -> DawnbreakShaders.glow);
        BufferUploader.drawWithShader(glows.end());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        opaque.sort(Comparator.comparingDouble(Job::distance).reversed());
        opaque.forEach(job -> job.draw().run());

        RenderSystem.depthMask(false);
        translucent.sort(Comparator.comparingDouble(Job::distance).reversed());
        translucent.forEach(job -> job.draw().run());
        halos.sort(Comparator.comparingDouble(Job::distance).reversed());
        halos.forEach(job -> job.draw().run());
        RenderSystem.disableDepthTest();

        drawWaypoint(galaxy, time, viewpoint, view);
    }

    private static void drawWaypoint(Galaxy galaxy, double time, Vec3 viewpoint, Matrix4f view) {
        Optional<Vec3> target = Waypoint.position(galaxy, time);
        if (target.isEmpty()) return;
        Vec3 rel = target.get().subtract(viewpoint);
        double dist = rel.length();
        if (dist < 1) return;
        Vec3 dir = rel.scale(1 / dist);
        float pulse = 0.75F + 0.25F * (float) Math.sin(System.currentTimeMillis() / 250.0);
        BufferBuilder buffer = GLOWS;
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        quad(buffer, view, dir.scale(SHELL * 0.5), dir, 1.3, 0xFFD54A, pulse);
        RenderSystem.setShaderTexture(0, SpaceTextures.marker());
        RenderSystem.setShader(() -> DawnbreakShaders.glow);
        BufferUploader.drawWithShader(buffer.end());
    }

    // ------------------------------------------------------------------ caches and labels

    private static List<StarSystem> detailedSystems(Galaxy galaxy, Vec3 viewpoint) {
        if (detailedFrom == null || detailedRevision != Galaxies.clientRevision() || detailedFrom.distanceToSqr(viewpoint) > 512 * 512) {
            DETAILED.clear();
            int cx = Galaxy.cellOf(viewpoint.x), cz = Galaxy.cellOf(viewpoint.z);
            int reach = (int) Math.ceil(DETAIL_RANGE / Galaxy.CELL) + 1;
            galaxy.forEachSystem(cx - reach, cx + reach, cz - reach, cz + reach, system -> {
                if (system.position().distanceTo(viewpoint) < system.extent() + DETAIL_RANGE + 1024) DETAILED.add(system);
            });
            detailedFrom = viewpoint;
            detailedRevision = Galaxies.clientRevision();
        }
        return DETAILED;
    }

    /** Drop cached geometry (leaving the world). */
    public static void reset() {
        FarField.reset();
        DETAILED.clear();
        detailedFrom = null;
    }

    /** Same rule as {@link Galaxy#containing}, but over the cached list instead of fresh cell lookups. */
    @Nullable
    private static StarSystem containing(List<StarSystem> systems, Vec3 pos) {
        StarSystem best = null;
        double bestDist = Double.MAX_VALUE;
        for (StarSystem system : systems) {
            double dx = system.position().x - pos.x, dz = system.position().z - pos.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < system.extent() + 600 && dist < bestDist) {
                best = system;
                bestDist = dist;
            }
        }
        return best;
    }

    /** Labels from the last sky frame, or nothing if the space sky was not drawn recently. */
    public static List<Label> labels() {
        return System.nanoTime() - lastFrameNanos < 200_000_000L ? LABELS : List.of();
    }

    /** Projects a true sky offset to GUI coordinates using last frame's camera; null if behind the camera. */
    @Nullable
    public static float[] project(Vec3 rel, int guiWidth, int guiHeight) {
        double dist = rel.length();
        if (dist < 1.0E-6) return null;
        double k = skyDistance(dist) / dist;
        org.joml.Vector4f v = new org.joml.Vector4f((float) (rel.x * k), (float) (rel.y * k), (float) (rel.z * k), 1F);
        LAST_VIEW.transform(v);
        LAST_PROJECTION.transform(v);
        if (v.w <= 0) return null;
        float nx = v.x / v.w, ny = v.y / v.w;
        if (nx < -1.05F || nx > 1.05F || ny < -1.05F || ny > 1.05F) return null;
        return new float[]{(nx * 0.5F + 0.5F) * guiWidth, (0.5F - ny * 0.5F) * guiHeight};
    }

    // ------------------------------------------------------------------ helpers

    /** Compressed draw distance: monotonic, so depth order survives, and always inside the far plane. */
    private static double skyDistance(double dist) {
        return 10 + 8 * Math.log1p(dist / 20);
    }

    private static double pixels(double radius, double dist, double pixelScale) {
        double angle = Math.asin(Math.min(0.999, radius / dist));
        return Math.tan(angle) * pixelScale;
    }

    private static int tier(double px) {
        return px < 48 ? 0 : px < 200 ? 1 : 2;
    }

    private static int mesh(double px) {
        return px < 20 ? 0 : px < 110 ? 1 : 2;
    }

    private static ResourceLocation texture(@Nullable ResourceLocation tex) {
        return tex != null ? tex : SpaceTextures.white();
    }

    private static void setBodyUniforms(ShaderInstance shader, Vec3 rel, double radius, float spin, float tilt, Vec3 light,
                                        int atmoColor, float atmoStrength, float mode, float ambient, float shellScale) {
        double dist = rel.length();
        double k = dist < 1.0E-6 ? 1 : skyDistance(dist) / dist;
        shader.safeGetUniform("BodyCenter").set((float) (rel.x * k), (float) (rel.y * k), (float) (rel.z * k));
        shader.safeGetUniform("BodyScale").set((float) (radius * k));
        shader.safeGetUniform("Spin").set(spin);
        shader.safeGetUniform("Tilt").set(tilt);
        shader.safeGetUniform("LightDir").set((float) light.x, (float) light.y, (float) light.z);
        shader.safeGetUniform("AtmoColor").set(((atmoColor >> 16) & 0xFF) / 255F, ((atmoColor >> 8) & 0xFF) / 255F, (atmoColor & 0xFF) / 255F);
        shader.safeGetUniform("AtmoStrength").set(atmoStrength);
        shader.safeGetUniform("Mode").set(mode);
        shader.safeGetUniform("Ambient").set(ambient);
        shader.safeGetUniform("ShellScale").set(shellScale);
    }

    private static void draw(ShaderInstance shader, VertexBuffer mesh, ResourceLocation texture, Matrix4f view, Matrix4f projection) {
        RenderSystem.setShaderTexture(0, texture);
        mesh.bind();
        mesh.drawWithShader(view, projection, shader);
        VertexBuffer.unbind();
    }

    private static void drawTinted(ShaderInstance shader, VertexBuffer mesh, ResourceLocation texture, int tint, Matrix4f view, Matrix4f projection) {
        RenderSystem.setShaderColor(((tint >> 16) & 0xFF) / 255F, ((tint >> 8) & 0xFF) / 255F, (tint & 0xFF) / 255F, 1F);
        draw(shader, mesh, texture, view, projection);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** Camera-facing quad; positions are transformed on the CPU when {@code view} is given. */
    private static void quad(BufferBuilder buffer, @Nullable Matrix4f view, Vec3 centre, Vec3 dir, double halfSize, int colour, float alpha) {
        Vec3 up = Math.abs(dir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = up.cross(dir).normalize().scale(halfSize);
        Vec3 v = dir.cross(u).normalize().scale(halfSize);
        float r = ((colour >> 16) & 0xFF) / 255F, g = ((colour >> 8) & 0xFF) / 255F, b = (colour & 0xFF) / 255F;
        vertex(buffer, view, centre.subtract(u).subtract(v)).uv(0, 0).color(r, g, b, alpha).endVertex();
        vertex(buffer, view, centre.add(u).subtract(v)).uv(1, 0).color(r, g, b, alpha).endVertex();
        vertex(buffer, view, centre.add(u).add(v)).uv(1, 1).color(r, g, b, alpha).endVertex();
        vertex(buffer, view, centre.subtract(u).add(v)).uv(0, 1).color(r, g, b, alpha).endVertex();
    }

    private static com.mojang.blaze3d.vertex.VertexConsumer vertex(BufferBuilder buffer, @Nullable Matrix4f view, Vec3 p) {
        return view == null
                ? buffer.vertex(p.x, p.y, p.z)
                : buffer.vertex(view, (float) p.x, (float) p.y, (float) p.z);
    }
}
