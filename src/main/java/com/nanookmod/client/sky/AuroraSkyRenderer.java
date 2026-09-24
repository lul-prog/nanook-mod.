package com.nanookmod.client.sky;

import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nanookmod.NanookMod;
import com.nanookmod.client.shader.ModShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dibuja una "cúpula" de aurora boreal sobre el jugador mientras esté parado
 * en el bioma Mar Primigenio. Usa el core shader nanookmod:aurora
 * (ver assets/nanookmod/shaders/core/aurora.*, portado desde el shader real
 * de Eternal Starlight), que genera todo el ruido/color/movimiento por GPU.
 *
 * Java solo se encarga de: comprobar el bioma, dibujar un plano grande
 * arriba de la cámara, y actualizar el uniform GameTime cada frame para que
 * la animación avance.
 *
 * FIX (antes no se veía nunca): al plano le faltaba deshabilitar el
 * culling de caras. El plano se dibuja a 170 bloques POR ENCIMA de la
 * cámara, y con el orden de vértices que tenía, la cara que queda mirando
 * hacia ABAJO (hacia el jugador, que es literalmente la única cara que el
 * jugador puede llegar a ver) es la cara "de atrás" según OpenGL - y por
 * defecto esas caras se descartan (culling) para no gastar tiempo
 * dibujando lo que se supone no se ve. Como nunca se llamaba a
 * RenderSystem.disableCull(), el plano SIEMPRE se descartaba antes de
 * llegar al fragment shader: por eso no importaba qué tan bien estuviera
 * el shader, nunca había nada que pintar en pantalla. Vainilla evita este
 * mismo problema en su cúpula de cielo/estrellas deshabilitando culling
 * mientras la dibuja - hacemos exactamente lo mismo acá.
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AuroraSkyRenderer {

    private static final ResourceKey<Biome> MAR_PRIMIGENIO = ResourceKey.create(
            Registries.BIOME, new ResourceLocation(NanookMod.MOD_ID, "mar_primigenio"));

    // Altura (en bloques) sobre la cámara a la que se dibuja el plano de la aurora.
    private static final float HEIGHT = 170.0f;
    // Medio-tamaño del plano cuadrado. Más grande = se ve más "lejos" en el horizonte.
    private static final float HALF_SIZE = 300.0f;

    @SubscribeEvent
    public static void onRenderSky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        ShaderInstance shader = ModShaders.getAuroraShader();
        if (shader == null) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        if (!level.getBiome(playerPos).is(MAR_PRIMIGENIO)) return;

        PoseStack poseStack = event.getPoseStack();

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        // Ver el comentario de clase: sin esto el plano nunca se veía,
        // porque su única cara visible (mirando hacia abajo, al jugador)
        // es la que OpenGL descarta por defecto.
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shader);

        // GameTime no es un uniform que Forge/vanilla actualicen solos (a diferencia de
        // ProjMat/ModelViewMat/Fog...), así que lo empujamos nosotros cada frame.
        Uniform gameTimeUniform = shader.getUniform("GameTime");
        if (gameTimeUniform != null) {
            gameTimeUniform.set((level.getGameTime() % 1_000_000L) / 20.0f);
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        poseStack.pushPose();
        // El PoseStack que llega aquí ya está centrado en la cámara (igual que el
        // cielo/estrellas vanilla), así que solo subimos el plano por encima nuestro.
        poseStack.translate(0.0, HEIGHT, 0.0);

        var mat = poseStack.last().pose();
        float a = 0.85f;
        builder.vertex(mat, -HALF_SIZE, 0, -HALF_SIZE).color(1f, 1f, 1f, a).endVertex();
        builder.vertex(mat, -HALF_SIZE, 0,  HALF_SIZE).color(1f, 1f, 1f, a).endVertex();
        builder.vertex(mat,  HALF_SIZE, 0,  HALF_SIZE).color(1f, 1f, 1f, a).endVertex();
        builder.vertex(mat,  HALF_SIZE, 0, -HALF_SIZE).color(1f, 1f, 1f, a).endVertex();

        poseStack.popPose();
        tesselator.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }
}
