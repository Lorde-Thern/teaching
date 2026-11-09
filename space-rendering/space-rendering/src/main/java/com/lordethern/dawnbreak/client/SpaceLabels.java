package com.lordethern.dawnbreak.client;

import com.lordethern.dawnbreak.client.render.SpaceRenderer;
import com.lordethern.dawnbreak.config.DawnbreakConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Names and distances floating next to planets, moons and stars in space, projected with
 * the sky camera from the frame just drawn. Closest first, capped by {@code maxLabels},
 * and labels that would overlap a closer one are skipped.
 */
public class SpaceLabels implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !DawnbreakConfig.CLIENT.showLabels.get()) return;
        List<SpaceRenderer.Label> labels = new ArrayList<>(SpaceRenderer.labels());
        if (labels.isEmpty()) return;
        labels.sort(Comparator.comparingDouble(SpaceRenderer.Label::distance));

        Font font = mc.font;
        int max = DawnbreakConfig.CLIENT.maxLabels.get();
        List<int[]> taken = new ArrayList<>();
        int drawn = 0;
        for (SpaceRenderer.Label label : labels) {
            if (drawn >= max) break;
            float[] pos = SpaceRenderer.project(label.rel(), width, height);
            if (pos == null) continue;
            int x = Math.round(pos[0]), y = Math.round(pos[1]);
            Component text = Component.empty().append(label.text())
                    .append(Component.literal(" " + NavigationOverlay.distance(label.distance())).withStyle(s -> s.withColor(0x8A9AB0)));
            int w = font.width(text);
            int lx = x + 5, ly = y - 4;
            boolean overlaps = false;
            for (int[] r : taken) {
                if (lx < r[2] && lx + w > r[0] && ly < r[3] && ly + 9 > r[1]) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) continue;
            taken.add(new int[]{lx, ly, lx + w, ly + 9});
            g.fill(x - 1, y - 1, x + 2, y + 2, 0xC0000000 | (label.colour() & 0xFFFFFF));
            g.drawString(font, text, lx, ly, 0xFFFFFFFF, true);
            drawn++;
        }
    }
}
