package com.lordethern.dawnbreak.client.render.texture;

import com.lordethern.dawnbreak.galaxy.Body;
import com.lordethern.dawnbreak.galaxy.BodyType;
import com.lordethern.dawnbreak.galaxy.Palette;
import com.lordethern.dawnbreak.galaxy.Star;

/** Everything needed to generate one body's textures, detached from game objects so it can go to a worker thread. */
public record TextureSpec(String key, BodyType type, long seed, Palette palette, boolean vary, int starColor, boolean star) {
    public static TextureSpec of(Body body) {
        return new TextureSpec(body.id().toString(), body.type(), body.seed(), body.palette(), !body.translatable(), 0, false);
    }

    public static TextureSpec of(String key, Star star, long seed) {
        return new TextureSpec(key, BodyType.BARREN, seed, Palette.NONE, false, star.color(), true);
    }
}
