# Space rendering: DMZ Dawnbreak

Every file that draws space in DMZ: Dawnbreak, copied from the mod's source (branch
`claude/inspiring-wright-jyheh4`, commit 8200a53) with their real paths, so they drop back into the
project as they are. They need the rest of the mod to compile; the galaxy model they read
(`galaxy/`) and the config are not included.

## How it fits together

1. A dimension type with `"effects": "dawnbreak:space"` (`data/.../dimension_type/space.json` and
   `airless.json`) gets a custom sky.
2. `DawnbreakClient` registers that effects id to `SpaceSkyEffects`.
3. `SpaceSkyEffects.renderSky` replaces vanilla's sky with `SpaceRenderer.render`.
4. `SpaceRenderer` draws the nebula and background stars, asks `FarField` to draw every distant
   system, then draws the nearby systems in full with the shaders in `shaders/core`, using
   textures from `SpaceTextures`.
5. The HUD and map read from the same galaxy: `SpaceLabels` (names in the sky, using the camera
   `SpaceRenderer` saved), `NavigationOverlay` (top-left readout) and `GalaxyMapScreen`.

## Files

| File | What it does |
|---|---|
| `client/SpaceSkyEffects.java` | The dimension's sky effects: turns off fog and clouds, and hands sky drawing to `SpaceRenderer`. |
| `client/DawnbreakClient.java` | Client setup: registers the `dawnbreak:space` sky effects, key bindings and HUD overlays. |
| `client/render/SpaceRenderer.java` | The main renderer: sky dome, background stars, then stars, planets, clouds, rings and atmospheres for nearby systems. Compressed "sky distance", level of detail, back-to-front job lists, waypoint marker, sky labels. |
| `client/render/FarField.java` | Every star within range, plus asteroid belts and orbit lines, built on a worker thread when you cross a galaxy cell and drawn from two static buffers. |
| `client/render/Meshes.java` | The sphere meshes (three levels of detail) and the ring mesh, built once. |
| `client/render/DawnbreakShaders.java` | Loads the six core shaders and reports when they're ready. |
| `client/render/texture/SpaceTextures.java` | Planet, cloud, ring, star, nebula and glow textures: generated in the background in three resolutions, cached, and freed when you leave. |
| `client/render/texture/TextureGenerator.java` | The procedural painting itself: palettes per planet type, noise terrain, gas-giant bands and storms, the nebula, the glow and waypoint sprites. |
| `client/render/texture/TextureSpec.java` | Everything needed to paint one body, detached from game objects so it can go to a worker thread. |
| `client/SpaceLabels.java` | Names and distances next to bodies in the sky, closest first, skipping overlaps. |
| `client/NavigationOverlay.java` | Top-left HUD: system, speed, waypoint and ETA, nearest bodies with steering arrows; on a planet its name, gravity and air warning. |
| `client/Waypoint.java` | The navigation target chosen on the map. |
| `client/GalaxyMapScreen.java` | The zoomable top-down galaxy map. |
| `shaders/core/planet.*` | One shader with five modes: planet surfaces, clouds, stars, rings and the sky dome. The vertex shader spins and tilts the sphere. The fragment shader does soft lighting with glowing lava and city lights on the night side, the atmosphere rim, star limb darkening, and the planet's shadow across its rings. `planet_translucent.json` uses it with blending for clouds and rings. |
| `shaders/core/atmosphere.*` | The glowing halo around planets with air, with a sunset tint along the day/night line (its own fragment shader on the planet vertex shader). |
| `shaders/core/glow.*` | Camera-facing glow sprites: star coronas and distant dots. |
| `shaders/core/skypoint.*` | Far-field star points, placed at the same compressed distance as planets, per vertex against the live camera; points enlarged to stay visible are dimmed instead. Uses the glow fragment shader. |
| `shaders/core/skyline.*` | Far-field orbit lines and belts, compressed the same way. |
| `data/.../dimension_type/*.json` | The two dimension types that use the space sky (open space and airless worlds). |

## The key idea

A neighbouring planet can be thousands of blocks away, far beyond what the camera draws. So each
object is drawn at a compressed distance, `10 + 8 * ln(1 + d / 20)`, and shrunk by the same
factor. Apparent size depends only on size divided by distance, so it looks exactly right, and
nearer things still cover farther ones. See `SpaceRenderer.skyDistance` and `setBodyUniforms`.
