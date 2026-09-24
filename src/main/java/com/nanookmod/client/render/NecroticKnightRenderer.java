package com.nanookmod.client.render;

import com.nanookmod.NanookMod;
import com.nanookmod.client.model.NecroticKnightModel;
import com.nanookmod.entity.NecroticKnightEntity;
import com.nanookmod.entity.NecroticKnightFakeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;

public class NecroticKnightRenderer extends GeoEntityRenderer<NecroticKnightEntity> {

    private static final ResourceLocation AURA_MODEL =
            new ResourceLocation(NanookMod.MOD_ID, "geo/entity/necroknight_aura_vfx.geo.json");

    // TEST del "espejo" (ver com.nanookmod.entity.NecroticKnightFakeEntity
    // y la charla sobre el Brain of Cthulhu de Confluence): 51% de opacidad
    // fija por ahora, sin la disolución con ruido de BrainTranslucent -
    // ajustable a gusto una vez que se vea en juego.
    private static final int MIRROR_ALPHA = 130;

    public NecroticKnightRenderer(EntityRendererProvider.Context context) {
        super(context, new NecroticKnightModel());
        this.shadowRadius = 0.8F;
        // Geo propio del Knight (trae Knight_Fuego + Horse_Fuego juntos,
        // extraído directamente del necroknight_1.bbmodel -- ya viene con
        // la posición correcta bakeada para las proporciones de este mob,
        // por eso el offset queda en 0,0,0). Misma textura/frames que el
        // Warrior, eso sí es compartido.
        this.addRenderLayer(new NecroticAuraRenderLayer<>(this, AURA_MODEL,
                "textures/entity/skeletal_warrior/aura_frame_", 8, 0.0, 0.0, 0.0));
        // Ojos + arma brillando -- ver NecroticKnightGlowLayer. Se registra
        // DESPUÉS del aura a propósito: las capas se dibujan en el orden en
        // que se agregan, y queremos que el brillo de los ojos/arma quede
        // por encima del aura de fuego, no tapado por ella.
        this.addRenderLayer(new NecroticKnightGlowLayer(this));
    }

    /**
     * getRenderType/getRenderColor son de la interfaz GeoRenderer<T> con
     * T=NecroticKnightEntity acá (por erasure de genéricos, el parámetro
     * SIGUE siendo NecroticKnightEntity incluso para instancias que en
     * runtime son NecroticKnightFakeEntity) - por eso NO hace falta una
     * clase renderer separada para el espejo: alcanza con este
     * instanceof, y como esta misma instancia de renderer se registra
     * para los dos EntityType (real y espejo), Forge la usa para ambos.
     */
    @Override
    public RenderType getRenderType(NecroticKnightEntity animatable, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick) {
        if (animatable instanceof NecroticKnightFakeEntity) {
            // GeckoLib usa entityCutoutNoCull por defecto, que solo hace
            // transparencia binaria (todo o nada) -- para blend real de
            // alpha hace falta el RenderType translúcido.
            return RenderType.entityTranslucent(texture);
        }
        // Entidad real: solo se cambia a translúcido cuando hace falta de
        // verdad (Vanish desvaneciéndose/reapareciendo, o el flash de
        // daño) -- así el resto del tiempo (99% de la pelea) sigue usando
        // el RenderType por defecto de GeckoLib sin costo extra ni
        // cambios de apariencia no pedidos.
        if (animatable.getRenderAlpha() < 255) {
            return RenderType.entityTranslucent(texture);
        }
        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    @Override
    public Color getRenderColor(NecroticKnightEntity animatable, float partialTick, int packedLight) {
        Color base = super.getRenderColor(animatable, partialTick, packedLight);
        if (animatable instanceof NecroticKnightFakeEntity) {
            return Color.ofRGBA(base.getRed(), base.getGreen(), base.getBlue(), MIRROR_ALPHA);
        }
        int alpha = animatable.getRenderAlpha();
        if (alpha < 255) {
            return Color.ofRGBA(base.getRed(), base.getGreen(), base.getBlue(), alpha);
        }
        return base;
    }
}