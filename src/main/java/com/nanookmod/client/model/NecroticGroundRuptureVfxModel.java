package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NecroticGroundRuptureVfx;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class NecroticGroundRuptureVfxModel extends GeoModel<NecroticGroundRuptureVfx> {

    @Override
    public ResourceLocation getModelResource(NecroticGroundRuptureVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/necrotic_ground_rupture.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NecroticGroundRuptureVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/necrotic_slash/necrotic_ground_rupture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(NecroticGroundRuptureVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/necrotic_ground_rupture.animation.json");
    }
}
