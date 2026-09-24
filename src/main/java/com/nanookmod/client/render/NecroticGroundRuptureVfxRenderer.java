package com.nanookmod.client.render;

import com.nanookmod.NanookMod;
import com.nanookmod.client.model.NecroticGroundRuptureVfxModel;
import net.minecraft.resources.ResourceLocation;
import com.nanookmod.entity.NecroticGroundRuptureVfx;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NecroticGroundRuptureVfxRenderer extends GeoEntityRenderer<NecroticGroundRuptureVfx> {

    public NecroticGroundRuptureVfxRenderer(EntityRendererProvider.Context context) {
        super(context, new NecroticGroundRuptureVfxModel());
        // Brilla siempre, de día, de noche o en una cueva -- ver AlwaysGlowRenderLayer.
        this.addRenderLayer(new AlwaysGlowRenderLayer<>(this,
                new ResourceLocation(NanookMod.MOD_ID, "textures/entity/necrotic_slash/necrotic_ground_rupture.png")));
        this.shadowRadius = 0f;
    }
}
