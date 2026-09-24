package com.nanookmod.client.render;

import com.nanookmod.client.model.NanookModel;
import com.nanookmod.entity.NanookEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renderer que GeckoLib usa para dibujar a Nanook en pantalla, combinando
 * el modelo (NanookModel) con la entidad (NanookEntity).
 */
public class NanookRenderer extends GeoEntityRenderer<NanookEntity> {

    public NanookRenderer(EntityRendererProvider.Context context) {
        super(context, new NanookModel());
        this.shadowRadius = 0.9f;
    }
}
