package com.lordethern.dawnbreak.client.render;

import com.lordethern.dawnbreak.Dawnbreak;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/** Core shaders in assets/dawnbreak/shaders/core. Reloaded with resource packs. */
@Mod.EventBusSubscriber(modid = Dawnbreak.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DawnbreakShaders {
    static ShaderInstance planet;
    static ShaderInstance planetTranslucent;
    static ShaderInstance atmosphere;
    static ShaderInstance glow;
    static ShaderInstance skyPoint;
    static ShaderInstance skyLine;

    private DawnbreakShaders() {}

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Dawnbreak.id("planet"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> planet = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Dawnbreak.id("planet_translucent"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> planetTranslucent = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Dawnbreak.id("atmosphere"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> atmosphere = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Dawnbreak.id("glow"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> glow = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Dawnbreak.id("skypoint"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> skyPoint = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Dawnbreak.id("skyline"), DefaultVertexFormat.POSITION_COLOR), s -> skyLine = s);
        } catch (IOException e) {
            Dawnbreak.LOGGER.error("Failed to load Dawnbreak space shaders; space will render without planets", e);
        }
    }

    static boolean ready() {
        return planet != null && planetTranslucent != null && atmosphere != null && glow != null && skyPoint != null && skyLine != null;
    }
}
