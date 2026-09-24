package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.GnutEntity;
import net.minecraft.resources.ResourceLocation;

/** Hueso de la cabeza en gnut.geo.json: "hi_head". */
public class GnutModel extends HeadTrackingGeoModel<GnutEntity> {

    @Override
    protected String headBoneName() {
        return "hi_head";
    }

    @Override
    public ResourceLocation getModelResource(GnutEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/gnut.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(GnutEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/gnut.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GnutEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/gnut.animation.json");
    }
}
