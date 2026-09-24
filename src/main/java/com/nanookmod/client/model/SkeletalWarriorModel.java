package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.SkeletalWarriorEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * Rutas esperadas:
 *   assets/nanookmod/geo/entity/skeletal_warrior.geo.json
 *   assets/nanookmod/textures/entity/skeletal_warrior.png
 *   assets/nanookmod/animations/entity/skeletal_warrior.animation.json
 *
 * Hueso de la cabeza: "h_head".
 */
public class SkeletalWarriorModel extends HeadTrackingGeoModel<SkeletalWarriorEntity> {

    @Override
    protected String headBoneName() {
        return "h_head";
    }

    @Override
    public ResourceLocation getModelResource(SkeletalWarriorEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/skeletal_warrior.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SkeletalWarriorEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/skeletal_warrior.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SkeletalWarriorEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/skeletal_warrior.animation.json");
    }
}
