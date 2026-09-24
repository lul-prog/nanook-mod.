package com.nanookmod.client.render;

import com.nanookmod.client.model.SnowyBlizzModel;
import com.nanookmod.entity.SnowyBlizzEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SnowyBlizzRenderer extends GeoEntityRenderer<SnowyBlizzEntity> {

    public SnowyBlizzRenderer(EntityRendererProvider.Context context) {
        super(context, new SnowyBlizzModel());
        this.shadowRadius = 0.5f;
    }
}