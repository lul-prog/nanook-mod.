package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.TwilightWispEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Rutas esperadas (exportar 'skull_wisps.bbmodel' desde Blockbench):
 *   assets/nanookmod/geo/entity/twilight_wisp.geo.json
 *   assets/nanookmod/textures/entity/twilight_wisp.png
 *   assets/nanookmod/animations/entity/twilight_wisp.animation.json
 *
 * Este SÍ es seguro de exportar tal cual - confirmé que la textura propia
 * del modelo (skull_wisps.png) es un cuadrado 32x32 normal, no una tira
 * animada como la del aura del warrior.
 */
public class TwilightWispModel extends GeoModel<TwilightWispEntity> {

    @Override
    public ResourceLocation getModelResource(TwilightWispEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/twilight_wisp.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TwilightWispEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/twilight_wisp.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TwilightWispEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/twilight_wisp.animation.json");
    }
}