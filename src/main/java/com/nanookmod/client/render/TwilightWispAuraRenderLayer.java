package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import com.nanookmod.client.model.TwilightWispModel;
import com.nanookmod.entity.TwilightWispEntity;

/**
 * Aura verde neón pegada al cuerpo del wisp -- a diferencia de
 * NecroticAuraRenderLayer (que usa un .geo.json + frames de textura APARTE
 * para el aura), esta capa reRenderea el MISMO modelo/textura del wisp un
 * poco más grande, translúcido y aditivo. Es el truco clásico de "outline
 * glow" reusando la malla propia -- no hace falta modelar ni texturizar un
 * aura separada, y como el modelo del wisp (twilight_wisp.geo.json) ya está
 * centrado bien cerca de su pivote, escalarlo desde el origen alcanza sin
 * tener que compensar con translaciones.
 *
 * Compañera de TwilightWispTrailRenderLayer: mismo criterio de color y de
 * pulso de brillo, para que aura y estela se lean como el mismo efecto.
 */
public class TwilightWispAuraRenderLayer extends GeoRenderLayer<TwilightWispEntity> {

    // Tono #9cf6de (cian/menta neón) -- mismo color que el halo de la estela.
    private static final float COLOR_R = 0.6118f;
    private static final float COLOR_G = 0.9647f;
    private static final float COLOR_B = 0.8706f;

    // Qué tan "inflada" se ve el aura respecto al cuerpo real.
    private static final float AURA_SCALE = 1.22f;
    private static final float BASE_ALPHA = 0.45f;

    private static final float PULSE_SPEED = 0.12f;
    private static final float PULSE_AMOUNT = 0.15f;

    // Usamos el modelo concreto directo (en vez de confiar en algún método
    // de conveniencia de la interfaz GeoRenderer que no pude verificar sin
    // compilar) -- así conseguimos la textura del wisp de la forma más
    // segura posible: exactamente el mismo GeoModel que ya usa
    // TwilightWispRenderer.
    private final TwilightWispModel wispModel = new TwilightWispModel();

    public TwilightWispAuraRenderLayer(GeoRenderer<TwilightWispEntity> entityRenderer) {
        super(entityRenderer);
    }

    @Override
    public void render(PoseStack poseStack, TwilightWispEntity animatable, BakedGeoModel bakedModel,
                        RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                        float partialTick, int packedLight, int packedOverlay) {

        ResourceLocation texture = wispModel.getTextureResource(animatable);
        RenderType auraRenderType = RenderType.entityTranslucentEmissive(texture);
        VertexConsumer auraBuffer = bufferSource.getBuffer(auraRenderType);

        float age = animatable.tickCount + partialTick;
        float pulse = 1.0f + PULSE_AMOUNT * Mth.sin(age * PULSE_SPEED);
        float alpha = BASE_ALPHA * pulse;

        poseStack.pushPose();
        poseStack.scale(AURA_SCALE, AURA_SCALE, AURA_SCALE);

        // full-bright (15) para que el aura no dependa de la luz ambiente --
        // igual que hace TwilightWispRenderer#getBlockLightLevel con el
        // cuerpo del wisp.
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, auraRenderType,
                auraBuffer, partialTick, 15, packedOverlay, COLOR_R, COLOR_G, COLOR_B, alpha);

        poseStack.popPose();
    }
}
