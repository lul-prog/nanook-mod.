package com.nanookmod.client.render;

import com.nanookmod.NanookMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanookmod.client.model.NecroticSlashVfxModel;
import net.minecraft.resources.ResourceLocation;
import com.nanookmod.entity.NecroticSlashVfx;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Renderer GeckoLib para la guadaña necrótica gigante. Sin sombra
 * (shadowRadius = 0): es un efecto, no un objeto físico en el mundo.
 *
 * v5: GeckoLib no siempre rota automáticamente entidades genéricas (que
 * extienden Entity y no Mob/LivingEntity) según su yaw -- por eso la guadaña
 * quedaba "mirando" siempre para el mismo lado sin importar hacia dónde
 * girara el jugador. Se fuerza la rotación a mano, con el mismo patrón que
 * ya usa NanookClawProjectileRenderer para el proyectil de garra
 * (Mth.lerp entre yRotO/getYRot() + Axis.YP.rotationDegrees), pero dejando
 * que GeckoLib siga manejando la animación esquelética con super.render().
 *
 * Si después de este cambio la guadaña gira MÁS de lo esperado (el doble de
 * rápido, o da vueltas raras), probablemente signifique que GeckoLib SÍ
 * estaba rotando por su cuenta en tu versión y ahora se está sumando con
 * esta rotación manual -- en ese caso, borrá las 3 líneas del bloque
 * "rotación manual" de abajo y dejá solo el super.render().
 */
public class NecroticSlashVfxRenderer extends GeoEntityRenderer<NecroticSlashVfx> {

    /**
     * El giro (vertical_spining_slash2) rota sobre el eje X del modelo (a
     * diferencia de los otros 3 golpes), así que nuestra rotación manual de
     * yaw (eje Y) no alcanza para orientarlo bien -- probablemente por eso
     * seguía viéndose "de costado" en vez de encarado hacia vos. Este es un
     * ángulo extra que SOLO se aplica durante el giro. Probá 0 / 90 / 180 /
     * 270 hasta que el anillo quede de frente.
     */
    private static final float SPIN_EXTRA_PITCH = 10.0F;

    public NecroticSlashVfxRenderer(EntityRendererProvider.Context context) {
        super(context, new NecroticSlashVfxModel());
        // Brilla siempre, de día, de noche o en una cueva -- ver AlwaysGlowRenderLayer.
        this.addRenderLayer(new AlwaysGlowRenderLayer<>(this,
                new ResourceLocation(NanookMod.MOD_ID, "textures/entity/necrotic_slash/necrotic_slash_vfx.png")));
        this.shadowRadius = 0f;
    }

    @Override
    public void render(NecroticSlashVfx entity, float entityYaw, float partialTick,
                        PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // --- rotación manual ---
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

        if (entity.isSpinning()) {
            poseStack.mulPose(Axis.XP.rotationDegrees(SPIN_EXTRA_PITCH));
        }
        // --- fin rotación manual ---

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}
