package com.lordethern.dawnbreak.client;

import com.lordethern.dawnbreak.client.render.texture.TextureGenerator;
import com.lordethern.dawnbreak.client.render.texture.TextureSpec;
import com.lordethern.dawnbreak.galaxy.Bodies;
import com.lordethern.dawnbreak.galaxy.Body;
import com.lordethern.dawnbreak.galaxy.Galaxies;
import com.lordethern.dawnbreak.galaxy.Galaxy;
import com.lordethern.dawnbreak.galaxy.Star;
import com.lordethern.dawnbreak.galaxy.StarSystem;
import com.lordethern.dawnbreak.space.ModDimensions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-down galaxy map. Scroll to zoom (from a single system out to dozens of cells),
 * drag to pan, left-click a star or planet to set a waypoint, right-click to clear it.
 */
public class GalaxyMapScreen extends Screen {
    private static final double MIN_SCALE = 2, MAX_SCALE = 320;
    private static final double SHOW_BODIES_BELOW = 28;
    private static double scale = 48;

    private double centerX, centerZ;
    private Vec3 playerPos;
    @Nullable private Target hovered;

    private record Target(Component name, List<Component> info, Vec3 pos, int sx, int sy, @Nullable Body body, @Nullable StarSystem system) {}

    private final Vec3 forcedCentre;

    public GalaxyMapScreen() {
        this(null);
    }

