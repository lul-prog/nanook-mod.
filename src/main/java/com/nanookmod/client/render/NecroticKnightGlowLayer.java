package com.nanookmod.client.render;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NecroticKnightEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Ojos y arma brillando -- redibuja el MISMO modelo del jefe (no un mesh
 * satélite aparte, a diferencia de NecroticAuraRenderLayer) usando
 * necrotic_knight_glowmask.png: una textura del mismo tamaño/UV que la
 * normal, transparente en todos lados salvo los ojos y la geometría del
 * arma, con los colores QUE YA TIENE la textura original en esos puntos
 * (no inventamos color nuevo -- ver conversación: se armó recortando los
 * rectángulos UV reales de necrotic_knight.geo.json).
 *
 * Reusa GlowingDepthWriteRenderType (extraída de NecroticAuraRenderLayer)
 * para el mismo truco de siempre: brillo parejo día/noche vía
 * entityTranslucentEmissive, pero SÍ escribe profundidad para ocluirse
 * bien contra el portal y el resto del mundo.
 *
 * La intensidad (alfa de esta capa) escala con el nivel de Corrupción del
 * jefe (ver NecroticKnightEntity#getCorruptionLevel) -- sutil en 0, casi
 * al tope en MAX_CORRUPTION. Encaja con la idea original: "sus ojos se
 * vuelven más intensos" a medida que sube la Corrupción.
 */
public class NecroticKnightGlowLayer extends GeoRenderLayer<NecroticKnightEntity> {

    private static final ResourceLocation GLOWMASK_TEXTURE =
            new ResourceLocation(NanookMod.MOD_ID, "textures/entity/necrotic_knight_glowmask.png");

    // Alfa de la capa en Corrupción 0 y en MAX_CORRUPTION -- interpola
    // lineal entre los dos según el nivel actual. No lo llevamos a 0 en el
    // mínimo porque un jefe con los ojos completamente apagados en reposo
    // se ve raro/roto, no "sutil".
    private static final float GLOW_ALPHA_AT_MIN_CORRUPTION = 0.55F;
    private static final float GLOW_ALPHA_AT_MAX_CORRUPTION = 1.0F;

    public NecroticKnightGlowLayer(GeoRenderer<NecroticKnightEntity> entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public void render(PoseStack poseStack, NecroticKnightEntity animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        RenderType glowRenderType = GlowingDepthWriteRenderType.get(GLOWMASK_TEXTURE);
        VertexConsumer glowBuffer = bufferSource.getBuffer(glowRenderType);

        // Multiplicado por el alpha de render del CUERPO (ver
        // NecroticKnightEntity#getRenderAlpha, 0-255 -- 255 = normal): sin
        // esto, durante el Vanish el cuerpo se desvanece pero los ojos y
        // el arma se quedarían brillando solos, flotando en el aire.
        // Mismo motivo para el flash de daño: si no, el flash de
        // transparencia del cuerpo no se notaría porque el glow lo tapa.
        float alpha = computeGlowAlpha(animatable) * (animatable.getRenderAlpha() / 255f);

        // Mismo bakedModel que el cuerpo principal (no uno satélite aparte
        // como el aura) -- por eso el glow queda pegado 1:1 a cada hueso
        // en cualquier pose/animación, sin offsets a mano.
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowRenderType,
                glowBuffer, partialTick, 15, packedOverlay, 1f, 1f, 1f, alpha);
    }

    private static float computeGlowAlpha(NecroticKnightEntity knight) {
        int level = knight.getCorruptionLevel();
        int max = knight.getMaxCorruptionLevel();
        if (max <= 0) {
            return GLOW_ALPHA_AT_MIN_CORRUPTION;
        }
        float t = Math.min(1.0F, level / (float) max);
        return GLOW_ALPHA_AT_MIN_CORRUPTION + (GLOW_ALPHA_AT_MAX_CORRUPTION - GLOW_ALPHA_AT_MIN_CORRUPTION) * t;
    }
}