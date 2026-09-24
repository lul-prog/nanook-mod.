package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NecroticKnightPulseVfx;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Exportar desde Blockbench 'vfx_pulse.bbmodel' (carpeta Extras del pack):
 *   assets/nanookmod/geo/entity/necrotic_knight_pulse_vfx.geo.json
 *   assets/nanookmod/textures/entity/necrotic_knight_pulse_vfx.png (era "impact_white", 16x16)
 *   assets/nanookmod/animations/entity/necrotic_knight_pulse_vfx.animation.json
 */
public class NecroticKnightPulseVfxModel extends GeoModel<NecroticKnightPulseVfx> {

    @Override
    public ResourceLocation getModelResource(NecroticKnightPulseVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/necrotic_knight_pulse_vfx.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NecroticKnightPulseVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/necrotic_knight_pulse_vfx.png");
    }

    @Override
    public ResourceLocation getAnimationResource(NecroticKnightPulseVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/necrotic_knight_pulse_vfx.animation.json");
    }
}