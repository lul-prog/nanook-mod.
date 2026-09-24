package com.nanookmod.client.render;

import com.nanookmod.client.model.SkeletalMageModel;
import com.nanookmod.entity.SkeletalMageEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SkeletalMageRenderer extends GeoEntityRenderer<SkeletalMageEntity> {

    public SkeletalMageRenderer(EntityRendererProvider.Context context) {
        super(context, new SkeletalMageModel());
        this.shadowRadius = 0.5F;
        this.addRenderLayer(new NecroticAuraRenderLayer<>(this));
    }
}