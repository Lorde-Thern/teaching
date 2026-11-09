package com.lordethern.dawnbreak.client;

import com.lordethern.dawnbreak.client.ship.ShipHud;
import com.lordethern.dawnbreak.config.DawnbreakConfig;
import com.lordethern.dawnbreak.galaxy.Bodies;
import com.lordethern.dawnbreak.galaxy.Body;
import com.lordethern.dawnbreak.galaxy.Galaxies;
import com.lordethern.dawnbreak.galaxy.Galaxy;
import com.lordethern.dawnbreak.galaxy.StarSystem;
import com.lordethern.dawnbreak.space.ModDimensions;
import com.lordethern.dawnbreak.space.gravity.GravityHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Top-left readout.
 * In space: where you are, speed, waypoint with ETA, and the nearest bodies with steering arrows.
 * On a planet: name, gravity, air warning, and the distance to orbit near the top of the world.
 */
public class NavigationOverlay implements IGuiOverlay {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int PANEL = 0x90000000;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui || mc.options.renderDebug) return;
        if (!DawnbreakConfig.CLIENT.showNavigationHud.get()) return;

        List<Component> lines = new ArrayList<>();
        ShipHud.lines(player, lines);
        Galaxy galaxy = Galaxies.get(true);
        double time = Galaxy.time(mc.level, partialTick);
        if (ModDimensions.isSpace(mc.level)) {
            spaceLines(player, galaxy, time, lines);
        } else {
            Bodies.at(player).ifPresent(body -> planetLines(player, body, lines));
        }
        if (lines.isEmpty()) return;

        Font font = mc.font;
        int panelWidth = 0;
        for (Component line : lines) panelWidth = Math.max(panelWidth, font.width(line));
        int x = 4, y = 4;
        graphics.fill(x - 2, y - 2, x + panelWidth + 2, y + lines.size() * 10, PANEL);
        for (Component line : lines) {
            graphics.drawString(font, line, x, y, 0xFFFFFF, true);
            y += 10;
        }
    }

    private static void spaceLines(LocalPlayer player, Galaxy galaxy, double time, List<Component> lines) {
        Vec3 eye = player.getEyePosition();
        Optional<StarSystem> system = SpaceFlightControls.currentSystem();
        lines.add(system.map(s -> Component.translatable("hud.dawnbreak.in_system", s.displayName()))
                .orElse(Component.translatable("hud.dawnbreak.interstellar")).withStyle(s -> s.withColor(0x9FD3FF)));

        double speed = SpaceFlightControls.speed();
        Component speedLine = Component.translatable("hud.dawnbreak.speed", Math.round(speed));
        if (SpaceFlightControls.cruising()) speedLine = speedLine.copy().append(Component.translatable("hud.dawnbreak.cruise").withStyle(ChatFormatting.GOLD));
        lines.add(speedLine);

        Waypoint.position(galaxy, time).ifPresent(target -> {
            Vec3 offset = target.subtract(eye);
            double dist = offset.length();
            Component name = Waypoint.name(galaxy).orElse(Component.literal("?"));
            String eta = speed > 1 ? formatTime(dist / speed) : "--";
            lines.add(Component.translatable("hud.dawnbreak.waypoint", arrow(player, offset), name, distance(dist), eta)
                    .withStyle(ChatFormatting.YELLOW));
        });

        system.ifPresent(s -> {
            List<Body> bodies = new ArrayList<>(s.bodies());
            bodies.sort(Comparator.comparingDouble(b -> s.bodyPosition(b, time).distanceToSqr(eye)));
            int max = DawnbreakConfig.CLIENT.hudMaxPlanets.get();
            for (int i = 0; i < Math.min(max, bodies.size()); i++) {
                Body body = bodies.get(i);
                Vec3 offset = s.bodyPosition(body, time).subtract(eye);
                double toAtmosphere = Math.max(0, offset.length() - body.atmosphereRadius());
                Component line = Component.translatable("hud.dawnbreak.planet_entry", arrow(player, offset), body.displayName(), distance(toAtmosphere));
                lines.add(body.landable() ? line : line.copy().withStyle(ChatFormatting.GRAY));
            }
        });
        lines.add(Component.translatable("hud.dawnbreak.map_hint", DawnbreakClient.GALAXY_MAP.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void planetLines(LocalPlayer player, Body body, List<Component> lines) {
        double gravity = GravityHandler.multiplier(player);
        lines.add(Component.translatable("hud.dawnbreak.gravity", body.displayName(), String.format("%.2f", gravity)));
        if (!body.breathable()) lines.add(Component.translatable("hud.dawnbreak.no_atmosphere").withStyle(ChatFormatting.RED));
        int orbitY = player.level().getMaxBuildHeight() + DawnbreakConfig.serverOr(DawnbreakConfig.SERVER.orbitExitMargin);
        int remaining = (int) Math.ceil(orbitY - player.getY());
        if (remaining <= 96) lines.add(Component.translatable("hud.dawnbreak.orbit_in", Math.max(0, remaining)));
    }

    static String distance(double blocks) {
        return blocks >= 10000 ? String.format("%.1f km", blocks / 1000) : Math.round(blocks) + " m";
    }

    private static String formatTime(double seconds) {
        long s = Math.round(seconds);
        return s >= 3600 ? (s / 3600) + "h " + (s % 3600 / 60) + "m" : s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }

    /** Arrow pointing where to steer: horizontal arrow from yaw, plus ▲/▼ when far off in pitch. */
    private static String arrow(LocalPlayer player, Vec3 offset) {
        float targetYaw = (float) Math.toDegrees(Math.atan2(-offset.x, offset.z));
        float yawDiff = Mth.wrapDegrees(targetYaw - player.getYRot());
        int sector = Math.floorMod(Math.round(yawDiff / 45F), 8);
        double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(offset.y, horizontal));
        float pitchDiff = targetPitch - player.getXRot();
        String vertical = pitchDiff < -20 ? "▲" : pitchDiff > 20 ? "▼" : "";
        return ARROWS[sector] + vertical;
    }
}
