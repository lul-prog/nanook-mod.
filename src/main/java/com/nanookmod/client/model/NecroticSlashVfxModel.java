package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.NecroticSlashVfx;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Indica a GeckoLib dónde están los 3 archivos del VFX de tajo:
 *  - geo/entity/necrotic_slash_vfx.geo.json
 *  - textures/entity/necrotic_slash/necrotic_slash_vfx.png
 *  - animations/entity/necrotic_slash_vfx.animation.json
 *
 * Estos 3 archivos se generan re-exportando necrotic_slash_vfx.bbmodel
 * (el necrotic_combo_1.bbmodel original, renombrado) desde Blockbench con el
 * plugin de GeckoLib -- mismo flujo que ya usás para Nanook.
 */
public class NecroticSlashVfxModel extends GeoModel<NecroticSlashVfx> {

    @Override
    public ResourceLocation getModelResource(NecroticSlashVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/necrotic_slash_vfx.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(NecroticSlashVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/necrotic_slash/necrotic_slash_vfx.png");
    }

    @Override
    public ResourceLocation getAnimationResource(NecroticSlashVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/necrotic_slash_vfx.animation.json");
    }
}