    /** Centred on {@code centre} (e.g. the ship you are aboard) instead of on yourself. */
    public GalaxyMapScreen(@Nullable Vec3 centre) {
        super(Component.translatable("screen.dawnbreak.galaxy_map"));
        this.forcedCentre = centre;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        playerPos = mc.player == null ? Vec3.ZERO : mc.player.position();
        if (mc.level != null && !ModDimensions.isSpace(mc.level) && mc.player != null) {
            Galaxy galaxy = Galaxies.get(true);
            playerPos = Bodies.at(mc.player).flatMap(b -> galaxy.positionOf(b, Galaxy.time(mc.level, 0))).orElse(Vec3.ZERO);
        }
        if (forcedCentre != null) playerPos = forcedCentre;
        centerX = playerPos.x;
        centerZ = playerPos.z;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Galaxy galaxy = Galaxies.get(true);
        double time = mc.level == null ? 0 : Galaxy.time(mc.level, partialTick);
        g.fill(0, 0, width, height, 0xF0040410);

        List<Target> targets = new ArrayList<>();
        int minCx = Galaxy.cellOf(worldX(0)) - 1, maxCx = Galaxy.cellOf(worldX(width)) + 1;
        int minCz = Galaxy.cellOf(worldZ(0)) - 1, maxCz = Galaxy.cellOf(worldZ(height)) + 1;
        boolean showBodies = scale < SHOW_BODIES_BELOW;

        // Faint cell grid when zoomed out.
        if (scale > 60) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                int sx = screenX((double) cx * Galaxy.CELL);
                g.fill(sx, 0, sx + 1, height, 0x14FFFFFF);
            }
            for (int cz = minCz; cz <= maxCz; cz++) {
                int sy = screenY((double) cz * Galaxy.CELL);
                g.fill(0, sy, width, sy + 1, 0x14FFFFFF);
            }
        }

        galaxy.forEachSystem(minCx, maxCx, minCz, maxCz, system -> {
            int sx = screenX(system.position().x), sy = screenY(system.position().z);
            if (system.belt().isPresent() && showBodies) circle(g, sx, sy, system.belt().get().radius() / scale, 0x30B0A080);
            if (showBodies) {
                for (Body body : system.bodies()) {
                    if (body.parent().isEmpty()) circle(g, sx, sy, body.orbit().radius() / scale, 0x28FFFFFF);
                }
            }
            for (int i = 0; i < system.stars().size(); i++) {
                Star star = system.stars().get(i);
                Vec3 pos = system.starPosition(i, time);
                int px = screenX(pos.x), py = screenY(pos.z);
                int size = (int) Math.max(1, Math.min(8, star.radius() / scale));
                dot(g, px, py, size + 1, 0xFF000000 | star.color());
            }
            List<Component> info = new ArrayList<>();
            info.add(Component.translatable("map.dawnbreak.stars", system.stars().size(), system.primary().starClass().getSerializedName().toUpperCase().replace('_', ' ')).withStyle(ChatFormatting.GRAY));
            info.add(Component.translatable("map.dawnbreak.bodies", system.bodies().size()).withStyle(ChatFormatting.GRAY));
            targets.add(new Target(system.displayName(), info, system.position(), sx, sy, null, system));
            if (scale < 120) g.drawString(font, system.displayName(), sx + 6, sy - 4, 0xB0C8E0FF, false);

            if (!showBodies) return;
            for (Body body : system.bodies()) {
                Vec3 pos = system.bodyPosition(body, time);
                int bx = screenX(pos.x), by = screenY(pos.z);
                int size = (int) Math.max(1, Math.min(10, body.radius() / scale));
                dot(g, bx, by, size, 0xFF000000 | TextureGenerator.previewColor(TextureSpec.of(body)));
                List<Component> bodyInfo = new ArrayList<>();
                bodyInfo.add(Component.translatable("body_type.dawnbreak." + body.type().getSerializedName()).withStyle(ChatFormatting.GRAY));
                bodyInfo.add(Component.translatable("map.dawnbreak.gravity", String.format("%.2f", body.gravity())).withStyle(ChatFormatting.GRAY));
                bodyInfo.add(Component.translatable(body.breathable() ? "map.dawnbreak.breathable" : "map.dawnbreak.vacuum")
                        .withStyle(body.breathable() ? ChatFormatting.GREEN : ChatFormatting.RED));
                if (!body.landable()) bodyInfo.add(Component.translatable("map.dawnbreak.not_landable").withStyle(ChatFormatting.RED));
                targets.add(new Target(body.displayName(), bodyInfo, pos, bx, by, body, null));
                if (scale < 8) g.drawString(font, body.displayName(), bx + size + 3, by - 4, 0xA0FFFFFF, false);
            }
        });

        // You are here.
        int px = screenX(playerPos.x), py = screenY(playerPos.z);
        dot(g, px, py, 2, 0xFFFFFFFF);
        g.fill(px - 6, py, px - 3, py + 1, 0xFFFFFFFF);
        g.fill(px + 4, py, px + 7, py + 1, 0xFFFFFFFF);
        g.fill(px, py - 6, px + 1, py - 3, 0xFFFFFFFF);
        g.fill(px, py + 4, px + 1, py + 7, 0xFFFFFFFF);

        Waypoint.position(galaxy, time).ifPresent(pos -> {
            int wx = screenX(pos.x), wy = screenY(pos.z);
            outline(g, wx - 6, wy - 6, wx + 7, wy + 7, 0xFFFFD54A);
        });

        hovered = null;
        double best = 10 * 10;
        for (Target t : targets) {
            double d = (t.sx - mouseX) * (t.sx - mouseX) + (t.sy - mouseY) * (t.sy - mouseY);
            // Prefer bodies over their system when both are under the cursor.
            if (d < best || (t.body != null && hovered != null && hovered.body == null && d < 100)) {
                best = d;
                hovered = t;
            }
        }
        if (hovered != null) {
            outline(g, hovered.sx - 4, hovered.sy - 4, hovered.sx + 5, hovered.sy + 5, 0xFFFFFFFF);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(hovered.name);
            tooltip.addAll(hovered.info);
            tooltip.add(Component.translatable("map.dawnbreak.distance", NavigationOverlay.distance(hovered.pos.distanceTo(playerPos))).withStyle(ChatFormatting.AQUA));
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }

        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.dawnbreak.galaxy_map.help"), width / 2, height - 12, 0x909090);
        g.drawString(font, Component.translatable("map.dawnbreak.scale", NavigationOverlay.distance(scale * 100)), 6, height - 12, 0x909090, false);
        g.fill(6, height - 16, 106, height - 15, 0xFF909090);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double beforeX = worldX(mouseX), beforeZ = worldZ(mouseY);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * (delta > 0 ? 0.8 : 1.25)));
        centerX += beforeX - worldX(mouseX);
        centerZ += beforeZ - worldZ(mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        centerX -= dragX * scale;
        centerZ -= dragY * scale;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            Waypoint.clear();
            return true;
        }
        if (button == 0 && hovered != null) {
            if (hovered.body != null) Waypoint.setBody(hovered.body);
            else if (hovered.system != null) Waypoint.setSystem(hovered.system);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (DawnbreakClient.GALAXY_MAP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private double worldX(double sx) { return centerX + (sx - width / 2.0) * scale; }
    private double worldZ(double sy) { return centerZ + (sy - height / 2.0) * scale; }
    private int screenX(double x) { return (int) Math.round(width / 2.0 + (x - centerX) / scale); }
    private int screenY(double z) { return (int) Math.round(height / 2.0 + (z - centerZ) / scale); }

    private static void dot(GuiGraphics g, int x, int y, int r, int colour) {
        g.fill(x - r, y - r, x + r + 1, y + r + 1, colour);
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        g.fill(x0, y0, x1, y0 + 1, colour);
        g.fill(x0, y1 - 1, x1, y1, colour);
        g.fill(x0, y0, x0 + 1, y1, colour);
        g.fill(x1 - 1, y0, x1, y1, colour);
    }

    private void circle(GuiGraphics g, int cx, int cy, double radius, int colour) {
        if (radius < 2 || radius > 4000) return;
        int steps = (int) Math.min(720, Math.max(24, radius * 2));
        for (int i = 0; i < steps; i += 2) {
            double a = Math.PI * 2 * i / steps;
            int x = cx + (int) Math.round(Math.cos(a) * radius), y = cy + (int) Math.round(Math.sin(a) * radius);
            if (x >= 0 && y >= 0 && x < width && y < height) g.fill(x, y, x + 1, y + 1, colour);
        }
    }
}
