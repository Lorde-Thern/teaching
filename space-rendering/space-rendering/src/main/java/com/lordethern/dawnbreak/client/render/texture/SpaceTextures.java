package com.lordethern.dawnbreak.client.render.texture;

import com.lordethern.dawnbreak.Dawnbreak;
import com.lordethern.dawnbreak.galaxy.Galaxies;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * GPU textures for the space renderer.
 *
 * <p>Planet textures are generated on background threads and uploaded a couple per frame,
 * so flying into a new system never hitches. Each body has three resolution tiers; the
 * renderer asks for the tier that matches its on-screen size and gets the best one that
 * is ready meanwhile. Least-recently-used textures are freed past {@value #MAX_LOADED}.</p>
 */
public final class SpaceTextures {
    public static final int[][] TIERS = {{256, 128}, {512, 256}, {1024, 512}};
    private static final int MAX_LOADED = 48;
    private static final int MAX_PENDING = 3;
    private static final int UPLOADS_PER_FRAME = 2;

    private enum Kind { SURFACE, CLOUDS }

    private record Key(String id, Kind kind, int tier) {}

    private static final Map<Key, ResourceLocation> LOADED = new LinkedHashMap<>(64, 0.75F, true);
    private static final Map<Key, CompletableFuture<NativeImage>> PENDING = new HashMap<>();
    private static final Map<String, ResourceLocation> RINGS = new HashMap<>();
    private static final Map<String, Integer> PREVIEW_COLOURS = new java.util.concurrent.ConcurrentHashMap<>();
    private static ResourceLocation glow, marker, white;
    private static ResourceLocation nebula;
    private static CompletableFuture<NativeImage> nebulaPending;
    private static long nebulaSeed = Long.MIN_VALUE;
    private static int galaxyRevision = -1;
    private static int counter;

    private SpaceTextures() {}

    /** Call once per frame on the render thread before asking for textures. */
    public static void beginFrame() {
        if (galaxyRevision != Galaxies.clientRevision()) {
            clear();
            galaxyRevision = Galaxies.clientRevision();
        }
        int uploads = 0;
        Iterator<Map.Entry<Key, CompletableFuture<NativeImage>>> it = PENDING.entrySet().iterator();
        while (it.hasNext() && uploads < UPLOADS_PER_FRAME) {
            Map.Entry<Key, CompletableFuture<NativeImage>> entry = it.next();
            if (!entry.getValue().isDone()) continue;
            it.remove();
            NativeImage image = entry.getValue().exceptionally(e -> {
                Dawnbreak.LOGGER.error("Failed to generate texture for {}", entry.getKey().id(), e);
                return null;
            }).join();
            if (image != null) {
                LOADED.put(entry.getKey(), upload(image, true));
                uploads++;
            }
        }
        while (LOADED.size() > MAX_LOADED) {
            Iterator<Map.Entry<Key, ResourceLocation>> eldest = LOADED.entrySet().iterator();
            release(eldest.next().getValue());
            eldest.remove();
        }
        if (nebulaPending != null && nebulaPending.isDone()) {
            NativeImage image = nebulaPending.exceptionally(e -> null).join();
            nebulaPending = null;
            if (image != null) {
                if (nebula != null) release(nebula);
                nebula = upload(image, true);
            }
        }
    }

    @Nullable
    public static ResourceLocation surface(TextureSpec spec, int tier) {
        return get(spec, Kind.SURFACE, tier, () -> TextureGenerator.surface(spec, TIERS[tier][0], TIERS[tier][1]));
    }

    @Nullable
    public static ResourceLocation clouds(TextureSpec spec, int tier) {
        int t = Math.min(tier, 1);
        return get(spec, Kind.CLOUDS, t, () -> TextureGenerator.clouds(spec, TIERS[t][0], TIERS[t][1]));
    }

    /** Far-away colour of a body, cached (the palette maths is not free and runs every frame). */
    public static int previewColor(TextureSpec spec) {
        return PREVIEW_COLOURS.computeIfAbsent(spec.key(), k -> TextureGenerator.previewColor(spec));
    }

    public static ResourceLocation rings(TextureSpec spec) {
        return RINGS.computeIfAbsent(spec.key(), k -> upload(TextureGenerator.rings(spec), true));
    }

    @Nullable
    private static ResourceLocation get(TextureSpec spec, Kind kind, int tier, Supplier<NativeImage> generator) {
        ResourceLocation exact = LOADED.get(new Key(spec.key(), kind, tier));
        if (exact != null) return exact;
        Key key = new Key(spec.key(), kind, tier);
        if (!PENDING.containsKey(key) && PENDING.size() < MAX_PENDING) {
            PENDING.put(key, CompletableFuture.supplyAsync(generator, Util.backgroundExecutor()));
        }
        for (int t = TIERS.length - 1; t >= 0; t--) {
            ResourceLocation fallback = LOADED.get(new Key(spec.key(), kind, t));
            if (fallback != null) return fallback;
        }
        return null;
    }

    @Nullable
    public static ResourceLocation nebula(long seed) {
        if (seed != nebulaSeed) {
            nebulaSeed = seed;
            nebulaPending = CompletableFuture.supplyAsync(() -> TextureGenerator.nebula(seed, 2048, 1024), Util.backgroundExecutor());
        }
        return nebula;
    }

    public static ResourceLocation glow() {
        if (glow == null) glow = upload(TextureGenerator.glow(64), true);
        return glow;
    }

    public static ResourceLocation marker() {
        if (marker == null) marker = upload(TextureGenerator.marker(32), false);
        return marker;
    }

    public static ResourceLocation white() {
        if (white == null) white = upload(TextureGenerator.solid(0xFFFFFF), false);
        return white;
    }

    private static ResourceLocation upload(NativeImage image, boolean smooth) {
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(smooth, false);
        ResourceLocation location = Dawnbreak.id("dynamic/space_" + counter++);
        Minecraft.getInstance().getTextureManager().register(location, texture);
        return location;
    }

    private static void release(ResourceLocation location) {
        Minecraft.getInstance().getTextureManager().release(location);
    }

    /** Frees every planet texture (galaxy changed, or leaving the world). */
    public static void clear() {
        LOADED.values().forEach(SpaceTextures::release);
        LOADED.clear();
        RINGS.values().forEach(SpaceTextures::release);
        RINGS.clear();
        PREVIEW_COLOURS.clear();
        PENDING.values().forEach(f -> f.thenAccept(image -> {
            if (image != null) image.close();
        }));
        PENDING.clear();
    }
}
