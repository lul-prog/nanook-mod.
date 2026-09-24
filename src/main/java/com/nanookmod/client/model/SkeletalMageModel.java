package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.SkeletalMageEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Rutas esperadas:
 *   assets/nanookmod/geo/entity/skeletal_mage.geo.json
 *   assets/nanookmod/textures/entity/skeletal_mage.png
 *   assets/nanookmod/animations/entity/skeletal_mage.animation.json
 *
 * Hueso de la cabeza: "h_head".
 */
public class SkeletalMageModel extends HeadTrackingGeoModel<SkeletalMageEntity> {

    @Override
    protected String headBoneName() {
        return "h_head";
    }

    @Override
    public ResourceLocation getModelResource(SkeletalMageEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/skeletal_mage.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SkeletalMageEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/skeletal_mage.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SkeletalMageEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/skeletal_mage.animation.json");
    }

    // La textura trae un degrade real de transparencia en las manos (varios
    // valores de alfa intermedios, no solo 0/255). El RenderType por
    // defecto de GeckoLib (entityCutoutNoCull) es binario; con
    // entityTranslucent el motor si mezcla por alfa.
    @Override
    public RenderType getRenderType(SkeletalMageEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
