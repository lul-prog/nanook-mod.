package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * PEDIDO: "los vfx de ataque que brillen siempre, igual que la espada, los ojos y otras partes",
 * refiriéndote a los efectos de golpe de GeckoLib -- necrotic_slash_vfx (el tajo), el pulso de
 * impacto y la grieta del suelo.
 *
 * QUÉ HACE. Vuelve a dibujar el modelo del VFX, con SU MISMA textura de color (no hace falta una
 * máscara .png aparte, a diferencia de NecroticKnightGlowLayer, que sí necesita
 * necrotic_knight_glowmask.png porque el Knight solo quiere que brille una PARTE del cuerpo), pero
 * con un RenderType "emissive": no lo oscurece la luz del mundo, así que se ve igual de brillante
 * de día que en plena noche o dentro de una cueva. Es la misma técnica que ya usa el brillo de la
 * espada/ojos del Knight (ver NecroticKnightGlowLayer) -- reutiliza el mismo
 * GlowingDepthWriteRenderType, así que respeta profundidad contra el resto del mundo en vez de
 * atravesar paredes.
 *
 * CÓMO USARLA (un solo argumento en el constructor del renderer, ver los 3 renderers de VFX):
 *   super(context, new NecroticSlashVfxModel());
 *   this.addRenderLayer(new AlwaysGlowRenderLayer<>(this));
 *
 * Si en algún momento un VFX necesita que solo brille una parte (por ejemplo un tajo con un filo
 * más intenso que el resto), la solución es la misma que ya existe para el Knight: pintar una
 * máscara aparte y usar NecroticKnightGlowLayer como plantilla en vez de esta capa genérica.
 */
public class AlwaysGlowRenderLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {

    private final ResourceLocation texture;

    public AlwaysGlowRenderLayer(GeoRenderer<T> entityRenderer, ResourceLocation texture) {
        super(entityRenderer);
        this.texture = texture;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel,
                       RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                       float partialTick, int packedLight, int packedOverlay) {
        RenderType glowRenderType = GlowingDepthWriteRenderType.get(this.texture);
        VertexConsumer glowBuffer = bufferSource.getBuffer(glowRenderType);
        // packedLight = 15 (máximo bloque, sin depender de la luz real del mundo) y alfa 1: full bright.
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowRenderType,
                glowBuffer, partialTick, 15, packedOverlay, 1f, 1f, 1f, 1f);
    }
}
