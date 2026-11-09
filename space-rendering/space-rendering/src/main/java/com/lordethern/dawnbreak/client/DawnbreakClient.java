package com.lordethern.dawnbreak.client;

import com.lordethern.dawnbreak.Dawnbreak;
import com.lordethern.dawnbreak.client.render.SpaceRenderer;
import com.lordethern.dawnbreak.client.render.texture.SpaceTextures;
import com.lordethern.dawnbreak.galaxy.Bodies;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Dawnbreak.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DawnbreakClient {
    /** Referenced by {@code "effects": "dawnbreak:space"} in dimension_type JSON. */
    public static final ResourceLocation SPACE_EFFECTS = Dawnbreak.id("space");
    public static final KeyMapping GALAXY_MAP = new KeyMapping("key.dawnbreak.galaxy_map",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.dawnbreak");

    private DawnbreakClient() {}

    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(SPACE_EFFECTS, new SpaceSkyEffects());
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("space_labels", new SpaceLabels());
        event.registerAboveAll("navigation", new NavigationOverlay());
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(GALAXY_MAP);
        MinecraftForge.EVENT_BUS.addListener(DawnbreakClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(DawnbreakClient::onLogout);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (GALAXY_MAP.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new GalaxyMapScreen());
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Bodies.setClientCurrent(null);
        Waypoint.clear();
        SpaceTextures.clear();
        SpaceRenderer.reset();
    }
}
