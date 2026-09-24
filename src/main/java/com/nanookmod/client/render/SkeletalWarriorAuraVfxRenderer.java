package com.nanookmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanookmod.client.model.SkeletalWarriorAuraVfxModel;
import com.nanookmod.entity.SkeletalWarriorAuraVfx;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * v3: en vez de dibujar el aura en su propia posición/rotación (sincronizada
 * por red por separado del dueño, lo que causaba un desfasaje visible entre
 * las dos entidades apenas había movimiento), resolvemos al dueño por
 * DATA_OWNER_ID y dibujamos el aura pegada exactamente a su posición y
 * rotación de CUERPO ya interpoladas (yBodyRot/yBodyRotO, las mismas que
 * usa el propio renderer del mob para su modelo) en cada frame. Así las dos
 * mallas quedan siempre alineadas, sin importar el tick rate de cada una.
 *
 * Si el dueño todavía no está cargado del lado cliente (raro, un frame o
 * dos al spawnear), caemos de vuelta a la rotación propia de esta entidad
 * como fallback para no reventar el render.
 */
public class SkeletalWarriorAuraVfxRenderer extends GeoEntityRenderer<SkeletalWarriorAuraVfx> {

    public SkeletalWarriorAuraVfxRenderer(EntityRendererProvider.Context context) {
        super(context, new SkeletalWarriorAuraVfxModel());
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(SkeletalWarriorAuraVfx entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Entity ownerEntity = entity.level().getEntity(entity.getOwnerId());

        poseStack.pushPose();

        if (ownerEntity instanceof LivingEntity owner) {
            // El dispatcher ya trasladó el poseStack a la posición
            // interpolada de ESTA entidad (el VFX). Lo corregimos sumando
            // la diferencia contra la posición interpolada del dueño, para
            // terminar exactamente en la posición del dueño sin importar
            // si las dos entidades están un frame desincronizadas por red.
            double ownerX = Mth.lerp(partialTick, owner.xo, owner.getX());
            double ownerY = Mth.lerp(partialTick, owner.yo, owner.getY());
            double ownerZ = Mth.lerp(partialTick, owner.zo, owner.getZ());
            double vfxX = Mth.lerp(partialTick, entity.xo, entity.getX());
            double vfxY = Mth.lerp(partialTick, entity.yo, entity.getY());
            double vfxZ = Mth.lerp(partialTick, entity.zo, entity.getZ());

            poseStack.translate(ownerX - vfxX, ownerY - vfxY, ownerZ - vfxZ);

            // yBodyRot/yBodyRotO: la rotación de CUERPO ya suavizada que usa
            // el propio LivingEntityRenderer del dueño para orientar su
            // modelo -- así el aura gira exactamente en sincro con el mob
            // que se ve en pantalla, no con su yaw "crudo" de movimiento.
            float yaw = Mth.lerp(partialTick, owner.yBodyRotO, owner.yBodyRot);
            poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        } else {
            float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        }

        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }

    @Override
    public int getPackedOverlay(SkeletalWarriorAuraVfx animatable, float u, float partialTick) {
        // Sin overlay de daño/blanco (no es una entidad "golpeable").
        return OverlayTexture.NO_OVERLAY;
    }

    @Override
    protected int getBlockLightLevel(SkeletalWarriorAuraVfx animatable, net.minecraft.core.BlockPos pos) {
        // Full-bright: es fuego mágico, se tiene que ver igual de noche que de día.
        return 15;
    }
}