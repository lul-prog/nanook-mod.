package com.nanookmod.client.model;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.TwilightPortalEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Modelo BASE del portal: star + ring_effects (los 2 anillos que rotan en
 * direcciones opuestas) + lightball (las 4 bolitas que orbitan). Los 3
 * comparten la textura estática del portal, por eso van juntos en un solo
 * modelo con animación de huesos real (a diferencia del intento anterior,
 * que aplanaba todo esto en una malla estática sin animar -- ver charla).
 *
 * Las 3 capas restantes (middle_effect / _2 / _3 / _4, las que usan las
 * texturas small/middle/big) NO van acá -- GeckoLib solo permite una
 * textura bindeada por modelo. Esas se dibujan como render layers aparte
 * (ver TwilightPortalSpinLayer en TwilightPortalRenderer), pero con
 * rotación real por código en vez del hack de 12 frames pre-rotados que
 * tenía el intento anterior.
 */
public class TwilightPortalModel extends GeoModel<TwilightPortalEntity> {

    @Override
    public ResourceLocation getModelResource(TwilightPortalEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "geo/entity/twilight_portal_base.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TwilightPortalEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "textures/entity/twilight_portal/portal_static.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TwilightPortalEntity animatable) {
        return new ResourceLocation(NanookMod.MOD_ID, "animations/entity/twilight_portal_base.animation.json");
    }

    // Sin esto, GeckoLib usa su RenderType default (entityCutoutNoCull),
    // que es "todo o nada": cualquier gradiente/transparencia parcial de
    // la textura (como el fundido de las franjas de ring_effect) se
    // dibuja 100% opaco en vez de mezclado -- por eso la franja se veía
    // como un bastón blanco sólido en vez de translúcida como en
    // Blockbench.
    @Override
    public RenderType getRenderType(TwilightPortalEntity animatable, ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture);
    }
}