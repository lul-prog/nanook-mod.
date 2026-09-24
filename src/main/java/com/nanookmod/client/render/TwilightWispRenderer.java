package com.nanookmod.client.render;

import com.nanookmod.client.model.TwilightWispModel;
import com.nanookmod.entity.TwilightWispEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TwilightWispRenderer extends GeoEntityRenderer<TwilightWispEntity> {

    public TwilightWispRenderer(EntityRendererProvider.Context context) {
        super(context, new TwilightWispModel());
        this.shadowRadius = 0.0F;
        this.addRenderLayer(new TwilightWispAuraRenderLayer(this));
        // La estela YA NO se registra acá -- ver TwilightWispTrailRenderer,
        // que la dibuja desde RenderLevelStageEvent en espacio de mundo real
        // (un GeoRenderLayer no sirve para esto: su PoseStack está rotado
        // según hacia dónde mira la entidad, y la estela necesita
        // coordenadas de mundo sin esa rotación).
    }

    @Override
    protected int getBlockLightLevel(TwilightWispEntity animatable, BlockPos pos) {
        // Full-bright, como el aura -- es una almita mágica, se tiene que
        // ver de noche (que es literalmente el único momento en que existe).
        return 15;
    }
}
