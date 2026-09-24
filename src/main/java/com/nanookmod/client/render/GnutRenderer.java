package com.nanookmod.client.render;

import com.nanookmod.client.model.GnutModel;
import com.nanookmod.entity.GnutEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GnutRenderer extends GeoEntityRenderer<GnutEntity> {

    public GnutRenderer(EntityRendererProvider.Context context) {
        super(context, new GnutModel());
        this.shadowRadius = 0.5F;
    }
}