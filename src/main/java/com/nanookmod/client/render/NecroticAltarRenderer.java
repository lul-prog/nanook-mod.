package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nanookmod.NanookMod;
import com.nanookmod.block.entity.NecroticAltarBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * PEDIDO: "no me termina de gustar cómo se ve el ring... quitemos [el panel
 * al mirar el altar] además de la textura que se coloca arriba del altar,
 * la de 'altar glow ring'; la otra relacionada con el cuerpo del bloque
 * no [la saques]".
 *
 * Esta es la versión recortada: el disco (RING_TEXTURE / altar_glow_ring.png)
 * y el núcleo (CORE_TEXTURE / altar_glow_core.png) que antes se dibujaban
 * flotando arriba del altar SE SACARON ENTERAS -- ya no hay ningún quad
 * plano ahí. Lo único que queda de la parte visual "extra" es:
 *
 *   1. El brillo del CUERPO del bloque (renderBodyGlow / BRIGHT_MODEL) --
 *      las líneas grabadas en la base y cerca del tope que se encienden en
 *      full-bright. Esto es justo lo que pediste mantener.
 *   2. El disparo de los filamentos (AltarFilamentManager.maybeSpawn),
 *      porque no forma parte de lo que mencionaste sacar -- siguen
 *      apareciendo alrededor del altar durante el ritual.
 *
 * El progreso ahora se comunica por MENSAJE (ver
 * NecroticAltarBlockEntity#showStatus / #buildStatusLine y el aviso
 * periódico en tickRitualBuildup), no por ningún disco creciendo. Las dos
 * texturas .png (altar_glow_ring.png, altar_glow_core.png) quedan sin
 * usar en el proyecto -- no se borraron del disco por las dudas de que
 * las quieras reciclar para otra cosa, pero podés eliminarlas sin miedo.
 */
public class NecroticAltarRenderer implements BlockEntityRenderer<NecroticAltarBlockEntity> {

    // PEDIDO: "ciertos detalles del altar (líneas verdes en la base y cerca del tope) que también
    // brillen". Mismo truco que usa vanilla para ojos de Enderman/Araña: un modelo con la MISMA
    // geometría del bloque pero una textura aparte, casi toda transparente, que solo pinta esos
    // detalles -- se dibuja como capa extra, en full-bright, encima del modelo normal. Coplanar con
    // el modelo real (exactamente los mismos vértices, mismo PoseStack), así que el depth test
    // (LEQUAL) lo deja pasar sin z-fighting: son los MISMOS valores de profundidad, no unos
    // "parecidos". Por eso no hace falta nada del truco de GlowingDepthWriteRenderType acá --
    // alcanza con el RenderType público entityTranslucent, sin reflection.
    //
    // El modelo variante (altar_necromante_bright.json) y su registro como "modelo adicional" (ver
    // ClientEvents#registerAdditionalModels) son necesarios porque este modelo no está atado a
    // ningún blockstate ni item -- si no lo pedís explícitamente, Forge ni se entera de que existe
    // y nunca lo hornea, así que getModel(...) devolvería el modelo "missing".
    private static final ModelResourceLocation BRIGHT_MODEL = new ModelResourceLocation(
            new ResourceLocation(NanookMod.MOD_ID, "altar_necromante_bright"), "inventory");

    public NecroticAltarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NecroticAltarBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        // El brillo del cuerpo sigue atado a getOverallOfferingFraction(): aparece/desaparece según
        // cuánto se ofrendó en total, no está prendido de entrada.
        float overallFraction = blockEntity.getOverallOfferingFraction();
        if (overallFraction > 0.001F) {
            renderBodyGlow(poseStack, bufferSource, packedOverlay);
        }

        // Filamentos: se disparan desde acá (ver AltarFilamentManager) leyendo directo el progreso
        // ya sincronizado del BlockEntity -- no hace falta ningún paquete de red nuevo para esto.
        if (blockEntity.getLevel() != null) {
            long gameTime = blockEntity.getLevel().getGameTime();
            float animTime = gameTime + partialTick;
            float ritualProgress = blockEntity.getRitualProgress();
            Vec3 center = Vec3.atCenterOf(blockEntity.getBlockPos()).add(0.0D, 0.75D, 0.0D);
            AltarFilamentManager.maybeSpawn(blockEntity.getBlockPos().asLong(), center, ritualProgress, animTime);
        }
    }

    /**
     * Dibuja el modelo "bright" (mismos elementos que el modelo real del altar, textura aparte
     * casi toda transparente) en full-bright, sin ningún tint -- el color de las líneas ya viene
     * horneado en altar_necromante_body_bright.png, así que r=g=b=1.0 para no alterarlo.
     *
     * entityTranslucent (sin "Emissive") respeta el culling normal, como el modelo sólido real --
     * sigue en full-bright porque eso lo da el packedLight que le pasamos (0xF000F0), no el shader
     * emissive en sí. Con la variante "Emissive" el culling se desactiva y desde ciertos ángulos
     * (parado debajo del altar, mirando entre las columnas) se veían caras de más, superpuestas.
     */
    private static void renderBodyGlow(PoseStack poseStack, MultiBufferSource bufferSource, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        BakedModel brightModel = mc.getModelManager().getModel(BRIGHT_MODEL);
        RenderType bodyGlowType = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        VertexConsumer buffer = bufferSource.getBuffer(bodyGlowType);
        int fullBrightLight = 0xF000F0;

        mc.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(), buffer, null, brightModel, 1.0F, 1.0F, 1.0F, fullBrightLight, packedOverlay);
    }
}
