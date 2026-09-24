package com.nanookmod.client.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import com.nanookmod.NanookMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Registra el "core shader" de la aurora boreal (assets/nanookmod/shaders/core/aurora.*)
 * usando la API de Forge (RegisterShadersEvent), igual que vanilla registra sus propios
 * shaders de cielo/partículas.
 *
 * El fragment shader (aurora.fsh) genera el ruido/forma de la aurora por GPU, así que
 * en Java solo necesitamos: 1) registrar el ShaderInstance, 2) dibujar un quad con él
 * (ver AuroraSkyRenderer), 3) actualizar el uniform GameTime cada frame para que se anime.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModShaders {

    @Nullable
    private static ShaderInstance auroraShader;

    @Nullable
    private static ShaderInstance necroticWaveShader;

    @Nullable
    private static ShaderInstance necroticBeamShader;

    @Nullable
    public static ShaderInstance getAuroraShader() {
        return auroraShader;
    }

    @Nullable
    public static ShaderInstance getNecroticWaveShader() {
        return necroticWaveShader;
    }

    @Nullable
    public static ShaderInstance getNecroticBeamShader() {
        return necroticBeamShader;
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        new ResourceLocation(NanookMod.MOD_ID, "aurora"),
                        DefaultVertexFormat.POSITION_COLOR
                ),
                shader -> auroraShader = shader
        );

        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        new ResourceLocation(NanookMod.MOD_ID, "necrotic_wave"),
                        DefaultVertexFormat.POSITION_COLOR
                ),
                shader -> necroticWaveShader = shader
        );

        // Necesita UV además de posición/color (el rayo usa esas
        // coordenadas para pintar el patrón de energía/borde, ver
        // necrotic_beam.fsh) -- por eso POSITION_TEX_COLOR y no
        // POSITION_COLOR como los otros dos.
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        new ResourceLocation(NanookMod.MOD_ID, "necrotic_beam"),
                        DefaultVertexFormat.POSITION_TEX_COLOR
                ),
                shader -> necroticBeamShader = shader
        );
    }
}
