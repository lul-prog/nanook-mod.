package com.nanookmod.client.render;

import com.nanookmod.client.model.SkeletalWarriorModel;
import com.nanookmod.entity.SkeletalWarriorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SkeletalWarriorRenderer extends GeoEntityRenderer<SkeletalWarriorEntity> {

    public SkeletalWarriorRenderer(EntityRendererProvider.Context context) {
        super(context, new SkeletalWarriorModel());
        this.shadowRadius = 0.5F;
        this.addRenderLayer(new NecroticAuraRenderLayer<>(this));
    }
}