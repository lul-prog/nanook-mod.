package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.CrowEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * Ahora extiende HeadTrackingGeoModel: el cuervo gira la cabeza hacia el
 * jugador como cualquier mob vanilla. Hueso de la cabeza en crow.geo.json: "head".
 */
public class CrowModel extends HeadTrackingGeoModel<CrowEntity> {

    @Override
    protected String headBoneName() {
        return "head";
    }

    @Override
    public ResourceLocation getModelResource(CrowEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/crow.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CrowEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/crow.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CrowEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/crow.animation.json");
    }
}
