package com.nanookmod.client.render;

import com.nanookmod.NanookMod;
import com.nanookmod.client.model.NecroticKnightPulseVfxModel;
import net.minecraft.resources.ResourceLocation;
import com.nanookmod.entity.NecroticKnightPulseVfx;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NecroticKnightPulseVfxRenderer extends GeoEntityRenderer<NecroticKnightPulseVfx> {

    public NecroticKnightPulseVfxRenderer(EntityRendererProvider.Context context) {
        super(context, new NecroticKnightPulseVfxModel());
        // Brilla siempre, de día, de noche o en una cueva -- ver AlwaysGlowRenderLayer.
        this.addRenderLayer(new AlwaysGlowRenderLayer<>(this,
                new ResourceLocation(NanookMod.MOD_ID, "textures/entity/necrotic_knight_pulse_vfx.png")));
        this.shadowRadius = 0.0F;
    }
}