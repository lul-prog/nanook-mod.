package com.nanookmod.client;

import com.nanookmod.entity.NecroticLightningBeamEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer "vacío" a propósito. La geometría del rayo (tubo, discos,
 * chispas) se dibuja aparte, en NecroticLightningRenderer, vía
 * RenderLevelStageEvent con el shader nanookmod:necrotic_beam -- no pasa
 * por el pipeline normal de texturas de entidad. Este renderer solo
 * existe porque Forge exige que toda entidad visible tenga uno
 * registrado; no dibuja nada él mismo (a diferencia del resto de las VFX
 * del mod, que sí usan GeckoLib -- este rayo no tiene modelo ni textura
 * propia, es pura geometría por código).
 */
public class NecroticLightningBeamRenderer extends EntityRenderer<NecroticLightningBeamEntity> {

    public NecroticLightningBeamRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(NecroticLightningBeamEntity entity) {
        // Nunca se usa -- no dibujamos nada en este renderer.
        return new ResourceLocation("missingno");
    }
}
