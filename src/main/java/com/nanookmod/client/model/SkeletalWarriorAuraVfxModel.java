package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.SkeletalWarriorAuraVfx;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * El truco entero está acá: en vez de una sola textura fija, devolvemos una
 * de las 8 (aura_frame_0.png ... aura_frame_7.png, recortadas de la tira
 * original aura_soul_long.png) según en qué frame está la entidad. GeckoLib
 * llama a este método en cada render, así que el "video" se arma solo con
 * el swap del archivo bindeado - no hace falta ninguna animación de huesos.
 *
 * Rutas esperadas:
 *   assets/nanookmod/geo/entity/skeletal_warrior_aura_vfx.geo.json
 *   assets/nanookmod/textures/entity/skeletal_warrior/aura_frame_0.png ... _7.png
 *   assets/nanookmod/animations/entity/skeletal_warrior_aura_vfx.animation.json
 */
public class SkeletalWarriorAuraVfxModel extends GeoModel<SkeletalWarriorAuraVfx> {

    private static final ResourceLocation[] FRAME_TEXTURES = new ResourceLocation[SkeletalWarriorAuraVfx.FRAME_COUNT];

    static {
        for (int i = 0; i < FRAME_TEXTURES.length; i++) {
            FRAME_TEXTURES[i] = new ResourceLocation(NanookMod.MOD_ID,
                    "textures/entity/skeletal_warrior/aura_frame_" + i + ".png");
        }
    }

    @Override
    public ResourceLocation getModelResource(SkeletalWarriorAuraVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/skeletal_warrior_aura_vfx.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SkeletalWarriorAuraVfx animatable) {
        return FRAME_TEXTURES[animatable.getCurrentFrame()];
    }

    @Override
    public ResourceLocation getAnimationResource(SkeletalWarriorAuraVfx animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/skeletal_warrior_aura_vfx.animation.json");
    }
}