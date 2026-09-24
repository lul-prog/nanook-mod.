package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NanookEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * Indica a GeckoLib dónde están los 3 archivos que necesita la entidad:
 *  - el modelo  (.geo.json)
 *  - la textura (.png)
 *  - la animación (.animation.json)
 *
 * Ahora extiende HeadTrackingGeoModel, así que Nanook gira la cabeza hacia
 * el jugador como cualquier mob vanilla (hueso "h_head" en nanook.geo.json).
 */
public class NanookModel extends HeadTrackingGeoModel<NanookEntity> {

    @Override
    protected String headBoneName() {
        return "h_head";
    }

    /**
     * Durante los ataques la animación manda: el rugido levanta la cabeza,
     * el salto la esconde, la embestida la tira hacia adelante. Si además
     * le sumáramos el seguimiento del jugador, esas poses se deformarían.
     * Fuera de combate y caminando, sí sigue al jugador.
     */
    @Override
    protected boolean shouldTrackHead(NanookEntity animatable) {
        return !animatable.isAttacking();
    }

    @Override
    public ResourceLocation getModelResource(NanookEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/nanook.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NanookEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/nanook.png");
    }

    @Override
    public ResourceLocation getAnimationResource(NanookEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/nanook.animation.json");
    }
}
