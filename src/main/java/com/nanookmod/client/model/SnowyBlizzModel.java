package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.SnowyBlizzEntity;
import net.minecraft.resources.ResourceLocation;

/** Hueso de la cabeza en snowy_blizz.geo.json: "hi_head". */
public class SnowyBlizzModel extends HeadTrackingGeoModel<SnowyBlizzEntity> {

    @Override
    protected String headBoneName() {
        return "hi_head";
    }

    @Override
    public ResourceLocation getModelResource(SnowyBlizzEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/snowy_blizz.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SnowyBlizzEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/snowy_blizz.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SnowyBlizzEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/snowy_blizz.animation.json");
    }
}
