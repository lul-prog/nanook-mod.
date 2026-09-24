package com.nanookmod.client.render;

import com.nanookmod.client.model.CrowModel;
import com.nanookmod.entity.CrowEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CrowRenderer extends GeoEntityRenderer<CrowEntity> {

    public CrowRenderer(EntityRendererProvider.Context context) {
        super(context, new CrowModel());
        this.shadowRadius = 0.3F;
    }
}
